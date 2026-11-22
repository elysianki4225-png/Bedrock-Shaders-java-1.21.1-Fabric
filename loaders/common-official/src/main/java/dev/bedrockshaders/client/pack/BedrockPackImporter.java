package dev.bedrockshaders.client.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bedrockshaders.BedrockShadersMod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Converts a Bedrock {@code .mcpack} (or any zip) into a Java resource pack.
 *
 * <p>Bedrock ships its rendering look as loose JSON. Java's resource pack system can
 * load those exact files if they are placed under this mod's namespace, so conversion
 * is a filtered copy plus a generated {@code pack.mcmeta} - no re-encoding, and a pack
 * that Bedrock itself would accept resolves to the same numbers here.</p>
 *
 * <p>Nested archives are handled recursively (the common {@code .mcpack} containing a
 * second {@code .mcpack}); the first archive that carries recognised rendering data
 * wins, so a combined resource+behaviour pack still imports its visuals.</p>
 */
public final class BedrockPackImporter {
    /** Pack format 34 == Minecraft 1.21.1 resource packs. */
    public static final int PACK_FORMAT = 34;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int MAX_NESTING = 4;
    private static final long MAX_ENTRY_BYTES = 8L * 1024L * 1024L;

    /** Result of one import attempt. */
    public record Result(boolean success, ImportedPack pack, BedrockPackKind kind, String message) {
        public static Result failure(String message) {
            return new Result(false, null, BedrockPackKind.UNSUPPORTED, message);
        }
    }

    private BedrockPackImporter() {
    }

    /**
     * Imports {@code archive} into {@code targetDir}: a directory named after the pack's
     * manifest/UUID. Files are written into a temporary directory first and moved into
     * place, so a failed import never leaves a half-written pack behind.
     */
    public static Result importArchive(Path archive, Path targetDir) {
        if (!Files.isRegularFile(archive)) {
            return Result.failure("not a file: " + archive);
        }
        long size = 0L;
        try {
            size = Files.size(archive);
        } catch (IOException ignored) {
            // Size is only used for the sanity check below.
        }
        if (size > 64L * 1024L * 1024L) {
            return Result.failure("archive too large: " + size + " bytes");
        }
        Path staging = null;
        try {
            staging = Files.createTempDirectory("bedrock-shaders-import");
        } catch (IOException e) {
            return Result.failure("cannot create temp directory: " + e.getMessage());
        }
        try {
            boolean found = extract(archive, staging, 0);
            if (!found) {
                deleteRecursively(staging);
                return Result.failure("no Bedrock rendering data (fogs/lighting/atmospherics/color_grading)");
            }
            Counting counted = count(staging);
            BedrockPackManifest manifest = readManifest(archive, 0);
            BedrockPackKind kind = counted.total() == 0
                    ? (manifest.behaviour() ? BedrockPackKind.BEHAVIOUR : BedrockPackKind.UNSUPPORTED)
                    : BedrockPackKind.DEFERRED_DATA;
            if (kind != BedrockPackKind.DEFERRED_DATA) {
                deleteRecursively(staging);
                return Result.failure("archive has no rendering data (" + kind.id() + ")");
            }

            String folder = PackRegistry.GENERATED_PREFIX + sanitise(manifest.slug());
            Path packDir = targetDir.resolve(folder);
            if (Files.isDirectory(packDir)) {
                deleteRecursively(packDir);
            }
            Files.createDirectories(packDir);

            // Move the staged assets and write the Java pack manifest.
            Path stagedAssets = staging.resolve("assets");
            if (Files.isDirectory(stagedAssets)) {
                copyTree(stagedAssets, packDir.resolve("assets"));
            }
            Files.writeString(packDir.resolve("pack.mcmeta"), packMeta(manifest), StandardCharsets.UTF_8);

            ImportedPack pack = ImportedPack.of(archive.getFileName().toString(), manifest,
                    BedrockPackKind.DEFERRED_DATA, folder);
            pack.fogFiles = counted.fog;
            pack.lightingFiles = counted.lighting;
            pack.atmosphericFiles = counted.atmospheric;
            pack.gradingFiles = counted.grading;
            pack.hasWaterTable = counted.water;
            deleteRecursively(staging);
            BedrockShadersMod.LOGGER.info("Imported Bedrock pack {} -> {}", archive.getFileName(), packDir);
            return new Result(true, pack, BedrockPackKind.DEFERRED_DATA, pack.describe());
        } catch (IOException | RuntimeException e) {
            deleteRecursively(staging);
            BedrockShadersMod.LOGGER.warn("Failed to import Bedrock archive {}", archive, e);
            return Result.failure(String.valueOf(e.getMessage()));
        }
    }

    /**
     * Walks one zip level and copies recognised JSON into {@code staging/assets}. Nested
     * archives are extracted to a scratch file and recursed into.
     *
     * @return true when at least one rendering JSON file was copied
     */
    private static boolean extract(Path archive, Path staging, int depth) throws IOException {
        if (depth > MAX_NESTING) {
            return false;
        }
        boolean copied = false;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = normalise(entry.getName());
                if (name.isEmpty() || entry.isDirectory()) {
                    continue;
                }
                if (BedrockPackLayout.isManifest(name)) {
                    continue;
                }
                if (isArchive(name)) {
                    if (entry.getSize() > 64L * 1024L * 1024L) {
                        continue;
                    }
                    Path nested = Files.createTempFile("bedrock-nested", ".zip");
                    try (InputStream stream = zip.getInputStream(entry)) {
                        Files.copy(stream, nested, StandardCopyOption.REPLACE_EXISTING);
                    }
                    if (extract(nested, staging, depth + 1)) {
                        copied = true;
                    }
                    Files.deleteIfExists(nested);
                    continue;
                }
                String target = BedrockPackLayout.targetFor(name);
                if (target != null) {
                    copyEntry(zip, entry, staging.resolve("assets").resolve(BedrockShadersMod.MOD_ID)
                            .resolve("bedrock").resolve(target));
                    copied = true;
                    continue;
                }
                if (BedrockPackLayout.isWaterTable(name)) {
                    copyEntry(zip, entry, staging.resolve("assets").resolve(BedrockShadersMod.MOD_ID)
                            .resolve("bedrock").resolve("water").resolve("biomes_client.json"));
                    copied = true;
                }
            }
        }
        return copied;
    }

    private static void copyEntry(ZipFile zip, ZipEntry entry, Path destination) throws IOException {
        if (entry.getSize() > MAX_ENTRY_BYTES) {
            BedrockShadersMod.LOGGER.warn("Skipping oversized Bedrock asset {} ({} bytes)",
                    entry.getName(), entry.getSize());
            return;
        }
        Files.createDirectories(destination.getParent());
        try (InputStream stream = zip.getInputStream(entry)) {
            Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static BedrockPackManifest readManifest(Path archive, int depth) {
        if (depth > MAX_NESTING) {
            return BedrockPackManifest.UNKNOWN;
        }
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = normalise(entry.getName());
                if (BedrockPackLayout.isManifest(name) && entry.getSize() < 512L * 1024L) {
                    String text;
                    try (InputStream stream = zip.getInputStream(entry)) {
                        text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    BedrockPackManifest manifest = BedrockPackManifest.parse(text);
                    if (manifest != BedrockPackManifest.UNKNOWN) {
                        return manifest;
                    }
                }
                if (isArchive(name) && entry.getSize() < 32L * 1024L * 1024L) {
                    Path nested = Files.createTempFile("bedrock-manifest", ".zip");
                    try (InputStream stream = zip.getInputStream(entry)) {
                        Files.copy(stream, nested, StandardCopyOption.REPLACE_EXISTING);
                    }
                    BedrockPackManifest manifest = readManifest(nested, depth + 1);
                    Files.deleteIfExists(nested);
                    if (manifest != BedrockPackManifest.UNKNOWN) {
                        return manifest;
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            BedrockShadersMod.LOGGER.debug("No readable Bedrock manifest in {}", archive, e);
        }
        return BedrockPackManifest.UNKNOWN;
    }

    private static String packMeta(BedrockPackManifest manifest) {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", PACK_FORMAT);
        pack.addProperty("description", "Bedrock pack: " + manifest.name() + " " + manifest.version()
                + " (imported by Bedrock Shaders)");
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return GSON.toJson(root);
    }

    private record Counting(int fog, int lighting, int atmospheric, int grading, boolean water) {
        int total() {
            return fog + lighting + atmospheric + grading;
        }
    }

    private static Counting count(Path staging) throws IOException {
        Path bedrock = staging.resolve("assets").resolve(BedrockShadersMod.MOD_ID).resolve("bedrock");
        int fog = listJson(bedrock.resolve("fogs"));
        int lighting = listJson(bedrock.resolve("lighting"));
        int atmospheric = listJson(bedrock.resolve("atmospherics"));
        int grading = listJson(bedrock.resolve("color_grading"));
        boolean water = Files.isRegularFile(bedrock.resolve("water").resolve("biomes_client.json"));
        return new Counting(fog, lighting, atmospheric, grading, water);
    }

    private static int listJson(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (var stream = Files.list(dir)) {
            return (int) stream.filter(path -> path.getFileName().toString().endsWith(".json")).count();
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (var stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path candidate : stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.deleteIfExists(candidate);
            }
        } catch (IOException e) {
            BedrockShadersMod.LOGGER.warn("Could not delete {}", path, e);
        }
    }

    private static String normalise(String name) {
        String value = name.replace('\\', '/');
        while (value.startsWith("./")) {
            value = value.substring(2);
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private static boolean isArchive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mcpack") || lower.endsWith(".zip") || lower.endsWith(".mcaddon")
                || lower.endsWith(".mctemplate") || lower.endsWith(".mcdat");
    }

    private static String sanitise(String value) {
        String clean = value.replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
        return clean.isEmpty() ? "bedrock_pack" : clean;
    }

    /** Convenience for tests / the GUI: import every archive in {@code dir}. */
    public static List<Result> importAll(Path dir, Path targetDir) {
        List<Result> results = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return results;
        }
        try (var stream = Files.list(dir)) {
            Map<Path, String> archives = new LinkedHashMap<>();
            for (Path path : stream.sorted().toList()) {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".mcpack") || name.endsWith(".mcaddon") || name.endsWith(".zip")) {
                    archives.put(path, name);
                }
            }
            for (Path path : archives.keySet()) {
                results.add(importArchive(path, targetDir));
            }
        } catch (IOException e) {
            BedrockShadersMod.LOGGER.warn("Could not scan import folder {}", dir, e);
        }
        return results;
    }

    /** Reads an {@code index.json} written by {@link PackRegistry}. */
    static JsonArray readIndex(Path indexFile) {
        if (!Files.isRegularFile(indexFile)) {
            return new JsonArray();
        }
        try {
            String text = Files.readString(indexFile, StandardCharsets.UTF_8);
            var parsed = JsonParser.parseString(text);
            return parsed.isJsonArray() ? parsed.getAsJsonArray() : new JsonArray();
        } catch (IOException | RuntimeException e) {
            return new JsonArray();
        }
    }
}

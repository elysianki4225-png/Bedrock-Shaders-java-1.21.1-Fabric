package dev.bedrockshaders.client.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bedrockshaders.BedrockShadersMod;
import dev.bedrockshaders.client.ConfigManager;

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

    /**
     * Result of one import attempt.
     *
     * <p>{@code detection} is the RenderDragon finding: always present, and
     * {@link RenderDragonSupport.Detection#none()} for ordinary deferred-data packs. It is
     * carried even when the import failed, because "this is a RenderDragon pack" is the
     * most useful thing to tell a user whose archive imported nothing.</p>
     */
    public record Result(boolean success, ImportedPack pack, BedrockPackKind kind, String message,
                         RenderDragonSupport.Detection detection) {
        public static Result failure(String message) {
            return new Result(false, null, BedrockPackKind.UNSUPPORTED, message,
                    RenderDragonSupport.Detection.none());
        }

        public static Result failure(String message, RenderDragonSupport.Detection detection) {
            return new Result(false, null, BedrockPackKind.UNSUPPORTED, message, detection);
        }

        /** True when this archive is a RenderDragon pack, imported or not. */
        public boolean renderDragon() {
            return detection.renderDragon();
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
            // RenderDragon detection has to happen before extraction: the compiled pipeline
            // is what the ordinary extractor deliberately drops, so afterwards there is
            // nothing left to recognise.
            RenderDragonSupport.Detection detection = detectRenderDragon(archive);
            boolean acceptRenderDragon = ConfigManager.get().config().experimentalRenderDragon;
            boolean disabledBySwitch = detection.renderDragon() && !acceptRenderDragon;

            boolean found = extract(archive, staging, 0, detection, acceptRenderDragon);
            if (!found) {
                deleteRecursively(staging);
                if (disabledBySwitch) {
                    return Result.failure(
                            "RenderDragon pack (" + detection.describe() + ") but experimental "
                                    + "RenderDragon support is off", detection);
                }
                if (detection.binaryOnly()) {
                    return Result.failure(
                            "RenderDragon pack with only compiled materials, which no Java "
                                    + "mod can run (" + detection.describe() + ")", detection);
                }
                return Result.failure(
                        "no Bedrock rendering data (fogs/lighting/atmospherics/color_grading)",
                        detection);
            }
            Counting counted = count(staging);
            BedrockPackManifest manifest = readManifest(archive, 0);
            BedrockPackKind kind = counted.total() == 0
                    ? (manifest.behaviour() ? BedrockPackKind.BEHAVIOUR : BedrockPackKind.UNSUPPORTED)
                    : BedrockPackKind.DEFERRED_DATA;
            if (kind != BedrockPackKind.DEFERRED_DATA) {
                deleteRecursively(staging);
                return Result.failure("archive has no rendering data (" + kind.id() + ")", detection);
            }

            // Extension packs see the archive before a single byte is written, and can add or
            // rewrite data files. This is the only place the public API may touch a pack's
            // content, and it cannot escape the pack folder (see PackWriter).
            // String.join throws on a null element and a Bedrock manifest may omit either
            // field, so the hints are built defensively.
            String[] hints = {archive.getFileName().toString(),
                    nullToEmpty(manifest.name()), nullToEmpty(manifest.description())};
            dev.bedrockshaders.api.PackDescriptor descriptor = new dev.bedrockshaders.api.PackDescriptor(
                    archive.getFileName().toString(), listStaged(staging));
            descriptor.setDetectedFamily(detection.familyHint());
            descriptor.setRenderDragon(detection.renderDragon());
            descriptor.metadata().put("name", String.valueOf(manifest.name()));
            descriptor.metadata().put("description", String.valueOf(manifest.description()));
            descriptor.metadata().put("slug", String.valueOf(manifest.slug()));
            descriptor.metadata().put("hints", String.join(" | ", hints));
            dev.bedrockshaders.api.PackDescriptor processed =
                    dev.bedrockshaders.api.ExtensionRegistry.get().runPreprocessors(descriptor);
            if (processed == null) {
                deleteRecursively(staging);
                return Result.failure("an extension pack vetoed this import"
                        + (descriptor.vetoReason() == null ? "" : ": " + descriptor.vetoReason()),
                        detection);
            }
            descriptor = processed;
            // Extension-supplied JSON lands next to the extracted Bedrock data and is picked
            // up by the existing pack loader on the next scan.
            if (!descriptor.extraFiles().isEmpty()) {
                writeExtraFiles(staging, descriptor);
                counted = count(staging);
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
            pack.renderDragon = detection.renderDragon();
            pack.renderDragonBinaries = detection.binaries();
            deleteRecursively(staging);
            BedrockShadersMod.LOGGER.info("Imported Bedrock pack {} -> {}", archive.getFileName(), packDir);
            if (detection.renderDragon()) {
                BedrockShadersMod.LOGGER.warn(
                        "Imported RenderDragon pack {} with experimental support: {}; the compiled "
                                + "pipeline cannot run on Java, only its data half was applied",
                        archive.getFileName(), detection.describe());
            }
            writeExtensionNotes(packDir, descriptor.descriptionNotes());
            dev.bedrockshaders.api.ExtensionRegistry.get().fireAfterImport(descriptor, packDir);
            return new Result(true, pack, BedrockPackKind.DEFERRED_DATA, pack.describe(), detection);
        } catch (IOException | RuntimeException e) {
            deleteRecursively(staging);
            BedrockShadersMod.LOGGER.warn("Failed to import Bedrock archive {}", archive, e);
            return Result.failure(String.valueOf(e.getMessage()), RenderDragonSupport.Detection.none());
        }
    }

    /**
     * Walks one zip level and copies recognised JSON into {@code staging/assets}. Nested
     * archives are extracted to a scratch file and recursed into.
     *
     * @return true when at least one rendering JSON file was copied
     */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Relative paths of everything that survived extraction, for the public descriptor. */
    private static java.util.List<String> listStaged(Path staging) {
        java.util.List<String> names = new java.util.ArrayList<>();
        Path assets = staging.resolve("assets");
        if (!Files.isDirectory(assets)) {
            return names;
        }
        try (var walk = Files.walk(assets)) {
            walk.filter(Files::isRegularFile).forEach(path ->
                    names.add(assets.relativize(path).toString().replace(java.io.File.separatorChar, '/')));
        } catch (IOException | RuntimeException e) {
            BedrockShadersMod.LOGGER.warn("Could not list staged pack entries", e);
        }
        java.util.Collections.sort(names);
        return names;
    }

    /**
     * Records what extensions did to a pack beside the pack itself, so the GUI can show it
     * and a bug report has the trail without digging through the log.
     */
    private static void writeExtensionNotes(Path packDir, java.util.List<String> notes) {
        if (notes.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder("# extension notes\n");
        for (String note : notes) {
            text.append("- ").append(note).append('\n');
        }
        try {
            Files.writeString(packDir.resolve("bedrock_shaders_extension_notes.txt"), text.toString(),
                    StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            BedrockShadersMod.LOGGER.warn("Could not write extension notes for {}", packDir, e);
        }
    }

    /**
     * Writes the files an extension preprocessor added, confined to the pack's own data tree.
     *
     * <p>{@link dev.bedrockshaders.api.PackDescriptor#addFile(String, String)} already validates
     * the path, and this repeats the containment check against the staging directory because a
     * preprocessor is third-party code.</p>
     */
    private static void writeExtraFiles(Path staging, dev.bedrockshaders.api.PackDescriptor descriptor) {
        Path assets = staging.resolve("assets").normalize();
        for (var entry : descriptor.extraFiles().entrySet()) {
            try {
                String relative = entry.getKey();
                if (relative.startsWith("/") || relative.contains("\\") || relative.contains("..")) {
                    BedrockShadersMod.LOGGER.warn("Extension file path refused: {}", relative);
                    continue;
                }
                Path target = staging.resolve("assets").resolve(relative).normalize();
                if (!target.startsWith(assets)) {
                    BedrockShadersMod.LOGGER.warn("Extension file escaped the pack folder: {}", relative);
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                BedrockShadersMod.LOGGER.warn("Extension file could not be written: {}", entry.getKey(), e);
            }
        }
    }

    private static boolean extract(Path archive, Path staging, int depth,
                                   RenderDragonSupport.Detection detection,
                                   boolean acceptRenderDragon) throws IOException {
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
                    if (extract(nested, staging, depth + 1, detection, acceptRenderDragon)) {
                        copied = true;
                    }
                    Files.deleteIfExists(nested);
                    continue;
                }
                // RenderDragon's authored JSON lives under renderer/. It is only applied
                // when the experimental switch is on, and the compiled pipeline beside it is
                // skipped either way: those binaries are unreadable on Java.
                if (RenderDragonSupport.isBinaryPipeline(name)) {
                    continue;
                }
                if (acceptRenderDragon) {
                    String renderDragonTarget = RenderDragonSupport.targetFor(name);
                    if (renderDragonTarget != null) {
                        copyEntry(zip, entry, staging.resolve("assets").resolve(BedrockShadersMod.MOD_ID)
                                .resolve("bedrock").resolve(renderDragonTarget));
                        copied = true;
                        continue;
                    }
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

    /**
     * Collects entry names from the archive and every nested archive, then asks
     * {@link RenderDragonSupport} what they are. Names only: nothing is decompressed, so
     * even a 60 MB material pack costs one directory walk.
     */
    private static RenderDragonSupport.Detection detectRenderDragon(Path archive) {
        List<String> entries = new ArrayList<>();
        collectEntryNames(archive, entries, 0);
        return RenderDragonSupport.detect(entries);
    }

    private static void collectEntryNames(Path archive, List<String> out, int depth) {
        if (depth > MAX_NESTING) {
            return;
        }
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = normalise(entry.getName());
                if (name.isEmpty() || entry.isDirectory()) {
                    continue;
                }
                // Names only, never contents: a 60 MB compiled material costs one string.
                out.add(name);
                if (RenderDragonSupport.isBinaryPipeline(name)) {
                    continue;
                }
                if (isArchive(name) && entry.getSize() < 64L * 1024L * 1024L) {
                    Path nested = Files.createTempFile("bedrock-dragon", ".zip");
                    try (InputStream stream = zip.getInputStream(entry)) {
                        Files.copy(stream, nested, StandardCopyOption.REPLACE_EXISTING);
                    }
                    try {
                        collectEntryNames(nested, out, depth + 1);
                    } finally {
                        Files.deleteIfExists(nested);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            BedrockShadersMod.LOGGER.debug("Could not inspect {} for RenderDragon content", archive, e);
        }
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

package dev.bedrockshaders.client.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.bedrockshaders.BedrockShadersMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Owns the instance folder that behaves like Iris' {@code shaderpacks} folder:
 *
 * <pre>
 * .minecraft/bedrock-shaders/packs/           &lt;- drop .mcpack files here
 * .minecraft/bedrock-shaders/index.json       &lt;- import bookkeeping
 * .minecraft/resourcepacks/bedrock_shaders_*  &lt;- generated Java resource packs
 * </pre>
 *
 * <p>The generated packs must live in vanilla's own {@code resourcepacks/} folder:
 * Minecraft only discovers pack folders there, and putting them there means imported
 * packs show up in the normal resource-pack screen and survive a restart on their
 * own.</p>
 *
 * <p>Every Bedrock archive in {@code packs/} is imported idempotently: unchanged files
 * keep their existing conversion, changed files are re-imported, removed files are
 * unregistered (their generated pack folder is deleted only when the user asks for a
 * clean-up).</p>
 */
public final class PackRegistry {
    private static final PackRegistry INSTANCE = new PackRegistry();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<String, ImportedPack> packs = new LinkedHashMap<>();
    private boolean loaded;

    private PackRegistry() {
    }

    public static PackRegistry get() {
        return INSTANCE;
    }

    public static Path root() {
        return FabricLoader.getInstance().getGameDir().resolve("bedrock-shaders");
    }

    public static Path importFolder() {
        return root().resolve("packs");
    }

    /** Prefix shared by every generated pack folder, so they are easy to find/clean. */
    public static final String GENERATED_PREFIX = "bedrock_shaders_";

    /** Vanilla's pack folder - the only place Minecraft scans for resource packs. */
    public static Path generatedFolder() {
        return FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
    }

    public static Path indexFile() {
        return root().resolve("index.json");
    }

    /** Creates the folder tree and a README explaining what to drop where. */
    public void ensureFolders() {
        try {
            Files.createDirectories(importFolder());
            Files.createDirectories(generatedFolder());
            Path readme = importFolder().resolve("README.txt");
            if (!Files.isRegularFile(readme)) {
                Files.writeString(readme, """
                        Bedrock Shaders - Bedrock shader pack import folder
                        ==================================================

                        Drop Bedrock Edition shader / resource packs here and they are
                        imported automatically on next launch (or immediately via
                        /bedrock import or the "Bedrock Packs" screen).

                        Supported files
                          *.mcpack    Bedrock shader or resource pack (zip)
                          *.mcaddon   Bedrock add-on bundle (nested archives are unpacked)
                          *.zip       any archive containing fogs/, lighting/,
                                      atmospherics/ or color_grading/ JSON

                        What is imported
                          fogs/*_fog_setting.json        biome fog distance + colour
                          lighting/*_lighting.json       sun/moon illuminance curves
                          atmospherics/*_atmospherics.json  sky colours
                          color_grading/*_color_grading.json  tone mapping
                          biomes_client.json             water colour per biome

                        Textures, models and sounds are ignored - only the deferred
                        rendering data is used. Binary RenderDragon shaders
                        (*.material.bin) are not supported by Minecraft: Java Edition.

                        Converted Java resource packs are written to
                        .minecraft/resourcepacks/bedrock_shaders_<name>/ and are enabled in
                        Options > Resource Packs automatically (listed last, so your own
                        packs still win).
                        """, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            BedrockShadersMod.LOGGER.error("Could not create Bedrock Shaders folders", e);
        }
    }

    public synchronized void load() {
        ensureFolders();
        packs.clear();
        JsonArray array = BedrockPackImporter.readIndex(indexFile());
        for (JsonElement element : array) {
            try {
                ImportedPack pack = GSON.fromJson(element, ImportedPack.class);
                if (pack != null && pack.folder != null && !pack.folder.isEmpty()) {
                    packs.put(pack.fileName.toLowerCase(Locale.ROOT), pack);
                }
            } catch (RuntimeException e) {
                BedrockShadersMod.LOGGER.warn("Skipping unreadable index entry: {}", element);
            }
        }
        loaded = true;
        invalidateActivePackCache();
        BedrockShadersMod.LOGGER.info("Bedrock pack registry: {} imported pack(s) in {}", packs.size(), root());
    }

    public synchronized void save() {
        JsonArray array = new JsonArray();
        for (ImportedPack pack : packs.values()) {
            array.add(GSON.toJsonTree(pack));
        }
        try {
            Files.createDirectories(root());
            Files.writeString(indexFile(), GSON.toJson(array), StandardCharsets.UTF_8);
        } catch (IOException e) {
            BedrockShadersMod.LOGGER.error("Could not write {}", indexFile(), e);
        }
    }

    /** Imports every archive found in {@link #importFolder()} that is new or changed. */
    public synchronized List<BedrockPackImporter.Result> importPending() {
        if (!loaded) {
            load();
        }
        ensureFolders();
        List<BedrockPackImporter.Result> results = new ArrayList<>();
        List<Path> archives = new ArrayList<>();
        try (var stream = Files.list(importFolder())) {
            for (Path path : stream.sorted().toList()) {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".mcpack") || name.endsWith(".mcaddon") || name.endsWith(".zip")) {
                    archives.add(path);
                }
            }
        } catch (IOException e) {
            BedrockShadersMod.LOGGER.warn("Could not list {}", importFolder(), e);
            return results;
        }
        for (Path archive : archives) {
            String key = archive.getFileName().toString().toLowerCase(Locale.ROOT);
            ImportedPack existing = packs.get(key);
            long modified = 0L;
            try {
                modified = Files.getLastModifiedTime(archive).toMillis();
            } catch (IOException ignored) {
                // Fall through with 0 so the pack is re-imported.
            }
            if (existing != null && existing.importedAt >= modified && Files.isDirectory(existing.packDirectory(generatedFolder()))) {
                continue;
            }
            BedrockPackImporter.Result result = BedrockPackImporter.importArchive(archive, generatedFolder());
            results.add(result);
            if (result.success() && result.pack() != null) {
                packs.put(key, result.pack());
            } else if (existing != null) {
                BedrockShadersMod.LOGGER.warn("Keeping previous import of {} after a failed re-import", key);
            }
        }
        if (!results.isEmpty()) {
            invalidateActivePackCache();
            save();
        }
        return results;
    }

    /** Imports one archive the user selected in the GUI. */
    public synchronized BedrockPackImporter.Result importFile(Path archive) {
        if (!loaded) {
            load();
        }
        ensureFolders();
        Path target = importFolder().resolve(archive.getFileName().toString());
        try {
            if (!archive.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
                Files.copy(archive, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            return BedrockPackImporter.Result.failure("cannot copy archive: " + e.getMessage());
        }
        BedrockPackImporter.Result result = BedrockPackImporter.importArchive(target, generatedFolder());
        if (result.success() && result.pack() != null) {
            packs.put(target.getFileName().toString().toLowerCase(Locale.ROOT), result.pack());
            save();
        }
        return result;
    }

    /** Archives the user has dropped and that imported successfully, in drop order. */
    public synchronized List<ImportedPack> imported() {
        return new ArrayList<>(packs.values());
    }

    public synchronized List<ImportedPack> active() {
        List<ImportedPack> active = new ArrayList<>();
        for (ImportedPack pack : packs.values()) {
            if (pack.enabled && pack.packKind() == BedrockPackKind.DEFERRED_DATA
                    && Files.isDirectory(pack.packDirectory(generatedFolder()))) {
                active.add(pack);
            }
        }
        return active;
    }

    /**
     * Cached answer to {@link #activePackName()}.
     *
     * <p>The frame path asks for this every frame; recomputing it meant taking the registry
     * lock and walking the map 60+ times a second. The cache is invalidated whenever the
     * pack set changes, which is the only time the answer can change.</p>
     */
    private volatile String activePackCache = "";

    /**
     * Family of the first enabled deferred-data pack, cached alongside the name. The frame
     * path reads this every frame, so it must not walk the pack map.
     */
    private volatile dev.bedrockshaders.config.ShaderFamily activeFamilyCache =
            dev.bedrockshaders.config.ShaderFamily.GENERIC;

    /** Tuning family of the active pack; {@code GENERIC} when none is active. */
    public dev.bedrockshaders.config.ShaderFamily activeFamily() {
        return activeFamilyCache;
    }

    private void invalidateActivePackCache() {
        String name = "";
        dev.bedrockshaders.config.ShaderFamily family =
                dev.bedrockshaders.config.ShaderFamily.GENERIC;
        for (ImportedPack pack : packs.values()) {
            if (pack.enabled && pack.packKind() == BedrockPackKind.DEFERRED_DATA) {
                name = pack.displayName;
                family = pack.shaderFamily();
                break;
            }
        }
        activePackCache = name;
        activeFamilyCache = family;
    }

    /** Name of the first active imported pack, or an empty string when none is loaded. */
    public String activePackName() {
        return activePackCache;
    }

    /**
     * Name of the imported pack that supplies data for this biome. Bedrock packs can be
     * biome scoped, but this importer merges them into the shared namespace, so the first
     * enabled pack is the one whose files win - returning its name keeps the debug/fog
     * output honest about where the numbers came from.
     */
    public synchronized String activePackForBiome(String biomeId) {
        return activePackName();
    }

    public synchronized void setEnabled(String fileName, boolean enabled) {
        ImportedPack pack = packs.get(fileName.toLowerCase(Locale.ROOT));
        if (pack != null) {
            pack.enabled = enabled;
            invalidateActivePackCache();
            save();
        }
    }

    /** Removes the import entry and the generated resource pack. */
    public synchronized void delete(String fileName) {
        ImportedPack pack = packs.remove(fileName.toLowerCase(Locale.ROOT));
        if (pack != null) {
            invalidateActivePackCache();
            BedrockPackImporter.deleteRecursively(pack.packDirectory(generatedFolder()));
            try {
                Files.deleteIfExists(importFolder().resolve(pack.fileName));
            } catch (IOException e) {
                BedrockShadersMod.LOGGER.warn("Could not delete source archive {}", pack.fileName, e);
            }
            save();
        }
    }

    public synchronized void reloadFromDisk() {
        loaded = false;
        load();
    }

    public synchronized boolean isLoaded() {
        return loaded;
    }
}

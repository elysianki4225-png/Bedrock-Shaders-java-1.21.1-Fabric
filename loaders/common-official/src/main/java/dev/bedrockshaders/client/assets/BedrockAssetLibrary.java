package dev.bedrockshaders.client.assets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bedrockshaders.BedrockShadersMod;
import dev.bedrockshaders.bedrock.AtmosphereSettings;
import dev.bedrockshaders.bedrock.ColorGradingSettings;
import dev.bedrockshaders.bedrock.FogSettings;
import dev.bedrockshaders.bedrock.WaterSettings;
import dev.bedrockshaders.bedrock.LightingSettings;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads the Bedrock sample JSON (lighting, atmospherics, fog, colour grading) that
 * ships inside the mod jar, through Minecraft's resource manager. User resource
 * packs may override any single file exactly like they can on Bedrock - for example
 * a pack can ship {@code assets/bedrock_shaders/bedrock/fogs/my_biome_fog_setting.json}.
 *
 * <p>All parsing happens once per resource reload and never during a frame.</p>
 */
public final class BedrockAssetLibrary {
    private static final BedrockAssetLibrary INSTANCE = new BedrockAssetLibrary();

    /** Mojang sample files -> parsed settings. */
    private final Map<String, FogSettings> fog = new HashMap<>();
    private final Map<String, LightingSettings> lighting = new HashMap<>();
    private final Map<String, AtmosphereSettings> atmosphere = new HashMap<>();
    private final Map<String, ColorGradingSettings> grading = new HashMap<>();
    private WaterSettings water = WaterSettings.empty();

    /** Bedrock fog identifier (e.g. {@code minecraft:fog_plains}) -> sample file names. */
    private final Map<String, Binding> bindings = new HashMap<>();
    private final List<String> fogCandidates = new ArrayList<>();

    private boolean loaded;
    private int parsedFiles;
    private int loadErrors;

    private BedrockAssetLibrary() {
    }

    public static BedrockAssetLibrary get() {
        return INSTANCE;
    }

    /** Mod-relative path of a Bedrock asset, e.g. {@code bedrock/fogs/plains_fog_setting.json}. */
    public static String path(String path) {
        return AssetNames.ROOT + "/" + path;
    }

    /** A binding from a Bedrock fog identifier to the three asset files that define its look. */
    public record Binding(String fogFile, String lightingFile, String atmosphereFile, String gradingFile) {
    }

    public void reload(ResourceSource manager) {
        fog.clear();
        lighting.clear();
        atmosphere.clear();
        grading.clear();
        bindings.clear();
        fogCandidates.clear();
        water = WaterSettings.empty();
        parsedFiles = 0;
        loadErrors = 0;

        // Fog settings (distance + volumetric).
        String[] fogFiles = {
                AssetNames.FOG_DEFAULT, AssetNames.FOG_PLAINS, AssetNames.FOG_DESERT, AssetNames.FOG_SNOW,
                AssetNames.FOG_FOREST, AssetNames.FOG_SWAMP, AssetNames.FOG_MESA, AssetNames.FOG_NETHER,
                AssetNames.FOG_END, AssetNames.FOG_OCEAN, AssetNames.FOG_WARM_OCEAN, AssetNames.FOG_COLD_OCEAN,
                AssetNames.FOG_FROZEN_OCEAN, AssetNames.FOG_JUNGLE, AssetNames.FOG_TAIGA, AssetNames.FOG_BAMBOO,
                AssetNames.FOG_CHERRY, AssetNames.FOG_PALE, AssetNames.FOG_LUSH_CAVES, AssetNames.FOG_POWDER_SNOW,
                AssetNames.FOG_RIVER, AssetNames.FOG_BEACH, AssetNames.FOG_MUSHROOM, AssetNames.FOG_ROOFED,
                AssetNames.FOG_FLOWER, AssetNames.FOG_SAVANNA, AssetNames.FOG_BIRCH, AssetNames.FOG_CRIMSON,
                AssetNames.FOG_WARPED, AssetNames.FOG_SOULSAND, AssetNames.FOG_BASALT
        };
        String[] lightingFiles = {
                AssetNames.LIGHTING_GLOBAL, AssetNames.LIGHTING_DESERT, AssetNames.LIGHTING_SNOW,
                AssetNames.LIGHTING_NETHER, AssetNames.LIGHTING_END, AssetNames.LIGHTING_SWAMP,
                AssetNames.LIGHTING_MESA, AssetNames.LIGHTING_ROOFED, AssetNames.LIGHTING_PALE
        };
        String[] atmosphereFiles = {
                AssetNames.ATMOSPHERE_GLOBAL, AssetNames.ATMOSPHERE_DESERT, AssetNames.ATMOSPHERE_SNOW,
                AssetNames.ATMOSPHERE_NETHER, AssetNames.ATMOSPHERE_END, AssetNames.ATMOSPHERE_SWAMP,
                AssetNames.ATMOSPHERE_MESA, AssetNames.ATMOSPHERE_ROOFED, AssetNames.ATMOSPHERE_PALE
        };
        String[] gradingFiles = {
                AssetNames.GRADING_GLOBAL, AssetNames.GRADING_DESERT, AssetNames.GRADING_SNOW,
                AssetNames.GRADING_SWAMP, AssetNames.GRADING_MESA, AssetNames.GRADING_ROOFED,
                AssetNames.GRADING_PALE, AssetNames.GRADING_LUSH
        };

        for (String file : fogFiles) {
            JsonObject json = readJson(manager, file);
            if (json != null) {
                FogSettings settings = FogSettings.parse(json, null);
                fog.put(file, settings);
                fogCandidates.add(file);
                String name = file.substring("fogs/".length(), file.length() - "_fog_setting.json".length());
                // Map the Bedrock resource name to Bedrock's fog identifier convention.
                registerBinding("minecraft:fog_" + name, file, AssetNames.LIGHTING_GLOBAL,
                        AssetNames.ATMOSPHERE_GLOBAL, AssetNames.GRADING_GLOBAL);
            }
        }
        for (String file : lightingFiles) {
            JsonObject json = readJson(manager, file);
            if (json != null) {
                lighting.put(file, LightingSettings.parse(json, null));
            }
        }
        for (String file : atmosphereFiles) {
            JsonObject json = readJson(manager, file);
            if (json != null) {
                atmosphere.put(file, AtmosphereSettings.parse(json, null));
            }
        }
        for (String file : gradingFiles) {
            JsonObject json = readJson(manager, file);
            if (json != null) {
                grading.put(file, ColorGradingSettings.parse(json, null));
            }
        }
        discoverFogFiles(manager);
        loadWaterTable(manager);
        registerClimateBindings();

        loaded = true;
        BedrockShadersMod.LOGGER.info(
                "Bedrock assets ready: {} fog, {} lighting, {} atmospherics, {} colour grading, {} water biomes ({} files, {} errors)",
                fog.size(), lighting.size(), atmosphere.size(), grading.size(), water.size(), parsedFiles, loadErrors);
    }

    private void registerBinding(String fogId, String fogFile, String lightingFile, String atmosphereFile, String gradingFile) {
        bindings.put(fogId, new Binding(fogFile, lightingFile, atmosphereFile, gradingFile));
    }

    private void registerClimateBindings() {
        bind(AssetNames.FOG_DESERT, AssetNames.LIGHTING_DESERT, AssetNames.ATMOSPHERE_DESERT, AssetNames.GRADING_DESERT,
                "minecraft:fog_desert", "minecraft:fog_desert_hills", "minecraft:fog_mesa",
                "minecraft:fog_mesa_bryce", "minecraft:fog_mesa_plateau", "minecraft:fog_mesa_plateau_stone",
                "minecraft:fog_mesa_mutated");
        bind(AssetNames.FOG_SNOW, AssetNames.LIGHTING_SNOW, AssetNames.ATMOSPHERE_SNOW, AssetNames.GRADING_SNOW,
                "minecraft:fog_ice_plains", "minecraft:fog_ice_plains_spikes", "minecraft:fog_ice_mountains",
                "minecraft:fog_cold_beach", "minecraft:fog_cold_ocean", "minecraft:fog_deep_cold_ocean",
                "minecraft:fog_deep_frozen_ocean", "minecraft:fog_frozen_ocean", "minecraft:fog_frozen_river",
                "minecraft:fog_cold_taiga", "minecraft:fog_cold_taiga_hills", "minecraft:fog_cold_taiga_mutated",
                "minecraft:fog_powder_snow", "minecraft:fog_taiga", "minecraft:fog_taiga_hills",
                "minecraft:fog_taiga_mutated", "minecraft:fog_mega_taiga", "minecraft:fog_mega_taiga_hills",
                "minecraft:fog_mega_taiga_mutated", "minecraft:fog_mega_spruce_taiga");
        bind(AssetNames.FOG_FOREST, AssetNames.LIGHTING_GLOBAL, AssetNames.ATMOSPHERE_GLOBAL, AssetNames.GRADING_GLOBAL,
                "minecraft:fog_forest", "minecraft:fog_forest_hills", "minecraft:fog_birch_forest",
                "minecraft:fog_birch_forest_hills", "minecraft:fog_flower_forest", "minecraft:fog_jungle",
                "minecraft:fog_jungle_hills", "minecraft:fog_jungle_edge", "minecraft:fog_jungle_mutated",
                "minecraft:fog_bamboo_jungle", "minecraft:fog_bamboo_jungle_hills", "minecraft:fog_cherry_grove",
                "minecraft:fog_dappled_forest");
        bind(AssetNames.FOG_ROOFED, AssetNames.LIGHTING_ROOFED, AssetNames.ATMOSPHERE_ROOFED, AssetNames.GRADING_ROOFED,
                "minecraft:fog_roofed_forest", "minecraft:fog_roofed_forest_mutated");
        bind(AssetNames.FOG_SWAMP, AssetNames.LIGHTING_SWAMP, AssetNames.ATMOSPHERE_SWAMP, AssetNames.GRADING_SWAMP,
                "minecraft:fog_swampland", "minecraft:fog_swampland_mutated", "minecraft:fog_mangrove_swamp");
        bind(AssetNames.FOG_PALE, AssetNames.LIGHTING_PALE, AssetNames.ATMOSPHERE_PALE, AssetNames.GRADING_PALE,
                "minecraft:fog_pale_garden");
        bind(AssetNames.FOG_LUSH_CAVES, AssetNames.LIGHTING_GLOBAL, AssetNames.ATMOSPHERE_GLOBAL, AssetNames.GRADING_LUSH,
                "minecraft:fog_lush_caves", "minecraft:fog_dripstone_caves", "minecraft:fog_deep_dark");
        bind(AssetNames.FOG_NETHER, AssetNames.LIGHTING_NETHER, AssetNames.ATMOSPHERE_NETHER, AssetNames.GRADING_GLOBAL,
                "minecraft:fog_hell", "minecraft:fog_crimson_forest", "minecraft:fog_warped_forest",
                "minecraft:fog_soulsand_valley", "minecraft:fog_basalt_deltas");
        bind(AssetNames.FOG_END, AssetNames.LIGHTING_END, AssetNames.ATMOSPHERE_END, AssetNames.GRADING_GLOBAL,
                "minecraft:fog_the_end");
        bind(AssetNames.FOG_OCEAN, AssetNames.LIGHTING_GLOBAL, AssetNames.ATMOSPHERE_GLOBAL, AssetNames.GRADING_GLOBAL,
                "minecraft:fog_ocean", "minecraft:fog_deep_ocean", "minecraft:fog_warm_ocean",
                "minecraft:fog_lukewarm_ocean", "minecraft:fog_deep_lukewarm_ocean", "minecraft:fog_warm_ocean_deep",
                "minecraft:fog_river", "minecraft:fog_beach");
        bind(AssetNames.FOG_PLAINS, AssetNames.LIGHTING_GLOBAL, AssetNames.ATMOSPHERE_GLOBAL, AssetNames.GRADING_GLOBAL,
                "minecraft:fog_plains", "minecraft:fog_sunflower_plains", "minecraft:fog_savanna",
                "minecraft:fog_savanna_mutated", "minecraft:fog_savanna_plateau", "minecraft:fog_mushroom_island",
                "minecraft:fog_mushroom_island_shore", "minecraft:fog_stone_beach", "minecraft:fog_extreme_hills",
                "minecraft:fog_extreme_hills_edge", "minecraft:fog_extreme_hills_mutated",
                "minecraft:fog_extreme_hills_plus_trees", "minecraft:fog_extreme_hills_plus_trees_mutated");
    }

    private void bind(String fogFile, String lightingFile, String atmosphereFile, String gradingFile, String... fogIds) {
        for (String fogId : fogIds) {
            bindings.put(fogId, new Binding(fogFile, lightingFile, atmosphereFile, gradingFile));
        }
    }

    /**
     * Loads Bedrock's per-biome water colours ({@code biomes_client.json}). Optional: the
     * shipped mod has no water table, so an absent file is not an error.
     */
    private void loadWaterTable(ResourceSource manager) {
        InputStream stream = manager.open(path("water/biomes_client.json"));
        if (stream == null) {
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            water = WaterSettings.parse(json);
            parsedFiles++;
            BedrockShadersMod.LOGGER.info("Bedrock water table loaded: {} biome entries", water.size());
        } catch (IOException | RuntimeException e) {
            loadErrors++;
            BedrockShadersMod.LOGGER.warn("Failed to parse Bedrock water table: {}", e.getMessage());
        }
    }

    /** Bedrock water appearance; empty when no pack supplied {@code biomes_client.json}. */
    public WaterSettings water() {
        return water;
    }

    private JsonObject readJson(ResourceSource manager, String relative) {
        InputStream stream = manager.open(path(relative));
        if (stream == null) {
            loadErrors++;
            BedrockShadersMod.LOGGER.warn("Bedrock asset missing from resource packs: {}", path(relative));
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            parsedFiles++;
            return json;
        } catch (IOException | RuntimeException e) {
            loadErrors++;
            BedrockShadersMod.LOGGER.warn("Failed to parse Bedrock asset {}: {}", path(relative), e.getMessage());
            return null;
        }
    }

    /**
     * Discovers every {@code *_fog_setting.json} present in the resource packs, so
     * users can drop additional Bedrock fog files into the mod's asset folder.
     */
    private void discoverFogFiles(ResourceSource manager) {
        try {
            TreeMap<String, String> sorted = new TreeMap<>();
            for (String found : manager.list("bedrock/fogs")) {
                if (found.endsWith("_fog_setting.json")) {
                    sorted.put(found, found);
                }
            }
            for (String found : sorted.values()) {
                String fileName = found.substring("bedrock/fogs/".length());
                String path = "fogs/" + fileName;
                if (fog.containsKey(path)) {
                    continue;
                }
                JsonObject json = readJson(manager, path);
                if (json == null) {
                    continue;
                }
                fog.put(path, FogSettings.parse(json, null));
                String name = fileName.substring(0, fileName.length() - "_fog_setting.json".length());
                bindings.putIfAbsent("minecraft:fog_" + name,
                        new Binding(path, AssetNames.LIGHTING_GLOBAL, AssetNames.ATMOSPHERE_GLOBAL, AssetNames.GRADING_GLOBAL));
            }
        } catch (RuntimeException e) {
            BedrockShadersMod.LOGGER.warn("Could not scan for extra Bedrock fog files: {}", e.getMessage());
        }
    }

    /** All parsed Bedrock fog identifiers, sorted - used by the effects browser. */
    public java.util.List<String> fogIdentifiers() {
        return new java.util.ArrayList<>(new TreeMap<>(bindings).keySet());
    }

    public java.util.List<String> fogFiles() {
        return new java.util.ArrayList<>(new TreeMap<>(fog).keySet());
    }

    public Binding binding(String fogId) {
        Binding binding = bindings.get(fogId);
        return binding != null ? binding : bindings.get("minecraft:fog_plains");
    }

    public FogSettings fog(String fogId) {
        Binding binding = binding(fogId);
        return fog.getOrDefault(binding == null ? AssetNames.FOG_DEFAULT : binding.fogFile(), fallbackFog());
    }

    public LightingSettings lighting(String fogId) {
        Binding binding = binding(fogId);
        return lighting.getOrDefault(binding == null ? AssetNames.LIGHTING_GLOBAL : binding.lightingFile(), fallbackLighting());
    }

    public AtmosphereSettings atmosphere(String fogId) {
        Binding binding = binding(fogId);
        return atmosphere.getOrDefault(binding == null ? AssetNames.ATMOSPHERE_GLOBAL : binding.atmosphereFile(), fallbackAtmosphere());
    }

    public ColorGradingSettings grading(String fogId) {
        Binding binding = binding(fogId);
        return grading.getOrDefault(binding == null ? AssetNames.GRADING_GLOBAL : binding.gradingFile(), fallbackGrading());
    }

    public FogSettings fogByPath(String path) {
        return fog.getOrDefault(path, fallbackFog());
    }

    public LightingSettings lightingByPath(String path) {
        return lighting.getOrDefault(path, fallbackLighting());
    }

    public AtmosphereSettings atmosphereByPath(String path) {
        return atmosphere.getOrDefault(path, fallbackAtmosphere());
    }

    public ColorGradingSettings gradingByPath(String path) {
        return grading.getOrDefault(path, fallbackGrading());
    }

    private FogSettings fallbackFog() {
        FogSettings settings = fog.get(AssetNames.FOG_DEFAULT);
        return settings != null ? settings : new FogSettings();
    }

    private LightingSettings fallbackLighting() {
        LightingSettings settings = lighting.get(AssetNames.LIGHTING_GLOBAL);
        return settings != null ? settings : new LightingSettings();
    }

    private AtmosphereSettings fallbackAtmosphere() {
        AtmosphereSettings settings = atmosphere.get(AssetNames.ATMOSPHERE_GLOBAL);
        return settings != null ? settings : new AtmosphereSettings();
    }

    private ColorGradingSettings fallbackGrading() {
        ColorGradingSettings settings = grading.get(AssetNames.GRADING_GLOBAL);
        return settings != null ? settings : new ColorGradingSettings();
    }

    public boolean isLoaded() {
        return loaded;
    }

    public int parsedFiles() {
        return parsedFiles;
    }

    public int loadErrors() {
        return loadErrors;
    }

    public List<String> fogCandidates() {
        return fogCandidates;
    }

    /**
     * Marks the library as loaded. The actual loading happens in each loader's resource
     * reload listener, which owns the {@link ResourceSource}.
     */
    public void ensureLoaded() {
        // Loading is driven by the platform reload listener; nothing to do here.
    }
}

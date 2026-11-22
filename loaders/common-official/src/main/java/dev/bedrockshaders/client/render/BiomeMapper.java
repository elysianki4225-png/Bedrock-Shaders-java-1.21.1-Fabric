package dev.bedrockshaders.client.render;

import java.util.Locale;

/**
 * Maps a Minecraft Java biome to the closest Bedrock fog identifier.
 *
 * <p>Bedrock identifies fog by {@code minecraft:fog_<name>}; the
 * {@link dev.bedrockshaders.client.assets.BedrockAssetLibrary} binds those ids to the
 * matching Bedrock JSON. Java biome ids are mostly identical, so the mapping is a lookup
 * table with climate fallbacks for modded biomes.</p>
 *
 * <p>Deliberately Minecraft-free: it takes plain strings and climate values so the same
 * logic serves Fabric, Quilt, Forge and NeoForge (whose biome classes differ).</p>
 */
public final class BiomeMapper {
    private BiomeMapper() {
    }

    /**
     * @param biomeId    Java biome id, e.g. {@code minecraft:plains} (may be empty)
     * @param dimensionKey dimension id, e.g. {@code minecraft:the_nether}
     * @param temperature biome temperature, used for modded biomes
     * @param hasPrecipitation whether the biome rains/snows
     */
    public static String fogIdFor(String biomeId, String dimensionKey, float temperature, boolean hasPrecipitation) {
        String dimension = dimensionKey == null ? "" : dimensionKey;
        String path = pathOf(biomeId);

        if ("minecraft:the_nether".equals(dimension)) {
            if (path.contains("soul")) {
                return "minecraft:fog_soulsand_valley";
            }
            if (path.contains("crimson")) {
                return "minecraft:fog_crimson_forest";
            }
            if (path.contains("warped")) {
                return "minecraft:fog_warped_forest";
            }
            if (path.contains("basalt")) {
                return "minecraft:fog_basalt_deltas";
            }
            return "minecraft:fog_hell";
        }
        if ("minecraft:the_end".equals(dimension)) {
            return "minecraft:fog_the_end";
        }

        if (path.isEmpty()) {
            return climateFallback(temperature, hasPrecipitation);
        }
        String mapped = mapOverworld(path);
        if (mapped != null) {
            return mapped;
        }
        return climateFallback(temperature, hasPrecipitation);
    }

    /** Splits {@code minecraft:plains} into {@code plains}; tolerates a bare path. */
    private static String pathOf(String biomeId) {
        if (biomeId == null || biomeId.isEmpty()) {
            return "";
        }
        String id = biomeId.toLowerCase(Locale.ROOT);
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private static String mapOverworld(String path) {
        return switch (path) {
            case "plains", "sunflower_plains" -> "minecraft:fog_plains";
            case "desert", "desert_hills", "badlands", "eroded_badlands", "wooded_badlands" -> "minecraft:fog_desert";
            case "snowy_plains", "snowy_taiga", "ice_spikes", "snowy_beach", "frozen_river",
                 "frozen_ocean", "deep_frozen_ocean", "snowy_slopes", "jagged_peaks", "frozen_peaks",
                 "grove", "powder_snow_forest" -> "minecraft:fog_ice_plains";
            case "forest", "flower_forest", "windswept_forest", "dark_forest", "birch_forest",
                 "old_growth_birch_forest" -> "minecraft:fog_forest";
            case "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga" -> "minecraft:fog_taiga";
            case "jungle", "sparse_jungle", "bamboo_jungle", "jungle_edge" -> "minecraft:fog_jungle";
            case "swamp", "mangrove_swamp" -> "minecraft:fog_swampland";
            case "pale_garden" -> "minecraft:fog_pale_garden";
            case "cherry_grove", "meadow", "sunflower_fields" -> "minecraft:fog_cherry_grove";
            case "lush_caves", "dripstone_caves", "deep_dark" -> "minecraft:fog_lush_caves";
            case "warm_ocean" -> "minecraft:fog_warm_ocean";
            case "cold_ocean", "deep_cold_ocean" -> "minecraft:fog_cold_ocean";
            case "lukewarm_ocean", "deep_lukewarm_ocean" -> "minecraft:fog_lukewarm_ocean";
            case "ocean", "deep_ocean" -> "minecraft:fog_ocean";
            case "river" -> "minecraft:fog_river";
            case "beach", "stony_shore" -> "minecraft:fog_beach";
            case "mushroom_fields" -> "minecraft:fog_mushroom_island";
            case "savanna", "savanna_plateau", "windswept_savanna" -> "minecraft:fog_savanna";
            case "windswept_hills", "windswept_gravelly_hills", "stony_peaks" -> "minecraft:fog_extreme_hills";
            default -> null;
        };
    }

    private static String climateFallback(float temperature, boolean hasPrecipitation) {
        if (temperature <= 0.15F) {
            return "minecraft:fog_ice_plains";
        }
        if (temperature >= 0.95F) {
            return "minecraft:fog_desert";
        }
        if (hasPrecipitation) {
            return "minecraft:fog_forest";
        }
        return "minecraft:fog_plains";
    }
}

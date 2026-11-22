package dev.bedrockshaders.client.render;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Maps a Minecraft Java biome to the closest Bedrock fog identifier.
 *
 * <p>Bedrock identifies fog by {@code minecraft:fog_<name>}; the {@link dev.bedrockshaders.client.assets.BedrockAssetLibrary}
 * binds those ids to the matching Mojang sample files. Java biome names are mostly
 * identical, so the mapping is primarily a prefix/alias table with fallbacks by
 * climate and dimension for modded biomes.</p>
 */
public final class BiomeMapper {
    private BiomeMapper() {
    }

    public static String fogIdFor(ClientWorld world, RegistryEntry<Biome> biomeEntry, BlockPos pos) {
        if (world.getRegistryKey() == World.NETHER) {
            String path = pathOf(biomeEntry);
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
        if (world.getRegistryKey() == World.END) {
            return "minecraft:fog_the_end";
        }

        String path = pathOf(biomeEntry);
        if (path.isEmpty()) {
            return climateFallback(world, pos);
        }
        String mapped = mapOverworld(path);
        if (mapped != null) {
            return mapped;
        }
        return climateFallback(world, pos);
    }

    private static String pathOf(RegistryEntry<Biome> biomeEntry) {
        if (biomeEntry == null || biomeEntry.getKey().isEmpty()) {
            return "";
        }
        return biomeEntry.getKey().get().getValue().getPath();
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
            case "windswept_hills", "windswept_gravelly_hills", "stony_peaks", "stony_shore2" -> "minecraft:fog_extreme_hills";
            default -> null;
        };
    }

    private static String climateFallback(ClientWorld world, BlockPos pos) {
        Biome biome = world.getBiome(pos).value();
        float temperature = biome.getTemperature();
        if (temperature <= 0.15F) {
            return "minecraft:fog_ice_plains";
        }
        if (temperature >= 0.95F) {
            return "minecraft:fog_desert";
        }
        if (biome.hasPrecipitation()) {
            return "minecraft:fog_forest";
        }
        return "minecraft:fog_plains";
    }
}

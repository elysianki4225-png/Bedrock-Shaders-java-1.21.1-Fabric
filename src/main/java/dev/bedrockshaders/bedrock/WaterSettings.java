package dev.bedrockshaders.bedrock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.bedrockshaders.util.ColorUtil;
import dev.bedrockshaders.util.Mth;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bedrock water appearance, read from {@code biomes_client.json}'s
 * {@code water_surface_color}, {@code water_fog_color} and
 * {@code water_surface_transparency} entries.
 *
 * <p>Bedrock keys these per biome; Java keys them per biome id as well, so the parsed
 * table is queried with the same {@code minecraft:plains} style id the fog mapper uses,
 * with {@code default} as the fallback entry.</p>
 */
public final class WaterSettings {
    public static final String DEFAULT_KEY = "default";

    public static final class Entry {
        public float[] surfaceColor = new float[] {0.12F, 0.42F, 0.62F};
        public float[] fogColor = new float[] {0.09F, 0.29F, 0.44F};
        public float transparency = 0.65F;
        public boolean hasSurfaceColor;
        public boolean hasFogColor;
        public boolean hasTransparency;
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public static WaterSettings empty() {
        return new WaterSettings();
    }

    /** Parses a {@code biomes_client.json} document; unknown structures yield an empty table. */
    public static WaterSettings parse(JsonObject root) {
        WaterSettings out = new WaterSettings();
        if (root == null) {
            return out;
        }
        JsonObject biomes = root.has("biomes") && root.get("biomes").isJsonObject()
                ? root.getAsJsonObject("biomes") : root;
        for (Map.Entry<String, JsonElement> entry : biomes.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject biome = entry.getValue().getAsJsonObject();
            Entry parsed = new Entry();
            JsonElement surface = biome.get("water_surface_color");
            if (surface != null && surface.isJsonPrimitive()) {
                parsed.surfaceColor = ColorUtil.parseHexLinear(surface.getAsString());
                parsed.hasSurfaceColor = true;
            }
            JsonElement fog = biome.get("water_fog_color");
            if (fog != null && fog.isJsonPrimitive()) {
                parsed.fogColor = ColorUtil.parseHexLinear(fog.getAsString());
                parsed.hasFogColor = true;
            }
            JsonElement transparency = biome.get("water_surface_transparency");
            if (transparency != null && transparency.isJsonPrimitive()) {
                parsed.transparency = Mth.clamp(transparency.getAsFloat(), 0.0F, 1.0F);
                parsed.hasTransparency = true;
            }
            if (parsed.hasSurfaceColor || parsed.hasFogColor || parsed.hasTransparency) {
                out.entries.put(normaliseKey(entry.getKey()), parsed);
            }
        }
        return out;
    }

    /** {@code minecraft:plains}, {@code plains} and {@code Plains} all resolve to the same entry. */
    private static String normaliseKey(String key) {
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        int colon = lower.indexOf(':');
        return colon >= 0 ? lower.substring(colon + 1) : lower;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /** Looks up a biome; falls back to {@code default} and then to the built-in look. */
    public Entry entryFor(String biomeId) {
        if (biomeId == null) {
            return fallback();
        }
        Entry entry = entries.get(normaliseKey(biomeId));
        return entry != null ? entry : fallback();
    }

    private Entry fallback() {
        Entry entry = entries.get(DEFAULT_KEY);
        return entry != null ? entry : new Entry();
    }

    /** Copies the per-biome look into the frame's water colour slots. */
    public void apply(String biomeId, float[] surfaceOut, float[] fogOut, float[] transparencyOut) {
        Entry entry = entryFor(biomeId);
        if (surfaceOut != null) {
            System.arraycopy(entry.surfaceColor, 0, surfaceOut, 0, 3);
        }
        if (fogOut != null) {
            System.arraycopy(entry.fogColor, 0, fogOut, 0, 3);
        }
        if (transparencyOut != null && transparencyOut.length > 0) {
            transparencyOut[0] = entry.transparency;
        }
    }
}

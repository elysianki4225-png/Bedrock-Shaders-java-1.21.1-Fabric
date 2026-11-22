package dev.bedrockshaders.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Parsed {@code minecraft:color_grading_settings}. The values are applied in a
 * dedicated grading pass between the HDR scene and the tone mapper, mirroring
 * Bedrock's "midtones / highlights / shadows" grading model.
 */
public final class ColorGradingSettings {
    public String identifier = "minecraft:default_color_grading";
    public float[] midtoneContrast = new float[] {1.0F, 1.0F, 1.0F};
    public float[] midtoneGain = new float[] {1.0F, 1.0F, 1.0F};
    public float[] midtoneGamma = new float[] {1.0F, 1.0F, 1.0F};
    public float[] midtoneOffset = new float[] {0.0F, 0.0F, 0.0F};
    public float[] midtoneSaturation = new float[] {1.0F, 1.0F, 1.0F};
    public boolean temperatureEnabled = false;
    public float temperature = 6500.0F;
    public boolean gradeHighlights = false;
    public boolean gradeShadows = false;
    public String toneMappingOperator = "generic";

    public static ColorGradingSettings parse(JsonObject root, ColorGradingSettings fallback) {
        ColorGradingSettings out = fallback == null ? new ColorGradingSettings() : copy(fallback);
        if (root == null) {
            return out;
        }
        JsonObject settings = Curry.obj(root, "minecraft:color_grading_settings");
        if (settings == null) {
            return out;
        }
        JsonObject description = Curry.obj(settings, "description");
        if (description != null && description.has("identifier")) {
            out.identifier = description.get("identifier").getAsString();
        }
        JsonObject grading = Curry.obj(settings, "color_grading");
        if (grading != null) {
            out.gradeShadows = grading.has("shadows");
            out.gradeHighlights = grading.has("highlights");
            out.midtoneContrast = vec3(grading, "midtones", "contrast", out.midtoneContrast);
            out.midtoneGain = vec3(grading, "midtones", "gain", out.midtoneGain);
            out.midtoneGamma = vec3(grading, "midtones", "gamma", out.midtoneGamma);
            out.midtoneOffset = vec3(grading, "midtones", "offset", out.midtoneOffset);
            out.midtoneSaturation = vec3(grading, "midtones", "saturation", out.midtoneSaturation);
            JsonObject temperature = Curry.obj(grading, "temperature");
            if (temperature != null) {
                out.temperatureEnabled = temperature.has("enabled") && temperature.get("enabled").getAsBoolean();
                out.temperature = CurveParser.getFloat(temperature, "temperature", out.temperature);
            }
        }
        JsonObject toneMapping = Curry.obj(settings, "tone_mapping");
        if (toneMapping != null && toneMapping.has("operator")) {
            out.toneMappingOperator = toneMapping.get("operator").getAsString();
        }
        return out;
    }

    private static float[] vec3(JsonObject parent, String group, String key, float[] fallback) {
        JsonObject g = Curry.obj(parent, group);
        if (g == null || !g.has(key)) {
            return fallback;
        }
        return CurveParser.readVec3(g.get(key), fallback);
    }

    private static ColorGradingSettings copy(ColorGradingSettings other) {
        ColorGradingSettings out = new ColorGradingSettings();
        out.identifier = other.identifier;
        out.midtoneContrast = other.midtoneContrast;
        out.midtoneGain = other.midtoneGain;
        out.midtoneGamma = other.midtoneGamma;
        out.midtoneOffset = other.midtoneOffset;
        out.midtoneSaturation = other.midtoneSaturation;
        out.temperatureEnabled = other.temperatureEnabled;
        out.temperature = other.temperature;
        out.gradeHighlights = other.gradeHighlights;
        out.gradeShadows = other.gradeShadows;
        out.toneMappingOperator = other.toneMappingOperator;
        return out;
    }
}

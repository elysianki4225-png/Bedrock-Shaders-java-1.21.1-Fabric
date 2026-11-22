package dev.bedrockshaders.bedrock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Parsed {@code minecraft:lighting_settings} from Bedrock's {@code lighting/*.json}.
 *
 * <p>Bedrock drives its Deferred Rendering Pipeline with physically based illuminance
 * values (lux). {@link #sunIlluminance} keeps Mojang's original numbers (0..100) and
 * {@link #keyLightIlluminance} exposes the normalised 0..1 version used by this pipeline.</p>
 */
public final class LightingSettings {
    /** Default: Mojang's {@code lighting/global.json}. */
    public String identifier = "minecraft:default_lighting";
    public FloatCurve sunIlluminance = defaultSunIlluminance();
    public ColorCurve sunColor = defaultSunColor();
    public FloatCurve moonIlluminance = defaultMoonIlluminance();
    public ColorCurve moonColor = defaultMoonColor();
    public float orbitalOffsetDegrees = 0.0F;
    public float flashIlluminance = 10.0F;
    public float[] flashColor = new float[] {0.79F, 0.13F, 1.0F};
    public float emissiveDesaturation = 0.0F;
    public float[] ambientColor = new float[] {1.0F, 1.0F, 1.0F};
    public float ambientIlluminance = 0.02F;
    public float skyIntensity = 1.0F;

    /** Sun illuminance normalised to a 0..1 range (Mojang's daylight peak is 100 lux). */
    public float sunIntensity(float sunAngle) {
        float lux = sunIlluminance.isEmpty() ? 0.0F : sunIlluminance.get(sunAngle);
        return Math.min(lux, 100.0F) / 100.0F;
    }

    public float moonIntensity(float sunAngle) {
        return moonIlluminance.isEmpty() ? 0.0F : moonIlluminance.get(sunAngle);
    }

    public static LightingSettings parse(JsonObject root, LightingSettings fallback) {
        LightingSettings out = fallback == null ? new LightingSettings() : copy(fallback);
        if (root == null) {
            return out;
        }
        JsonObject settings = Curry.obj(root, "minecraft:lighting_settings");
        if (settings == null) {
            return out;
        }
        JsonObject description = Curry.obj(settings, "description");
        if (description != null && description.has("identifier")) {
            out.identifier = description.get("identifier").getAsString();
        }
        JsonObject directional = Curry.obj(settings, "directional_lights");
        if (directional != null) {
            JsonObject orbital = Curry.obj(directional, "orbital");
            JsonObject container = orbital != null ? orbital : directional;
            out.orbitalOffsetDegrees = CurveParser.getFloat(container, "orbital_offset_degrees", out.orbitalOffsetDegrees);
            JsonObject sun = Curry.obj(container, "sun");
            if (sun != null) {
                out.sunIlluminance = CurveParser.floatCurve(Curry.obj(sun, "illuminance"), 0.0F);
                ColorCurve color = CurveParser.colorCurve(Curry.obj(sun, "color"));
                if (!color.isEmpty()) {
                    out.sunColor = color;
                }
            }
            JsonObject moon = Curry.obj(container, "moon");
            if (moon != null) {
                out.moonIlluminance = CurveParser.floatCurve(Curry.obj(moon, "illuminance"), 0.0F);
                ColorCurve color = CurveParser.colorCurve(Curry.obj(moon, "color"));
                if (!color.isEmpty()) {
                    out.moonColor = color;
                }
            }
            JsonObject flash = Curry.obj(directional, "flash");
            if (flash != null) {
                out.flashIlluminance = CurveParser.getFloat(flash, "illuminance", out.flashIlluminance);
                out.flashColor = CurveParser.readColor(flash.get("color"), out.flashColor);
            }
        }
        JsonObject emissive = Curry.obj(settings, "emissive");
        if (emissive != null) {
            out.emissiveDesaturation = CurveParser.getFloat(emissive, "desaturation", out.emissiveDesaturation);
        }
        JsonObject ambient = Curry.obj(settings, "ambient");
        if (ambient != null) {
            JsonElement color = ambient.get("color");
            if (color != null) {
                out.ambientColor = CurveParser.readColor(color, out.ambientColor);
            }
            out.ambientIlluminance = CurveParser.getFloat(ambient, "illuminance", out.ambientIlluminance);
        }
        JsonObject sky = Curry.obj(settings, "sky");
        if (sky != null) {
            out.skyIntensity = CurveParser.getFloat(sky, "intensity", out.skyIntensity);
        }
        return out;
    }

    /** Mojang's {@code lighting/global.json} sun illuminance (lux) keyframes. */
    public static FloatCurve defaultSunIlluminance() {
        return new FloatCurve(
                new float[] {0.0F, 0.05F, 0.282F, 0.2915F, 0.292F, 0.709F, 0.719F, 0.95F, 1.0F},
                new float[] {100.0F, 100.0F, 1.0F, 0.01F, 0.0F, 0.0F, 1.0F, 100.0F, 100.0F},
                0.0F);
    }

    public static FloatCurve defaultMoonIlluminance() {
        return new FloatCurve(
                new float[] {0.0F, 0.2F, 0.225F, 0.735F, 0.75F, 1.0F},
                new float[] {0.0F, 0.0F, 0.4F, 0.4F, 0.0F, 0.0F},
                0.0F);
    }

    public static ColorCurve defaultSunColor() {
        return new ColorCurve(
                new float[] {0.140811F, 0.216944F, 0.242908F, 0.269504F, 0.314561F, 0.506998F, 0.717949F, 0.801062F, 1.0F},
                new float[][] {
                        dev.bedrockshaders.util.ColorUtil.rgbToLinear(new float[] {255.0F, 173.0F, 81.0F}),
                        dev.bedrockshaders.util.ColorUtil.rgbToLinear(new float[] {255.0F, 155.0F, 82.0F}),
                        dev.bedrockshaders.util.ColorUtil.rgbToLinear(new float[] {255.0F, 127.0F, 0.0F}),
                        dev.bedrockshaders.util.ColorUtil.rgbToLinear(new float[] {255.0F, 127.0F, 0.0F}),
                        dev.bedrockshaders.util.ColorUtil.rgbToLinear(new float[] {255.0F, 154.0F, 154.0F}),
                        dev.bedrockshaders.util.ColorUtil.rgbToLinear(new float[] {255.0F, 105.0F, 0.0F}),
                        dev.bedrockshaders.util.ColorUtil.rgbToLinear(new float[] {255.0F, 133.0F, 133.0F}),
                        dev.bedrockshaders.util.ColorUtil.rgbToLinear(new float[] {255.0F, 173.0F, 81.0F}),
                        dev.bedrockshaders.util.ColorUtil.rgbToLinear(new float[] {255.0F, 227.0F, 215.0F})
                },
                new float[] {1.0F, 1.0F, 1.0F});
    }

    public static ColorCurve defaultMoonColor() {
        float[] linear = dev.bedrockshaders.util.ColorUtil.rgbToLinear(new float[] {163.0F, 182.0F, 255.0F});
        return new ColorCurve(new float[0], new float[0][], linear);
    }

    private static LightingSettings copy(LightingSettings other) {
        LightingSettings out = new LightingSettings();
        out.identifier = other.identifier;
        out.sunIlluminance = other.sunIlluminance;
        out.sunColor = other.sunColor;
        out.moonIlluminance = other.moonIlluminance;
        out.moonColor = other.moonColor;
        out.orbitalOffsetDegrees = other.orbitalOffsetDegrees;
        out.flashIlluminance = other.flashIlluminance;
        out.flashColor = other.flashColor;
        out.emissiveDesaturation = other.emissiveDesaturation;
        out.ambientColor = other.ambientColor;
        out.ambientIlluminance = other.ambientIlluminance;
        out.skyIntensity = other.skyIntensity;
        return out;
    }
}

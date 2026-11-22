package dev.bedrockshaders.bedrock;

import com.google.gson.JsonObject;

/**
 * Parsed {@code minecraft:atmosphere_settings} (Bedrock {@code atmospherics/*.json}).
 * Drives the analytical BRDF-free sky pass: Rayleigh / Mie scattering strengths,
 * sun glare, zenith and horizon gradients.
 */
public final class AtmosphereSettings {
    public String identifier = "minecraft:default_atmospherics";
    public FloatCurve rayleighStrength = FloatCurve.EMPTY;
    public FloatCurve sunMieStrength = FloatCurve.EMPTY;
    public FloatCurve moonMieStrength = FloatCurve.EMPTY;
    public FloatCurve sunGlareShape = FloatCurve.EMPTY;
    public ColorCurve skyZenithColor = ColorCurve.BLACK;
    public ColorCurve skyHorizonColor = ColorCurve.BLACK;
    public FloatCurve horizonMin = FloatCurve.EMPTY;
    public FloatCurve horizonStart = FloatCurve.EMPTY;
    public FloatCurve horizonMieStart = FloatCurve.EMPTY;
    public FloatCurve horizonMax = FloatCurve.EMPTY;

    public static AtmosphereSettings parse(JsonObject root, AtmosphereSettings fallback) {
        AtmosphereSettings out = fallback == null ? new AtmosphereSettings() : copy(fallback);
        if (root == null) {
            return out;
        }
        JsonObject settings = Curry.obj(root, "minecraft:atmosphere_settings");
        if (settings == null) {
            return out;
        }
        JsonObject description = Curry.obj(settings, "description");
        if (description != null && description.has("identifier")) {
            out.identifier = description.get("identifier").getAsString();
        }
        FloatCurve rayleigh = CurveParser.floatCurve(Curry.obj(settings, "rayleigh_strength"), 0.0F);
        if (!rayleigh.isEmpty()) {
            out.rayleighStrength = rayleigh;
        }
        FloatCurve sunMie = CurveParser.floatCurve(Curry.obj(settings, "sun_mie_strength"), 0.0F);
        if (!sunMie.isEmpty()) {
            out.sunMieStrength = sunMie;
        }
        FloatCurve moonMie = CurveParser.floatCurve(Curry.obj(settings, "moon_mie_strength"), 0.0F);
        if (!moonMie.isEmpty()) {
            out.moonMieStrength = moonMie;
        }
        FloatCurve glare = CurveParser.floatCurve(Curry.obj(settings, "sun_glare_shape"), 0.0F);
        if (!glare.isEmpty()) {
            out.sunGlareShape = glare;
        }
        ColorCurve zenith = CurveParser.colorCurve(Curry.obj(settings, "sky_zenith_color"));
        if (!zenith.isEmpty()) {
            out.skyZenithColor = zenith;
        }
        ColorCurve horizon = CurveParser.colorCurve(Curry.obj(settings, "sky_horizon_color"));
        if (!horizon.isEmpty()) {
            out.skyHorizonColor = horizon;
        }
        JsonObject stops = Curry.obj(settings, "horizon_blend_stops");
        if (stops != null) {
            out.horizonMin = CurveParser.floatCurve(Curry.obj(stops, "min"), 0.0F);
            out.horizonStart = CurveParser.floatCurve(Curry.obj(stops, "start"), 0.0F);
            out.horizonMieStart = CurveParser.floatCurve(Curry.obj(stops, "mie_start"), 0.0F);
            out.horizonMax = CurveParser.floatCurve(Curry.obj(stops, "max"), 0.0F);
        }
        return out;
    }

    private static AtmosphereSettings copy(AtmosphereSettings other) {
        AtmosphereSettings out = new AtmosphereSettings();
        out.identifier = other.identifier;
        out.rayleighStrength = other.rayleighStrength;
        out.sunMieStrength = other.sunMieStrength;
        out.moonMieStrength = other.moonMieStrength;
        out.sunGlareShape = other.sunGlareShape;
        out.skyZenithColor = other.skyZenithColor;
        out.skyHorizonColor = other.skyHorizonColor;
        out.horizonMin = other.horizonMin;
        out.horizonStart = other.horizonStart;
        out.horizonMieStart = other.horizonMieStart;
        out.horizonMax = other.horizonMax;
        return out;
    }
}

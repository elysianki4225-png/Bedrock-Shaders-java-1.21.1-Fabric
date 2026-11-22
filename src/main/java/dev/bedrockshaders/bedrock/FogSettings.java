package dev.bedrockshaders.bedrock;

import com.google.gson.JsonObject;

/**
 * Parsed {@code minecraft:fog_settings}. Bedrock stores fog in two places:
 * atmospheric "distance" fog (air / water / weather) and optional "volumetric"
 * fog with density and media coefficients. Both are supported here.
 */
public final class FogSettings {
    public String identifier = "minecraft:fog_default";

    public boolean hasAir = false;
    public float airStart = 0.92F;
    public float airEnd = 1.0F;
    public float[] airColor = new float[] {0.65F, 0.82F, 1.0F};
    public String airDistanceType = "render";

    public boolean hasWater = false;
    public float waterStart = 0.0F;
    public float waterEnd = 60.0F;
    public float[] waterColor = new float[] {0.26F, 0.68F, 0.96F};
    public String waterDistanceType = "fixed";

    public boolean hasWeather = false;
    public float weatherStart = 0.23F;
    public float weatherEnd = 0.7F;
    public float[] weatherColor = new float[] {0.4F, 0.4F, 0.4F};

    public boolean hasLava = false;
    public float lavaStart = 0.0F;
    public float lavaEnd = 0.64F;
    public float[] lavaColor = new float[] {0.6F, 0.1F, 0.0F};

    public boolean hasLavaResistance = false;
    public float lavaResistanceStart = 2.0F;
    public float lavaResistanceEnd = 4.0F;

    public float volumetricAirMaxDensity = 0.05F;
    public float volumetricAirZeroHeight = 320.0F;
    public float volumetricAirMaxDensityHeight = 320.0F;
    public float[] airScattering = new float[] {0.06F, 0.06F, 0.06F};
    public float[] airAbsorption = new float[] {0.0F, 0.0F, 0.0F};
    public float airHeneyGreensteinG = 0.4F;

    public boolean hasWeatherVolumetric = false;
    public float weatherVolumetricDensity = 0.05F;
    public float[] weatherScattering = new float[] {0.06F, 0.06F, 0.06F};

    private boolean fogColorOverridden = false;

    public static FogSettings parse(JsonObject root, FogSettings fallback) {
        FogSettings out = fallback == null ? new FogSettings() : copy(fallback);
        if (root == null) {
            return out;
        }
        JsonObject settings = Curry.obj(root, "minecraft:fog_settings");
        if (settings == null) {
            return out;
        }
        JsonObject description = Curry.obj(settings, "description");
        if (description != null && description.has("identifier")) {
            out.identifier = description.get("identifier").getAsString();
        }
        out.fogColorOverridden = false;
        JsonObject distance = Curry.obj(settings, "distance");
        if (distance != null) {
            JsonObject air = Curry.obj(distance, "air");
            if (air != null) {
                out.readDistance(air, "air");
            }
            JsonObject water = Curry.obj(distance, "water");
            if (water != null) {
                out.readDistance(water, "water");
            }
            JsonObject weather = Curry.obj(distance, "weather");
            if (weather != null) {
                out.readDistance(weather, "weather");
            }
            JsonObject lava = Curry.obj(distance, "lava");
            if (lava != null) {
                out.readDistance(lava, "lava");
            }
            JsonObject lavaResistance = Curry.obj(distance, "lava_resistance");
            if (lavaResistance != null) {
                out.hasLavaResistance = true;
                out.lavaResistanceStart = CurveParser.getFloat(lavaResistance, "fog_start", 2.0F);
                out.lavaResistanceEnd = CurveParser.getFloat(lavaResistance, "fog_end", 4.0F);
            }
        }
        JsonObject volumetric = Curry.obj(settings, "volumetric");
        if (volumetric != null) {
            JsonObject density = Curry.obj(volumetric, "density");
            JsonObject airDensity = density == null ? null : Curry.obj(density, "air");
            if (airDensity != null) {
                out.volumetricAirMaxDensity = CurveParser.getFloat(airDensity, "max_density", out.volumetricAirMaxDensity);
                out.volumetricAirZeroHeight = CurveParser.getFloat(airDensity, "zero_density_height", out.volumetricAirZeroHeight);
                out.volumetricAirMaxDensityHeight = CurveParser.getFloat(airDensity, "max_density_height", out.volumetricAirMaxDensityHeight);
            }
            JsonObject media = Curry.obj(volumetric, "media_coefficients");
            JsonObject airMedia = media == null ? null : Curry.obj(media, "air");
            if (airMedia != null) {
                out.airScattering = CurveParser.readVec3(airMedia.get("scattering"), out.airScattering);
                out.airAbsorption = CurveParser.readVec3(airMedia.get("absorption"), out.airAbsorption);
            }
            JsonObject hg = Curry.obj(volumetric, "henyey_greenstein_g");
            JsonObject airHg = hg == null ? null : Curry.obj(hg, "air");
            if (airHg != null) {
                out.airHeneyGreensteinG = CurveParser.getFloat(airHg, "henyey_greenstein_g", out.airHeneyGreensteinG);
            }
            JsonObject weatherDensity = density == null ? null : Curry.obj(density, "weather");
            if (weatherDensity != null) {
                out.hasWeatherVolumetric = true;
                out.weatherVolumetricDensity = CurveParser.getFloat(weatherDensity, "max_density", out.weatherVolumetricDensity);
            }
        }
        return out;
    }

    private void readDistance(JsonObject object, String type) {
        float start = CurveParser.getFloat(object, "fog_start", 0.0F);
        float end = CurveParser.getFloat(object, "fog_end", 1.0F);
        float[] color = object.has("fog_color")
                ? CurveParser.readColor(object.get("fog_color"), null)
                : null;
        String distanceType = object.has("render_distance_type")
                ? object.get("render_distance_type").getAsString()
                : "render";
        switch (type) {
            case "air" -> {
                hasAir = true;
                airStart = start;
                airEnd = Math.max(end, start + 1.0E-4F);
                airDistanceType = distanceType;
                if (color != null) {
                    airColor = color;
                }
            }
            case "water" -> {
                hasWater = true;
                waterStart = start;
                waterEnd = Math.max(end, start + 1.0E-4F);
                waterDistanceType = distanceType;
                if (color != null) {
                    waterColor = color;
                }
            }
            case "weather" -> {
                hasWeather = true;
                weatherStart = start;
                weatherEnd = Math.max(end, start + 1.0E-4F);
                if (color != null) {
                    weatherColor = color;
                }
            }
            case "lava" -> {
                hasLava = true;
                lavaStart = start;
                lavaEnd = Math.max(end, start + 1.0E-4F);
                if (color != null) {
                    lavaColor = color;
                }
            }
            default -> {
            }
        }
        if (color != null) {
            fogColorOverridden = true;
        }
    }

    public boolean isFogColorOverridden() {
        return fogColorOverridden;
    }

    private static FogSettings copy(FogSettings other) {
        FogSettings out = new FogSettings();
        out.identifier = other.identifier;
        out.hasAir = other.hasAir;
        out.airStart = other.airStart;
        out.airEnd = other.airEnd;
        out.airColor = other.airColor;
        out.airDistanceType = other.airDistanceType;
        out.hasWater = other.hasWater;
        out.waterStart = other.waterStart;
        out.waterEnd = other.waterEnd;
        out.waterColor = other.waterColor;
        out.waterDistanceType = other.waterDistanceType;
        out.hasWeather = other.hasWeather;
        out.weatherStart = other.weatherStart;
        out.weatherEnd = other.weatherEnd;
        out.weatherColor = other.weatherColor;
        out.hasLava = other.hasLava;
        out.lavaStart = other.lavaStart;
        out.lavaEnd = other.lavaEnd;
        out.lavaColor = other.lavaColor;
        out.hasLavaResistance = other.hasLavaResistance;
        out.lavaResistanceStart = other.lavaResistanceStart;
        out.lavaResistanceEnd = other.lavaResistanceEnd;
        out.volumetricAirMaxDensity = other.volumetricAirMaxDensity;
        out.volumetricAirZeroHeight = other.volumetricAirZeroHeight;
        out.volumetricAirMaxDensityHeight = other.volumetricAirMaxDensityHeight;
        out.airScattering = other.airScattering;
        out.airAbsorption = other.airAbsorption;
        out.airHeneyGreensteinG = other.airHeneyGreensteinG;
        out.hasWeatherVolumetric = other.hasWeatherVolumetric;
        out.weatherVolumetricDensity = other.weatherVolumetricDensity;
        out.weatherScattering = other.weatherScattering;
        out.fogColorOverridden = other.fogColorOverridden;
        return out;
    }
}

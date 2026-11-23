package dev.bedrockshaders.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * User configuration. Stored as human editable JSON in {@code config/bedrock-shaders.json}.
 *
 * <p>All values are validated and clamped when loaded so a hand-edited file can never
 * crash the renderer.</p>
 */
public final class BedrockConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Master switches
    public boolean enabled = true;
    public int preset = QualityPreset.BALANCED.ordinal();
    public boolean replaceVanillaSky = true;
    public boolean vanillaShaderOverrides = true;
    /**
     * Apply the recognised shader family's tuning (haze reach, underwater density) on top
     * of the pack's own data. On by default: without it a Newb pack renders with neutral
     * defaults and loses the look its author intended.
     */
    public boolean usePackFamilyTuning = true;
    public boolean vanillaFogCurve = true;
    public boolean bedrockLightmap = true;

    // Atmosphere
    public boolean atmosphere = true;
    public float fogDensity = 1.0F;
    public float skyBrightness = 1.0F;
    public float sunIntensity = 1.0F;
    public float ambientFloor = 0.035F;
    public boolean clouds = true;
    public boolean stars = true;

    // Water
    public boolean water = true;
    public float waterWaveStrength = 1.0F;
    public float waterReflectionStrength = 0.85F;
    public float waterRefractionStrength = 0.35F;
    public float caustics = 0.6F;

    // Effects
    public boolean ssao = true;
    public float ssaoStrength = 0.85F;
    public float ssaoRadius = 1.0F;
    public boolean reflections = true;
    public boolean godrays = true;
    public float godrayStrength = 1.0F;
    public boolean bloom = true;
    public float bloomStrength = 0.32F;
    public boolean underwater = true;
    public boolean lensFlare = true;

    // Grade
    public boolean tonemap = true;
    public float exposure = 1.0F;
    public boolean colorGrading = true;
    public float saturation = 1.0F;
    public float contrast = 1.0F;
    public float temperatureKelvin = 6500.0F;

    // Imported Bedrock packs (.mcpack dropped into bedrock-shaders/packs)
    public boolean autoImportPacks = true;
    public boolean deletePackAfterImport = false;

    // Advanced / debug
    public float renderScale = 1.0F;
    public boolean debugOverlay = false;
    public boolean logFrameDiagnostics = false;
    public String profile = "custom";

    private BedrockConfig() {
    }

    public static BedrockConfig defaults() {
        return new BedrockConfig();
    }

    /** True when any colour-grading / tone-mapping step should run. */
    public boolean composite() {
        return tonemap || colorGrading || exposure != 1.0F || saturation != 1.0F || contrast != 1.0F;
    }

    public QualityPreset qualityPreset() {
        return QualityPreset.values()[Math.max(0, Math.min(preset, QualityPreset.values().length - 1))];
    }

    public void applyPreset(QualityPreset value) {
        preset = value.ordinal();
        profile = value.id;
        renderScale = value.renderScale;
        ssao = value.ssao;
        ssaoStrength = value.ssaoStrength;
        reflections = value.reflections;
        waterReflectionStrength = value.reflectionStrength;
        bloom = value.bloom;
        bloomStrength = value.bloomStrength;
        godrays = value.godrays;
        water = true;
        underwater = true;
    }

    /** Copies every value from {@code other} into this instance (used by the settings screen). */
    public void copyFrom(BedrockConfig other) {
        enabled = other.enabled;
        preset = other.preset;
        replaceVanillaSky = other.replaceVanillaSky;
        vanillaShaderOverrides = other.vanillaShaderOverrides;
        vanillaFogCurve = other.vanillaFogCurve;
        bedrockLightmap = other.bedrockLightmap;
        atmosphere = other.atmosphere;
        fogDensity = other.fogDensity;
        skyBrightness = other.skyBrightness;
        sunIntensity = other.sunIntensity;
        ambientFloor = other.ambientFloor;
        clouds = other.clouds;
        stars = other.stars;
        water = other.water;
        waterWaveStrength = other.waterWaveStrength;
        waterReflectionStrength = other.waterReflectionStrength;
        waterRefractionStrength = other.waterRefractionStrength;
        caustics = other.caustics;
        ssao = other.ssao;
        ssaoStrength = other.ssaoStrength;
        ssaoRadius = other.ssaoRadius;
        reflections = other.reflections;
        godrays = other.godrays;
        godrayStrength = other.godrayStrength;
        bloom = other.bloom;
        bloomStrength = other.bloomStrength;
        underwater = other.underwater;
        lensFlare = other.lensFlare;
        tonemap = other.tonemap;
        exposure = other.exposure;
        colorGrading = other.colorGrading;
        saturation = other.saturation;
        contrast = other.contrast;
        temperatureKelvin = other.temperatureKelvin;
        autoImportPacks = other.autoImportPacks;
        deletePackAfterImport = other.deletePackAfterImport;
        renderScale = other.renderScale;
        debugOverlay = other.debugOverlay;
        logFrameDiagnostics = other.logFrameDiagnostics;
        profile = other.profile;
    }

    public void mutate(Consumer<BedrockConfig> action) {
        action.accept(this);
        sanitize();
    }

    private void sanitize() {
        preset = Math.max(0, Math.min(preset, QualityPreset.values().length - 1));
        fogDensity = clamp(fogDensity, 0.0F, 4.0F);
        skyBrightness = clamp(skyBrightness, 0.0F, 4.0F);
        sunIntensity = clamp(sunIntensity, 0.0F, 8.0F);
        ambientFloor = clamp(ambientFloor, 0.0F, 1.0F);
        waterWaveStrength = clamp(waterWaveStrength, 0.0F, 4.0F);
        waterReflectionStrength = clamp(waterReflectionStrength, 0.0F, 1.0F);
        waterRefractionStrength = clamp(waterRefractionStrength, 0.0F, 2.0F);
        caustics = clamp(caustics, 0.0F, 3.0F);
        ssaoStrength = clamp(ssaoStrength, 0.0F, 3.0F);
        ssaoRadius = clamp(ssaoRadius, 0.1F, 5.0F);
        godrayStrength = clamp(godrayStrength, 0.0F, 5.0F);
        bloomStrength = clamp(bloomStrength, 0.0F, 2.0F);
        exposure = clamp(exposure, 0.05F, 8.0F);
        saturation = clamp(saturation, 0.0F, 3.0F);
        contrast = clamp(contrast, 0.0F, 3.0F);
        temperatureKelvin = clamp(temperatureKelvin, 1500.0F, 15000.0F);
        renderScale = clamp(renderScale, 0.25F, 2.0F);
        if (profile == null || profile.isEmpty()) {
            profile = "custom";
        }
    }

    private static float clamp(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }

    public static BedrockConfig load(Path path) {
        BedrockConfig config = new BedrockConfig();
        if (Files.isRegularFile(path)) {
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                BedrockConfig loaded = GSON.fromJson(json, BedrockConfig.class);
                if (loaded != null) {
                    config.copyFrom(loaded);
                }
            } catch (IOException | JsonSyntaxException e) {
                System.err.println("[Bedrock Shaders] Could not read config, using defaults: " + e.getMessage());
            }
        }
        config.sanitize();
        return config;
    }

    public void save(Path path) {
        sanitize();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Bedrock Shaders] Could not write config: " + e.getMessage());
        }
    }
}

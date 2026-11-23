package dev.bedrockshaders.client.render;

import dev.bedrockshaders.bedrock.AtmosphereSettings;
import dev.bedrockshaders.bedrock.ColorGradingSettings;
import dev.bedrockshaders.bedrock.FogSettings;
import dev.bedrockshaders.bedrock.WaterSettings;
import dev.bedrockshaders.bedrock.LightingSettings;
import dev.bedrockshaders.client.ConfigManager;
import dev.bedrockshaders.client.assets.BedrockAssetLibrary;
import dev.bedrockshaders.client.pack.PackRegistry;
import dev.bedrockshaders.util.ColorUtil;
import dev.bedrockshaders.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Everything the Bedrock shaders need to know about the current frame: camera,
 * matrices, sky/atmosphere curves sampled for the current time of day, water
 * state and the Bedrock fog identifier of the biome we are standing in.
 *
 * <p>One instance is reused every frame; {@link #beginFrame()} recomputes it
 * exactly once and every renderer entry point reads from it.</p>
 */
public final class FrameContext {
    private static final FrameContext INSTANCE = new FrameContext();

    private long frameId;
    private boolean active;
    private boolean inWorld;

    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f inverseViewMatrix = new Matrix4f();
    private final Matrix4f inverseProjectionMatrix = new Matrix4f();
    private final Vector3f sunDirection = new Vector3f(0.0F, 1.0F, 0.0F);
    private final Vector3f moonDirection = new Vector3f(0.0F, -1.0F, 0.0F);
    private float sunIntensity;
    private float moonIntensity;
    private float sunAngle;
    private float tickDelta;
    private float worldTimeSeconds;
    private float rainGradient;
    private float thunderGradient;
    private boolean underwater;
    private boolean eyesInWater;
    private float waterSurfaceHeight;
    private int waterSurfaceY;
    private boolean inWaterBody;
    private boolean nether;
    private boolean end;
    private float skyDarken;
    private float viewDistanceBlocks;
    private float fov;
    private int framebufferWidth;
    private int framebufferHeight;

    private final float[] skyZenith = new float[] {0.16F, 0.28F, 0.55F};
    private final float[] skyHorizon = new float[] {0.55F, 0.68F, 0.86F};
    private final float[] sunColor = new float[] {1.0F, 0.92F, 0.82F};
    private final float[] moonColor = new float[] {0.62F, 0.72F, 1.0F};
    private final float[] fogColor = new float[] {0.55F, 0.68F, 0.86F};
    private final float[] waterFogColor = new float[] {0.09F, 0.29F, 0.44F};
    private final float[] waterSurfaceColor = new float[] {0.12F, 0.42F, 0.62F};
    private String biomeId = "";
    private String activePackName = "";
    private float waterTransparency = 0.65F;
    private float fogDensity = 1.0F;
    private float fogStart;
    private float fogEnd;

    private String fogId = "minecraft:fog_plains";
    /** Shader family of the active pack, sampled once per frame. */
    private dev.bedrockshaders.config.ShaderFamily family =
            dev.bedrockshaders.config.ShaderFamily.GENERIC;
    private FogSettings fogSettings = new FogSettings();
    private LightingSettings lightingSettings = new LightingSettings();
    private AtmosphereSettings atmosphereSettings = new AtmosphereSettings();
    private ColorGradingSettings gradingSettings = new ColorGradingSettings();

    private FrameContext() {
    }

    public static FrameContext get() {
        return INSTANCE;
    }

    public long frameId() {
        return frameId;
    }

    /**
     * Called once at the start of the world pass. All game access goes through
     * {@link ClientSnapshot}, which each loader implements against its own mappings.
     */
    public void beginFrame(ClientSnapshot snapshot) {
        frameId++;
        // A downgraded mode (Iris owns the frame, or there is no GL backend at all) must
        // leave every downstream consumer inactive: uniforms stop binding, the sky is
        // untouched and the post chain never runs.
        if (!RenderCompat.get().allowsGlWork()) {
            active = false;
            inWorld = snapshot != null && snapshot.inWorld();
            return;
        }
        active = snapshot != null && snapshot.inWorld();
        inWorld = active;
        if (!active) {
            return;
        }

        tickDelta = snapshot.tickDelta();
        cameraX = snapshot.cameraX();
        cameraY = snapshot.cameraY();
        cameraZ = snapshot.cameraZ();
        framebufferWidth = snapshot.framebufferWidth();
        framebufferHeight = snapshot.framebufferHeight();
        viewDistanceBlocks = snapshot.viewDistanceBlocks();
        // GameRenderer#getFov is private in every mapping; derive the vertical FOV from
        // the projection matrix that the world pass hands to the mixin.
        fov = projectionMatrix == null ? 70.0F
                : (float) Math.toDegrees(2.0 * Math.atan(1.0 / Math.max(projectionMatrix.m11(), 1.0E-4)));

        // Sky time: Bedrock's lighting curves are keyed on a normalised 0..1 day.
        long timeOfDay = snapshot.dayTime();
        sunAngle = snapshot.skyAngle(tickDelta);
        float celestialAngle = sunAngle * (float) (Math.PI * 2.0);
        worldTimeSeconds = (float) (timeOfDay % 24000L) * 0.05F + tickDelta * 0.05F;
        sunDirection.set(
                (float) Math.cos(celestialAngle) * 0.6F + 0.0F,
                (float) Math.sin(celestialAngle) * 0.85F + 0.0F,
                (float) Math.cos(celestialAngle + 1.2) * 0.45F
        ).normalize();
        moonDirection.set(-sunDirection.x, -sunDirection.y, -sunDirection.z).normalize();

        rainGradient = snapshot.rainLevel(tickDelta);
        thunderGradient = snapshot.thunderLevel(tickDelta);
        skyDarken = snapshot.skyDarken(tickDelta);
        eyesInWater = false;
        // Cheap enum-backed checks instead of formatting a dimension id every frame.
        nether = snapshot.inNether();
        end = snapshot.inEnd();
        String dimension = nether ? "minecraft:the_nether" : end ? "minecraft:the_end" : "minecraft:overworld";

        biomeId = snapshot.biomeId(cameraX, cameraY, cameraZ);
        fogId = BiomeMapper.fogIdFor(biomeId, dimension,
                snapshot.biomeTemperature(cameraX, cameraY, cameraZ),
                snapshot.biomeHasPrecipitation(cameraX, cameraY, cameraZ));
        BedrockAssetLibrary assets = BedrockAssetLibrary.get();
        assets.ensureLoaded();
        fogSettings = assets.fog(fogId);
        lightingSettings = assets.lighting(fogId);
        atmosphereSettings = assets.atmosphere(fogId);
        gradingSettings = assets.grading(fogId);

        // Bedrock sun/moon curve sampling.
        float sunCurve = lightingSettings.sunIlluminance.isEmpty() ? 0.0F : lightingSettings.sunIlluminance.get(sunAngle);
        sunIntensity = Mth.clamp(sunCurve / 100.0F, 0.0F, 1.0F);
        moonIntensity = lightingSettings.moonIlluminance.isEmpty() ? 0.0F : Mth.clamp(lightingSettings.moonIlluminance.get(sunAngle), 0.0F, 1.0F);
        if (nether) {
            sunIntensity = 0.0F;
            moonIntensity = 0.25F;
        }
        // Sample the family before colouring: litColors() and the fog window both use it.
        family = ConfigManager.get().config().usePackFamilyTuning
                ? PackRegistry.get().activeFamily()
                : dev.bedrockshaders.config.ShaderFamily.GENERIC;

        litColors();

        // Final light strength, scaled by the family's intent (Newb runs a touch brighter).
        if (family.sunIntensityScale != 1.0F) {
            sunIntensity = Mth.clamp(sunIntensity * family.sunIntensityScale, 0.0F, 1.0F);
        }

        // Water detection (used by terrain caustics + water waves).
        underwater = snapshot.cameraInWater();
        eyesInWater = underwater;

        // Fog window in blocks (Bedrock "render" distance_type vs "fixed").
        FogSettings fog = fogSettings;
        float density = Math.max(0.05F, ConfigManager.get().config().fogDensity);

        // Recognised Bedrock shader families ship data, not code, so their look only
        // survives here if the renderer reproduces it. The Newb family in particular is
        // known for haze that reaches further and a denser blue underwater view.
        if (underwater && fog.hasWater) {
            fogStart = fog.waterStart / family.underwaterFogScale;
            fogEnd = Math.max(fog.waterEnd / family.underwaterFogScale, fogStart + 1.0F);
            fogDensity = density * 1.25F * family.fogDensityScale;
        } else {
            float distance = viewDistanceBlocks;
            if (fog.hasAir && "render".equals(fog.airDistanceType)) {
                fogStart = fog.airStart * distance;
                fogEnd = Math.max(fog.airEnd * distance, fogStart + 1.0F);
            } else if (fog.hasAir) {
                fogStart = fog.airStart;
                fogEnd = Math.max(fog.airEnd, fogStart + 1.0F);
            } else {
                fogStart = distance * 0.92F;
                fogEnd = distance;
            }
            fogDensity = density * family.fogDensityScale;
            if (rainGradient > 0.01F && fog.hasWeather) {
                fogStart = Mth.lerp(rainGradient * 0.5F, fogStart, fog.weatherStart * distance);
                fogEnd = Mth.lerp(rainGradient * 0.5F, fogEnd, Math.max(fog.weatherEnd * distance, fogEnd * 0.5F));
            }
        }

        // The family's haze reach: packs like Newb deliberately let fog sit further out,
        // which is what makes their distance depth read differently from vanilla Bedrock.
        if (family.fogScale != 1.0F && fogEnd > fogStart) {
            float span = (fogEnd - fogStart) * family.fogScale;
            fogEnd = fogStart + Math.max(1.0F, span);
        }

        // Underwater surface + water column detection.
        if (underwater) {
            waterSurfaceHeight = (float) Math.floor(cameraY) + 1.0F;
            waterSurfaceY = (int) Math.floor(cameraY);
            inWaterBody = true;
        } else {
            waterSurfaceHeight = snapshot.waterSurfaceHeight(
                    (int) Math.floor(cameraX), (int) Math.floor(cameraY), (int) Math.floor(cameraZ),
                    (float) Math.floor(cameraY) + 1.0F);
            waterSurfaceY = (int) Math.floor(waterSurfaceHeight);
            inWaterBody = waterSurfaceHeight > snapshot.minBuildHeight();
        }

        // Extension pack hook, last: the frame is complete, so an addon sees final values and
        // whatever it writes here is what the uniforms upload. No hook registered means this
        // is one volatile read and a branch.
        applyExtensionHooks();
    }

    /**
     * Runs the extension frame hooks and folds their output into this frame.
     *
     * <p>Multipliers only, clamped by the public API, and the family's own tuning has already
     * been applied above, so an addon adjusts the final look rather than fighting pack data.</p>
     */
    private void applyExtensionHooks() {
        var registry = dev.bedrockshaders.api.ExtensionRegistry.get();
        if (registry.frameHooks().isEmpty()) {
            return;
        }
        if (!registry.applyFrameHooks(new ExtensionFrameView(), extensionDelta)) {
            return;
        }
        if (extensionDelta.fogDensity != 1.0F) {
            fogDensity *= extensionDelta.fogDensity;
        }
        if (extensionDelta.fogSpan != 1.0F && fogEnd > fogStart) {
            float span = (fogEnd - fogStart) * extensionDelta.fogSpan;
            fogEnd = fogStart + Math.max(1.0F, span);
        }
        if (extensionDelta.underwaterFog != 1.0F && underwater) {
            float span = (fogEnd - fogStart) * extensionDelta.underwaterFog;
            fogEnd = fogStart + Math.max(1.0F, span);
        }
        if (extensionDelta.sunIntensity != 1.0F) {
            sunIntensity = Mth.clamp(sunIntensity * extensionDelta.sunIntensity, 0.0F, 1.0F);
        }
        extensionExposure = extensionDelta.exposure;
        extensionSaturation = extensionDelta.saturation;
        extensionContrast = extensionDelta.contrast;
        extensionBloomBias = extensionDelta.bloomBias;
        extensionCausticsBias = extensionDelta.causticsBias;
    }

    /** Reused every frame: the hook API promises not to allocate on the render path. */
    private final dev.bedrockshaders.api.TuningDelta extensionDelta = new dev.bedrockshaders.api.TuningDelta();

    private float extensionExposure = 1.0F;
    private float extensionSaturation = 1.0F;
    private float extensionContrast = 1.0F;
    private float extensionBloomBias = 0.0F;
    private float extensionCausticsBias = 0.0F;

    /** Read-only adapter over this frame for the public hook API. */
    private final class ExtensionFrameView implements dev.bedrockshaders.api.FrameHook.FrameView {
        @Override public String packId() { return activePackName; }
        @Override public String familyId() { return family.id; }
        @Override public float worldTimeSeconds() { return worldTimeSeconds; }
        @Override public float sunIntensity() { return FrameContext.this.sunIntensity; }
        @Override public float moonIntensity() { return moonIntensity; }
        @Override public float rainGradient() { return rainGradient; }
        @Override public String biomeId() { return biomeId; }
        @Override public String fogId() { return fogId; }
        @Override public boolean underwater() { return underwater; }
        @Override public boolean nether() { return nether; }
        @Override public boolean end() { return end; }
        @Override public double cameraX() { return cameraX; }
        @Override public double cameraY() { return cameraY; }
        @Override public double cameraZ() { return cameraZ; }
        @Override public float fogStart() { return fogStart; }
        @Override public float fogEnd() { return fogEnd; }
    }

    /** Exposure multiplier requested by extension hooks this frame (1.0 when none). */
    public float extensionExposure() { return extensionExposure; }
    /** Saturation multiplier requested by extension hooks this frame. */
    public float extensionSaturation() { return extensionSaturation; }
    /** Contrast multiplier requested by extension hooks this frame. */
    public float extensionContrast() { return extensionContrast; }
    /** Additive bloom bias requested by extension hooks this frame. */
    public float extensionBloomBias() { return extensionBloomBias; }
    /** Additive caustics bias requested by extension hooks this frame. */
    public float extensionCausticsBias() { return extensionCausticsBias; }

    private void litColors() {
        float[] zenith = atmosphereSettings.skyZenithColor.get(sunAngle, skyZenith);
        float[] horizon = atmosphereSettings.skyHorizonColor.get(sunAngle, skyHorizon);
        System.arraycopy(zenith, 0, skyZenith, 0, 3);
        System.arraycopy(horizon, 0, skyHorizon, 0, 3);
        float[] sun = lightingSettings.sunColor.get(sunAngle, sunColor);
        System.arraycopy(sun, 0, sunColor, 0, 3);
        if (family.sunIntensityScale != 1.0F) {
            // These packs warm the light rather than brightening every channel equally:
            // the red channel gains the most, which is what reads as "golden hour".
            sunColor[0] = Mth.clamp(sunColor[0] * family.sunIntensityScale, 0.0F, 4.0F);
            sunColor[1] = Mth.clamp(sunColor[1] * (1.0F + (family.sunIntensityScale - 1.0F) * 0.8F), 0.0F, 4.0F);
            sunColor[2] = Mth.clamp(sunColor[2] * (1.0F + (family.sunIntensityScale - 1.0F) * 0.5F), 0.0F, 4.0F);
        }
        float[] moon = lightingSettings.moonColor.get(sunAngle, moonColor);
        System.arraycopy(moon, 0, moonColor, 0, 3);
        float[] airFog = ColorUtil.rgbToLinear(new float[] {
                (float) (Math.pow(Mth.clamp(skyHorizon[0], 0.0F, 1.0F), 0.65F) * 255.0F),
                (float) (Math.pow(Mth.clamp(skyHorizon[1], 0.0F, 1.0F), 0.65F) * 255.0F),
                (float) (Math.pow(Mth.clamp(skyHorizon[2], 0.0F, 1.0F), 0.65F) * 255.0F)
        });
        if (fogSettings.isFogColorOverridden()) {
            System.arraycopy(fogSettings.airColor, 0, fogColor, 0, 3);
        } else {
            System.arraycopy(airFog, 0, fogColor, 0, 3);
        }
        if (fogSettings.hasWater) {
            System.arraycopy(fogSettings.waterColor, 0, waterFogColor, 0, 3);
            System.arraycopy(fogSettings.waterColor, 0, waterSurfaceColor, 0, 3);
        }
        waterTransparency = Mth.clamp(0.35F + 0.35F * (fogSettings.hasWater ? 1.0F : 0.6F), 0.2F, 0.85F);
        // Bedrock's biomes_client.json describes water per biome and is the authoritative
        // source when an imported pack provides it; the fog setting is only the fallback.
        activePackName = PackRegistry.get().activePackForBiome(biomeId);
        // litColors() runs outside the frame setup, so the library is fetched directly.
        WaterSettings waterTable = BedrockAssetLibrary.get().water();
        if (!waterTable.isEmpty()) {
            WaterSettings.Entry waterEntry = waterTable.entryFor(biomeId);
            if (waterEntry.hasFogColor) {
                System.arraycopy(waterEntry.fogColor, 0, waterFogColor, 0, 3);
            }
            if (waterEntry.hasSurfaceColor) {
                System.arraycopy(waterEntry.surfaceColor, 0, waterSurfaceColor, 0, 3);
            }
            if (waterEntry.hasTransparency) {
                waterTransparency = Mth.clamp(waterEntry.transparency, 0.05F, 1.0F);
            }
        }
        if (nether) {
            skyZenith[0] = 0.11F; skyZenith[1] = 0.02F; skyZenith[2] = 0.02F;
            skyHorizon[0] = 0.22F; skyHorizon[1] = 0.05F; skyHorizon[2] = 0.03F;
            fogColor[0] = 0.30F; fogColor[1] = 0.06F; fogColor[2] = 0.03F;
        } else if (end) {
            skyZenith[0] = 0.045F; skyZenith[1] = 0.03F; skyZenith[2] = 0.09F;
            skyHorizon[0] = 0.16F; skyHorizon[1] = 0.10F; skyHorizon[2] = 0.22F;
        }
    }

    /** Called by the GameRenderer mixin with the exact matrices used for the world pass. */
    public void captureMatrices(Matrix4f projection, Matrix4f view) {
        setProjection(projection);
        setView(view);
    }

    public void setProjection(Matrix4f projection) {
        projectionMatrix.set(projection);
        inverseProjectionMatrix.set(projection).invert();
    }

    public void setView(org.joml.Matrix4fc view) {
        viewMatrix.set(view);
        inverseViewMatrix.set(view);
        inverseViewMatrix.invert();
    }

    public void endFrame() {
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isInWorld() {
        return inWorld;
    }

    public double cameraX() { return cameraX; }
    public double cameraY() { return cameraY; }
    public double cameraZ() { return cameraZ; }
    public Matrix4f projectionMatrix() { return projectionMatrix; }
    public Matrix4f viewMatrix() { return viewMatrix; }
    public Matrix4f inverseViewMatrix() { return inverseViewMatrix; }
    public Matrix4f inverseProjectionMatrix() { return inverseProjectionMatrix; }
    public Vector3f sunDirection() { return sunDirection; }
    public Vector3f moonDirection() { return moonDirection; }
    public float sunIntensity() { return sunIntensity; }
    public float moonIntensity() { return moonIntensity; }
    public float sunAngle() { return sunAngle; }
    public float tickDelta() { return tickDelta; }
    public float worldTimeSeconds() { return worldTimeSeconds; }
    public float rainGradient() { return rainGradient; }
    public float thunderGradient() { return thunderGradient; }
    public boolean isUnderwater() { return underwater; }
    public boolean isEyesInWater() { return eyesInWater; }
    public float waterSurfaceHeight() { return waterSurfaceHeight; }
    public int waterSurfaceY() { return waterSurfaceY; }
    public boolean isInWaterBody() { return inWaterBody; }
    public boolean isNether() { return nether; }
    public boolean isEnd() { return end; }
    public float skyDarken() { return skyDarken; }
    public float viewDistanceBlocks() { return viewDistanceBlocks; }
    public float fov() { return fov; }
    public int framebufferWidth() { return framebufferWidth; }
    public int framebufferHeight() { return framebufferHeight; }
    public float[] skyZenith() { return skyZenith; }
    public float[] skyHorizon() { return skyHorizon; }
    public float[] sunColor() { return sunColor; }
    public float[] moonColor() { return moonColor; }
    public float[] fogColor() { return fogColor; }
    public float[] waterFogColor() { return waterFogColor; }
    public float[] waterSurfaceColor() { return waterSurfaceColor; }
    public float waterTransparency() { return waterTransparency; }
    public float fogDensity() { return fogDensity; }
    public float fogStart() { return fogStart; }
    public float fogEnd() { return fogEnd; }
    public String fogId() { return fogId; }

    /** Shader family the active pack belongs to (generic when none is recognised). */
    public dev.bedrockshaders.config.ShaderFamily shaderFamily() { return family; }

    /** Java biome id (e.g. {@code minecraft:plains}) used for the water table lookup. */
    public String biomeId() { return biomeId; }

    /** Display name of the imported Bedrock pack providing this frame's data, if any. */
    public String activePackName() { return activePackName; }
    public FogSettings fogSettings() { return fogSettings; }
    public LightingSettings lightingSettings() { return lightingSettings; }
    public AtmosphereSettings atmosphereSettings() { return atmosphereSettings; }
    public ColorGradingSettings gradingSettings() { return gradingSettings; }
}

package dev.bedrockshaders.client.render;

import dev.bedrockshaders.bedrock.AtmosphereSettings;
import dev.bedrockshaders.bedrock.ColorGradingSettings;
import dev.bedrockshaders.bedrock.FogSettings;
import dev.bedrockshaders.bedrock.LightingSettings;
import dev.bedrockshaders.client.ConfigManager;
import dev.bedrockshaders.client.assets.BedrockAssetLibrary;
import dev.bedrockshaders.util.ColorUtil;
import dev.bedrockshaders.util.Mth;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.world.biome.Biome;
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
    private float waterTransparency = 0.65F;
    private float fogDensity = 1.0F;
    private float fogStart;
    private float fogEnd;

    private String fogId = "minecraft:fog_plains";
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

    /** Called once at the start of {@code GameRenderer.renderWorld}. */
    public void beginFrame(MinecraftClient client) {
        frameId++;
        ClientWorld world = client.world;
        ClientPlayerEntity player = client.player;
        active = world != null && player != null;
        inWorld = active;
        if (!active) {
            return;
        }

        tickDelta = client.getRenderTickCounter().getTickDelta(false);

        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        cameraX = cameraPos.x;
        cameraY = cameraPos.y;
        cameraZ = cameraPos.z;
        framebufferWidth = client.getWindow().getFramebufferWidth();
        framebufferHeight = client.getWindow().getFramebufferHeight();
        viewDistanceBlocks = client.options.getViewDistance().getValue() * 16.0F;
        // GameRenderer#getFov is private; derive the vertical FOV from the projection matrix instead.
        fov = projectionMatrix == null ? 70.0F
                : (float) Math.toDegrees(2.0 * Math.atan(1.0 / Math.max(projectionMatrix.m11(), 1.0E-4)));

        // Sky time: Bedrock's lighting curves are keyed on a normalised 0..1 day.
        long timeOfDay = world.getTimeOfDay();
        sunAngle = world.getSkyAngle(tickDelta);
        float celestialAngle = sunAngle * (float) (Math.PI * 2.0);
        worldTimeSeconds = (float) (timeOfDay % 24000L) * 0.05F + tickDelta * 0.05F;
        sunDirection.set(
                (float) Math.cos(celestialAngle) * 0.6F + 0.0F,
                (float) Math.sin(celestialAngle) * 0.85F + 0.0F,
                (float) Math.cos(celestialAngle + 1.2) * 0.45F
        ).normalize();
        moonDirection.set(-sunDirection.x, -sunDirection.y, -sunDirection.z).normalize();

        rainGradient = world.getRainGradient(tickDelta);
        thunderGradient = world.getThunderGradient(tickDelta);
        skyDarken = client.gameRenderer.getSkyDarkness(tickDelta);
        eyesInWater = false;
        nether = world.getRegistryKey() == World.NETHER;
        end = world.getRegistryKey() == World.END;

        BlockPos cameraBlockPos = BlockPos.ofFloored(cameraX, cameraY, cameraZ);
        RegistryEntry<Biome> biomeEntry = world.getBiome(cameraBlockPos);
        fogId = BiomeMapper.fogIdFor(world, biomeEntry, cameraBlockPos);
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
        litColors();

        // Water detection (used by terrain caustics + water waves).
        underwater = client.gameRenderer.getCamera().getSubmersionType() == CameraSubmersionType.WATER;
        eyesInWater = underwater;

        // Fog window in blocks (Bedrock "render" distance_type vs "fixed").
        FogSettings fog = fogSettings;
        float density = Math.max(0.05F, ConfigManager.get().config().fogDensity);
        if (underwater && fog.hasWater) {
            fogStart = fog.waterStart;
            fogEnd = fog.waterEnd;
            fogDensity = density * 1.25F;
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
            fogDensity = density;
            if (rainGradient > 0.01F && fog.hasWeather) {
                fogStart = Mth.lerp(rainGradient * 0.5F, fogStart, fog.weatherStart * distance);
                fogEnd = Mth.lerp(rainGradient * 0.5F, fogEnd, Math.max(fog.weatherEnd * distance, fogEnd * 0.5F));
            }
        }

        // Underwater surface + water column detection.
        if (underwater) {
            waterSurfaceHeight = (float) Math.floor(cameraY) + 1.0F;
            waterSurfaceY = (int) Math.floor(cameraY);
            inWaterBody = true;
        } else {
            waterSurfaceHeight = findWaterSurface(world, cameraBlockPos);
            waterSurfaceY = (int) Math.floor(waterSurfaceHeight);
            inWaterBody = waterSurfaceHeight > world.getBottomY();
        }
    }

    private void litColors() {
        float[] zenith = atmosphereSettings.skyZenithColor.get(sunAngle, skyZenith);
        float[] horizon = atmosphereSettings.skyHorizonColor.get(sunAngle, skyHorizon);
        System.arraycopy(zenith, 0, skyZenith, 0, 3);
        System.arraycopy(horizon, 0, skyHorizon, 0, 3);
        float[] sun = lightingSettings.sunColor.get(sunAngle, sunColor);
        System.arraycopy(sun, 0, sunColor, 0, 3);
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
        if (nether) {
            skyZenith[0] = 0.11F; skyZenith[1] = 0.02F; skyZenith[2] = 0.02F;
            skyHorizon[0] = 0.22F; skyHorizon[1] = 0.05F; skyHorizon[2] = 0.03F;
            fogColor[0] = 0.30F; fogColor[1] = 0.06F; fogColor[2] = 0.03F;
        } else if (end) {
            skyZenith[0] = 0.045F; skyZenith[1] = 0.03F; skyZenith[2] = 0.09F;
            skyHorizon[0] = 0.16F; skyHorizon[1] = 0.10F; skyHorizon[2] = 0.22F;
        }
    }

    private float findWaterSurface(ClientWorld world, BlockPos origin) {
        // Probe a small column around the camera: if we are in an ocean, let the water
        // pass know where the surface is for caustics and wave shading.
        int top = Math.min(world.getTopY(), origin.getY() + 24);
        for (int y = top; y >= world.getBottomY(); y--) {
            BlockPos pos = new BlockPos(origin.getX(), y, origin.getZ());
            if (world.getBlockState(pos).getFluidState().isStill()) {
                return y + 1.0F;
            }
            if (world.getBlockState(pos).isSolidBlock(world, pos)) {
                break;
            }
        }
        return world.getBottomY();
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
    public FogSettings fogSettings() { return fogSettings; }
    public LightingSettings lightingSettings() { return lightingSettings; }
    public AtmosphereSettings atmosphereSettings() { return atmosphereSettings; }
    public ColorGradingSettings gradingSettings() { return gradingSettings; }
}

package dev.bedrockshaders.client.render;

/**
 * Everything {@link FrameContext} needs from the running client, expressed without a
 * single Minecraft type.
 *
 * <p>Yarn and Mojang mappings disagree on almost every getter here ({@code
 * getRenderTickCounter} vs {@code getDeltaTracker}, {@code getPos} vs {@code getPosition},
 * {@code getRainGradient} vs {@code getRainLevel}, ...). Each loader implements this
 * once in a small platform class, which keeps the 350-line frame calculation identical
 * across Fabric, Quilt, Forge and NeoForge instead of maintaining four copies that can
 * drift apart.</p>
 */
public interface ClientSnapshot {
    /** Whether a world is loaded; when false the frame is inactive. */
    boolean inWorld();

    /** The world's dimension key, e.g. {@code minecraft:overworld}. */
    String dimensionKey();

    /** Render tick delta (partial tick). */
    float tickDelta();

    double cameraX();

    double cameraY();

    double cameraZ();

    /** Window framebuffer size in pixels. */
    int framebufferWidth();

    int framebufferHeight();

    /** Configured view distance in blocks. */
    float viewDistanceBlocks();

    /** World time in ticks (0..24000 within a day). */
    long dayTime();

    /** Normalised sun angle (0..1) at the given partial tick. */
    float skyAngle(float tickDelta);

    float rainLevel(float tickDelta);

    float thunderLevel(float tickDelta);

    /** Vanilla's sky darkening (0..1), used for Bedrock's night curve. */
    float skyDarken(float tickDelta);

    /** True when the camera is submerged in water. */
    boolean cameraInWater();

    /** Java biome id at the camera position (e.g. {@code minecraft:plains}). */
    String biomeId(double x, double y, double z);

    /** Biome temperature at the given position, for Bedrock's climate fallbacks. */
    float biomeTemperature(double x, double y, double z);

    /** True when the biome at the position has precipitation. */
    boolean biomeHasPrecipitation(double x, double y, double z);

    /** World bottom Y (the void floor for water-surface detection). */
    int minBuildHeight();

    /** Highest buildable Y. */
    int maxBuildHeight();

    /**
     * Highest water block Y at or below {@code startY} in the column, or
     * {@code fallback} when no fluid is found.
     */
    float waterSurfaceHeight(int x, int startY, int z, float fallback);
}

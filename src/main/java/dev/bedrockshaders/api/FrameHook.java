package dev.bedrockshaders.api;

/**
 * Per-frame hook for an extension: the read-only frame state in, tuning nudges out.
 *
 * <p>This is the main extension point for an addon that wants to react to the world (time
 * of day, biome, weather, being underwater) without patching anything. It runs once per
 * frame on the render thread, so it must be cheap: no allocation in the common path, no I/O,
 * no GL calls. A typical implementation reads a couple of getters and fills a pre-built
 * {@link TuningDelta}.</p>
 *
 * <p>It is called after the mod has computed the frame from the pack data and before the
 * uniforms are uploaded, so anything written here is what the shaders actually see.</p>
 */
@FunctionalInterface
public interface FrameHook {

    /**
     * @param frame  read-only view of the computed frame
     * @param output mutable, reused every frame; mutate it, do not keep a reference
     */
    void apply(FrameView frame, TuningDelta output);

    /**
     * Read-only snapshot of the frame the renderer computed.
     *
     * <p>Deliberately small and additive-stable: values may be added in a later API version,
     * but existing ones keep their meaning.</p>
     */
    interface FrameView {
        /** Stable id of the active pack, or an empty string for the built-in data. */
        String packId();

        /** Recognised family id, or {@code "generic"}. */
        String familyId();

        /** World time in seconds, 0 at sunrise; ~600 is noon, ~12000 midnight. */
        float worldTimeSeconds();

        /** Sun elevation as an intensity, 0 at night, 1 at noon. */
        float sunIntensity();

        /** Moon intensity, 0 during the day. */
        float moonIntensity();

        /** Rain gradient, 0 clear .. 1 downpour. */
        float rainGradient();

        /** Biome id as reported by the loader ({@code "minecraft:plains"}). */
        String biomeId();

        /** Fog id selected for this frame ({@code "minecraft:default"}). */
        String fogId();

        boolean underwater();

        boolean nether();

        boolean end();

        /** Camera position, blocks. */
        double cameraX();

        double cameraY();

        double cameraZ();

        /** Current fog window; the mod's own values for this frame. */
        float fogStart();

        float fogEnd();
    }
}

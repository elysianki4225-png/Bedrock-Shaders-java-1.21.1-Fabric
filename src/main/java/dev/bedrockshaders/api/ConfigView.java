package dev.bedrockshaders.api;

/**
 * Read-only view of the mod's settings, handed to extensions that need to react to them.
 *
 * <p>Read-only on purpose: an addon observes and adjusts its own behaviour, it does not
 * rewrite the user's configuration behind their back. The only mutable path is
 * {@link ExtensionRegistry} registration, which is explicit and logged.</p>
 */
public interface ConfigView {

    boolean enabled();

    boolean atmosphere();

    boolean water();

    boolean ssao();

    boolean bloom();

    boolean godrays();

    boolean colorGrading();

    /** 0..1, multipled into the frame's exposure. */
    float exposure();

    float saturation();

    float contrast();

    float fogDensity();

    /** Whether the recognised pack family's tuning may be applied. */
    boolean usePackFamilyTuning();

    /** Whether the experimental RenderDragon data path is on. */
    boolean experimentalRenderDragon();

    /** The active quality preset id ({@code potato}..{@code ultra}). */
    String preset();
}

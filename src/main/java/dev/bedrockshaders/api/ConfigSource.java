package dev.bedrockshaders.api;

import java.util.function.Supplier;

/**
 * The bridge that lets the public API read the mod's settings without depending on the
 * client tree.
 *
 * <p>The API package is compiled into every loader jar and is also compiled standalone by
 * the offline verification scripts, so it cannot import {@code dev.bedrockshaders.client}.
 * The client installs a supplier at startup; until then {@link #current()} returns a
 * frozen default so {@link BedrockShadersExtension#onConfigChanged(ConfigView)} can be
 * implemented and unit-tested without a game running.</p>
 */
public final class ConfigSource {

    /** Settings as they are before the client has loaded the user's config file. */
    private static final ConfigView DEFAULT = new DefaultView();

    private static volatile Supplier<ConfigView> supplier;

    private ConfigSource() {
    }

    /** Installs the live view. Called once from the client during startup. */
    public static void install(Supplier<ConfigView> source) {
        supplier = source;
    }

    /** The current settings; never {@code null}. */
    public static ConfigView current() {
        Supplier<ConfigView> current = supplier;
        if (current == null) {
            return DEFAULT;
        }
        try {
            ConfigView view = current.get();
            return view == null ? DEFAULT : view;
        } catch (RuntimeException e) {
            // A broken view must never take the render thread down with it.
            return DEFAULT;
        }
    }

    /** Clears the installed view; used by tests and by {@link ExtensionRegistry#clear()}. */
    public static void reset() {
        supplier = null;
    }

    /** Mod-default settings, matching {@code BedrockConfig}'s field initialisers. */
    private static final class DefaultView implements ConfigView {
        @Override public boolean enabled() { return true; }
        @Override public boolean atmosphere() { return true; }
        @Override public boolean water() { return true; }
        @Override public boolean ssao() { return true; }
        @Override public boolean bloom() { return true; }
        @Override public boolean godrays() { return true; }
        @Override public boolean colorGrading() { return true; }
        @Override public float exposure() { return 1.0F; }
        @Override public float saturation() { return 1.0F; }
        @Override public float contrast() { return 1.0F; }
        @Override public float fogDensity() { return 1.0F; }
        @Override public boolean usePackFamilyTuning() { return true; }
        @Override public boolean experimentalRenderDragon() { return false; }
        @Override public String preset() { return "balanced"; }
    }
}

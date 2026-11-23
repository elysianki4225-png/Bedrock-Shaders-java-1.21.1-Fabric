package dev.bedrockshaders.api;

/**
 * One extension pack.
 *
 * <p>Implement this, list the class in {@code META-INF/services/
 * dev.bedrockshaders.api.BedrockShadersExtension} inside your jar, or (for a resource-only
 * addon) ship {@code assets/<your_id>/bedrock_shaders/api.json}. The mod discovers it and
 * calls {@link #onRegister(ExtensionRegistry)} once, after the config has loaded and before
 * the first frame.</p>
 *
 * <p>All methods have defaults, so an addon implements only what it needs. Nothing here is
 * allowed to throw into the renderer: the registry catches and disables a faulty extension
 * rather than crashing the game.</p>
 */
public interface BedrockShadersExtension {

    /** Stable id, lower-case {@code [a-z0-9_]+}. Used in logs and for conflict detection. */
    String id();

    /** API version this addon was written against; see {@link BedrockShadersApi#API_VERSION}. */
    default int apiVersion() {
        return BedrockShadersApi.API_VERSION;
    }

    /** Human-readable name for {@code /bedrock api list}. Defaults to {@link #id()}. */
    default String displayName() {
        return id();
    }

    /**
     * Called once at client init. Register families, frame hooks, config fields and pack
     * preprocessors here. Values registered here are live for the rest of the session.
     */
    default void onRegister(ExtensionRegistry registry) {
    }

    /**
     * Called after the config is loaded and again after the user changes it. Use it to read
     * the addon's own settings and to react to the mod's settings changing.
     */
    default void onConfigChanged(ConfigView config) {
    }

    /**
     * Called when an archive is about to be imported, before any file is written. Returning
     * a modified {@link PackDescriptor} changes what the importer does; returning null
     * leaves it alone.
     */
    default PackDescriptor onBeforeImport(PackDescriptor descriptor) {
        return descriptor;
    }

    /**
     * Called after an archive was imported successfully, so an addon can write extra files
     * into the generated pack (for example its own language file for a family badge).
     */
    default void onAfterImport(PackDescriptor descriptor, PackWriter writer) {
    }

    /** Called when an extension is being unloaded or the mod shuts down. */
    default void onDispose() {
    }
}

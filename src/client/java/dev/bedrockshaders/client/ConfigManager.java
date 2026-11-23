package dev.bedrockshaders.client;

import dev.bedrockshaders.BedrockShadersMod;
import dev.bedrockshaders.config.BedrockConfig;
import dev.bedrockshaders.GamePaths;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Holds the live {@link BedrockConfig} plus a small undo stack so the settings
 * screen can offer "cancel" without touching the file.
 */
public final class ConfigManager {
    private static final ConfigManager INSTANCE = new ConfigManager();

    private BedrockConfig config = BedrockConfig.defaults();
    /** Resolved on first use: the platform sets {@link GamePaths} before the first load. */
    private Path path() {
        return GamePaths.configDir.resolve(BedrockShadersMod.MOD_ID + ".json");
    }
    private final Deque<BedrockConfig> undo = new ArrayDeque<>();
    private boolean dirty = false;

    private ConfigManager() {
    }

    public static ConfigManager get() {
        return INSTANCE;
    }

    public BedrockConfig config() {
        return config;
    }

    public void load() {
        config = BedrockConfig.load(path());
        dirty = false;
        installApiConfigBridge();
        BedrockShadersMod.LOGGER.info("Bedrock Shaders config loaded from {}", path());
    }

    public void save() {
        config.save(path());
        dirty = false;
        dev.bedrockshaders.api.ExtensionRegistry.get().notifyConfigChanged();
    }

    /**
     * Points the public API at this config, so an extension pack can read the user's real
     * settings instead of the frozen defaults.
     */
    private void installApiConfigBridge() {
        dev.bedrockshaders.api.ConfigSource.install(this::apiView);
    }

    /** Read-only adapter handed to extension packs; recreated per call so it always sees the
     *  currently loaded {@link BedrockConfig} instance (undo can swap it). */
    private dev.bedrockshaders.api.ConfigView apiView() {
        BedrockConfig view = config;
        return new dev.bedrockshaders.api.ConfigView() {
            @Override public boolean enabled() { return view.enabled; }
            @Override public boolean atmosphere() { return view.atmosphere; }
            @Override public boolean water() { return view.water; }
            @Override public boolean ssao() { return view.ssao; }
            @Override public boolean bloom() { return view.bloom; }
            @Override public boolean godrays() { return view.godrays; }
            @Override public boolean colorGrading() { return view.colorGrading; }
            @Override public float exposure() { return view.exposure; }
            @Override public float saturation() { return view.saturation; }
            @Override public float contrast() { return view.contrast; }
            @Override public float fogDensity() { return view.fogDensity; }
            @Override public boolean usePackFamilyTuning() { return view.usePackFamilyTuning; }
            @Override public boolean experimentalRenderDragon() { return view.experimentalRenderDragon; }
            @Override public String preset() { return view.qualityPreset().id; }
        };
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    private boolean undoPendingOnOpen;

    /** Pushes a single undo point when a settings screen opens (init() runs repeatedly). */
    public void pushUndoOnce() {
        if (undoPendingOnOpen) {
            undoPendingOnOpen = false;
            pushUndo();
        }
    }

    /** Marks the next settings-screen open as needing an undo point. */
    public void armUndoOnOpen() {
        undoPendingOnOpen = true;
    }

    public void pushUndo() {
        BedrockConfig copy = BedrockConfig.defaults();
        copy.copyFrom(config);
        undo.push(copy);
        if (undo.size() > 32) {
            undo.removeLast();
        }
    }

    /** True when there is a change to undo; the settings screen disables the button otherwise. */
    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public void undo() {
        BedrockConfig previous = undo.poll();
        if (previous != null) {
            config.copyFrom(previous);
            dirty = true;
        }
    }

    public void resetToDefaults() {
        pushUndo();
        config.copyFrom(BedrockConfig.defaults());
        dirty = true;
    }

    public void edit(Consumer<BedrockConfig> change) {
        change.accept(config);
        dirty = true;
    }
}

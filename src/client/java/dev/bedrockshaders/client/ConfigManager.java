package dev.bedrockshaders.client;

import dev.bedrockshaders.BedrockShadersMod;
import dev.bedrockshaders.config.BedrockConfig;
import net.fabricmc.loader.api.FabricLoader;

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
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve(BedrockShadersMod.MOD_ID + ".json");
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
        config = BedrockConfig.load(path);
        dirty = false;
        BedrockShadersMod.LOGGER.info("Bedrock Shaders config loaded from {}", path);
    }

    public void save() {
        config.save(path);
        dirty = false;
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

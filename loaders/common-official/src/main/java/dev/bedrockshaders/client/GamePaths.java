package dev.bedrockshaders.client;

import java.nio.file.Path;

/**
 * The two instance directories the mod needs. Set once by the platform entrypoint,
 * because only the loader knows where {@code config/} and the instance root are
 * ({@code FabricLoader#getGameDir} vs. {@code FMLPaths.GAMEDIR}).
 */
public final class GamePaths {
    /** {@code <instance>/config}, where {@code bedrock_shaders.json} lives. */
    public static Path configDir;

    /** The instance root, i.e. the folder that contains {@code mods/} and {@code resourcepacks/}. */
    public static Path gameDir;

    private GamePaths() {
    }

    public static void set(Path config, Path game) {
        configDir = config;
        gameDir = game;
    }

    /** {@code <instance>/bedrock-shaders}: the drop folder for {@code .mcpack} archives. */
    public static Path packFolder() {
        return gameDir.resolve("bedrock-shaders");
    }

    /** Vanilla scans this folder natively, so converted packs are written here. */
    public static Path resourcePacksFolder() {
        return gameDir.resolve("resourcepacks");
    }
}

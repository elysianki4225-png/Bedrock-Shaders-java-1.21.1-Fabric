package dev.bedrockshaders.client;

import dev.bedrockshaders.GamePaths;

import dev.bedrockshaders.BedrockShadersMod;
import dev.bedrockshaders.client.assets.BedrockAssetLibrary;
import dev.bedrockshaders.client.command.BedrockShadersCommand;
import dev.bedrockshaders.client.gui.BedrockShadersScreen;
import dev.bedrockshaders.client.pack.BedrockPackImporter;
import dev.bedrockshaders.client.pack.ImportedPackActivator;
import dev.bedrockshaders.client.pack.PackRegistry;
import dev.bedrockshaders.client.render.BedrockPipeline;
import dev.bedrockshaders.client.render.BedrockShaderPack;
import dev.bedrockshaders.client.render.BedrockUniforms;
import dev.bedrockshaders.client.render.RenderCompat;
import dev.bedrockshaders.client.render.ShaderSources;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Loader-agnostic client bootstrap for the Forge and NeoForge builds.
 *
 * <p>Everything here is official-mapped vanilla API plus plain Java, so both loaders run
 * this exact class; the thin platform classes ({@code NeoForgeClient}, {@code ForgeClient})
 * only decide <em>when</em> to call {@link #init()}, how to register the key bindings and
 * command, and where {@code config/} lives.</p>
 */
public final class BedrockShadersClient {
    public static final String MOD_ID = BedrockShadersMod.MOD_ID;

    /** Supplied by the platform entrypoint, because only it knows the config directory. */
    public interface Platform {
        java.nio.file.Path configDir();

        /** Instance root: the folder containing {@code mods/} and {@code resourcepacks/}. */
        java.nio.file.Path gameDir();

        /** Registers a key mapping; returns the live instance the tick handler polls. */
        KeyMapping registerKey(String descriptionId, int defaultKey);

        /** Ids of every loaded mod, so the compatibility check can see Iris/VulkanMod. */
        java.util.List<String> loadedModIds();

        /** Whether a mod id is loaded; used for ids that were not listed above. */
        boolean isModLoaded(String modId);
    }

    private static Platform platform;
    private static KeyMapping openMenuKey;
    private static KeyMapping toggleKey;
    private static boolean pendingPackSync = true;

    /** Cleared after the first world join, so the Fabric recommendation shows once. */
    private static boolean pendingFabricHint = true;
    private static boolean initialised;

    private BedrockShadersClient() {
    }

    public static void init(Platform platform) {
        if (initialised) {
            return;
        }
        initialised = true;
        BedrockShadersClient.platform = platform;
        GamePaths.set(platform.configDir(), platform.gameDir());

        // Decide what may run before anything touches GL: under VulkanMod even compiling our
        // post programs would crash the client, and next to Iris two pipelines would fight
        // over the same framebuffer.
        RenderCompat.install(platform::isModLoaded, platform.loadedModIds());

        ConfigManager.get().load();

        // Instance folder that behaves like Iris' shaderpacks folder: drop a .mcpack in
        // and it is converted + enabled on this launch.
        PackRegistry.get().ensureFolders();
        PackRegistry.get().load();
        if (ConfigManager.get().config().autoImportPacks) {
            List<BedrockPackImporter.Result> imported = PackRegistry.get().importPending();
            for (BedrockPackImporter.Result result : imported) {
                if (!result.success()) {
                    BedrockShadersMod.LOGGER.warn("Bedrock pack import skipped: {}", result.message());
                }
            }
            if (!imported.isEmpty()) {
                BedrockShadersMod.LOGGER.info("Imported {} Bedrock archive(s) from {}",
                        imported.size(), PackRegistry.importFolder());
            }
        }

        BedrockShaderPack.setPackRoot(platform.configDir().resolve("bedrock-shaders").resolve("pack"));

        // The pipeline only needs three values from the main framebuffer; the platform
        // peeks at the game type so the pipeline stays loader- and version-agnostic.
        BedrockPipeline.setMainFramebufferSupplier(() -> {
            var target = Minecraft.getInstance().getMainRenderTarget();
            return new dev.bedrockshaders.client.render.VanillaFramebuffer() {
                @Override
                public int framebufferId() {
                    return target.frameBufferId;
                }

                @Override
                public int width() {
                    return target.width;
                }

                @Override
                public int height() {
                    return target.height;
                }
            };
        });

        openMenuKey = platform.registerKey("key.bedrock_shaders.open_menu", GLFW.GLFW_KEY_F9);
        toggleKey = platform.registerKey("key.bedrock_shaders.toggle", GLFW.GLFW_KEY_F10);

        BedrockShadersMod.LOGGER.info("Bedrock Shaders client initialised (import folder: {})",
                PackRegistry.importFolder());
    }

    public static boolean isInitialised() {
        return initialised;
    }

    /** Runs inside the client tick event registered by the platform class. */
    public static void onClientTick(Minecraft client) {
        if (!initialised || client == null) {
            return;
        }
        if (pendingPackSync) {
            pendingPackSync = false;
            ImportedPackActivator.reload(client);
        }
        // Forge/NeoForge builds: one quiet, once-per-launch note that Fabric is the build
        // this mod is written against. Not a nag (no repeat, no screen, no sound), and it
        // still fires when the renderer has been downgraded for Iris/VulkanMod, because
        // that is exactly the situation where the Fabric build would serve them better.
        if (pendingFabricHint && client.player != null) {
            pendingFabricHint = false;
            client.player.displayClientMessage(
                    Component.translatable("bedrock_shaders.message.prefer_fabric"), false);
        }
        while (openMenuKey != null && openMenuKey.consumeClick()) {
            if (client.screen == null) {
                ConfigManager.get().armUndoOnOpen();
                client.setScreen(new BedrockShadersScreen(null));
            }
        }
        while (toggleKey != null && toggleKey.consumeClick()) {
            boolean enabled = !ConfigManager.get().config().enabled;
            ConfigManager.get().edit(config -> config.enabled = enabled);
            ConfigManager.get().save();
            BedrockShaderPack.regenerateFromCurrentResources();
            if (client.player != null) {
                client.player.displayClientMessage(Component.translatable(enabled
                        ? "bedrock_shaders.message.enabled"
                        : "bedrock_shaders.message.disabled"), true);
            }
        }
    }

    /**
     * Resource reload: refresh shader sources, Bedrock assets and regenerate the pack.
     * Called from each loader's resource-reload event with its own {@link ResourceManager}.
     */
    public static void onReload(ResourceManager manager) {
        ShaderSources.clearCache();
        OfficialResourceSource source = new OfficialResourceSource(manager);
        BedrockShaderPack.setCurrentSource(source);
        ShaderSources.reload(source);
        BedrockAssetLibrary.get().reload(source);
        BedrockUniforms.invalidate();
        // Generating the override pack is pointless (and under Iris, actively harmful: it
        // would fight Iris' own core programs) when the renderer is downgraded.
        if (RenderCompat.get().allowsGlWork()) {
            BedrockShaderPack.ensureGenerated(source, true);
        }
        ImportedPackActivator.invalidate();
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.level == null) {
            // Outside a world the pipeline is recreated lazily on the next frame.
            BedrockPipeline.get().close();
        }
    }

    /** Platform entrypoints call this once their command dispatcher is available. */
    public static void registerCommands(Object dispatcher) {
        if (dispatcher instanceof com.mojang.brigadier.CommandDispatcher<?> commands) {
            BedrockShadersCommand.register(cast(commands));
        }
    }

    @SuppressWarnings("unchecked")
    private static com.mojang.brigadier.CommandDispatcher<
            net.minecraft.commands.CommandSourceStack> cast(
            com.mojang.brigadier.CommandDispatcher<?> dispatcher) {
        return (com.mojang.brigadier.CommandDispatcher<
                net.minecraft.commands.CommandSourceStack>) (Object) dispatcher;
    }
}

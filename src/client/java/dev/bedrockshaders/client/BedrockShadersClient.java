package dev.bedrockshaders.client;

import dev.bedrockshaders.BedrockShadersMod;
import dev.bedrockshaders.GamePaths;
import dev.bedrockshaders.client.assets.BedrockAssetLibrary;
import dev.bedrockshaders.client.command.BedrockShadersCommand;
import dev.bedrockshaders.client.pack.BedrockPackImporter;
import dev.bedrockshaders.client.pack.ImportedPackActivator;
import dev.bedrockshaders.client.pack.PackRegistry;
import dev.bedrockshaders.client.gui.BedrockShadersScreen;
import dev.bedrockshaders.client.render.BedrockPipeline;
import dev.bedrockshaders.client.render.BedrockShaderPack;
import dev.bedrockshaders.client.render.BedrockUniforms;
import dev.bedrockshaders.client.render.ShaderSources;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Client entrypoint: loads the config, generates the runtime shader pack, registers
 * the resource reload listeners, the key binding and the {@code /bedrock} command.
 */
public final class BedrockShadersClient implements ClientModInitializer {
    public static final String MOD_ID = BedrockShadersMod.MOD_ID;
    private static KeyBinding openMenuKey;
    private static KeyBinding toggleKey;
    private static boolean packRegistered;

    @Override
    public void onInitializeClient() {
        // Fabric is the only loader whose paths are resolved here rather than from an
        // FML service; everything downstream reads GamePaths so the shared pack code stays
        // free of loader imports.
        GamePaths.set(FabricLoader.getInstance().getConfigDir(),
                FabricLoader.getInstance().getGameDir());

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

        BedrockShaderPack.setPackRoot(FabricLoader.getInstance().getConfigDir()
                .resolve("bedrock-shaders").resolve("pack"));

        FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(container ->
                packRegistered = ResourceManagerHelper.registerBuiltinResourcePack(
                        Identifier.of(MOD_ID, "runtime_shaders"),
                        container,
                        Text.literal("Bedrock Shaders (runtime)"),
                        ResourcePackActivationType.DEFAULT_ENABLED));

        // The render pipeline only needs three values from the main framebuffer; keep the
        // game-type dependency here so the pipeline stays loader-agnostic.
        BedrockPipeline.setMainFramebufferSupplier(() -> {
            var framebuffer = MinecraftClient.getInstance().getFramebuffer();
            return new dev.bedrockshaders.client.render.VanillaFramebuffer() {
                @Override
                public int framebufferId() {
                    return framebuffer.fbo;
                }

                @Override
                public int width() {
                    return framebuffer.textureWidth;
                }

                @Override
                public int height() {
                    return framebuffer.textureHeight;
                }
            };
        });

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new ReloadListener());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                BedrockShadersCommand.register(dispatcher));

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.bedrock_shaders.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                "key.categories.bedrock_shaders"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.bedrock_shaders.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F10,
                "key.categories.bedrock_shaders"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingPackSync) {
                pendingPackSync = false;
                ImportedPackActivator.reload(client);
            }
            // One-time hint on first join: without it a new player has no way to know the
            // menu exists or that a key toggles the renderer.
            if (pendingWelcome && client.player != null && client.world != null) {
                pendingWelcome = false;
                client.player.sendMessage(Text.translatable("bedrock_shaders.message.welcome",
                        openMenuKey.getBoundKeyLocalizedText(),
                        toggleKey.getBoundKeyLocalizedText()), false);
            }
            while (openMenuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    ConfigManager.get().armUndoOnOpen();
                    client.setScreen(new BedrockShadersScreen(null));
                }
            }
            while (toggleKey.wasPressed()) {
                boolean enabled = !ConfigManager.get().config().enabled;
                ConfigManager.get().edit(config -> config.enabled = enabled);
                ConfigManager.get().save();
                BedrockShaderPack.regenerateFromCurrentResources();
                if (client.player != null) {
                    client.player.sendMessage(Text.translatable(enabled
                            ? "bedrock_shaders.message.enabled"
                            : "bedrock_shaders.message.disabled"), true);
                }
            }
        });

        BedrockShadersMod.LOGGER.info("Bedrock Shaders client initialised (builtin pack: {}, import folder: {})",
                packRegistered, PackRegistry.importFolder());
    }

    /** Set once the client is far enough through startup for a resource reload. */
    private static boolean pendingPackSync = true;

    /** Cleared after the first join, so the welcome hint shows exactly once per launch. */
    private static boolean pendingWelcome = true;

    public static boolean isPackRegistered() {
        return packRegistered;
    }

    /** Resource reload: refresh shader sources, Bedrock assets and regenerate the pack. */
    private static final class ReloadListener implements SimpleSynchronousResourceReloadListener {
        private static final Identifier ID = Identifier.of(MOD_ID, "renderer");

        @Override
        public Identifier getFabricId() {
            return ID;
        }

        @Override
        public void reload(ResourceManager manager) {
            ShaderSources.clearCache();
            FabricResourceSource source = new FabricResourceSource(manager);
            BedrockShaderPack.setCurrentSource(source);
            ShaderSources.reload(source);
            BedrockAssetLibrary.get().reload(source);
            BedrockUniforms.invalidate();
            BedrockShaderPack.setCurrentSource(source);
            BedrockShaderPack.ensureGenerated(source, true);
            ImportedPackActivator.invalidate();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.world == null) {
                // Outside a world the pipeline is recreated lazily on the next frame.
                BedrockPipeline.get().close();
            }
        }
    }
}

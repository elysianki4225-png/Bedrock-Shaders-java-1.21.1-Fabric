package dev.bedrockshaders.forge;

import dev.bedrockshaders.client.BedrockReloadListener;
import dev.bedrockshaders.client.BedrockShadersClient;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;

/**
 * Forge client entrypoint. Same structure as the NeoForge one: Forge fires its
 * registration events later than 1.20, so the reload listener, key bindings and command
 * all arrive through the mod event bus as {@code @SubscribeEvent} hooks.
 */
@Mod(value = "bedrock_shaders", dist = Dist.CLIENT)
public final class ForgeClient {
    /** Filled by {@link BedrockShadersClient#init}; registered when the mod bus fires. */
    private static final List<KeyMapping> PENDING_KEYS = new ArrayList<>();

    public ForgeClient() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterKeys);
        modBus.addListener(this::onRegisterReloadListeners);
        modBus.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        BedrockShadersClient.init(new BedrockShadersClient.Platform() {
            @Override
            public java.nio.file.Path configDir() {
                return FMLPaths.CONFIGDIR.get();
            }

            @Override
            public java.nio.file.Path gameDir() {
                return FMLPaths.GAMEDIR.get();
            }

            @Override
            public KeyMapping registerKey(String descriptionId, int defaultKey) {
                KeyMapping mapping = new KeyMapping(descriptionId, defaultKey,
                        "key.categories.bedrock_shaders");
                PENDING_KEYS.add(mapping);
                return mapping;
            }
        });
    }

    private void onRegisterKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping mapping : PENDING_KEYS) {
            event.register(mapping);
        }
    }

    private void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new BedrockReloadListener());
    }

    private void onRegisterCommands(RegisterClientCommandsEvent event) {
        BedrockShadersClient.registerCommands(event.getDispatcher());
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            BedrockShadersClient.onClientTick(net.minecraft.client.Minecraft.getInstance());
        }
    }
}

package dev.bedrockshaders.neoforge;

import dev.bedrockshaders.client.BedrockReloadListener;
import dev.bedrockshaders.client.BedrockShadersClient;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge client entrypoint. All the real work lives in {@link BedrockShadersClient};
 * this class only translates NeoForge's event bus into the shared bootstrap calls.
 */
@Mod(value = "bedrock_shaders", dist = Dist.CLIENT)
public final class NeoForgeClient {
    /** Filled by {@link BedrockShadersClient#init}; registered when the mod bus fires. */
    private final List<KeyMapping> pendingKeys = new ArrayList<>();

    private KeyMapping[] registeredKeys = new KeyMapping[0];

    public NeoForgeClient(ModContainer container, IEventBus modBus) {
        modBus.addListener(this::onRegisterKeys);
        modBus.addListener(NeoForgeClient::onRegisterCommands);
        modBus.addListener(NeoForgeClient::onRegisterReloadListeners);

        NeoForge.EVENT_BUS.addListener(NeoForgeClient::onClientTick);

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
            public java.util.List<String> loadedModIds() {
                java.util.List<String> ids = new ArrayList<>();
                for (net.neoforged.neoforgespi.language.IModInfo info : net.neoforged.fml.ModList.get().getMods()) {
                    ids.add(info.getModId());
                }
                return ids;
            }

            @Override
            public boolean isModLoaded(String modId) {
                return net.neoforged.fml.ModList.get().isLoaded(modId);
            }

            @Override
            public KeyMapping registerKey(String descriptionId, int defaultKey) {
                // Stored now, handed to RegisterKeyMappingsEvent below: the event fires
                // after this constructor, and the client ticks the same instance.
                KeyMapping mapping = new KeyMapping(descriptionId, defaultKey,
                        "key.categories.bedrock_shaders");
                pendingKeys.add(mapping);
                return mapping;
            }
        });

        registeredKeys = pendingKeys.toArray(new KeyMapping[0]);
    }

    private void onRegisterKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping mapping : registeredKeys) {
            event.register(mapping);
        }
    }

    private static void onRegisterCommands(RegisterClientCommandsEvent event) {
        BedrockShadersClient.registerCommands(event.getDispatcher());
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new BedrockReloadListener());
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        BedrockShadersClient.onClientTick(net.minecraft.client.Minecraft.getInstance());
    }
}

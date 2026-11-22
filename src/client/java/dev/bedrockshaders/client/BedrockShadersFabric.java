package dev.bedrockshaders.client;

import dev.bedrockshaders.BedrockShadersMod;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric entrypoint.
 *
 * <p>Kept separate from {@link BedrockShadersMod} - and inside the client source set - so
 * the shared constants class stays free of loader APIs and can be compiled into the Forge
 * and NeoForge jars as well.</p>
 */
public final class BedrockShadersFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BedrockShadersMod.LOGGER.info(
                "Bedrock Shaders {} loaded (client renderer initialises on the client entrypoint).",
                BedrockShadersMod.VERSION);
    }
}

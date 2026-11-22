package dev.bedrockshaders;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint.
 *
 * <p>Everything that touches Minecraft's renderer lives in the client source set;
 * this entrypoint only exposes shared constants and boot logging so the mod is
 * safe to install on a dedicated server (where it simply does nothing).</p>
 */
public final class BedrockShadersMod implements ModInitializer {
    public static final String MOD_ID = "bedrock_shaders";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("BedrockShaders");

    @Override
    public void onInitialize() {
        LOGGER.info("Bedrock Shaders {} loaded (client renderer initialises on the client entrypoint).", VERSION);
    }
}

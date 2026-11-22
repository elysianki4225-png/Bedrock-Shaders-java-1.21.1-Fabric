package dev.bedrockshaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common mod surface for the Forge and NeoForge builds.
 *
 * <p>The Fabric build has its own {@code BedrockShadersMod implements ModInitializer} in
 * {@code src/main/java}; that file is not part of these source sets. Both classes expose the
 * same {@code MOD_ID} and {@code LOGGER} constants, which is all the shared code uses.</p>
 */
public final class BedrockShadersMod {
    public static final String MOD_ID = "bedrock_shaders";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private BedrockShadersMod() {
    }
}

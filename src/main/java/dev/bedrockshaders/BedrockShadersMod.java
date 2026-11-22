package dev.bedrockshaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared mod constants.
 *
 * <p>Deliberately free of any loader API: this class is compiled into the Fabric, Quilt,
 * Forge and NeoForge jars alike. The Fabric entrypoint lives in {@link BedrockShadersFabric}
 * and the Forge/NeoForge entrypoints live in their own source sets.</p>
 */
public final class BedrockShadersMod {
    public static final String MOD_ID = "bedrock_shaders";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("BedrockShaders");

    private BedrockShadersMod() {
    }
}

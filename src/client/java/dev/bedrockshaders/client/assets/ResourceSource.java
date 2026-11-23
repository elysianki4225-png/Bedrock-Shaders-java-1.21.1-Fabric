package dev.bedrockshaders.client.assets;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * The two resource-manager operations the Bedrock asset loader needs, without any
 * Minecraft type.
 *
 * <p>Yarn calls the manager {@code ResourceManager} and ids {@code Identifier}; Mojang
 * calls them {@code ResourceManager} and {@code ResourceLocation}. Both can hand back an
 * {@link InputStream} for an id and list ids under a directory, so the asset parsing and
 * shader flattening code is shared verbatim.</p>
 */
public interface ResourceSource {
    /**
     * Opens a resource, or returns {@code null} when it does not exist in any pack.
     * The caller closes the stream.
     */
    InputStream open(String path);

    /**
     * Every {@code *_fog_setting.json} style path under {@code directory}, as
     * mod-relative paths such as {@code bedrock/fogs/plains_fog_setting.json}.
     */
    List<String> list(String directory) throws IOException;
}

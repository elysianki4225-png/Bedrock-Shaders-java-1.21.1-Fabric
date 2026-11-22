package dev.bedrockshaders.client;

import dev.bedrockshaders.BedrockShadersMod;
import dev.bedrockshaders.client.assets.ResourceSource;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Yarn-side {@link ResourceSource}: the only place the asset loader touches
 * {@code ResourceManager}/{@code Identifier}.
 */
public record FabricResourceSource(ResourceManager manager) implements ResourceSource {
    @Override
    public InputStream open(String path) {
        Identifier identifier = Identifier.of(BedrockShadersMod.MOD_ID, path);
        Optional<Resource> resource = manager.getResource(identifier);
        if (resource.isEmpty()) {
            return null;
        }
        try {
            return resource.get().getInputStream();
        } catch (IOException e) {
            BedrockShadersMod.LOGGER.warn("Could not open {}", identifier, e);
            return null;
        }
    }

    @Override
    public List<String> list(String directory) {
        List<String> found = new ArrayList<>();
        try {
            manager.findResources(directory, identifier ->
                            identifier.getNamespace().equals(BedrockShadersMod.MOD_ID))
                    .keySet()
                    .forEach(identifier -> found.add(identifier.getPath()));
        } catch (RuntimeException e) {
            BedrockShadersMod.LOGGER.warn("Could not list {}", directory, e);
        }
        return found;
    }
}

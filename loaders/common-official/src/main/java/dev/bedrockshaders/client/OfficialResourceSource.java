package dev.bedrockshaders.client;

import dev.bedrockshaders.BedrockShadersMod;
import dev.bedrockshaders.client.assets.ResourceSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mojang-mapped {@link ResourceSource}, shared by the Forge and NeoForge builds: since
 * 1.21 the resource-manager API is identical on both loaders.
 */
public record OfficialResourceSource(ResourceManager manager) implements ResourceSource {
    @Override
    public InputStream open(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BedrockShadersMod.MOD_ID, path);
        Optional<Resource> resource = manager.getResource(id);
        if (resource.isEmpty()) {
            return null;
        }
        try {
            return resource.get().open();
        } catch (IOException e) {
            BedrockShadersMod.LOGGER.warn("Could not open {}", id, e);
            return null;
        }
    }

    @Override
    public List<String> list(String directory) {
        List<String> found = new ArrayList<>();
        try {
            Map<ResourceLocation, Resource> resources = manager.listResources(directory,
                    id -> id.getNamespace().equals(BedrockShadersMod.MOD_ID));
            resources.keySet().forEach(id -> found.add(id.getPath()));
        } catch (RuntimeException e) {
            BedrockShadersMod.LOGGER.warn("Could not list {}", directory, e);
        }
        return found;
    }
}

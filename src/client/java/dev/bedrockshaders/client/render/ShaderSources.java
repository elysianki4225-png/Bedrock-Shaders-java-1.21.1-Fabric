package dev.bedrockshaders.client.render;

import dev.bedrockshaders.BedrockShadersMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the Bedrock shader sources that ship in the jar at
 * {@code assets/bedrock_shaders/bedrockshader/*} and prepares them for vanilla's
 * core-shader importer by flattening {@code #moj_import <...>} includes.
 *
 * <p>Sources are reloaded on resource reloads so pack overrides are respected.</p>
 */
public final class ShaderSources {
    private static final String ROOT = "bedrockshader/";
    private static final Map<String, String> CACHE = new HashMap<>();

    private ShaderSources() {
    }

    public static Identifier id(String path) {
        return Identifier.of(BedrockShadersMod.MOD_ID, ROOT + path);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /** Returns the fully flattened GLSL for {@code core/<name>.vsh|fsh}. */
    public static String get(ResourceManager manager, String stage) {
        String cached = CACHE.get(stage);
        if (cached != null) {
            return cached;
        }
        String source = readRaw(manager, "core/" + stage);
        String flattened = flatten(manager, source, 0);
        CACHE.put(stage, flattened);
        return flattened;
    }

    private static String readRaw(ResourceManager manager, String path) {
        Identifier identifier = id(path);
        Optional<Resource> resource = manager.getResource(identifier);
        if (resource.isEmpty()) {
            throw new IllegalStateException("Missing Bedrock shader source " + identifier);
        }
        try (InputStream stream = resource.get().getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + identifier, e);
        }
    }

    /**
     * Flattens {@code #moj_import <name>} directives exactly like vanilla's
     * {@code GlImportProcessor}, but also supports {@code #moj_import "name"}.
     * Include files must reside under {@code bedrockshader/include/}.
     */
    public static String flatten(ResourceManager manager, String source, int depth) {
        if (depth > 8) {
            throw new IllegalStateException("Bedrock shader include depth exceeded");
        }
        StringBuilder out = new StringBuilder(source.length() + 4096);
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#moj_import")) {
                String target = trimmed.substring("#moj_import".length()).trim();
                if (target.startsWith("<") && target.endsWith(">")) {
                    target = target.substring(1, target.length() - 1);
                } else if (target.startsWith("\"") && target.endsWith("\"")) {
                    target = target.substring(1, target.length() - 1);
                }
                String included = flatten(manager, readRaw(manager, "include/" + target), depth + 1);
                out.append("// begin ").append(target).append('\n');
                out.append(included);
                out.append("// end ").append(target).append('\n');
            } else {
                out.append(line);
                out.append('\n');
            }
        }
        return out.toString();
    }

    /** Called by the resource reload listener. */
    public static void reload(MinecraftClient client) {
        clearCache();
        if (client == null) {
            return;
        }
        ShaderSources.get(client.getResourceManager(), "terrain.vsh");
        ShaderSources.get(client.getResourceManager(), "terrain.fsh");
        ShaderSources.get(client.getResourceManager(), "bedrock_water.vsh");
        ShaderSources.get(client.getResourceManager(), "bedrock_water.fsh");
        ShaderSources.get(client.getResourceManager(), "entity.vsh");
        ShaderSources.get(client.getResourceManager(), "entity.fsh");
    }
}

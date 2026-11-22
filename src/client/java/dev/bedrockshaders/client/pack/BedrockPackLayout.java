package dev.bedrockshaders.client.pack;

import java.util.List;
import java.util.Locale;

/**
 * Recognises where Bedrock stores Deferred Rendering data inside a pack and works out
 * the resource-pack path each JSON document has to be copied to.
 *
 * <p>Bedrock packs are inconsistent: the pipeline data lives at {@code fogs/},
 * {@code lighting/}, ... in the official samples, but community packs bury the same
 * files under an arbitrary top level folder. Matching is therefore done on the file
 * name pattern plus the parent directory, anywhere below the pack root.</p>
 */
public final class BedrockPackLayout {
    /** Directories this mod can consume; anything else in a dropped pack is ignored. */
    public static final List<String> DATA_DIRS = List.of("fogs", "lighting", "atmospherics", "color_grading");

    private BedrockPackLayout() {
    }

    /** A Bedrock JSON document mapped onto the mod's asset namespace. */
    public record MappedFile(String sourcePath, String targetPath) {
    }

    /**
     * Returns the asset path this entry should be copied to, or {@code null} when the
     * file is not Bedrock rendering data. {@code entryPath} is the zip entry path with
     * forward slashes and no leading {@code ./}.
     */
    public static String targetFor(String entryPath) {
        String lower = entryPath.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".json")) {
            return null;
        }
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);
        String parent = parentDir(lower);

        if (fileName.endsWith("_fog_setting.json") || fileName.equals("fog_settings.json")) {
            return "fogs/" + fileName;
        }
        if (fileName.endsWith("_color_grading.json") || fileName.equals("color_grading.json")
                || fileName.equals("color_grading_settings.json")) {
            return "color_grading/" + fileName;
        }
        if (fileName.endsWith("_lighting.json") || fileName.equals("lighting.json")
                || fileName.equals("lighting_settings.json") || fileName.equals("global.json") && parent.endsWith("lighting")) {
            return "lighting/" + fileName;
        }
        if (fileName.endsWith("_atmospherics.json") || fileName.equals("atmospherics.json")
                || fileName.equals("atmosphere_settings.json")) {
            return "atmospherics/" + fileName;
        }
        return null;
    }

    /** True when the entry is the water colour table ({@code biomes_client.json}). */
    public static boolean isWaterTable(String entryPath) {
        String lower = entryPath.toLowerCase(Locale.ROOT);
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);
        return fileName.equals("biomes_client.json") || fileName.equals("biomes.json");
    }

    /** True when the entry is a Bedrock manifest (any depth, so nested packs work). */
    public static boolean isManifest(String entryPath) {
        String lower = entryPath.toLowerCase(Locale.ROOT);
        return lower.equals("manifest.json") || lower.endsWith("/manifest.json");
    }

    /**
     * True when the entry must not be copied: manifests, textures, models and other
     * non-rendering data would only bloat the generated Java resource pack.
     */
    public static boolean isIgnored(String entryPath) {
        String lower = entryPath.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/")) {
            return true;
        }
        return lower.contains("/textures/") || lower.contains("/models/") || lower.contains("/sounds/")
                || lower.contains("/font/") || lower.contains("/particles/") || lower.contains("/ui/")
                || lower.startsWith("textures/") || lower.startsWith("models/") || lower.startsWith("sounds/")
                || lower.startsWith("font/") || lower.startsWith("ui/")
                || lower.endsWith(".png") || lower.endsWith(".tga") || lower.endsWith(".ogg")
                || lower.endsWith(".fsb") || lower.endsWith(".material") || lower.endsWith(".bin")
                || lower.equals("pack_icon.png") || lower.endsWith("/pack_icon.png")
                || lower.endsWith(".lang") || lower.endsWith("contents.json") || lower.endsWith("splashes.json")
                || lower.endsWith("signatures.json") || lower.endsWith("world_icon.jpeg")
                || lower.startsWith("__macosx/");
    }

    private static String parentDir(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? "" : path.substring(0, index);
    }
}

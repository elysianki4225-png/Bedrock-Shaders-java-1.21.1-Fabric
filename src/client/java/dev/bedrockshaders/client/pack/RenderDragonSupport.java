package dev.bedrockshaders.client.pack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Recognises a Bedrock <b>RenderDragon</b> shader pack.
 *
 * <p>Bedrock has two shader systems. The old one is data-only: loose fog / lighting /
 * atmospherics / colour-grading JSON, which this mod imports and which describes its intent
 * completely. The new one, RenderDragon, ships a compiled graphics pipeline instead:</p>
 *
 * <pre>
 *   renderer/materials/*.material
 *   renderer/materials/*.material.bin
 *   renderer/lighting/global.json
 *   renderer/shadows/*.json
 *   renderer/sky/*.json
 *   renderer/water/*.json
 * </pre>
 *
 * <p>The {@code .material.bin} files are compiled binaries for Bedrock's own pipeline.
 * <b>They cannot be translated to Java Edition</b> - there is no equivalent stage, no
 * Java-side compiler, and their contents are opaque. No mod can run them; what a
 * "RenderDragon shader" looks like on Java has to be re-implemented in GLSL from scratch.</p>
 *
 * <p>So "support" here means something specific and honest:</p>
 * <ul>
 *   <li>the pack is <b>detected</b>, and the user is told which parts can and cannot work;</li>
 *   <li>the data half (fog, lighting, atmos, grading, shadows, sky, water JSON) <b>is</b>
 *       imported, so the pack's authored colours and densities still apply;</li>
 *   <li>the binary half is <b>not</b> claimed to work. It is skipped, deliberately.</li>
 * </ul>
 *
 * <p>Detection is a pure function over entry names, which is what makes it testable
 * offline and cheap enough to run on every archive.</p>
 */
public final class RenderDragonSupport {

    /** Marker extension of the compiled pipeline, the clearest single signal. */
    public static final String BINARY_SUFFIX = ".material.bin";

    /** RenderDragon's authored data half; these JSON files are importable. */
    private static final List<String> DATA_HINTS = List.of(
            "renderer/lighting/", "renderer/shadows/", "renderer/sky/", "renderer/water/",
            "renderer/fogs/", "renderer/atmospherics/", "renderer/color_grading/");

    /** Where RenderDragon keeps its material definitions. */
    private static final String MATERIALS_DIR = "renderer/materials/";

    private RenderDragonSupport() {
    }

    /** What an archive turned out to contain. */
    public record Detection(boolean renderDragon, int binaries, int dataFiles, List<String> samples,
                            List<String> importable) {

        /** No RenderDragon content at all: the ordinary deferred-data path. */
        public static Detection none() {
            return new Detection(false, 0, 0, List.of(), List.of());
        }

        /** True when the archive is RenderDragon but nothing in it can be imported. */
        public boolean binaryOnly() {
            return renderDragon && importable.isEmpty();
        }

        /** Short human-readable summary for a log line or a chat warning. */
        public String describe() {
            if (!renderDragon) {
                return "not RenderDragon";
            }
            StringBuilder out = new StringBuilder("RenderDragon");
            if (binaries > 0) {
                out.append(": ").append(binaries).append(" compiled material file(s)");
            }
            if (dataFiles > 0) {
                out.append(binaries > 0 ? ", " : ": ").append(dataFiles).append(" data file(s)");
            }
            return out.toString();
        }
    }

    /**
     * Inspects the entry names of one archive level.
     *
     * @param entryPaths every non-directory entry, with forward slashes
     */
    public static Detection detect(List<String> entryPaths) {
        List<String> samples = new ArrayList<>();
        List<String> importable = new ArrayList<>();
        int binaries = 0;
        int dataFiles = 0;

        for (String raw : entryPaths) {
            String path = normalise(raw);
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.isEmpty()) {
                continue;
            }

            boolean binary = lower.endsWith(BINARY_SUFFIX);
            boolean material = lower.endsWith(".material");
            boolean underMaterials = lower.startsWith(MATERIALS_DIR);
            boolean data = lower.endsWith(".json") && DATA_HINTS.stream().anyMatch(lower::startsWith);

            if (binary) {
                binaries++;
                remember(samples, path);
            } else if (material || underMaterials) {
                // A .material is the definition for the sibling .material.bin: it is part of
                // the compiled pipeline, not a description that can be applied as-is.
                remember(samples, path);
            } else if (data) {
                dataFiles++;
                importable.add(path);
                remember(samples, path);
            }
        }

        boolean renderDragon = binaries > 0 || dataFiles > 0
                || samples.stream().anyMatch(p -> p.toLowerCase(Locale.ROOT).startsWith(MATERIALS_DIR));
        return renderDragon
                ? new Detection(true, binaries, dataFiles, List.copyOf(samples), List.copyOf(importable))
                : Detection.none();
    }

    private static void remember(List<String> samples, String path) {
        if (samples.size() < 6) {
            samples.add(path);
        }
    }

    /**
     * Maps a RenderDragon data file onto the mod's asset namespace, or {@code null} when it
     * is not one of the files that can be applied.
     *
     * <p>Only the authored JSON is considered. It is copied verbatim, so a pack's own fog
     * and colour values survive; the compiled pipeline next to it is never touched.</p>
     */
    public static String targetFor(String entryPath) {
        String path = normalise(entryPath);
        String lower = path.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".json")) {
            return null;
        }
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);

        if (lower.startsWith("renderer/fogs/")) {
            return "fogs/" + fileName;
        }
        if (lower.startsWith("renderer/lighting/")) {
            return "lighting/" + fileName;
        }
        if (lower.startsWith("renderer/atmospherics/")) {
            return "atmospherics/" + fileName;
        }
        if (lower.startsWith("renderer/color_grading/")) {
            return "color_grading/" + fileName;
        }
        if (lower.startsWith("renderer/shadows/")) {
            return "shadows/" + fileName;
        }
        if (lower.startsWith("renderer/sky/")) {
            return "sky/" + fileName;
        }
        if (lower.startsWith("renderer/water/")) {
            return "water/" + fileName;
        }
        return null;
    }

    /**
     * True for entries the RenderDragon path must never copy: the compiled pipeline, its
     * material definitions and the renderer's textures. Shipping them would multiply the
     * generated pack's size for data nothing can read.
     */
    public static boolean isBinaryPipeline(String entryPath) {
        String lower = normalise(entryPath).toLowerCase(Locale.ROOT);
        return lower.endsWith(BINARY_SUFFIX) || lower.endsWith(".material") || lower.startsWith(MATERIALS_DIR);
    }

    private static String normalise(String name) {
        String value = name.replace('\\', '/');
        while (value.startsWith("./")) {
            value = value.substring(2);
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    /** Counts used by the import result, so one warning can name what was skipped. */
    public static Map<String, Integer> summarise(Detection detection) {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("binaries", detection.binaries());
        out.put("data", detection.dataFiles());
        return out;
    }
}

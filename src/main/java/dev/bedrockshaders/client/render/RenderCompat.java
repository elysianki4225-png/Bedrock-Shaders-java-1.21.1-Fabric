package dev.bedrockshaders.client.render;

import dev.bedrockshaders.BedrockShadersMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Decides how much of the Bedrock renderer may run next to other mods.
 *
 * <p>The renderer does two invasive things: it replaces five vanilla core shader programs
 * with its own GLSL, and it runs a post-processing chain with its own framebuffers that
 * blits back over the vanilla main framebuffer. That is fine by itself, but several widely
 * used mods own the same ground:</p>
 *
 * <ul>
 *   <li><b>Iris</b> (with Sodium) installs its own shader pipeline over the same five
 *       programs and the same main framebuffer. Two pipelines cannot both own the frame:
 *       the result is double tone mapping, a black screen, or a crash in the blit. Iris
 *       loses nothing if we step aside, and stepping aside is what was asked for.</li>
 *   <li><b>VulkanMod</b> replaces the entire GL backend. Every {@code glXxx} call in
 *       {@link GlObjects} becomes undefined there, so no GL work may run at all.</li>
 *   <li><b>Embeddium / Rubidium / Sodium itself</b> only replace the chunk render backend.
 *       They do not own the core shader programs or the main framebuffer, so they coexist
 *       fine and must NOT trigger a downgrade.</li>
 *   <li><b>Lithium / FerriteCore / ModernFix / ImmediatelyFast / EntityCulling / C2ME /
 *       Krypton / Starlight</b> touch simulation, memory, culling or networking - never the
 *       GL pipeline. They need no special handling; this class only records them so the
 *       debug output can show what was detected.</li>
 * </ul>
 *
 * <p>Everything here is pure logic on a list of loaded mod ids: no Minecraft class, no GL
 * call, no file access. That keeps it testable offline and safe to call before the render
 * thread has a context (which is exactly when VulkanMod has to be detected).</p>
 */
public final class RenderCompat {

    /** How much of the renderer is allowed to run. */
    public enum Mode {
        /** Everything: core shader overrides, sky replacement, post chain. */
        FULL,
        /**
         * A conflicting shader pipeline owns the frame. Keep the .mcpack importer, the
         * generated resource pack and the settings UI, but bind no uniforms, replace no
         * sky and run no post chain.
         */
        YIELD_TO_IRIS,
        /**
         * No OpenGL: any GL call crashes the game. Nothing visual runs, but the mod still
         * loads, the GUI still opens and the pack importer still works.
         */
        DISABLED_NO_GL
    }

    /** Ids whose pipeline owns the whole frame; we yield to these. */
    private static final List<String> FRAME_OWNERS = List.of("iris", "oculus");

    /** Ids that remove OpenGL entirely. */
    private static final List<String> NO_GL_BACKENDS = List.of("vulkanmod");

    /**
     * Mods that are known to coexist. Listed for the debug line only: nothing is disabled
     * for them, and a mod that is absent simply does not appear.
     */
    private static final List<String> KNOWN_COEXISTING = List.of(
            "sodium", "embeddium", "rubidium", "lithium", "ferritecore", "modernfix",
            "immediatelyfast", "entityculling", "c2me", "krypton", "starlight", "memoryleakfix",
            "badoptimizations", "moreculling");

    private final Mode mode;
    private final String triggeredBy;
    private final List<String> detected;

    private RenderCompat(Mode mode, String triggeredBy, List<String> detected) {
        this.mode = mode;
        this.triggeredBy = triggeredBy;
        this.detected = List.copyOf(detected);
    }

    /**
     * Decides the mode from the ids of every loaded mod.
     *
     * <p>Order matters: VulkanMod wins over Iris because with no GL backend even yielding
     * to Iris would still try to touch GL state. An Iris/Oculus install with no GL backend
     * is reported as {@link Mode#DISABLED_NO_GL}.</p>
     *
     * @param isLoaded asks the loader whether a mod id is present
     */
    public static RenderCompat detect(Predicate<String> isLoaded) {
        List<String> found = new ArrayList<>();

        String noGl = firstMatch(isLoaded, NO_GL_BACKENDS, found);
        if (noGl != null) {
            return new RenderCompat(Mode.DISABLED_NO_GL, noGl, found);
        }

        String owner = firstMatch(isLoaded, FRAME_OWNERS, found);
        if (owner != null) {
            return new RenderCompat(Mode.YIELD_TO_IRIS, owner, found);
        }

        firstMatch(isLoaded, KNOWN_COEXISTING, found);
        return new RenderCompat(Mode.FULL, null, found);
    }

    private static String firstMatch(Predicate<String> isLoaded, List<String> candidates,
                                     List<String> found) {
        for (String candidate : candidates) {
            if (isLoaded.test(candidate)) {
                found.add(candidate);
                return candidate;
            }
        }
        return null;
    }

    /**
     * Builds the predicate used by {@link #detect}: a candidate matches an id that is equal
     * to it, or that continues with a separator. That catches forks such as
     * {@code vulkanmod-fabric} or {@code iris-mc1.21.1} without matching an unrelated mod
     * that merely starts with the same letters.
     */
    public static Predicate<String> matcher(Predicate<String> rawLoaded, List<String> allIds) {
        List<String> normalised = new ArrayList<>(allIds.size());
        for (String id : allIds) {
            normalised.add(id.toLowerCase(Locale.ROOT));
        }
        return candidate -> {
            String want = candidate.toLowerCase(Locale.ROOT);
            for (String id : normalised) {
                if (id.equals(want) || id.startsWith(want + "-") || id.startsWith(want + "_")) {
                    return true;
                }
            }
            return rawLoaded.test(candidate);
        };
    }

    /**
     * Process-wide decision, set once by the platform entrypoint (only the loader knows
     * which mods are installed) and read on every frame. Defaults to {@link Mode#FULL} so
     * an early call - before the entrypoint has run - behaves like the unmodded case
     * instead of silently disabling the renderer.
     */
    private static volatile RenderCompat instance =
            new RenderCompat(Mode.FULL, null, List.of());

    public static RenderCompat get() {
        return instance;
    }

    /** Called once at client init with the ids of every loaded mod. */
    public static RenderCompat install(Predicate<String> rawLoaded, List<String> allIds) {
        instance = detect(matcher(rawLoaded, allIds));
        BedrockShadersMod.LOGGER.info("Compatibility: {}", instance.reason());
        if (instance.mode != Mode.FULL) {
            BedrockShadersMod.LOGGER.info(
                    "Bedrock effects are off ({}); the .mcpack importer and the settings screen still work.",
                    instance.reason());
        }
        return instance;
    }

    /** Test seam: force a mode without a loader. */
    public static void setForTesting(RenderCompat value) {
        instance = value;
    }

    public Mode mode() {
        return mode;
    }

    /** The mod that caused the downgrade, or {@code null} in {@link Mode#FULL}. */
    public String triggeredBy() {
        return triggeredBy;
    }

    /** Every relevant mod id that was found, for the debug line. */
    public List<String> detected() {
        return detected;
    }

    /** Core shader overrides, sky replacement and post-processing may run. */
    public boolean allowsGlWork() {
        return mode == Mode.FULL;
    }

    /** The .mcpack importer and the generated resource pack stay usable in every mode. */
    public boolean allowsPackImport() {
        return true;
    }

    /** Short explanation of why the mode is what it is. */
    public String reason() {
        return switch (mode) {
            case FULL -> "full renderer";
            case YIELD_TO_IRIS -> "shader pipeline owned by " + triggeredBy + ", Bedrock effects yielded";
            case DISABLED_NO_GL -> "no OpenGL backend (" + triggeredBy + "), renderer disabled";
        };
    }

    @Override
    public String toString() {
        return "RenderCompat[" + mode + (triggeredBy == null ? "" : " by " + triggeredBy)
                + (detected.isEmpty() ? "" : " detected=" + detected) + "]";
    }
}

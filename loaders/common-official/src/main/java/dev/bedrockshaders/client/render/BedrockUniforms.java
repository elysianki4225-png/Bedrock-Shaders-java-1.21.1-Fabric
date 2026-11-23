package dev.bedrockshaders.client.render;

import dev.bedrockshaders.BedrockShadersMod;
import dev.bedrockshaders.client.ConfigManager;
import dev.bedrockshaders.config.BedrockConfig;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Feeds the Bedrock uniforms into vanilla's own {@link ShaderInstance} objects.
 *
 * <p>Minecraft 1.21.1 parses every uniform declared in a core shader's JSON and
 * uploads it lazily. Our generated JSON declares the Bedrock uniforms, so all this
 * class has to do is write the values into the parsed program with plain
 * {@code glUniform*} calls right before vanilla uses it. Programs are identified by
 * name ({@code rendertype_solid}, ...) and uniform locations are cached.</p>
 */
public final class BedrockUniforms {
    private static final Set<String> TARGETS = Set.of(
            "rendertype_solid",
            "rendertype_cutout",
            "rendertype_cutout_mipped",
            "rendertype_translucent",
            "rendertype_entity_solid",
            "rendertype_entity_cutout",
            "rendertype_entity_cutout_no_cull",
            "rendertype_entity_translucent"
    );

    private static final Map<String, Map<String, Integer>> LOCATIONS = new HashMap<>();

    private BedrockUniforms() {
    }

    public static boolean isTarget(String programName) {
        return programName != null && TARGETS.contains(programName);
    }

    /**
     * Uploads every Bedrock uniform the program actually declares.
     *
     * @return true if the program was uploaded to
     */
    public static boolean apply(ShaderInstance program, FrameContext context, float materialGloss) {
        if (program == null || !TARGETS.contains(program.getName())) {
            return false;
        }
        int glRef = program.getId();
        if (glRef == 0) {
            return false;
        }
        BedrockConfig config = ConfigManager.get().config();
        if (!config.enabled) {
            return false;
        }
        Map<String, Integer> locations = locationsFor(program);
        boolean uploaded = false;

        uploaded |= setMatrix(locations, "BedrockViewMat", context.viewMatrix());
        uploaded |= setMatrix(locations, "BedrockInvViewMat", context.inverseViewMatrix());
        uploaded |= setMatrix(locations, "BedrockProjectionInv", context.inverseProjectionMatrix());
        uploaded |= set4(locations, "BedrockSunDir", context.sunDirection().x, context.sunDirection().y,
                context.sunDirection().z, context.sunIntensity() * config.sunIntensity);
        uploaded |= set4(locations, "BedrockMoonDir", context.moonDirection().x, context.moonDirection().y,
                context.moonDirection().z, context.moonIntensity());
        uploaded |= set3(locations, "BedrockSkyZenith", context.skyZenith()[0] * config.skyBrightness,
                context.skyZenith()[1] * config.skyBrightness, context.skyZenith()[2] * config.skyBrightness);
        uploaded |= set3(locations, "BedrockSkyHorizon", context.skyHorizon()[0] * config.skyBrightness,
                context.skyHorizon()[1] * config.skyBrightness, context.skyHorizon()[2] * config.skyBrightness);
        uploaded |= set3(locations, "BedrockSunColorLinear", context.sunColor());
        uploaded |= set3(locations, "BedrockMoonColorLinear", context.moonColor());
        uploaded |= set1(locations, "BedrockExposure", 1.0F);
        uploaded |= set1(locations, "BedrockSunGlare", context.sunIntensity());
        uploaded |= set1(locations, "BedrockGameTime", context.worldTimeSeconds());
        uploaded |= set1(locations, "BedrockRain", context.rainGradient());
        uploaded |= set1(locations, "BedrockUnderwater", context.isUnderwater() ? 1.0F : 0.0F);
        uploaded |= set3(locations, "BedrockWaterFogColor", context.waterFogColor());
        uploaded |= set3(locations, "BedrockFogColorLinear", context.fogColor());
        uploaded |= set1(locations, "BedrockCloudHeight", 192.0F);
        uploaded |= set1(locations, "BedrockSunAngle", context.sunAngle());
        uploaded |= set1(locations, "BedrockFogDensity", context.fogDensity() * config.fogDensity);
        uploaded |= set1(locations, "BedrockMaterialGloss", materialGloss);
        uploaded |= set1(locations, "BedrockWaterline", context.isInWaterBody() ? 1.0F : 0.0F);
        uploaded |= set1(locations, "BedrockWaterSurfaceHeight", context.waterSurfaceHeight());
        uploaded |= set3(locations, "BedrockWaterColorLinear", context.waterFogColor());
        uploaded |= set3(locations, "BedrockWaterSurfaceColorLinear", context.waterSurfaceColor());
        uploaded |= set1(locations, "BedrockWaterTransparency", context.waterTransparency());
        uploaded |= set1(locations, "BedrockWaterWaveStrength", config.waterWaveStrength);
        uploaded |= set1(locations, "BedrockWaterReflection", config.waterReflectionStrength);
        uploaded |= set1(locations, "BedrockWaterRefraction", config.waterRefractionStrength);
        uploaded |= set1(locations, "BedrockCaustics",
                Math.max(0.0F, config.caustics + context.extensionCausticsBias()));
        uploaded |= set2(locations, "BedrockScreenSize", context.framebufferWidth(), context.framebufferHeight());
        uploaded |= set2(locations, "BedrockWaveOffsetA",
                (context.worldTimeSeconds() * 0.035F) % 6.2831855F,
                (context.worldTimeSeconds() * 0.021F) % 6.2831855F);
        uploaded |= set2(locations, "BedrockWaveOffsetB",
                (context.worldTimeSeconds() * 0.017F) % 6.2831855F,
                (context.worldTimeSeconds() * 0.029F) % 6.2831855F);
        return uploaded;
    }

    /**
     * Returns a fresh location map for this program. Locations are cached per program
     * name and invalidated whenever vanilla reloads its shaders (see {@link #invalidate()}).
     */
    private static Map<String, Integer> locationsFor(ShaderInstance program) {
        return LOCATIONS.computeIfAbsent(program.getName(), name -> new HashMap<>());
    }

    /** Called after a resource reload - all GL objects changed. */
    public static void invalidate() {
        LOCATIONS.clear();
        currentProgram = 0;
    }

    private static int location(Map<String, Integer> locations, String uniform) {
        Integer cached = locations.get(uniform);
        if (cached != null) {
            return cached;
        }
        int location = lookup(uniform);
        locations.put(uniform, location);
        return location;
    }

    private static int currentProgram;

    /** Location lookup that targets the currently bound program. */
    private static int lookup(String uniform) {
        return GL20.glGetUniformLocation(currentProgram, uniform);
    }

    /** Must be called by the caller before {@link #apply} so lookups hit the right program. */
    public static void bind(ShaderInstance program) {
        currentProgram = program.getId();
    }

    private static boolean set1(Map<String, Integer> locations, String name, float v) {
        int location = location(locations, name);
        if (location < 0) {
            return false;
        }
        GL20.glUniform1f(location, v);
        return true;
    }

    private static boolean set2(Map<String, Integer> locations, String name, float x, float y) {
        int location = location(locations, name);
        if (location < 0) {
            return false;
        }
        GL20.glUniform2f(location, x, y);
        return true;
    }

    private static boolean set3(Map<String, Integer> locations, String name, float[] v) {
        return set3(locations, name, v[0], v[1], v[2]);
    }

    private static boolean set3(Map<String, Integer> locations, String name, float x, float y, float z) {
        int location = location(locations, name);
        if (location < 0) {
            return false;
        }
        GL20.glUniform3f(location, x, y, z);
        return true;
    }

    private static boolean set4(Map<String, Integer> locations, String name, float x, float y, float z, float w) {
        int location = location(locations, name);
        if (location < 0) {
            return false;
        }
        GL20.glUniform4f(location, x, y, z, w);
        return true;
    }

    private static final float[] MATRIX = new float[16];
    private static final java.nio.FloatBuffer MATRIX_BUFFER = org.lwjgl.BufferUtils.createFloatBuffer(16);

    private static boolean setMatrix(Map<String, Integer> locations, String name, Matrix4f matrix) {
        int location = location(locations, name);
        if (location < 0) {
            return false;
        }
        matrix.get(MATRIX);
        MATRIX_BUFFER.clear();
        MATRIX_BUFFER.put(MATRIX).flip();
        GL20.glUniformMatrix4fv(location, false, MATRIX_BUFFER);
        return true;
    }

    /** Material gloss heuristics for vanilla's render types. */
    public static float glossFor(String programName) {
        if (programName == null) {
            return 0.18F;
        }
        if (programName.contains("translucent")) {
            return 0.85F;
        }
        if (programName.startsWith("rendertype_entity")) {
            return 0.35F;
        }
        if (programName.contains("cutout")) {
            return 0.12F;
        }
        return 0.2F;
    }

    public static void logMissingUniforms(ShaderInstance program) {
        if (program == null) {
            return;
        }
        if (BedrockShadersMod.LOGGER.isDebugEnabled()) {
            BedrockShadersMod.LOGGER.debug("Bedrock uniforms bound to {}", program.getName());
        }
    }

}

package dev.bedrockshaders.client.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.bedrockshaders.BedrockShadersMod;
import dev.bedrockshaders.client.assets.ResourceSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Generates a real resource pack in {@code config/bedrock-shaders/pack} containing
 * Minecraft 1.21.1 core-shader definitions ({@code rendertype_terrain},
 * {@code rendertype_translucent}, {@code rendertype_entity_solid}) whose GLSL is
 * written against Bedrock-style lighting.
 *
 * <p>Vanilla's resource-pack system is used on purpose: the pack is discovered by
 * Minecraft itself, shipped shaders are never patched at runtime, and users can
 * still override any generated file.</p>
 *
 * <p>The pack must be enabled by Fabric's built-in resource pack API (see
 * {@code BedrockShadersClient}); {@link #ensureGenerated} only writes the files.</p>
 */
public final class BedrockShaderPack {
    public static final String PACK_ID = "bedrock_shaders_runtime";
    /** Uniforms vanilla's GLSL declares as {@code int} (everything else is float/vec/mat). */
    private static final java.util.Set<String> INT_UNIFORMS = java.util.Set.of("FogShape");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** A core-shader program: GLSL stage names plus the uniforms/samplers it uses. */
    private record Program(String name, String vertex, String fragment, String[] samplers, String[] uniforms) {
    }

    private static final String[] BLOCK_UNIFORMS = {
            "ModelViewMat", "ProjMat", "ColorModulator", "FogStart", "FogEnd", "FogColor", "FogShape",
            "ChunkOffset", "BedrockViewMat", "BedrockInvViewMat", "BedrockSunDir", "BedrockMoonDir",
            "BedrockSkyZenith", "BedrockSkyHorizon", "BedrockExposure", "BedrockGameTime",
            "BedrockRain", "BedrockUnderwater", "BedrockWaterFogColor", "BedrockFogColorLinear",
            "BedrockCloudHeight", "BedrockSunAngle", "BedrockFogDensity", "BedrockMaterialGloss",
            "BedrockWaterline", "BedrockWaterSurfaceHeight", "BedrockSunColorLinear",
            "BedrockWaterColorLinear", "BedrockWaterSurfaceColorLinear", "BedrockWaterTransparency"
    };

    private static final String[] TRANSLUCENT_UNIFORMS = {
            "ModelViewMat", "ProjMat", "ColorModulator", "FogStart", "FogEnd", "FogColor", "FogShape",
            "ChunkOffset", "BedrockViewMat", "BedrockInvViewMat", "BedrockSunDir", "BedrockMoonDir",
            "BedrockSkyZenith", "BedrockSkyHorizon", "BedrockExposure", "BedrockGameTime",
            "BedrockRain", "BedrockUnderwater", "BedrockWaterFogColor", "BedrockFogColorLinear",
            "BedrockCloudHeight", "BedrockSunAngle", "BedrockFogDensity",
            "BedrockWaveOffsetA", "BedrockWaveOffsetB", "BedrockSunColorLinear",
            "BedrockWaterColorLinear", "BedrockWaterSurfaceColorLinear", "BedrockWaterTransparency",
            "BedrockWaterWaveStrength", "BedrockWaterReflection", "BedrockWaterRefraction",
            "BedrockCaustics", "BedrockScreenSize", "BedrockProjectionInv", "BedrockWaterSurfaceHeight"
    };

    private static final String[] ENTITY_UNIFORMS = {
            "ModelViewMat", "ProjMat", "ColorModulator", "FogStart", "FogEnd", "FogColor", "FogShape",
            "Light0_Direction", "Light1_Direction", "BedrockViewMat", "BedrockInvViewMat",
            "BedrockSunDir", "BedrockMoonDir", "BedrockSkyZenith", "BedrockSkyHorizon",
            "BedrockExposure", "BedrockGameTime", "BedrockRain", "BedrockUnderwater",
            "BedrockWaterFogColor", "BedrockFogColorLinear", "BedrockCloudHeight",
            "BedrockSunAngle", "BedrockFogDensity", "BedrockMaterialGloss"
    };

    /**
     * Minecraft 1.21.1 has no {@code terrain} program: chunk geometry is drawn with
     * {@code rendertype_solid}, {@code rendertype_cutout} and
     * {@code rendertype_cutout_mipped}, which share one vertex format. All three are
     * pointed at the same Bedrock terrain shader; translucent block layers (water,
     * glass, ice) use the Bedrock water shader and solid entities the entity shader.
     */
    private static final Program[] PROGRAMS = {
            new Program("rendertype_solid", "bedrock_terrain.vsh", "bedrock_terrain.fsh",
                    new String[] {"Sampler0", "Sampler2"}, BLOCK_UNIFORMS),
            new Program("rendertype_cutout", "bedrock_terrain.vsh", "bedrock_terrain.fsh",
                    new String[] {"Sampler0", "Sampler2"}, BLOCK_UNIFORMS),
            new Program("rendertype_cutout_mipped", "bedrock_terrain.vsh", "bedrock_terrain.fsh",
                    new String[] {"Sampler0", "Sampler2"}, BLOCK_UNIFORMS),
            // Vanilla binds exactly Sampler0 (block atlas) and Sampler2 (lightmap) for
            // translucent block layers; declaring more samplers would leave them unbound
            // (a GL error every frame), so the water shader only uses those two.
            new Program("rendertype_translucent", "bedrock_translucent.vsh", "bedrock_translucent.fsh",
                    new String[] {"Sampler0", "Sampler2"}, TRANSLUCENT_UNIFORMS),
            new Program("rendertype_entity_solid", "bedrock_entity.vsh", "bedrock_entity.fsh",
                    new String[] {"Sampler0", "Sampler1", "Sampler2"}, ENTITY_UNIFORMS)
    };

    private BedrockShaderPack() {
    }

    /**
     * Root of the generated pack. Set by each loader's entrypoint, because only the loader
     * knows its config directory; everything after that step is loader-agnostic.
     */
    private static Path packRoot;

    public static void setPackRoot(Path root) {
        packRoot = root;
    }

    public static Path packDirectory() {
        return packRoot;
    }

    public static boolean isGenerated() {
        Path dir = packDirectory();
        return Files.isRegularFile(dir.resolve("pack.mcmeta")) && Files.isDirectory(dir.resolve("assets/minecraft/shaders/core"));
    }

    /** (Re)generates the pack from the shaders bundled in the jar. */
    public static boolean ensureGenerated(ResourceSource manager, boolean force) {
        try {
            Path root = packDirectory();
            if (!force && isGenerated()) {
                return true;
            }
            deleteRecursively(root.resolve("assets/minecraft/shaders/core"));
            Files.createDirectories(root.resolve("assets/minecraft/shaders/core"));

            writePackMeta(root);
            for (Program program : PROGRAMS) {
                String vertex = ShaderSources.get(manager, program.vertex());
                String fragment = ShaderSources.get(manager, program.fragment());
                Files.writeString(root.resolve("assets/minecraft/shaders/core/" + program.name() + ".vsh"),
                        vertex, StandardCharsets.UTF_8);
                Files.writeString(root.resolve("assets/minecraft/shaders/core/" + program.name() + ".fsh"),
                        fragment, StandardCharsets.UTF_8);
                Files.writeString(root.resolve("assets/minecraft/shaders/core/" + program.name() + ".json"),
                        GSON.toJson(programJson(program)), StandardCharsets.UTF_8);
            }
            BedrockShadersMod.LOGGER.info("Generated Bedrock shader resource pack at {}", root);
            return true;
        } catch (RuntimeException | IOException e) {
            BedrockShadersMod.LOGGER.error("Failed to generate Bedrock shader resource pack", e);
            return false;
        }
    }

    private static void writePackMeta(Path root) throws IOException {
        JsonObject meta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 34);
        pack.addProperty("supported_formats", "34");
        pack.addProperty("description", "Bedrock Shaders - Runtime Bedrock render pipeline (generated)");
        meta.add("pack", pack);
        JsonObject languages = new JsonObject();
        languages.addProperty("en_us", "Bedrock Shaders (runtime)");
        meta.add("language", languages);
        Files.writeString(root.resolve("pack.mcmeta"), GSON.toJson(meta), StandardCharsets.UTF_8);
    }

    private static JsonObject programJson(Program program) {
        JsonObject json = new JsonObject();
        // 1.21.1 loads <vertex>.vsh and <fragment>.fsh next to the JSON, so both
        // fields carry the vanilla program id and the files are written under it.
        json.addProperty("vertex", program.name());
        json.addProperty("fragment", program.name());

        JsonArray samplers = new JsonArray();
        for (String sampler : program.samplers()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", sampler);
            samplers.add(entry);
        }
        json.add("samplers", samplers);

        Map<String, float[]> defaults = defaults();
        JsonArray uniforms = new JsonArray();
        for (String uniform : program.uniforms()) {
            float[] value = defaults.get(uniform);
            if (value == null) {
                value = new float[] {0.0F};
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("name", uniform);
            entry.addProperty("type", INT_UNIFORMS.contains(uniform) ? "int" : typeOf(value));
            entry.addProperty("count", value.length);
            JsonArray values = new JsonArray();
            for (float v : value) {
                values.add(v);
            }
            entry.add("values", values);
            uniforms.add(entry);
        }
        json.add("uniforms", uniforms);
        return json;
    }

    /**
     * Vanilla only accepts three GLSL uniform types in a core-shader definition;
     * anything else aborts {@code addUniform} and fails the whole resource reload.
     * Vectors are uploaded as a scalar array with {@code count} components.
     */
    private static String typeOf(float[] value) {
        return value.length == 16 ? "matrix4x4" : "float";
    }

    private static Map<String, float[]> defaults() {
        Map<String, float[]> map = new LinkedHashMap<>();
        float[] identity = {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1
        };
        map.put("ModelViewMat", identity);
        map.put("ProjMat", identity);
        map.put("ColorModulator", new float[] {1.0F, 1.0F, 1.0F, 1.0F});
        map.put("FogStart", new float[] {0.0F});
        map.put("FogEnd", new float[] {1.0F});
        map.put("FogColor", new float[] {0.0F, 0.0F, 0.0F, 1.0F});
        map.put("FogShape", new float[] {0.0F});
        map.put("ChunkOffset", new float[] {0.0F, 0.0F, 0.0F});
        map.put("Light0_Direction", new float[] {0.2F, 1.0F, -0.7F});
        map.put("Light1_Direction", new float[] {-0.2F, 1.0F, 0.7F});
        map.put("BedrockViewMat", identity);
        map.put("BedrockInvViewMat", identity);
        map.put("BedrockProjectionInv", identity);
        map.put("BedrockSunDir", new float[] {0.0F, 1.0F, 0.0F, 1.0F});
        map.put("BedrockMoonDir", new float[] {0.0F, -1.0F, 0.0F, 0.0F});
        map.put("BedrockSkyZenith", new float[] {0.16F, 0.28F, 0.55F});
        map.put("BedrockSkyHorizon", new float[] {0.55F, 0.68F, 0.86F});
        map.put("BedrockExposure", new float[] {1.0F});
        map.put("BedrockGameTime", new float[] {0.0F});
        map.put("BedrockRain", new float[] {0.0F});
        map.put("BedrockUnderwater", new float[] {0.0F});
        map.put("BedrockWaterFogColor", new float[] {0.09F, 0.29F, 0.44F});
        map.put("BedrockFogColorLinear", new float[] {0.55F, 0.68F, 0.86F});
        map.put("BedrockCloudHeight", new float[] {192.0F});
        map.put("BedrockSunAngle", new float[] {0.0F});
        map.put("BedrockFogDensity", new float[] {1.0F});
        map.put("BedrockMaterialGloss", new float[] {0.25F});
        map.put("BedrockWaterline", new float[] {0.0F});
        map.put("BedrockWaterSurfaceHeight", new float[] {62.0F});
        map.put("BedrockSunColorLinear", new float[] {1.0F, 0.92F, 0.82F});
        map.put("BedrockWaterColorLinear", new float[] {0.09F, 0.29F, 0.44F});
        map.put("BedrockWaterSurfaceColorLinear", new float[] {0.12F, 0.42F, 0.62F});
        map.put("BedrockWaterTransparency", new float[] {0.65F});
        map.put("BedrockWaveOffsetA", new float[] {0.0F, 0.0F});
        map.put("BedrockWaveOffsetB", new float[] {0.0F, 0.0F});
        map.put("BedrockWaterWaveStrength", new float[] {1.0F});
        map.put("BedrockWaterReflection", new float[] {0.85F});
        map.put("BedrockWaterRefraction", new float[] {0.35F});
        map.put("BedrockCaustics", new float[] {0.6F});
        map.put("BedrockScreenSize", new float[] {1920.0F, 1080.0F});
        return map;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.walk(path)) {
                var list = stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList();
                for (Path p : list) {
                    Files.deleteIfExists(p);
                }
            }
        } else {
            Files.deleteIfExists(path);
        }
    }

    /** Fabric passes the mod container so the pack can also be registered as built-in. */
    /**
     * Regenerates from whatever {@link ResourceSource} the platform installed last. Each
     * loader caches its live source here so a config change can rebuild the pack without
     * waiting for a resource reload.
     */
    private static ResourceSource currentSource;

    public static void setCurrentSource(ResourceSource source) {
        currentSource = source;
    }

    public static void regenerateFromCurrentResources() {
        if (currentSource != null) {
            ensureGenerated(currentSource, true);
        }
    }
}

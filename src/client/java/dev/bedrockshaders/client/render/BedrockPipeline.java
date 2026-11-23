package dev.bedrockshaders.client.render;

import dev.bedrockshaders.BedrockShadersMod;
import dev.bedrockshaders.client.ConfigManager;
import dev.bedrockshaders.client.render.effects.PostShaders;
import dev.bedrockshaders.config.BedrockConfig;
import dev.bedrockshaders.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Owns every framebuffer and post-processing program of the Bedrock pipeline and
 * executes the per-frame passes:
 *
 * <pre>
 *  vanilla main framebuffer (opaque + translucent scene)
 *      |  copy colour + depth
 *      v
 *   scene copy  --depth-->  SSAO  ------------\
 *               --colour-->  bloom downsample --+--> composite (grading, tonemap)  --> main
 *               --colour-->  god rays (sun)  --/
 * </pre>
 *
 * <p>The pipeline is intentionally conservative: if any program fails to compile or
 * a framebuffer is incomplete, the frame is left untouched and vanilla rendering is
 * preserved.</p>
 */
public final class BedrockPipeline {
    private static final BedrockPipeline INSTANCE = new BedrockPipeline();

    private final ScreenQuad quad = new ScreenQuad();

    /**
     * Platform hook: the game's main framebuffer. Fabric/Quilt return
     * {@code MinecraftClient#getFramebuffer()}; Forge/NeoForge return
     * {@code Minecraft#getMainRenderTarget()}. Set once by the loader's entrypoint.
     */
    private static java.util.function.Supplier<VanillaFramebuffer> mainFramebufferSupplier;

    public static void setMainFramebufferSupplier(java.util.function.Supplier<VanillaFramebuffer> supplier) {
        mainFramebufferSupplier = supplier;
    }

    private static VanillaFramebuffer mainFramebuffer() {
        return mainFramebufferSupplier == null ? null : mainFramebufferSupplier.get();
    }
    private final BedrockTarget sceneCopy = new BedrockTarget("scene_copy");
    private final BedrockTarget bloomTarget = new BedrockTarget("bloom");
    private final BedrockTarget aoTarget = new BedrockTarget("ao");
    private final BedrockTarget raysTarget = new BedrockTarget("godrays");
    private final BedrockTarget compositeTarget = new BedrockTarget("composite");

    private BedrockProgram ssaoProgram;
    private BedrockProgram bloomProgram;
    private BedrockProgram godRaysProgram;
    private BedrockProgram compositeProgram;
    private BedrockProgram skyProgram;

    private boolean initialized;
    private boolean failed;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int capturedFrame = -1;
    private long startupFrames;

    private final Matrix4f invViewProj = new Matrix4f();
    private final Vector3f tempVec = new Vector3f();
    private final float[] projectionScratch = new float[16];
    private final float[] sunUv = new float[3];

    private BedrockPipeline() {
    }

    public static BedrockPipeline get() {
        return INSTANCE;
    }

    public boolean isReady() {
        return initialized && !failed;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void ensureInitialized() {
        if (initialized || failed) {
            return;
        }
        // No GL at all under VulkanMod: compiling a program would crash the game. Iris owns
        // the frame too, and our post chain would fight its pipeline, so neither mode gets
        // to allocate anything.
        if (!RenderCompat.get().allowsGlWork()) {
            return;
        }
        try {
            quad.create();
            ssaoProgram = compile("ssao", PostShaders.ssaoFragment());
            bloomProgram = compile("bloom", PostShaders.bloomFragment());
            godRaysProgram = compile("godrays", PostShaders.godRaysFragment());
            compositeProgram = compile("composite", PostShaders.compositeFragment());
            skyProgram = compile("sky", PostShaders.skyFragment());
            initialized = ssaoProgram.isValid() && bloomProgram.isValid() && godRaysProgram.isValid()
                    && compositeProgram.isValid() && skyProgram.isValid();
            if (!initialized) {
                failed = true;
                BedrockShadersMod.LOGGER.error("Bedrock pipeline disabled: one or more effect programs failed to compile");
            } else {
                BedrockShadersMod.LOGGER.info("Bedrock post-processing pipeline initialised");
            }
        } catch (RuntimeException e) {
            failed = true;
            BedrockShadersMod.LOGGER.error("Bedrock pipeline disabled after unexpected error", e);
        }
    }

    private BedrockProgram compile(String name, String fragmentSource) {
        return BedrockProgram.compile("bedrock_" + name, PostShaders.fullscreenVertex(), fragmentSource);
    }

    public BedrockProgram skyProgram() {
        return skyProgram;
    }

    /** True when the effects need the scene copy this frame (i.e. something is enabled). */
    public boolean wantsEffects() {
        if (!RenderCompat.get().allowsGlWork()) {
            return false;
        }
        BedrockConfig config = ConfigManager.get().config();
        return config.enabled && (config.ssao || config.bloom || config.godrays || config.composite());
    }

    /**
     * Copies the vanilla main framebuffer into our scene target and remembers the
     * depth + matrices. Called after {@code WorldRenderer.render} has drawn the world.
     */
    public void captureScene(FrameContext context) {
        if (!RenderCompat.get().allowsGlWork()) {
            return;
        }
        if (!isReady()) {
            return;
        }
        VanillaFramebuffer main = mainFramebuffer();
        int width = main.width();
        int height = main.height();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (width != lastWidth || height != lastHeight) {
            lastWidth = width;
            lastHeight = height;
            sceneCopy.ensureSize(width, height, true);
            bloomTarget.ensureSize(Math.max(1, width / 4), Math.max(1, height / 4), false);
            aoTarget.ensureSize(Math.max(1, width / 2), Math.max(1, height / 2), false);
            raysTarget.ensureSize(Math.max(1, width / 4), Math.max(1, height / 4), false);
            compositeTarget.ensureSize(width, height, false);
        }
        if (!sceneCopy.isValid() || !bloomTarget.isValid() || !aoTarget.isValid()
                || !raysTarget.isValid() || !compositeTarget.isValid()) {
            return;
        }

        // Blit colour + depth so the effects read a consistent view of the frame.
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, main.framebufferId());
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, sceneCopy.framebuffer());
        org.lwjgl.opengl.GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_NEAREST);
        org.lwjgl.opengl.GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_NEAREST);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0);
        capturedFrame = (int) context.frameId();
        startupFrames++;
    }

    public boolean hasCapturedScene(long frameId) {
        return capturedFrame == (int) frameId && sceneCopy.isValid();
    }

    /**
     * Runs every enabled post effect and writes the final image back into the
     * vanilla framebuffer. Must be called from the render thread during
     * {@code GameRenderer.render}.
     */
    public void renderEffects(FrameContext context) {
        if (!RenderCompat.get().allowsGlWork()) {
            return;
        }
        if (!isReady() || !hasCapturedScene(context.frameId())) {
            return;
        }
        BedrockConfig config = ConfigManager.get().config();
        boolean ssao = config.ssao;
        boolean bloom = config.bloom;
        boolean rays = config.godrays && context.sunIntensity() > 0.01F;
        boolean composite = config.composite();
        if (!ssao && !bloom && !rays && !composite) {
            return;
        }

        int width = sceneCopy.width();
        int height = sceneCopy.height();
        GlObjects.beginFragmentPass();

        // 1) Ambient occlusion from the scene depth.
        if (ssao) {
            aoTarget.begin();
            ssaoProgram.use();
            ssaoProgram.setSampler("DepthSampler", 0);
            GlObjects.bindTexture(0, sceneCopy.depthTexture());
            ssaoProgram.set("ProjMat", context.projectionMatrix());
            ssaoProgram.set("ProjInv", context.inverseProjectionMatrix());
            ssaoProgram.set("ScreenSize", (float) aoTarget.width(), (float) aoTarget.height());
            ssaoProgram.set("Radius", 1.15F * config.ssaoRadius);
            ssaoProgram.set("Strength", config.ssaoStrength);
            ssaoProgram.set("Near", 0.05F);
            ssaoProgram.set("Far", Math.max(64.0F, context.viewDistanceBlocks() * 2.0F));
            quad.draw();
            BedrockTarget.end();
        } else {
            clearTarget(aoTarget);
        }

        // 2) Bloom bright pass.
        if (bloom) {
            bloomTarget.begin();
            bloomProgram.use();
            bloomProgram.setSampler("InputSampler", 0);
            GlObjects.bindTexture(0, sceneCopy.colorTexture());
            bloomProgram.set("TexelSize", 1.0F / width, 1.0F / height);
            bloomProgram.set("Threshold", 0.55F);
            quad.draw();
            BedrockTarget.end();
        } else {
            clearTarget(bloomTarget);
        }

        // 3) Screen-space god rays towards the sun.
        if (rays) {
            raysTarget.begin();
            godRaysProgram.use();
            godRaysProgram.setSampler("SceneSampler", 0);
            godRaysProgram.setSampler("DepthSampler", 1);
            GlObjects.bindTexture(0, sceneCopy.colorTexture());
            GlObjects.bindTexture(1, sceneCopy.depthTexture());
            float[] sunUv = projectSun(context);
            godRaysProgram.set("SunScreenPos", sunUv[0], sunUv[1]);
            godRaysProgram.set("SunVisible", sunUv[2]);
            godRaysProgram.set("Strength", config.godrayStrength);
            godRaysProgram.set("ScreenSize", (float) raysTarget.width(), (float) raysTarget.height());
            quad.draw();
            BedrockTarget.end();
        } else {
            clearTarget(raysTarget);
        }

        // 4) Composite everything into our HDR-ish target, then blit to vanilla.
        compositeTarget.begin();
        compositeProgram.use();
        compositeProgram.setSampler("SceneSampler", 0);
        compositeProgram.setSampler("BloomSampler", 1);
        compositeProgram.setSampler("AoSampler", 2);
        compositeProgram.setSampler("RaysSampler", 3);
        GlObjects.bindTexture(0, sceneCopy.colorTexture());
        GlObjects.bindTexture(1, bloomTarget.colorTexture());
        GlObjects.bindTexture(2, aoTarget.colorTexture());
        GlObjects.bindTexture(3, raysTarget.colorTexture());
        compositeProgram.set("ScreenSize", (float) width, (float) height);
        compositeProgram.set("BloomStrength", bloom ? config.bloomStrength : 0.0F);
        compositeProgram.set("AoStrength", ssao ? config.ssaoStrength * 0.65F : 0.0F);
        compositeProgram.set("RaysStrength", rays ? config.godrayStrength * 0.55F : 0.0F);
        compositeProgram.set("Exposure", config.exposure * exposureForContext(context));
        compositeProgram.set("Saturation", config.saturation);
        var grading = context.gradingSettings();
        compositeProgram.set("Gain", grading.midtoneGain);
        compositeProgram.set("Offset", grading.midtoneOffset);
        // pow() in the shader expects a 1/gamma exponent, Bedrock stores gamma directly.
        compositeProgram.set("Gamma", 1.0F / Math.max(0.1F, grading.midtoneGamma[0]),
                1.0F / Math.max(0.1F, grading.midtoneGamma[1]),
                1.0F / Math.max(0.1F, grading.midtoneGamma[2]));
        compositeProgram.set("Contrast", grading.midtoneContrast[0] * config.contrast,
                grading.midtoneContrast[1] * config.contrast,
                grading.midtoneContrast[2] * config.contrast);
        compositeProgram.set("Vignette", 0.35F + context.rainGradient() * 0.15F);
        compositeProgram.set("Underwater", context.isUnderwater() ? 1.0F : 0.0F);
        compositeProgram.set("Time", context.worldTimeSeconds());
        quad.draw();
        BedrockTarget.end();

        blitToVanilla(compositeTarget);
        GlObjects.restoreVanillaState();
    }

    private float exposureForContext(FrameContext context) {
        // Brighten as the sun sets so night still reads, like Bedrock's auto exposure.
        float exposure = Mth.lerp(context.sunIntensity(), 1.65F, 1.0F);
        if (context.isNether() || context.isEnd()) {
            exposure = 1.15F;
        }
        return exposure;
    }

    private void blitToVanilla(BedrockTarget source) {
        VanillaFramebuffer main = mainFramebuffer();
        int width = Math.min(source.width(), main.width());
        int height = Math.min(source.height(), main.height());
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, source.framebuffer());
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, main.framebufferId());
        org.lwjgl.opengl.GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_NEAREST);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0);
    }

    private void clearTarget(BedrockTarget target) {
        if (!target.isValid()) {
            return;
        }
        target.begin();
        GL11.glClearColor(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        BedrockTarget.end();
    }

    /** Projects the sun direction into screen space; returns {u, v, visibility}. */
    private float[] projectSun(FrameContext context) {
        Vector3f direction = tempVec.set(context.sunDirection()).mul(-1.0F).normalize();
        Matrix4f view = context.viewMatrix();
        Vector3f viewDir = new Vector3f(direction);
        view.transformDirection(viewDir);
        if (viewDir.z >= 0.0F) {
            return new float[] {0.5F, 0.5F, 0.0F};
        }
        float[] proj = context.projectionMatrix().get(projectionScratch);
        float x = proj[0] * viewDir.x + proj[4] * viewDir.y + proj[8] * viewDir.z + proj[12];
        float y = proj[1] * viewDir.x + proj[5] * viewDir.y + proj[9] * viewDir.z + proj[13];
        float w = proj[3] * viewDir.x + proj[7] * viewDir.y + proj[11] * viewDir.z + proj[15];
        if (Math.abs(w) < 1.0E-5F) {
            return new float[] {0.5F, 0.5F, 0.0F};
        }
        float u = (x / w) * 0.5F + 0.5F;
        float v = (y / w) * 0.5F + 0.5F;
        boolean onScreen = u > -0.2F && u < 1.2F && v > -0.2F && v < 1.2F;
        float visibility = onScreen ? Mth.clamp(context.sunIntensity() + context.moonIntensity() * 0.3F, 0.0F, 1.0F) : 0.0F;
        return new float[] {Mth.clamp(u, 0.0F, 1.0F), Mth.clamp(v, 0.0F, 1.0F), visibility};
    }

    /** Draws the Bedrock sky into the main framebuffer (replacing vanilla's sky). */
    public void renderSky(FrameContext context) {
        if (!RenderCompat.get().allowsGlWork()) {
            return;
        }
        if (!isReady() || !ConfigManager.get().config().replaceVanillaSky) {
            return;
        }
        VanillaFramebuffer main = mainFramebuffer();
        GlObjects.bindFramebuffer(main.framebufferId());
        GlObjects.beginFragmentPass();
        GL11.glDepthMask(false);
        skyProgram.use();
        invViewProj.set(context.projectionMatrix()).mul(context.viewMatrix()).invert();
        skyProgram.set("InvViewProj", invViewProj);
        skyProgram.set("SunDir", context.sunDirection());
        skyProgram.set("MoonDir", context.moonDirection());
        skyProgram.set("Zenith", context.skyZenith());
        skyProgram.set("Horizon", context.skyHorizon());
        skyProgram.set("SunColor", context.sunColor());
        skyProgram.set("MoonColor", context.moonColor());
        skyProgram.set("SunIntensity", context.sunIntensity() * ConfigManager.get().config().sunIntensity);
        skyProgram.set("MoonIntensity", context.moonIntensity());
        skyProgram.set("Stars", ConfigManager.get().config().stars ? 1.0F : 0.0F);
        skyProgram.set("Time", context.worldTimeSeconds());
        skyProgram.set("CameraPos", (float) context.cameraX(), (float) context.cameraY(), (float) context.cameraZ());
        quad.draw();
        GlObjects.restoreVanillaState();
    }

    public void close() {
        ssaoProgramDispose(ssaoProgram);
        ssaoProgramDispose(bloomProgram);
        ssaoProgramDispose(godRaysProgram);
        ssaoProgramDispose(compositeProgram);
        ssaoProgramDispose(skyProgram);
        ssaoProgram = bloomProgram = godRaysProgram = compositeProgram = skyProgram = null;
        quad.dispose();
        sceneCopy.dispose();
        bloomTarget.dispose();
        aoTarget.dispose();
        raysTarget.dispose();
        compositeTarget.dispose();
        initialized = false;
        failed = false;
        lastWidth = lastHeight = -1;
    }

    private void ssaoProgramDispose(BedrockProgram program) {
        if (program != null) {
            program.dispose();
        }
    }
}

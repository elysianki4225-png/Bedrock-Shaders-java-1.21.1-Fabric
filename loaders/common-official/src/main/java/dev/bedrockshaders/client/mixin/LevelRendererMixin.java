package dev.bedrockshaders.client.mixin;

import dev.bedrockshaders.client.ConfigManager;
import dev.bedrockshaders.client.render.BedrockPipeline;
import dev.bedrockshaders.client.render.FrameContext;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Official-mapped twin of the Fabric {@code WorldRendererMixin}.
 *
 * <p>{@code renderSky} keeps its 1.21.1 signature (NeoForge does not patch it), and the
 * deferred capture happens at the return of the level pass, exactly as on Fabric.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
            at = @At("TAIL"), require = 0)
    private void bedrockShaders$afterSky(Matrix4f frustumMatrix, Matrix4f projectionMatrix,
                                         float partialTick, Camera camera, boolean isFoggy,
                                         Runnable setupFog, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return;
        }
        FrameContext context = FrameContext.get();
        if (!context.isActive()) {
            return;
        }
        BedrockPipeline pipeline = BedrockPipeline.get();
        pipeline.ensureInitialized();
        if (!pipeline.isReady()) {
            return;
        }
        if (ConfigManager.get().config().enabled && ConfigManager.get().config().replaceVanillaSky) {
            pipeline.renderSky(context);
        }
    }

    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At("RETURN"), require = 0)
    private void bedrockShaders$afterLevel(DeltaTracker deltaTracker, boolean renderBlockOutline,
                                           Camera camera, GameRenderer gameRenderer,
                                           LightTexture lightTexture, Matrix4f frustumMatrix,
                                           Matrix4f projectionMatrix, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return;
        }
        FrameContext context = FrameContext.get();
        if (!context.isActive()) {
            return;
        }
        BedrockPipeline pipeline = BedrockPipeline.get();
        if (!pipeline.isReady() || !pipeline.wantsEffects()) {
            return;
        }
        pipeline.captureScene(context);
        // Composite immediately: the scene target was just captured, so the post stack
        // works on this frame's world and the result lands before the HUD is drawn.
        // Compositing after the HUD would blit a world-only image over the hotbar.
        pipeline.renderEffects(context);
    }
}

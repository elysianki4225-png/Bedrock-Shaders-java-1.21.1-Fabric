package dev.bedrockshaders.client.mixin;

import dev.bedrockshaders.client.render.BedrockPipeline;
import dev.bedrockshaders.client.render.FrameContext;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Official-mapped twin of the Fabric {@code WorldRendererMixin}.
 *
 * <p>Two hooks: {@code renderSky(Matrix4f, Matrix4f, float, Camera, boolean, Runnable)} - whose
 * signature NeoForge leaves untouched at 1.21.1 - and the lighting pass inside
 * {@code renderLevel}, which is where the Bedrock sky and fog are substituted.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
            at = @At("HEAD"), require = 0)
    private void bedrockShaders$beforeSky(Matrix4f frustumMatrix, Matrix4f projectionMatrix,
                                          float partialTick, Camera camera, boolean isFoggy,
                                          Runnable setupFog, CallbackInfo ci) {
        BedrockPipeline.get().renderSky(FrameContext.get());
    }

    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void bedrockShaders$afterSetup(DeltaTracker deltaTracker, boolean renderBlockOutline,
                                           Camera camera, GameRenderer gameRenderer,
                                           LightTexture lightTexture, Matrix4f frustumMatrix,
                                           Matrix4f projectionMatrix, CallbackInfo ci) {
        BedrockPipeline.get().renderEffects(FrameContext.get());
    }
}

package dev.bedrockshaders.client.mixin;

import dev.bedrockshaders.client.ConfigManager;
import dev.bedrockshaders.client.render.BedrockPipeline;
import dev.bedrockshaders.client.render.FrameContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the world pass: Bedrock sky replaces vanilla's after the vanilla sky has
 * been drawn, and the finished frame is captured for the post-processing stack.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
            at = @At("TAIL"))
    private void bedrockShaders$afterSky(Matrix4f projectionMatrix, Matrix4f viewMatrix, float tickDelta,
                                         Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
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

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At("RETURN"))
    private void bedrockShaders$afterRender(RenderTickCounter tickCounter, boolean renderBlockOutline,
                                            Camera camera, net.minecraft.client.render.GameRenderer gameRenderer,
                                            LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix,
                                            Matrix4f modelViewMatrix, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
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
    }
}

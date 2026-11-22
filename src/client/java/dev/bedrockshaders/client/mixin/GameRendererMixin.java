package dev.bedrockshaders.client.mixin;

import dev.bedrockshaders.client.render.BedrockPipeline;
import dev.bedrockshaders.client.render.FrameContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Begins the Bedrock frame, runs the post stack after the world+HUD are drawn and
 * releases GL resources when the renderer shuts down.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"))
    private void bedrockShaders$beginWorld(RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        FrameContext context = FrameContext.get();
        context.beginFrame(client);
        BedrockPipeline.get().ensureInitialized();
        if (client.world != null && client.getCameraEntity() != null && context.isActive()) {
            // Mirrors the view matrix vanilla builds for the world pass, including hurt tilt.
            Matrix4f view = new Matrix4f();
            view.rotate(RotationAxis.POSITIVE_X.rotationDegrees(client.gameRenderer.getCamera().getPitch()));
            view.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(client.gameRenderer.getCamera().getYaw() + 180.0F));
            context.setView(view);
            context.setProjection(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("TAIL"))
    private void bedrockShaders$afterFrame(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        FrameContext context = FrameContext.get();
        BedrockPipeline pipeline = BedrockPipeline.get();
        if (!context.isActive()) {
            return;
        }
        if (client.world != null && pipeline.isReady() && pipeline.wantsEffects()) {
            pipeline.renderEffects(client, context);
        }
        context.endFrame();
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void bedrockShaders$close(CallbackInfo ci) {
        BedrockPipeline.get().close();
    }
}

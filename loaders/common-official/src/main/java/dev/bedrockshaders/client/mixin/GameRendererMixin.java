package dev.bedrockshaders.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.bedrockshaders.client.OfficialClientSnapshot;
import dev.bedrockshaders.client.render.BedrockPipeline;
import dev.bedrockshaders.client.render.FrameContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Official-mapped twin of the Fabric {@code GameRendererMixin}: same two hooks, but on
 * {@code GameRenderer#renderLevel(DeltaTracker)} and {@code render(DeltaTracker, boolean)}
 * instead of the Yarn {@code renderWorld}/{@code render} names.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void bedrockShaders$beginLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        FrameContext context = FrameContext.get();
        context.beginFrame(OfficialClientSnapshot.get());
        BedrockPipeline.get().ensureInitialized();
        if (client.level != null && client.getCameraEntity() != null && context.isActive()) {
            // Mirrors the view matrix vanilla builds for the world pass, including hurt tilt.
            Matrix4f view = new Matrix4f();
            view.rotate(org.joml.RotationAxis.POSITIVE_X.rotationDegrees(
                    client.gameRenderer.getMainCamera().getXRot()));
            view.rotate(org.joml.RotationAxis.POSITIVE_Y.rotationDegrees(
                    client.gameRenderer.getMainCamera().getYRot() + 180.0F));
            context.setView(view);
            context.setProjection(RenderSystem.getProjectionMatrix());
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("TAIL"))
    private void bedrockShaders$endFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        BedrockPipeline.get().onFrameEnd();
    }
}

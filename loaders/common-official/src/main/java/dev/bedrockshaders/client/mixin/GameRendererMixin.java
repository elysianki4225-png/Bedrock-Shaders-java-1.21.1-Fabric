package dev.bedrockshaders.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.bedrockshaders.client.OfficialClientSnapshot;
import dev.bedrockshaders.client.render.BedrockPipeline;
import dev.bedrockshaders.client.render.FrameContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Official-mapped twin of the Fabric {@code GameRendererMixin}: the same three hooks on
 * {@code renderLevel(DeltaTracker)}, {@code render(DeltaTracker, boolean)} and
 * {@code close()}.
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
            view.rotate(Axis.XP.rotationDegrees(client.gameRenderer.getMainCamera().getXRot()));
            view.rotate(Axis.YP.rotationDegrees(client.gameRenderer.getMainCamera().getYRot() + 180.0F));
            context.setView(view);
            context.setProjection(RenderSystem.getProjectionMatrix());
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("TAIL"))
    private void bedrockShaders$afterFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        FrameContext context = FrameContext.get();
        BedrockPipeline pipeline = BedrockPipeline.get();
        if (!context.isActive()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.level != null && pipeline.isReady() && pipeline.wantsEffects()) {
            pipeline.renderEffects(context);
        }
        context.endFrame();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void bedrockShaders$close(CallbackInfo ci) {
        BedrockPipeline.get().close();
    }
}

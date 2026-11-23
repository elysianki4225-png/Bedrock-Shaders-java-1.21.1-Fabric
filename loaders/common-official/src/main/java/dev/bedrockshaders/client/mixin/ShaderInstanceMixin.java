package dev.bedrockshaders.client.mixin;

import dev.bedrockshaders.client.render.BedrockUniforms;
import dev.bedrockshaders.client.render.FrameContext;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Official-mapped twin of the Fabric {@code ShaderProgramMixin}.
 *
 * <p>Mojang renamed {@code ShaderProgram} to {@code ShaderInstance} in 1.21.1 and its
 * {@code bind()} to {@code apply()}. Uploading the Bedrock uniforms at {@code TAIL} of
 * {@code apply()} is the only moment the program is guaranteed to be current.</p>
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceMixin {
    @Inject(method = "apply", at = @At("TAIL"))
    private void bedrockShaders$afterApply(CallbackInfo ci) {
        ShaderInstance self = (ShaderInstance) (Object) this;
        if (!BedrockUniforms.isTarget(self.getName())) {
            return;
        }
        FrameContext context = FrameContext.get();
        if (!context.isActive()) {
            return;
        }
        GL20.glUseProgram(self.getId());
        BedrockUniforms.bind(self);
        BedrockUniforms.apply(self, context, BedrockUniforms.glossFor(self.getName()));
    }
}

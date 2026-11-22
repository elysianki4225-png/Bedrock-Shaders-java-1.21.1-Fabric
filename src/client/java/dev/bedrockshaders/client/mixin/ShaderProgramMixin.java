package dev.bedrockshaders.client.mixin;

import dev.bedrockshaders.client.render.BedrockUniforms;
import dev.bedrockshaders.client.render.FrameContext;
import net.minecraft.client.gl.ShaderProgram;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Uploads the Bedrock uniforms right after vanilla binds a core shader program,
 * which is the only moment the program is guaranteed to be current.
 */
@Mixin(ShaderProgram.class)
public abstract class ShaderProgramMixin {
    @Inject(method = "bind()V", at = @At("TAIL"))
    private void bedrockShaders$afterBind(CallbackInfo ci) {
        ShaderProgram self = (ShaderProgram) (Object) this;
        if (!BedrockUniforms.isTarget(self.getName())) {
            return;
        }
        FrameContext context = FrameContext.get();
        if (!context.isActive()) {
            return;
        }
        GL20.glUseProgram(self.getGlRef());
        BedrockUniforms.bind(self);
        BedrockUniforms.apply(self, context, BedrockUniforms.glossFor(self.getName()));
    }
}

package dev.bedrockshaders.client.render;

/**
 * The only part of the game's main framebuffer the render pipeline needs.
 *
 * <p>Yarn calls this type {@code net.minecraft.client.gl.Framebuffer}; Forge and NeoForge
 * call it {@code com.mojang.blaze3d.pipeline.RenderTarget}. Both expose the same three
 * values but with different field/method names, so each loader supplies a trivial adapter
 * in its own platform class and every other renderer file stays loader-agnostic.</p>
 */
public interface VanillaFramebuffer {
    /** OpenGL framebuffer object name. */
    int framebufferId();

    int width();

    int height();
}

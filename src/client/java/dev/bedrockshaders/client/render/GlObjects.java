package dev.bedrockshaders.client.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Minimal, explicit OpenGL helpers. Everything here is called from the render
 * thread while a Minecraft frame is being built, so the helpers deliberately
 * avoid allocations and keep their state changes tiny.
 */
public final class GlObjects {
    public static final int COLOR_ATTACHMENT = GL30.GL_COLOR_ATTACHMENT0;

    private GlObjects() {
    }

    public static int genFramebuffer() {
        return GL30.glGenFramebuffers();
    }

    public static int genTexture() {
        return GL11.glGenTextures();
    }

    public static void bindFramebuffer(int fbo) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
    }

    public static void deleteFramebuffer(int fbo) {
        GL30.glDeleteFramebuffers(fbo);
    }

    public static void deleteTexture(int texture) {
        GL11.glDeleteTextures(texture);
    }

    /** Creates an RGBA8 colour texture with linear filtering and clamp-to-edge. */
    public static int createColorTexture(int width, int height, int internalFormat) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }

    /** Creates a 24-bit depth texture used to sample the scene depth. */
    public static int createDepthTexture(int width, int height) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, width, height, 0,
                GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }

    public static void attachColor(int fbo, int texture) {
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
    }

    public static void attachDepth(int fbo, int texture) {
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, texture, 0);
    }

    public static boolean isFramebufferComplete() {
        return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    public static void bindTexture(int unit, int texture) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    public static void viewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
    }

    public static void drawFullscreenTriangles(int vao) {
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GL30.glBindVertexArray(0);
    }

    /** Restores the blending / depth / culling state Minecraft expects after our passes. */
    public static void restoreVanillaState() {
        if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        if (GL11.glIsEnabled(GL11.GL_CULL_FACE)) {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
        if (GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
        GL20.glUseProgram(0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * Enables the state a fullscreen fragment pass needs and nothing more.
     *
     * <p>Deliberately no matrix setup: {@code ScreenQuad} emits clip-space positions and
     * binds its own VAO, so nothing here touches the fixed-function matrix stack. That
     * matters more than it sounds — {@code glMatrixMode} was removed in the core profile
     * that 1.21.1 runs under, and calling it does not throw a Java exception, it aborts
     * the whole JVM via an LWJGL function-pointer check.</p>
     */
    public static void beginFragmentPass() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

}

package dev.bedrockshaders.client.render;

import dev.bedrockshaders.BedrockShadersMod;

/**
 * Off-screen render target: RGBA8 colour + optional sampleable depth.
 * Targets are resized in place; if the framebuffer is incomplete the target
 * reports itself invalid and every consumer falls back to vanilla rendering.
 */
public final class BedrockTarget {
    private final String name;
    private int framebuffer;
    private int color;
    private int depth;
    private int width;
    private int height;
    private boolean valid;

    public BedrockTarget(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public int framebuffer() {
        return framebuffer;
    }

    public int colorTexture() {
        return color;
    }

    public int depthTexture() {
        return depth;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean isValid() {
        return valid;
    }

    public void ensureSize(int newWidth, int newHeight, boolean wantDepth) {
        newWidth = Math.max(1, newWidth);
        newHeight = Math.max(1, newHeight);
        if (framebuffer != 0 && newWidth == width && newHeight == height) {
            return;
        }
        dispose();
        width = newWidth;
        height = newHeight;
        framebuffer = GlObjects.genFramebuffer();
        GlObjects.bindFramebuffer(framebuffer);
        color = GlObjects.createColorTexture(width, height, org.lwjgl.opengl.GL11.GL_RGBA8);
        GlObjects.attachColor(framebuffer, color);
        if (wantDepth) {
            depth = GlObjects.createDepthTexture(width, height);
            GlObjects.attachDepth(framebuffer, depth);
        }
        valid = GlObjects.isFramebufferComplete();
        GlObjects.bindFramebuffer(0);
        if (!valid) {
            BedrockShadersMod.LOGGER.error("Framebuffer '{}' is incomplete ({}x{})", name, width, height);
        }
    }

    public void begin() {
        GlObjects.bindFramebuffer(framebuffer);
        GlObjects.viewport(0, 0, width, height);
    }

    public static void end() {
        GlObjects.bindFramebuffer(0);
    }

    public void dispose() {
        if (framebuffer != 0) {
            GlObjects.bindFramebuffer(0);
            GlObjects.deleteFramebuffer(framebuffer);
            framebuffer = 0;
        }
        if (color != 0) {
            GlObjects.deleteTexture(color);
            color = 0;
        }
        if (depth != 0) {
            GlObjects.deleteTexture(depth);
            depth = 0;
        }
        valid = false;
        width = 0;
        height = 0;
    }
}

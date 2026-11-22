package dev.bedrockshaders.client.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

/**
 * A single oversized triangle in clip space. Cheaper than a quad and avoids the
 * diagonal seam; the vertex shader emits {@code gl_Position = vec4(Position.xy, 0, 1)}.
 */
public final class ScreenQuad {
    private int vao;
    private int vbo;
    private int ibo;

    public void create() {
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();

        FloatBuffer vertices = org.lwjgl.BufferUtils.createFloatBuffer(9);
        // Triangle covers the whole screen: (-1,-1) (3,-1) (-1,3)
        vertices.put(-1.0F).put(-1.0F).put(0.0F);
        vertices.put(3.0F).put(-1.0F).put(0.0F);
        vertices.put(-1.0F).put(3.0F).put(0.0F);
        vertices.flip();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0L);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public void draw() {
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GL30.glBindVertexArray(0);
    }

    public void dispose() {
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            vao = 0;
        }
        if (vbo != 0) {
            GL15.glDeleteBuffers(vbo);
            vbo = 0;
        }
    }
}

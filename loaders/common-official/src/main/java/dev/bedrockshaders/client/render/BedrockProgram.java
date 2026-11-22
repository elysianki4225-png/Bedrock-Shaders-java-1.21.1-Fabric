package dev.bedrockshaders.client.render;

import dev.bedrockshaders.BedrockShadersMod;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * A small compiled GLSL program with lazy uniform lookup.
 *
 * <p>Uniform "missing" is cached as location {@code -1} so a shader without a
 * given uniform never pays for a second {@code glGetUniformLocation} call.</p>
 */
public final class BedrockProgram {
    private static final FloatBuffer MATRIX_BUFFER = org.lwjgl.BufferUtils.createFloatBuffer(16);
    private static final float[] MATRIX_SCRATCH = new float[16];

    private final String name;
    private final int program;
    private final Map<String, Integer> uniforms = new HashMap<>();
    private boolean valid;

    private BedrockProgram(String name, int program) {
        this.name = name;
        this.program = program;
        this.valid = program != 0;
    }

    public static BedrockProgram compile(String name, String vertexSource, String fragmentSource) {
        int vertex = compileStage(name + ".vsh", GL20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileStage(name + ".fsh", GL20.GL_FRAGMENT_SHADER, fragmentSource);
        if (vertex == 0 || fragment == 0) {
            if (vertex != 0) {
                GL20.glDeleteShader(vertex);
            }
            if (fragment != 0) {
                GL20.glDeleteShader(fragment);
            }
            return new BedrockProgram(name, 0);
        }
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        // Fixed attribute locations keep the fullscreen quad and the scene passes stable.
        GL20.glBindAttribLocation(program, 0, "Position");
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        int status = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS);
        if (status == 0) {
            String log = GL20.glGetProgramInfoLog(program);
            BedrockShadersMod.LOGGER.error("Bedrock program '{}' failed to link:\n{}", name, log);
            GL20.glDeleteProgram(program);
            return new BedrockProgram(name, 0);
        }
        String log = GL20.glGetProgramInfoLog(program);
        if (!log.isBlank()) {
            BedrockShadersMod.LOGGER.info("Bedrock program '{}' linked with warnings:\n{}", name, log.trim());
        }
        return new BedrockProgram(name, program);
    }

    private static int compileStage(String name, int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader);
            BedrockShadersMod.LOGGER.error("Bedrock shader '{}' failed to compile:\n{}", name, log);
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    public boolean isValid() {
        return valid;
    }

    public String name() {
        return name;
    }

    public int id() {
        return program;
    }

    public void use() {
        GL20.glUseProgram(program);
    }

    public void dispose() {
        if (program != 0) {
            GL20.glDeleteProgram(program);
            valid = false;
        }
    }

    private int location(String uniform) {
        Integer cached = uniforms.get(uniform);
        if (cached != null) {
            return cached;
        }
        int location = GL20.glGetUniformLocation(program, uniform);
        uniforms.put(uniform, location);
        return location;
    }

    public void set(String uniform, float value) {
        int location = location(uniform);
        if (location >= 0) {
            GL20.glUniform1f(location, value);
        }
    }

    public void set(String uniform, int value) {
        int location = location(uniform);
        if (location >= 0) {
            GL20.glUniform1i(location, value);
        }
    }

    public void set(String uniform, float x, float y) {
        int location = location(uniform);
        if (location >= 0) {
            GL20.glUniform2f(location, x, y);
        }
    }

    public void set(String uniform, Vector2f value) {
        set(uniform, value.x, value.y);
    }

    public void set(String uniform, float x, float y, float z) {
        int location = location(uniform);
        if (location >= 0) {
            GL20.glUniform3f(location, x, y, z);
        }
    }

    public void set(String uniform, Vector3f value) {
        set(uniform, value.x, value.y, value.z);
    }

    public void set(String uniform, float[] xyz) {
        set(uniform, xyz[0], xyz[1], xyz[2]);
    }

    public void set(String uniform, float x, float y, float z, float w) {
        int location = location(uniform);
        if (location >= 0) {
            GL20.glUniform4f(location, x, y, z, w);
        }
    }

    public void setSampler(String uniform, int unit) {
        int location = location(uniform);
        if (location >= 0) {
            GL20.glUniform1i(location, unit);
        }
    }

    public void set(String uniform, Matrix4f matrix) {
        int location = location(uniform);
        if (location < 0) {
            return;
        }
        matrix.get(MATRIX_SCRATCH);
        MATRIX_BUFFER.clear();
        MATRIX_BUFFER.put(MATRIX_SCRATCH).flip();
        GL20.glUniformMatrix4fv(location, false, MATRIX_BUFFER);
    }
}

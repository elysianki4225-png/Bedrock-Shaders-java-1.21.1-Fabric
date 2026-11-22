package dev.bedrockshaders.bedrock;

import dev.bedrockshaders.util.Mth;

/**
 * A Bedrock "Molang keyframe" style curve: keys are normalised times in [0, 1]
 * (sun angle for lighting / atmospherics), values are scalars.
 */
public final class FloatCurve {
    public static final FloatCurve EMPTY = new FloatCurve(new float[0], new float[0], 0.0F);

    private final float[] keys;
    private final float[] values;
    private final float fallback;

    public FloatCurve(float[] keys, float[] values, float fallback) {
        this.keys = keys;
        this.values = values;
        this.fallback = fallback;
    }

    public boolean isEmpty() {
        return keys.length == 0;
    }

    public float get(float time) {
        if (keys.length == 0) {
            return fallback;
        }
        return Mth.sampleCurve(keys, values, time);
    }

    public float max() {
        float max = fallback;
        for (float v : values) {
            max = Math.max(max, v);
        }
        return max;
    }

    public float[] keys() {
        return keys;
    }

    public float[] values() {
        return values;
    }
}

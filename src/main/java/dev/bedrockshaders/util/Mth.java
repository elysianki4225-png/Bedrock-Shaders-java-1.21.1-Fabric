package dev.bedrockshaders.util;

/** Small, dependency-free math helpers shared by the renderer and the asset curves. */
public final class Mth {
    private Mth() {
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    public static float saturate(float value) {
        return clamp(value, 0.0F, 1.0F);
    }

    public static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    public static float luminance(float r, float g, float b) {
        return 0.2126F * r + 0.7152F * g + 0.0722F * b;
    }

    /** Converts an sRGB component in [0, 1] to linear space. */
    public static float srgbToLinear(float c) {
        return c <= 0.04045F ? c / 12.92F : (float) Math.pow((c + 0.055F) / 1.055F, 2.4F);
    }

    /** Converts a linear component in [0, 1] to sRGB space. */
    public static float linearToSrgb(float c) {
        return c <= 0.0031308F ? c * 12.92F : 1.055F * (float) Math.pow(c, 1.0F / 2.4F) - 0.055F;
    }

    /** Interpolates a sorted {key, value} curve at {@code x}. Keys must be ascending. */
    public static float sampleCurve(float[] keys, float[] values, float x) {
        int n = keys.length;
        if (n == 0) {
            return 0.0F;
        }
        if (n == 1 || x <= keys[0]) {
            return values[0];
        }
        if (x >= keys[n - 1]) {
            return values[n - 1];
        }
        int lo = 0;
        int hi = n - 1;
        while (lo + 1 < hi) {
            int mid = (lo + hi) >>> 1;
            if (keys[mid] <= x) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        float span = keys[hi] - keys[lo];
        float t = span <= 1.0E-6F ? 0.0F : (x - keys[lo]) / span;
        return lerp(t, values[lo], values[hi]);
    }
}

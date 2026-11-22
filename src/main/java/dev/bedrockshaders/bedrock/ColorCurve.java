package dev.bedrockshaders.bedrock;

import dev.bedrockshaders.util.ColorUtil;
import dev.bedrockshaders.util.Mth;

/**
 * A Bedrock colour keyframe curve. Values are stored in <b>linear</b> RGB so the
 * shader side never has to guess which space a colour is in.
 */
public final class ColorCurve {
    public static final ColorCurve BLACK = new ColorCurve(new float[0], new float[0][], new float[] {0.0F, 0.0F, 0.0F});

    private final float[] keys;
    private final float[][] values;
    private final float[] fallback;
    private final float[] out = new float[3];

    public ColorCurve(float[] keys, float[][] values, float[] fallback) {
        this.keys = keys;
        this.values = values;
        this.fallback = fallback;
    }

    public boolean isEmpty() {
        return keys.length == 0;
    }

    /** Writes the interpolated linear RGB colour into {@code dest} and returns it. */
    public float[] get(float time, float[] dest) {
        if (keys.length == 0) {
            dest[0] = fallback[0];
            dest[1] = fallback[1];
            dest[2] = fallback[2];
            return dest;
        }
        if (keys.length == 1 || time <= keys[0]) {
            return copy(values[0], dest);
        }
        int n = keys.length;
        if (time >= keys[n - 1]) {
            return copy(values[n - 1], dest);
        }
        int lo = 0;
        int hi = n - 1;
        while (lo + 1 < hi) {
            int mid = (lo + hi) >>> 1;
            if (keys[mid] <= time) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        float span = keys[hi] - keys[lo];
        float t = span <= 1.0E-6F ? 0.0F : (time - keys[lo]) / span;
        float[] a = values[lo];
        float[] b = values[hi];
        dest[0] = Mth.lerp(t, a[0], b[0]);
        dest[1] = Mth.lerp(t, a[1], b[1]);
        dest[2] = Mth.lerp(t, a[2], b[2]);
        return dest;
    }

    public float[] get(float time) {
        return get(time, out);
    }

    /** Converts a packed 0xRRGGBB Bedrock colour into a constant linear curve. */
    public static ColorCurve constant(int rgb) {
        float[] linear = ColorUtil.rgbToLinear(new float[] {(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF});
        return new ColorCurve(new float[0], new float[0][], linear);
    }

    public static ColorCurve constantLinear(float r, float g, float b) {
        return new ColorCurve(new float[0], new float[0][], new float[] {r, g, b});
    }

    private static float[] copy(float[] from, float[] to) {
        to[0] = from[0];
        to[1] = from[1];
        to[2] = from[2];
        return to;
    }
}

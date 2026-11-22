package dev.bedrockshaders.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.bedrockshaders.util.ColorUtil;
import dev.bedrockshaders.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Converts the keyframe objects found in Mojang's Bedrock sample files
 * ({@code "0.717949": [255, 133, 133]}) into sorted runtime curves.
 */
public final class CurveParser {
    private CurveParser() {
    }

    public static FloatCurve floatCurve(JsonObject object, float fallback) {
        if (object == null || object.size() == 0) {
            return FloatCurve.EMPTY;
        }
        List<float[]> entries = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            try {
                float key = Float.parseFloat(entry.getKey());
                float value = entry.getValue().getAsFloat();
                entries.add(new float[] {key, value});
            } catch (RuntimeException ignored) {
                // Skip malformed keyframes instead of failing the whole pack.
            }
        }
        entries.sort(Comparator.comparingDouble(a -> a[0]));
        float[] keys = new float[entries.size()];
        float[] values = new float[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            keys[i] = entries.get(i)[0];
            values[i] = entries.get(i)[1];
        }
        return new FloatCurve(keys, values, fallback);
    }

    /** Bedrock colour curves are RGB 0-255 arrays; they are converted to linear floats. */
    public static ColorCurve colorCurve(JsonObject object) {
        if (object == null || object.size() == 0) {
            return ColorCurve.BLACK;
        }
        List<float[]> keys = new ArrayList<>();
        List<float[]> colors = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            try {
                float key = Float.parseFloat(entry.getKey());
                float[] rgb = readColor(entry.getValue());
                if (rgb == null) {
                    continue;
                }
                keys.add(new float[] {key});
                colors.add(rgb);
            } catch (RuntimeException ignored) {
            }
        }
        // Sort by key
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble(i -> keys.get(i)[0]));
        float[] outKeys = new float[order.size()];
        float[][] outValues = new float[order.size()][];
        for (int i = 0; i < order.size(); i++) {
            int src = order.get(i);
            outKeys[i] = keys.get(src)[0];
            outValues[i] = colors.get(src);
        }
        return new ColorCurve(outKeys, outValues, new float[] {0.0F, 0.0F, 0.0F});
    }

    public static float[] readColor(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return ColorUtil.parseHexLinear(element.getAsString());
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() >= 3) {
                return ColorUtil.rgbToLinear(new float[] {
                        array.get(0).getAsFloat(),
                        array.get(1).getAsFloat(),
                        array.get(2).getAsFloat()
                });
            }
        }
        return null;
    }

    public static float[] readColor(JsonElement element, float[] fallback) {
        float[] value = readColor(element);
        return value == null ? fallback : value;
    }

    public static float getFloat(JsonObject object, String name, float fallback) {
        if (object == null || !object.has(name)) {
            return fallback;
        }
        try {
            return object.get(name).getAsFloat();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static float[] readVec3(JsonElement element, float[] fallback) {
        if (element != null && element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() >= 3) {
                return new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
            }
        }
        return fallback;
    }

    /** Applies the Bedrock "offset + gain" curve transform used by colour grading. */
    public static float[] gradingTransform(float[] input, float[] gain, float[] offset, float[] gamma,
                                           float[] saturation, float[] contrast) {
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            float c = input[i] * gain[i] + offset[i];
            float mid = 0.5F;
            c = (c - mid) * contrast[i] + mid;
            c = Mth.clamp(c, 0.0F, 1.0F);
            out[i] = (float) Math.pow(Math.max(c, 0.0F), 1.0F / Math.max(gamma[i], 1.0E-4F));
        }
        float luma = Mth.luminance(out[0], out[1], out[2]);
        for (int i = 0; i < 3; i++) {
            out[i] = Mth.clamp(Mth.lerp(luma, out[i], saturation[i]), 0.0F, 1.0F);
        }
        return out;
    }
}

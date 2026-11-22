package dev.bedrockshaders.util;

/** Colour conversions used by the Bedrock asset loader. */
public final class ColorUtil {
    private ColorUtil() {
    }

    /** Parses {@code #RRGGBB} / {@code #AARRGGBB} (Bedrock writes #RRGGBB) into linear floats. */
    public static float[] parseHexLinear(String hex) {
        int[] rgb = parseHex(hex);
        return new float[] {
                Mth.srgbToLinear(rgb[0] / 255.0F),
                Mth.srgbToLinear(rgb[1] / 255.0F),
                Mth.srgbToLinear(rgb[2] / 255.0F)
        };
    }

    public static int[] parseHex(String hex) {
        if (hex == null) {
            return new int[] {255, 255, 255};
        }
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        if (s.length() == 8) {
            s = s.substring(2);
        }
        if (s.length() != 6) {
            return new int[] {255, 255, 255};
        }
        try {
            int v = Integer.parseInt(s, 16);
            return new int[] {(v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF};
        } catch (NumberFormatException e) {
            return new int[] {255, 255, 255};
        }
    }

    /** Converts 0-255 sRGB RGB triple into linear floats. */
    public static float[] rgbToLinear(float[] rgb255) {
        return new float[] {
                Mth.srgbToLinear(Mth.clamp(rgb255[0] / 255.0F, 0.0F, 1.0F)),
                Mth.srgbToLinear(Mth.clamp(rgb255[1] / 255.0F, 0.0F, 1.0F)),
                Mth.srgbToLinear(Mth.clamp(rgb255[2] / 255.0F, 0.0F, 1.0F))
        };
    }

    /** Converts a packed sRGB ARGB int as used by Minecraft into linear floats. */
    public static float[] packedToLinear(int argb) {
        return rgbToLinear(new float[] {(argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF});
    }
}

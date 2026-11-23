package dev.bedrockshaders.api;

/**
 * Mutable per-frame tuning nudge, handed to a {@link FrameHook}.
 *
 * <p>Every field is a multiplier on what the renderer already computed, and starts at 1.0
 * each frame. Leaning on multipliers keeps a hook safe: it cannot turn fog off entirely or
 * set exposure to zero, and two hooks compose instead of overwriting each other.</p>
 *
 * <p>An instance is reused across frames, so a hook must write to it synchronously and
 * never keep a reference.</p>
 */
public final class TuningDelta {

    /** Range every multiplier is clamped to after all hooks have run. */
    public static final float MIN = 0.5F;
    public static final float MAX = 1.5F;

    public float fogDensity = 1.0F;
    public float fogSpan = 1.0F;
    public float sunIntensity = 1.0F;
    public float exposure = 1.0F;
    public float saturation = 1.0F;
    public float contrast = 1.0F;
    /** Additive bloom bias, 0 means "leave the family's value alone". */
    public float bloomBias = 0.0F;
    public float causticsBias = 0.0F;
    /** Underwater fog scale: >1 pushes the water fog further out. */
    public float underwaterFog = 1.0F;

    public void reset() {
        fogDensity = 1.0F;
        fogSpan = 1.0F;
        sunIntensity = 1.0F;
        exposure = 1.0F;
        saturation = 1.0F;
        contrast = 1.0F;
        bloomBias = 0.0F;
        causticsBias = 0.0F;
        underwaterFog = 1.0F;
    }

    /**
     * Folds another delta in multiplicatively (biases add). Used when several hooks write
     * the same field, so registration order does not matter.
     */
    public void compose(float otherFogDensity, float otherFogSpan, float otherSunIntensity,
                        float otherExposure, float otherSaturation, float otherContrast,
                        float otherBloomBias, float otherCausticsBias, float otherUnderwaterFog) {
        fogDensity *= otherFogDensity;
        fogSpan *= otherFogSpan;
        sunIntensity *= otherSunIntensity;
        exposure *= otherExposure;
        saturation *= otherSaturation;
        contrast *= otherContrast;
        bloomBias += otherBloomBias;
        causticsBias += otherCausticsBias;
        underwaterFog *= otherUnderwaterFog;
    }

    /** True when nothing was touched, so the renderer can skip the whole apply step. */
    public boolean isIdentity() {
        return fogDensity == 1.0F && fogSpan == 1.0F && sunIntensity == 1.0F
                && exposure == 1.0F && saturation == 1.0F && contrast == 1.0F
                && bloomBias == 0.0F && causticsBias == 0.0F && underwaterFog == 1.0F;
    }

    /** Clamps every multiplier into the documented range; called after all hooks ran. */
    public void clamp() {
        fogDensity = clamp(fogDensity);
        fogSpan = clamp(fogSpan);
        sunIntensity = clamp(sunIntensity);
        exposure = clamp(exposure);
        saturation = clamp(saturation);
        contrast = clamp(contrast);
        bloomBias = Math.max(-1.0F, Math.min(1.0F, bloomBias));
        causticsBias = Math.max(-1.0F, Math.min(1.0F, causticsBias));
        underwaterFog = clamp(underwaterFog);
    }

    public static float clamp(float value) {
        if (Float.isNaN(value)) {
            return 1.0F;
        }
        return Math.max(MIN, Math.min(MAX, value));
    }
}

package dev.bedrockshaders.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A shader family an addon teaches the renderer about.
 *
 * <p>The built-in families (Newb, YSS, Eternity, Solas) are exactly this shape: a set of
 * name fragments used for detection plus the tuning applied when one matches. Making the
 * record public is what lets a pack author add their own family without a code change in
 * the mod.</p>
 *
 * <p>Every tuning value is a <b>multiplier</b> applied on top of the pack's own data, not
 * a replacement: 1.0 means "leave it alone", which is why a minimally-filled spec is still
 * useful (it only adds a name badge). Values are clamped to the same ranges the built-in
 * families live in, so an addon cannot set bloom to 900 and blind the user.</p>
 */
public final class FamilySpec {

    /** Legal range for the multiplicative tuning fields, matching BedrockConfig's clamps. */
    public static final float MIN_SCALE = 0.25F;
    public static final float MAX_SCALE = 4.0F;

    private final String id;
    private final String translationKey;
    private final List<String> aliases;
    private final boolean fallback;
    private final float fogScale;
    private final float fogDensityScale;
    private final float sunIntensityScale;
    private final float underwaterFogScale;
    private final float exposureScale;
    private final float saturationScale;
    private final float contrastScale;
    private final float bloomBias;
    private final float causticsBias;
    private final float waterReflectionBias;
    private final float godrayScale;
    private final float ssaoBias;

    private FamilySpec(Builder builder) {
        this.id = builder.id;
        this.translationKey = builder.translationKey;
        this.aliases = List.copyOf(builder.aliases);
        this.fallback = builder.fallback;
        this.fogScale = builder.fogScale;
        this.fogDensityScale = builder.fogDensityScale;
        this.sunIntensityScale = builder.sunIntensityScale;
        this.underwaterFogScale = builder.underwaterFogScale;
        this.exposureScale = builder.exposureScale;
        this.saturationScale = builder.saturationScale;
        this.contrastScale = builder.contrastScale;
        this.bloomBias = builder.bloomBias;
        this.causticsBias = builder.causticsBias;
        this.waterReflectionBias = builder.waterReflectionBias;
        this.godrayScale = builder.godrayScale;
        this.ssaoBias = builder.ssaoBias;
    }

    public String id() {
        return id;
    }

    /** Translation key for the badge; an addon is expected to ship its own language file. */
    public String translationKey() {
        return translationKey;
    }

    public List<String> aliases() {
        return aliases;
    }

    /**
     * True for a catch-all family: it is applied when no other family matches, instead of
     * being detection-driven. Only the built-in {@code generic} uses this, but an addon
     * that wants to restyle every unclaimed pack can set it too.
     */
    public boolean fallback() {
        return fallback;
    }

    public float fogScale() {
        return fogScale;
    }

    public float fogDensityScale() {
        return fogDensityScale;
    }

    public float sunIntensityScale() {
        return sunIntensityScale;
    }

    public float underwaterFogScale() {
        return underwaterFogScale;
    }

    public float exposureScale() {
        return exposureScale;
    }

    public float saturationScale() {
        return saturationScale;
    }

    public float contrastScale() {
        return contrastScale;
    }

    public float bloomBias() {
        return bloomBias;
    }

    public float causticsBias() {
        return causticsBias;
    }

    public float waterReflectionBias() {
        return waterReflectionBias;
    }

    public float godrayScale() {
        return godrayScale;
    }

    public float ssaoBias() {
        return ssaoBias;
    }

    /**
     * True when any hint contains one of this family's aliases. Matching is
     * case-insensitive and substring-based, mirroring the built-in detection: Bedrock pack
     * names are free text, so exact matching would recognise almost nothing.
     */
    public boolean matches(String... hints) {
        if (aliases.isEmpty() || hints == null) {
            return false;
        }
        for (String hint : hints) {
            if (hint == null) {
                continue;
            }
            String lower = hint.toLowerCase(Locale.ROOT);
            for (String alias : aliases) {
                if (!alias.isEmpty() && lower.contains(alias)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Builder builder(String id, String translationKey) {
        return new Builder(id, translationKey);
    }

    /** Fluent builder; every tuning field defaults to 1.0 (no change). */
    public static final class Builder {
        private final String id;
        private final String translationKey;
        private final List<String> aliases = new ArrayList<>();
        private boolean fallback;
        private float fogScale = 1.0F;
        private float fogDensityScale = 1.0F;
        private float sunIntensityScale = 1.0F;
        private float underwaterFogScale = 1.0F;
        private float exposureScale = 1.0F;
        private float saturationScale = 1.0F;
        private float contrastScale = 1.0F;
        private float bloomBias = 1.0F;
        private float causticsBias = 1.0F;
        private float waterReflectionBias = 1.0F;
        private float godrayScale = 1.0F;
        private float ssaoBias = 1.0F;

        private Builder(String id, String translationKey) {
            this.id = Objects.requireNonNull(id, "id");
            this.translationKey = Objects.requireNonNull(translationKey, "translationKey");
        }

        /** Adds name fragments, lower-cased. Duplicates are ignored. */
        public Builder aliases(String... values) {
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                String lower = value.trim().toLowerCase(Locale.ROOT);
                if (!lower.isEmpty() && !aliases.contains(lower)) {
                    aliases.add(lower);
                }
            }
            return this;
        }

        public Builder fallback() {
            this.fallback = true;
            return this;
        }

        public Builder fogScale(float value) {
            this.fogScale = clamp(value);
            return this;
        }

        public Builder fogDensityScale(float value) {
            this.fogDensityScale = clamp(value);
            return this;
        }

        public Builder sunScale(float value) {
            this.sunIntensityScale = clamp(value);
            return this;
        }

        public Builder underwaterFogScale(float value) {
            this.underwaterFogScale = clamp(value);
            return this;
        }

        public Builder exposureScale(float value) {
            this.exposureScale = clamp(value);
            return this;
        }

        public Builder saturationScale(float value) {
            this.saturationScale = clamp(value);
            return this;
        }

        public Builder contrastScale(float value) {
            this.contrastScale = clamp(value);
            return this;
        }

        public Builder bloomBias(float value) {
            this.bloomBias = clamp(value);
            return this;
        }

        public Builder causticsBias(float value) {
            this.causticsBias = clamp(value);
            return this;
        }

        public Builder waterReflectionBias(float value) {
            this.waterReflectionBias = clamp(value);
            return this;
        }

        public Builder godrayScale(float value) {
            this.godrayScale = clamp(value);
            return this;
        }

        public Builder ssaoBias(float value) {
            this.ssaoBias = clamp(value);
            return this;
        }

        public FamilySpec build() {
            return new FamilySpec(this);
        }

        private static float clamp(float value) {
            if (Float.isNaN(value)) {
                return 1.0F;
            }
            return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
        }
    }

    @Override
    public String toString() {
        return "FamilySpec[" + id + " aliases=" + aliases.size()
                + (fallback ? " fallback" : "") + "]";
    }
}

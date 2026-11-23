package dev.bedrockshaders.config;

import java.util.Locale;

/**
 * Well-known Bedrock shader families, and the tuning this renderer applies when one of
 * their packs is loaded.
 *
 * <p>Bedrock packs are data, not code: they ship fog/lighting/atmosphere/colour-grading
 * JSON and nothing else. That means a pack cannot "look like Newb" on Java by itself - the
 * renderer has to reproduce the family's intent. The Newb family in particular is known
 * for soft natural light, warm golden sunsets, per-biome haze, a blue underwater fog,
 * aurora nights and a light touch on contrast, so a pack from that family should not be
 * rendered with this mod's neutral defaults.</p>
 *
 * <p>Detection is name-based because Bedrock packs rarely share a machine-readable author
 * field. It is deliberately forgiving: a false positive only nudges a few sliders that the
 * user can override.</p>
 */
public enum ShaderFamily {
    /** Not a recognised family: keep the mod's own Bedrock-faithful defaults. */
    GENERIC("generic", "bedrock_shaders.family.generic",
            /*fogScale*/ 1.0F, /*fogDensity*/ 1.0F,
            /*sun*/ 1.0F, /*exposure*/ 1.0F, /*saturation*/ 1.0F, /*contrast*/ 1.0F,
            /*bloom*/ 0.32F, /*caustics*/ 0.6F, /*waterReflection*/ 0.85F,
            /*underwaterFog*/ 1.0F, /*godrays*/ 1.0F, /*ssao*/ 0.85F),

    /**
     * Newb family: Newb X Legacy, LMI, Golden Legacy, Glow Legacy, Renewed, Reforged,
     * Nostalgia, Dawn, Ale, Dragon Complementary.
     *
     * <p>Signatures taken from the packs' own feature lists: warm golden light, denser
     * biome haze and morning mist, a distinctly blue underwater view with soft caustics,
     * aurora nights, glowing ores, and tone mapping that stays close to vanilla rather than
     * crushing contrast.</p>
     */
    NEWB("newb", "bedrock_shaders.family.newb",
            /*fogScale*/ 1.12F, /*fogDensity*/ 1.05F,
            /*sun*/ 1.06F, /*exposure*/ 1.02F, /*saturation*/ 1.05F, /*contrast*/ 0.97F,
            /*bloom*/ 0.36F, /*caustics*/ 0.7F, /*waterReflection*/ 0.9F,
            /*underwaterFog*/ 1.45F, /*godrays*/ 1.05F, /*ssao*/ 0.8F),

    /** YSS: lighting and water emphasis, mid-range friendly. */
    YSS("yss", "bedrock_shaders.family.yss",
            1.05F, 1.0F, 1.04F, 1.0F, 1.02F, 1.02F,
            0.3F, 0.65F, 0.92F, 1.2F, 1.0F, 0.85F),

    /** Eternity: cinematic, heavier post-processing, stronger reflections. */
    ETERNITY("eternity", "bedrock_shaders.family.eternity",
            1.0F, 1.1F, 1.0F, 1.05F, 1.06F, 1.08F,
            0.45F, 0.75F, 1.0F, 1.25F, 1.2F, 0.95F),

    /** Solas / Harmonics style: clean, crisp, blue-hour leaning. */
    SOLAS("solas", "bedrock_shaders.family.solas",
            0.95F, 0.95F, 1.02F, 1.0F, 1.03F, 1.0F,
            0.34F, 0.6F, 0.88F, 1.1F, 1.0F, 0.9F);

    public final String id;
    /** Translation key for the family name shown in the GUI. */
    public final String translationKey;

    /** Multiplier applied to the Bedrock fog window (bigger = haze reaches further). */
    public final float fogScale;
    public final float fogDensityScale;
    public final float sunIntensityScale;
    public final float exposureScale;
    public final float saturationScale;
    public final float contrastScale;
    public final float bloomBias;
    public final float causticsBias;
    public final float waterReflectionBias;
    /** Extra haze underwater: these packs all grade the water view colder and denser. */
    public final float underwaterFogScale;
    public final float godrayScale;
    public final float ssaoBias;

    ShaderFamily(String id, String translationKey,
                 float fogScale, float fogDensityScale,
                 float sunIntensityScale, float exposureScale, float saturationScale,
                 float contrastScale, float bloomBias, float causticsBias,
                 float waterReflectionBias, float underwaterFogScale,
                 float godrayScale, float ssaoBias) {
        this.id = id;
        this.translationKey = translationKey;
        this.fogScale = fogScale;
        this.fogDensityScale = fogDensityScale;
        this.sunIntensityScale = sunIntensityScale;
        this.exposureScale = exposureScale;
        this.saturationScale = saturationScale;
        this.contrastScale = contrastScale;
        this.bloomBias = bloomBias;
        this.causticsBias = causticsBias;
        this.waterReflectionBias = waterReflectionBias;
        this.underwaterFogScale = underwaterFogScale;
        this.godrayScale = godrayScale;
        this.ssaoBias = ssaoBias;
    }

    /**
     * Pack-name fragments per family, lower case.
     *
     * <p>A static map rather than a field: enum construction runs before instance fields
     * assigned inside the constructor are visible, so {@code switch (this)} there would
     * read a null array.</p>
     */
    private static String[] aliasesOf(ShaderFamily family) {
        return switch (family) {
            case NEWB -> new String[] {
                    "newb", "x legacy", "golden legacy", "glow legacy", "x lmi",
                    "renewed", "reforged", "nostalgia", "newb x dawn", "newb x ale",
                    "dragon complementary", "newb complementary"
            };
            case YSS -> new String[] {"yss"};
            case ETERNITY -> new String[] {"eternity"};
            case SOLAS -> new String[] {"solas", "harmonics", "harmony"};
            case GENERIC -> new String[0];
        };
    }

    /**
     * Identifies the family from a pack's file name, display name and description.
     * Returns {@link #GENERIC} when nothing matches.
     */
    public static ShaderFamily detect(String... hints) {
        StringBuilder joined = new StringBuilder();
        for (String hint : hints) {
            if (hint != null && !hint.isBlank()) {
                joined.append(' ').append(hint.toLowerCase(Locale.ROOT));
            }
        }
        if (joined.isEmpty()) {
            return GENERIC;
        }
        String text = joined.toString();
        // Extension packs get first refusal: an addon exists to teach this mod about a family
        // it does not ship, so its aliases must not be shadowed by a built-in long alias list.
        String extensionFamily = dev.bedrockshaders.api.ExtensionRegistry.matchFamilyId(text);
        if (extensionFamily != null) {
            return byId(extensionFamily);
        }
        // Order matters: check the more specific families before NEWB, whose alias list is
        // long enough to swallow names that merely mention a complementary-style shader.
        for (ShaderFamily family : new ShaderFamily[] {ETERNITY, SOLAS, YSS, NEWB}) {
            for (String alias : aliasesOf(family)) {
                if (text.contains(alias)) {
                    return family;
                }
            }
        }
        return GENERIC;
    }

    public static ShaderFamily byId(String id) {
        if (id != null) {
            for (ShaderFamily family : values()) {
                if (family.id.equalsIgnoreCase(id)) {
                    return family;
                }
            }
        }
        // An extension-only family has no enum constant; GENERIC keeps the built-in tuning
        // neutral and lets the addon's FamilySpec/hook supply the look instead of guessing.
        return GENERIC;
    }

    /** {@code true} when {@code id} names a built-in family rather than an extension one. */
    public static boolean isBuiltIn(String id) {
        if (id != null) {
            for (ShaderFamily family : values()) {
                if (family.id.equalsIgnoreCase(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Bridges the enum to the public API record so addons and built-ins share one vocabulary. */
    public dev.bedrockshaders.api.FamilySpec toSpec() {
        return dev.bedrockshaders.api.FamilySpec.builder(id, translationKey)
                .fogScale(fogScale)
                .fogDensityScale(fogDensityScale)
                .sunScale(sunIntensityScale)
                .underwaterFogScale(underwaterFogScale)
                .exposureScale(exposureScale)
                .saturationScale(saturationScale)
                .contrastScale(contrastScale)
                .bloomBias(bloomBias)
                .causticsBias(causticsBias)
                .waterReflectionBias(waterReflectionBias)
                .godrayScale(godrayScale)
                .ssaoBias(ssaoBias)
                .aliases(aliasesOf(this))
                .build();
    }
}

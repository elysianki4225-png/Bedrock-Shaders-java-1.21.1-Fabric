package dev.bedrockshaders.config;

/** Curated quality tiers. Each tier tunes the pipeline cost/quality trade-off. */
public enum QualityPreset {
    POTATO("potato", 0.55F, false, false, false, false, 0.45F, 0.55F, 0.0F),
    LOW("low", 0.75F, true, false, false, true, 0.6F, 0.7F, 0.15F),
    BALANCED("balanced", 1.0F, true, true, true, true, 0.85F, 0.9F, 0.32F),
    HIGH("high", 1.0F, true, true, true, true, 1.0F, 1.0F, 0.4F),
    ULTRA("ultra", 1.25F, true, true, true, true, 1.25F, 1.0F, 0.5F);

    public final String id;
    public final float renderScale;
    public final boolean ssao;
    public final boolean reflections;
    public final boolean bloom;
    public final boolean godrays;
    public final float ssaoStrength;
    public final float reflectionStrength;
    public final float bloomStrength;

    QualityPreset(String id, float renderScale, boolean ssao, boolean reflections, boolean bloom,
                  boolean godrays, float ssaoStrength, float reflectionStrength, float bloomStrength) {
        this.id = id;
        this.renderScale = renderScale;
        this.ssao = ssao;
        this.reflections = reflections;
        this.bloom = bloom;
        this.godrays = godrays;
        this.ssaoStrength = ssaoStrength;
        this.reflectionStrength = reflectionStrength;
        this.bloomStrength = bloomStrength;
    }

    public static QualityPreset byId(String id) {
        for (QualityPreset preset : values()) {
            if (preset.id.equalsIgnoreCase(id)) {
                return preset;
            }
        }
        return BALANCED;
    }
}

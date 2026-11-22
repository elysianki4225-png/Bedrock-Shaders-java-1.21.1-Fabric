package dev.bedrockshaders.client.pack;

/**
 * What a dropped Bedrock archive turned out to be. Most real-world {@code .mcpack}
 * files are layered (a zip containing zips), so detection is iterative rather than a
 * single extension check.
 */
public enum BedrockPackKind {
    /** Deferred Rendering data only: fog / lighting / atmospherics / colour grading JSON. */
    DEFERRED_DATA("data"),
    /** Bedrock resource pack without shader data (textures, models, sounds...). */
    RESOURCE("resource"),
    /** Bedrock behaviour pack ({@code is_behaviour_pack}) - never renderable. */
    BEHAVIOUR("behaviour"),
    /** Skin pack / world template / unsupported container. */
    UNSUPPORTED("unsupported");

    private final String id;

    BedrockPackKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "bedrock_shaders.import.kind." + id;
    }
}

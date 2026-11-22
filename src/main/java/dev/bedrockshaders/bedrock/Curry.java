package dev.bedrockshaders.bedrock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Tiny null-safe JSON accessors used by all Bedrock asset parsers. */
final class Curry {
    private Curry() {
    }

    static JsonObject obj(JsonObject parent, String name) {
        if (parent == null) {
            return null;
        }
        JsonElement element = parent.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }
}

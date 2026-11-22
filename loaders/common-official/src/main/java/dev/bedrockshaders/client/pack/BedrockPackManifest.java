package dev.bedrockshaders.client.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The subset of a Bedrock {@code manifest.json} the importer needs: pack name, UUID,
 * version and whether this is a behaviour pack.
 */
public record BedrockPackManifest(String name, String uuid, String version, boolean behaviour,
                                  List<String> modules) {
    public static final BedrockPackManifest UNKNOWN = new BedrockPackManifest("unknown", "", "1.0.0", false, List.of());

    public static BedrockPackManifest parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject header = root.has("header") && root.get("header").isJsonObject()
                    ? root.getAsJsonObject("header") : null;
            String name = header != null && header.has("name") ? header.get("name").getAsString() : "unknown";
            String uuid = header != null && header.has("uuid") ? header.get("uuid").getAsString() : "";
            String version = header != null ? versionOf(header.get("version")) : "1.0.0";
            boolean behaviour = header != null && header.has("pack_scope")
                    && "world".equalsIgnoreCase(header.get("pack_scope").getAsString())
                    && header.has("is_behaviour_pack") && header.get("is_behaviour_pack").getAsBoolean();

            List<String> modules = new ArrayList<>();
            if (root.has("modules") && root.get("modules").isJsonArray()) {
                JsonArray array = root.getAsJsonArray("modules");
                for (JsonElement element : array) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject module = element.getAsJsonObject();
                    if (module.has("type")) {
                        modules.add(module.get("type").getAsString().toLowerCase(Locale.ROOT));
                    }
                }
            }
            return new BedrockPackManifest(name, uuid, version, behaviour, List.copyOf(modules));
        } catch (RuntimeException e) {
            return UNKNOWN;
        }
    }

    private static String versionOf(JsonElement element) {
        if (element == null) {
            return "1.0.0";
        }
        if (element.isJsonArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonElement part : element.getAsJsonArray()) {
                if (builder.length() > 0) {
                    builder.append('.');
                }
                builder.append(part.getAsString());
            }
            return builder.toString();
        }
        return element.getAsString();
    }

    /** A filesystem- and pack-name safe slug for the imported resource pack. */
    public String slug() {
        String base = name == null ? "pack" : name.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (char c : base.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                builder.append(c);
            } else if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '_') {
                builder.append('_');
            }
        }
        String slug = builder.toString().replaceAll("_+$", "");
        if (slug.isEmpty()) {
            slug = uuid.isEmpty() ? "bedrock_pack" : uuid.replace("-", "").substring(0, Math.min(8, uuid.replace("-", "").length()));
        }
        return slug.length() > 48 ? slug.substring(0, 48) : slug;
    }
}

package dev.bedrockshaders.api;

import dev.bedrockshaders.BedrockShadersMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code api.json} out of a resource pack so a pack author can extend this mod
 * without writing Java.
 *
 * <p>A data addon lives at {@code assets/<namespace>/bedrock_shaders/api.json}:</p>
 *
 * <pre>{@code
 * {
 *   "api": 1,
 *   "id": "aurora_pack",
 *   "name": "Aurora Pack",
 *   "families": [
 *     {
 *       "id": "aurora",
 *       "name_key": "aurora_pack.family.aurora",
 *       "aliases": ["aurora", "northern lights"],
 *       "fog_scale": 1.1,
 *       "underwater_fog_scale": 1.3,
 *       "bloom_bias": 0.4,
 *       "description": "cold nights, wide haze"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>Numbers a pack author leaves out default to 1.0 for multipliers and 0 for biases, i.e.
 * "no change", which is the only sane default for a data format that must stay forward
 * compatible. Every number is clamped by {@link FamilySpec}'s own bounds.</p>
 */
public final class JsonExtensionLoader {

    /** File name a data addon uses, relative to {@code assets/<namespace>/}. */
    public static final String DECLARATION_FILE = "bedrock_shaders/api.json";

    private JsonExtensionLoader() {
    }

    /**
     * Parses one declaration and registers its families.
     *
     * @param origin  where the JSON came from, for the log line
     * @return the ids that registered, empty when the document was unusable
     */
    public static List<String> load(String origin, String json) {
        List<String> registered = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return registered;
        }
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                BedrockShadersMod.LOGGER.warn("Extension declaration {} is not an object", origin);
                return registered;
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            BedrockShadersMod.LOGGER.warn("Extension declaration {} is not valid JSON: {}",
                    origin, e.getMessage());
            return registered;
        }

        int api = intOr(root, "api", 0);
        if (!BedrockShadersApi.supports(api)) {
            BedrockShadersMod.LOGGER.warn(
                    "Extension declaration {} targets API {} but this build offers {}",
                    origin, api, BedrockShadersApi.API_VERSION);
            return registered;
        }

        String owner = BedrockShadersApi.validateId(stringOr(root, "id", ""));
        if (owner == null) {
            BedrockShadersMod.LOGGER.warn("Extension declaration {} has no valid id ({})",
                    origin, BedrockShadersApi.ID_PATTERN);
            return registered;
        }

        ExtensionRegistry registry = ExtensionRegistry.get();
        for (JsonElement element : arrayOr(root, "families")) {
            FamilySpec spec = readFamily(origin, element);
            if (spec != null && registry.registerFamily(spec) != null) {
                registered.add(spec.id());
            }
        }
        if (!registered.isEmpty()) {
            BedrockShadersMod.LOGGER.info("Extension declaration {} from {} registered {} family(ies)",
                    owner, origin, registered.size());
        }
        return registered;
    }

    /** {@code null} when the element is not a usable family object. */
    private static FamilySpec readFamily(String origin, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String id = BedrockShadersApi.validateId(stringOr(object, "id", ""));
        if (id == null) {
            BedrockShadersMod.LOGGER.warn("Extension declaration {} has a family with no valid id",
                    origin);
            return null;
        }
        String key = stringOr(object, "name_key", "");
        if (key.isBlank()) {
            key = id;
        }
        FamilySpec.Builder builder = FamilySpec.builder(id, key)
                .fogScale(floatOr(object, "fog_scale", 1.0F))
                .fogDensityScale(floatOr(object, "fog_density_scale", 1.0F))
                .sunScale(floatOr(object, "sun_scale", 1.0F))
                .underwaterFogScale(floatOr(object, "underwater_fog_scale", 1.0F))
                .exposureScale(floatOr(object, "exposure_scale", 1.0F))
                .saturationScale(floatOr(object, "saturation_scale", 1.0F))
                .contrastScale(floatOr(object, "contrast_scale", 1.0F))
                .bloomBias(floatOr(object, "bloom_bias", 0.0F))
                .causticsBias(floatOr(object, "caustics_bias", 0.0F))
                .waterReflectionBias(floatOr(object, "water_reflection_bias", 0.0F))
                .godrayScale(floatOr(object, "godray_scale", 1.0F))
                .ssaoBias(floatOr(object, "ssao_bias", 0.0F));
        List<String> aliases = new ArrayList<>();
        for (JsonElement alias : arrayOr(object, "aliases")) {
            if (alias != null && alias.isJsonPrimitive()) {
                String text = alias.getAsString();
                if (text != null && !text.isBlank()) {
                    aliases.add(text);
                }
            }
        }
        if (!aliases.isEmpty()) {
            builder.aliases(aliases.toArray(new String[0]));
        }
        if (boolOr(object, "fallback", false)) {
            builder.fallback();
        }
        return builder.build();
    }

    private static String stringOr(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return value.getAsString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float floatOr(JsonObject object, String key, float fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            float parsed = value.getAsFloat();
            return Float.isNaN(parsed) || Float.isInfinite(parsed) ? fallback : parsed;
        } catch (RuntimeException e) {
            // A pack author typo must not stop the pack from loading.
            BedrockShadersMod.LOGGER.warn("Extension field {} is not a number, using {}", key, fallback);
            return fallback;
        }
    }

    private static int intOr(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return value.getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean boolOr(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return value.getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static JsonArray arrayOr(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) {
            return new JsonArray();
        }
        return value.getAsJsonArray();
    }
}

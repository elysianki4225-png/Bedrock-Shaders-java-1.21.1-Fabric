package dev.bedrockshaders.api;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Finds and installs extension packs.
 *
 * <p>Two routes, because two kinds of addon exist:</p>
 *
 * <ul>
 *   <li><b>Code addons</b> ship a class implementing {@link BedrockShadersExtension} and
 *       register it in {@code META-INF/services/dev.bedrockshaders.api.BedrockShadersExtension}.
 *       Found through {@link ServiceLoader}, which works the same on Fabric, Forge and
 *       NeoForge because it is plain JDK machinery.</li>
 *   <li><b>Data addons</b> ship no code at all: a resource pack with an
 *       {@code assets/<namespace>/bedrock_shaders/api.json} declaring families and the packs
 *       they cover. Those are read by {@link #loadDeclarations(List, ClassLoader)} callers
 *       hand in already-parsed JSON.</li>
 * </ul>
 *
 * <p>Every failure is contained: an addon that cannot be instantiated is logged and skipped,
 * never allowed to abort startup or the other addons.</p>
 */
public final class ExtensionDiscovery {

    /** Service file path a code addon must provide. */
    public static final String SERVICE_FILE =
            "META-INF/services/" + BedrockShadersExtension.class.getName();

    private ExtensionDiscovery() {
    }

    /**
     * Loads every code addon visible to {@code loader} and registers it.
     *
     * @return the ids that registered successfully
     */
    public static List<String> loadServices(ClassLoader loader) {
        List<String> loaded = new ArrayList<>();
        ServiceLoader<BedrockShadersExtension> services;
        try {
            services = ServiceLoader.load(BedrockShadersExtension.class, loader);
        } catch (Throwable error) {
            BedrockShadersMod.LOGGER.warn("Extension discovery unavailable: {}", error.toString());
            return loaded;
        }
        for (var iterator = services.iterator(); iterator.hasNext(); ) {
            BedrockShadersExtension extension;
            try {
                extension = iterator.next();
            } catch (Throwable error) {
                // One broken provider must not hide the ones behind it.
                BedrockShadersMod.LOGGER.warn("An extension pack failed to load: {}", error.toString());
                continue;
            }
            registerSafely(extension, loaded);
        }
        if (!loaded.isEmpty()) {
            BedrockShadersMod.LOGGER.info("Loaded {} extension pack(s) via ServiceLoader", loaded.size());
        }
        return loaded;
    }

    /** Registers one extension, keeping its surprises out of the caller. */
    public static void registerSafely(BedrockShadersExtension extension, List<String> loaded) {
        if (extension == null) {
            return;
        }
        try {
            ExtensionRegistry.Entry entry = ExtensionRegistry.get().register(extension);
            if (entry != null) {
                loaded.add(entry.id());
            }
        } catch (Throwable error) {
            BedrockShadersMod.LOGGER.warn("Extension {} could not register: {}",
                    extension.getClass().getName(), error.toString());
        }
    }

    /**
     * Description of what a code addon should look like, printed by
     * {@code /bedrock api guide} so pack authors do not have to read the source.
     */
    public static List<String> guide() {
        return List.of(
                "Extension packs extend this mod's renderer without forking it.",
                "",
                "1. Depend on dev.bedrockshaders.api (shipped in every jar).",
                "2. Implement dev.bedrockshaders.api.BedrockShadersExtension.",
                "3. Declare it in " + SERVICE_FILE + ".",
                "4. Register families, frame hooks and preprocessors from onRegister().",
                "",
                "Families teach the mod to recognise a pack:",
                "  registry.registerFamily(FamilySpec.builder(\"my_family\", \"your.addon.key\")",
                "      .aliases(\"my shader\", \"my pack\").fogScale(1.1F).build());",
                "",
                "Frame hooks adjust the final look every frame:",
                "  registry.registerFrameHook(id, (frame, delta) ->",
                "      delta.exposure = frame.underwater() ? 0.9F : 1.0F);",
                "",
                "Preprocessors inspect and extend packs at import time:",
                "  registry.registerPackPreprocessor(id, descriptor -> {",
                "      descriptor.addDescriptionNote(\"tuned by my addon\");",
                "      return descriptor;",
                "  });",
                "",
                "All tuning is multiplicative and clamped to "
                        + TuningDelta.MIN + ".." + TuningDelta.MAX + ".",
                "An extension that throws five times is disabled, not fatal.",
                "API version: " + BedrockShadersApi.API_VERSION);
    }
}

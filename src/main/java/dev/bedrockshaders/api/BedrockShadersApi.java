package dev.bedrockshaders.api;

/**
 * Entry point for extension packs.
 *
 * <pre>{@code
 * public final class MyAddon implements BedrockShadersExtension {
 *     @Override public String id() { return "my_addon"; }
 *
 *     @Override public void onRegister(ExtensionRegistry registry) {
 *         registry.registerFamily(FamilySpec.builder("mypack", "your.addon.family_key")
 *                 .aliases("my shader", "mypack x")
 *                 .fogScale(1.08f)
 *                 .sunScale(1.04f)
 *                 .build());
 *     }
 * }
 * }</pre>
 *
 * <h2>What this API promises</h2>
 * <ul>
 *   <li>Everything here is plain Java: no Minecraft class, no loader class, no GL call. An
 *       addon can be compiled against these interfaces alone and unit-tested offline.</li>
 *   <li>Hooks never throw into the renderer. A failure inside an extension is logged and
 *       the extension is marked faulty; the mod keeps rendering.</li>
 *   <li>Unknown {@code apiVersion} is refused at registration with a clear log line, so a
 *       future incompatible API cannot half-load an addon.</li>
 * </ul>
 *
 * <h2>How addons are discovered</h2>
 * <p>The platform entrypoint scans the resource pack stack for
 * {@code assets/<addon>/bedrock_shaders/api.json}, and for JVM mods it also accepts
 * {@link java.util.ServiceLoader} providers of {@link BedrockShadersExtension}. Either way
 * the addon only ever talks to this package.</p>
 */
public final class BedrockShadersApi {

    /** Bumped only for an incompatible change; addons declare the version they need. */
    public static final int API_VERSION = 1;

    /**
     * Namespace prefix for anything an addon registers. The mod refuses ids outside this
     * shape so two addons cannot silently claim the same name.
     */
    public static final String ID_PATTERN = "[a-z0-9_]{1,48}";

    private BedrockShadersApi() {
    }

    /** True when an addon written against {@code requiredVersion} can run against this mod. */
    public static boolean supports(int requiredVersion) {
        return requiredVersion >= 1 && requiredVersion <= API_VERSION;
    }

    /** Validates an addon/extension id, returning {@code null} when it is unusable. */
    public static String validateId(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim().toLowerCase(java.util.Locale.ROOT);
        return trimmed.matches(ID_PATTERN) ? trimmed : null;
    }
}

package dev.bedrockshaders.api;

import dev.bedrockshaders.BedrockShadersMod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where extensions register, and where the renderer reads what they registered.
 *
 * <p>Deliberately not an event bus: an addon cannot subscribe to arbitrary internals, only
 * to the shapes below. That keeps the mod free to refactor its renderer without breaking
 * addons, which is the entire point of having an API rather than exposing the internals.</p>
 *
 * <h2>Failure isolation</h2>
 * <p>Every hook call goes through {@link #guarded}, which catches {@link Throwable} (not
 * just {@link Exception}: a {@code NoClassDefFoundError} from a missing dependency is the
 * most likely failure of all), logs it once, counts it, and after a small budget disables
 * that extension for the rest of the session. One broken addon can therefore cost its own
 * features and nothing else.</p>
 *
 * <p>Thread-safe for registration during init and reads during the frame: families use a
 * copy-on-write list, hooks a volatile array.</p>
 */
public final class ExtensionRegistry {

    /** How many times one extension may throw before it is switched off. */
    public static final int FAILURE_BUDGET = 5;

    private static final ExtensionRegistry INSTANCE = new ExtensionRegistry();

    /** Status of one registered extension, for {@code /bedrock api list}. */
    public static final class Entry {
        private final BedrockShadersExtension extension;
        private final String id;
        private final String name;
        private final int apiVersion;
        private int failures;
        private boolean disabled;
        private String registeredFamilies = "";

        private Entry(BedrockShadersExtension extension) {
            this.extension = extension;
            this.id = BedrockShadersApi.validateId(extension.id());
            this.name = extension.displayName() == null ? extension.id() : extension.displayName();
            this.apiVersion = extension.apiVersion();
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        public int apiVersion() {
            return apiVersion;
        }

        public int failures() {
            return failures;
        }

        public boolean disabled() {
            return disabled;
        }

        public String registeredFamilies() {
            return registeredFamilies;
        }
    }

    private final List<BedrockShadersExtension> extensions = new ArrayList<>();
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** Copy-on-write: detection reads these on the render thread without locking. */
    private volatile List<FamilySpec> families = List.of();
    private volatile List<OwnedHook> frameHooks = List.of();
    private volatile List<PackPreprocessor> preprocessors = List.of();

    private ExtensionRegistry() {
    }

    public static ExtensionRegistry get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------ registration

    /**
     * Registers an extension and calls its {@code onRegister}.
     *
     * @return the entry, or {@code null} when the extension was refused (bad id, duplicate id
     *         or an incompatible {@code apiVersion}); the reason is logged
     */
    public synchronized Entry register(BedrockShadersExtension extension) {
        if (extension == null) {
            return null;
        }
        String id = BedrockShadersApi.validateId(extension.id());
        if (id == null) {
            BedrockShadersMod.LOGGER.warn("Extension refused: id {} is not {}",
                    safeId(extension), BedrockShadersApi.ID_PATTERN);
            return null;
        }
        if (!BedrockShadersApi.supports(extension.apiVersion())) {
            BedrockShadersMod.LOGGER.warn(
                    "Extension {} refused: it needs API {} but this build offers {}",
                    id, extension.apiVersion(), BedrockShadersApi.API_VERSION);
            return null;
        }
        if (entries.containsKey(id)) {
            BedrockShadersMod.LOGGER.warn("Extension {} refused: id already registered", id);
            return null;
        }

        Entry entry = new Entry(extension);
        entries.put(id, entry);
        extensions.add(extension);

        // Registration itself is isolated too: an addon that throws from onRegister keeps
        // whatever it managed to register, but is disabled for later hooks.
        List<String> before = familyIds();
        guarded(entry, "onRegister", () -> extension.onRegister(this));
        List<String> after = familyIds();
        List<String> added = new ArrayList<>(after);
        added.removeAll(before);
        entry.registeredFamilies = String.join(",", added);

        BedrockShadersMod.LOGGER.info("Extension registered: {} (API {}), {} family(ies)",
                id, extension.apiVersion(), added.size());
        return entry;
    }

    /** Registers a family. Returns the spec, or {@code null} when it was refused. */
    public synchronized FamilySpec registerFamily(FamilySpec spec) {
        if (spec == null) {
            return null;
        }
        String id = BedrockShadersApi.validateId(spec.id());
        if (id == null) {
            BedrockShadersMod.LOGGER.warn("Family refused: id {} is not {}", spec.id(),
                    BedrockShadersApi.ID_PATTERN);
            return null;
        }
        for (FamilySpec existing : families) {
            if (existing.id().equals(id)) {
                BedrockShadersMod.LOGGER.warn("Family {} refused: already registered", id);
                return null;
            }
        }
        List<FamilySpec> copy = new ArrayList<>(families);
        copy.add(spec);
        families = List.copyOf(copy);
        BedrockShadersMod.LOGGER.info("Family registered: {} ({} alias(es))", id, spec.aliases().size());
        return spec;
    }

    /** A hook plus the extension that registered it, so a failure lands on the right one. */
    public record OwnedHook(String ownerId, FrameHook hook) {
    }

    /** Registers a per-frame hook under the extension that owns it. */
    public synchronized void registerFrameHook(String ownerId, FrameHook hook) {
        if (hook == null) {
            return;
        }
        List<OwnedHook> copy = new ArrayList<>(frameHooks);
        copy.add(new OwnedHook(ownerId == null ? "unknown" : ownerId, hook));
        frameHooks = List.copyOf(copy);
        BedrockShadersMod.LOGGER.debug("Frame hook registered by {}", ownerId);
    }

    /** Registers an archive preprocessor, which may rewrite what the importer does. */
    public synchronized void registerPackPreprocessor(String ownerId, PackPreprocessor preprocessor) {
        if (preprocessor == null) {
            return;
        }
        List<PackPreprocessor> copy = new ArrayList<>(preprocessors);
        copy.add(preprocessor);
        preprocessors = List.copyOf(copy);
        BedrockShadersMod.LOGGER.debug("Pack preprocessor registered by {}", ownerId);
    }

    // ------------------------------------------------------------------ reads

    public List<FamilySpec> families() {
        return families;
    }

    public List<OwnedHook> frameHooks() {
        return frameHooks;
    }

    public List<PackPreprocessor> preprocessors() {
        return preprocessors;
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    public synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Resets everything. Only for tests and for a clean shutdown. */
    public synchronized void clear() {
        for (BedrockShadersExtension extension : extensions) {
            try {
                extension.onDispose();
            } catch (Throwable ignored) {
                // Disposing is best-effort; a failure here must not block the reset.
            }
        }
        extensions.clear();
        entries.clear();
        families = List.of();
        frameHooks = List.of();
        preprocessors = List.of();
    }

    /**
     * Tells every extension that the user's settings changed.
     *
     * <p>Called after a save, a preset switch or an undo. Extensions that throw are counted
     * against their failure budget like any other callback.</p>
     */
    public void notifyConfigChanged() {
        List<Entry> snapshot;
        synchronized (this) {
            if (extensions.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(entries.values());
        }
        ConfigView view = ConfigSource.current();
        for (Entry entry : snapshot) {
            if (entry.disabled) {
                continue;
            }
            guarded(entry, "onConfigChanged", () -> entry.extension.onConfigChanged(view));
        }
    }

    /**
     * Hands a freshly imported pack to every installed preprocessor.
     *
     * <p>Import runs off the render thread and only once per file, so this is allowed to be
     * the one unhurried path. Each stage sees the previous stage's result, and an extension
     * that throws is skipped for the rest of that import rather than aborting it.</p>
     *
     * @return the descriptor as they left it, or {@code null} when an extension vetoed it
     */
    public PackDescriptor runPreprocessors(PackDescriptor descriptor) {
        List<PackPreprocessor> snapshot = preprocessors();
        PackDescriptor current = descriptor;
        for (PackPreprocessor preprocessor : snapshot) {
            if (current == null) {
                return null;
            }
            try {
                PackDescriptor next = preprocessor.process(current);
                if (next == null) {
                    BedrockShadersMod.LOGGER.warn(
                            "An extension preprocessor dropped pack {}; continuing with the "
                                    + "previous result", descriptor.fileName());
                    return null;
                }
                current = next;
            } catch (Throwable error) {
                BedrockShadersMod.LOGGER.warn("Extension preprocessor failed on {}: {}",
                        descriptor.fileName(), error.toString());
            }
        }
        return current;
    }

    /**
     * Tells every extension that a pack was imported.
     *
     * <p>Each extension gets its <b>own</b> {@link PackWriter}, scoped to a namespace equal to
     * its own id. That is the whole point of the writer: an addon can drop extra JSON into the
     * pack it just helped import, but it cannot write into another addon's namespace, into this
     * mod's namespace, or anywhere outside the pack folder.</p>
     *
     * @param packDir the generated pack folder; the writer roots itself there
     * @return true when at least one extension ran
     */
    public boolean fireAfterImport(PackDescriptor descriptor, java.nio.file.Path packDir) {
        List<Entry> snapshot;
        synchronized (this) {
            if (extensions.isEmpty()) {
                return false;
            }
            snapshot = new ArrayList<>(entries.values());
        }
        boolean any = false;
        for (Entry entry : snapshot) {
            if (entry.disabled) {
                continue;
            }
            PackWriter writer = new PackWriter(packDir, entry.id);
            guarded(entry, "onAfterImport", () -> entry.extension.onAfterImport(descriptor, writer));
            any = true;
        }
        return any;
    }

    // ------------------------------------------------------------------ frame path

    /**
     * Runs every frame hook and folds their output into {@code output}.
     *
     * <p>Called on the render thread every frame, so the no-hook case is a single field read
     * and an early return. Each hook gets the shared delta reset first, and composes, so two
     * hooks touching the same field multiply rather than overwrite.</p>
     *
     * @return true when at least one hook changed something
     */
    public boolean applyFrameHooks(FrameHook.FrameView frame, TuningDelta output) {
        List<OwnedHook> hooks = frameHooks;
        if (hooks.isEmpty()) {
            return false;
        }
        output.reset();
        boolean changed = false;
        for (OwnedHook owned : hooks) {
            FrameHook hook = owned.hook();
            // Reset the scratch delta, run the hook, fold it in. The hook may write a partial
            // delta: untouched fields stay 1.0 / 0.0 and are identity for compose().
            scratch.reset();
            boolean ok = guardedHook(owned, () -> hook.apply(frame, scratch));
            if (!ok || scratch.isIdentity()) {
                continue;
            }
            output.compose(scratch.fogDensity, scratch.fogSpan, scratch.sunIntensity,
                    scratch.exposure, scratch.saturation, scratch.contrast,
                    scratch.bloomBias, scratch.causticsBias, scratch.underwaterFog);
            changed = true;
        }
        if (changed) {
            output.clamp();
        }
        return changed;
    }

    private final TuningDelta scratch = new TuningDelta();

    /**
     * Runs one hook, isolating a throw. The owner is recorded at registration, so a failing
     * hook is charged to the extension that registered it and never to an innocent one.
     */
    private boolean guardedHook(OwnedHook owned, Runnable action) {
        try {
            action.run();
            return true;
        } catch (Throwable error) {
            BedrockShadersMod.LOGGER.warn("Frame hook from {} failed: {}", owned.ownerId(),
                    error.toString());
            Entry entry;
            synchronized (this) {
                entry = entries.get(owned.ownerId());
            }
            if (entry != null) {
                recordFailure(entry, error);
            }
            return false;
        }
    }

    /** Runs {@code action}, isolating a throw from an extension. */
    private void guarded(Entry entry, String what, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            recordFailure(entry, error);
            BedrockShadersMod.LOGGER.warn("Extension {} failed in {}: {}",
                    entry.id, what, error.toString());
        }
    }

    private void recordFailure(Entry entry, Throwable error) {
        entry.failures++;
        if (entry.failures >= FAILURE_BUDGET && !entry.disabled) {
            entry.disabled = true;
            BedrockShadersMod.LOGGER.warn(
                    "Extension {} disabled after {} failure(s); last: {}",
                    entry.id, entry.failures, error.toString());
        }
    }

    private synchronized List<String> familyIds() {
        List<String> ids = new ArrayList<>(families.size());
        for (FamilySpec spec : families) {
            ids.add(spec.id());
        }
        return ids;
    }

    private static String safeId(BedrockShadersExtension extension) {
        try {
            return String.valueOf(extension.id());
        } catch (Throwable error) {
            return "<" + extension.getClass().getName() + ">";
        }
    }

    /**
     * Resolves a family by id across the built-in families and everything addons registered.
     *
     * @param builtIns consulted first, in their own order; may be empty
     */
    public static FamilySpec resolveFamily(String id, List<FamilySpec> builtIns) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String wanted = id.toLowerCase(Locale.ROOT);
        for (FamilySpec spec : builtIns) {
            if (spec.id().equals(wanted)) {
                return spec;
            }
        }
        for (FamilySpec spec : INSTANCE.families) {
            if (spec.id().equals(wanted)) {
                return spec;
            }
        }
        return null;
    }

    /**
     * Matches pack-name hints against every registered extension family, first match wins.
     *
     * <p>Called from the name-based family detection, so it must be cheap and must never
     * throw: an addon's alias list is arbitrary data as far as the registry is concerned.</p>
     *
     * @return the matching family id, or {@code null} when no extension claims the text
     */
    public static String matchFamilyId(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<FamilySpec> snapshot;
        synchronized (INSTANCE) {
            if (INSTANCE.families.isEmpty()) {
                return null;
            }
            snapshot = new ArrayList<>(INSTANCE.families);
        }
        for (FamilySpec spec : snapshot) {
            if (spec.matches(text)) {
                return spec.id();
            }
        }
        return null;
    }

    /** Families in registration order, for {@code /bedrock api list}. */
    public synchronized List<String> familyIdsSnapshot() {
        List<String> ids = new ArrayList<>();
        for (FamilySpec spec : families) {
            ids.add(spec.id());
        }
        return Collections.unmodifiableList(ids);
    }
}

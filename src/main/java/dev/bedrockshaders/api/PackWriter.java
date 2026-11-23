package dev.bedrockshaders.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes files into a generated pack after an import succeeded.
 *
 * <p>The only I/O an addon gets, and it is confined to one pack directory: paths are
 * resolved against that directory and anything escaping it is refused. An addon that ships
 * a language file for its own family badge uses this; an addon that wants arbitrary file
 * access does not get any.</p>
 */
public final class PackWriter {

    private final Path root;
    private final String namespace;

    public PackWriter(Path root, String namespace) {
        this.root = root;
        this.namespace = namespace;
    }

    /** Directory of the pack that was just generated. */
    public Path root() {
        return root;
    }

    /** The addon's namespace; writes outside {@code assets/<namespace>/} are refused. */
    public String namespace() {
        return namespace;
    }

    /** True when {@code relative} resolves inside this pack and, if it has one, its namespace. */
    public boolean canWrite(String relative) {
        Path target = resolve(relative);
        return target != null && allowed(target);
    }

    /** All files currently in the generated pack, relative to {@link #root()}. */
    public List<String> list() throws IOException {
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    /**
     * Writes a UTF-8 text file.
     *
     * @return true when written; false when the path was refused or the write failed
     */
    public boolean write(String relative, String contents) {
        Path target = resolve(relative);
        if (target == null || !allowed(target) || contents == null) {
            return false;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, contents, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** True when the file already exists in the pack (so an addon can avoid clobbering). */
    public boolean exists(String relative) {
        Path target = resolve(relative);
        return target != null && Files.isRegularFile(target);
    }

    private Path resolve(String relative) {
        if (relative == null || relative.isBlank()) {
            return null;
        }
        String cleaned = relative.replace('\\', '/');
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.contains("..")) {
            return null;
        }
        Path target = root.resolve(cleaned).normalize();
        return target.startsWith(root.normalize()) ? target : null;
    }

    /**
     * An addon may only write under its own asset namespace, plus {@code pack.png} which is
     * shared and harmless. {@code pack.mcmeta} and {@code assets/minecraft/**} are off limits:
     * they are the mod's output and, for the latter, the core shader programs themselves.
     */
    private boolean allowed(Path target) {
        String relative = root.normalize().relativize(target).toString().replace('\\', '/');
        // Explicit refusal, not an accident of the allow-list below: pack.mcmeta carries the
        // pack's declared format and the mod's own generated metadata, and corrupting it
        // makes the whole pack unreadable at the next scan.
        if (relative.equals("pack.mcmeta") || relative.endsWith("/pack.mcmeta")) {
            return false;
        }
        if (relative.equals("pack.png") || relative.equals("pack_icon.png")) {
            return true;
        }
        String owned = "assets/" + namespace + "/";
        if (!relative.startsWith(owned)) {
            return false;
        }
        return !relative.startsWith("assets/minecraft/");
    }
}

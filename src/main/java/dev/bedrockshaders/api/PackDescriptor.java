package dev.bedrockshaders.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an addon may know and change about an archive during an import.
 *
 * <p>Handed to {@link BedrockShadersExtension#onBeforeImport(PackDescriptor)}; the importer
 * uses whatever the addon leaves behind. Read-only fields (the entry list) are copies, so an
 * addon cannot corrupt the importer's view of the archive.</p>
 */
public final class PackDescriptor {

    private final String fileName;
    private final List<String> entries;
    private final Map<String, String> metadata;
    private String detectedFamily;
    private boolean renderDragon;
    /** Set by an addon to have this archive skipped entirely, with a reason for the log. */
    private String vetoReason;
    /** Extra literal files the addon wants written into the generated pack. */
    private final Map<String, String> extraFiles = new LinkedHashMap<>();
    /** Additions to the generated pack's description line. */
    private final List<String> descriptionNotes = new ArrayList<>();

    public PackDescriptor(String fileName, List<String> entries) {
        this.fileName = fileName == null ? "" : fileName;
        this.entries = List.copyOf(entries == null ? List.of() : entries);
        this.metadata = new LinkedHashMap<>();
    }

    public String fileName() {
        return fileName;
    }

    /** Every entry path in the archive, nested archives already flattened. */
    public List<String> entries() {
        return entries;
    }

    /** Free-form metadata; the importer seeds the pack name and version here. */
    public Map<String, String> metadata() {
        return metadata;
    }

    /** Family id detected by the mod before the addon runs; an addon may override it. */
    public String detectedFamily() {
        return detectedFamily;
    }

    public void setDetectedFamily(String family) {
        this.detectedFamily = family;
    }

    public boolean renderDragon() {
        return renderDragon;
    }

    public void setRenderDragon(boolean value) {
        this.renderDragon = value;
    }

    /** Skips this archive. The reason shows up in the import result and the log. */
    public void veto(String reason) {
        this.vetoReason = reason == null ? "vetoed by an extension" : reason;
    }

    public String vetoReason() {
        return vetoReason;
    }

    /**
     * Queues a literal file to be written into the generated pack. Paths are relative to the
     * pack root ({@code assets/...}), and an addon can only write under its own namespace:
     * attempting to overwrite {@code pack.mcmeta} or another addon's assets is refused.
     */
    public void addFile(String path, String contents) {
        if (path == null || contents == null || path.isBlank()) {
            return;
        }
        extraFiles.put(path, contents);
    }

    public Map<String, String> extraFiles() {
        return extraFiles;
    }

    /** Adds one line to the generated pack description. */
    public void addDescriptionNote(String note) {
        if (note != null && !note.isBlank() && descriptionNotes.size() < 4) {
            descriptionNotes.add(note);
        }
    }

    public List<String> descriptionNotes() {
        return descriptionNotes;
    }

    @Override
    public String toString() {
        return "PackDescriptor[" + fileName + ", " + entries.size() + " entries"
                + (renderDragon ? ", RenderDragon" : "") + "]";
    }
}

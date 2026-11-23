package dev.bedrockshaders.client.pack;

import java.nio.file.Path;

/**
 * One Bedrock archive the user dropped, plus where its converted Java resource pack
 * lives. Persisted in {@code index.json} so state survives restarts without rescanning
 * every zip.
 */
public final class ImportedPack {
    public String fileName = "";
    public String displayName = "";
    public String uuid = "";
    public String version = "1.0.0";
    public String kind = BedrockPackKind.DEFERRED_DATA.id();
    /**
     * Recognised shader family id ({@code newb}, {@code yss}, ...), detected at import
     * time from the archive name, display name and description. Empty means generic.
     */
    public String family = "";
    /**
     * This archive was a RenderDragon pack and only its data half was imported. Persisted so
     * the pack list can say so on every later launch, not just in the once-per-import chat
     * warning: a user who sees a half-working pack months later deserves the explanation.
     */
    public boolean renderDragon;
    /** Compiled material files that were skipped, quoted in the pack list. */
    public int renderDragonBinaries;
    public String folder = "";
    public int fogFiles;
    public int lightingFiles;
    public int atmosphericFiles;
    public int gradingFiles;
    public boolean hasWaterTable;
    public boolean enabled = true;
    public long importedAt;
    public String error = "";

    public ImportedPack() {
    }

    public static ImportedPack of(String fileName, BedrockPackManifest manifest, BedrockPackKind kind, String folder) {
        ImportedPack pack = new ImportedPack();
        pack.fileName = fileName;
        pack.displayName = manifest.name();
        pack.uuid = manifest.uuid();
        pack.version = manifest.version();
        pack.kind = kind.id();
        pack.folder = folder;
        pack.importedAt = System.currentTimeMillis();
        // Detect the family once, at import time, from everything the archive tells us.
        pack.family = dev.bedrockshaders.config.ShaderFamily
                .detect(fileName, manifest.name(), manifest.description()).id;
        return pack;
    }

    public BedrockPackKind packKind() {
        for (BedrockPackKind candidate : BedrockPackKind.values()) {
            if (candidate.id().equals(kind)) {
                return candidate;
            }
        }
        return BedrockPackKind.UNSUPPORTED;
    }

    public int totalDataFiles() {
        return fogFiles + lightingFiles + atmosphericFiles + gradingFiles;
    }

    /** Directory of the generated Java resource pack, for the pack manager. */
    public Path packDirectory(Path root) {
        return root.resolve(folder);
    }

    /** The tuning family this pack belongs to. */
    public dev.bedrockshaders.config.ShaderFamily shaderFamily() {
        if (family != null && !family.isEmpty()) {
            return dev.bedrockshaders.config.ShaderFamily.byId(family);
        }
        return dev.bedrockshaders.config.ShaderFamily.detect(fileName, displayName, error);
    }

    public String describe() {
        return displayName + " " + version + " (" + fogFiles + " fog, " + lightingFiles + " lighting, "
                + atmosphericFiles + " atm, " + gradingFiles + " grade"
                + (hasWaterTable ? ", water" : "") + ")";
    }
}

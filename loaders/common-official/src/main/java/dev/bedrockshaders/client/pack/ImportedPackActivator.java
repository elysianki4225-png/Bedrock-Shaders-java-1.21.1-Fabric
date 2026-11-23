package dev.bedrockshaders.client.pack;

import dev.bedrockshaders.BedrockShadersMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Enables the converted packs in vanilla's resource-pack stack.
 *
 * <p>Converted packs are written to {@code resourcepacks/bedrock_shaders_<name>/}, which
 * vanilla scans natively, so the profile id is always {@code file/<folder>}. This class
 * only has to keep {@code options.txt}'s enabled list in sync: it adds profiles for
 * active imports, drops profiles for deleted/disabled ones, and appends new packs at the
 * <b>end</b> of the list. The end position is deliberate - Minecraft applies packs in
 * order with later packs winning, so imported Bedrock data overrides the bundled sample
 * JSON while any pack the user placed above still wins.</p>
 */
public final class ImportedPackActivator {
    private static String lastSignature = "";

    private ImportedPackActivator() {
    }

    /**
     * Syncs the enabled list with the registry.
     *
     * @return true when the enabled set changed and resources should be reloaded
     */
    public static boolean apply(Minecraft client) {
        if (client == null) {
            return false;
        }
        PackRepository manager = client.getResourcePackRepository();
        Options options = client.options;
        if (manager == null || options == null) {
            return false;
        }
        List<ImportedPack> active = PackRegistry.get().active();
        String signature = signature(active);
        if (active.isEmpty() && lastSignature.isEmpty()) {
            return false;
        }

        try {
            // Pick up folders written while the game was running.
            manager.reload();
        } catch (RuntimeException e) {
            BedrockShadersMod.LOGGER.warn("Could not rescan resource packs", e);
            return false;
        }

        Collection<Pack> profiles = manager.getAvailablePacks();
        List<String> wanted = new ArrayList<>();
        for (ImportedPack pack : active) {
            String folder = pack.packDirectory(PackRegistry.generatedFolder()).getFileName().toString();
            Pack profile = findProfile(profiles, folder);
            if (profile == null) {
                BedrockShadersMod.LOGGER.warn("Converted pack {} is not visible to the pack manager yet", folder);
                continue;
            }
            wanted.add(profile.getId());
        }

        List<String> enabled = new ArrayList<>(options.resourcePacks);
        List<String> before = List.copyOf(enabled);
        // Remove stale converted packs (deleted or disabled imports).
        enabled.removeIf(id -> id.contains(PackRegistry.GENERATED_PREFIX) && !wanted.contains(id));
        for (String id : wanted) {
            if (!enabled.contains(id)) {
                enabled.add(id);
            }
        }

        lastSignature = signature;
        if (enabled.equals(before)) {
            return false;
        }
        options.resourcePacks.clear();
        options.resourcePacks.addAll(enabled);
        options.save();
        BedrockShadersMod.LOGGER.info("Resource packs synced: {} converted Bedrock pack(s) enabled", wanted.size());
        return true;
    }

    private static Pack findProfile(Collection<Pack> profiles, String folder) {
        String expected = "file/" + folder;
        for (Pack profile : profiles) {
            if (profile.getId().equals(expected)) {
                return profile;
            }
        }
        // Defensive: folder names are sanitised, so fall back to a case-insensitive match.
        String lower = expected.toLowerCase(Locale.ROOT);
        for (Pack profile : profiles) {
            if (profile.getId().toLowerCase(Locale.ROOT).equals(lower)) {
                return profile;
            }
        }
        return null;
    }

    private static String signature(List<ImportedPack> packs) {
        StringBuilder builder = new StringBuilder();
        for (ImportedPack pack : packs) {
            builder.append(pack.folder).append(';');
        }
        return builder.toString();
    }

    /** Applies the enabled set and reloads resources if anything changed. */
    public static void reload(Minecraft client) {
        if (client != null && apply(client)) {
            client.reloadResourcePacks();
        }
    }

    /** Forces the next {@link #apply} to re-check even if the signature is unchanged. */
    public static void invalidate() {
        lastSignature = "";
    }
}

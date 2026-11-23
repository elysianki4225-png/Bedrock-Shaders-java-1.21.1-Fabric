package dev.bedrockshaders.client;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Reload listener shared by the Forge and NeoForge builds.
 *
 * <p>Both loaders register this class directly, so the "shader sources + Bedrock assets +
 * regenerate the pack" sequence only exists once. {@code apply} does the work because the
 * Bedrock JSON parsing is synchronous and cheap (a few hundred small files).</p>
 */
public final class BedrockReloadListener extends SimplePreparableReloadListener<Void> {
    @Override
    protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
        return null;
    }

    @Override
    protected void apply(Void unused, ResourceManager manager, ProfilerFiller profiler) {
        BedrockShadersClient.onReload(manager);
    }
}

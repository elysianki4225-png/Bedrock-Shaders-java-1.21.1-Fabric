package dev.bedrockshaders.client;

import dev.bedrockshaders.client.render.ClientSnapshot;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

/**
 * Yarn-side implementation of {@link ClientSnapshot}. This is the only place the frame
 * calculation touches Yarn names, so the Forge/NeoForge ports only need their own copy of
 * this one class instead of a rewrite of {@code FrameContext}.
 */
public final class FabricClientSnapshot implements ClientSnapshot {
    private static final FabricClientSnapshot INSTANCE = new FabricClientSnapshot();

    private final MinecraftClient client = MinecraftClient.getInstance();
    private ClientWorld world;
    private ClientPlayerEntity player;

    private FabricClientSnapshot() {
    }

    /** Reused every frame; the getters read live state. */
    public static FabricClientSnapshot get() {
        return INSTANCE;
    }

    /** Refreshes the cached world/player; call once per frame before the getters. */
    public FabricClientSnapshot refresh() {
        this.world = client.world;
        this.player = client.player;
        return this;
    }

    private ClientWorld world() {
        return world != null ? world : client.world;
    }

    @Override
    public boolean inWorld() {
        return refresh().world != null && player != null;
    }

    @Override
    public String dimensionKey() {
        ClientWorld current = world();
        return current == null ? "" : current.getRegistryKey().getValue().toString();
    }

    @Override
    public float tickDelta() {
        return client.getRenderTickCounter().getTickDelta(false);
    }

    @Override
    public double cameraX() {
        return client.gameRenderer.getCamera().getPos().x;
    }

    @Override
    public double cameraY() {
        return client.gameRenderer.getCamera().getPos().y;
    }

    @Override
    public double cameraZ() {
        return client.gameRenderer.getCamera().getPos().z;
    }

    @Override
    public int framebufferWidth() {
        return client.getWindow().getFramebufferWidth();
    }

    @Override
    public int framebufferHeight() {
        return client.getWindow().getFramebufferHeight();
    }

    @Override
    public float viewDistanceBlocks() {
        return client.options.getViewDistance().getValue() * 16.0F;
    }

    @Override
    public long dayTime() {
        ClientWorld current = world();
        return current == null ? 0L : current.getTimeOfDay();
    }

    @Override
    public float skyAngle(float tickDelta) {
        ClientWorld current = world();
        return current == null ? 0.0F : current.getSkyAngle(tickDelta);
    }

    @Override
    public float rainLevel(float tickDelta) {
        ClientWorld current = world();
        return current == null ? 0.0F : current.getRainGradient(tickDelta);
    }

    @Override
    public float thunderLevel(float tickDelta) {
        ClientWorld current = world();
        return current == null ? 0.0F : current.getThunderGradient(tickDelta);
    }

    @Override
    public float skyDarken(float tickDelta) {
        return client.gameRenderer.getSkyDarkness(tickDelta);
    }

    @Override
    public boolean cameraInWater() {
        return client.gameRenderer.getCamera().getSubmersionType()
                == net.minecraft.block.enums.CameraSubmersionType.WATER;
    }

    @Override
    public String biomeId(double x, double y, double z) {
        ClientWorld current = world();
        if (current == null) {
            return "";
        }
        RegistryEntry<Biome> entry = current.getBiome(BlockPos.ofFloored(x, y, z));
        return entry.getKey().map(key -> key.getValue().toString()).orElse("");
    }

    @Override
    public float biomeTemperature(double x, double y, double z) {
        ClientWorld current = world();
        return current == null ? 0.8F : current.getBiome(BlockPos.ofFloored(x, y, z)).value().getTemperature();
    }

    @Override
    public boolean biomeHasPrecipitation(double x, double y, double z) {
        ClientWorld current = world();
        return current != null && current.getBiome(BlockPos.ofFloored(x, y, z)).value().hasPrecipitation();
    }

    @Override
    public int minBuildHeight() {
        ClientWorld current = world();
        return current == null ? -64 : current.getBottomY();
    }

    @Override
    public int maxBuildHeight() {
        ClientWorld current = world();
        return current == null ? 320 : current.getTopY();
    }

    @Override
    public float waterSurfaceHeight(int x, int startY, int z, float fallback) {
        ClientWorld current = world();
        if (current == null) {
            return fallback;
        }
        BlockPos.Mutable pos = new BlockPos.Mutable(x, Math.min(startY, maxBuildHeight() - 1), z);
        for (int y = pos.getY(); y >= minBuildHeight(); y--) {
            pos.setY(y);
            BlockState state = current.getBlockState(pos);
            var fluid = state.getFluidState();
            if (!fluid.isEmpty() && fluid.isStill()) {
                return (float) y + fluid.getHeight();
            }
            if (state.isOpaqueFullCube(current, pos) && y < startY - 1) {
                // Hit solid ground without finding water: no water body here.
                return fallback;
            }
        }
        return fallback;
    }
}

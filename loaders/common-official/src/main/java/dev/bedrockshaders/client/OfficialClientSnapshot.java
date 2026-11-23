package dev.bedrockshaders.client;

import dev.bedrockshaders.client.render.ClientSnapshot;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;

/**
 * Mojang-mapped {@link ClientSnapshot}, shared by the Forge and NeoForge builds.
 *
 * <p>Yarn spells these {@code MinecraftClient#getRenderTickCounter}, {@code World#getTimeOfDay}
 * and {@code Camera#getSubmersionType}; the official names are {@code getDeltaTracker},
 * {@code getDayTime} and {@code getFluidInCamera}. Confining the translation here is what
 * keeps the frame calculation byte-identical to the Fabric build.</p>
 */
public final class OfficialClientSnapshot implements ClientSnapshot {
    private static final OfficialClientSnapshot INSTANCE = new OfficialClientSnapshot();

    private final Minecraft client = Minecraft.getInstance();
    private ClientLevel level;
    private LocalPlayer player;

    private OfficialClientSnapshot() {
    }

    public static OfficialClientSnapshot get() {
        return INSTANCE;
    }

    public OfficialClientSnapshot refresh() {
        this.level = client.level;
        this.player = client.player;
        return this;
    }

    private ClientLevel level() {
        return level != null ? level : client.level;
    }

    @Override
    public boolean inWorld() {
        return refresh().level != null && player != null;
    }

    @Override
    public String dimensionKey() {
        ClientLevel current = level();
        return current == null ? "" : current.dimension().location().toString();
    }

    @Override
<<<<<<< HEAD
=======
    public boolean inNether() {
        ClientLevel current = level();
        return current != null && current.dimension() == Level.NETHER;
    }

    @Override
    public boolean inEnd() {
        ClientLevel current = level();
        return current != null && current.dimension() == Level.END;
    }

    @Override
>>>>>>> 65b9f7e (feat(ux): scrollable settings menu, and a cheaper frame path)
    public float tickDelta() {
        return client.getTimer().getGameTimeDeltaPartialTick(false);
    }

    @Override
    public double cameraX() {
        return client.gameRenderer.getMainCamera().getPosition().x;
    }

    @Override
    public double cameraY() {
        return client.gameRenderer.getMainCamera().getPosition().y;
    }

    @Override
    public double cameraZ() {
        return client.gameRenderer.getMainCamera().getPosition().z;
    }

    @Override
    public int framebufferWidth() {
        return client.getWindow().getWidth();
    }

    @Override
    public int framebufferHeight() {
        return client.getWindow().getHeight();
    }

    @Override
    public float viewDistanceBlocks() {
        return client.options.getEffectiveRenderDistance() * 16.0F;
    }

    @Override
    public long dayTime() {
        ClientLevel current = level();
        return current == null ? 0L : current.getDayTime();
    }

    @Override
    public float skyAngle(float tickDelta) {
        ClientLevel current = level();
        return current == null ? 0.0F : current.getTimeOfDay(tickDelta);
    }

    @Override
    public float rainLevel(float tickDelta) {
        ClientLevel current = level();
        return current == null ? 0.0F : current.getRainLevel(tickDelta);
    }

    @Override
    public float thunderLevel(float tickDelta) {
        ClientLevel current = level();
        return current == null ? 0.0F : current.getThunderLevel(tickDelta);
    }

    @Override
    public float skyDarken(float tickDelta) {
        return client.gameRenderer.getDarkenWorldAmount(tickDelta);
    }

    @Override
    public boolean cameraInWater() {
        Camera camera = client.gameRenderer.getMainCamera();
        return camera.getFluidInCamera() == FogType.WATER;
    }

    @Override
    public String biomeId(double x, double y, double z) {
        ClientLevel current = level();
        if (current == null) {
            return "";
        }
        Holder<Biome> biome = current.getBiome(BlockPos.containing(x, y, z));
        return biome.unwrapKey().map(key -> key.location().toString()).orElse("");
    }

    @Override
    public float biomeTemperature(double x, double y, double z) {
        ClientLevel current = level();
        return current == null ? 0.8F
                : current.getBiome(BlockPos.containing(x, y, z)).value().getBaseTemperature();
    }

    @Override
    public boolean biomeHasPrecipitation(double x, double y, double z) {
        ClientLevel current = level();
        return current != null && current.getBiome(BlockPos.containing(x, y, z)).value().hasPrecipitation();
    }

    @Override
    public int minBuildHeight() {
        ClientLevel current = level();
        return current == null ? -64 : current.getMinBuildHeight();
    }

    @Override
    public int maxBuildHeight() {
        ClientLevel current = level();
        return current == null ? 320 : current.getMaxBuildHeight();
    }

    @Override
    public float waterSurfaceHeight(int x, int startY, int z, float fallback) {
        ClientLevel current = level();
        if (current == null) {
            return fallback;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, Math.min(startY, maxBuildHeight() - 1), z);
        for (int y = pos.getY(); y >= minBuildHeight(); y--) {
            pos.setY(y);
            BlockState state = current.getBlockState(pos);
            var fluid = state.getFluidState();
            if (!fluid.isEmpty() && fluid.isSource()) {
                return (float) y + fluid.getOwnHeight();
            }
            if (state.isSolidRender(current, pos) && y < startY - 1) {
                return fallback;
            }
        }
        return fallback;
    }
}

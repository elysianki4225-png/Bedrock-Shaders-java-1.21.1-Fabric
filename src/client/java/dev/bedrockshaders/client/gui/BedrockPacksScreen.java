package dev.bedrockshaders.client.gui;

import dev.bedrockshaders.client.pack.BedrockPackImporter;
import dev.bedrockshaders.client.pack.ImportedPack;
import dev.bedrockshaders.client.pack.ImportedPackActivator;
import dev.bedrockshaders.client.pack.PackRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Iris-style pack browser: lists every imported Bedrock pack, toggles it, opens the
 * import folder, imports a {@code .mcpack} by path, and links to vanilla's resource-pack
 * screen. Importing never needs a restart - the list refreshes and the pack stack is
 * resynced immediately.
 */
public final class BedrockPacksScreen extends Screen {
    private static final int ROW_HEIGHT = 24;

    private final Screen parent;
    private TextFieldWidget pathField;
    private String status = "";
    private int statusColor = 0xA0A0A0;
    private int scroll;
    private int rowsPerPage;

    public BedrockPacksScreen(Screen parent) {
        super(Text.translatable("bedrock_shaders.packs.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        PackRegistry.get().ensureFolders();
        rowsPerPage = Math.max(3, (height - 132) / ROW_HEIGHT);
        int left = width / 2 - 150;

        addDrawableChild(ButtonWidget.builder(Text.translatable("bedrock_shaders.packs.open_folder"), button -> {
            openFolder();
        }).dimensions(left, height - 84, 145, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("bedrock_shaders.packs.rescan"), button -> {
            rescan();
        }).dimensions(left + 155, height - 84, 145, 20).build());

        pathField = new TextFieldWidget(textRenderer, left, height - 60, 215, 20,
                Text.translatable("bedrock_shaders.packs.path_hint"));
        pathField.setMaxLength(512);
        pathField.setPlaceholder(Text.translatable("bedrock_shaders.packs.path_hint"));
        addDrawableChild(pathField);

        addDrawableChild(ButtonWidget.builder(Text.translatable("bedrock_shaders.packs.import_path"), button -> {
            importPath();
        }).dimensions(left + 220, height - 60, 80, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("bedrock_shaders.packs.vanilla_screen"), button -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.setScreen(new net.minecraft.client.gui.screen.pack.PackScreen(
                    client.getResourcePackManager(),
                    manager -> client.options.refreshResourcePacks(manager),
                    PackRegistry.generatedFolder(),
                    Text.translatable("bedrock_shaders.packs.vanilla_screen")));
        }).dimensions(left, height - 36, 300, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions(width / 2 - 50, height - 12, 100, 12).build());
    }

    private void openFolder() {
        PackRegistry.get().ensureFolders();
        Util.getOperatingSystem().open(PackRegistry.importFolder());
        setStatus(Text.translatable("bedrock_shaders.packs.status.opened").getString(), 0x80C080);
    }

    private void rescan() {
        PackRegistry.get().reloadFromDisk();
        List<BedrockPackImporter.Result> results = PackRegistry.get().importPending();
        ImportedPackActivator.invalidate();
        int ok = 0;
        int failed = 0;
        for (BedrockPackImporter.Result result : results) {
            if (result.success()) {
                ok++;
            } else {
                failed++;
            }
        }
        ImportedPackActivator.reload(MinecraftClient.getInstance());
        setStatus(Text.translatable("bedrock_shaders.packs.status.scanned", ok, failed).getString(),
                failed == 0 ? 0x80C080 : 0xE0C060);
        rebuild();
    }

    private void importPath() {
        String raw = pathField == null ? "" : pathField.getText().trim();
        if (raw.isEmpty()) {
            setStatus(Text.translatable("bedrock_shaders.packs.status.need_path").getString(), 0xE08080);
            return;
        }
        Path path = Path.of(raw);
        if (!Files.isRegularFile(path)) {
            setStatus(Text.translatable("bedrock_shaders.packs.status.no_file", raw).getString(), 0xE08080);
            return;
        }
        BedrockPackImporter.Result result = PackRegistry.get().importFile(path);
        ImportedPackActivator.invalidate();
        ImportedPackActivator.reload(MinecraftClient.getInstance());
        if (result.success()) {
            setStatus(Text.translatable("bedrock_shaders.packs.status.imported",
                    result.pack().displayName, result.pack().totalDataFiles()).getString(), 0x80C080);
        } else {
            setStatus(Text.translatable("bedrock_shaders.packs.status.failed", result.message()).getString(), 0xE08080);
        }
        rebuild();
    }

    private void rebuild() {
        this.clearChildren();
        this.init();
    }

    private void setStatus(String message, int color) {
        this.status = message;
        this.statusColor = color;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderInGameBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("bedrock_shaders.packs.subtitle", PackRegistry.importFolder().toString()),
                width / 2, 22, 0x9BB7D4);

        List<ImportedPack> packs = PackRegistry.get().imported();
        int left = width / 2 - 150;
        if (packs.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("bedrock_shaders.packs.empty"),
                    width / 2, 56, 0xC0C0C0);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("bedrock_shaders.packs.empty_hint"),
                    width / 2, 72, 0x909090);
        } else {
            int start = Math.min(scroll, Math.max(0, packs.size() - 1));
            for (int i = 0; i < Math.min(rowsPerPage, packs.size() - start); i++) {
                ImportedPack pack = packs.get(start + i);
                drawRow(context, pack, left, 40 + i * ROW_HEIGHT);
            }
        }

        if (!status.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, height - 96, statusColor);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawRow(DrawContext context, ImportedPack pack, int x, int y) {
        int width = 300;
        boolean hovered = false;
        context.fill(x, y, x + width, y + ROW_HEIGHT - 2, hovered ? 0x90202A38 : 0x80141A22);
        String name = pack.displayName + " " + pack.version;
        context.drawTextWithShadow(textRenderer, Text.literal(name), x + 4, y + 4, 0xFFFFFF);
        // Show the recognised family next to the counts: it explains why a Newb pack looks
        // different from a generic one, and tells the user the tuning is intentional.
        var family = pack.shaderFamily();
        if (family != dev.bedrockshaders.config.ShaderFamily.GENERIC) {
            String badge = Text.translatable(family.translationKey).getString();
            int badgeWidth = textRenderer.getWidth(badge) + 6;
            context.fill(x + width - 100 - badgeWidth, y + 3, x + width - 100, y + 13, 0x60204030);
            context.drawTextWithShadow(textRenderer, Text.literal(badge),
                    x + width - 100 - badgeWidth + 3, y + 4, 0x9FE0B0);
        }
        context.drawTextWithShadow(textRenderer, Text.literal(pack.describe()), x + 4, y + 14, 0x8899AA);

        int toggleX = x + width - 96;
        ButtonWidget toggle = ButtonWidget.builder(
                        Text.translatable(pack.enabled ? "bedrock_shaders.packs.on" : "bedrock_shaders.packs.off"),
                        button -> {
                            PackRegistry.get().setEnabled(pack.fileName, !pack.enabled);
                            ImportedPackActivator.invalidate();
                            ImportedPackActivator.reload(MinecraftClient.getInstance());
                            rebuild();
                        })
                .dimensions(toggleX, y + 2, 44, 18).build();
        addDrawableChild(toggle);

        addDrawableChild(ButtonWidget.builder(Text.translatable("bedrock_shaders.packs.delete"), button -> {
            PackRegistry.get().delete(pack.fileName);
            ImportedPackActivator.invalidate();
            ImportedPackActivator.reload(MinecraftClient.getInstance());
            setStatus(Text.translatable("bedrock_shaders.packs.status.deleted", pack.displayName).getString(), 0xE0C060);
            rebuild();
        }).dimensions(toggleX + 48, y + 2, 44, 18).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int packCount = PackRegistry.get().imported().size();
        int maxScroll = Math.max(0, packCount - rowsPerPage);
        if (maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

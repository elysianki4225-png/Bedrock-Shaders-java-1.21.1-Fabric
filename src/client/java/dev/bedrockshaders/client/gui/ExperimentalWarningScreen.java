package dev.bedrockshaders.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * A modal warning shown before an experimental feature is switched on, and when a pack
 * turns out to need it.
 *
 * <p>Two rules make it a warning rather than a nag:</p>
 * <ul>
 *   <li>it always names the specific limitation, not "may cause issues";</li>
 *   <li>it is only ever shown when something is actually being enabled or refused, so a
 *       user who never touches the feature never sees it.</li>
 * </ul>
 */
public class ExperimentalWarningScreen extends Screen {

    /** What the warning is about; keeps the strings in the language files. */
    public enum Kind {
        ENABLE_RENDER_DRAGON("bedrock_shaders.warn.renderdragon.title",
                "bedrock_shaders.warn.renderdragon.body"),
        RENDER_DRAGON_PACK("bedrock_shaders.warn.dragon_pack.title",
                "bedrock_shaders.warn.dragon_pack.body");

        private final String titleKey;
        private final String bodyKey;

        Kind(String titleKey, String bodyKey) {
            this.titleKey = titleKey;
            this.bodyKey = bodyKey;
        }

        public String titleKey() {
            return titleKey;
        }

        public String bodyKey() {
            return bodyKey;
        }
    }

    private final Screen parent;
    private final Kind kind;
    private final List<String> detailLines;
    private final Runnable onAccept;
    private final String acceptKey;

    public ExperimentalWarningScreen(Screen parent, Kind kind, List<String> detailLines,
                                     String acceptKey, Runnable onAccept) {
        super(Text.translatable(kind.titleKey()));
        this.parent = parent;
        this.kind = kind;
        this.detailLines = List.copyOf(detailLines);
        this.acceptKey = acceptKey;
        this.onAccept = onAccept;
    }

    @Override
    protected void init() {
        int y = this.height - 52;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable(acceptKey), button -> {
            close();
            if (onAccept != null) {
                onAccept.run();
            }
        }).dimensions(this.width / 2 - 155, y, 150, 20).build());

        // Cancel is first in tab order and on the left of the primary action: the safe
        // choice should never be the accidental one.
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(this.width / 2 + 5, y, 150, 20).build());
    }

    private void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.textRenderer, this.title, this.width / 2, 40, 0xFFAA00);

        List<Text> body = this.textRenderer.wrapLines(Text.translatable(kind.bodyKey()),
                Math.max(180, this.width - 80));
        int y = 70;
        for (Text line : body) {
            context.drawCenteredString(this.textRenderer, line, this.width / 2, y, 0xDDDDDD);
            y += 11;
        }

        if (!detailLines.isEmpty()) {
            y += 8;
            for (String detail : detailLines) {
                context.drawCenteredString(this.textRenderer, Text.literal(detail),
                        this.width / 2, y, 0x8FA8BF);
                y += 10;
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}

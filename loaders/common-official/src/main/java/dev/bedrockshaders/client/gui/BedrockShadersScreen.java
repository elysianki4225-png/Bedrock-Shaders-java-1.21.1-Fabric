package dev.bedrockshaders.client.gui;

import dev.bedrockshaders.client.BedrockShadersClient;
import dev.bedrockshaders.client.ConfigManager;
import dev.bedrockshaders.client.assets.BedrockAssetLibrary;
import dev.bedrockshaders.client.render.BedrockPipeline;
import dev.bedrockshaders.client.render.BedrockShaderPack;
import dev.bedrockshaders.client.render.FrameContext;
import dev.bedrockshaders.config.BedrockConfig;
import dev.bedrockshaders.config.QualityPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


/**
 * In-game Bedrock Shaders settings: a three-column, page-based menu that edits the
 * live {@link BedrockConfig}. Every change is applied immediately, so the user sees
 * the result behind the (translucent) menu.
 */
public final class BedrockShadersScreen extends Screen {
    private static final int COLUMN_WIDTH = 200;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 4;
<<<<<<< HEAD
=======
    /** Pixels per wheel notch: one row, so the content tracks the wheel predictably. */
    private static final int SCROLL_STEP = 24;
>>>>>>> 65b9f7e (feat(ux): scrollable settings menu, and a cheaper frame path)

    private enum Page {
        PRESETS("bedrock_shaders.page.presets"),
        ATMOSPHERE("bedrock_shaders.page.atmosphere"),
        WATER("bedrock_shaders.page.water"),
        EFFECTS("bedrock_shaders.page.effects"),
        PACKS("bedrock_shaders.page.packs"),
        GRADE("bedrock_shaders.page.grade"),
        SYSTEM("bedrock_shaders.page.system");

        final String translationKey;

        Page(String translationKey) {
            this.translationKey = translationKey;
        }
    }

<<<<<<< HEAD
    private final Screen parent;
    private Page page = Page.PRESETS;

=======
    private static final int TOP = 44;
    /** Bottom of the scrollable region: the footer (status + Done button) lives below it. */
    private int contentBottom() {
        return height - 56;
    }

    private final Screen parent;
    private Page page = Page.PRESETS;

    /** Vertical scroll offset in pixels; 0 when the page fits. */
    private int scroll;
    /** Height the current page needs, computed while building it. */
    private int contentHeight;

>>>>>>> 65b9f7e (feat(ux): scrollable settings menu, and a cheaper frame path)
    public BedrockShadersScreen(Screen parent) {
        super(Component.translatable("bedrock_shaders.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ConfigManager.get().pushUndoOnce();
        ConfigManager.get().armUndoOnOpen();
        if (ConfigManager.get().config() == null) {
            return;
        }
        int centerX = width / 2;
<<<<<<< HEAD
        int top = 44;
        buildPage(top, centerX);
=======
        int top = TOP - scroll;
        contentHeight = buildPage(top, centerX);
        // A page that no longer fits must not be left scrolled past its end.
        clampScroll();
>>>>>>> 65b9f7e (feat(ux): scrollable settings menu, and a cheaper frame path)
        addPageButtons(centerX);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(centerX - 100, height - 28, 200, 20).build());
    }

    private void addPageButtons(int centerX) {
        Page[] pages = Page.values();
        int pageWidth = Math.min(120, (width - 20) / pages.length);
        int totalWidth = pageWidth * pages.length;
        int left = centerX - totalWidth / 2;
        for (int i = 0; i < pages.length; i++) {
            Page value = pages[i];
            Button button = Button.builder(Component.translatable(value.translationKey), b -> {
                page = value;
                rebuild();
            }).bounds(left + i * pageWidth, 20, pageWidth - 2, 20).build();
            button.active = value != page;
            addRenderableWidget(button);
        }
    }

    /** Rebuilds the widgets for the current page (Screen#clearAndInit is final). */
    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

<<<<<<< HEAD
    private void buildPage(int top, int centerX) {
=======
    /**
     * Builds the widgets for the active page.
     *
     * @return the y coordinate just past the last row, which is the page's height in
     *         screen space; the caller uses it to bound scrolling.
     */
    private int buildPage(int top, int centerX) {
>>>>>>> 65b9f7e (feat(ux): scrollable settings menu, and a cheaper frame path)
        BedrockConfig config = ConfigManager.get().config();
        switch (page) {
            case PRESETS -> {
                int y = top;
                for (QualityPreset preset : QualityPreset.values()) {
                    addRenderableWidget(Button.builder(
                                    Component.translatable("bedrock_shaders.preset." + preset.id),
                                    button -> applyPreset(preset))
                            .bounds(centerX - 100, y, 200, ROW_HEIGHT).build());
                    y += ROW_HEIGHT + ROW_GAP;
                }
                addRenderableWidget(toggle(centerX - 100, y, 200, "bedrock_shaders.option.enabled",
                        config.enabled, value -> config.enabled = value));
<<<<<<< HEAD
=======
                return y + ROW_HEIGHT;
>>>>>>> 65b9f7e (feat(ux): scrollable settings menu, and a cheaper frame path)
            }
            case ATMOSPHERE -> {
                int y = top;
                addRenderableWidget(toggle(centerX - 100, y, 200, "bedrock_shaders.option.atmosphere",
                        config.atmosphere, value -> config.atmosphere = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(toggle(centerX - 100, y, 200, "bedrock_shaders.option.sky",
                        config.replaceVanillaSky, value -> config.replaceVanillaSky = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(toggle(centerX - 100, y, 200, "bedrock_shaders.option.clouds",
                        config.clouds, value -> config.clouds = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(toggle(centerX - 100, y, 200, "bedrock_shaders.option.stars",
                        config.stars, value -> config.stars = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(slider(centerX - 100, y, 200, "bedrock_shaders.option.fog_density",
                        config.fogDensity, 0.0F, 3.0F, value -> config.fogDensity = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(slider(centerX - 100, y, 200, "bedrock_shaders.option.sun_intensity",
                        config.sunIntensity, 0.0F, 3.0F, value -> config.sunIntensity = value));
<<<<<<< HEAD
=======
                return y + ROW_HEIGHT;
>>>>>>> 65b9f7e (feat(ux): scrollable settings menu, and a cheaper frame path)
            }
            case WATER -> {
                int y = top;
                addRenderableWidget(toggle(centerX - 100, y, 200, "bedrock_shaders.option.water",
                        config.water, value -> config.water = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(slider(centerX - 100, y, 200, "bedrock_shaders.option.wave_strength",
                        config.waterWaveStrength, 0.0F, 3.0F, value -> config.waterWaveStrength = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(slider(centerX - 100, y, 200, "bedrock_shaders.option.reflections",
                        config.waterReflectionStrength, 0.0F, 1.0F, value -> config.waterReflectionStrength = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(slider(centerX - 100, y, 200, "bedrock_shaders.option.refraction",
                        config.waterRefractionStrength, 0.0F, 2.0F, value -> config.waterRefractionStrength = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(slider(centerX - 100, y, 200, "bedrock_shaders.option.caustics",
                        config.caustics, 0.0F, 2.0F, value -> config.caustics = value));
<<<<<<< HEAD
=======
                return y + ROW_HEIGHT;
>>>>>>> 65b9f7e (feat(ux): scrollable settings menu, and a cheaper frame path)
            }
            case EFFECTS -> {
                int y = top;
                addRenderableWidget(toggle(centerX - 100, y, 200, "bedrock_shaders.option.ssao",
                        config.ssao, value -> config.ssao = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(slider(centerX - 100, y, 200, "bedrock_shaders.option.ssao_strength",
                        config.ssaoStrength, 0.0F, 2.0F, value -> config.ssaoStrength = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(toggle(centerX - 100, y, 200, "bedrock_shaders.option.bloom",
                        config.bloom, value -> config.bloom = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(slider(centerX - 100, y, 200, "bedrock_shaders.option.bloom_strength",
                        config.bloomStrength, 0.0F, 1.5F, value -> config.bloomStrength = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(toggle(centerX - 100, y, 200, "bedrock_shaders.option.godrays",
                        config.godrays, value -> config.godrays = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(slider(centerX - 100, y, 200, "bedrock_shaders.option.godray_strength",
                        config.godrayStrength, 0.0F, 3.0F, value -> config.godrayStrength = value));
<<<<<<< HEAD
=======
                return y + ROW_HEIGHT;
>>>>>>> 65b9f7e (feat(ux): scrollable settings menu, and a cheaper frame path)
            }
            case GRADE -> {
                int y = top;
                addRenderableWidget(toggle(centerX - 100, y, 200, "bedrock_shaders.option.tonemap",
                        config.tonemap, value -> config.tonemap = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(slider(centerX - 100, y, 200, "bedrock_shaders.option.exposure",
                        config.exposure, 0.2F, 3.0F, value -> config.exposure = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(slider(centerX - 100, y, 200, "bedrock_shaders.option.saturation",
                        config.saturation, 0.0F, 2.0F, value -> config.saturation = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(slider(centerX - 100, y, 200, "bedrock_shaders.option.contrast",
                        config.contrast, 0.5F, 2.0F, value -> config.contrast = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(Button.builder(Component.translatable("bedrock_shaders.action.reset_grade"),
                        button -> {
                            ConfigManager.get().edit(c -> {
                                c.saturation = 1.0F;
                                c.contrast = 1.0F;
                                c.exposure = 1.0F;
                            });
                        }).bounds(centerX - 100, y, 200, ROW_HEIGHT).build());
<<<<<<< HEAD
=======
                return y + ROW_HEIGHT;
>>>>>>> 65b9f7e (feat(ux): scrollable settings menu, and a cheaper frame path)
            }
            case PACKS -> {
                int y = top;
                addRenderableWidget(Button.builder(Component.translatable("bedrock_shaders.action.open_packs"),
                        button -> Minecraft.getInstance().setScreen(new BedrockPacksScreen(this)))
                        .bounds(centerX - 100, y, 200, ROW_HEIGHT).build());
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(toggle(centerX - 100, y, 200, "bedrock_shaders.option.auto_import",
                        config.autoImportPacks, value -> config.autoImportPacks = value));
                y += ROW_HEIGHT + ROW_GAP;
                int imported = dev.bedrockshaders.client.pack.PackRegistry.get().imported().size();
                addRenderableWidget(Button.builder(
                                Component.translatable("bedrock_shaders.action.import_now", imported),
                                button -> {
                                    var registry = dev.bedrockshaders.client.pack.PackRegistry.get();
                                    registry.ensureFolders();
                                    registry.importPending();
                                    dev.bedrockshaders.client.pack.ImportedPackActivator.invalidate();
                                    dev.bedrockshaders.client.pack.ImportedPackActivator.reload(Minecraft.getInstance());
                                    rebuild();
                                })
                        .bounds(centerX - 100, y, 200, ROW_HEIGHT).build());
<<<<<<< HEAD
=======
                return y + ROW_HEIGHT;
>>>>>>> 65b9f7e (feat(ux): scrollable settings menu, and a cheaper frame path)
            }
            case SYSTEM -> {
                int y = top;
                addRenderableWidget(Button.builder(Component.translatable("bedrock_shaders.action.reload_shaders"),
                        button -> {
                            BedrockShaderPack.regenerateFromCurrentResources();
                            Minecraft.getInstance().reloadResourcePacks();
                        }).bounds(centerX - 100, y, 200, ROW_HEIGHT).build());
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(toggle(centerX - 100, y, 200, "bedrock_shaders.option.debug",
                        config.debugOverlay, value -> config.debugOverlay = value));
                y += ROW_HEIGHT + ROW_GAP;
                addRenderableWidget(Button.builder(Component.translatable("bedrock_shaders.action.reset_all"),
                        button -> ConfigManager.get().resetToDefaults())
                        .bounds(centerX - 100, y, 200, ROW_HEIGHT).build());
<<<<<<< HEAD
            }
        }
=======
                return y + ROW_HEIGHT;
            }
        }
        return top;
    }

    /** How far the current page can be scrolled before its last row leaves the footer. */
    private int maxScroll() {
        int available = contentBottom() - TOP;
        return Math.max(0, contentHeight - available);
    }

    private void clampScroll() {
        int max = maxScroll();
        if (scroll > max) {
            scroll = max;
        }
        if (scroll < 0) {
            scroll = 0;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll() <= 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        scroll -= (int) Math.signum(verticalAmount) * SCROLL_STEP;
        clampScroll();
        rebuild();
        return true;
>>>>>>> 65b9f7e (feat(ux): scrollable settings menu, and a cheaper frame path)
    }

    private void applyPreset(QualityPreset preset) {
        ConfigManager.get().pushUndo();
        ConfigManager.get().edit(config -> config.applyPreset(preset));
        rebuild();
    }

<<<<<<< HEAD
    private CycleButton<Boolean> toggle(int x, int y, int width, String key, boolean initial,
                                                java.util.function.Consumer<Boolean> setter) {
        return CycleButton.onOffBuilder(initial)
                .create(x, y, width, ROW_HEIGHT, Component.translatable(key),
=======
    private CyclingButton<Boolean> toggle(int x, int y, int width, String key, boolean initial,
                                                java.util.function.Consumer<Boolean> setter) {
        return CyclingButton.onOffBuilder()
                .initially(initial)
                .build(x, y, width, ROW_HEIGHT, Component.translatable(key),
>>>>>>> 65b9f7e (feat(ux): scrollable settings menu, and a cheaper frame path)
                        (button, value) -> {
                            setter.accept(value);
                            ConfigManager.get().markDirty();
                        });
    }

    private AbstractSliderButton slider(int x, int y, int width, String key, float initial, float min, float max,
                                java.util.function.Consumer<Float> setter) {
        return new ValueSlider(x, y, width, ROW_HEIGHT, Component.translatable(key), initial, min, max, setter);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);

        BedrockPipeline pipeline = BedrockPipeline.get();
        FrameContext frame = FrameContext.get();
        BedrockAssetLibrary assets = BedrockAssetLibrary.get();
        String status = Component.translatable("bedrock_shaders.status.line",
                pipeline.isReady() ? Component.translatable("bedrock_shaders.status.active")
                        : Component.translatable("bedrock_shaders.status.fallback"),
                assets.parsedFiles(), frame.fogId()).getString();
        context.drawCenteredString(font, Component.literal(status), width / 2, height - 44, 0x9BB7D4);
<<<<<<< HEAD
=======
        // Tell the user there is more below (and above) when the page does not fit.
        if (maxScroll() > 0) {
            if (scroll > 0) {
                context.drawCenteredString(font,
                        Component.translatable("bedrock_shaders.hint.scroll_up"),
                        width / 2, 34, 0x8899AA);
            }
            if (scroll < maxScroll()) {
                context.drawCenteredString(font,
                        Component.translatable("bedrock_shaders.hint.scroll_down"),
                        width / 2, contentBottom() - 10, 0x8899AA);
            }
        }
>>>>>>> 65b9f7e (feat(ux): scrollable settings menu, and a cheaper frame path)
        if (ConfigManager.get().isDirty()) {
            context.drawString(font,
                    Component.translatable("bedrock_shaders.hint.unsaved"), 6, height - 52, 0xE0C060);
        }
    }

    /** {@code Screen#close()} is final in Mojang's mappings; this is the overridable hook. */
    @Override
    public void onClose() {
        ConfigManager.get().save();
        Minecraft client = Minecraft.getInstance();
        client.setScreen(parent);
    }

    /** A float slider with a keyboard/step-friendly API. */
    private static final class ValueSlider extends AbstractSliderButton {
        private final float min;
        private final float max;
        private final java.util.function.Consumer<Float> setter;
        private final Component label;

        ValueSlider(int x, int y, int width, int height, Component label, float value, float min, float max,
                    java.util.function.Consumer<Float> setter) {
            super(x, y, width, height, label, (value - min) / (max - min));
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.label = label;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            float current = min + (max - min) * (float) this.value;
            setMessage(Component.translatable("bedrock_shaders.slider", label, String.format("%.2f", current)));
        }

        @Override
        protected void applyValue() {
            setter.accept(min + (float) (max - min) * (float) this.value);
            ConfigManager.get().markDirty();
        }
    }
}

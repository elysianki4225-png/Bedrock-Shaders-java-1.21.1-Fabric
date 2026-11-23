package dev.bedrockshaders.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.bedrockshaders.client.ConfigManager;
import dev.bedrockshaders.client.assets.BedrockAssetLibrary;
import dev.bedrockshaders.client.gui.BedrockShadersScreen;
import dev.bedrockshaders.client.render.BedrockPipeline;
import dev.bedrockshaders.client.render.BedrockShaderPack;
import dev.bedrockshaders.client.render.FrameContext;
import dev.bedrockshaders.client.render.RenderCompat;
import dev.bedrockshaders.config.QualityPreset;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client-side command tree:
 * <pre>
 *   /bedrock menu                       open the settings screen
 *   /bedrock toggle                     enable / disable the pipeline
 *   /bedrock preset &lt;potato..ultra&gt;
 *   /bedrock reload                     regenerate the shader pack and reload resources
 *   /bedrock debug                      log pipeline diagnostics
 *   /bedrock fog                        print the active Bedrock biome bindings
 *   /bedrock effects                    list every loaded Bedrock fog identifier
 * </pre>
 */
public final class BedrockShadersCommand {
    private BedrockShadersCommand() {
    }

    private static int openMenu() {
        MinecraftClient.getInstance().execute(() -> {
            ConfigManager.get().armUndoOnOpen();
            MinecraftClient.getInstance().setScreen(new BedrockShadersScreen(null));
        });
        return 1;
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = literal("bedrock")
                .executes(ctx -> openMenu())
                .then(literal("menu").executes(ctx -> openMenu()))
                .then(literal("toggle").executes(ctx -> {
                    boolean enabled = !ConfigManager.get().config().enabled;
                    ConfigManager.get().edit(config -> config.enabled = enabled);
                    ConfigManager.get().save();
                    BedrockShaderPack.regenerateFromCurrentResources();
                    ctx.getSource().sendFeedback(Text.translatable(enabled
                            ? "bedrock_shaders.message.enabled"
                            : "bedrock_shaders.message.disabled"));
                    return 1;
                }))
                .then(literal("preset")
                        .then(argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (QualityPreset preset : QualityPreset.values()) {
                                        builder.suggest(preset.id);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                                    QualityPreset preset = QualityPreset.byId(name);
                                    ConfigManager.get().pushUndo();
                                    ConfigManager.get().edit(config -> config.applyPreset(preset));
                                    ConfigManager.get().save();
                                    ctx.getSource().sendFeedback(Text.literal("Bedrock Shaders preset: " + preset.id));
                                    return 1;
                                })))
                .then(literal("reload").executes(ctx -> {
                    ConfigManager.get().load();
                    BedrockShaderPack.regenerateFromCurrentResources();
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.reloadResources();
                    ctx.getSource().sendFeedback(Text.literal("Bedrock Shaders: regenerated the runtime shader pack"));
                    return 1;
                }))
                .then(literal("debug").executes(ctx -> {
                    BedrockPipeline pipeline = BedrockPipeline.get();
                    FrameContext frame = FrameContext.get();
                    var assets = BedrockAssetLibrary.get();
                    ctx.getSource().sendFeedback(Text.literal(
                            "Bedrock pipeline ready=" + pipeline.isReady()
                                    + " assets=" + assets.parsedFiles() + " files / " + assets.loadErrors() + " errors"
                                    + " fog=" + frame.fogId()
                                    + " sun=" + String.format("%.2f", frame.sunIntensity())
                                    + " moon=" + String.format("%.2f", frame.moonIntensity())
                                    + " time=" + String.format("%.2f", frame.sunAngle())
                                    + " biome=" + frame.biomeId()
                                    + " pack=" + (frame.activePackName().isEmpty() ? "builtin" : frame.activePackName())
                                    + " family=" + frame.shaderFamily().id
                                    + " compat=" + RenderCompat.get().mode()
                                    + " (" + RenderCompat.get().reason() + ")"));
                    return 1;
                }))
                .then(literal("fog").executes(ctx -> {
                    FrameContext frame = FrameContext.get();
                    var fog = frame.fogSettings();
                    ctx.getSource().sendFeedback(Text.literal(
                            "Bedrock fog id=" + frame.fogId() + " file=" + fog.identifier
                                    + " air=[" + fog.airStart + ", " + fog.airEnd + "]"
                                    + " water=[" + fog.waterStart + ", " + fog.waterEnd + "]"
                                    + " volumetric=" + fog.volumetricAirMaxDensity));
                    return 1;
                }))
                .then(literal("import").executes(ctx -> {
                    var registry = dev.bedrockshaders.client.pack.PackRegistry.get();
                    registry.ensureFolders();
                    var results = registry.importPending();
                    dev.bedrockshaders.client.pack.ImportedPackActivator.invalidate();
                    dev.bedrockshaders.client.pack.ImportedPackActivator.reload(MinecraftClient.getInstance());
                    if (results.isEmpty()) {
                        ctx.getSource().sendFeedback(Text.translatable("bedrock_shaders.command.import.none",
                                registry.importFolder().toString()));
                    } else {
                        int ok = 0;
                        for (var result : results) {
                            if (result.success()) {
                                ok++;
                                ctx.getSource().sendFeedback(Text.literal("  + " + result.message()));
                            } else {
                                ctx.getSource().sendFeedback(Text.literal("  ! " + result.message()));
                            }
                        }
                        ctx.getSource().sendFeedback(Text.translatable("bedrock_shaders.command.import.done",
                                ok, results.size() - ok));
                    }
                    return results.size();
                }))
                .then(literal("packs").executes(ctx -> {
                    MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance()
                            .setScreen(new dev.bedrockshaders.client.gui.BedrockPacksScreen(null)));
                    return 1;
                }))
                .then(literal("effects").executes(ctx -> {
                    var assets = BedrockAssetLibrary.get();
                    var ids = assets.fogIdentifiers();
                    ctx.getSource().sendFeedback(Text.literal(
                            "Loaded " + ids.size() + " Bedrock effects: " + String.join(", ",
                                    ids.subList(0, Math.min(ids.size(), 12)))
                                    + (ids.size() > 12 ? "..." : "")));
                    return 1;
                }));
        dispatcher.register(root);
    }
}

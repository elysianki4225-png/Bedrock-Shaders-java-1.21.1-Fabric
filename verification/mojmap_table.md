# Yarn -> Mojang official mappings (1.21.1)

Authoritative source: NeoForge `1.21.1` branch patches and Iris `1.21.1` branch sources
(both are Mojang-mapped). Used to port the client source set to Forge/NeoForge, which
cannot use Yarn.

## Classes

| Yarn (Fabric) | Official (Forge/NeoForge) |
| --- | --- |
| `net.minecraft.client.MinecraftClient` | `net.minecraft.client.Minecraft` |
| `net.minecraft.client.gui.DrawContext` | `net.minecraft.client.gui.GuiGraphics` |
| `net.minecraft.client.gui.screen.Screen` | `net.minecraft.client.gui.screens.Screen` |
| `net.minecraft.client.gui.widget.ButtonWidget` | `net.minecraft.client.gui.components.Button` |
| `net.minecraft.client.gui.widget.SliderWidget` | `net.minecraft.client.gui.components.AbstractSliderButton` |
| `net.minecraft.client.gui.widget.CyclingButtonWidget` | `net.minecraft.client.gui.components.CycleButton` |
| `net.minecraft.client.gui.widget.TextFieldWidget` | `net.minecraft.client.gui.components.EditBox` |
| `net.minecraft.text.Text` | `net.minecraft.network.chat.Component` |
| `net.minecraft.util.Identifier` | `net.minecraft.resources.ResourceLocation` |
| `net.minecraft.resource.ResourceManager` | `net.minecraft.server.packs.resources.ResourceManager` |
| `net.minecraft.resource.Resource` | `net.minecraft.server.packs.resources.Resource` |
| `net.minecraft.resource.ResourceType` | `net.minecraft.server.packs.PackType` |
| `net.minecraft.resource.ResourcePackManager` | `net.minecraft.server.packs.repository.PackRepository` |
| `net.minecraft.resource.ResourcePackProfile` | `net.minecraft.server.packs.repository.Pack` |
| `net.minecraft.client.gl.Framebuffer` | `com.mojang.blaze3d.pipeline.RenderTarget` |
| `net.minecraft.client.gl.ShaderProgram` | `net.minecraft.client.renderer.ShaderInstance` |
| `net.minecraft.client.render.RenderTickCounter` | `net.minecraft.client.DeltaTracker` |
| `net.minecraft.client.render.Camera` | `net.minecraft.client.Camera` |
| `net.minecraft.client.render.GameRenderer` | `net.minecraft.client.renderer.GameRenderer` |
| `net.minecraft.client.render.WorldRenderer` | `net.minecraft.client.renderer.LevelRenderer` |
| `net.minecraft.client.render.LightmapTextureManager` | `net.minecraft.client.renderer.LightTexture` |
| `net.minecraft.client.render.BufferBuilderStorage` | `net.minecraft.client.renderer.RenderBuffers` |
| `net.minecraft.client.util.math.MatrixStack` | `com.mojang.blaze3d.vertex.PoseStack` |
| `net.minecraft.client.util.InputUtil` | `com.mojang.blaze3d.platform.InputConstants` |
| `net.minecraft.client.option.KeyBinding` | `net.minecraft.client.KeyMapping` |
| `net.minecraft.client.option.GameOptions` | `net.minecraft.client.Options` |
| `net.minecraft.client.world.ClientWorld` | `net.minecraft.client.multiplayer.ClientLevel` |
| `net.minecraft.world.World` | `net.minecraft.world.level.Level` |
| `net.minecraft.world.biome.Biome` | `net.minecraft.world.level.biome.Biome` |
| `net.minecraft.util.math.BlockPos` | `net.minecraft.core.BlockPos` |
| `net.minecraft.util.math.Vec3d` | `net.minecraft.world.phys.Vec3` |
| `net.minecraft.util.math.RotationAxis` | `com.mojang.math.Axis` |
| `net.minecraft.registry.entry.RegistryEntry` | `net.minecraft.core.Holder` |
| `net.minecraft.block.enums.CameraSubmersionType` | `net.minecraft.world.level.material.FogType` |
| `net.minecraft.util.Util` | `net.minecraft.Util` |

## Members (verified against NeoForge 1.21.1 patches / Iris 1.21.1)

| Yarn member | Official member |
| --- | --- |
| `MinecraftClient.getInstance()` | `Minecraft.getInstance()` |
| `MinecraftClient.getFramebuffer()` | `Minecraft.getMainRenderTarget()` |
| `Framebuffer.fbo` / `textureWidth` / `textureHeight` | `RenderTarget.frameBufferId` / `.width` / `.height` |
| `MinecraftClient.getResourceManager()` | `Minecraft.getResourceManager()` |
| `MinecraftClient.getResourcePackManager()` | `Minecraft.getResourcePackRepository()` |
| `MinecraftClient.reloadResources()` | `Minecraft.reloadResourcePacks()` |
| `MinecraftClient.setScreen(s)` | `Minecraft.setScreen(s)` |
| `GameOptions.resourcePacks` | `Options.resourcePacks` |
| `GameOptions.write()` | `Options.save()` |
| `GameOptions.refreshResourcePacks(mgr)` | `Options.updateResourcePacks(repo)` |
| `Text.translatable(k, args)` | `Component.translatable(k, args)` |
| `Text.literal(s)` | `Component.literal(s)` |
| `Identifier.of(ns, path)` | `ResourceLocation.fromNamespaceAndPath(ns, path)` |
| `ShaderProgram.getName()` | `ShaderInstance.getName()` |
| `ShaderProgram.getGlRef()` | `ShaderInstance.getId()` |
| `ShaderProgram.bind()` | `ShaderInstance.apply()` |
| `World.getRegistryKey() == World.NETHER` | `Level.dimension() == Level.NETHER` |
| `World.getSkyAngle(f)` | `Level.getTimeOfDay(f)` |
| `World.getTimeOfDay()` | `Level.getDayTime()` |
| `World.getRainGradient(f)` | `Level.getRainLevel(f)` |
| `World.getThunderGradient(f)` | `Level.getThunderLevel(f)` |
| `World.getBiome(pos)` | `Level.getBiome(pos)` |
| `WorldRenderer.renderSky(...)` | `LevelRenderer.renderSky(...)` (same 6 params) |
| `WorldRenderer.render(...)` | `LevelRenderer.renderLevel(...)` |
| `GameRenderer.renderWorld(RenderTickCounter)` | `GameRenderer.renderLevel(DeltaTracker)` |
| `Camera.getSubmersionType()` | `Camera.getFluidInCamera()` |
| `Camera.getPos()` | `Camera.getPosition()` |
| `Camera.getYaw()/getPitch()` | `Camera.getYRot()/getXRot()` |
| `Screen.renderInGameBackground(DrawContext)` | `Screen.renderBackground(GuiGraphics, int, int, float)` |
| `ButtonWidget.builder(...).dimensions(...).build()` | `Button.builder(...).bounds(...).build()` |
| `KeyBinding.wasPressed()` | `KeyMapping.consumeClick()` |
| `PlayerEntity.sendMessage(Text, boolean)` | `Player.displayClientMessage(Component, boolean)` |
| `GameRenderer.getCamera()` | `GameRenderer.getMainCamera()` |
| `MinecraftClient.gameRenderer` | `Minecraft.gameRenderer` |
| `MinecraftClient.options` | `Minecraft.options` |
| `MinecraftClient.world` | `Minecraft.level` |
| `MinecraftClient.player` | `Minecraft.player` |
| `MinecraftClient.currentScreen` | `Minecraft.screen` |
| `MinecraftClient.getWindow()` | `Minecraft.getWindow()` |
| `Window.getFramebufferWidth()/Height()` | `Window.getWidth()/getHeight()` |

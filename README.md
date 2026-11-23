# Bedrock Shaders — Minecraft Java Edition 1.21.1 (Fabric · Quilt · Forge · NeoForge)

Brings the look of Minecraft **Bedrock Edition's Deferred Rendering Pipeline** to
**Java Edition 1.21.1** on all four major loaders: the data-driven Bedrock
lighting/atmosphere/fog/colour-grading system, PBR-style water, Bedrock sky scattering,
and a post-processing chain (SSAO, bloom, god rays, tone mapping).

| Loader | Jar | Requires |
| --- | --- | --- |
| Fabric | [`dist/bedrock-shaders-1.0.0.jar`](dist/bedrock-shaders-1.0.0.jar) | Fabric Loader ≥ 0.16, Fabric API ≥ 0.100 |
| Quilt | [`dist/bedrock-shaders-quilt-1.0.0.jar`](dist/bedrock-shaders-quilt-1.0.0.jar) | Quilt Loader ≥ 0.30, Fabric API ≥ 0.100 |
| Forge | [`dist/bedrock-shaders-forge-1.0.0.jar`](dist/bedrock-shaders-forge-1.0.0.jar) | Forge 52.0.28 (1.21.1) |
| NeoForge | [`dist/bedrock-shaders-neoforge-1.0.0.jar`](dist/bedrock-shaders-neoforge-1.0.0.jar) | NeoForge 21.1.119 (1.21.1) |

**Fabric and Quilt are the maintained, optimised targets.** The Forge and NeoForge ports
compile and are updated alongside them, but they are built non-blocking in CI while their
packaging is finished, so a loader-only failure does not mark the branch red.

## Install

1. Install your loader for Minecraft 1.21.1 (Fabric/Quilt also need Fabric API: Quilt
   Loader loads it natively and there is no Quilted Fabric API release for 1.21.1).
2. Drop the matching jar from `dist/` into `.minecraft/mods/`.
3. Launch. The mod generates its runtime shader pack into `config/bedrock-shaders/pack`
   and enables it automatically.

Languages: English and 简体中文 (`assets/bedrock_shaders/lang/{en_us,zh_cn}.json`, key-identical).

| Key | Action |
| --- | --- |
| `F9` | Open the Bedrock Shaders settings screen |
| `F10` | Toggle the pipeline on/off |

Commands: `/bedrock menu`, `/bedrock toggle`, `/bedrock preset <potato|low|balanced|high|ultra>`,
`/bedrock packs`, `/bedrock import`, `/bedrock reload`, `/bedrock fog`, `/bedrock effects`,
`/bedrock debug`.

## Installing Bedrock shader packs (.mcpack)

Like Iris, the mod creates an instance folder on first launch:

```
.minecraft/bedrock-shaders/packs/      <- drop .mcpack / .mcaddon / .zip here
```

Then either restart, run `/bedrock import`, or press **Rescan folder** in the pack manager
(`F9` → **Packs** → *Bedrock Pack Manager*). For each archive the mod:

1. unpacks nested archives (a `.mcpack` inside a `.mcaddon`, or an `.mcpack` inside an
   `.mcpack`, is followed up to 4 levels deep),
2. keeps only Bedrock rendering data — `*_fog_setting.json`, `*_lighting.json`,
   `*_atmospherics.json`, `*_color_grading.json` and `biomes_client.json` — and drops
   textures, models, sounds and manifests,
3. writes a valid Java resource pack to
   `.minecraft/resourcepacks/bedrock_shaders_<name>/` with a generated `pack.mcmeta`,
4. enables it in the resource-pack stack (**appended last**, so any pack you placed above
   still wins) and reloads resources — no restart needed.

Per-pack state is tracked in `bedrock-shaders/index.json`, so unchanged archives are not
re-imported and deleted packs are unregistered. The manager screen can toggle, delete and
open the folder, and it links to vanilla's own resource-pack screen.

**What is not supported:** binary RenderDragon shaders (`*.material.bin`) — those are
compiled pipeline binaries with no Java Edition equivalent. Only the data-driven Deferred
Rendering JSON is imported.

## One renderer, four loaders

The renderer itself (`BedrockPipeline`, `FrameContext`, `BedrockAssetLibrary`, the
`.mcpack` importer, the GUI and the command) is plain Java with no Minecraft import.
Each platform supplies the same three small pieces:

- a `ClientSnapshot` (camera, biome, weather, water surface) — Yarn names on Fabric/Quilt,
  official names on Forge/NeoForge;
- a `ResourceSource` (`open` + `list`) over that loader's resource manager;
- three mixins on the same three vanilla methods (`GameRenderer#renderLevel`,
  `LevelRenderer#renderSky`, `ShaderInstance#apply` / `ShaderProgram#bind`).

Quilt reuses the Fabric code byte-for-byte: Quilt Loader loads Fabric mods natively, so
the Quilt artifact only swaps `fabric.mod.json` for `quilt.mod.json`.

## How it works

Minecraft 1.21.1 draws chunks through the core programs `rendertype_solid`,
`rendertype_cutout`, `rendertype_cutout_mipped`, `rendertype_translucent` and
`rendertype_entity_solid`. The mod does **not** patch vanilla classes' rendering code;
instead it:

1. **Generates a real resource pack** (`BedrockShaderPack`) that overrides those five
   programs with Bedrock-style GLSL files plus their JSON definitions. Because it is a
   normal resource pack, the pack stack, reload logic and user overrides all keep working.
2. **Feeds Bedrock uniforms** (`BedrockUniforms`, `ShaderProgramMixin`) — sun/moon
   direction and intensity, sky zenith/horizon, fog colour and density, water colour and
   wave offsets — straight into vanilla's parsed `ShaderProgram` right after it is bound.
3. **Resolves Bedrock assets at runtime** (`BedrockAssetLibrary`): the 126 Bedrock JSON
   documents under `assets/bedrock_shaders/bedrock/` (lighting, atmospherics, fogs,
   colour grading) are parsed into curves and bound to vanilla biome/fog identifiers, so
   each biome gets its Bedrock fog, sky and grade.
4. **Runs a post chain** (`BedrockPipeline`) with SSAO, bloom, god rays and Bedrock
   colour grading using its own framebuffers and GLSL 150 programs.

### Shader sources

`shadersrc/` is the source of truth. `verification/sync_shaders.mjs` flattens the
`#moj_import` includes and writes the packaged copies to
`src/main/resources/assets/bedrock_shaders/bedrockshader/`; Gradle runs it before
`processResources`, so the jar cannot ship un-synced GLSL.

## Verification (no GPU, no Minecraft install needed)

```bash
cd verification && npm install
node validate_shaders.mjs        # compiles every core + post-processing program with Khronos glslang
python3 ../verification/validate_pack.py     # generated pack obeys 1.21.1 core-shader rules
python3 ../verification/validate_assets.py   # every Bedrock JSON parses and is in range
python3 ../verification/test_importer.py     # .mcpack layout rules, nested archives, lang parity
```

The pack validator exists because a bad property in a core-shader JSON aborts the whole
resource reload instead of failing gracefully; it checks `vertex`/`fragment` naming,
uniform types (`matrix4x4`/`float`/`int` only), `count`/`values` consistency and that the
samplers a program declares are exactly those the vanilla render layer binds.

## Layout

```
shadersrc/core|include      Bedrock GLSL (authored, #version 150)
src/main/java               common: config model, Bedrock asset model (curves, lighting, fog, water)
src/client/java/.../pack    .mcpack importer, pack registry, resource-pack activation
src/client/java             Fabric client: pipeline, uniforms, Yarn mixins, GUI, command
src/main/resources/assets    generated GLSL + Bedrock JSON samples + lang + icon
src/quilt/resources         quilt.mod.json (the Quilt jar is the Fabric jar re-metadata'd)
loaders/common-official     Mojang-mapped client code shared by Forge and NeoForge
loaders/forge|neoforge      independent Gradle builds + their entrypoints and metadata
verification/               offline shader/asset/pack validators, CI entry points
dist/                       jars published by CI
```

## Licensing note

`assets/bedrock_shaders/bedrock/**` contains sample JSON documents from Minecraft: Bedrock
Edition (Mojang/bedrock-samples) and remains © Mojang AB under the Minecraft EULA — see
`LICENSE`. Everything else is MIT.

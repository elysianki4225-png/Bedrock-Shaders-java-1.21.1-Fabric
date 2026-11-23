# Bedrock Shaders — Minecraft Java Edition 1.21.1 (Fabric · Quilt · Forge · NeoForge)

**English** · [简体中文](README.zh_cn.md)

Brings the look of Minecraft **Bedrock Edition's Deferred Rendering Pipeline** to
**Java Edition 1.21.1** on all four major loaders: the data-driven Bedrock
lighting/atmosphere/fog/colour-grading system, PBR-style water, Bedrock sky scattering,
and a post-processing chain (SSAO, bloom, god rays, tone mapping).

## Download

Each loader has its own jar, and every file name states which loader it is for.

| Loader | Download | Requires | Status |
| --- | --- | --- | --- |
| **Fabric** | [**bedrock-shaders-fabric-1.0.0.jar**](dist/bedrock-shaders-fabric-1.0.0.jar) | Fabric Loader ≥ 0.16 + Fabric API ≥ 0.100 | **Primary target** |
| **Quilt** | [**the same Fabric jar**](dist/bedrock-shaders-fabric-1.0.0.jar) — no separate file | Quilt Loader ≥ 0.30 + Fabric API ≥ 0.100 | **Primary target** |
| Forge | [bedrock-shaders-forge-1.0.0.jar](dist/bedrock-shaders-forge-1.0.0.jar) | Forge 52.0.28 (1.21.1) | Maintained, secondary |
| NeoForge | [bedrock-shaders-neoforge-1.0.0.jar](dist/bedrock-shaders-neoforge-1.0.0.jar) | NeoForge 21.1.119 (1.21.1) | Maintained, secondary |

Sources jar: [bedrock-shaders-fabric-1.0.0-sources.jar](dist/bedrock-shaders-fabric-1.0.0-sources.jar)

**There is deliberately no `-quilt` jar.** Quilt Loader 0.30+ loads Fabric mods natively,
so the Fabric artifact *is* the Quilt download. A second jar that only swapped metadata
meant two things to test and two places for the version to drift — and for a while it
shipped a `quilt.mod.json` with a literal `${version}` that Quilt Loader refuses outright.
One artifact, one thing to get right.

**Fabric (and therefore Quilt) is the maintained, optimised target.** The Forge and
NeoForge ports compile and are updated alongside it, but they are built non-blocking in CI
while their packaging is finished, so a loader-only failure does not mark the branch red.

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

## Living next to other mods

The renderer is invasive by nature: it replaces five vanilla core shader programs and runs
its own post chain into the main framebuffer. Some very popular mods want the same ground,
so the mod picks a mode at launch instead of finding out later:

| Installed | Mode | What happens |
| --- | --- | --- |
| Nothing relevant | **Full** | Everything runs |
| **Sodium / Embeddium / Rubidium** | **Full** | They replace the chunk render backend only, so nothing changes for us |
| **Lithium, FerriteCore, ModernFix, ImmediatelyFast, EntityCulling, C2ME, Krypton, Starlight, MemoryLeakFix, BadOptimizations, MoreCulling** | **Full** | They never touch the GL pipeline: no conflict, no special handling |
| **Iris / Oculus** | **Yield** | Iris owns the frame and the same five programs. The mod keeps the `.mcpack` importer, the generated resource packs and the settings screen, but binds no uniforms, replaces no sky and runs no post chain — no double tone mapping, no fighting over the framebuffer |
| **VulkanMod** | **Disabled (no GL)** | There is no OpenGL at all, so any GL call would crash. The mod still loads, the GUI still opens, imports still work — nothing visual runs |

This is decided once at startup from the installed mod ids and reported in the log, so
`latest.log` always says which mode is active and why. `/bedrock debug` shows it too.
`verification/test_compat.py` pins the whole matrix — including that a content mod like
Create, or an unrelated mod whose id merely starts with `iris` or `vulkan`, never triggers
a downgrade.

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

### Directional tuning for well-known Bedrock packs

Bedrock shader packs are **data only** — they ship fog/lighting/atmosphere/colour-grading
JSON and no code. So a pack cannot look like its Bedrock original on Java unless the
renderer reproduces the look its author intended. When an imported pack is recognised as
belonging to a known family, the renderer applies that family's tuning on top of the pack's
data:

| Family | Recognised names | What is tuned |
| --- | --- | --- |
| **Newb** | Newb X Legacy, Golden Legacy, Glow Legacy, X LMI, Renewed, Reforged, Nostalgia, Dawn, Ale, Dragon Complementary | Haze reaches further, denser and colder underwater fog, warmer (golden) sunlight, slightly lifted saturation and gentler contrast, bloom and caustics nudged up — the signature Newb look |
| YSS | YSS, YSS Special Edition | Crisper water reflections, slightly stronger sun |
| Eternity | Eternity | Cinematic: heaver bloom, stronger reflections and god rays, more contrast |
| Solas / Harmonics | Solas, Harmonics, Harmony | Cleaner, blue-hour leaning, lighter haze |
| Generic | everything else | The mod's Bedrock-faithful defaults, untouched |

Detection reads the archive name, the pack's manifest name and its description, so
`Newb-X-Legacy-v1.2.mcpack` and a nameless pack whose manifest says "Newb X LMI" both land
on the Newb family. The recognised family is shown as a badge in the pack manager, reported
by `/bedrock debug`, and can be switched off entirely under `Packs → Shader family tuning`.

Per-pack state is tracked in `bedrock-shaders/index.json`, so unchanged archives are not
re-imported and deleted packs are unregistered. The manager screen can toggle, delete and
open the folder, and it links to vanilla's own resource-pack screen.

**What is not supported:** binary RenderDragon shaders (`*.material.bin`) — those are
compiled pipeline binaries with no Java Edition equivalent. Only the data-driven Deferred
Rendering JSON is imported.

## One renderer, three artifacts

The renderer itself (`BedrockPipeline`, `FrameContext`, `BedrockAssetLibrary`, the
`.mcpack` importer, the GUI and the command) is plain Java with no Minecraft import.
Each platform supplies the same three small pieces:

- a `ClientSnapshot` (camera, biome, weather, water surface) — Yarn names on Fabric/Quilt,
  official names on Forge/NeoForge;
- a `ResourceSource` (`open` + `list`) over that loader's resource manager;
- three mixins on the same three vanilla methods (`GameRenderer#renderLevel`,
  `LevelRenderer#renderSky`, `ShaderInstance#apply` / `ShaderProgram#bind`).

Quilt needs no port at all: Quilt Loader 0.30 loads Fabric mods natively (Fabric API
included, since there is no Quilted Fabric API release for 1.21.1), so the Fabric jar runs
there unchanged.

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

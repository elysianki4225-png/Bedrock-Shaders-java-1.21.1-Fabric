# Bedrock Shaders — Minecraft Java Edition 1.21.1 (Fabric)

Brings the look of Minecraft **Bedrock Edition's Deferred Rendering Pipeline** to
**Java Edition 1.21.1** on Fabric: the data-driven Bedrock lighting/atmosphere/fog/
colour-grading system, PBR-style water, Bedrock sky scattering, and a post-processing
chain (SSAO, bloom, god rays, tone mapping).

Built jar: [`dist/bedrock-shaders-1.0.0.jar`](dist/bedrock-shaders-1.0.0.jar) (rebuilt by CI on every push).

## Install

1. Install **Fabric Loader ≥ 0.16** for Minecraft 1.21.1 and **Fabric API ≥ 0.100**.
2. Drop `dist/bedrock-shaders-1.0.0.jar` into `.minecraft/mods/`.
3. Launch. The mod generates its runtime shader pack into `config/bedrock-shaders/pack`
   and enables it automatically.

| Key | Action |
| --- | --- |
| `F9` | Open the Bedrock Shaders settings screen |
| `F10` | Toggle the pipeline on/off |

Commands: `/bedrock menu`, `/bedrock toggle`, `/bedrock preset <potato|low|balanced|high|ultra>`,
`/bedrock reload`, `/bedrock fog`, `/bedrock effects`, `/bedrock debug`.

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
```

The pack validator exists because a bad property in a core-shader JSON aborts the whole
resource reload instead of failing gracefully; it checks `vertex`/`fragment` naming,
uniform types (`matrix4x4`/`float`/`int` only), `count`/`values` consistency and that the
samplers a program declares are exactly those the vanilla render layer binds.

## Layout

```
shadersrc/core|include      Bedrock GLSL (authored, #version 150)
src/main/java               common: config model, Bedrock asset model (curves, lighting, fog)
src/client/java             client: pipeline, uniforms, mixins, GUI, command, asset library
src/main/resources/assets    generated GLSL + Bedrock JSON samples + lang + icon
verification/               offline shader/asset/pack validators, CI entry points
dist/                       jar published by CI
```

## Licensing note

`assets/bedrock_shaders/bedrock/**` contains sample JSON documents from Minecraft: Bedrock
Edition (Mojang/bedrock-samples) and remains © Mojang AB under the Minecraft EULA — see
`LICENSE`. Everything else is MIT.

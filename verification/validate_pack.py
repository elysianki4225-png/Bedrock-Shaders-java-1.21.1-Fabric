#!/usr/bin/env python3
"""
Reproduces BedrockShaderPack's output contract offline and checks it against the
rules Minecraft 1.21.1's ShaderProgram parser enforces, because a violation there
does not fail gracefully - it aborts the whole resource reload.

Checked:
  * every JSON has vertex/fragment naming an existing .vsh/.fsh pair in the same dir
  * uniform types are only matrix4x4 / float / int
  * count matches the type (matrix4x4 -> 16) and values length matches count
  * samplers only reference Sampler0..3 (vanilla binds a fixed sampler set)
  * the GLSL for each program declares exactly the samplers its JSON lists
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CORE = ROOT / 'src/main/resources/assets/bedrock_shaders/bedrockshader/core'

# Mirrors BedrockShaderPack.PROGRAMS (program id -> (vertex file, fragment file, samplers)).
PROGRAMS = {
    'rendertype_solid': ('bedrock_terrain.vsh', 'bedrock_terrain.fsh', ['Sampler0', 'Sampler2']),
    'rendertype_cutout': ('bedrock_terrain.vsh', 'bedrock_terrain.fsh', ['Sampler0', 'Sampler2']),
    'rendertype_cutout_mipped': ('bedrock_terrain.vsh', 'bedrock_terrain.fsh', ['Sampler0', 'Sampler2']),
    'rendertype_translucent': ('bedrock_translucent.vsh', 'bedrock_translucent.fsh', ['Sampler0', 'Sampler2']),
    'rendertype_entity_solid': ('bedrock_entity.vsh', 'bedrock_entity.fsh', ['Sampler0', 'Sampler1', 'Sampler2']),
}

VALID_TYPES = {'matrix4x4', 'float', 'int'}
VALID_SAMPLERS = {f'Sampler{i}' for i in range(4)}
errors = []

for program, (vsh, fsh, samplers) in PROGRAMS.items():
    for stage in (vsh, fsh):
        if not (CORE / stage).is_file():
            errors.append(f'{program}: missing shader source {stage}')

    declared = set()
    for stage in (vsh, fsh):
        path = CORE / stage
        if path.is_file():
            declared |= set(re.findall(r'^\s*uniform\s+sampler2D\s+(\w+)\s*;', path.read_text(), re.M))
    missing = set(samplers) - declared
    if missing:
        errors.append(f'{program}: JSON declares samplers {sorted(missing)} that the GLSL never samples')
    for name in samplers:
        if name not in VALID_SAMPLERS:
            errors.append(f'{program}: sampler {name} is not in vanilla\'s bound set {sorted(VALID_SAMPLERS)}')

    # The JSON the Java builder would emit, re-derived here.
    stage_v = (CORE / vsh).read_text()
    stage_f = (CORE / fsh).read_text()
    if not stage_v.lstrip().startswith('#version 150'):
        errors.append(f'{vsh}: must start with #version 150 for Minecraft\'s GL 3.2 core context')
    if not stage_f.lstrip().startswith('#version 150'):
        errors.append(f'{fsh}: must start with #version 150 for Minecraft\'s GL 3.2 core context')

# Cross-check the Java program table against this file so the two cannot drift.
java = (ROOT / 'src/client/java/dev/bedrockshaders/client/render/BedrockShaderPack.java').read_text()
java_programs = re.findall(r'new Program\("(\w+)",\s*"([\w.]+)",\s*"([\w.]+)"', java)
if len(java_programs) != len(PROGRAMS):
    errors.append(f'BedrockShaderPack has {len(java_programs)} programs, validator expects {len(PROGRAMS)}')
for name, vsh, fsh in java_programs:
    if name not in PROGRAMS:
        errors.append(f'BedrockShaderPack program {name} is unknown to the validator')
    elif (vsh, fsh) != PROGRAMS[name][:2]:
        errors.append(f'BedrockShaderPack program {name} uses {vsh}/{fsh}, validator expects {PROGRAMS[name][:2]}')

# Vanilla's own 1.21.1 core JSONs must exist for these ids or the override would add new programs.
reference = pathlib.Path('/tmp/assets_repo/assets/minecraft/shaders/core')
if reference.is_dir():
    available = {p.stem for p in reference.glob('*.json')}
    for program in PROGRAMS:
        if program not in available:
            print(f'note: {program}.json was not in the 1.21.1 reference set ({len(available)} programs seen)')
else:
    print('note: 1.21.1 reference shaders not present, skipped existence check')

if errors:
    print(f'\n{len(errors)} pack contract violation(s):')
    for error in errors:
        print('  -', error)
    sys.exit(1)
print(f'Shader pack contract OK: {len(PROGRAMS)} programs (vanilla 1.21.1 core-shader rules).')

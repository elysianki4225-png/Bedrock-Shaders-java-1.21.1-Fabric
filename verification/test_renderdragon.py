#!/usr/bin/env python3
"""Offline test for RenderDragon detection.

RenderDragonSupport.detect() decides three things that are easy to get wrong and expensive
to get wrong in a shipped build:

1. whether an archive is RenderDragon at all (otherwise ordinary packs get refused);
2. how many compiled materials it has (this is what the user's warning quotes);
3. which of its files are importable data and which are unreadable binaries.

The Java cannot be instantiated without Minecraft, so this mirrors the two rules that
matter - the .material.bin extension and the renderer/ data directories - and pins them
against realistic entry lists, including the layouts real Bedrock packs use.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = ROOT / 'src/client/java/dev/bedrockshaders/client/pack/RenderDragonSupport.java'
failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


text = JAVA.read_text()

# ---- the constants the implementation actually uses ----
suffix_match = re.search(r'BINARY_SUFFIX = "([^"]+)"', text)
BINARY_SUFFIX = suffix_match.group(1) if suffix_match else ''
check(BINARY_SUFFIX == '.material.bin', f'unexpected BINARY_SUFFIX: {BINARY_SUFFIX!r}')

hints_match = re.search(r'DATA_HINTS = List\.of\((.*?)\);', text, re.S)
DATA_HINTS = re.findall(r'"([^"]+)"', hints_match.group(1)) if hints_match else []
check(len(DATA_HINTS) >= 5, 'DATA_HINTS should cover the RenderDragon data directories')

materials_match = re.search(r'MATERIALS_DIR = "([^"]+)"', text)
MATERIALS_DIR = materials_match.group(1) if materials_match else ''
check(MATERIALS_DIR == 'renderer/materials/', f'unexpected MATERIALS_DIR: {MATERIALS_DIR!r}')


def normalise(path):
    value = path.replace('\\', '/')
    while value.startswith('./'):
        value = value[2:]
    while value.startswith('/'):
        value = value[1:]
    return value


def detect(entries):
    """Mirror of RenderDragonSupport.detect: returns (renderDragon, binaries, dataFiles)."""
    binaries = data = 0
    saw_materials = False
    for raw in entries:
        lower = normalise(raw).lower()
        if not lower:
            continue
        if lower.endswith(BINARY_SUFFIX):
            binaries += 1
        elif lower.endswith('.material') or lower.startswith(MATERIALS_DIR):
            saw_materials = True
        elif lower.endswith('.json') and any(lower.startswith(p) for p in DATA_HINTS):
            data += 1
    return (binaries > 0 or data > 0 or saw_materials), binaries, data


def target_for(path):
    """Mirror of RenderDragonSupport.targetFor."""
    lower = normalise(path).lower()
    if not lower.endswith('.json'):
        return None
    name = lower[lower.rindex('/') + 1:]
    for prefix, folder in (('renderer/fogs/', 'fogs'), ('renderer/lighting/', 'lighting'),
                           ('renderer/atmospherics/', 'atmospherics'),
                           ('renderer/color_grading/', 'color_grading'),
                           ('renderer/shadows/', 'shadows'), ('renderer/sky/', 'sky'),
                           ('renderer/water/', 'water')):
        if lower.startswith(prefix):
            return f'{folder}/{name}'
    return None


# ---- realistic archives ----
CASES = [
    # (entries, renderDragon?, binaries, data, why)
    ([], False, 0, 0, 'empty archive'),
    (['manifest.json', 'pack_icon.png'], False, 0, 0, 'a resource pack with no shaders'),
    (['fogs/overworld_fog_setting.json', 'lighting/global.json'], False, 0, 0,
     'classic deferred data is NOT RenderDragon'),
    (['renderer/materials/terrain.material.bin'], True, 1, 0, 'binary-only shader pack'),
    (['renderer/materials/terrain.material', 'renderer/materials/terrain.material.bin'],
     True, 1, 0, 'material definition plus its binary'),
    (['renderer/lighting/global.json', 'renderer/shadows/shadows.json',
      'renderer/sky/sky.json', 'renderer/water/water.json'],
     True, 0, 4, 'data-only RenderDragon pack'),
    (['renderer/materials/terrain.material.bin', 'renderer/lighting/global.json',
      'renderer/sky/sky.json'],
     True, 1, 2, 'the common case: both halves present'),
    (['RENDERER/MATERIALS/TERRAIN.MATERIAL.BIN'], True, 1, 0, 'uppercase paths from Windows tools'),
    (['./renderer/materials/water.material.bin'], True, 1, 0, 'leading ./ in the zip entry'),
    # detectRenderDragon() recurses into nested archives, so an entry inside a nested
    # .mcpack is seen by its inner path. That is why a layered .mcaddon still detects.
    (['renderer/materials/rock.material.bin'], True, 1, 0,
     'entry from inside a nested archive, seen by its inner path'),
    (['renderer/materials/readme.txt'], True, 0, 0,
     'anything under renderer/materials marks the pack as RenderDragon'),
    (['renderer/particles/particles.json'], False, 0, 0,
     'renderer/ has non-data directories too; they must not trip detection'),
]

for entries, expected_rd, expected_bin, expected_data, why in CASES:
    rd, binaries, data = detect(entries)
    check(rd == expected_rd, f'{why}: expected renderDragon={expected_rd}, got {rd}')
    check(binaries == expected_bin, f'{why}: expected {expected_bin} binaries, got {binaries}')
    check(data == expected_data, f'{why}: expected {expected_data} data files, got {data}')

# ---- data mapping ----
MAPPING = [
    ('renderer/lighting/global.json', 'lighting/global.json'),
    ('renderer/shadows/shadows.json', 'shadows/shadows.json'),
    ('renderer/sky/sky.json', 'sky/sky.json'),
    ('renderer/water/water.json', 'water/water.json'),
    ('renderer/fogs/nether_fog_setting.json', 'fogs/nether_fog_setting.json'),
    ('renderer/materials/terrain.material.bin', None),
    ('renderer/materials/terrain.material', None),
    ('renderer/particles/particles.json', None),
    ('textures/blocks/stone.png', None),
]
for source, expected in MAPPING:
    actual = target_for(source)
    check(actual == expected, f'{source}: expected target {expected!r}, got {actual!r}')

# ---- the compiled pipeline must never be copied into a Java pack ----
for path in ('renderer/materials/x.material.bin', 'renderer/materials/x.material',
             'renderer/materials/anything'):
    check(path.lower().endswith(BINARY_SUFFIX)
          or path.lower().endswith('.material')
          or path.lower().startswith(MATERIALS_DIR),
          f'{path} would not be recognised as compiled pipeline')

# ---- the switch is off by default, and documented as impossible to fully honour ----
config = (ROOT / 'src/main/java/dev/bedrockshaders/config/BedrockConfig.java').read_text()
check('public boolean experimentalRenderDragon = false;' in config,
      'experimentalRenderDragon must default to false')
# The user-facing text must not promise the compiled shaders run.
lang = (ROOT / 'src/main/resources/assets/bedrock_shaders/lang/en_us.json').read_text()
check('bedrock_shaders.warn.renderdragon.body' in lang, 'warning body key missing')
body = re.search(r'"bedrock_shaders\.warn\.renderdragon\.body":\s*"(.*?)"', lang, re.S)
check(body is not None and 'cannot run' in body.group(1).lower(),
      'the warning must state that the compiled pipeline cannot run')

if failures:
    print('\n'.join(failures), file=sys.stderr)
    print(f'\n{len(failures)} RenderDragon check(s) failed', file=sys.stderr)
    sys.exit(1)

print(f'RenderDragon detection OK: {len(CASES)} archive shapes, {len(MAPPING)} path mappings, '
      f'binary suffix {BINARY_SUFFIX}.')

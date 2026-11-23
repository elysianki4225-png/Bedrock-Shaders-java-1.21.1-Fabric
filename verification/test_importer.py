#!/usr/bin/env python3
"""
Offline test for the .mcpack importer's classification rules.

The Java importer cannot run without Minecraft, so this mirrors its layout rules
(BedrockPackLayout.targetFor / isIgnored / isWaterTable) in Python and asserts they agree
with a table of real-world pack shapes, then builds fixture archives and checks the
expected set of files is selected. It also verifies the shipped language files are key-
identical so no translation can silently go missing.
"""
import json
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA_LAYOUT = ROOT / 'src/client/java/dev/bedrockshaders/client/pack/BedrockPackLayout.java'
failures = []


def target_for(entry: str):
    lower = entry.lower()
    if not lower.endswith('.json'):
        return None
    name = lower.rsplit('/', 1)[-1]
    parent = lower.rsplit('/', 1)[0] if '/' in lower else ''
    if name.endswith('_fog_setting.json') or name == 'fog_settings.json':
        return 'fogs/' + name
    if name.endswith('_color_grading.json') or name in ('color_grading.json', 'color_grading_settings.json'):
        return 'color_grading/' + name
    if name.endswith('_lighting.json') or name in ('lighting.json', 'lighting_settings.json') \
            or (name == 'global.json' and parent.endswith('lighting')):
        return 'lighting/' + name
    if name.endswith('_atmospherics.json') or name in ('atmospherics.json', 'atmosphere_settings.json'):
        return 'atmospherics/' + name
    return None


def is_water(entry: str) -> bool:
    name = entry.lower().rsplit('/', 1)[-1]
    return name in ('biomes_client.json', 'biomes.json')


def is_ignored(entry: str) -> bool:
    lower = entry.lower()
    if lower.endswith('/'):
        return True
    if any(part in lower for part in ('/textures/', '/models/', '/sounds/', '/font/', '/particles/', '/ui/')):
        return True
    if lower.startswith(('textures/', 'models/', 'sounds/', 'font/', 'ui/')):
        return True
    if lower.endswith(('.png', '.tga', '.ogg', '.fsb', '.material', '.bin', '.lang')):
        return True
    if lower.endswith(('contents.json', 'splashes.json', 'signatures.json', 'world_icon.jpeg')):
        return True
    return lower.startswith('__macosx/')


CASES = [
    # (entry path, expected target or None)
    ('fogs/plains_fog_setting.json', 'fogs/plains_fog_setting.json'),
    ('Fogs/My_Biome_Fog_Setting.JSON', 'fogs/my_biome_fog_setting.json'),
    ('deferred/fogs/hell_fog_setting.json', 'fogs/hell_fog_setting.json'),
    ('lighting/global.json', 'lighting/global.json'),
    ('x/lighting/desert_lighting.json', 'lighting/desert_lighting.json'),
    ('atmospherics/end_atmospherics.json', 'atmospherics/end_atmospherics.json'),
    ('color_grading/color_grading.json', 'color_grading/color_grading.json'),
    ('random/something_lighting.json', 'lighting/something_lighting.json'),
    ('manifest.json', None),
    ('textures/blocks/stone.png', None),
    ('pack_icon.png', None),
    ('sounds/step.ogg', None),
    ('entity/bat.entity.json', None),
    ('fogs/notes.txt', None),
]

for entry, expected in CASES:
    actual = target_for(entry)
    if actual != expected:
        failures.append(f'targetFor({entry}) = {actual}, expected {expected}')

IGNORED = ['textures/blocks/stone.png', 'pack_icon.png', 'sounds/step.ogg', 'ui/hud.png',
           '__MACOSX/._manifest.json', 'contents.json', 'splashes.json']
NOT_IGNORED = ['fogs/plains_fog_setting.json', 'lighting/global.json', 'biomes_client.json']
for entry in IGNORED:
    if not is_ignored(entry):
        failures.append(f'{entry} should be ignored')
for entry in NOT_IGNORED:
    if is_ignored(entry):
        failures.append(f'{entry} must not be ignored')

# The Java source must still contain the same predicates (drift guard).
java = JAVA_LAYOUT.read_text()
for needle in ('_fog_setting.json', '_color_grading.json', '_lighting.json', '_atmospherics.json',
               'biomes_client.json', 'pack_icon.png', '__macosx/'):
    if needle not in java:
        failures.append(f'BedrockPackLayout.java no longer handles {needle}')

# ---- fixture archives: flat pack, nested .mcpack, and combined addon ----
import tempfile
work = pathlib.Path(tempfile.mkdtemp())

def write_zip(path, entries):
    with zipfile.ZipFile(path, 'w') as archive:
        for name, data in entries.items():
            archive.writestr(name, data)

manifest = json.dumps({
    "format_version": 2,
    "header": {"name": "Test Bedrock Shaders", "uuid": "12345678-1234-1234-1234-123456789abc",
               "version": [1, 0, 0], "description": "unit test"},
    "modules": [{"type": "resources", "uuid": "aaaaaaaa-1234-1234-1234-123456789abc",
                 "version": [1, 0, 0]}],
})
fog = json.dumps({"format_version": "1.21.90", "minecraft:fog_settings": {
    "description": {"identifier": "minecraft:fog_plains"},
    "distance": {"air": {"fog_start": 0.92, "fog_end": 1.0, "fog_color": "#ABD2FF"}}}})
water = json.dumps({"biomes": {"plains": {"water_surface_color": "#44AFF5",
                                          "water_fog_color": "#44AFF5",
                                          "water_surface_transparency": 0.65},
                               "default": {"water_surface_color": "#44AFF5"}}})

flat = work / 'flat.mcpack'
write_zip(flat, {'manifest.json': manifest, 'fogs/plains_fog_setting.json': fog,
                 'biomes_client.json': water, 'pack_icon.png': b'\x89PNG', 'textures/a.png': b'x'})

inner = work / 'inner.mcpack'
write_zip(inner, {'manifest.json': manifest, 'lighting/global.json': '{}',
                  'atmospherics/atmospherics.json': '{}'})
nested = work / 'nested.mcpack'
with zipfile.ZipFile(nested, 'w') as archive:
    archive.writestr('manifest.json', manifest)
    archive.write(inner, 'nested/inner.mcpack')

addon = work / 'combo.mcaddon'
with zipfile.ZipFile(addon, 'w') as archive:
    archive.writestr('behaviour/manifest.json', manifest)
    archive.writestr('behaviour/entities/x.json', '{}')
    archive.write(flat, 'resources/flat.mcpack')


def select(path, depth=0):
    chosen, nested_found = [], False
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if name.endswith('/'):
                continue
            if name.lower().endswith(('.mcpack', '.zip', '.mcaddon')) and depth < 4:
                inner_path = work / f'nested_{depth}_{name.replace("/", "_")}'
                inner_path.write_bytes(archive.read(name))
                sub, sub_nested = select(inner_path, depth + 1)
                chosen.extend(sub)
                nested_found |= sub_nested or bool(sub)
                continue
            if is_ignored(name):
                continue
            target = target_for(name)
            if target:
                chosen.append(target)
            elif is_water(name):
                chosen.append('water/biomes_client.json')
    return chosen, nested_found


selected, _ = select(flat)
expected_flat = {'fogs/plains_fog_setting.json', 'water/biomes_client.json'}
if set(selected) != expected_flat:
    failures.append(f'flat.mcpack selected {sorted(selected)}, expected {sorted(expected_flat)}')

selected_nested, found_nested = select(nested)
expected_nested = {'lighting/global.json', 'atmospherics/atmospherics.json'}
if set(selected_nested) != expected_nested:
    failures.append(f'nested.mcpack selected {sorted(selected_nested)}, expected {sorted(expected_nested)}')

selected_addon, _ = select(addon)
if set(selected_addon) != expected_flat:
    failures.append(f'combo.mcaddon selected {sorted(selected_addon)}, expected {sorted(expected_flat)}')

# Behaviour-pack-only archive must yield nothing (so the importer rejects it).
behaviour = work / 'behaviour_only.mcpack'
write_zip(behaviour, {'manifest.json': manifest, 'entities/bat.json': '{}'})
selected_behaviour, _ = select(behaviour)
if selected_behaviour:
    failures.append(f'behaviour-only pack selected {selected_behaviour}, expected none')

# ---- language files must stay key-identical ----
lang_dir = ROOT / 'src/main/resources/assets/bedrock_shaders/lang'
langs = {p.stem: json.loads(p.read_text(encoding='utf-8')) for p in lang_dir.glob('*.json')}
base = langs.get('en_us', {})
for code, table in langs.items():
    missing = set(base) - set(table)
    extra = set(table) - set(base)
    if missing:
        failures.append(f'{code}.json missing keys: {sorted(missing)[:5]}')
    if extra:
        failures.append(f'{code}.json has unknown keys: {sorted(extra)[:5]}')
print(f'localisation: {", ".join(f"{k}={len(v)}" for k, v in sorted(langs.items()))}')

# ---- every translation key a source file names must exist, in both languages ----
# This is the check that would have caught the RenderDragon and Fabric-hint keys: they were
# added with the wrong prefix and nothing noticed, because a missing key only shows up as a
# raw "bedrock_shaders.option.renderdragon" string in the GUI at runtime.
import re

referenced = set()
for source in ROOT.rglob('*.java'):
    if '.git' in source.parts:
        continue
    for key in re.findall(r'"(bedrock_shaders\.[a-zA-Z0-9_.]+)"', source.read_text(encoding='utf-8')):
        # Skip fragments used to build another key ("bedrock_shaders.preset." + id).
        if key.endswith('.'):
            continue
        referenced.add(key)

for code, table in langs.items():
    missing = sorted(referenced - set(table))
    if missing:
        failures.append(f'{code}.json missing {len(missing)} referenced key(s): {missing[:6]}')
print(f'translation keys referenced from Java: {len(referenced)}, all resolved')

if failures:
    print(f'\n{len(failures)} importer test failure(s):')
    for failure in failures:
        print('  -', failure)
    sys.exit(1)
print('Importer layout rules, fixture archives and localisation all OK.')

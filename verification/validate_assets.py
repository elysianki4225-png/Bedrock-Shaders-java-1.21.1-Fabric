#!/usr/bin/env python3
"""
Validates every Mojang Bedrock sample file that ships with the mod:
  * strict JSON parse (the shipped files must stay byte-valid JSON),
  * schema sanity: the expected top level settings object and description identifier,
  * value ranges (colours 0-255, fog starts < ends, non-negative densities),
  * that every fog file used by the Java binding table exists.
Exits non-zero on any problem so CI catches broken assets.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'src/main/resources/assets/bedrock_shaders/bedrock'

EXPECTED = {
    'lighting': 'minecraft:lighting_settings',
    'atmospherics': 'minecraft:atmosphere_settings',
    'fogs': 'minecraft:fog_settings',
    'color_grading': 'minecraft:color_grading_settings',
}

errors = []
counts = {}

for folder, root_key in EXPECTED.items():
    directory = ASSETS / folder
    files = sorted(directory.glob('*.json'))
    counts[folder] = len(files)
    for path in files:
        try:
            data = json.loads(path.read_text(encoding='utf-8'))
        except json.JSONDecodeError as exc:
            errors.append(f'{path.relative_to(ROOT)}: invalid JSON: {exc}')
            continue
        if root_key not in data:
            errors.append(f'{path.relative_to(ROOT)}: missing "{root_key}"')
            continue
        settings = data[root_key]
        desc = settings.get('description', {})
        if not str(desc.get('identifier', '')).startswith('minecraft:'):
            errors.append(f'{path.relative_to(ROOT)}: description.identifier must be namespaced')

        if folder == 'fogs':
            distance = settings.get('distance', {})
            for kind, entry in distance.items():
                start = entry.get('fog_start')
                end = entry.get('fog_end')
                if start is None or end is None:
                    continue
                if isinstance(start, (int, float)) and isinstance(end, (int, float)) and start > end:
                    errors.append(f'{path.relative_to(ROOT)}: {kind} fog_start {start} > fog_end {end}')
                color = entry.get('fog_color')
                if isinstance(color, str):
                    if not re.fullmatch(r'#[0-9A-Fa-f]{6}|#[0-9A-Fa-f]{8}', color):
                        errors.append(f'{path.relative_to(ROOT)}: {kind} fog_color "{color}" is not #RRGGBB')
            volumetric = settings.get('volumetric', {})
            density = volumetric.get('density', {})
            for media, entry in density.items():
                value = entry.get('max_density')
                if value is not None and value < 0:
                    errors.append(f'{path.relative_to(ROOT)}: negative volumetric density for {media}')

        if folder == 'atmospherics':
            for key in ('sky_zenith_color', 'sky_horizon_color'):
                for keyframe, rgb in (settings.get(key) or {}).items():
                    if not (isinstance(rgb, list) and len(rgb) == 3 and all(0 <= c <= 255 for c in rgb)):
                        errors.append(f'{path.relative_to(ROOT)}: {key}[{keyframe}] is not an RGB triple')

        if folder == 'color_grading':
            grading = settings.get('color_grading', {})
            mid = grading.get('midtones', {})
            for key in ('gain', 'gamma'):
                values = mid.get(key)
                if values and any(v <= 0 for v in values):
                    errors.append(f'{path.relative_to(ROOT)}: midtones.{key} must be > 0')

# Every fog file referenced by the Java binding table must exist.
java = (ROOT / 'src/client/java/dev/bedrockshaders/client/assets/AssetNames.java').read_text()
for match in re.finditer(r'"(fogs/[a-z0-9_]+\.json)"', java):
    path = ASSETS / match.group(1)
    if not path.is_file():
        errors.append(f'AssetNames.java references missing file {match.group(1)}')

print('Bedrock assets checked: ' + ', '.join(f'{k}={v}' for k, v in counts.items()))
if errors:
    print(f'\n{len(errors)} problem(s):')
    for err in errors:
        print('  -', err)
    sys.exit(1)
print('All Bedrock assets valid.')

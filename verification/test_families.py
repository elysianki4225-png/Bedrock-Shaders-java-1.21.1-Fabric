#!/usr/bin/env python3
"""
Offline test for shader-family detection.

ShaderFamily.detect() runs in the game, but its rule is simple and worth pinning down:
it reads the archive name, the pack's manifest name and its description, and maps them to
a recognised family. This mirrors that table in Python and asserts the expected family for
real pack names from the Bedrock community, plus the fallback behaviour.

It also checks that every family id has a translation in both language files, so the pack
list can never show a raw key.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = ROOT / 'src/main/java/dev/bedrockshaders/config/ShaderFamily.java'
failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


def java_aliases():
    """Parses the alias table out of ShaderFamily.java so the test cannot drift from it."""
    text = JAVA.read_text()
    aliases = {}
    for family in ['NEWB', 'YSS', 'ETERNITY', 'SOLAS', 'GENERIC']:
        match = re.search(r'case ' + family + r' -> new String\[\] \{(.*?)\}', text, re.S)
        if not match:
            aliases[family] = []
            continue
        aliases[family] = re.findall(r'"([^"]+)"', match.group(1))
    return aliases


ALIASES = java_aliases()
# detect() checks these families first; GENERIC is the fallback.
ORDER = ['ETERNITY', 'SOLAS', 'YSS', 'NEWB']


def detect(*hints):
    text = ' '.join(h.lower() for h in hints if h).strip()
    if not text:
        return 'GENERIC'
    for family in ORDER:
        for alias in ALIASES[family]:
            if alias in text:
                return family
    return 'GENERIC'


# --- the alias table itself
for family, aliases in ALIASES.items():
    if family != 'GENERIC':
        check(aliases, f'{family} has no aliases, detection can never match it')

# --- real-world pack names from the Bedrock community
CASES = [
    ('Newb-X-Legacy-v1.2.mcpack', '', '', 'NEWB'),
    ('Newb X Golden Legacy.mcpack', '', '', 'NEWB'),
    ('newb-x-lmi.mcpack', 'Newb X LMI', '', 'NEWB'),
    ('NewbGlowLegacy.mcpack', '', '', 'NEWB'),
    ('Newb_X_Renewed_v3.mcpack', '', '', 'NEWB'),
    ('newb-x-dawn.mcpack', '', '', 'NEWB'),
    ('', 'Newb X Ale Renewed', '', 'NEWB'),
    ('', 'Newb X Dragon Complementary', '', 'NEWB'),
    ('', 'X Legacy', 'Soft natural lighting and auroras', 'NEWB'),
    ('YSS-RD.mcpack', '', '', 'YSS'),
    ('', 'YSS Special Edition', '', 'YSS'),
    ('Eternity-Shader.mcpack', '', '', 'ETERNITY'),
    ('solas-shaders.mcpack', '', '', 'SOLAS'),
    ('', 'Harmonics', '', 'SOLAS'),
    ('SomeRandomPack.mcpack', 'Cool Fog', 'my first pack', 'GENERIC'),
    ('', '', '', 'GENERIC'),
    # detection is case-insensitive and reads all three hints together
    ('', 'pack', 'inspired by NEWB X LEGACY', 'NEWB'),
]
for file_name, name, description, expected in CASES:
    got = detect(file_name, name, description)
    check(got == expected,
          f'detect({file_name!r}, {name!r}, {description!r}) = {got}, expected {expected}')

# --- every family id reachable from detection must be translated
en = json.loads((ROOT / 'src/main/resources/assets/bedrock_shaders/lang/en_us.json').read_text())
zh = json.loads((ROOT / 'src/main/resources/assets/bedrock_shaders/lang/zh_cn.json').read_text())
for family in ['GENERIC'] + ORDER:
    key = 'bedrock_shaders.family.' + family.lower()
    check(key in en, f'en_us.json is missing {key}')
    check(key in zh, f'zh_cn.json is missing {key}')

# --- the tuning switch must be translated too (it appears on the Packs page)
key = 'bedrock_shaders.option.family_tuning'
check(key in en and key in zh, f'{key} must exist in both languages')

if failures:
    print('Shader family detection FAILED:')
    for failure in failures:
        print('  -', failure)
    sys.exit(1)

print(f'Shader family detection OK: {len(CASES)} pack names, '
      f'{len(ORDER)} families + generic, all translated.')

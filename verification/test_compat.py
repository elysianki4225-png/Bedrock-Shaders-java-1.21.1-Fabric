#!/usr/bin/env python3
"""Offline test for the mod-compatibility matrix.

RenderCompat decides, from the ids of installed mods, how much of the renderer may run.
That decision is pure logic and is the only thing standing between "works next to Sodium"
and "black screen next to Iris" or "hard crash under VulkanMod", so it is pinned here.

The Java classes cannot be instantiated without Minecraft, so this re-implements the
classification from the lists in RenderCompat.java (parsed out of the source, so the test
cannot drift from the implementation) and asserts the expected mode for realistic mod sets.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = ROOT / 'src/main/java/dev/bedrockshaders/client/render/RenderCompat.java'
failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


def java_list(name):
    """Parses `private static final List<String> NAME = List.of("a", "b", ...);`."""
    text = JAVA.read_text()
    match = re.search(
        r'List<String>\s+' + name + r'\s*=\s*List\.of\((.*?)\);', text, re.S)
    if not match:
        failures.append(f'{name} not found in RenderCompat.java')
        return []
    return re.findall(r'"([^"]+)"', match.group(1))


FRAME_OWNERS = java_list('FRAME_OWNERS')
NO_GL = java_list('NO_GL_BACKENDS')
COEXISTING = java_list('KNOWN_COEXISTING')


def matcher(installed):
    """Mirrors RenderCompat.matcher: equal id, or the id continued with - or _."""
    lowered = [i.lower() for i in installed]

    def is_loaded(candidate):
        want = candidate.lower()
        return any(i == want or i.startswith(want + '-') or i.startswith(want + '_')
                   for i in lowered)

    return is_loaded


def mode_for(installed):
    """Mirrors RenderCompat.detect: no-GL wins over a frame owner, which wins over full."""
    is_loaded = matcher(installed)
    for candidate in NO_GL:
        if is_loaded(candidate):
            return 'DISABLED_NO_GL'
    for candidate in FRAME_OWNERS:
        if is_loaded(candidate):
            return 'YIELD_TO_IRIS'
    return 'FULL'


def detect_all(installed):
    is_loaded = matcher(installed)
    found = [c for c in NO_GL + FRAME_OWNERS + COEXISTING if is_loaded(c)]
    return found


CASES = [
    # (installed mods, expected mode, why)
    ([], 'FULL', 'vanilla install: everything runs'),
    (['fabricloader', 'minecraft', 'bedrock_shaders'], 'FULL', 'the mod by itself'),
    (['sodium'], 'FULL', 'Sodium replaces chunk rendering only, not our programs'),
    (['sodium', 'lithium', 'ferritecore'], 'FULL', 'the standard optimisation trio'),
    (['sodium', 'iris'], 'YIELD_TO_IRIS', 'Iris owns the frame; we step aside'),
    (['iris'], 'YIELD_TO_IRIS', 'Iris without Sodium still owns the frame'),
    (['oculus'], 'YIELD_TO_IRIS', 'Oculus is the Forge Iris'),
    (['iris-mc1.21.1'], 'YIELD_TO_IRIS', 'a versioned Iris fork still counts'),
    (['embeddium'], 'FULL', 'Embeddium is a Sodium fork for Forge: chunk rendering only'),
    (['rubidium'], 'FULL', 'Rubidium is the older Embeddium name'),
    (['vulkanmod'], 'DISABLED_NO_GL', 'no OpenGL: any GL call would crash'),
    (['vulkanmod-fabric'], 'DISABLED_NO_GL', 'a VulkanMod fork is still VulkanMod'),
    (['sodium', 'vulkanmod'], 'DISABLED_NO_GL', 'no GL beats everything else'),
    (['iris', 'vulkanmod'], 'DISABLED_NO_GL', 'no GL wins over Iris'),
    (['indium', 'continuity'], 'FULL', 'unrelated render tweaks'),
    (['modernfix', 'immediatelyfast', 'entityculling', 'c2me', 'krypton', 'starlight'],
     'FULL', 'optimisation mods that never touch the core shader programs'),
    (['memoryleakfix', 'badoptimizations', 'moreculling'], 'FULL', 'more of the same class'),
    (['create'], 'FULL', 'a content mod must never change our mode'),
    (['irisx'], 'FULL', 'prefix-only similarity must not trigger a downgrade'),
    (['vulkan_extra'], 'FULL', 'an unrelated mod starting with "vulkan" must not match'),
]

for installed, expected, why in CASES:
    actual = mode_for(installed)
    check(actual == expected,
          f'{installed}: expected {expected}, got {actual} ({why})')

# The decision must survive the ids loaders actually report, not just the pretty names.
for candidate in FRAME_OWNERS + NO_GL:
    check(candidate == candidate.lower(),
          f'{candidate}: ids are matched case-insensitively, keep the list lowercase')

check('vulkanmod' in NO_GL, 'VulkanMod must be in NO_GL_BACKENDS')
check('iris' in FRAME_OWNERS, 'Iris must be in FRAME_OWNERS')
check(not (set(FRAME_OWNERS) & set(NO_GL)), 'no mod may be both a frame owner and a no-GL backend')
check(not (set(COEXISTING) & (set(FRAME_OWNERS) | set(NO_GL))),
      'a coexisting mod must not also be a frame owner or no-GL backend')

# Detection is reported, not silent: the debug line must see what was found.
check(detect_all(['sodium', 'lithium', 'iris']) == ['iris', 'sodium', 'lithium'],
      'detect_all should report the frame owner first, then coexisting mods')

# Every mode has to keep the importer usable -- the whole point of yielding rather than
# refusing to load.
java_text = JAVA.read_text()
check('allowsPackImport' in java_text and 'return true;' in java_text.split('allowsPackImport')[1][:80],
      'allowsPackImport must return true: imports keep working in every mode')

if failures:
    print('\n'.join(failures), file=sys.stderr)
    print(f'\n{len(failures)} compatibility check(s) failed', file=sys.stderr)
    sys.exit(1)

print(f'Compatibility matrix OK: {len(CASES)} install combinations, '
      f'{len(FRAME_OWNERS)} frame owner(s), {len(NO_GL)} no-GL backend(s), '
      f'{len(COEXISTING)} coexisting mods.')

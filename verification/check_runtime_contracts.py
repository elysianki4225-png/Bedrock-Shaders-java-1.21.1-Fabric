#!/usr/bin/env python3
"""Catch the runtime crashes that static shape checks cannot see.

Two real crashes shipped past every other script:

1. `ShaderSources.readRaw` built `core/x.vsh` but never applied the `bedrockshader/`
   root, so every lookup missed the file that WAS in the jar. The mod logged
   "Missing Bedrock shader source" and silently failed to generate its runtime pack.

2. `GlObjects.beginFragmentPass` called `glMatrixMode`. Minecraft 1.21.1 runs an
   OpenGL 3.2 core profile, where that function does not exist; LWJGL does not throw,
   it aborts the JVM with "No context is current".

Both are contract violations between what the code asks for and what the engine
provides, and both are checkable offline. That is what this script does.
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SHADER_ROOT = ROOT / "src/main/resources/assets/bedrock_shaders"
JAVA_ROOTS = [
    ROOT / "src/main/java",
    ROOT / "src/client/java",
    ROOT / "loaders/common-official/src/main/java",
]

# Removed from the GL 3.2 core profile that 1.21.1 requires. LWJGL turns each of these
# into a native crash, not a Java exception, so they must never appear in shipped code.
FIXED_FUNCTION = [
    "glMatrixMode", "glLoadIdentity", "glLoadMatrix", "glMultMatrix",
    "glOrtho", "glFrustum", "glPushMatrix", "glPopMatrix",
    "glBegin", "glEnd", "glVertex2f", "glVertex3f", "glTexCoord2f",
    "glColor3f", "glColor4f", "glRasterPos", "glEnableClientState",
    "glDisableClientState", "glVertexPointer", "glTexCoordPointer",
    "glNormalPointer", "glColorPointer", "glIndexPointer",
    "glCallList", "glNewList", "glGenLists", "glShadeModel",
    "glPolygonMode", "glDrawBuffer", "glReadBuffer",
]

problems: list[str] = []


def fail(message: str) -> None:
    problems.append(message)


def java_files():
    for root in JAVA_ROOTS:
        if root.is_dir():
            yield from sorted(root.rglob("*.java"))


def strip_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def check_no_fixed_function() -> None:
    """A core-profile call must not exist, even behind a capability flag."""
    for path in java_files():
        text = strip_comments(path.read_text(encoding="utf-8"))
        rel = path.relative_to(ROOT).as_posix()
        for needle in FIXED_FUNCTION:
            # `glEnd` is a prefix of nothing else in LWJGL we use, but keyword-anchor it
            # so glEndQuery/glEndTransformFeedback are not false positives.
            pattern = rf"\b{needle}\s*\("
            for match in re.finditer(pattern, text):
                line = text.count("\n", 0, match.start()) + 1
                fail(f"{rel}:{line}: {needle}() is not in the OpenGL 3.2 core profile "
                     f"-> native crash, not an exception")


def check_shader_paths() -> None:
    """Every asset path the code builds must exist where the jar actually ships it."""
    sources = ROOT / "src/client/java/dev/bedrockshaders/client/render/ShaderSources.java"
    official = ROOT / "loaders/common-official/src/main/java/dev/bedrockshaders/client/render/ShaderSources.java"
    for path in (sources, official):
        if not path.is_file():
            fail(f"missing {path.relative_to(ROOT).as_posix()}")
            continue
        text = strip_comments(path.read_text(encoding="utf-8"))
        rel = path.relative_to(ROOT).as_posix()

        root_match = re.search(r'ROOT\s*=\s*"([^"]+)"', text)
        if not root_match:
            fail(f"{rel}: ROOT is missing, so asset paths cannot be verified")
            continue
        root = root_match.group(1)

        # readRaw must route through path(), otherwise ROOT is dead code and every
        # lookup misses. This is exactly the bug that shipped.
        read_raw = re.search(r"private static String readRaw\([^)]*\)\s*\{(.*?)\n    \}", text, re.S)
        if not read_raw:
            fail(f"{rel}: readRaw not found")
        elif "path(" not in read_raw.group(1):
            fail(f"{rel}: readRaw does not apply the {root!r} root — lookups will target "
                 f"the namespace root instead of the shipped directory")

        on_disk = SHADER_ROOT / root
        if not on_disk.is_dir():
            fail(f"{rel}: {root!r} does not exist under assets/bedrock_shaders/")
            continue

        # Every core shader the code loads must be present.
        for stage in re.findall(r'"(core/[\w.]+)"', text):
            if not (on_disk / stage).is_file():
                fail(f"{rel}: asks for {root}{stage} but it is not in the jar")
        for stage in re.findall(r'"(bedrock_\w+\.(?:vsh|fsh))"', text):
            if not (on_disk / "core" / stage).is_file():
                fail(f"{rel}: asks for core/{stage} but it is not in the jar")
        if not any((on_disk / "core").glob("*.vsh")):
            fail(f"{rel}: no core shader sources under {on_disk.relative_to(ROOT)}")
        if not any((on_disk / "include").glob("*.glsl")):
            fail(f"{rel}: no include sources under {on_disk.relative_to(ROOT)}")


def check_import_targets() -> None:
    """`#moj_import <x>` must resolve to a file the code can read as include/x."""
    core = SHADER_ROOT / "bedrockshader" / "core"
    include = SHADER_ROOT / "bedrockshader" / "include"
    if not core.is_dir() or not include.is_dir():
        return
    available = {p.name for p in include.glob("*.glsl")}
    for shader in sorted(core.glob("*")):
        for target in re.findall(r"#moj_import\s+[<\"]([^>\"]+)[>\"]",
                                 shader.read_text(encoding="utf-8")):
            if target not in available:
                fail(f"{shader.name}: imports {target!r}, which is not in include/")


def check_core_profile_requested() -> None:
    """A 3.2 core profile is what makes the fixed-function ban real; keep it pinned."""
    found = False
    for path in java_files():
        text = path.read_text(encoding="utf-8")
        if "GLFW_OPENGL_CORE_PROFILE" in text or "GLFW_OPENGL_PROFILE" in text:
            found = True
            break
    # Fabric handles this through Minecraft itself, so absence is not a failure; the check
    # only asserts that if the code does configure a profile, it does not ask for compat.
    for path in java_files():
        text = strip_comments(path.read_text(encoding="utf-8"))
        if "GLFW_OPENGL_COMPAT_PROFILE" in text:
            fail(f"{path.relative_to(ROOT).as_posix()}: requests a compatibility profile; "
                 f"1.21.1 renderers assume core")


def main() -> int:
    check_no_fixed_function()
    check_shader_paths()
    check_import_targets()
    check_core_profile_requested()

    print(f"runtime contracts: {len(FIXED_FUNCTION)} fixed-function names banned, "
          f"{len(problems)} problem(s)")
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print("Runtime contract OK: core-profile clean, every shader path resolves to a shipped file.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

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


def check_shadersrc_in_sync() -> None:
    """`shadersrc/` is the source of truth; the packaged copies are generated from it.

    Gradle runs `sync_shaders.mjs` before `processResources`, so editing
    `assets/bedrock_shaders/bedrockshader/` directly looks like it works locally and is
    then silently reverted at build time. That is exactly the trap this catches.
    """
    src_root = ROOT / "shadersrc"
    out_root = SHADER_ROOT / "bedrockshader"
    if not src_root.is_dir() or not out_root.is_dir():
        fail("shadersrc/ or the packaged shader tree is missing")
        return

    for name in ("bedrock_terrain.fsh", "bedrock_entity.fsh", "bedrock_translucent.fsh"):
        source = src_root / "core" / name
        packaged = out_root / "core" / name
        if not source.is_file() or not packaged.is_file():
            fail(f"missing core shader {name}")
            continue
        # The packaged file is the source plus flattened imports; the body must match.
        src_body = strip_comments(source.read_text(encoding="utf-8"))
        out_body = strip_comments(packaged.read_text(encoding="utf-8"))
        src_body = re.sub(r"^#include\s.*$", "", src_body, flags=re.M)
        out_body = re.sub(r"^#moj_import\s.*$", "", out_body, flags=re.M)
        strip = lambda t: [l.strip() for l in t.splitlines() if l.strip()]
        if strip(src_body) != strip(out_body):
            fail(f"{name}: packaged copy differs from shadersrc - run "
                 f"`node verification/sync_shaders.mjs` (Gradle would revert your edit)")

    # Nothing may sample the scene/depth targets: the uniforms are declared nowhere the
    # mod binds, so a sampler left behind reads the block texture on unit 0.
    for include in out_root.rglob("*.glsl"):
        text = strip_comments(include.read_text(encoding="utf-8"))
        for sampler in ("BedrockSceneSampler", "BedrockDepthSampler"):
            if sampler in text:
                fail(f"{include.name}: references {sampler}, which nothing ever binds")


def check_single_transfer_function() -> None:
    """Exactly one place may encode to display space, or the image double-gammas."""
    core = SHADER_ROOT / "bedrockshader" / "core"
    encoded = []
    for shader in sorted(core.glob("*.fsh")):
        text = strip_comments(shader.read_text(encoding="utf-8"))
        # Nested parentheses (pow(vec3(...), ...)) defeat [^)]*, so match on the exponent
        # literal plus the pow that wraps it instead.
        if re.search(r"pow\s*\(.*?1\.0\s*/\s*2\.2", text):
            encoded.append(shader.name)
    if encoded:
        fail(f"{', '.join(encoded)}: encodes to display space; the composite pass owns the "
             f"transfer curve, so doing it here double-gammas the whole image")

    post = ROOT / "src/client/java/dev/bedrockshaders/client/render/effects/PostShaders.java"
    composite = strip_comments(post.read_text(encoding="utf-8"))
    if "1.0 / 2.2" not in composite:
        fail("the composite pass no longer encodes to display space; the image will be "
             "rendered linear and look crushed")


def main() -> int:
    check_no_fixed_function()
    check_shader_paths()
    check_import_targets()
    check_core_profile_requested()
    check_shadersrc_in_sync()
    check_single_transfer_function()

    print(f"runtime contracts: {len(FIXED_FUNCTION)} fixed-function names banned, "
          f"{len(problems)} problem(s)")
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print("Runtime contract OK: core-profile clean, every shader path resolves to a shipped file.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

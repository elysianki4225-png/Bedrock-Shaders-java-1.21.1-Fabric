#!/usr/bin/env python3
"""Guard the Forge/NeoForge source set against Fabric-only imports.

`loaders/common-official` is compiled against official (Mojang) mappings together with
`src/main/java`, and it must stay loader-agnostic: the Fabric client tree resolves paths
through FabricLoader, but the shared tree resolves them through GamePaths. Copying a
Fabric file into the shared tree compiles on Fabric and fails on Forge/NeoForge with
"package net.fabricmc.loader.api does not exist" — which is exactly how the 1.21.1 Forge
port broke. This check fails the build instead of letting CI discover it 90 seconds later.
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SHARED = ROOT / "loaders" / "common-official" / "src" / "main" / "java"
# RenderCompat is shared config-style code: both the Fabric client tree and the official
# source set compile it, so it must stay free of loader and Minecraft imports too.
SHARED_TOO = [
    ROOT / "src" / "main" / "java" / "dev" / "bedrockshaders" / "client" / "render" / "RenderCompat.java",
]

FABRIC = re.compile(r"\bnet\.fabricmc\b|\bFabricLoader\b|\bFabricClient\b")
# Yarn class names that differ from the official (Mojang) names the shared tree compiles
# against. Each entry is a Yarn name that must be spelled the official way here.
# RenderCompat is called from both platform entrypoints and from FrameContext, so it may
# not depend on a Minecraft class either.
MINECRAFT = re.compile(r"\bnet\.minecraft\b")

YARN_ONLY = re.compile(
    r"\bCyclingButton\b"          # official: CycleButton
    r"|\bMinecraftClient\b"       # official: Minecraft
    r"|\.initially\("              # CycleButton.Builder: official is withInitialValue
    r"|\bText\b(?=\s*[.(])"       # official: Component
    r"|\bnet\.minecraft\.client\.util\.InputUtil\b"
)


def main() -> int:
    if not SHARED.is_dir():
        print(f"missing shared source set: {SHARED}", file=sys.stderr)
        return 1

    problems: list[str] = []
    files = sorted(SHARED.rglob("*.java")) + [p for p in SHARED_TOO if p.is_file()]
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        text = path.read_text(encoding="utf-8")
        # Ignore comments entirely: the javadoc deliberately names the Yarn spelling to
        # explain the translation, and that must not count as a use.
        text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        for number, line in enumerate(text.splitlines(), 1):
            code = line.split("//", 1)[0]
            if line.lstrip().startswith("*"):
                continue
            patterns = [(FABRIC, "Fabric API"), (YARN_ONLY, "Yarn-only class")]
            # Loader-neutral by contract (see SHARED_TOO): no Minecraft class at all.
            if path in SHARED_TOO:
                patterns.append((MINECRAFT, "Minecraft class in shared code"))
            for pattern, label in patterns:
                if pattern.search(code):
                    problems.append(f"{rel}:{number}: {label} in the shared source set -> {line.strip()}")

    print(f"checked {len(files)} shared source files")
    if problems:
        print("\n".join(problems), file=sys.stderr)
        print(
            "\nThe Forge/NeoForge builds compile loaders/common-official against official "
            "mappings. Use GamePaths for instance paths and the official-mapped API names.",
            file=sys.stderr,
        )
        return 1

    print("Loader source set is clean: no Fabric/Yarn-only references in common-official.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

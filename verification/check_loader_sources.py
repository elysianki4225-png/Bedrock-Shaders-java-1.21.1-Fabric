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

FABRIC = re.compile(r"\bnet\.fabricmc\b|\bFabricLoader\b|\bFabricClient\b")
# Yarn-mapped Minecraft packages must not leak either: the shared tree compiles against
# official names (net.minecraft.client.Minecraft#getInstance is fine, ...#getWindow is not,
# but any net.minecraft import at all is a smell worth banning outright).
YARN_ONLY = re.compile(r"\bnet\.minecraft\.client\.util\.InputUtil\b|\bnet\.minecraft\.text\.Text\b")


def main() -> int:
    if not SHARED.is_dir():
        print(f"missing shared source set: {SHARED}", file=sys.stderr)
        return 1

    problems: list[str] = []
    files = sorted(SHARED.rglob("*.java"))
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            code = line.split("//", 1)[0]
            for pattern, label in ((FABRIC, "Fabric API"), (YARN_ONLY, "Yarn-only class")):
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

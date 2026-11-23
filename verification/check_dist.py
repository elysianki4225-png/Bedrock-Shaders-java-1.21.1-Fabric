#!/usr/bin/env python3
"""Check that dist/ holds one loadable jar per loader, and that each one says so.

The repository publishes four jars, all renamed with their loader, and the README links
each one separately. Two failure modes have already happened here and both are silent:

* a jar built by the wrong project (a 20 MB toolchain merged jar) landed in dist/;
* the metadata inside a jar named -quilt was actually Fabric's, or a jar contained a
  second loader's metadata by accident.

This check reads the zip directory of each artifact and asserts the metadata file the file
name promises, so a mislabelled download cannot ship.
"""
from __future__ import annotations

import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
DIST = ROOT / "dist"

# loader -> the metadata entry that makes the loader accept the jar
METADATA = {
    "fabric": "fabric.mod.json",
    "quilt": "quilt.mod.json",
    "forge": "META-INF/mods.toml",
    "neoforge": "META-INF/neoforge.mods.toml",
}


def declared_version() -> str:
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("mod_version="):
            return line.split("=", 1)[1].strip()
    raise SystemExit("gradle.properties has no mod_version")


def check_templates(version: str) -> list[str]:
    """The mod metadata carries the version literally, because the jars are assembled from
    the raw resources (sourcesJar) or from src/quilt/resources (quiltJar), which bypass the
    processResources expansion. Keep the literal and mod_version in step."""
    problems = []
    for rel, key in (("src/main/resources/fabric.mod.json", '"version"'),
                     ("src/quilt/resources/quilt.mod.json", '"version"')):
        path = ROOT / rel
        if not path.is_file():
            problems.append(f"missing {rel}")
            continue
        text = path.read_text(encoding="utf-8")
        if '"${version}"' in text:
            problems.append(f"{rel}: placeholder ${{version}} would ship verbatim")
        elif f'{key}: "{version}"' not in text:
            problems.append(f"{rel}: version does not match mod_version={version}")
    return problems


def main() -> int:
    version = declared_version()
    problems = check_templates(version)
    jars = sorted(p for p in DIST.glob("*.jar") if not p.name.endswith("-sources.jar"))
    if not jars:
        print(f"no jars in {DIST}", file=sys.stderr)
        return 1

    notes: list[str] = []
    seen: dict[str, pathlib.Path] = {}

    for jar in jars:
        name = jar.name
        loader = next((l for l in METADATA if f"-{l}-" in f"-{name}-"), None)
        if loader is None:
            problems.append(f"{name}: file name does not state a loader")
            continue
        seen[loader] = jar

        size_mb = jar.stat().st_size / (1024 * 1024)
        if size_mb > 5:
            problems.append(f"{name}: {size_mb:.1f} MB looks like a toolchain jar, not a mod")
            continue

        with zipfile.ZipFile(jar) as zf:
            entries = set(zf.namelist())
            wanted = METADATA[loader]
            if wanted not in entries:
                problems.append(f"{name}: missing {wanted}")
            # A Fabric jar must not carry quilt.mod.json and vice versa: one of them is
            # wrong, and which one the loader reads depends on load order.
            for other_loader, other in METADATA.items():
                if other_loader == loader or other == wanted:
                    continue
                if other_loader in ("forge", "neoforge"):
                    continue  # both read META-INF/*mods.toml; one mod may support both
                if other in entries:
                    problems.append(f"{name}: also contains {other} ({other_loader})")
            if wanted.endswith(".json"):
                text = zf.read(wanted).decode("utf-8")
                if "${version}" in text:
                    notes.append(f"{name}: {wanted} has an unexpanded ${{version}} (rebuild needed)")

    for loader in METADATA:
        if loader not in seen:
            problems.append(f"dist/ has no -{loader}- jar")

    # The sources artifact is the reference for the other three ports: it must actually
    # contain sources, and its metadata must not be a template. Both have regressed before.
    sources = sorted(DIST.glob("*-sources.jar"))
    if not sources:
        notes.append("dist/ has no sources jar")
    for jar in sources:
        with zipfile.ZipFile(jar) as zf:
            entries = zf.namelist()
            count = sum(1 for e in entries if e.endswith(".java"))
            if count == 0:
                problems.append(f"{jar.name}: contains no .java sources")
            for entry in entries:
                if entry.endswith(".json") and "${" in zf.read(entry).decode("utf-8", "replace"):
                    problems.append(f"{jar.name}: {entry} has an unexpanded placeholder")

    print(f"dist/: {len(jars)} jar(s) -> {', '.join(sorted(seen))}")
    for note in notes:
        print(f"note: {note}")
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print("Every jar names its loader and carries that loader's metadata.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

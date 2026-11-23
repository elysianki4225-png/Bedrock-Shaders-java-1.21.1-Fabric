#!/usr/bin/env python3
"""Catch the one Java mistake that javac reports but no offline script did until now.

A subpackage does NOT see types from its parent package. `dev.bedrockshaders.api` cannot
name `BedrockShadersMod` (in `dev.bedrockshaders`) without an explicit import, and the
error javac gives is the confusing "package BedrockShadersMod does not exist". That
exact mistake broke the build once, so it is checked here instead of in CI.

The rule: a simple type name used in a file must be declared in that file's own package,
imported by name, or covered by a wildcard import.
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE_ROOTS = [
    ROOT / "src" / "main" / "java",
    ROOT / "src" / "client" / "java",
    ROOT / "loaders" / "common-official" / "src" / "main" / "java",
    ROOT / "loaders" / "forge" / "src" / "main" / "java",
    ROOT / "loaders" / "neoforge" / "src" / "main" / "java",
]

TYPE_DECL = re.compile(r"^(?:public\s+|final\s+|abstract\s+|sealed\s+|non-sealed\s+)*"
                       r"(?:class|interface|enum|record)\s+(\w+)", re.M)
PACKAGE = re.compile(r"^package\s+([\w.]+)\s*;", re.M)
IMPORT = re.compile(r"^import\s+(?:static\s+)?([\w.]+)\s*;", re.M)


def strip_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    return re.sub(r"^\s*(?:package|import)\s[^\n]*$", "", text, flags=re.M)


def main() -> int:
    files = []
    for root in SOURCE_ROOTS:
        if root.is_dir():
            files += sorted(root.rglob("*.java"))

    # Every top-level type in the project, mapped to where it is declared.
    home: dict[str, str] = {}
    packages: dict[pathlib.Path, str] = {}
    for path in files:
        text = path.read_text(encoding="utf-8")
        package = PACKAGE.search(text)
        if not package:
            continue
        packages[path] = package.group(1)
        for declared in TYPE_DECL.finditer(text):
            home.setdefault(declared.group(1), package.group(1))

    problems: list[str] = []
    for path in files:
        if path not in packages:
            continue
        own = packages[path]
        text = path.read_text(encoding="utf-8")
        imports = IMPORT.findall(text)
        by_name = {i.split(".")[-1] for i in imports}
        wildcard = {i[:-2] for i in imports if i.endswith(".*")}
        body = strip_comments(text)
        rel = path.relative_to(ROOT).as_posix()
        for name, declared_in in home.items():
            if declared_in == own or name in by_name or declared_in in wildcard:
                continue
            # A bare use is one not preceded by a dot (that would be a qualified name).
            if re.search(rf"(?<![\w.]){re.escape(name)}\b", body):
                problems.append(
                    f"{rel}: uses {name} from {declared_in} without importing it "
                    f"(a subpackage does not see its parent package)")

    print(f"imports: {len(files)} source file(s), {len(home)} type(s) indexed")
    if problems:
        print("\n".join(sorted(set(problems))), file=sys.stderr)
        return 1
    print("Import check OK: every cross-package type is imported explicitly.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

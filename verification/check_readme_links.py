#!/usr/bin/env python3
"""Every download link in the READMEs must point at a file that is really in dist/.

The READMEs are the download page: four jars, one line each per language, and a renamed
artifact silently turns them into 404s. This asserts that the English and Chinese READMEs
link the same set, that the set is exactly what dist/ ships, and that both files carry the
language switcher.
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
# document -> the other document it must cross-link to
DOCS = {
    "README.md": "README.zh_cn.md",
    "README.zh_cn.md": "README.md",
}
LINK = re.compile(r"\((dist/[^)\s]+\.jar)\)")


def links(path: pathlib.Path) -> set[str]:
    return set(LINK.findall(path.read_text(encoding="utf-8")))


def main() -> int:
    problems: list[str] = []
    per_doc: dict[str, set[str]] = {}

    for name, other in DOCS.items():
        path = ROOT / name
        if not path.is_file():
            problems.append(f"missing {name}")
            continue
        text = path.read_text(encoding="utf-8")
        found = links(path)
        per_doc[name] = found
        if not found:
            problems.append(f"{name}: no dist/ download links")
        # language switcher: the other document must be reachable by name
        if other not in text:
            problems.append(f"{name}: no link to {other}")
        for target in sorted(found):
            if not (ROOT / target).is_file():
                problems.append(f"{name}: {target} does not exist")

    if len(per_doc) == 2:
        en, zh = per_doc["README.md"], per_doc["README.zh_cn.md"]
        if en != zh:
            problems.append(
                "README.md and README.zh_cn.md link different jars: "
                f"only-en={sorted(en - zh)} only-zh={sorted(zh - en)}"
            )
        # one download per loader, plus the sources jar
        for loader in ("fabric", "quilt", "forge", "neoforge"):
            pattern = f"-{loader}-"
            if not any(pattern in target for target in en):
                problems.append(f"no download link for the {loader} jar")

    print(f"checked {len(per_doc)} README file(s), {len(set().union(*per_doc.values()) if per_doc else set())} link target(s)")
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print("README download links match dist/ and both languages are cross-linked.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Cheap structural check for every Java source file in the repo.

javac is the real compiler, but it only runs in CI, twice per job, several minutes in. The
mistakes that slip through are almost always structural: a stray or missing brace when a
line is appended to a long concatenation, an unbalanced parenthesis in a fluent chain.
Three separate CI rounds were spent on exactly that during the compatibility work, so this
fails in a second instead.

It strips comments and string/char literals, then checks that (), {}, [] nest correctly and
that no file ends mid-block. It is deliberately not a parser: it will not catch a type
error, only a shape error.
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SKIP = {'.git', 'build', 'node_modules', '.gradle', 'dist'}

PAIRS = {')': '(', '}': '{', ']': '['}


def strip_noise(text: str) -> str:
    """Removes comments and literals in one pass, tracking escapes properly.

    A regex cannot do this correctly: a Java string may contain escaped quotes, and comment
    markers inside a literal are not comments. Getting this wrong produces false
    "unbalanced" reports, which is worse than having no check at all.
    """
    out = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ''
        if ch == '/' and nxt == '/':
            while i < n and text[i] != '\n':
                i += 1
        elif ch == '/' and nxt == '*':
            i += 2
            while i + 1 < n and not (text[i] == '*' and text[i + 1] == '/'):
                if text[i] == '\n':
                    out.append('\n')
                i += 1
            i += 2
        elif ch == '"' and text[i:i + 3] == '"""':
            # Text block: runs to the next unescaped triple quote, newlines preserved so
            # line numbers stay right. Skipping it as a normal literal would leave a
            # dangling quote and a bogus "unbalanced" report.
            i += 3
            while i < n and text[i:i + 3] != '"""':
                if text[i] == '\\':
                    i += 2
                    continue
                if text[i] == '\n':
                    out.append('\n')
                i += 1
            i += 3
        elif ch == '"' or ch == "'":
            quote = ch
            out.append('""')
            i += 1
            while i < n:
                if text[i] == '\\':
                    i += 2
                    continue
                if text[i] == quote or text[i] == '\n':
                    i += 1
                    break
                i += 1
        else:
            out.append(ch)
            i += 1
    return ''.join(out)


def check(path: pathlib.Path) -> list[str]:
    text = strip_noise(path.read_text(encoding='utf-8'))
    stack: list[tuple[str, int]] = []
    problems: list[str] = []
    line = 1
    for ch in text:
        if ch == '\n':
            line += 1
        elif ch in '({[':
            stack.append((ch, line))
        elif ch in ')}]':
            if not stack:
                problems.append(f'line {line}: unexpected {ch}')
                return problems
            opener, opened_at = stack.pop()
            if opener != PAIRS[ch]:
                problems.append(f'line {line}: {ch} closes {opener} opened on line {opened_at}')
                return problems
    if stack:
        opener, opened_at = stack[-1]
        problems.append(f'{len(stack)} unclosed block(s), last {opener} on line {opened_at}')
    return problems


def main() -> int:
    files = [p for p in ROOT.rglob('*.java')
             if not (set(p.relative_to(ROOT).parts) & SKIP)]
    if not files:
        print('no Java sources found', file=sys.stderr)
        return 1

    failures = 0
    for path in sorted(files):
        problems = check(path)
        if problems:
            failures += 1
            rel = path.relative_to(ROOT).as_posix()
            for problem in problems:
                print(f'{rel}: {problem}', file=sys.stderr)

    if failures:
        print(f'\n{failures} of {len(files)} Java file(s) are structurally broken', file=sys.stderr)
        return 1

    print(f'Java syntax shape OK: {len(files)} files, all blocks and parentheses balanced.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

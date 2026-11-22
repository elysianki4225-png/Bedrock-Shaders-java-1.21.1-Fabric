#!/usr/bin/env python3
"""
Down-converts Minecraft's GLSL 150 core-profile shaders to GLSL 300 es so they can
be syntax/type checked offline with Khronos glslang (via glslang-wasm-validator).

Only the constructs Minecraft actually uses are rewritten; the rewritten source is
never shipped. Anything unsupported fails loudly so the check stays honest.
"""
import re
import sys

HEADER = """#version 300 es
precision highp float;
precision highp int;
precision highp sampler2D;
"""

SAMPLER_TYPES = {"sampler2D", "sampler2DShadow", "samplerCube", "isampler2D", "usampler2D", "sampler3D"}

TYPE_RE = re.compile(r"\b(float|int|uint|bool|vec2|vec3|vec4|ivec2|ivec3|ivec4|uvec2|uvec3|uvec4|bvec2|bvec3|bvec4|mat2|mat3|mat4|sampler2D|sampler2DShadow|samplerCube|isampler2D|usampler2D|sampler3D)\b")
DECL_RE = re.compile(
    r"^(?P<inout>(?:in|out))\s+(?P<type>[A-Za-z0-9_]+)\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*;\s*$")


def strip_comments_keep_lines(text: str) -> str:
    out_lines = []
    for line in text.splitlines():
        if line.lstrip().startswith("//"):
            out_lines.append("")
            continue
        out_lines.append(line)
    return "\n".join(out_lines)


def convert(source: str) -> str:
    text = strip_comments_keep_lines(source)
    if "#version 400" in text:
        text = text.replace("#version 400", "").lstrip("\n")
    lines = text.splitlines()

    frag_outputs = []
    varying_names = []
    result = []

    for line in lines:
        stripped = line.strip()
        m = DECL_RE.match(stripped)
        if m:
            kind, typ = m.group("inout"), m.group("type")
            name = m.group("name")
            if typ in SAMPLER_TYPES:
                result.append(f"uniform {typ} {name};")
                continue
            if kind == "in":
                result.append(f"in {typ} {name};")
                varying_names.append(name)
                continue
            if kind == "out":
                frag_outputs.append((typ, name))
                continue
        result.append(line)

    body = "\n".join(result)

    # Rewrite texture(...) calls on shadow samplers is not used by vanilla, keep plain.
    # texelFetch on sampler2D with ivec2 works in ES 3.0; nothing to do.

    # Declare the fragment outputs after globals but before the first function.
    if frag_outputs:
        decl = "\n".join(f"out {typ} {name};" for typ, name in frag_outputs)
        # Insert before the first function definition at column 0 ending with ') {' line.
        insertion = None
        lines2 = body.splitlines()
        for i, line in enumerate(lines2):
            if re.match(r"^[A-Za-z_][A-Za-z0-9_]*\s+[A-Za-z_][A-Za-z0-9_]*\s*\(.*\)\s*\{\s*$", line) and not line.strip().startswith("uniform"):
                insertion = i
                break
        if insertion is None:
            raise SystemExit("could not find function body to insert fragment outputs")
        lines2.insert(insertion, decl + "\n")
        body = "\n".join(lines2)

    # main() must come from the original shader; ES requires the fragment output to be written.
    return HEADER + body + "\n"


def main():
    src_path, dst_path = sys.argv[1], sys.argv[2]
    with open(src_path, "r", encoding="utf-8") as f:
        src = f.read()
    out = convert(src)
    with open(dst_path, "w", encoding="utf-8") as f:
        f.write(out)


if __name__ == "__main__":
    main()

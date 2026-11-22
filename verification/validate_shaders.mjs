#!/usr/bin/env node
/*
 * Validates every Bedrock Shaders core shader offline:
 *   1. compose the runtime GLSL exactly like the Java pack builder does,
 *   2. rewrite the GLSL 150 core shader to GLSL 300 es (see glsl_compat.py),
 *   3. compile the vertex/fragment pair with Khronos glslang (WASM, no GPU needed).
 *
 * The rewritten GLSL is never shipped; only the original 150 sources are packaged.
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { execFileSync as exec0 } from 'node:child_process';
import { createValidator } from 'glslang-wasm-validator';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..');
const SRC = path.join(ROOT, 'shadersrc');
const OUT = path.join(HERE, 'out');

const PROGRAMS = [
  { name: 'rendertype_solid', vertex: 'bedrock_terrain.vsh', fragment: 'bedrock_terrain.fsh', shared: 'common_lighting.glsl' },
  { name: 'rendertype_translucent', vertex: 'bedrock_translucent.vsh', fragment: 'bedrock_translucent.fsh', helper: 'bedrock_translucent.glsl' },
  { name: 'rendertype_entity_solid', vertex: 'bedrock_entity.vsh', fragment: 'bedrock_entity.fsh', shared: 'common_lighting.glsl' },
];

function stripVersion(text) {
  return text
    .split('\n')
    .map((line) => (line.trim().startsWith('#version') ? '' : line))
    .join('\n');
}

function dedupeUniforms(text, seen) {
  return text
    .split('\n')
    .filter((line) => {
      const m = line.trim().match(/^uniform\s+[A-Za-z0-9_]+\s+([A-Za-z_][A-Za-z0-9_]*)\s*;/);
      if (!m) return true;
      if (seen.has(m[1])) return false;
      seen.add(m[1]);
      return true;
    })
    .join('\n');
}

function compose(program, stage) {
  const body = fs.readFileSync(path.join(SRC, 'core', stage), 'utf8');
  const global = fs.readFileSync(path.join(SRC, 'include', 'bedrock_global.glsl'), 'utf8');
  const helpers = program.helper ? fs.readFileSync(path.join(SRC, 'include', program.helper), 'utf8') : '';
  const shared = program.shared ? fs.readFileSync(path.join(SRC, 'include', program.shared), 'utf8') : '';
  const idx = body.indexOf('void main()');
  if (idx < 0) throw new Error(`${stage}: no main()`);
  const seen = new Set();
  return body.slice(0, idx) + '\n' + dedupeUniforms(stripVersion(global), seen) + '\n'
      + dedupeUniforms(stripVersion(helpers), seen) + '\n'
      + (shared ? dedupeUniforms(stripVersion(shared), seen) + '\n' : '') + body.slice(idx);
}

fs.mkdirSync(OUT, { recursive: true });

// 1) Post-processing / sky / water effects compiled at runtime from Java text blocks.
exec0('node', [path.join(HERE, 'extract_java_glsl.mjs')], { stdio: 'inherit' });

const validator = await createValidator();
const failures0 = [];
const javaEffects = fs.readdirSync(OUT).filter((f) => /\.(vert|frag)$/.test(f) && !f.includes('.es.'));
const vertex = javaEffects.find((f) => f.endsWith('.vert'));
for (const frag of javaEffects.filter((f) => f.endsWith('.frag'))) {
  if (!vertex) break;
  const result = validator.checkPass({
    vertex: { name: vertex, source: fs.readFileSync(path.join(OUT, vertex), 'utf8') },
    fragment: { name: frag, source: fs.readFileSync(path.join(OUT, frag), 'utf8') },
  });
  if (result.status === 'pass') {
    console.log(`ok   java:${frag}`);
  } else {
    failures0.push(frag);
    console.error(`FAIL java:${frag}`);
    for (const d of result.diagnostics ?? []) {
      console.error(`     ${d.severity}: ${d.message} (${d.source}:${d.line})`);
    }
  }
}
let failures = 0;

try {
  for (const program of PROGRAMS) {
    const vert = compose(program, program.vertex);
    const frag = compose(program, program.fragment);
    const vertPath = path.join(OUT, `${program.name}.vert`);
    const fragPath = path.join(OUT, `${program.name}.frag`);
    const vertEs = path.join(OUT, `${program.name}.es.vert`);
    const fragEs = path.join(OUT, `${program.name}.es.frag`);
    fs.writeFileSync(vertPath, vert);
    fs.writeFileSync(fragPath, frag);
    // Only VARYINGS are converted from `in/out` to `uniform` for the validator;
    // samplers keep their original qualifiers, so no declaration becomes invalid.
    execFileSync('python3', [path.join(HERE, 'glsl_compat.py'), vertPath, vertEs], { stdio: 'inherit' });
    execFileSync('python3', [path.join(HERE, 'glsl_compat.py'), fragPath, fragEs], { stdio: 'inherit' });

    const result = validator.checkPass({
      vertex: { name: `${program.name}.vert`, source: fs.readFileSync(vertEs, 'utf8') },
      fragment: { name: `${program.name}.frag`, source: fs.readFileSync(fragEs, 'utf8') },
    });
    if (result.status === 'pass') {
      console.log(`ok   ${program.name}`);
    } else {
      failures++;
      console.error(`FAIL ${program.name}`);
      for (const d of result.diagnostics ?? []) {
        console.error(`     ${d.severity}: ${d.message} (${d.source}:${d.line})`);
      }
    }
  }
} finally {
  validator.dispose();
}

if (failures === 0 && failures0.length === 0) {
  console.log('\nAll Bedrock Shaders core shaders compile cleanly.');
  process.exit(0);
}
console.error(`\n${failures + failures0.length} shader program(s) failed validation.`);
process.exit(1);

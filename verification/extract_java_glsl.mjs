#!/usr/bin/env node
/*
 * Extracts every GLSL string literal from the Java pipelines so the same offline
 * glslang validation also covers the runtime-compiled post-processing shaders.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..');
const SRC = path.join(ROOT, 'src', 'client', 'java', 'dev', 'bedrockshaders', 'client', 'render');
const OUT = path.join(HERE, 'out');

const chunks = [];
function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full);
    else if (entry.name.endsWith('.java')) chunks.push(full);
  }
}
walk(SRC);

fs.mkdirSync(OUT, { recursive: true });
// Clean previous extractions so stale shaders never pass validation.
for (const f of fs.readdirSync(OUT)) {
  if (/\.(vert|frag)$/.test(f)) fs.rmSync(path.join(OUT, f));
}
let count = 0;
for (const file of chunks) {
  const text = fs.readFileSync(file, 'utf8');
  // Java text blocks: """ ... """
  const re = /"""\n([\s\S]*?)"""/g;
  let m;
  let index = 0;
  while ((m = re.exec(text)) !== null) {
    const body = m[1];
    if (!body.trimStart().startsWith('#version 150')) continue;
    const isVertex = body.includes('gl_Position') && !body.includes('fragColor');
    const name = `${path.basename(file, '.java')}_${index}${isVertex ? '.vert' : '.frag'}`;
    fs.writeFileSync(path.join(OUT, name), body);
    console.log(`extracted ${name}`);
    index++;
    count++;
  }
}
console.log(`extracted ${count} shader sources from Java`);

#!/usr/bin/env node
/*
 * Single source of truth for the shipped GLSL.
 *
 * Reads shadersrc/core/<program>.{vsh,fsh}, removes every uniform declaration that
 * one of the shared includes provides, then prepends
 *
 *     #moj_import <bedrock_global.glsl>
 *     #moj_import <common_lighting.glsl>
 *     #moj_import <bedrock_translucent.glsl>     (water program only)
 *
 * and writes the result to the mod's assets so the runtime pack builder can flatten
 * it with vanilla's own #moj_import rules.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..');
const SRC = path.join(ROOT, 'shadersrc');
const DEST = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'bedrock_shaders', 'bedrockshader');

const INCLUDES = ['bedrock_global.glsl', 'common_lighting.glsl'];
const UNIFORM_RE = /^uniform\s+[A-Za-z0-9_]+\s+([A-Za-z_][A-Za-z0-9_]*)\s*;/;

function declaredUniforms(texts) {
  const names = new Set();
  for (const text of texts) {
    for (const line of text.split('\n')) {
      const m = line.trim().match(UNIFORM_RE);
      if (m) names.add(m[1]);
    }
  }
  return names;
}

const globalSrc = fs.readFileSync(path.join(SRC, 'include', 'bedrock_global.glsl'), 'utf8');
const sharedSrc = fs.readFileSync(path.join(SRC, 'include', 'common_lighting.glsl'), 'utf8');
const waterSrc = fs.readFileSync(path.join(SRC, 'include', 'bedrock_translucent.glsl'), 'utf8');

fs.mkdirSync(path.join(DEST, 'core'), { recursive: true });
fs.mkdirSync(path.join(DEST, 'include'), { recursive: true });

// Ship the includes verbatim (minus their #version line, which vanilla forbids mid-file).
for (const name of ['bedrock_global.glsl', 'common_lighting.glsl', 'bedrock_translucent.glsl']) {
  const text = fs.readFileSync(path.join(SRC, 'include', name), 'utf8')
    .split('\n')
    .filter((line) => !line.trim().startsWith('#version'))
    .join('\n');
  fs.writeFileSync(path.join(DEST, 'include', name), text);
}

for (const file of fs.readdirSync(path.join(SRC, 'core'))) {
  if (!/\.(vsh|fsh)$/.test(file)) continue;
  const isWater = file.startsWith('bedrock_water');
  const includes = isWater ? [...INCLUDES, 'bedrock_translucent.glsl'] : INCLUDES;
  const provided = declaredUniforms([
    globalSrc,
    sharedSrc,
    ...(isWater ? [waterSrc] : []),
  ]);
  const lines = fs.readFileSync(path.join(SRC, 'core', file), 'utf8').split('\n');
  const kept = lines.filter((line) => {
    const m = line.trim().match(UNIFORM_RE);
    return !(m && provided.has(m[1]));
  });
  const versionIndex = kept.findIndex((line) => line.trim().startsWith('#version'));
  const directives = includes.map((name) => `#moj_import <${name}>`);
  kept.splice(versionIndex + 1, 0, '', ...directives, '');
  fs.writeFileSync(path.join(DEST, 'core', file), kept.join('\n'));
  console.log(`synced core/${file} (${includes.length} includes)`);
}

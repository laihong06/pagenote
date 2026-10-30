import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { createRequire } from 'node:module';
const result = await build({
  stdin: {
    contents:
      "export {replaceNativeState} from './src/markdown-native.js'; export {importNativeLesson} from './src/markdown-native-import.js'",
    resolveDir: process.cwd(),
  },
  bundle: true,
  write: false,
  format: 'cjs',
  platform: 'node',
  loader: { '.css': 'empty' },
  logLevel: 'silent',
});
const module = { exports: {} };
new Function('module', 'exports', 'require', result.outputFiles[0].text)(
  module,
  module.exports,
  createRequire(import.meta.url),
);
const replace = module.exports.replaceNativeState;
const original = {
  version: 1,
  id: 'native-one',
  kind: 'genetic-lab',
  genes: Array(12).fill(0),
  position: 50,
};
for (const newline of ['\n', '\r\n']) {
  const prefix =
    ['# 手写原文', '', 'Keep **this** and $x^2$ exactly.', ''].join(newline) +
    newline;
  const suffix = newline + '> 不要改写这个段落' + newline;
  const block = ['```MarkdownS', JSON.stringify(original), '```', ''].join(
    newline,
  );
  const changed = replace(prefix + block + suffix, original.id, {
    ...original,
    position: 80,
  });
  assert.ok(changed.startsWith(prefix));
  assert.ok(changed.endsWith(suffix));
  assert.ok(changed.includes('"position": 80'));
  if (newline === '\r\n')
    assert.equal(
      changed.replaceAll('\r\n', '').includes('\n'),
      false,
      'preserve CRLF',
    );
  assert.throws(() => replace(block + block, original.id, original), /重复/);
  assert.throws(() => replace(block, 'missing', original), /缺失/);
}
console.log(
  'PASS native same-file state updates: untouched prose, UTF-8, LF/CRLF, uppercase fence and duplicate/missing ID rejection',
);

const imported = module.exports.importNativeLesson(
  '<script id="ga-core"></script><canvas id="evolutionMap"></canvas>',
  'lesson.html',
  'known-id',
);
assert.ok(imported.markdown.includes('known-id'));
assert.equal(imported.state.genes.length, 12);
assert.ok(!imported.markdown.includes('<script'));
assert.equal(imported.state.manifest.dependencies.length, 3);
assert.throws(
  () =>
    module.exports.importNativeLesson(
      '<script>throw Error("must not execute")</script>',
      'unknown.html',
      'unknown',
    ),
  /尚不能转换/,
);
console.log(
  'PASS recognized HTML lesson becomes native Markdown data; arbitrary document scripts are not executed',
);

const fence =
  '\x60\x60\x60markdowns\n' + JSON.stringify(original) + '\n\x60\x60\x60';
assert.throws(
  () =>
    replace(
      fence,
      original.id,
      { ...original, position: 80 },
      { ...original, position: 40 },
    ),
  /其他修改/,
);
assert.ok(
  replace(fence, original.id, { ...original, position: 80 }, original).includes(
    '80',
  ),
);
console.log(
  'PASS native stale-state writes are rejected before changing the Markdown',
);

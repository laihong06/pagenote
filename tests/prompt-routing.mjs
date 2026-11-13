import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import prompts from '../desktop/prompts.cjs';

const data = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-routing-'));
const store = prompts.createPromptStore(path.resolve('skills'), data);
const index = store.index();
for (const workflow of ['generation', 'revision', 'direct']) {
  const route = prompts.resolvePromptRoute(workflow, 'explain');
  for (const id of index.qualityModules)
    assert.ok(route.modules.includes(id), workflow + ': ' + id);
  for (const intent of ['summarize', 'translate', 'edit', 'annotate']) {
    const result = prompts.resolvePromptRoute(workflow, intent);
    assert.ok(!result.modules.includes('learning'));
    assert.ok(!result.modules.includes('representation'));
    for (const id of result.modules)
      assert.ok(index.modules.some((m) => m.id === id));
  }
}
assert.throws(() => prompts.resolvePromptRoute('unknown', 'explain'));
assert.throws(() => prompts.resolvePromptRoute('direct', 'unknown'));
const defaults = store.read('representation');
const edited = await store.save(
  'representation',
  defaults.text + '\n用户自定义表达。',
  defaults.revision,
);
assert.equal(store.read('representation', edited.revision).unchanged, true);
assert.notEqual(store.index().revision, index.revision);
assert.ok(store.read('representation').text.includes('用户自定义表达。'));
assert.equal(
  (await store.save('representation', null, edited.revision)).text,
  defaults.text,
);
console.log(
  'PASS shared explanation standards across generation/revision/direct; non-teaching intents, module discovery, editable representation, revisions and reset',
);

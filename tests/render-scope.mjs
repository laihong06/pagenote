import assert from 'node:assert/strict';
import { RenderScope } from '../src/render-scope.js';
const scope = new RenderScope();
const first = scope.begin('diagram', 'A');
const second = scope.begin('diagram', 'B');
const last = scope.begin('diagram', 'A');
assert.equal(first.current(), false, 'A→B→A must invalidate the first A');
assert.equal(second.signal.aborted, true);
assert.equal(last.current(), true);
assert.ok(last.generation > first.generation);
const pending = Promise.resolve().then(() => last.current());
scope.abort();
assert.equal(
  await pending,
  false,
  'async completion after disposal cannot commit',
);
assert.equal(last.signal.aborted, true);
assert.equal(
  scope.begin('later', 'C').current(),
  false,
  'disposed scopes cannot revive',
);
console.log(
  'PASS render scope: ABA generation, cancellation and disposed async completion',
);

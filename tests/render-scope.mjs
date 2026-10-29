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

const repeated = new RenderScope();
for (let i = 0; i < 1000; i++) repeated.begin({ id: i }, 'source').cancel();
assert.equal(repeated.size, 0, 'cancelled block references must be released');
const old = repeated.begin('same', 'A');
const newer = repeated.begin('same', 'B');
old.cancel();
assert.equal(
  newer.current(),
  true,
  'old cancellation must not remove newer task',
);
newer.release();
assert.equal(repeated.size, 0);
const disposed = new RenderScope();
disposed.abort();
assert.equal(disposed.begin('invalid', 'A').signal.aborted, true);
assert.equal(disposed.size, 0);
console.log('PASS render scope resource release across 1000 cancellations');

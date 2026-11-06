import assert from 'node:assert/strict';
import { RenderQueue } from '../src/render-queue.js';
const queue = new RenderQueue();
const order = [];
const controller = new AbortController();
const cancelled = queue.enqueue({
  signal: controller.signal,
  run: () => order.push('cancelled'),
});
controller.abort();
const low = queue.enqueue({ priority: 0, run: () => order.push('background') });
const high = queue.enqueue({ priority: 10, run: () => order.push('editing') });
assert.equal(await cancelled, 'cancelled');
await Promise.all([low, high]);
assert.deepEqual(order, ['editing', 'background']);
let release;
let published = false;
let timeoutError;
const slow = queue.enqueue({
  timeout: 5,
  run: () =>
    new Promise((r) => {
      release = r;
    }),
  publish: () => {
    published = true;
  },
  fail: (e) => {
    timeoutError = e;
  },
});
assert.equal(await slow, 'timeout');
assert.match(timeoutError.message, /超时/);
let nextStarted = false;
const next = queue.enqueue({
  run: () => {
    nextStarted = true;
  },
});
await new Promise((r) => setTimeout(r, 10));
assert.equal(nextStarted, false, 'timeout must not release the engine lane');
release('late SVG');
await next;
assert.equal(published, false, 'timed out render must not publish');
assert.equal(nextStarted, true);
const failed = queue.enqueue({
  run: () => {
    throw Error('invalid diagram');
  },
});
assert.equal(await failed, 'error');
assert.equal(await queue.enqueue({ run: () => 'valid diagram' }), 'ready');
console.log(
  'PASS render queue: priority, cancellation, timeout, serial exclusion and failure recovery',
);

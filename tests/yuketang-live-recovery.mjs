import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const fixture = require('../docs/spec/yuketang/fixtures/live-reconnect-timeline.json');
const {
  normalizeLiveMessage,
  selectTimelineReplay,
} = require('../desktop/yuketang-protocol.cjs');

const normalized = normalizeLiveMessage(fixture);
assert.equal(normalized.type, 'timeline');
assert.deepEqual(
  normalized.slides.map(({ presentationId, sourceIndex }) => [
    presentationId,
    sourceIndex,
  ]),
  [
    ['fixture-presentation-a', 0],
    ['fixture-presentation-a', 1],
    ['fixture-presentation-b', 0],
    ['fixture-presentation-b', 1],
  ],
);

assert.deepEqual(
  selectTimelineReplay(normalized.slides, null, true).slides.map(
    (slide) => slide.sourceIndex,
  ),
  [1],
  'first connection imports the currently visible slide only',
);

const cursor = 'fixture-presentation-a:0';
const resumed = selectTimelineReplay(normalized.slides, cursor, false);
assert.equal(resumed.possibleGap, false);
assert.deepEqual(
  resumed.slides.map(({ presentationId, sourceIndex }) => [
    presentationId,
    sourceIndex,
  ]),
  [
    ['fixture-presentation-a', 1],
    ['fixture-presentation-b', 0],
    ['fixture-presentation-b', 1],
  ],
);

const missingCursor = selectTimelineReplay(
  normalized.slides,
  'older-presentation:9',
  false,
);
assert.equal(missingCursor.possibleGap, true);
assert.deepEqual(
  missingCursor.slides.map(({ presentationId, sourceIndex }) => [
    presentationId,
    sourceIndex,
  ]),
  [['fixture-presentation-b', 1]],
);

console.log('Yuketang live timeline recovery tests passed');

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
  [0, 1, 0, 1],
  'first connection backfills the complete visible timeline',
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

const stablePages = normalizeLiveMessage({
  op: 'hello',
  timeline: [
    { type: 'slide', pres: 'deck', sid: 'slide-zero' },
    { type: 'question', pres: 'deck', sid: 'not-a-slide' },
    { type: 'slide', pres: 'deck', si: null, sid: 'slide-one' },
  ],
});
assert.deepEqual(stablePages.slides, [
  { presentationId: 'deck', sourceIndex: null, pageId: 'slide-zero' },
  { presentationId: 'deck', sourceIndex: null, pageId: 'slide-one' },
]);
assert.deepEqual(
  selectTimelineReplay(
    stablePages.slides,
    'deck:0',
    false,
    'deck:page:slide-zero',
  ),
  {
    slides: [stablePages.slides[1]],
    possibleGap: false,
  },
);

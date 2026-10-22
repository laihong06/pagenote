import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const fixture = require('../docs/spec/yuketang/fixtures/current-lessons.json');
const noStatusFixture = require('../docs/spec/yuketang/fixtures/current-lessons-no-status.json');
const { normalizeCurrentLessons } = require('../desktop/yuketang-protocol.cjs');
const { createRainClassroomService } = require('../desktop/yuketang.cjs');

assert.equal(typeof normalizeCurrentLessons, 'function');
assert.deepEqual(normalizeCurrentLessons(fixture), [
  {
    lessonId: '9282',
    classroomId: 'fixture-classroom-1',
    courseId: '7351',
    name: 'Fixture live lecture',
  },
]);
assert.deepEqual(normalizeCurrentLessons(noStatusFixture), [
  {
    lessonId: '9283',
    classroomId: 'fixture-classroom-2',
    courseId: null,
    name: 'Fixture course without status',
  },
]);

let requestUrl;
const service = createRainClassroomService({
  secureStore: {
    get: async () => JSON.stringify({ cookie: 'fixture-session=secret' }),
    set: async () => {},
    clear: async () => {},
  },
  fetchImpl: async (url, options) => {
    requestUrl = new URL(url);
    assert.equal(options.headers.Cookie, 'fixture-session=secret');
    return new Response(JSON.stringify(fixture), {
      headers: { 'content-type': 'application/json' },
    });
  },
});

assert.deepEqual(await service.listCurrentLessons(), [
  {
    lessonId: '9282',
    classroomId: 'fixture-classroom-1',
    courseId: '7351',
    name: 'Fixture live lecture',
  },
]);
assert.equal(requestUrl.origin, 'https://www.yuketang.cn');
assert.equal(requestUrl.pathname, '/api/v3/classroom/on-lesson');

console.log('Yuketang current lesson discovery tests passed');

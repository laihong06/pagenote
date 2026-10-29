import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { normalizeLessons } = require('../desktop/yuketang-protocol.cjs');
const { createRainClassroomService } = require('../desktop/yuketang.cjs');

const lessons = normalizeLessons(
  {
    data: {
      activities: [
        { type: 3, courseware_id: 81, title: 'Lesson report' },
        { type: 14, courseware_id: '82', title: 'Second report' },
        { type: 2, courseware_id: 'not-a-deck' },
        { presentation_id: 'standalone-deck', title: 'Standalone' },
      ],
    },
  },
  'room',
);
assert.deepEqual(
  lessons.map(({ lessonId, presentationId }) => [lessonId, presentationId]),
  [
    ['81', 'report-81'],
    ['82', 'report-82'],
    [null, 'standalone-deck'],
  ],
);

let stored = JSON.stringify({
  cookie: 'fixture=session',
  authorization: 'fixture-old',
});
let ongoing = false;
let incomplete = false;
const paths = [];
const service = createRainClassroomService({
  secureStore: {
    get: async () => stored,
    set: async (value) => {
      stored = value;
    },
    clear: async () => {
      stored = null;
    },
  },
  fetchImpl: async (url, options) => {
    const parsed = new URL(url);
    paths.push(parsed.pathname);
    assert.equal(options.headers.Cookie, 'fixture=session');
    assert.equal(options.headers.Origin, 'https://www.yuketang.cn');
    assert.equal(options.headers.xtbz, 'ykt');
    assert.equal(options.headers['X-Client'], 'h5');
    if (parsed.pathname.endsWith('/lesson-info')) {
      assert.equal(parsed.searchParams.get('lesson_id'), '81');
      assert.equal(options.headers.Authorization, 'Bearer fixture-old');
      if (ongoing) return json({ code: 1001, msg: 'LESSON_ON' });
      return json(
        { data: { presentationIds: ['deck-a', 'deck-b'] } },
        { 'set-auth': 'Bearer fixture-new' },
      );
    }
    assert.equal(parsed.pathname, '/api/v3/classroom-report/student/ppt');
    assert.equal(options.headers.Authorization, 'Bearer fixture-new');
    const deck = parsed.searchParams.get('presentationId');
    return json({
      data: {
        slideList: [
          { id: `${deck}-hidden`, index: 0, visible: false },
          {
            id: `${deck}-2`,
            index: 2,
            visible: true,
            cover: incomplete ? '' : `https://cdn.yuketang.cn/${deck}-2.png`,
          },
          {
            id: `${deck}-1`,
            index: 1,
            visible: true,
            cover: `https://cdn.yuketang.cn/${deck}-1.png`,
          },
        ],
      },
    });
  },
});
const presentation = await service.fetchLesson(lessons[0]);
assert.deepEqual(
  presentation.pages.map(({ presentationId, pageId, sourceIndex }) => [
    presentationId,
    pageId,
    sourceIndex,
  ]),
  [
    ['deck-a', 'deck-a-1', 1],
    ['deck-a', 'deck-a-2', 2],
    ['deck-b', 'deck-b-1', 1],
    ['deck-b', 'deck-b-2', 2],
  ],
);
assert.equal(JSON.parse(stored).authorization, 'Bearer fixture-new');
assert.equal(
  paths.some((path) => path.includes('presentation/fetch')),
  false,
);
stored = JSON.stringify({
  cookie: 'fixture=session',
  authorization: 'fixture-old',
});
ongoing = true;
await assert.rejects(
  service.fetchLesson(lessons[0]),
  (error) => error.code === 'LESSON_ON',
);
ongoing = false;
incomplete = true;
await assert.rejects(service.fetchLesson(lessons[0]), /页面数据不完整/);

function json(payload, headers = {}) {
  return new Response(JSON.stringify(payload), {
    headers: { 'content-type': 'application/json', ...headers },
  });
}
console.log(
  'Yuketang lesson history, visible pages and bearer rotation tests passed',
);

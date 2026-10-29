import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { PDFDocument } from 'pdf-lib';

const root = await fs.mkdtemp(
  path.join(os.tmpdir(), 'pagenote-rain-live-new-'),
);
process.env.PAGE_NOTES_DATA = path.join(root, 'data');
const parent = path.join(root, 'library');
await fs.mkdir(parent);
const png = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2WfQAAAAASUVORK5CYII=',
  'base64',
);
let server;
let capture;
try {
  const { configureRainClassroomService, startServer } = await import(
    '../server.mjs'
  );
  configureRainClassroomService({
    startLive: async (callbacks) => {
      capture = callbacks;
      return { status: 'connecting' };
    },
    downloadImage: async () => ({ bytes: png, contentType: 'image/png' }),
    stopLive: async () => ({ status: 'stopped', pendingSlides: 0 }),
  });
  server = await startServer(0);
  const origin = `http://127.0.0.1:${server.address().port}`;
  const response = await fetch(`${origin}/api/rain/live/start`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      parent,
      name: 'Live notebook',
      consent: true,
      course: { courseId: 'course', classroomId: 'room' },
      lesson: { lessonId: 'lesson', classroomId: 'room' },
    }),
  });
  assert.equal(response.status, 200);
  assert.deepEqual(
    await fs.readdir(parent),
    [],
    'no placeholder PDF before the first real slide',
  );
  const first = await capture.onSlide({
    presentationId: 'deck',
    pageId: 'one',
    sourceIndex: 0,
    imageUrl: 'https://cdn.yuketang.cn/one.png',
  });
  assert.ok(first.projectId);
  const projectRoot = path.join(parent, 'Live notebook');
  await fs.writeFile(
    path.join(projectRoot, 'src', 'right.md'),
    'Saved first-page note',
  );
  const replay = await capture.onSlide({
    presentationId: 'deck',
    pageId: 'one',
    sourceIndex: 0,
    imageUrl: 'https://cdn.yuketang.cn/one.png',
  });
  assert.equal(replay.added, false);
  const second = await capture.onSlide({
    presentationId: 'deck',
    pageId: 'two',
    sourceIndex: 1,
    imageUrl: 'https://cdn.yuketang.cn/two.png',
  });
  assert.equal(second.projectId, first.projectId);
  assert.equal(second.page, 2);
  const pdf = await PDFDocument.load(
    await fs.readFile(path.join(projectRoot, 'src', 'document.pdf')),
  );
  assert.equal(pdf.getPageCount(), 2);
  assert.match(
    await fs.readFile(path.join(projectRoot, 'src', 'right.md'), 'utf8'),
    /^Saved first-page note/,
  );
  console.log(
    'Yuketang new live project creation, replay and append route tests passed',
  );
} finally {
  if (server) await new Promise((resolve) => server.close(resolve));
  await fs.rm(root, { recursive: true, force: true });
}

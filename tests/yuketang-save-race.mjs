import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-rain-save-'));
process.env.PAGE_NOTES_DATA = path.join(tmp, 'data');
const parent = path.join(tmp, 'library');
await fs.mkdir(parent);
let server, capture, release;
const readFile = fs.readFile;
try {
  const { startServer, configureRainClassroomService } = await import(
    '../server.mjs'
  );
  configureRainClassroomService({
    startLive: async (value) => {
      capture = value;
      return { status: 'active' };
    },
    downloadImage: async () => ({
      contentType: 'image/png',
      bytes: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2WfQAAAAASUVORK5CYII=',
        'base64',
      ),
    }),
  });
  server = await startServer(0);
  const base = `http://127.0.0.1:${server.address().port}`;
  const response = await fetch(base + '/api/rain/live/start', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      parent,
      name: 'Concurrent notes',
      consent: true,
      course: { courseId: 'c', classroomId: 'r' },
      lesson: { lessonId: 'l' },
    }),
  });
  assert.equal(response.status, 200);
  const first = await capture.onSlide({
    presentationId: 'p',
    pageId: 'one',
    sourceIndex: 0,
    imageUrl: 'https://cdn.yuketang.cn/one.png',
  });
  const right = path.join(parent, 'Concurrent notes/src/right.md');
  const gate = new Promise((resolve) => {
    release = resolve;
  });
  let blocked;
  const entered = new Promise((resolve) => {
    blocked = resolve;
  });
  let armed = true;
  fs.readFile = async (file, ...args) => {
    const bytes = await readFile(file, ...args);
    if (armed && path.resolve(String(file)) === right) {
      armed = false;
      blocked();
      await gate;
    }
    return bytes;
  };
  const append = capture.onSlide({
    presentationId: 'p',
    pageId: 'two',
    sourceIndex: 1,
    imageUrl: 'https://cdn.yuketang.cn/two.png',
  });
  await entered;
  const save = fetch(`${base}/api/documents/${first.projectId}/notes/1`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ markdown: 'Concurrent note must survive' }),
  });
  await Promise.race([
    save,
    new Promise((resolve) => setTimeout(resolve, 200)),
  ]);
  release();
  await append;
  assert.equal((await save).status, 200);
  assert.match(
    await readFile(right, 'utf8'),
    /^Concurrent note must survive/,
    'append and autosave share one write transaction queue',
  );
  console.log(
    'PASS Rain append overlapping note save preserves portable notes',
  );
} finally {
  release?.();
  fs.readFile = readFile;
  if (server) await new Promise((resolve) => server.close(resolve));
  await fs.rm(tmp, { recursive: true, force: true });
}

import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

const root = await fs.mkdtemp(
  path.join(os.tmpdir(), 'pagenote-yuketang-route-'),
);
const data = path.join(root, 'data');
const parent = path.join(root, 'chosen-import-folder');
const png = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2WfQAAAAASUVORK5CYII=',
  'base64',
);
process.env.PAGE_NOTES_DATA = data;

let server;
try {
  const { configureRainClassroomService, startServer } = await import(
    '../server.mjs'
  );
  configureRainClassroomService({
    fetchPresentation: async (presentationId) => ({
      name: 'Fixture presentation',
      pages: [
        {
          pageId: 'fixture-slide',
          sourceIndex: 0,
          presentationId,
          imageUrl: 'https://cdn.yuketang.cn/fixture.png',
        },
      ],
    }),
    downloadImage: async () => ({ bytes: png, contentType: 'image/png' }),
  });
  server = await startServer(0);
  const origin = `http://127.0.0.1:${server.address().port}`;
  const course = {
    courseId: 'fixture-course',
    classroomId: 'fixture-classroom',
    name: 'Fixture course',
  };
  const lesson = {
    lessonId: 'fixture-lesson',
    presentationId: 'fixture-presentation',
    name: 'Fixture lesson',
  };

  const missingDirectory = await fetch(`${origin}/api/rain/import`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ course, lesson }),
  });
  assert.equal(missingDirectory.status, 400);
  assert.match((await missingDirectory.json()).error, /导入目录/);

  await fs.mkdir(parent);
  const response = await fetch(`${origin}/api/rain/import`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ course, lesson, parent }),
  });
  assert.equal(response.status, 200);
  const project = await response.json();
  assert.equal(
    project.projectRoot,
    path.join(parent, 'Fixture course - Fixture lesson'),
  );
  assert.ok(await fs.stat(path.join(project.projectRoot, 'project.pagenote')));
  assert.ok(
    await fs.stat(path.join(project.projectRoot, 'src', 'document.pdf')),
  );
  console.log('Yuketang import destination route tests passed');
} finally {
  if (server) await new Promise((resolve) => server.close(resolve));
  await fs.rm(root, { recursive: true, force: true });
}

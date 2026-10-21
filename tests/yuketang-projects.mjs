import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createProjectStore } from '../projects.mjs';

const root = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-yuketang-'));
const store = createProjectStore({ app: { post() {} }, data: root });
const bytes = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2WfQAAAAASUVORK5CYII=',
  'base64',
);
const page = {
  pageId: 'shared-slide-id',
  sourceIndex: 0,
  contentType: 'image/png',
  bytes,
};
const source = {
  courseId: 'fixture-course-a',
  classroomId: 'fixture-classroom-a',
  lessonId: 'fixture-lesson-a',
  presentationId: 'fixture-presentation-shared',
};

try {
  const project = await store.createRainProject({
    name: 'Rain project',
    parent: path.join(root, 'projects'),
    source,
    pages: [page],
  });

  const duplicate = await store.appendRainSlide({
    id: project.id,
    source,
    page,
  });
  assert.deepEqual(duplicate, { added: false, page: 1 });

  const otherClass = {
    ...source,
    courseId: 'fixture-course-b',
    classroomId: 'fixture-classroom-b',
    lessonId: 'fixture-lesson-b',
  };
  const appended = await store.appendRainSlide({
    id: project.id,
    source: otherClass,
    page,
  });
  assert.equal(appended.added, true);
  assert.equal(appended.page, 2);

  const manifestPath = path.join(
    project.projectRoot,
    'src',
    'yuketang-source.json',
  );
  const manifest = JSON.parse(await fs.readFile(manifestPath, 'utf8'));
  assert.deepEqual(
    manifest.pages.map(({ courseId, classroomId, lessonId }) => ({
      courseId,
      classroomId,
      lessonId,
    })),
    [
      {
        courseId: 'fixture-course-a',
        classroomId: 'fixture-classroom-a',
        lessonId: 'fixture-lesson-a',
      },
      {
        courseId: 'fixture-course-b',
        classroomId: 'fixture-classroom-b',
        lessonId: 'fixture-lesson-b',
      },
    ],
  );

  const legacyProject = await store.createRainProject({
    name: 'Legacy project',
    parent: path.join(root, 'projects'),
    source,
    pages: [page],
  });
  const legacyManifestPath = path.join(
    legacyProject.projectRoot,
    'src',
    'yuketang-source.json',
  );
  const legacyManifest = JSON.parse(
    await fs.readFile(legacyManifestPath, 'utf8'),
  );
  for (const key of ['courseId', 'classroomId', 'lessonId'])
    delete legacyManifest.pages[0][key];
  await fs.writeFile(legacyManifestPath, JSON.stringify(legacyManifest));

  await store.appendRainSlide({
    id: legacyProject.id,
    source,
    page: { ...page, pageId: 'new-slide-id', sourceIndex: 1 },
  });
  const migrated = JSON.parse(await fs.readFile(legacyManifestPath, 'utf8'));
  assert.deepEqual(
    {
      courseId: migrated.pages[0].courseId,
      classroomId: migrated.pages[0].classroomId,
      lessonId: migrated.pages[0].lessonId,
    },
    {
      courseId: source.courseId,
      classroomId: source.classroomId,
      lessonId: source.lessonId,
    },
  );
  console.log('Yuketang project source identity tests passed');
} finally {
  await fs.rm(root, { recursive: true, force: true });
}

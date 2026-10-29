import { _electron as electron } from '@playwright/test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { dismissWelcome } from './dismiss-welcome.mjs';

const root = path.resolve(import.meta.dirname, '..');
const temporary = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-rain-ui-'));
const parent = path.join(temporary, 'library');
await fs.mkdir(parent);
const executablePath =
  process.env.DESKTOP_EXE ||
  path.join(root, 'release/mac-arm64/pagenote.app/Contents/MacOS/pagenote');
const app = await electron.launch({
  executablePath,
  env: {
    ...process.env,
    PAGE_NOTES_USER_DATA: path.join(temporary, 'profile'),
    PAGE_NOTES_DATA: path.join(temporary, 'data'),
  },
});
try {
  const page = await app.firstWindow();
  await page.waitForFunction(() => Boolean(window.pageNotes));
  await dismissWelcome(page);
  await page.evaluate(() => window.pageNotes.setLanguage('zh-CN'));
  await page.evaluate(async (parent) => {
    await fetch('/api/bookshelf/config', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ root: parent }),
    });
    await window.pageNotes.showBookshelf();
    const response = await fetch('/api/projects', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Existing notebook' }),
    });
    const project = await response.json();
    await window.pageNotes.openProject(project.projectFile);
  }, parent);
  await app.evaluate(async ({ app, dialog }, parent) => {
    const path = process.getBuiltinModule('node:path');
    const { pathToFileURL } = process.getBuiltinModule('node:url');
    const vm = process.getBuiltinModule('node:vm');
    const backend = await vm.runInThisContext(
      `import(${JSON.stringify(pathToFileURL(path.join(app.getAppPath(), 'server.mjs')).href)})`,
      { importModuleDynamically: vm.constants.USE_MAIN_CONTEXT_DEFAULT_LOADER },
    );
    const image = Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2WfQAAAAASUVORK5CYII=',
      'base64',
    );
    const course = {
      courseId: 'course',
      classroomId: 'room',
      name: 'Fixture course',
    };
    const lesson = {
      lessonId: 'lesson',
      classroomId: 'room',
      name: 'Fixture lesson',
    };
    let callbacks;
    let state = { status: 'stopped', pendingSlides: 0 };
    backend.configureRainClassroomService({
      status: async () => ({
        authenticated: true,
        user: { name: 'Fixture account' },
      }),
      listCourses: async () => [course],
      listCurrentLessons: async () => [lesson],
      listLessons: async () => [],
      getLiveStatus: () => state,
      startLive: async (value) => {
        callbacks = value;
        state = { status: 'active', pendingSlides: 0 };
        return state;
      },
      stopLive: async () => {
        state = { ...state, status: 'stopped' };
        return state;
      },
      downloadImage: async () => ({ bytes: image, contentType: 'image/png' }),
    });
    dialog.showOpenDialog = async () => {
      throw Error('Bound library should not prompt for another directory');
    };
    globalThis.emitRainFixtureSlide = async (index) => {
      const written = await callbacks.onSlide({
        presentationId: 'deck',
        pageId: `slide-${index}`,
        sourceIndex: index,
        imageUrl: 'https://cdn.yuketang.cn/fixture.png',
      });
      state = {
        ...state,
        latest: {
          presentationId: 'deck',
          sourceIndex: index,
          projectPage: written.page,
          projectId: written.projectId,
          added: written.added,
        },
      };
    };
  }, parent);
  await page.evaluate(() => window.pageNotes.menuAction('rainClassroom'));
  await page.locator('#rainLoadCourses').click();
  await page.locator('#rainCurrentLesson').selectOption('0');
  await page.locator('#rainProjectName').fill('Live UI notebook');
  assert.ok(await page.locator('#rainLiveNew').isDisabled());
  assert.ok(await page.locator('#rainLiveStart').isDisabled());
  await page.locator('#rainLiveConsent').check();
  await page.locator('#rainLiveNew').click();
  await page.waitForFunction(() =>
    document.querySelector('#rainLiveStatus').textContent.includes('采集中'),
  );
  assert.deepEqual(await fs.readdir(parent), ['Existing notebook']);
  await app.evaluate(() => globalThis.emitRainFixtureSlide(0));
  await page.waitForFunction(() =>
    window.pageNotes
      .getBookshelfState()
      .books.some((book) => book.name === 'Live UI notebook'),
  );
  assert.equal(
    (await page.evaluate(() => window.pageNotes.getState())).document.name,
    'Existing notebook',
    'first slide must not interrupt existing reader',
  );
  await page.locator('#rainClose').click();
  await page.locator('#bookshelfButton').click();
  await page.locator('.book-title', { hasText: 'Live UI notebook' }).click();
  await page.locator('#bookshelf').waitFor({ state: 'hidden' });
  await page.evaluate(() => {
    const editor = document.getElementById('editor');
    editor.value = 'Keep my live page note';
    editor.dispatchEvent(new Event('input', { bubbles: true }));
  });
  await app.evaluate(() => globalThis.emitRainFixtureSlide(1));
  await page.waitForFunction(() =>
    document.querySelector('#pageCount').textContent.includes('2'),
  );
  assert.equal(
    await page.locator('#editor').inputValue(),
    'Keep my live page note',
  );
  await page.evaluate(() => window.pageNotes.flush());
  assert.match(
    await fs.readFile(
      path.join(parent, 'Live UI notebook/src/right.md'),
      'utf8',
    ),
    /^Keep my live page note/,
  );
  await page.evaluate(() => window.pageNotes.menuAction('rainClassroom'));
  await page.locator('#rainLiveStop').click();
  await page.screenshot({
    path: path.join(root, 'release/rain-desktop-ui.png'),
  });
  console.log(
    'Packaged desktop Rain UI: consent, new project, two slides and note preservation passed',
  );
} finally {
  await app.close();
  await fs.rm(temporary, { recursive: true, force: true });
}

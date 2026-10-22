import { _electron as electron } from '@playwright/test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { createHash } from 'node:crypto';
import { dismissWelcome } from './dismiss-welcome.mjs';
const root = path.resolve(import.meta.dirname, '..');
const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-shelf-ui-'));
const library = path.join(tmp, 'library');
for (const [name, pdf] of [
  ['Notebook', false],
  ['Lecture PDF', true],
]) {
  const dir = path.join(library, name);
  await fs.mkdir(path.join(dir, 'src'), { recursive: true });
  if (pdf)
    await fs.copyFile(
      path.join(root, 'tests/fixtures/sample.pdf'),
      path.join(dir, 'src/left.pdf'),
    );
  else await fs.writeFile(path.join(dir, 'src/left.md'), '# Notebook');
  await fs.writeFile(path.join(dir, 'src/right.md'), 'Original explanation');
  await fs.writeFile(
    path.join(dir, 'project.pagenote'),
    JSON.stringify({
      format: 'pagenote-project',
      version: 1,
      id: createHash('sha256').update(name).digest('hex'),
      name,
      left: 'src/left.' + (pdf ? 'pdf' : 'md'),
      right: 'src/right.md',
    }),
  );
}
const executablePath =
  process.env.DESKTOP_EXE ||
  path.join(
    root,
    'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron',
  );
const launch = () =>
  electron.launch({
    executablePath,
    args: process.env.DESKTOP_EXE ? [] : [root],
    env: {
      ...process.env,
      PAGE_NOTES_USER_DATA: path.join(tmp, 'profile'),
      PAGE_NOTES_DATA: path.join(tmp, 'data'),
    },
  });
let app;
let w;
try {
  app = await launch();
  w = await app.firstWindow();
  w.setDefaultTimeout(10000);
  await dismissWelcome(w);
  await w.locator('#bookshelf').waitFor({ timeout: 5000 });
  assert.ok(await w.locator('#shelfBind').isVisible());
  await app.evaluate(async ({ BrowserWindow, dialog }, root) => {
    const original = dialog.showOpenDialog;
    dialog.showOpenDialog = async () => ({
      canceled: false,
      filePaths: [root],
    });
    try {
      await BrowserWindow.getAllWindows()[0].webContents.executeJavaScript(
        'window.pageNotes.chooseBookshelfDirectory()',
      );
    } finally {
      dialog.showOpenDialog = original;
    }
  }, library);
  await w.locator('[data-book-key]').first().waitFor();
  assert.equal(await w.locator('[data-book-key]').count(), 2);
  await w.locator('.book-cover img').first().waitFor();
  await w.waitForFunction(() =>
    [...document.querySelectorAll('.book-cover img')].some(
      (im) => im.complete && im.naturalWidth > 0,
    ),
  );
  assert.ok(
    await w
      .locator('.book-cover img')
      .first()
      .evaluate((image) => {
        const canvas = document.createElement('canvas');
        canvas.width = image.naturalWidth;
        canvas.height = image.naturalHeight;
        const ctx = canvas.getContext('2d');
        ctx.drawImage(image, 0, 0);
        return ctx
          .getImageData(0, 0, canvas.width, canvas.height)
          .data.some((value, index) => index % 4 !== 3 && value < 180);
      }),
    'PDF thumbnail contains actual page pixels',
  );
  assert.ok(await w.getByText('Note', { exact: true }).isVisible());
  await w.locator('#shelfSearch').fill('Notebook');
  assert.equal(await w.locator('[data-book-key]').count(), 1);
  await w.locator('#shelfSearch').fill('');
  await w.locator('#shelfView').click();
  await w.waitForFunction(
    () => document.getElementById('shelfGrid').dataset.view === 'list',
  );
  assert.equal(await w.locator('#shelfGrid').getAttribute('data-view'), 'list');
  await w.locator('#shelfView').click();
  await w.waitForFunction(
    () => document.getElementById('shelfGrid').dataset.view === 'grid',
  );
  const notebook = w
    .locator('.shelf-book')
    .filter({ has: w.locator('.book-title', { hasText: 'Notebook' }) });
  await notebook.locator('.book-more').click();
  await w.locator('#bookTitle').fill('My notebook');
  await w.locator('[data-paper=rose]').click();
  await w.locator('#bookSave').click();
  await w.locator('#bookAppearance').waitFor({ state: 'hidden' });
  await w.locator('[data-book-key]').filter({ hasText: 'Note' }).click();
  await w.locator('#bookshelf').waitFor({ state: 'hidden' });
  await w.route('**/api/documents/*/notes/*', (route) =>
    route.request().method() === 'PUT'
      ? route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ error: 'Simulated save failure' }),
        })
      : route.continue(),
  );
  await w.evaluate(() => {
    const editor = document.getElementById('editor');
    editor.value = 'Saved by bookshelf return';
    editor.dispatchEvent(new Event('input', { bubbles: true }));
  });
  await w.locator('#bookshelfButton').click();
  await w.waitForFunction(() =>
    document
      .getElementById('toast')
      .textContent.includes('Simulated save failure'),
  );
  assert.ok(
    await w.locator('#bookshelf').isHidden(),
    'failed save must not leave reader',
  );
  assert.equal(
    (await w.evaluate(() => window.pageNotes.getState())).markdown,
    'Saved by bookshelf return',
  );
  await w.unroute('**/api/documents/*/notes/*');
  await w.locator('#bookshelfButton').click();
  await w.locator('#bookshelf').waitFor();
  assert.equal(
    await fs.readFile(path.join(library, 'Notebook/src/right.md'), 'utf8'),
    'Saved by bookshelf return',
  );
  const notebookId = (
    await w.evaluate(() => window.pageNotes.getBookshelfState())
  ).books.find((book) => book.name === 'Notebook').id;
  await w.locator('#shelfRecent').selectOption(notebookId);
  await w.locator('#bookshelf').waitFor({ state: 'hidden' });
  assert.equal(
    (await w.evaluate(() => window.pageNotes.getState())).markdown,
    'Saved by bookshelf return',
  );
  await w.locator('#bookshelfButton').click();
  await w.locator('#bookshelf').waitFor();
  const mirror = path.join(library, 'Mirror');
  await fs.cp(path.join(library, 'Notebook'), mirror, { recursive: true });
  const sameId = JSON.parse(
    await fs.readFile(path.join(mirror, 'project.pagenote'), 'utf8'),
  );
  sameId.name = 'Mirror';
  await fs.writeFile(
    path.join(mirror, 'project.pagenote'),
    JSON.stringify(sameId),
  );
  await fs.rm(path.join(mirror, 'src/bookshelf.json'));
  await fs.writeFile(path.join(mirror, 'src/right.md'), 'Separate mirror note');
  await w.evaluate(() => window.pageNotes.showBookshelf());
  await w.locator('.book-title', { hasText: 'Mirror' }).click();
  await w.locator('#bookshelf').waitFor({ state: 'hidden' });
  assert.equal(
    (await w.evaluate(() => window.pageNotes.getState())).markdown,
    'Separate mirror note',
  );
  await w.evaluate(() => {
    const editor = document.getElementById('editor');
    editor.value = 'Edited mirror only';
    editor.dispatchEvent(new Event('input', { bubbles: true }));
  });
  await w.locator('#bookshelfButton').click();
  await w.locator('#bookshelf').waitFor();
  await w.locator('.book-title', { hasText: 'My notebook' }).click();
  await w.locator('#bookshelf').waitFor({ state: 'hidden' });
  assert.equal(
    (await w.evaluate(() => window.pageNotes.getState())).markdown,
    'Saved by bookshelf return',
  );
  assert.equal(
    await fs.readFile(path.join(mirror, 'src/right.md'), 'utf8'),
    'Edited mirror only',
  );
  await w.locator('#bookshelfButton').click();
  await w.evaluate(() => window.pageNotes.setLanguage('en-US'));
  assert.ok(
    await w.locator('#shelfSort').evaluate((select) => {
      const ctx = document.createElement('canvas').getContext('2d');
      ctx.font = getComputedStyle(select).font;
      return (
        select.clientWidth >=
        ctx.measureText(select.selectedOptions[0].textContent).width + 26
      );
    }),
    'English sort label fits without clipping',
  );
  assert.ok(
    await w.locator('.book-title', { hasText: 'My notebook' }).isVisible(),
  );
  await w.evaluate(() => window.pageNotes.setTheme('dark'));
  await w.waitForFunction(() =>
    [...document.querySelectorAll('.book-cover img')].some(
      (image) => image.complete && image.naturalWidth > 0,
    ),
  );
  await w.screenshot({
    path: path.join(root, 'release/bookshelf-desktop-dark.png'),
  });
  await w.evaluate(() => window.pageNotes.setTheme('light'));
  await w.evaluate(() => window.pageNotes.setLanguage('zh-CN'));
  await w
    .locator('#shelfFileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.locator('#bookshelf').waitFor({ state: 'hidden' });
  await w.waitForFunction(
    async () => (await window.pageNotes.getState()).document?.name === 'sample',
  );
  await w.evaluate(async () => {
    const state = await window.pageNotes.getState();
    await window.pageNotes.open(state.document.id, 2);
  });
  await w.locator('#bookshelfButton').click();
  await w.locator('#bookshelf').waitFor();
  await w.locator('.book-title', { hasText: /^sample$/ }).click();
  await w.waitForFunction(
    async () => (await window.pageNotes.getState()).page === 2,
  );
  await w.locator('#bookshelfButton').click();
  await w.locator('#shelfNew').click();
  await w.locator('#newName').fill('New shelf book');
  await w.locator('#newConfirm').click();
  await w.locator('#newConfirm').waitFor({ state: 'hidden' });
  await w.locator('#bookshelfButton').click();
  await w.locator('.book-title', { hasText: 'New shelf book' }).waitFor();
  await fs.mkdir(path.join(root, 'release'), { recursive: true });
  await w.setViewportSize({ width: 1440, height: 960 });
  await w.screenshot({
    path: path.join(root, 'release/bookshelf-desktop.png'),
  });
  await w.setViewportSize({ width: 900, height: 620 });
  assert.equal(
    await w.evaluate(() => document.documentElement.scrollWidth > innerWidth),
    false,
  );
  await w.screenshot({
    path: path.join(root, 'release/bookshelf-desktop-compact.png'),
  });
  await app.close();
  app = await launch();
  w = await app.firstWindow();
  await dismissWelcome(w);
  await w.locator('#bookshelf').waitFor();
  await w.locator('.book-title', { hasText: 'New shelf book' }).waitFor();
  console.log(
    'PASS bookshelf startup, nonblank PDF covers, customization, save-failure protection, duplicate-ID isolation, themes, import, reading positions, creation and restart',
  );
} catch (error) {
  if (w && !w.isClosed())
    console.error(
      'Bookshelf failure:',
      await w.locator('#toast').textContent(),
      await w.locator('#shelfStatus').textContent(),
    );
  throw error;
} finally {
  if (app) await app.close();
  await fs.rm(tmp, { recursive: true, force: true });
}

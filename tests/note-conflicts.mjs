import { _electron as electron, expect } from '@playwright/test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const profile = path.join(root, 'tests', 'note-conflict-data-' + Date.now());
const app = await electron.launch({
  executablePath:
    process.env.DESKTOP_EXE ||
    path.join(
      root,
      process.platform === 'darwin'
        ? 'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron'
        : 'node_modules/electron/dist/electron.exe',
    ),
  args: process.env.DESKTOP_EXE ? [] : [root],
  env: { ...process.env, PAGE_NOTES_USER_DATA: profile },
  timeout: 40000,
});
let release = () => {};
try {
  const w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await w.evaluate(() => window.pageNotes.ready);
  await w.evaluate(() => window.pageNotes.setLanguage('zh-CN'));
  await w.evaluate(() => window.pageNotes.setLayout({ expandEditor: false }));
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.md'));
  await w.locator('#loading').waitFor({ state: 'hidden' });
  const id = (await w.evaluate(() => window.pageNotes.getState())).document.id;
  const origin = new URL(w.url()).origin;
  const url = (p = 1) => `${origin}/api/documents/${id}/notes/${p}`;
  const put = (markdown, expectedRevision, p = 1) =>
    fetch(url(p), {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ markdown, expectedRevision }),
    });
  const hash = (text) => createHash('sha256').update(text).digest('hex');
  const disk = async () =>
    (await (await fetch(`${origin}/api/documents/${id}/notes`)).json())['1'];
  const edit = async (text) => {
    if ((await w.evaluate(() => window.pageNotes.getState())).mode !== 'edit') {
      await w.evaluate(() => window.pageNotes.menuAction('chooseEditor'));
      await w.locator('.note-pane .pane-heading').click();
    }
    await w.locator('#preview').fill(text);
    await w.waitForFunction(
      (text) => document.querySelector('#editor').value.includes(text),
      text,
    );
  };
  const saveSoon = () =>
    w.evaluate(() => {
      window.saveTestError = null;
      window.saveTestPromise = window.pageNotes.flush().catch((e) => {
        window.saveTestError = e.message;
      });
    });
  const waitSaved = async () => {
    await expect(w.locator('#saveStatus')).toHaveText(
      /已保存到本地|Saved locally/,
    );
    await expect.poll(disk).toBe(await w.locator('#editor').inputValue());
  };
  const reloadSaved = async () => {
    await w.locator('#resolveNoteConflict').click();
    await expect(w.locator('#noteConflictDialog')).toBeVisible();
    await w.locator('#reloadConflictSaved').click();
    await expect(w.locator('#noteConflictDialog')).toBeHidden();
  };
  assert.equal((await put('Initial saved note')).status, 200);
  await w.evaluate((id) => window.pageNotes.syncNotes(id), id);

  // Two independent writers share one base. Exactly one may commit.
  const race = await Promise.all([
    put('Writer A', hash(''), 2),
    put('Writer B', hash(''), 2),
  ]);
  assert.deepEqual(race.map((r) => r.status).sort(), [200, 409]);
  const rejected = await race.find((r) => r.status === 409).json();
  assert.equal(rejected.code, 'NOTE_CONFLICT');
  const archive = await (
    await fetch(
      `${origin}/api/documents/${id}/notes/2/history/${rejected.draftVersion}`,
    )
  ).json();
  assert.ok(['Writer A', 'Writer B'].includes(archive.markdown));
  assert.notEqual(archive.markdown, rejected.currentMarkdown);
  assert.equal((await put('Invalid condition', 'bad')).status, 400);

  // Delay a user save while another writer commits. Sync must retain the editor.
  let started;
  const start = new Promise((r) => {
    started = r;
  });
  const gate = new Promise((r) => {
    release = r;
  });
  const routePattern = '**/api/documents/' + id + '/notes/1';
  const delayed = async (route) => {
    started();
    await gate;
    await route.continue();
  };
  await w.route(routePattern, delayed);
  await edit('My local draft 草稿');
  await saveSoon();
  await start;
  assert.equal((await put('Newer saved note')).status, 200);
  await w.evaluate((id) => window.pageNotes.syncNotes(id), id);
  assert.ok(
    (await w.locator('#editor').inputValue()).includes('My local draft'),
  );
  release();
  await expect(w.locator('#resolveNoteConflict')).toBeVisible();
  await w.unroute(routePattern, delayed);
  assert.equal(await disk(), 'Newer saved note');
  await w.locator('#resolveNoteConflict').click();
  await expect(w.locator('#noteConflictSaved')).toHaveValue('Newer saved note');
  assert.ok(
    (await w.locator('#noteConflictDraft').inputValue()).includes(
      'My local draft',
    ),
  );
  await w.screenshot({
    path: path.join(root, 'tests/note-conflict-dialog.png'),
  });
  await w.locator('#reloadConflictSaved').click();
  await expect(w.locator('#noteConflictDialog')).toBeHidden();
  const history = await (await fetch(url() + '/history')).json();
  const versions = await Promise.all(
    history.versions.map(
      async (v) =>
        (await (await fetch(url() + '/history/' + v.id)).json()).markdown,
    ),
  );
  assert.ok(versions.some((text) => text.includes('My local draft')));
  // Resolving a rejected save must unblock subsequent navigation and editing.
  await w.evaluate((id) => window.pageNotes.open(id, 2), id);
  await w.evaluate((id) => window.pageNotes.open(id, 1), id);
  await edit('Saved after conflict');
  await waitSaved();

  // Rapid edits queue behind a held request and must use its acknowledged base.
  let queueStarted;
  const queueStart = new Promise((r) => {
    queueStarted = r;
  });
  const queueGate = new Promise((r) => {
    release = r;
  });
  let first = true;
  const queued = async (route) => {
    if (first) {
      first = false;
      queueStarted();
      await queueGate;
    }
    await route.continue();
  };
  await w.route(routePattern, queued);
  await edit('Queued first');
  await saveSoon();
  await queueStart;
  await edit('Queued second');
  await saveSoon();
  release();
  await waitSaved();
  await w.unroute(routePattern, queued);
  assert.equal(await w.evaluate(() => window.saveTestError), null);

  // HTTP failure preserves the draft; explicit retry succeeds without conflict.
  const failure = (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: '{"error":"Temporary save failure"}',
    });
  await w.route(routePattern, failure);
  await edit('Retry this draft');
  await expect(w.locator('#retryNoteSave')).toBeVisible();
  await w.unroute(routePattern, failure);
  await w.locator('#retryNoteSave').click();
  await waitSaved();

  // A real failed draft survives renderer restart and must not rebase on a new disk version.
  await w.route(routePattern, failure);
  await edit('Recovered conflict draft');
  await expect(w.locator('#retryNoteSave')).toBeVisible();
  assert.equal((await put('Disk changed during restart')).status, 200);
  await w.unroute(routePattern, failure);
  // Force renderer reload only in this isolated profile, as a crash recovery test.
  const main = await app.browserWindow(w);
  await main.evaluate((window) =>
    window.webContents.once('will-prevent-unload', (event) =>
      event.preventDefault(),
    ),
  );
  await w.reload();
  await w.waitForFunction(() => !!window.pageNotes);
  await w.evaluate(() => window.pageNotes.ready);
  await expect(w.locator('#resolveNoteConflict')).toBeVisible();
  await expect(w.locator('#saveStatus')).toHaveText(
    '保存冲突 · 草稿已备份到历史',
  );
  assert.ok(
    (await w.locator('#editor').inputValue()).includes(
      'Recovered conflict draft',
    ),
  );
  assert.equal(await disk(), 'Disk changed during restart');
  await reloadSaved();
  assert.equal(
    await w.evaluate((id) => localStorage.getItem(`page-notes:${id}:1`), id),
    null,
  );

  // Old installations stored only draft text: unknown bases require review too.
  await w.evaluate((id) => {
    localStorage.setItem(`page-notes:${id}:1`, 'Legacy draft');
    localStorage.removeItem(`page-notes:${id}:1:base`);
  }, id);
  await w.reload();
  await w.waitForFunction(() => !!window.pageNotes);
  await w.evaluate(() => window.pageNotes.ready);
  await expect(w.locator('#resolveNoteConflict')).toBeVisible();
  assert.equal(await disk(), 'Disk changed during restart');
  await reloadSaved();
  // A full recovery cache must not replace the newest editor text with old cache data.
  await w.evaluate((id) => {
    localStorage.setItem(`page-notes:${id}:1`, 'Stale cached text');
    window.originalStorageSet = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key, value) {
      if (key.startsWith('page-notes:'))
        throw new DOMException('Full recovery cache', 'QuotaExceededError');
      return window.originalStorageSet.call(this, key, value);
    };
  }, id);
  await w.route(routePattern, failure);
  await edit('Newest draft despite full recovery cache');
  await expect(w.locator('#retryNoteSave')).toBeVisible();
  await expect(w.locator('#saveStatus')).toHaveText(
    '保存失败 · 草稿仍在编辑区，请导出备份',
  );
  await w.unroute(routePattern, failure);
  await w.locator('#retryNoteSave').click();
  await waitSaved();
  assert.ok(
    (await disk()).includes('Newest draft despite full recovery cache'),
  );
  assert.equal(
    await w.evaluate((id) => localStorage.getItem(`page-notes:${id}:1`), id),
    null,
  );
  await w.evaluate(() => {
    Storage.prototype.setItem = window.originalStorageSet;
  });
  await w.evaluate(() => window.pageNotes.setLanguage('en-US'));
  await edit('English saved note');
  await waitSaved();
  await expect(w.locator('#saveStatus')).toHaveText('Saved locally');
  await w.screenshot({
    path: path.join(root, 'tests/note-conflicts-results.png'),
  });
  console.log(
    'PASS atomic conditional writes, archived conflicts, pending-sync preservation, queued edits, retry, recovered/legacy drafts and post-resolution navigation',
  );
} catch (error) {
  console.error(error);
  await app.evaluate(({ app }) => app.exit()).catch(() => {});
  throw error;
} finally {
  release();
  await app.close().catch(() => {});
}

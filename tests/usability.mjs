import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron } from '@playwright/test';
import path from 'node:path';
import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
const root = path.resolve(import.meta.dirname, '..');
const executable =
  process.env.DESKTOP_EXE ||
  path.join(
    root,
    process.platform === 'darwin'
      ? 'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron'
      : 'node_modules/electron/dist/electron.exe',
  );

const app = await electron.launch({
  executablePath: executable,
  args: [root],
  env: {
    ...process.env,
    PAGE_NOTES_USER_DATA: path.join(root, 'tests/usability-data'),
  },
  timeout: 40000,
});
try {
  const w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.locator('#loading').waitFor({ state: 'hidden' });
  const state = await w.evaluate(() => window.pageNotes.getState());
  await w.evaluate((id) => window.pageNotes.open(id, 1), state.document.id);
  const first = '# 恢复前的早期笔记\n\n$$\\mu=18$$',
    second = '# 修改后的笔记\n\n需要保留这个版本。';
  await w.locator('[data-mode="edit"]').click();
  await w.locator('#editor').fill(first);
  await w.evaluate(() => window.pageNotes.flush());
  await w.locator('#editor').fill(second);
  await w.evaluate(() => window.pageNotes.flush());
  const route = `/api/documents/${state.document.id}/notes/1`;
  const history = await w.evaluate(async (route) => {
    const info = await (await fetch(route + '/history')).json();
    for (const v of info.versions)
      v.markdown = (
        await (await fetch(route + '/history/' + v.id)).json()
      ).markdown;
    return info;
  }, route);
  assert.ok(history.versions.some((v) => v.markdown === first));
  assert.ok(history.versions.some((v) => v.markdown === second));
  const beforeCount = history.versions.length;
  await w.evaluate(() => window.pageNotes.flush());
  assert.equal(
    await w.evaluate(
      async (r) => (await (await fetch(r + '/history')).json()).versions.length,
      route,
    ),
    beforeCount,
    'unchanged saves do not duplicate history',
  );
  await w.locator('#historyButton').click();
  await w
    .locator('#historyList')
    .selectOption(history.versions.find((v) => v.markdown === first).id);
  await w.waitForFunction(
    () =>
      document.querySelector('#historyPreview h1')?.textContent ===
      '恢复前的早期笔记',
  );
  w.once('dialog', (dialog) => dialog.accept());
  await w.locator('#restoreHistory').click();
  await w.locator('#historyDialog').waitFor({ state: 'hidden' });
  assert.equal(await w.locator('#editor').inputValue(), first);
  const after = await w.evaluate(async (r) => {
    const info = await (await fetch(r + '/history')).json();
    for (const v of info.versions)
      v.markdown = (
        await (await fetch(r + '/history/' + v.id)).json()
      ).markdown;
    return info;
  }, route);
  assert.ok(
    after.versions.some((v) => v.markdown === second),
    'recovery retains overwritten content',
  );
  const conflict = await w.evaluate(
    async ({ route, version }) => {
      const r = await fetch(route + '/restore', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ version, expectedRevision: '0'.repeat(64) }),
      });
      return { status: r.status, body: await r.json() };
    },
    { route, version: history.versions[0].id },
  );
  assert.equal(conflict.status, 400);
  assert.ok(conflict.body.error.includes('发生变化'));
  await w.reload();
  await dismissWelcome(w);
  assert.equal(
    await w.locator('#editor').inputValue(),
    first,
    'restoration persists',
  );
  await app.evaluate(({ BrowserWindow }) =>
    BrowserWindow.getAllWindows()
      .find((w) => w.isVisible())
      .setContentSize(900, 650),
  );
  await w.waitForTimeout(300);
  const fit = await w
    .locator('.ribbon button,.ribbon select')
    .evaluateAll((es) =>
      es.every((e) => {
        const r = e.getBoundingClientRect();
        return (
          r.x >= 0 &&
          r.right <= innerWidth &&
          r.bottom <
            document.querySelector('#workspace').getBoundingClientRect().top + 1
        );
      }),
    );
  assert.ok(fit, 'commands fit at 900px');
  assert.ok(
    await w
      .locator('#noteScroll')
      .evaluate((e) => e.scrollWidth <= e.clientWidth + 1),
    'notes stay within horizontal boundary',
  );
  assert.ok(
    await w
      .locator('#documentScroll')
      .evaluate((e) => e.scrollWidth <= e.clientWidth + 1),
    'fit document has no horizontal overflow',
  );
  await app.evaluate(({ BrowserWindow }) =>
    BrowserWindow.getAllWindows()
      .find((w) => w.isVisible())
      .setContentSize(1440, 950),
  );
  await w.waitForTimeout(300);
  await w.locator('[data-mode="read"]').click();
  await w.evaluate(() => window.pageNotes.waitVisuals());
  const screenshot = await app.evaluate(async ({ BrowserWindow }) =>
    (
      await BrowserWindow.getAllWindows()
        .find((w) => w.isVisible())
        .webContents.capturePage()
    )
      .toPNG()
      .toString('base64'),
  );
  await fs.writeFile(
    path.join(root, 'tests/usability-preview.png'),
    Buffer.from(screenshot, 'base64'),
  );
  const rect = await w.locator('.command-area').evaluate((e) => {
    const r = e.getBoundingClientRect();
    return {
      x: 0,
      y: Math.floor(r.top),
      width: innerWidth,
      height: Math.ceil(r.height),
    };
  });
  const toolbar = await app.evaluate(
    async ({ BrowserWindow }, rect) =>
      (
        await BrowserWindow.getAllWindows()
          .find((w) => w.isVisible())
          .webContents.capturePage(rect)
      )
        .toPNG()
        .toString('base64'),
    rect,
  );
  await fs.writeFile(
    path.join(root, 'tests/ribbon-preview.png'),
    Buffer.from(toolbar, 'base64'),
  );
  console.log(
    'PASS: automatic version backup; preview and recovery; preserves prior notes; conflict protection; restart persistence; 900px command layout; bounded note paper.',
  );
} finally {
  await app.close();
}

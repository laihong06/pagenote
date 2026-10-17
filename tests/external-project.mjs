import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const root = path.resolve(import.meta.dirname, '..'),
  profile = path.join(root, 'tests', 'external-project-data-' + Date.now()),
  executable =
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
  env: { ...process.env, PAGE_NOTES_USER_DATA: profile },
});
try {
  const w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await app.evaluate(({ shell }) => {
    shell.openPath = async (file) => {
      globalThis.testExternalFile = file;
      return '';
    };
  });
  await w.evaluate(() => window.pageNotes.menuAction('newProject'));
  await w.locator('#newName').fill('External project');
  await w.locator('#newParent').fill(path.join(profile, 'projects'));
  await w.locator('#newConfirm').click();
  await w.locator('#newConfirm').waitFor({ state: 'hidden' });
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.locator('#loading').waitFor({ state: 'hidden' });
  const before = await w.evaluate(() => window.pageNotes.getState());
  await w.evaluate(async (id) => {
    await fetch('/api/documents/' + id + '/notes/1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ markdown: 'Keep project notes' }),
    });
    await window.pageNotes.syncNotes(id);
  }, before.document.id);
  await w.evaluate(() => window.pageNotes.menuAction('chooseEditor'));
  await w.locator('.document-pane .pane-heading').click();
  await w.waitForFunction(
    () =>
      document.querySelector('#toast').textContent.includes('默认应用') ||
      document.querySelector('#toast').textContent.includes('default app'),
  );
  const file = await app.evaluate(() => globalThis.testExternalFile);
  assert.equal(file, path.join(before.document.projectRoot, 'src', 'left.pdf'));
  await fs.appendFile(file, '\n% External save\n');
  await w.evaluate(() => window.pageNotes.refreshExternalSource());
  const after = await w.evaluate(() => window.pageNotes.getState());
  assert.equal(after.document.id, before.document.id);
  assert.ok(after.markdown.includes('Keep project notes'));
  assert.ok(after.sourceText.includes('Pattern Recognition'));
  assert.ok(
    (
      await fs.readFile(
        path.join(after.document.projectRoot, 'src', 'right.md'),
        'utf8',
      )
    ).includes('Keep project notes'),
  );
  console.log(
    'PASS external project edits target src link, saved PDF refreshes and project ID/right-side notes survive.',
  );
} finally {
  await app.close();
}

import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
const root = path.resolve(import.meta.dirname, '..'),
  profile = path.join(root, 'tests', 'default-word-data-' + Date.now()),
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
  timeout: 40000,
});
try {
  const w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.docx'));
  await w.locator('#loading').waitFor({ state: 'hidden', timeout: 120000 });
  const before = await w.evaluate(() => window.pageNotes.getState());
  await w.evaluate(async (id) => {
    await fetch('/api/documents/' + id + '/notes/1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        markdown: 'Notes must survive external editing.',
      }),
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
  assert.ok(
    await w.locator('#wordTools').isHidden(),
    'embedded editor is not used',
  );
  const file = path.join(before.document.projectRoot, 'src', 'source.docx');
  const result = await promisify(execFile)(
    'powershell.exe',
    [
      '-STA',
      '-NoProfile',
      '-File',
      path.join(root, 'tests/default-word-keyboard.ps1'),
      '-Document',
      file,
    ],
    { windowsHide: true, timeout: 45000 },
  );
  console.log(result.stdout.trim());
  await w.evaluate(() => window.pageNotes.refreshExternalSource());
  const after = await w.evaluate(() => window.pageNotes.getState());
  assert.ok(after.markdown.includes('Notes must survive external editing.'));
  assert.equal(after.document.id, before.document.id);
  assert.ok(after.document.modified, 'preview metadata updated after saving');
  assert.ok(!after.sourceText.includes('DELETEPROBE'));
  assert.ok(after.sourceText.includes('Feature'), 'Word table retained');
  console.log(
    'PASS default-app launch, no embedded Word, saved source refresh, table/notes/document-ID preservation.',
  );
} finally {
  await app.close();
}

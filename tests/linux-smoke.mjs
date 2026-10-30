import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron } from '@playwright/test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
const root = path.resolve(import.meta.dirname, '..');
const profile = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-linux-'));
let app;
try {
  app = await electron.launch({
    executablePath:
      process.env.DESKTOP_EXE ||
      path.join(root, 'node_modules/electron/dist/electron'),
    args: process.env.DESKTOP_EXE ? [] : [root],
    env: { ...process.env, PAGE_NOTES_USER_DATA: profile },
    timeout: 45000,
  });
  const page = await app.firstWindow();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.waitForFunction(() => !!window.pageNotes);
  await page.evaluate(() => window.pageNotes.ready);
  await dismissWelcome(page);
  await page
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await page.waitForFunction(() => !!window.pageNotes.getSummary().document);
  await page.locator('#loading').waitFor({ state: 'hidden' });
  await page.waitForFunction(
    () => document.querySelector('#pdfCanvas').width > 0,
  );
  const state = await page.evaluate(() => window.pageNotes.getState());
  assert.ok(state.document.id);
  const note = 'Linux 本地适配笔记验证';
  const saved = await page.evaluate(
    async ({ id, note }) => {
      const url = '/api/documents/' + id + '/notes/1';
      const result = await fetch(url, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ markdown: note }),
      });
      if (!result.ok) throw Error(await result.text());
      return (await fetch('/api/documents/' + id + '/notes')).json();
    },
    { id: state.document.id, note },
  );
  assert.ok(JSON.stringify(saved).includes(note));
  assert.deepEqual(errors, []);
  console.log(
    'PASS Ubuntu desktop startup, PDF rendering and persisted note readback',
  );
} finally {
  if (app) await app.close();
  await fs.rm(profile, { recursive: true, force: true });
}

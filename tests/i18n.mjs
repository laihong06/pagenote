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

const profile = path.join(root, 'tests', 'i18n-data-' + Date.now());
const launch = () =>
  electron.launch({
    executablePath: executable,
    args: [root],
    env: { ...process.env, PAGE_NOTES_USER_DATA: profile },
  });
let app = await launch();
try {
  let w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await w.evaluate(() => window.pageNotes.setLanguage('zh-CN'));
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.waitForFunction(() => !!window.pageNotes.getSummary().document);
  await w.locator('#loading').waitFor({ state: 'hidden' });
  await w.evaluate(async () => {
    const state = await window.pageNotes.getState();
    await fetch('/api/documents/' + state.document.id + '/notes/1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        markdown:
          '# 文件\n\n鼠标、抓手、修改意见是用户写下的内容。\n\n```python\nprint("中文")\n```',
      }),
    });
    await window.pageNotes.syncNotes(state.document.id);
  });
  const before = await w.evaluate(() => window.pageNotes.getState());
  await w.evaluate(() => window.pageNotes.setLanguage('en-US'));
  assert.equal(await w.locator('html').getAttribute('lang'), 'en-US');
  assert.equal(
    await w.locator('#editor').getAttribute('placeholder'),
    'Paste page notes. Markdown and LaTeX are supported.',
  );
  const labels = await app.evaluate(({ Menu }) =>
    Menu.getApplicationMenu().items.map((i) => i.label),
  );
  assert.ok(labels.includes('File'));
  assert.ok(labels.includes('AI Annotations'));
  await w.waitForFunction(() =>
    document
      .querySelector('#aiSharedTools [data-ai-pointer=hand]')
      .textContent.includes('Hand'),
  );
  const after = await w.evaluate(() => window.pageNotes.getState());
  assert.equal(
    after.markdown,
    before.markdown,
    'language changes must not translate user notes',
  );
  assert.equal(
    after.sourceText,
    before.sourceText,
    'source content remains unchanged',
  );
  assert.ok(
    (await w.locator('#preview').innerText()).includes(
      '修改意见是用户写下的内容',
    ),
  );
  assert.ok(await w.locator('#preview .hljs-built_in').count());
  await app.close();
  app = await launch();
  w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  assert.equal(await w.evaluate(() => window.pageNotes.getLanguage()), 'en-US');
  assert.equal(
    JSON.parse(
      await fs.readFile(
        path.join(profile, 'documents-data/preferences.json'),
        'utf8',
      ),
    ).language,
    'en-US',
  );
  await w.evaluate(() => window.pageNotes.setLanguage('zh-CN'));
  assert.ok(
    (
      await app.evaluate(({ Menu }) =>
        Menu.getApplicationMenu().items.map((i) => i.label),
      )
    ).includes('文件'),
  );
  console.log(
    'PASS bilingual UI/native menus, restart persistence, user Markdown/source preservation and code highlighting.',
  );
} finally {
  await app.close();
}

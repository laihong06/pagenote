import { _electron as electron } from '@playwright/test';
import { dismissWelcome } from './dismiss-welcome.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
if (process.platform !== 'darwin') {
  console.log('SKIP native settings app');
  process.exit(0);
}
const root = path.resolve(import.meta.dirname, '..');
const profile = await fs.mkdtemp(
  path.join(root, 'tmp', 'native-settings-app-'),
);
const app = await electron.launch({
  executablePath:
    process.env.DESKTOP_EXE ||
    path.join(
      root,
      'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron',
    ),
  args: process.env.DESKTOP_EXE ? [] : [root],
  env: { ...process.env, PAGE_NOTES_USER_DATA: profile },
});
try {
  const w = await app.firstWindow();
  await dismissWelcome(w);
  const first = await w.evaluate(() =>
    window.pageNotes.openSettings('appearance'),
  );
  assert.equal(first.native, true);
  assert.ok(first.windowNumber > 0);
  assert.equal(
    await w.locator('#settings').evaluate((e) => e.open),
    false,
    'native entry never opens HTML modal',
  );
  const second = await w.evaluate(() =>
    window.pageNotes.openSettings('reading'),
  );
  assert.equal(second.pid, first.pid);
  assert.equal(
    await app.evaluate(
      ({ BrowserWindow }) => BrowserWindow.getAllWindows().length,
    ),
    1,
    'settings is AppKit, not another web window',
  );
  await w.evaluate(async () => {
    await window.pageNotes.applySetting('theme', 'dark');
    await window.pageNotes.applySetting('markdownTheme', 'paper');
    await window.pageNotes.applySetting('language', 'en-US');
    await window.pageNotes.applySetting('noteAnimation', false);
    await window.pageNotes.applySetting('expandEditor', false);
  });
  const settings = await w.evaluate(() => window.pageNotes.getSettingsState());
  assert.equal(settings.theme, 'dark');
  assert.equal(settings.markdownTheme, 'paper');
  assert.equal(settings.language, 'en-US');
  assert.equal(settings.noteAnimation, false);
  assert.equal(settings.expandEditor, false);
  await assert.rejects(
    w.evaluate(() => window.pageNotes.applySetting('theme', 'not-a-theme')),
    /Unsupported/,
  );
  await assert.rejects(
    w.evaluate(() => window.pageNotes.applySetting('expandEditor', 'false')),
    /boolean/,
  );
  const prefs = JSON.parse(
    await fs.readFile(
      path.join(profile, 'documents-data/preferences.json'),
      'utf8',
    ),
  );
  assert.equal(prefs.theme, 'dark');
  assert.equal(prefs.markdownTheme, 'paper');
  const layout = JSON.parse(
    await fs.readFile(path.join(profile, 'documents-data/layout.json'), 'utf8'),
  );
  assert.equal(layout.noteAnimation, false);
  assert.equal(layout.expandEditor, false);
  await app.evaluate(({ Menu }) => {
    const settings = Menu.getApplicationMenu()
      .items.flatMap((i) => i.submenu?.items || [])
      .find((i) => i.accelerator === 'CmdOrCtrl+,');
    settings.click();
  });
  assert.equal(await w.locator('#settings').evaluate((e) => e.open), false);
  await app.close();
  await new Promise((resolve) => setTimeout(resolve, 300));
  assert.throws(
    () => process.kill(first.pid, 0),
    /ESRCH/,
    'quitting reader closes native helper',
  );
  console.log(
    'PASS native settings IPC/menu, true AppKit window, live preference/layout writes, validation and graceful quit',
  );
} finally {
  await app.close().catch(() => {});
}

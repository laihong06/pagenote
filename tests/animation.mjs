import { dismissWelcome } from './dismiss-welcome.mjs';
import { useWebSettings } from './web-settings.mjs';
import { _electron as electron } from '@playwright/test';
import path from 'node:path';
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
    PAGE_NOTES_USER_DATA: path.join(root, 'tests/animation-data'),
  },
  timeout: 40000,
});
try {
  const w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await useWebSettings(app);
  assert.ok(
    await w.locator('#noteAnimationSetting').isChecked(),
    'animation defaults on',
  );
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.locator('#loading').waitFor({ state: 'hidden' });
  await w.evaluate(async () => {
    const s = await window.pageNotes.getState();
    await window.pageNotes.open(s.document.id, 1);
    window.animationObserved = false;
    new MutationObserver(() => {
      if (document.querySelector('#pageNumber').value === '2')
        window.animationObserved =
          document.querySelector('#noteBody').getAnimations().length > 0;
    }).observe(document.querySelector('#noteTitle'), { childList: true });
  });
  await w.locator('#next').click();
  await w.waitForFunction(
    () => document.querySelector('#pageNumber').value === '2',
  );
  assert.ok(
    await w.evaluate(() => window.animationObserved),
    'page transition actually animates',
  );
  await w.waitForTimeout(300);
  assert.equal(
    await w.locator('#noteBody').evaluate((e) => e.getAnimations().length),
    0,
    'animation finishes',
  );
  await w.evaluate(() => window.pageNotes.openSettings('reading'));
  await w.locator('#noteAnimationSetting').uncheck();
  await w.waitForFunction(
    () => !document.querySelector('#noteAnimationSetting').disabled,
  );
  await w.locator('#closeSettings').click();
  await w.locator('#prev').click();
  await w.waitForFunction(
    () => document.querySelector('#pageNumber').value === '1',
  );
  assert.equal(
    await w.locator('#noteBody').evaluate((e) => e.getAnimations().length),
    0,
    'disabled transition is immediate',
  );
  await w.reload();
  await dismissWelcome(w);
  assert.ok(
    !(await w.locator('#noteAnimationSetting').isChecked()),
    'preference survives reload',
  );
  await w.evaluate(() => window.pageNotes.openSettings('reading'));
  await w.locator('#noteAnimationSetting').check();
  await w.waitForFunction(
    () => !document.querySelector('#noteAnimationSetting').disabled,
  );
  await w.locator('#closeSettings').click();
  console.log(
    'PASS: animation enabled by default, real page animation, finish, toggle off, persistence, toggle on.',
  );
} finally {
  await app.close();
}

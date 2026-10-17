import { _electron as electron } from '@playwright/test';
import { dismissWelcome } from './dismiss-welcome.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
if (process.platform !== 'darwin') {
  console.log('SKIP native fallback');
  process.exit(0);
}
const root = path.resolve(import.meta.dirname, '..');
const profile = await fs.mkdtemp(path.join(root, 'tmp', 'settings-fallback-'));
const bootstrap = path.join(profile, 'bootstrap.cjs');
await fs.writeFile(
  bootstrap,
  `
const {app}=require('electron');app.setAppPath(${JSON.stringify(root)});
const settings=require(${JSON.stringify(path.join(root, 'desktop/mac-settings.cjs'))});
const create=settings.createMacSettings;
globalThis.nativeFailureErrors=[];
settings.createMacSettings=options=>create({...options,executable:${JSON.stringify(path.join(profile, 'missing-native-helper'))},onError:error=>nativeFailureErrors.push(error.message)});
require(${JSON.stringify(path.join(root, 'desktop/main.cjs'))});
`,
);
const app = await electron.launch({
  executablePath: path.join(
    root,
    'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron',
  ),
  args: [bootstrap],
  env: { ...process.env, PAGE_NOTES_USER_DATA: profile },
});
try {
  const w = await app.firstWindow();
  await dismissWelcome(w);
  const result = await w.evaluate(() =>
    window.pageNotes.openSettings('appearance'),
  );
  assert.equal(result.native, false);
  assert.equal(
    await w.locator('#settings').evaluate((e) => e.open),
    true,
    'missing helper falls back to usable web settings',
  );
  assert.match(
    await w.locator('#toast').innerText(),
    /原生设置|Native settings/,
    'fallback is explained',
  );
  await w.locator('#markdownThemeSetting').selectOption('paper');
  await w.waitForFunction(
    () => window.pageNotes.getMarkdownTheme() === 'paper',
  );
  await w.locator('#closeSettings').click();
  await new Promise((resolve) => setTimeout(resolve, 100));
  assert.deepEqual(
    await app.evaluate(() => nativeFailureErrors),
    [],
    'missing helper must not also open a blocking EPIPE error dialog',
  );
  console.log(
    'PASS missing native helper reports failure and preserves usable web settings',
  );
} finally {
  await app.close();
}

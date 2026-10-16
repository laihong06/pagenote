import { _electron as electron } from '@playwright/test';
import { dismissWelcome } from './dismiss-welcome.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
if (process.platform !== 'darwin') {
  console.log('SKIP native quit race');
  process.exit(0);
}
const root = path.resolve(import.meta.dirname, '..');
const profile = await fs.mkdtemp(path.join(root, 'tmp', 'settings-quit-race-'));
const bootstrap = path.join(profile, 'bootstrap.cjs');
await fs.writeFile(
  bootstrap,
  `
const {app}=require('electron');
app.setAppPath(${JSON.stringify(root)});
const settings=require(${JSON.stringify(path.join(root, 'desktop/mac-settings.cjs'))});
const create=settings.createMacSettings;
globalThis.quitGate={count:0,allow:false,hold:true,started:false};
settings.createMacSettings=(options)=>{
 const controller=create(options), prepare=controller.prepareQuit;
 controller.prepareQuit=async()=>{quitGate.count++;return quitGate.allow ? prepare() : false;};
 return controller;
};
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
let exited = false;
app.on('close', () => {
  exited = true;
});
try {
  const w = await app.firstWindow();
  await dismissWelcome(w);
  await app.evaluate(({ BrowserWindow }) => {
    const win = BrowserWindow.getAllWindows()[0];
    const execute = win.webContents.executeJavaScript.bind(win.webContents);
    win.webContents.executeJavaScript = async (code, ...args) => {
      const value = await execute(code, ...args);
      if (code === 'window.pageNotes?.finishEditing?.()' && quitGate.hold) {
        quitGate.started = true;
        await new Promise((resolve) => {
          quitGate.release = resolve;
        });
      }
      return value;
    };
    win.close();
  });
  for (let i = 0; i < 100; i++) {
    if (await app.evaluate(() => quitGate.started)) break;
    await new Promise((r) => setTimeout(r, 10));
  }
  await app.evaluate(({ app }) => {
    app.quit();
    quitGate.hold = false;
    quitGate.release();
  });
  await new Promise((resolve) => setTimeout(resolve, 400));
  assert.equal(
    exited,
    false,
    'Quit must not bypass settings draft cancellation during an existing note flush',
  );
  assert.equal(
    await app.evaluate(() => quitGate.count),
    1,
    'native draft handling is rechecked when Close becomes Quit',
  );
  console.log(
    'PASS close-to-quit escalation honors native draft cancellation after pending note flush',
  );
} finally {
  if (!exited) {
    await app.evaluate(() => {
      quitGate.allow = true;
      quitGate.hold = false;
      quitGate.release?.();
    });
    await app.close();
  }
}

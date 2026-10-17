import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
const root = path.resolve(import.meta.dirname, '..');
const profile = path.join(root, 'tests', 'launch-' + Date.now());
await fs.mkdir(profile, { recursive: true });
async function fixture(name) {
  const dir = path.join(profile, name, 'src');
  await fs.mkdir(dir, { recursive: true });
  await fs.writeFile(path.join(dir, 'left.md'), '# ' + name);
  await fs.writeFile(path.join(dir, 'right.md'), '# Explanation ' + name);
  const file = path.join(dir, '..', 'project.pagenote');
  await fs.writeFile(file, JSON.stringify({ format: 'pagenote-project', version: 1, id: createHash('sha256').update(name).digest('hex'), name, created: new Date().toISOString(), left: 'src/left.md', right: 'src/right.md' }));
  return file;
}
const first = await fixture('first project 中文');
const second = await fixture('second project 中文');
const executablePath =
  process.env.DESKTOP_EXE ||
  path.join(
    root,
    process.platform === 'darwin'
      ? 'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron'
      : 'node_modules/electron/dist/electron.exe',
  );
const prefix = [root];
const env = { ...process.env, PAGE_NOTES_USER_DATA: profile };
const app = await electron.launch({ executablePath, args: [...prefix, first], env });
try {
  const w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  async function waitProject(file) {
    const deadline=Date.now()+20000;
    while(Date.now()<deadline) {
      const state=await w.evaluate(()=>window.pageNotes.getState());
      if(state.document?.projectFile === file && state.markdown.includes('Explanation')) return state;
      await new Promise(resolve=>setTimeout(resolve,100));
    }
    throw Error('Project did not open: '+file);
  }
  await waitProject(first);
  await app.evaluate(({ BrowserWindow }) => {
    const win = BrowserWindow.getAllWindows()[0];
    win.minimize();
    win.hide();
  });
  const child = spawn(executablePath, [...prefix, second], { env, windowsHide: true, stdio: 'ignore' });
  await new Promise((resolve, reject) => { child.once('error', reject); child.once('exit', resolve); });
  const state = await waitProject(second);
  assert.equal(state.document.projectFile, second);
  assert.ok(await app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].isVisible()), 'file activation shows a hidden window');
  assert.equal(await app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].isMinimized()), false, 'file activation restores a minimized window');
  assert.equal((await app.windows()).length, 1);
  console.log('PASS actual command-line cold open and second-instance project open (spaces and Chinese paths)');
} finally { await app.close(); }

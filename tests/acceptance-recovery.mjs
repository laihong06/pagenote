import { _electron as electron } from '@playwright/test';
import { dismissWelcome } from './dismiss-welcome.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { once } from 'node:events';
import { spawnSync } from 'node:child_process';

const root = path.resolve(import.meta.dirname, '..');
const profile = path.join(root, 'tests', 'acceptance-data-' + Date.now());
const sourceParent = await fs.mkdtemp(
  path.join(os.tmpdir(), 'pagenote-acceptance-'),
);
const launch = () =>
  electron.launch({
    executablePath:
      process.env.DESKTOP_EXE ||
      path.join(
        root,
        process.platform === 'darwin'
          ? 'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron'
          : 'node_modules/electron/dist/electron.exe',
      ),
    args: process.env.DESKTOP_EXE ? [] : [root],
    env: { ...process.env, PAGE_NOTES_USER_DATA: profile },
    timeout: 40000,
  });
let app = await launch(),
  killed = false;
try {
  let w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await w.evaluate(() => window.pageNotes.setLanguage('en-US'));
  await w.evaluate(() => window.pageNotes.menuAction('newProject'));
  await w.locator('#newName').fill('验收 project with spaces');
  await w.locator('#newParent').fill(sourceParent);
  await w.locator('#newConfirm').click();
  await w.locator('#newConfirm').waitFor({ state: 'hidden' });
  const initial = await w.evaluate(() => window.pageNotes.getState());
  const id = initial.document.id;
  const first = '# Confirmed save A\n\n$$\nx^2+1\n$$';
  const second = '# Confirmed save B\n\nRetain before history restore.';
  const write = async (markdown) => {
    await w.evaluate(
      async ({ id, markdown }) => {
        const r = await fetch(`/api/documents/${id}/notes/1`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ markdown }),
        });
        if (!r.ok) throw Error('save failed ' + r.status);
        await window.pageNotes.syncNotes(id);
      },
      { id, markdown },
    );
  };
  await write(first);
  await write(second);
  const history = await w.evaluate(async (id) => {
    const base = `/api/documents/${id}/notes/1`;
    const list = await (await fetch(base + '/history')).json();
    for (const version of list.versions) {
      const content = await (
        await fetch(base + '/history/' + encodeURIComponent(version.id))
      ).json();
      if (content.markdown.includes('Confirmed save A'))
        return { ...list, version: version.id };
    }
    throw Error('confirmed saved version missing from history');
  }, id);
  const stale = await w.evaluate(
    async ({ id, version }) => {
      const r = await fetch(`/api/documents/${id}/notes/1/restore`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ version, expectedRevision: '0'.repeat(64) }),
      });
      return r.ok;
    },
    { id, version: history.version },
  );
  assert.equal(stale, false, 'stale history restore is refused');
  assert.equal(
    (await w.evaluate(() => window.pageNotes.getState())).markdown,
    second,
  );
  await app.evaluate(({ Menu }) => {
    const file = Menu.getApplicationMenu().items.find(
      (item) => item.label === 'File',
    );
    const history = file.submenu.items.find(
      (item) => item.label === 'Version history',
    );
    if (!history)
      throw Error(
        'Version history must be reachable through the native File menu',
      );
    history.click();
  });
  await w.locator('#historyList').selectOption(history.version);
  await w.waitForFunction(
    () => !document.querySelector('#restoreHistory').disabled,
  );
  w.once('dialog', (dialog) => dialog.accept());
  await w.locator('#restoreHistory').click();
  await w.locator('#historyDialog').waitFor({ state: 'hidden' });
  assert.equal(
    (await w.evaluate(() => window.pageNotes.getState())).markdown,
    first,
  );
  await w.evaluate(() => window.pageNotes.flush());
  const source = initial.document.projectRoot;
  const moved = path.join(profile, '迁移 target with spaces');
  await fs.cp(source, moved, { recursive: true });
  await fs.rename(source, source + '-unavailable');
  await w.evaluate(
    (file) => window.pageNotes.openProject(file),
    path.join(moved, 'project.pagenote'),
  );
  const movedState = await w.evaluate(() => window.pageNotes.getState());
  assert.equal(movedState.markdown, first);
  assert.equal(movedState.document.projectRoot, moved);
  assert.equal(movedState.sourceText, initial.sourceText);
  const crossDrive =
    path.parse(source).root.toLowerCase() !==
    path.parse(moved).root.toLowerCase();
  await w.evaluate(() => window.pageNotes.flush());
  const child = app.process(),
    exited = once(child, 'exit');
  if (process.platform === 'win32') {
    const stopped = spawnSync(
      'taskkill',
      ['/PID', String(child.pid), '/T', '/F'],
      { windowsHide: true },
    );
    assert.equal(
      stopped.status,
      0,
      'terminate only this isolated test application tree',
    );
  } else child.kill();
  await exited;
  killed = true;
  app = await launch();
  killed = false;
  w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await w.evaluate(
    (file) => window.pageNotes.openProject(file),
    path.join(moved, 'project.pagenote'),
  );
  const recovered = await w.evaluate(() => window.pageNotes.getState());
  assert.equal(
    recovered.markdown,
    first,
    'confirmed save survives abnormal exit',
  );
  assert.equal(await w.evaluate(() => window.pageNotes.getLanguage()), 'en-US');
  assert.equal(
    await fs.readFile(path.join(moved, 'src/right.md'), 'utf8'),
    first,
  );
  console.log(
    'PASS history UI restore, stale-revision refusal, Chinese/space project paths, ' +
      (crossDrive
        ? 'actual cross-drive copy'
        : 'same-drive copy (cross-drive not covered)') +
      ', unavailable original directory, confirmed-save crash recovery and settings persistence',
  );
} finally {
  if (!killed) await app.close();
}

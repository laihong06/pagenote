import { _electron as electron } from '@playwright/test';
import { dismissWelcome } from './dismiss-welcome.mjs';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';

const root = path.resolve(import.meta.dirname, '..');
const profile = path.join(root, 'tests', 'project-import-data-' + Date.now());
const app = await electron.launch({
  executablePath:
    process.env.DESKTOP_EXE ||
    path.join(
      root,
      'node_modules/electron/dist',
      process.platform === 'darwin'
        ? 'Electron.app/Contents/MacOS/Electron'
        : 'electron.exe',
    ),
  args: [root],
  env: { ...process.env, PAGE_NOTES_USER_DATA: profile },
});
try {
  const window = await app.firstWindow();
  await dismissWelcome(window);
  async function waitState(predicate) {
    const deadline = Date.now() + 30000;
    while (Date.now() < deadline) {
      const state = await window.evaluate(() => window.pageNotes.getState());
      if (predicate(state)) return state;
      await window.waitForTimeout(100);
    }
    throw Error('Desktop project state did not become ready');
  }
  await window
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  const imported = (await waitState((state) => !!state.document?.projectFile))
    .document;
  const manifest = JSON.parse(await fs.readFile(imported.projectFile));
  assert.equal(manifest.left, 'src/source.pdf');
  assert.equal(manifest.right, 'src/notes.md');
  assert.equal(
    (await fs.stat(path.join(imported.projectRoot, manifest.left))).size,
    (await fs.stat(path.join(root, 'tests/fixtures/sample.pdf'))).size,
  );
  const legacyId = createHash('sha256')
    .update('legacy-import-fixture')
    .digest('hex');
  const dir = path.join(profile, 'documents-data', 'documents', legacyId);
  await fs.mkdir(dir, { recursive: true });
  await fs.writeFile(
    path.join(dir, 'meta.json'),
    JSON.stringify({
      id: legacyId,
      name: '旧独立文档.md',
      ext: '.md',
      kind: 'markdown',
      created: new Date().toISOString(),
      url: `/files/${legacyId}/original.md`,
    }),
  );
  await fs.writeFile(path.join(dir, 'original.md'), '# 旧独立原文');
  await fs.writeFile(
    path.join(dir, 'notes.json'),
    JSON.stringify({ 1: '旧笔记应完整保留' }),
  );
  await fs.writeFile(path.join(dir, 'history.keep'), 'old history');
  // The app now opens to the bookshelf instead of restoring last-document
  // automatically. Exercise the supported open path for this legacy entry.
  await window.evaluate((id) => window.pageNotes.open(id), legacyId);
  await waitState(
    (state) =>
      state.document?.id === legacyId &&
      !!state.document.projectFile &&
      state.markdown.includes('旧笔记应完整保留'),
  );
  const adopted = (await window.evaluate(() => window.pageNotes.getState()))
    .document;
  assert.equal(
    await fs.readFile(path.join(adopted.projectRoot, 'src/source.md'), 'utf8'),
    '# 旧独立原文',
  );
  assert.equal(
    await fs.readFile(path.join(adopted.projectRoot, 'src/notes.md'), 'utf8'),
    '旧笔记应完整保留',
  );
  assert.equal(
    await fs.readFile(path.join(dir, 'history.keep'), 'utf8'),
    'old history',
  );
  console.log(
    'PASS desktop empty-state import creates a portable project; restoring a legacy standalone entry copies notes/source and preserves history.',
  );
} catch (error) {
  const window = await app.firstWindow();
  console.log(
    'Import failure state:',
    await window.evaluate(async () => ({
      state: await window.pageNotes?.getState(),
      toast: document.querySelector('#toast')?.textContent,
    })),
  );
  await window.screenshot({ path: path.join(profile, 'failure.png') });
  throw error;
} finally {
  try {
    await app.close();
  } finally {
    await fs.rm(profile, { recursive: true, force: true });
  }
}

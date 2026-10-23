import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
const root = path.resolve(import.meta.dirname, '..'),
  profile = path.join(root, 'tests', 'project-' + Date.now()),
  projectParent = path.join(profile, 'portable-projects'),
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
});
try {
  const w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await w.evaluate(() => window.pageNotes.setLanguage('zh-CN'));
  await w.evaluate(() => window.pageNotes.menuAction('newProject'));
  await w.locator('#newName').fill('项目测试');
  await w.locator('#newParent').fill(projectParent);
  await w.locator('#newConfirm').click();
  await w.locator('#newConfirm').waitFor({ state: 'hidden' });
  let state = await w.evaluate(() => window.pageNotes.getState());
  const id = state.document.id,
    folder = state.document.projectRoot,
    manifestFile = state.document.projectFile;
  let manifest = JSON.parse(await fs.readFile(manifestFile));
  assert.equal(manifest.left, 'src/source.md');
  assert.equal(manifest.right, 'src/notes.md');
  await w.locator('#aiSharedTools [data-ai-pointer=mouse]').click();
  assert.ok(await w.locator('.mouse-options').isVisible());
  await w.locator('#aiSharedTools [data-ai-pointer=mouse]').click();
  assert.ok(await w.locator('.mouse-options').isHidden());
  await w.evaluate(async (id) => {
    await fetch('/api/documents/' + id + '/notes/1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ markdown: '右侧保存的解释。' }),
    });
    await window.pageNotes.syncNotes(id);
  }, id);
  assert.ok(
    (await fs.readFile(path.join(folder, manifest.right), 'utf8')).includes(
      '右侧保存',
    ),
  );
  const handoffSummary = '本页已说明样本均值，后页沿用这一概念。';
  const handoffSaved = await w.evaluate(
    async ({ id, revision, summary }) => {
      const response = await fetch('/api/documents/' + id + '/handoffs/1', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          summary,
          expectedRevision: revision,
          sourceRevision: 'a'.repeat(64),
        }),
      });
      return response.json();
    },
    {
      id,
      revision: createHash('sha256').update('右侧保存的解释。').digest('hex'),
      summary: handoffSummary,
    },
  );
  assert.equal(handoffSaved.saved, true);
  assert.equal(
    JSON.parse(
      await fs.readFile(path.join(folder, 'src/ai-handoffs.json'), 'utf8'),
    )['1'].summary,
    handoffSummary,
  );
  await fs.writeFile(
    path.join(folder, 'src/notes.md'),
    '右侧保存的解释。（按链接重新读取）',
  );
  await w.evaluate((file) => window.pageNotes.openProject(file), manifestFile);
  const portableHandoff = await w.evaluate(
    async (id) => (await fetch('/api/documents/' + id + '/handoffs/1')).json(),
    id,
  );
  assert.equal(portableHandoff.handoff.summary, handoffSummary);
  assert.equal(
    portableHandoff.stale,
    true,
    'external note changes make portable handoffs stale',
  );
  assert.ok(
    (await w.evaluate(() => window.pageNotes.getState())).markdown.includes(
      '按链接重新读取',
    ),
    'reopening reads src instead of overwriting it with cache',
  );
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.locator('#loading').waitFor({ state: 'hidden' });
  state = await w.evaluate(() => window.pageNotes.getState());
  assert.equal(
    state.document.id,
    id,
    'left import keeps the project ID and right notes',
  );
  assert.ok(state.markdown.includes('右侧保存'));
  manifest = JSON.parse(await fs.readFile(manifestFile));
  assert.equal(manifest.left, 'src/source.pdf');
  assert.equal(
    (await fs.stat(path.join(folder, manifest.left))).size,
    (await fs.stat(path.join(root, 'tests/fixtures/sample.pdf'))).size,
  );
  await w.locator('#preview p').evaluate((el) => {
    const r = document.createRange();
    r.selectNodeContents(el);
    getSelection().removeAllRanges();
    getSelection().addRange(r);
    el.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
  });
  await w.locator('#selectionTag').waitFor();
  await w.locator('#selectionTag').click();
  await w.locator('#reviewDialog').waitFor();
  await w.locator('#reviewInstruction').fill('解释一下');
  await w.locator('#reviewSave').click();
  await w.locator('#reviewDialog').waitFor({ state: 'hidden' });
  const originalMarker = await w
      .locator('#noteBody .review-marker')
      .elementHandle(),
    originalPosition = await w
      .locator('#noteBody .review-marker')
      .boundingBox();
  await w
    .locator('#noteBody .review-marker')
    .dblclick({ timeout: 5000 })
    .catch(async (e) => {
      console.log(
        'AFTER',
        await w.locator('#noteBody .review-marker').evaluate((e) => ({
          rect: e.getBoundingClientRect().toJSON(),
          hit: document
            .elementFromPoint(
              e.getBoundingClientRect().x + 12,
              e.getBoundingClientRect().y + 12,
            )
            ?.outerHTML.slice(0, 200),
          noteScroll: document.querySelector('#noteScroll').scrollTop,
          bodyScroll: document.querySelector('.note-pane').scrollTop,
          selected: getSelection().toString(),
        })),
      );
      await w.screenshot({ path: 'tests/tag-click-debug.png' });
      throw e;
    });
  assert.ok(
    await w
      .locator('.ai-workbench[data-side=note]')
      .evaluate((e) => e.classList.contains('open')),
  );
  await w.waitForTimeout(280);
  assert.ok(
    await originalMarker.evaluate((e) => e.isConnected),
    'tag is not recreated when its canvas opens',
  );
  const afterPosition = await w
    .locator('#noteBody .review-marker')
    .boundingBox();
  assert.ok(
    Math.abs(originalPosition.x - afterPosition.x) < 1 &&
      Math.abs(originalPosition.y - afterPosition.y) < 1,
    'tag does not jump when clicked',
  );
  await w.locator('#noteBody .review-marker').click({ button: 'right' });
  await w.locator('#deleteTagMenu').click();
  await w.waitForFunction(
    () => !document.querySelector('#noteBody .review-marker'),
  );
  assert.equal(
    JSON.parse(await fs.readFile(path.join(folder, 'src/ai-reviews.json')))
      .length,
    0,
  );
  const imageName = '11111111-1111-4111-8111-111111111111.png',
    uploads = path.join(profile, 'documents-data', 'uploads');
  await fs.writeFile(
    path.join(uploads, imageName),
    Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2WfQAAAAASUVORK5CYII=',
      'base64',
    ),
  );
  await w.evaluate(
    async ({ id, imageName }) => {
      await fetch('/api/documents/' + id + '/notes/1', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          markdown: '右侧解释\n\n![图片](/attachments/' + imageName + ')',
        }),
      });
      await window.pageNotes.syncNotes(id);
    },
    { id, imageName },
  );
  await w.evaluate(() => window.pageNotes.finishEditing());
  assert.ok(
    (await fs.readFile(path.join(folder, 'src/notes.md'), 'utf8')).includes(
      'assets/' + imageName,
    ),
  );
  assert.ok((await fs.stat(path.join(folder, 'src/assets', imageName))).size);
  const moved = path.join(projectParent, '移动后的项目');
  await fs.cp(folder, moved, { recursive: true });
  await fs.writeFile(
    path.join(moved, 'src/notes.md'),
    '从项目 src 链接读取的右侧内容。\n\n![图片](assets/' + imageName + ')',
  );
  await fs.rename(
    path.join(uploads, imageName),
    path.join(uploads, imageName + '.original'),
  );
  await w.evaluate(
    (file) => window.pageNotes.openProject(file),
    path.join(moved, 'project.pagenote'),
  );
  state = await w.evaluate(() => window.pageNotes.getState());
  assert.equal(state.document.projectRoot, moved);
  assert.ok(state.sourceText.includes('Pattern Recognition'));
  assert.ok(state.markdown.includes('从项目 src 链接读取'));
  assert.ok(
    (await fs.stat(path.join(uploads, imageName))).size,
    'project assets recovered in a moved project',
  );
  const bad = path.join(projectParent, 'invalid-project');
  await fs.mkdir(bad, { recursive: true });
  await fs.writeFile(
    path.join(bad, 'project.pagenote'),
    JSON.stringify({ ...manifest, left: '../outside.md' }),
  );
  const invalid = await w.evaluate(
    async (file) => {
      const r = await fetch('/api/projects/open', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ filePath: file }),
      });
      return r.ok;
    },
    path.join(bad, 'project.pagenote'),
  );
  assert.equal(invalid, false, 'relative links cannot escape src');
  console.log(
    'PASS mouse toggles; project manifest/src files; left import preserves notes; saved notes synced to right.md; tag double-click opens canvas; right-click deletes; move/reopen resolves relative files; unsafe links rejected.',
  );
} finally {
  await app.close();
}

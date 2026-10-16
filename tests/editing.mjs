import { dismissWelcome } from './dismiss-welcome.mjs';
import { useWebSettings } from './web-settings.mjs';
import { _electron as electron } from '@playwright/test';
import path from 'node:path';
import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
const root = path.resolve(import.meta.dirname, '..'),
  userData = path.join(root, 'tests', `editing-data-${Date.now()}`),
  executable =
    process.env.DESKTOP_EXE ||
    path.join(
      root,
      process.platform === 'darwin'
        ? 'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron'
        : 'node_modules/electron/dist/electron.exe',
    );
await fs.mkdir(userData, { recursive: true });
const app = await electron.launch({
  executablePath: executable,
  args: [root],
  env: { ...process.env, PAGE_NOTES_USER_DATA: userData },
  timeout: 40000,
});
try {
  const w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await useWebSettings(app);
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.locator('#loading').waitFor({ state: 'hidden' });
  await w.evaluate(async () => {
    const s = await window.pageNotes.getState();
    await window.pageNotes.open(s.document.id, 1);
  });
  assert.ok(await w.locator('#expandEditorSetting').isChecked());
  const initialWidth = await w
    .locator('.document-pane')
    .evaluate((e) => e.getBoundingClientRect().width);
  await w.locator('#documentInkButton').click();
  await w.waitForTimeout(60);
  if (
    !(await w.evaluate(
      () => matchMedia('(prefers-reduced-motion: reduce)').matches,
    ))
  ) {
    const middle = await w.locator('.document-pane').evaluate((e) => ({
      width: e.getBoundingClientRect().width,
      limit: innerWidth - 8,
    }));
    assert.ok(
      middle.width > initialWidth && middle.width < middle.limit - 1,
      'expansion has intermediate animation frames',
    );
  }
  await w.waitForTimeout(590);
  assert.equal(
    await w.locator('#workspace').getAttribute('data-focus'),
    'document',
  );
  assert.ok(
    await w
      .locator('.document-pane')
      .evaluate((e) => e.getBoundingClientRect().width > innerWidth * 0.95),
  );
  const draw = async (tool) => {
    await w.locator(`[data-ink-tool="${tool}"]`).click();
    const r = await w.locator('[data-page="1"] > .ink-layer').boundingBox();
    await w.mouse.move(r.x + 100, r.y + 90);
    await w.mouse.down();
    if (tool === 'rect') await w.waitForTimeout(650);
    await w.mouse.move(r.x + 180, r.y + 140, { steps: 12 });
    await w.mouse.up();
  };
  await draw('pen');
  await draw('rect');
  assert.equal(
    (await w.evaluate(() => window.pageNotes.getAnnotations())).items
      .document[1].length,
    2,
  );
  const longStroke = (await w.evaluate(() => window.pageNotes.getAnnotations()))
    .items.document[1][1];
  assert.notDeepEqual(
    longStroke.points[0],
    longStroke.points[1],
    'autosave does not end an in-progress stroke',
  );
  await w.keyboard.press('Control+Z');
  await w.waitForFunction(
    () => window.pageNotes.getAnnotations().items.document[1].length === 1,
  );
  await w.keyboard.press('Control+Y');
  await w.waitForFunction(
    () => window.pageNotes.getAnnotations().items.document[1].length === 2,
  );
  await w.locator('#inkUndo').click();
  assert.equal(
    (await w.evaluate(() => window.pageNotes.getAnnotations())).items
      .document[1].length,
    1,
  );
  await w.locator('#inkRedo').click();
  await app.evaluate(async ({ clipboard }) => {
    globalThis.editingTestClipboard = await clipboard.read();
  });
  await w.locator('[data-ink-tool="select"]').click();
  const copyRect = await w
    .locator('[data-page="1"] > .ink-layer')
    .boundingBox();
  await w.mouse.click(copyRect.x + 140, copyRect.y + 115);
  await w.keyboard.press('Control+C');
  await w.waitForFunction(async () =>
    (await navigator.clipboard.readText()).startsWith(
      'PAGE_NOTES_ANNOTATION:\n',
    ),
  );
  const clipPng = await w.locator('#pdfCanvas').screenshot();
  await app.evaluate(async ({ clipboard, ClipboardItem }, base64) => {
    await clipboard.write([
      new ClipboardItem({
        'image/png': new Blob([Buffer.from(base64, 'base64')], {
          type: 'image/png',
        }),
      }),
    ]);
  }, clipPng.toString('base64'));
  await w.keyboard.press('Control+V');
  await w.waitForFunction(
    () => window.pageNotes.getAnnotations().items.document[1].length === 3,
  );
  assert.equal(
    (
      await w.evaluate(() => window.pageNotes.getAnnotations())
    ).items.document[1].at(-1).type,
    'image',
  );
  await w.locator('#inkUndo').click();
  await w.evaluate(() => window.pageNotes.flush());
  const splitter = await w.locator('#splitter').boundingBox();
  await w.mouse.move(splitter.x + 4, splitter.y + 100);
  await w.mouse.down();
  await w.mouse.move(600, splitter.y + 100, { steps: 10 });
  await w.mouse.up();
  await w.waitForTimeout(300);
  assert.equal(
    await w.locator('#workspace').getAttribute('data-focus'),
    null,
    'drag returns both columns',
  );
  await w.locator('#inkDone').click();
  await w.locator('[data-mode="edit"]').click();
  await w.waitForTimeout(500);
  assert.equal(
    await w.locator('#workspace').getAttribute('data-focus'),
    'note',
  );
  await w.locator('#editor').fill('需要加粗');
  await w.locator('#editor').selectText();
  await w.locator('[data-md="bold"]').click();
  assert.equal(await w.locator('#editor').inputValue(), '**需要加粗**');
  await w.locator('#editor').fill('列表项');
  await w.locator('[data-md="bullet"]').click();
  assert.equal(await w.locator('#editor').inputValue(), '- 列表项');
  await w.locator('#editor').fill('');
  await w.locator('[data-md="table"]').click();
  assert.ok(
    (await w.locator('#editor').inputValue()).includes('| --- | --- |'),
  );
  await w.evaluate(() =>
    window.pageNotes.pasteContent('note', {
      html: '<h2>粘贴的标题</h2><p><strong>加粗内容</strong></p><table><thead><tr><th>A</th><th>B</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr></tbody></table>',
    }),
  );
  assert.ok(
    (await w.locator('#editor').inputValue()).includes('## 粘贴的标题'),
  );
  assert.ok((await w.locator('#editor').inputValue()).includes('**加粗内容**'));
  await app.evaluate(async ({ clipboard, ClipboardItem }, base64) => {
    await clipboard.write([
      new ClipboardItem({
        'text/html': new Blob(
          ['<h2>富文本原生粘贴</h2><p><strong>保留格式</strong></p>'],
          { type: 'text/html' },
        ),
        'image/png': new Blob([Buffer.from(base64, 'base64')], {
          type: 'image/png',
        }),
      }),
    ]);
  }, clipPng.toString('base64'));
  await w.locator('#editor').focus();
  await w.locator('#editor').press('Control+End');
  await w.locator('#editor').press('Control+V');
  await w.waitForFunction(() =>
    document.querySelector('#editor').value.includes('## 富文本原生粘贴'),
  );
  assert.ok(
    (await w.locator('#editor').inputValue()).includes('**保留格式**'),
    'rich text stays editable when clipboard also carries a bitmap',
  );
  await w.locator('[data-mode="read"]').click();
  await w.waitForTimeout(500);
  await w.locator('#noteInkButton').click();
  await w.waitForTimeout(500);
  await drawNote();
  function drawNote() {
    return (async () => {
      await w.locator('[data-ink-tool="arrow"]').click();
      const r = await w.locator('#noteBody > .ink-layer').boundingBox();
      await w.mouse.move(r.x + 100, r.y + 100);
      await w.mouse.down();
      await w.mouse.move(r.x + 220, r.y + 170, { steps: 8 });
      await w.mouse.up();
    })();
  }
  const note = await w.evaluate(() => window.pageNotes.getAnnotations());
  assert.equal(note.items.note[1].length, 1);
  const object =
    'PAGE_NOTES_ANNOTATION:\n' + JSON.stringify(note.items.note[1][0]);
  await w.evaluate(
    (text) => window.pageNotes.pasteContent('document', { text }),
    object,
  );
  assert.equal(
    (await w.evaluate(() => window.pageNotes.getAnnotations())).items
      .document[1].length,
    3,
    'cross-side annotation keeps vector format',
  );
  await w.evaluate(() =>
    window.pageNotes.pasteContent('document', {
      html: '<h2>格式粘贴</h2><p><b>粗体</b>与文字</p>',
    }),
  );
  assert.equal(
    (
      await w.evaluate(() => window.pageNotes.getAnnotations())
    ).items.document[1].at(-1).type,
    'html',
  );
  await w.evaluate(() => window.pageNotes.flush());
  await w.locator('#inkDone').click();
  await w.reload();
  await dismissWelcome(w);
  const saved = await w.evaluate(() => window.pageNotes.getAnnotations());
  assert.equal(saved.items.document[1].length, 4);
  assert.equal(saved.items.note[1].length, 1, 'annotations survive reload');
  await w.locator('#settingsButton').click();
  await w.locator('#expandEditorSetting').uncheck();
  await w.waitForFunction(
    () => !document.querySelector('#expandEditorSetting').disabled,
  );
  await w.locator('#closeSettings').click();
  await w.locator('#documentInkButton').click();
  assert.equal(
    await w.locator('#workspace').getAttribute('data-focus'),
    null,
    'setting disables expansion',
  );
  await w.locator('#inkDone').click();
  await w.locator('[data-mode="read"]').click();
  await w.evaluate(() => window.pageNotes.waitVisuals());
  const png = await app.evaluate(async ({ BrowserWindow }) =>
    (
      await BrowserWindow.getAllWindows()
        .find((w) => w.isVisible())
        .webContents.capturePage()
    )
      .toPNG()
      .toString('base64'),
  );
  await fs.writeFile(
    path.join(root, 'tests/editing-preview.png'),
    Buffer.from(png, 'base64'),
  );
  console.log(
    'PASS editing: full document/note expansion; drag back; pen/shape/arrow; undo/redo; Markdown tools; rich format paste; cross-side vectors; persistence; expand setting.',
  );
} finally {
  await app
    .evaluate(async ({ clipboard }) => {
      if (globalThis.editingTestClipboard)
        await clipboard.write(globalThis.editingTestClipboard);
    })
    .catch(() => {});
  await app.close();
}

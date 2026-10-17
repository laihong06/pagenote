import { _electron as electron, expect } from '@playwright/test';
import { dismissWelcome } from './dismiss-welcome.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const profile = path.join(root, 'tmp', 'paper-data-' + Date.now());
await fs.mkdir(profile, { recursive: true });
const fixture = path.join(profile, 'reading.md');
const text =
  '# 把知识，写进纸页\n\nReading, slowly.\n\n## 留一点时间，给理解\n\n阅读不是匆匆翻过一页，而是在文字之间停留。让概念与自己的经验相遇，笔记才会成为真正属于你的理解。\n\nGood notes leave room to think. A quieter page lets the ideas speak, with generous margins and a familiar rhythm.\n\n> 学而不思则罔，思而不学则殆。\n>\n> Take your time. Make the idea your own.\n\n### 从一个小问题开始\n\n1. 看清问题的条件。\n2. 用自己的话复述。\n3. 留下一个值得继续追问的问题。\n\n| Reading | Reflection |\n| --- | --- |\n| Observe | Notice the details |\n| Connect | Make it your own |\n\n```js\nconst idea = "A little more understanding";\nconsole.log(idea);\n```\n\n$$E = mc^2$$\n\n---\n\n[继续阅读 · Keep exploring](https://example.com)';
await fs.writeFile(fixture, text);
const executablePath =
  process.env.DESKTOP_EXE ||
  path.join(
    root,
    process.platform === 'darwin'
      ? 'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron'
      : 'node_modules/electron/dist/electron.exe',
  );
const launch = () =>
  electron.launch({
    executablePath,
    args: [root],
    env: { ...process.env, PAGE_NOTES_USER_DATA: profile },
  });
let app = await launch();
let w;
const errors = [];
async function ready() {
  w = await app.firstWindow();
  w.on('pageerror', (error) => errors.push(error.message));
  await dismissWelcome(w);
  await w.bringToFront();
}
async function choose(value) {
  await w.evaluate(() => document.getElementById('settings').showModal());
  await w.locator('#markdownThemeSetting').selectOption(value);
  await w.waitForFunction(
    (value) => window.pageNotes.getMarkdownTheme() === value,
    value,
  );
  await w.locator('#closeSettings').click();
}
async function captureStyle() {
  const connection = JSON.parse(
    await fs.readFile(path.join(profile, 'ai-connection.json'), 'utf8'),
  );
  const response = await fetch(connection.url + '/api/ai/call', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer ' + connection.token,
    },
    body: JSON.stringify({
      action: 'screenshot',
      args: { page: 1, scope: 'note' },
    }),
  });
  const result = await response.json();
  assert.equal(
    response.status,
    200,
    result.error || 'hidden screenshot succeeds',
  );
  return app.evaluate(async ({ BrowserWindow }) => {
    const window = BrowserWindow.getAllWindows().find((w) =>
      w.webContents.getURL().includes('capture=1'),
    );
    return window.webContents.executeJavaScript(
      '({appearance:document.documentElement.dataset.theme,reading:document.documentElement.dataset.markdownTheme,font:getComputedStyle(document.getElementById("preview")).fontFamily})',
    );
  });
}
async function inkPosition() {
  return w.evaluate(() => {
    const block = document.querySelector('.markdown-page h3');
    const ink = document.querySelector(
      '.document-sheet .ink-layer [data-item="0"]',
    );
    const a = block.getBoundingClientRect(),
      b = ink.getBoundingClientRect();
    const font = parseFloat(getComputedStyle(block).fontSize);
    return {
      x: (b.x - a.x) / font,
      y: (b.y - a.y) / font,
      width: b.width / font,
    };
  });
}
function aligned(actual, expected, label) {
  for (const key of ['x', 'y', 'width'])
    assert.ok(
      Math.abs(actual[key] - expected[key]) < 0.15,
      `${label}: ${key} stays attached to its text block (${actual[key]} vs ${expected[key]})`,
    );
}
try {
  await ready();
  assert.equal(
    await w.evaluate(() => window.pageNotes.getMarkdownTheme?.()),
    'classic',
    'existing appearance stays the default',
  );
  await w.evaluate(() =>
    Promise.all([
      window.pageNotes.setMarkdownTheme('paper'),
      window.pageNotes.setMarkdownTheme('classic'),
    ]),
  );
  assert.equal(
    await w.evaluate(() => window.pageNotes.getMarkdownTheme()),
    'classic',
    'the latest theme request wins while layout is being saved',
  );
  await w.evaluate(() => window.pageNotes.setTheme('light'));
  await w.locator('#fileInput').setInputFiles(fixture);
  await w.locator('.markdown-page').first().waitFor();
  await w.evaluate(async (markdown) => {
    const { document } = await window.pageNotes.getState();
    const response = await fetch('/api/documents/' + document.id + '/notes/1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ markdown }),
    });
    if (!response.ok) throw Error('Could not create paper fixture');
    await window.pageNotes.syncNotes(document.id);
    await window.pageNotes.waitVisuals();
  }, text);
  const before = await w.evaluate(() => window.pageNotes.getState());
  await w.locator('.markdown-page h3').scrollIntoViewIfNeeded();
  await w.locator('#aiSharedTools [data-ai-pointer=pen]').click();
  await w.evaluate(() =>
    document.querySelector('[data-ink-tool=line]').click(),
  );
  const heading = await w.locator('.markdown-page h3').boundingBox();
  await w.mouse.move(heading.x + 4, heading.y + 4);
  await w.mouse.down();
  await w.mouse.move(heading.x + 84, heading.y + 4, { steps: 5 });
  await w.mouse.up();
  await w.evaluate(() => window.pageNotes.finishEditing());
  const originalInk = await w.evaluate(
    () => window.pageNotes.getAnnotations().items.document[1][0],
  );
  const classicInk = await inkPosition();
  const classic = await captureStyle();
  assert.equal(classic.reading, 'classic');
  await choose('paper');
  await w.waitForFunction(() =>
    document.querySelector('.document-sheet .ink-layer [data-item="0"]'),
  );
  aligned(await inkPosition(), classicInk, 'Classic to Paper');
  // Open unmigrated ink while Paper is already saved, at its original width.
  await w.evaluate(
    async ({ id, item }) => {
      delete item.layoutAnchor;
      const response = await fetch(
        `/api/documents/${id}/annotations/document/1`,
        {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ items: [item] }),
        },
      );
      if (!response.ok) throw Error('Could not load legacy ink');
      await window.pageNotes.open(id, 1, true);
    },
    { id: before.document.id, item: originalInk },
  );
  aligned(
    await inkPosition(),
    classicInk,
    'Legacy ink opened with saved Paper',
  );
  await choose('classic');
  const storedInk = await w.evaluate(
    async (id) =>
      (await (await fetch(`/api/documents/${id}/annotations`)).json())
        .document[1][0],
    before.document.id,
  );
  assert.ok(
    storedInk.layoutAnchor,
    'legacy anchors are persisted before changing the theme',
  );
  aligned(await inkPosition(), classicInk, 'Paper to Classic');
  await choose('paper');
  await w.locator('.markdown-page h3').scrollIntoViewIfNeeded();
  await w.evaluate(() =>
    document.querySelector('[data-ink-tool=select]').click(),
  );
  const paperHeading = await w
    .locator('.markdown-page h3')
    .evaluate((e) => parseFloat(getComputedStyle(e).fontSize));
  // Dismiss the tool popover before dragging; on compact displays it covers
  // the source pane's center and otherwise receives the pointer events.
  await w.locator('.document-pane .pane-heading').click();
  const inkBox = await w
    .locator('.document-sheet .ink-layer [data-item="0"]')
    .boundingBox();
  await w.mouse.move(inkBox.x + 8, inkBox.y);
  await w.mouse.down();
  await w.mouse.move(inkBox.x + 38, inkBox.y + 20, { steps: 5 });
  await w.mouse.up();
  const movedInk = {
    x: classicInk.x + 30 / paperHeading,
    y: classicInk.y + 20 / paperHeading,
    width: classicInk.width,
  };
  aligned(await inkPosition(), movedInk, 'Drag after reflow');
  await w.evaluate(() => window.pageNotes.editCommand('undo'));
  aligned(await inkPosition(), classicInk, 'Undo drag');
  await choose('classic');
  await w.evaluate(() => window.pageNotes.editCommand('redo'));
  aligned(await inkPosition(), movedInk, 'Redo drag under Classic');
  await w.evaluate(() => window.pageNotes.editCommand('undo'));
  await choose('paper');
  aligned(
    await inkPosition(),
    classicInk,
    'Restored after undo and theme switch',
  );
  await expect(w.locator('html')).toHaveAttribute(
    'data-markdown-theme',
    'paper',
  );
  for (const selector of ['#preview', '.markdown-page']) {
    const style = await w
      .locator(selector)
      .first()
      .evaluate((element) => ({
        font: getComputedStyle(element).fontFamily,
        bg: getComputedStyle(element).backgroundColor,
      }));
    assert.match(style.font, /Charter|Georgia/);
    const channels = style.bg.match(/\d+/g).slice(0, 3).map(Number);
    assert.ok(
      channels[0] > 240 && channels[0] > channels[2],
      'paper has a warm light background',
    );
  }
  assert.match(
    await w
      .locator('#preview pre code')
      .first()
      .evaluate((e) => getComputedStyle(e).fontFamily),
    /monospace|Consolas/,
  );
  await expect(w.locator('#preview .katex')).not.toHaveCount(0);
  await w.evaluate(() => {
    document.getElementById('documentScroll').scrollTop = 0;
    document.getElementById('noteScroll').scrollTop = 0;
  });
  await w.screenshot({ path: path.join(profile, 'paper-light.png') });
  const paper = await captureStyle();
  assert.equal(paper.appearance, 'light');
  assert.equal(
    paper.reading,
    'paper',
    'an existing hidden renderer picks up a new reading theme',
  );
  assert.match(paper.font, /Charter|Georgia/);

  await w.evaluate(() => window.pageNotes.setTheme('dark'));
  const dark = await w
    .locator('#noteBody')
    .evaluate((e) => getComputedStyle(e).backgroundColor);
  assert.ok(
    dark
      .match(/\d+/g)
      .slice(0, 3)
      .map(Number)
      .every((v) => v < 80),
  );
  await w.screenshot({ path: path.join(profile, 'paper-dark.png') });
  assert.equal((await captureStyle()).appearance, 'light');
  assert.equal(
    (await w.evaluate(() => window.pageNotes.getState())).markdown,
    before.markdown,
  );
  assert.equal(
    (await w.evaluate(() => window.pageNotes.getState())).sourceText,
    before.sourceText,
  );

  await w.evaluate(() => window.pageNotes.menuAction('chooseEditor'));
  await w.locator('.note-pane .pane-heading').click();
  await expect(w.locator('#preview')).toHaveAttribute(
    'contenteditable',
    'true',
  );
  await expect(w.locator('#preview .rich-code-block')).not.toHaveCount(0);
  const codePaper = await w
    .locator('#preview .rich-code-block')
    .first()
    .evaluate((e) => getComputedStyle(e).backgroundColor);
  const codeChannels = codePaper.match(/\d+/g).slice(0, 3).map(Number);
  assert.ok(
    codeChannels[0] > codeChannels[2],
    'editable code keeps a warm paper palette',
  );
  await choose('classic');
  await expect(w.locator('#preview')).toHaveAttribute(
    'contenteditable',
    'true',
  );
  await choose('paper');
  await w.locator('#noteEditButton').click();
  await w.evaluate(() => window.pageNotes.setLanguage('en-US'));
  await app.close();
  app = await launch();
  await ready();
  assert.equal(
    await w.evaluate(() => window.pageNotes.getMarkdownTheme()),
    'paper',
  );
  assert.equal(await w.evaluate(() => window.pageNotes.getTheme()), 'dark');
  assert.equal(await w.evaluate(() => window.pageNotes.getLanguage()), 'en-US');
  await w.evaluate((id) => window.pageNotes.open(id), before.document.id);
  aligned(await inkPosition(), classicInk, 'Paper after restart');
  await choose('classic');
  assert.equal((await captureStyle()).reading, 'classic');

  await choose('paper');
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.locator('.document-sheet[data-painted="1"] canvas').first().waitFor();
  assert.equal(
    await w
      .locator('.document-sheet canvas')
      .first()
      .evaluate((e) => getComputedStyle(e).filter),
    'none',
  );
  const pixel = await w
    .locator('.document-sheet canvas')
    .first()
    .evaluate((e) => [...e.getContext('2d').getImageData(3, 3, 1, 1).data]);
  assert.ok(
    pixel.slice(0, 3).every((v) => v > 240),
    'original PDF paper remains white',
  );
  assert.deepEqual(errors, []);
  console.log(
    'PASS paper serif light/dark, native fonts, code/math/editing, source ink reflow with legacy anchors and move/undo/redo, preference persistence/latest request, hidden captures and original PDF colors.',
  );
  console.log('Previews: ' + profile);
} catch (error) {
  await w
    ?.screenshot({ path: path.join(profile, 'failure.png') })
    .catch(() => {});
  await fs.writeFile(path.join(profile, 'failure.txt'), String(error));
  throw error;
} finally {
  await app.close();
}

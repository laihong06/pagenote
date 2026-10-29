import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron, expect } from '@playwright/test';
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
    PAGE_NOTES_USER_DATA: path.join(
      root,
      'tests',
      'ai-workspace-' + Date.now(),
    ),
  },
  timeout: 40000,
});
try {
  const w = await app.firstWindow();
  w.on('pageerror', (e) => console.error(e.message));
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.waitForFunction(() => !!window.pageNotes.getSummary().document);
  await w.locator('#loading').waitFor({ state: 'hidden' });
  const documentId = await w.evaluate(
    () => window.pageNotes.getSummary().document.id,
  );
  await w.evaluate(async () => {
    const state = await window.pageNotes.getState();
    await fetch('/api/documents/' + state.document.id + '/notes/1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        markdown:
          '这里是一段需要解释的笔记。\n\n' +
          Array(40).fill('第二段正文，用于检查阅读和抓手滚动。').join('\n\n'),
      }),
    });
    await window.pageNotes.syncNotes(state.document.id);
  });
  assert.ok(await w.locator('#aiSharedTools').isVisible());
  assert.equal(
    await w.locator('#preview').getAttribute('contenteditable'),
    'false',
  );
  const select = async (selector) => {
    await w
      .locator(selector)
      .first()
      .evaluate((el) => {
        const r = document.createRange();
        r.selectNodeContents(el);
        getSelection().removeAllRanges();
        getSelection().addRange(r);
        el.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
      });
    await w.locator('#selectionTag').waitFor();
    await w.locator('#selectionTag').click();
    await w.locator('#reviewInstruction').fill('请用直观的例子解释这一段。');
    await w.locator('#reviewSave').click();
    await w
      .locator('#reviewDialog')
      .waitFor({ state: 'hidden', timeout: 5000 })
      .catch(async (e) => {
        console.log(
          'SAVE',
          await w.locator('#toast').textContent(),
          await w.locator('#reviewQuote').textContent(),
        );
        throw e;
      });
  };
  assert.equal(await w.locator('.ai-workbench:visible').count(), 0);
  await select('#preview p');
  await w.evaluate(() => window.pageNotes.menuAction('reviewRight'));
  assert.ok(
    await w
      .locator('.ai-workbench[data-side=note]')
      .evaluate((e) => e.classList.contains('open')),
  );
  assert.equal(
    await w.locator('.ai-workbench[data-side=note] .improvement-card').count(),
    1,
  );
  assert.equal(await w.locator('#noteBody .review-marker').count(), 1);
  await w.waitForTimeout(400);
  console.log(
    'LAYOUT',
    await w.evaluate(() => ({
      state: document
        .querySelector('#workspace')
        .getBoundingClientRect()
        .toJSON(),
      scroll: document
        .querySelector('#documentScroll')
        .getBoundingClientRect()
        .toJSON(),
      sheet: document.querySelector('.document-sheet')?.outerHTML.slice(0, 300),
      panes: document
        .querySelector('.document-pane')
        .getBoundingClientRect()
        .toJSON(),
    })),
  );
  await select('.document-sheet[data-page="1"] .textLayer span');
  await w.evaluate(() => window.pageNotes.menuAction('reviewLeft'));
  assert.equal(
    await w
      .locator('.ai-workbench[data-side=document] .improvement-card')
      .count(),
    1,
  );
  assert.equal(
    await w.locator('.document-sheet[data-page="1"] .review-marker').count(),
    1,
  );
  const board = w.locator('.ai-workbench[data-side=note]');
  await board.locator('.board-add').click();
  await w.locator('#reviewInstruction').fill('整体减少公式，多讲直觉。');
  await w.locator('#reviewSave').click();
  await w.locator('#reviewDialog').waitFor({ state: 'hidden' });
  await expect(board.locator('.improvement-card')).toHaveCount(2);
  const card = board.locator('.improvement-card').first(),
    title = await card.locator('header').boundingBox();
  const draggedId = await card.getAttribute('data-record-id');
  const savedPosition = w.waitForResponse(
    (response) =>
      response.url().endsWith('/ai-reviews/' + draggedId) &&
      response.request().method() === 'PATCH',
  );
  await w.mouse.move(title.x + 30, title.y + 10);
  await w.mouse.down();
  await w.mouse.move(title.x + 140, title.y + 45, { steps: 8 });
  await w.mouse.up();
  const savedResponse = await savedPosition;
  assert.ok(savedResponse.ok());
  const position = (await savedResponse.json()).boardPosition;
  assert.ok(position.x > 50);
  await expect
    .poll(async () =>
      w.evaluate(async (draggedId) => {
        const state = await window.pageNotes.getState();
        return (
          await (
            await fetch('/api/documents/' + state.document.id + '/ai-reviews')
          ).json()
        ).find((r) => r.id === draggedId)?.boardPosition;
      }, draggedId),
    )
    .toEqual(position);
  await board.locator('.board-close').click();
  await w.locator('.ai-workbench[data-side=document] .board-close').click();
  await w.locator('#aiSharedTools [data-ai-pointer=pen]').click();
  await w.locator('[data-ai-pen=black]').click();
  assert.equal(
    (await w.evaluate(() => window.pageNotes.getState())).annotations.active,
    'both',
  );
  for (const selector of [
    '.document-sheet[data-page="1"] .ink-layer',
    '#noteBody .ink-layer',
  ]) {
    const box = await w.locator(selector).boundingBox();
    const scroll = await w
      .locator(selector.startsWith('#') ? '#noteScroll' : '#documentScroll')
      .boundingBox();
    const y = Math.max(box.y + 30, scroll.y + 40);
    await w.mouse.move(box.x + 150, y);
    await w.mouse.down();
    await w.mouse.move(box.x + 210, y + 35, { steps: 6 });
    await w.mouse.up();
  }
  await w.evaluate(() => window.pageNotes.flush());
  const state = await w.evaluate(() => window.pageNotes.getState());
  const ink = await w.evaluate(() => window.pageNotes.getAnnotations());
  assert.ok(Object.values(ink.items.document).some((items) => items.length));
  assert.ok(Object.values(ink.items.note).some((items) => items.length));
  await w.locator('#aiSharedTools [data-ai-pointer=hand]').click();
  const scroller = w.locator('#noteScroll'),
    before = await scroller.evaluate((e) => e.scrollTop),
    box = await scroller.boundingBox();
  await w.mouse.move(box.x + 80, box.y + 180);
  await w.mouse.down();
  await w.mouse.move(box.x + 80, box.y + 80, { steps: 6 });
  await w.mouse.up();
  assert.ok((await scroller.evaluate((e) => e.scrollTop)) > before);
  await w.locator('#aiSharedTools [data-ai-pointer=mouse]').click();
  assert.equal(
    (await w.evaluate(() => window.pageNotes.getState())).annotations.active,
    null,
  );
  const checkAlignment = async () => {
    await w.waitForFunction(
      () => {
        const p = document.querySelector('#preview p'),
          range = document.createRange();
        range.selectNodeContents(p);
        const text = range.getClientRects()[0],
          mark = document
            .querySelector('#noteBody .review-highlight')
            ?.getBoundingClientRect();
        return (
          mark &&
          Math.abs(text.top - mark.top) < 2 &&
          Math.abs(text.left - mark.left) < 2 &&
          Math.abs(text.width - mark.width) < 2
        );
      },
      null,
      { timeout: 3000 },
    );
  };
  await w.evaluate(() => (document.querySelector('#noteScroll').scrollTop = 0));
  const penOffset = await w.evaluate(() => {
    const path = document.querySelector('#noteBody .ink-layer path'),
      p = document.querySelector('#preview p').getBoundingClientRect(),
      svg = path.ownerSVGElement,
      pt = path.getPointAtLength(0),
      matrix = svg.getScreenCTM(),
      screen = new DOMPoint(pt.x, pt.y).matrixTransform(matrix);
    return {
      x:
        (screen.x - p.left) /
        parseFloat(
          getComputedStyle(document.querySelector('#preview p')).fontSize,
        ),
      y:
        (screen.y - p.top) /
        parseFloat(
          getComputedStyle(document.querySelector('#preview p')).fontSize,
        ),
    };
  });
  await w.evaluate(() =>
    window.pageNotes.setLayout({
      noteWidth: 450,
      fontSize: 24,
      leftPercent: 50,
    }),
  );
  await checkAlignment();
  await w
    .waitForFunction((original) => {
      return document
        .querySelector('#noteBody .ink-layer path')
        ?.getAttribute('d');
    }, penOffset)
    .catch(() => {});
  const shifted = await w.evaluate(() => {
    const path = document.querySelector('#noteBody .ink-layer path'),
      p = document.querySelector('#preview p').getBoundingClientRect(),
      pt = path.getPointAtLength(0),
      screen = new DOMPoint(pt.x, pt.y).matrixTransform(
        path.ownerSVGElement.getScreenCTM(),
      ),
      font = parseFloat(
        getComputedStyle(document.querySelector('#preview p')).fontSize,
      );
    return { x: (screen.x - p.left) / font, y: (screen.y - p.top) / font };
  });
  assert.ok(Math.abs(shifted.x - penOffset.x) < 0.1);
  assert.ok(Math.abs(shifted.y - penOffset.y) < 0.1);
  const docBox = await w.locator('#documentScroll').boundingBox();
  await w.mouse.move(docBox.x + 120, docBox.y + 120);
  await w.keyboard.down('Control');
  await w.mouse.wheel(0, -100);
  await w.keyboard.up('Control');
  await w.waitForTimeout(350);
  await w.waitForFunction(
    () => {
      const host = document.querySelector('.document-sheet[data-page="1"]'),
        span = host.querySelector('.textLayer span'),
        mark = host.querySelector('.review-highlight');
      if (!span || !mark) return false;
      const range = document.createRange();
      range.selectNodeContents(span);
      const text = range.getClientRects()[0],
        rect = mark.getBoundingClientRect();
      return (
        text &&
        Math.abs(text.top - rect.top) < 2 &&
        Math.abs(text.left - rect.left) < 2
      );
    },
    null,
    { timeout: 3000 },
  );
  const noteBox = await w.locator('#noteScroll').boundingBox();
  await w.mouse.move(noteBox.x + 100, noteBox.y + 100);
  await w.keyboard.down('Control');
  await w.mouse.wheel(0, -100);
  await w.keyboard.up('Control');
  await w.waitForTimeout(350);
  await checkAlignment();
  await w.evaluate(() =>
    window.pageNotes.setLayout({
      noteWidth: 1000,
      fontSize: 15,
      leftPercent: 42,
    }),
  );
  await w.evaluate(() => window.pageNotes.resetNoteZoom());
  await checkAlignment();
  await w.reload();
  await dismissWelcome(w);
  await w.evaluate((id) => window.pageNotes.open(id), documentId);
  await w.evaluate(() => window.pageNotes.menuAction('reviewRight'));
  await expect(
    w.locator('.ai-workbench[data-side=note] .improvement-card'),
  ).toHaveCount(2);
  await expect(
    w.locator(`.improvement-card[data-record-id="${draggedId}"]`),
  ).toHaveCSS('left', position.x + 'px');
  await w.waitForFunction(
    () =>
      document.querySelector('.ai-workbench[data-side=note] .board-body')
        .offsetHeight >= 315,
  );
  await w.screenshot({
    path: path.join(root, 'tests/ai-workspace-preview.png'),
  });
  console.log(
    'PASS shared mouse/hand/pen; selection tags on both panes; numbered markers; independent workbenches; overall cards; draggable persisted positions; ink on both panes; hand panning; reload recovery.',
  );
} finally {
  await app.close();
}

import { dismissWelcome } from './dismiss-welcome.mjs';
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
    PAGE_NOTES_USER_DATA: path.join(root, 'tests', 'box-tag-' + Date.now()),
  },
});
try {
  const w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.waitForFunction(() => !!window.pageNotes.getSummary().document);
  await w.locator('#loading').waitFor({ state: 'hidden' });
  await w.evaluate(async () => {
    const s = await window.pageNotes.getState();
    await fetch('/api/documents/' + s.document.id + '/notes/1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ markdown: '这段内容需要更直观的解释。' }),
    });
    await window.pageNotes.syncNotes(s.document.id);
  });
  await w.waitForTimeout(400);
  assert.equal(await w.evaluate(()=>document.body.dataset.aiPointer),'mouse');
  for(const selector of ['#preview p','.document-sheet[data-page="1"] .textLayer span']){
    await w.locator('#aiSharedTools [data-ai-pointer=mouse]').click();
    assert.ok(await w.locator('.mouse-options').isVisible());
    await w.locator(selector).first().click();
    assert.ok(await w.locator('.mouse-options').isHidden());
    assert.equal(await w.evaluate(()=>document.body.dataset.aiPointer),'mouse');
    assert.equal(await w.locator('#aiSharedTools [data-ai-pointer=mouse]').getAttribute('aria-expanded'),'false');
  }
  await w.locator('#aiSharedTools [data-ai-pointer=mouse]').click();
  await w.keyboard.press('Escape');
  assert.ok(await w.locator('.mouse-options').isHidden());
  await w.locator('#aiSharedTools [data-ai-pointer=mouse]').click();
  assert.equal(await w.locator('[data-mouse-mode]').count(), 2);
  assert.equal(await w.locator('[data-mouse-mode=highlight]').count(), 0);
  assert.equal(await w.locator('[data-ai-pen]').count(), 3);
  assert.equal(await w.locator('[data-ai-pen] svg').count(), 3);
  for (const [selector, quote, side] of [
    ['#preview p', '更直观', 'note'],
    [
      '.document-sheet[data-page="1"] .textLayer span',
      'Pattern Recognition',
      'document',
    ],
  ]) {
    if (await w.locator('.mouse-options').isHidden())
      await w.locator('#aiSharedTools [data-ai-pointer=mouse]').click();
    await w.locator('[data-mouse-mode=rect]').click();
    const box = await w.locator(selector).first().boundingBox();
    await w.mouse.move(box.x - 3, box.y - 3);
    await w.mouse.down();
    await w.mouse.move(box.x + box.width + 3, box.y + box.height + 3, {
      steps: 12,
    });
    await w.mouse.up();
    await w.locator('#selectionTag').waitFor();
    assert.ok(
      await w.locator('#reviewDialog').isHidden(),
      'box selection offers a tag instead of opening the dialog',
    );
    await w.locator('#selectionTag').click();
    await w.locator('#reviewDialog').waitFor({ state: 'visible' });
    assert.equal(
      await w.locator('#reviewDialog').evaluate((e) => e.matches(':modal')),
      false,
    );
    const pane = await w
        .locator(side === 'note' ? '.note-pane' : '.document-pane')
        .boundingBox(),
      floating = await w.locator('#reviewDialog').boundingBox();
    assert.ok(
      floating.x >= pane.x &&
        floating.x + floating.width <= pane.x + pane.width + 1,
      'tag stays in its own pane',
    );
    const overlap =
      Math.max(
        0,
        Math.min(floating.x + floating.width, box.x + box.width) -
          Math.max(floating.x, box.x),
      ) *
      Math.max(
        0,
        Math.min(floating.y + floating.height, box.y + box.height) -
          Math.max(floating.y, box.y),
      );
    assert.equal(overlap, 0, 'panel avoids selected content');
    const title = await w.locator('#reviewDraftTitle').boundingBox();
    await w.mouse.move(title.x + 40, title.y + 15);
    await w.mouse.down();
    await w.mouse.move(title.x + 15, title.y + 35, { steps: 5 });
    await w.mouse.up();
    const dragged = await w.locator('#reviewDialog').boundingBox();
    assert.ok(
      Math.abs(dragged.x - floating.x) > 5 ||
        Math.abs(dragged.y - floating.y) > 5,
      'floating tag can be dragged',
    );
    assert.ok((await w.locator('#reviewQuote').textContent()).includes(quote));
    await w.locator('#reviewInstruction').fill('请解释框选内容。');
    await w.locator('#reviewSave').click();
    await w.locator('#reviewDialog').waitFor({ state: 'hidden' });
    const state = await w.evaluate(() => window.pageNotes.getState());
    assert.ok(
      state.aiWorkspace.reviews.some(
        (r) =>
          r.side === side &&
          r.kind === 'region' &&
          r.anchor.shape.type === 'rect' &&
          r.quote.includes(quote),
      ),
    );
  }
  await w.locator('#aiSharedTools [data-ai-pointer=pen]').click();
  assert.ok(await w.locator('#aiPenColor').isVisible(), 'pen controls open');
  await w.screenshot({ path: path.join(root, 'tests/pen-menu-preview.png') });
  console.log(
    'PASS default text selection; click-outside and Escape dismiss menus; compact icon menus; box selection on both sides; optional tag; captured text/coordinates persisted.',
  );
} finally {
  await app.close();
}

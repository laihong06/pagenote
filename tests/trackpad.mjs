import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron } from '@playwright/test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const profile = await fs.mkdtemp(path.join(root, 'tmp', 'trackpad-'));
const app = await electron.launch({
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
});
const errors = [];
try {
  const w = await app.firstWindow();
  w.on('pageerror', (e) => errors.push(e.message));
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await w.evaluate(() =>
    window.pageNotes.setLayout({ expandEditor: false, noteAnimation: false }),
  );
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.locator('#loading').waitFor({ state: 'hidden' });
  await w.waitForTimeout(450);
  await w.evaluate(async () => {
    const { document: doc } = await window.pageNotes.getState();
    await fetch(`/api/documents/${doc.id}/ai-reviews`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        side: 'document',
        kind: 'region',
        page: 1,
        instruction: 'Track this region through zoom',
        anchor: {
          shape: {
            type: 'rect',
            points: [
              [300, 250],
              [500, 400],
            ],
          },
        },
      }),
    });
    await window.pageNotes.reloadReviews();
  });
  const regionPosition = () =>
    w
      .locator('.document-sheet')
      .first()
      .evaluate((host) => {
        const r = host.getBoundingClientRect(),
          b = host.querySelector('.review-marker').getBoundingClientRect();
        return { x: (b.left - r.left) / r.width, y: (b.top - r.top) / r.width };
      });
  const regionBefore = await regionPosition();
  // A subpixel pinch must be visible on the next frame, not after a debounce.
  const fine = await w.evaluate(async () => {
    const sheet = document.querySelector('.document-sheet'),
      canvas = sheet.querySelector('canvas'),
      span = sheet.querySelector('.textLayer span'),
      r = sheet.getBoundingClientRect(),
      width = canvas.width;
    window.gestureText = span;
    sheet.dispatchEvent(
      new WheelEvent('wheel', {
        bubbles: true,
        cancelable: true,
        ctrlKey: true,
        deltaY: -0.8,
        clientX: r.left + r.width / 2,
        clientY: r.top + 140,
      }),
    );
    await new Promise(requestAnimationFrame);
    return {
      ratio: sheet.getBoundingClientRect().width / r.width,
      sameCanvas: canvas === sheet.querySelector('canvas'),
      sameText: span === sheet.querySelector('.textLayer span'),
      backingUnchanged: canvas.width === width,
      initialWidth: width,
    };
  });
  assert.ok(
    fine.ratio > 1.001 && fine.ratio < 1.04,
    'fine pinch follows delta immediately: ' + JSON.stringify(fine),
  );
  assert.ok(
    fine.sameCanvas && fine.sameText && fine.backingUnchanged,
    'pinch keeps bitmap and text DOM until settle',
  );

  async function pinch(selector, delta = -8, count = 12) {
    return w
      .locator(selector)
      .first()
      .evaluate(
        async (host, { delta, count }) => {
          const r = host.getBoundingClientRect(),
            scroller = host.closest('#documentScroll,#noteScroll'),
            s = scroller.getBoundingClientRect(),
            x = Math.max(r.left, s.left) + 140,
            y = Math.max(r.top, s.top) + 130,
            focal = {
              x,
              y,
              fx: (x - r.left) / r.width,
              fy: (y - r.top) / r.height,
            };
          for (let i = 0; i < count; i++) {
            host.dispatchEvent(
              new WheelEvent('wheel', {
                bubbles: true,
                cancelable: true,
                ctrlKey: true,
                deltaY: delta,
                clientX: x,
                clientY: y,
              }),
            );
            await new Promise(requestAnimationFrame);
          }
          return focal;
        },
        { delta, count },
      );
  }
  await pinch('.document-sheet');
  const beforeSettle = await w
    .locator('.document-sheet canvas')
    .first()
    .evaluate((c) => ({
      width: c.width,
      screen: c.getBoundingClientRect().width,
    }));
  assert.ok(
    beforeSettle.width === fine.initialWidth && beforeSettle.screen > 500,
    'bitmap scales throughout gesture before high resolution render',
  );
  await w.waitForTimeout(500);
  assert.ok(
    await w.evaluate(
      () =>
        window.gestureText ===
        document.querySelector('.document-sheet .textLayer span'),
    ),
    'settling does not rebuild text DOM or lose selection',
  );
  const sharp = await w
    .locator('.document-sheet canvas')
    .first()
    .evaluate((c) => ({
      width: c.width,
      screen: c.getBoundingClientRect().width,
      ratio: devicePixelRatio,
    }));
  assert.ok(
    Math.abs(sharp.width - sharp.screen * sharp.ratio) < 5,
    'settled PDF matches display resolution: ' + JSON.stringify(sharp),
  );
  const regionAfter = await regionPosition();
  assert.ok(
    Math.abs(regionAfter.x - regionBefore.x) < 0.005 &&
      Math.abs(regionAfter.y - regionBefore.y) < 0.005,
    'region review badge follows its source through settled zoom',
  );
  const anchor = await pinch('.document-sheet', -2, 4);
  await w.waitForTimeout(350);
  const focalAfter = await w
    .locator('.document-sheet')
    .first()
    .evaluate((s, a) => {
      const r = s.getBoundingClientRect();
      return {
        dx: r.left + r.width * a.fx - a.x,
        dy: r.top + r.height * a.fy - a.y,
      };
    }, anchor);
  assert.ok(
    Math.abs(focalAfter.dx) < 3 && Math.abs(focalAfter.dy) < 3,
    'pinch preserves focal point in both axes: ' + JSON.stringify(focalAfter),
  );
  assert.equal(
    await w.locator('#pageNumber').inputValue(),
    '1',
    'pinch does not switch notes',
  );

  async function pan(scroller) {
    const box = await w.locator(scroller).boundingBox();
    await w.locator(scroller).evaluate((s) => {
      s.scrollLeft = 0;
      s.scrollTop = 0;
    });
    await w.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
    await w.mouse.wheel(91, 133);
    await w.waitForTimeout(180);
    const moved = await w
      .locator(scroller)
      .evaluate((s) => ({ x: s.scrollLeft, y: s.scrollTop }));
    assert.ok(
      moved.x > 60 && moved.y > 100,
      scroller + ' supports native diagonal pan: ' + JSON.stringify(moved),
    );
  }
  await w.locator('#aiSharedTools [data-ai-pointer=pen]').click();
  await pan('#documentScroll');
  await w.locator('#aiSharedTools [data-ai-pointer=mouse]').click();
  await w.evaluate(async () => {
    const { document: doc } = await window.pageNotes.getState();
    const markdown =
      '# Trackpad notes\n\n' +
      'A paper paragraph with stable line wrapping and ink.\n\n'.repeat(60);
    await fetch(`/api/documents/${doc.id}/notes/1`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ markdown }),
    });
    await window.pageNotes.syncNotes(doc.id);
  });
  const originalNote = await w.locator('#preview').innerText();
  await pinch('#noteBody');
  await w.waitForTimeout(250);
  assert.equal(await w.locator('#preview').innerText(), originalNote);
  await pan('#noteScroll');
  await w.evaluate(() => window.pageNotes.resetNoteZoom());
  const fitNote = await w
    .locator('#noteScroll')
    .evaluate((s) => s.scrollWidth <= s.clientWidth + 2);
  assert.ok(fitNote, 'reset note zoom restores fitted page');

  assert.deepEqual(errors, []);
  console.log(
    'PASS Windows-compatible continuous pinch, PDF/text retention, anchor preservation, Tag position and native diagonal pan',
  );
} finally {
  await app.close();
}

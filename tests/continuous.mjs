import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron } from '@playwright/test';
import path from 'node:path';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
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
      'tests/continuous-data-' + Date.now(),
    ),
  },
  timeout: 40000,
});
try {
  const w = await app.firstWindow();
  // Keep the full footer controls visible on small CI desktops.
  await app.evaluate(({ BrowserWindow }) =>
    BrowserWindow.getAllWindows()[0].setSize(1600, 1000),
  );
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await w.evaluate(() => window.pageNotes.setLayout({ expandEditor: false }));
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.waitForFunction(() => !!window.pageNotes.getSummary().document);
  await w.locator('#loading').waitFor({ state: 'hidden' });
  await w.evaluate(async () => {
    const s = await window.pageNotes.getState();
    await window.pageNotes.open(s.document.id, 1);
  });
  assert.equal(await w.locator('.document-sheet').count(), 3);
  await w.evaluate(() => window.pageNotes.menuAction('chooseEditor'));
  await w.locator('.note-pane .pane-heading').click();
  await w.locator('#preview').fill('第一页尚未保存的笔记');
  const crossMidpoint = (offset) =>
    w.evaluate((offset) => {
      const el = document.querySelector('#documentScroll');
      const sheets = [...document.querySelectorAll('.document-sheet')];
      const top =
        (sheets[0].offsetTop + sheets[0].offsetHeight + sheets[1].offsetTop) /
          2 -
        el.clientHeight / 2;
      el.dispatchEvent(new WheelEvent('wheel'));
      el.scrollTop = top + offset;
    }, offset);
  await crossMidpoint(-12);
  await w.waitForTimeout(200);
  assert.equal(
    await w.locator('#pageNumber').inputValue(),
    '1',
    'before midpoint keeps first explanation',
  );
  const visible = await w.locator('.document-sheet').evaluateAll((es) => {
    const r = document.querySelector('#documentScroll').getBoundingClientRect();
    return es.slice(0, 2).every((e) => {
      const b = e.getBoundingClientRect();
      return b.bottom > r.top && b.top < r.bottom;
    });
  });
  assert.ok(visible, 'both neighboring pages visible before switch');
  await crossMidpoint(12);
  await w.waitForFunction(
    () => document.querySelector('#pageNumber').value === '2',
  );
  await w.locator('#preview').fill('第二页自己的笔记');
  await crossMidpoint(-12);
  await w.waitForFunction(
    () => document.querySelector('#pageNumber').value === '1',
  );
  assert.equal(await w.locator('#editor').inputValue(), '第一页尚未保存的笔记');
  const state = await w.evaluate(() => window.pageNotes.readPages([1, 2]));
  assert.equal(state[1].markdown, '第二页自己的笔记');
  await w.locator('#next').click();
  await w.waitForFunction(
    () => document.querySelector('#pageNumber').value === '2',
  );
  await w.waitForTimeout(700);
  assert.equal(await w.locator('#editor').inputValue(), '第二页自己的笔记');
  await w.mouse.move(250, 400);
  await w.keyboard.down('Control');
  await w.mouse.wheel(0, -120);
  await w.keyboard.up('Control');
  await w.waitForTimeout(600);
  assert.equal(await w.locator('#pageNumber').inputValue(), '2');
  assert.equal(await w.locator('.document-sheet').count(), 3);
  await w.evaluate(() => window.pageNotes.waitVisuals());
  // Small sheets zoom around their center, irrespective of cursor position.
  await w.evaluate(async () => {
    const s = await window.pageNotes.getState();
    await window.pageNotes.open(s.document.id, 1);
  });
  for (let i = 0; i < 7; i++) {
    await w.locator('#zoomOut').click();
    await w.waitForTimeout(80);
  }
  const center = () =>
    w.locator('[data-page="1"]').evaluate((e) => {
      const r = e.getBoundingClientRect();
      return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
    });
  const beforeCenter = await center();
  await w.locator('[data-page="1"]').evaluate((e) => {
    const r = e.getBoundingClientRect();
    e.dispatchEvent(
      new WheelEvent('wheel', {
        bubbles: true,
        cancelable: true,
        ctrlKey: true,
        deltaY: -120,
        clientX: r.left + 20,
        clientY: r.top + 20,
      }),
    );
  });
  await w.waitForTimeout(350);
  const afterCenter = await center();
  assert.ok(
    Math.abs(afterCenter.x - beforeCenter.x) < 3 &&
      Math.abs(afterCenter.y - beforeCenter.y) < 3,
    'small page center stays still',
  );
  // Once full, preserve the actual point under the pointer in both axes.
  // Compact Mac CI displays hide this optional footer label below 1200px.
  // Exercise the same fit handler without requiring the desktop's screen size.
  const fit = w.locator('#fit');
  if (await fit.isVisible()) await fit.click();
  else await fit.evaluate((button) => button.click());
  await w.waitForTimeout(200);
  await w.locator('#zoomIn').click();
  await w.waitForTimeout(200);
  const focal = await w.locator('[data-page="1"]').evaluate((e) => {
    const r = e.getBoundingClientRect();
    const x = r.left + 140,
      y = r.top + 160;
    e.dispatchEvent(
      new WheelEvent('wheel', {
        bubbles: true,
        cancelable: true,
        ctrlKey: true,
        deltaY: -120,
        clientX: x,
        clientY: y,
      }),
    );
    return { x, y, fx: (x - r.left) / r.width, fy: (y - r.top) / r.height };
  });
  await w.waitForTimeout(350);
  const shifted = await w.locator('[data-page="1"]').evaluate((e, a) => {
    const r = e.getBoundingClientRect();
    return { x: r.left + r.width * a.fx, y: r.top + r.height * a.fy };
  }, focal);
  assert.ok(
    Math.abs(shifted.x - focal.x) < 3 && Math.abs(shifted.y - focal.y) < 3,
    'pointer content stays still',
  );
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
    path.join(root, 'tests/continuous-preview.png'),
    Buffer.from(png, 'base64'),
  );
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.md'));
  await w.waitForFunction(() => !!window.pageNotes.getSummary().document);
  await w.locator('#loading').waitFor({ state: 'hidden' });
  await w.evaluate(async () => {
    const s = await window.pageNotes.getState();
    await window.pageNotes.open(s.document.id, 1);
  });
  assert.equal(await w.locator('.document-sheet').count(), 2);
  await w.locator('#next').click();
  await w.waitForFunction(
    () => document.querySelector('#pageNumber').value === '2',
  );
  console.log(
    'PASS continuous PDF/Markdown; midpoint in both directions; unsaved notes; smooth buttons; centered small-page zoom; pointer-anchored full-page zoom.',
  );
} finally {
  await app.close();
}

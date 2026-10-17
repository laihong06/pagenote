import { _electron as electron } from '@playwright/test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';

// A diagram's temporary SVG must never take space from the reading panes.
// Observe both footers during real asynchronous Mermaid rendering, including
// cold/warm renders and syntax errors, rather than checking only the final DOM.
const root = path.resolve(import.meta.dirname, '..');
const profile = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-footer-'));
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
const formula = String.raw`$$p(x)=\frac{1}{\sqrt{2\pi\sigma^2}}e^{-\frac{(x-\mu)^2}{2\sigma^2}}$$`;
const notes = [
  '# 第一页\n\n这一页也有笔记。',
  '# 第二页\n\n' +
    Array.from(
      { length: 40 },
      (_, i) => `第 ${i + 1} 个公式\n\n${formula}`,
    ).join('\n\n') +
    '\n\n```mermaid\nflowchart TD\n A[原始数据] --> B[特征提取]\n B --> C{分类判断}\n C --> D[第一类]\n C --> E[第二类]\n```',
  '# 第三页\n\n```mermaid\nflowchart TD\n A --> [\n```',
];
let passed = false;
try {
  const w = await app.firstWindow();
  const errors = [];
  w.on('pageerror', (error) => errors.push(error.message));
  await w.waitForFunction(() => !!window.pageNotes);
  await w.evaluate(() => window.pageNotes.setLayout({ expandEditor: false }));
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.locator('#loading').waitFor({ state: 'hidden' });
  await w.evaluate(async (notes) => {
    const { document: doc } = await window.pageNotes.getState();
    for (const [i, markdown] of notes.entries()) {
      const response = await fetch(`/api/documents/${doc.id}/notes/${i + 1}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ markdown }),
      });
      if (!response.ok) throw Error('Failed to prepare page notes');
    }
    await window.pageNotes.syncNotes(doc.id);
    await window.pageNotes.open(doc.id, 1);
    await window.pageNotes.waitVisuals();
  }, notes);

  const navigate = async (label, action, page, diagramClass) => {
    await w.evaluate(() => {
      const read = () =>
        [...document.querySelectorAll('.pane-footer')].map((el) => {
          const { top, bottom, height } = el.getBoundingClientRect();
          return { top, bottom, height };
        });
      const baseline = read();
      window.footerAddedRoots = [];
      window.footerRootObserver = new MutationObserver((changes) => {
        for (const change of changes)
          for (const node of change.addedNodes)
            if (node instanceof Element) window.footerAddedRoots.push(node);
      });
      window.footerRootObserver.observe(document.body, { childList: true });
      window.footerProbe = { baseline, frames: 0, moved: [] };
      const tick = () => {
        const probe = window.footerProbe;
        probe.frames++;
        const positions = read();
        if (
          positions.some(
            (r, i) =>
              Math.abs(r.top - baseline[i].top) > 1 ||
              Math.abs(r.bottom - baseline[i].bottom) > 1 ||
              Math.abs(r.height - baseline[i].height) > 1,
          )
        ) {
          if (probe.moved.length < 10)
            probe.moved.push({
              positions,
              rows: getComputedStyle(document.body).gridTemplateRows,
            });
        }
        probe.frame = requestAnimationFrame(tick);
      };
      tick();
    });
    await action();
    await w.waitForFunction(
      (page) => document.querySelector('#pageNumber').value === String(page),
      page,
    );
    await w.waitForFunction(
      (note) => document.querySelector('#editor').value === note,
      notes[page - 1],
    );
    if (diagramClass) {
      await w
        .locator(`#preview .mermaid-diagram.${diagramClass}`)
        .waitFor({ state: 'attached', timeout: 30000 });
    }
    await w.evaluate(() => window.pageNotes.waitVisuals());
    // Keep observing through the asynchronous render's cleanup and final paint.
    await w.evaluate(async () => {
      for (let i = 0; i < 8; i++) await new Promise(requestAnimationFrame);
      cancelAnimationFrame(window.footerProbe.frame);
      window.footerRootObserver.disconnect();
    });
    const probe = await w.evaluate(() => window.footerProbe);
    assert.equal(
      await w.evaluate(
        () => window.footerAddedRoots.filter((el) => el.isConnected).length,
      ),
      0,
      'temporary rendering elements are removed after success and errors',
    );
    assert.equal(
      probe.baseline.length,
      2,
      'observe page-count and word-count footers',
    );
    assert.equal(
      probe.moved.length,
      0,
      `${label}: footer moved during rendering: ${JSON.stringify(probe)}`,
    );
    if (diagramClass === 'diagram-ready') {
      assert.equal(await w.locator('#preview .katex').count(), 40);
      assert.ok(await w.locator('#preview .mermaid-diagram svg').count());
      assert.ok(
        await w.locator('#preview .mermaid-diagram svg').isVisible(),
        'finished diagram is visible after the hidden measurement host is removed',
      );
      const size = await w
        .locator('#preview .mermaid-diagram svg')
        .evaluate((el) => ({
          width: el.viewBox.baseVal.width,
          height: el.viewBox.baseVal.height,
        }));
      assert.ok(
        size.width > 100 && size.height > 100,
        'diagram retains measurable geometry',
      );
    }
    console.log(`PASS footer stability: ${label} (${probe.frames} frames)`);
  };

  await navigate(
    'next page, cold diagram render',
    () => w.locator('#next').click(),
    2,
    'diagram-ready',
  );
  await navigate(
    'previous page with notes',
    () => w.locator('#prev').click(),
    1,
  );
  await navigate(
    'next page, warm diagram render',
    () => w.locator('#next').click(),
    2,
    'diagram-ready',
  );
  await navigate(
    'two-finger scrolling to invalid diagram',
    async () => {
      const point = await w.locator('#documentScroll').boundingBox();
      await w.mouse.move(point.x + point.width / 2, point.y + point.height / 2);
      await w.mouse.wheel(0, 900);
    },
    3,
    'diagram-error',
  );
  await navigate(
    'previous page after diagram error',
    () => w.locator('#prev').click(),
    2,
    'diagram-ready',
  );
  assert.deepEqual(errors, []);
  passed = true;
} finally {
  await app.close();
  if (passed) await fs.rm(profile, { recursive: true, force: true });
  else console.log('Footer regression profile:', profile);
}

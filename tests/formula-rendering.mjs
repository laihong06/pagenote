import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron } from '@playwright/test';
import assert from 'node:assert/strict';
import path from 'node:path';
const root = path.resolve(import.meta.dirname, '..');
const markdownBase =
  String.raw`# Formula compatibility

Inline $\mu$, normal \(\sigma^2\), copied \\(\\theta\\), mixed \\(\alpha\\).

$$\frac{1}{N}\sum_{i=1}^N x_i$$

\[p(x)=\frac{1}{\sqrt{2\pi}}\exp(-x^2/2)\]

\\[x\sim\mathcal N(\mu,\sigma^2)\\]

\\[\\frac{1}{2}+\\sqrt{4}\\]

\[\begin{pmatrix}1&2\\3&4\end{pmatrix}\]

\\[\begin{pmatrix}1&2\\3&4\end{pmatrix}\\]

` +
  '```latex\n' +
  String.raw`\hat\mu=\frac1N\sum_i x_i` +
  '\n```\n\n' +
  '```python\n' +
  String.raw`text = "\\[not math\\]"` +
  '\n```\n\nEnd of page.';
const issueFormula =
  String.raw`\frac{(\mathbf w^{\mathsf T}\boldsymbol\delta)^2}{\mathbf w^{\mathsf T}S_w\mathbf w}` +
  '\n' +
  String.raw`\le\boldsymbol\delta^{\mathsf T}S_w^{-1}\boldsymbol\delta.`;
const markdown =
  markdownBase +
  '\n\n$$' +
  issueFormula +
  '$$\n\n紧邻正文$$' +
  issueFormula +
  '$$继续解释。\n\n$$\n' +
  issueFormula +
  '\n$$\n\n紧邻段落\n$$\n\\theta=1\n$$\n继续正文。';
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
  env: {
    ...process.env,
    PAGE_NOTES_USER_DATA: path.join(root, 'tests/formula-data-' + Date.now()),
  },
});
try {
  const w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  const remote = [];
  await w.context().route('**/*', (route) => {
    const u = new URL(route.request().url());
    if (
      ['http:', 'https:'].includes(u.protocol) &&
      u.hostname !== '127.0.0.1'
    ) {
      remote.push(u.hostname);
      return route.abort();
    }
    return route.continue();
  });
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.waitForFunction(() => !!window.pageNotes.getSummary().document);
  await w.locator('#loading').waitFor({ state: 'hidden' });
  const id = await w.evaluate(async (markdown) => {
    const s = await window.pageNotes.getState();
    await fetch('/api/documents/' + s.document.id + '/notes/1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ markdown }),
    });
    await window.pageNotes.syncNotes(s.document.id);
    return s.document.id;
  }, markdown);
  const check = async () => {
    await w.evaluate(() => document.fonts.ready);
    assert.equal(await w.locator('#preview .katex-error').count(), 0);
    assert.equal(await w.locator('#preview .katex').count(), 15);
    assert.equal(await w.locator('#preview .katex-display').count(), 11);
    assert.equal(await w.locator('#preview .katex .mtable').count(), 2);
    assert.ok(
      (await w.locator('#preview pre code').textContent()).includes('not math'),
    );
    const text = await w.locator('#preview').evaluate((el) => {
      const c = el.cloneNode(true);
      c.querySelectorAll('.katex,pre').forEach((n) => n.remove());
      return c.textContent;
    });
    assert.ok(
      !text.includes('\\'),
      'no raw delimiter or command slashes in prose',
    );
    const fonts = await w.evaluate(
      () =>
        [...document.fonts].filter(
          (f) => f.family.includes('KaTeX') && f.status === 'loaded',
        ).length,
    );
    assert.ok(fonts > 0, 'bundled math fonts load offline');
  };
  if (process.env.FORMULA_SCREENSHOT)
    await w.screenshot({ path: process.env.FORMULA_SCREENSHOT });
  await check();
  await w.evaluate(() => window.pageNotes.menuAction('chooseEditor'));
  await w.locator('.note-pane .pane-heading').click();
  await w.locator('#preview > p').last().click();
  await w.keyboard.press('End');
  await w.keyboard.insertText(' Saved.');
  await w.evaluate(() => window.pageNotes.flush());
  await w.locator('#noteEditButton').click();
  await w.evaluate((id) => window.pageNotes.syncNotes(id), id);
  await check();
  assert.deepEqual(remote, []);
  console.log(
    'PASS offline dollar/TeX/doubled-escape formulas, matrix rows, math fences, bundled fonts and edit/save roundtrip',
  );
} finally {
  await app.close();
}

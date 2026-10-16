import { _electron as electron } from '@playwright/test';
import { dismissWelcome } from './dismiss-welcome.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const minutes = Number(process.env.TEST_SOAK_MINUTES || 60);
assert.ok(Number.isFinite(minutes) && minutes > 0 && minutes <= 240);
const output = path.resolve(process.env.ACCEPTANCE_OUTPUT || 'output/soak');
await fs.mkdir(output, { recursive: true });
const profile = path.join(root, 'tests', 'soak-data-' + Date.now());
await fs.mkdir(profile, { recursive: true });
// A synthetic 60-page PDF keeps personal course material out of test evidence.
const font = 123;
const objects = [
  '<< /Type /Catalog /Pages 2 0 R >>',
  `<< /Type /Pages /Count 60 /Kids [${Array.from({ length: 60 }, (_, i) => `${3 + i * 2} 0 R`).join(' ')}] >>`,
];
for (let page = 1; page <= 60; page++) {
  const content = `BT /F1 18 Tf 60 700 Td (Pattern Recognition Page ${page}) Tj 0 -30 Td (Linear models and candidate acceptance.) Tj ET`;
  objects.push(
    `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 ${font} 0 R >> >> /Contents ${4 + (page - 1) * 2} 0 R >>`,
    `<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}\nendstream`,
  );
}
objects.push('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>');
let pdf = '%PDF-1.4\n';
const offsets = [0];
objects.forEach((object, i) => {
  offsets.push(Buffer.byteLength(pdf));
  pdf += `${i + 1} 0 obj\n${object}\nendobj\n`;
});
const xref = Buffer.byteLength(pdf);
pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n${offsets
  .slice(1)
  .map((n) => `${String(n).padStart(10, '0')} 00000 n \n`)
  .join(
    '',
  )}trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
const file = path.join(profile, '60-page-course.pdf');
await fs.writeFile(file, pdf);
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
    args: process.env.DESKTOP_EXE ? [] : [root],
    env: { ...process.env, PAGE_NOTES_USER_DATA: profile },
    timeout: 40000,
  });
let app = await launch();
const samples = [],
  errors = [],
  expected = new Map();
const start = Date.now();
let iterations = 0,
  id;
try {
  let w = await app.firstWindow();
  w.on('pageerror', (error) => errors.push(error.message));
  w.on('crash', () => errors.push('renderer crashed'));
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await w.locator('#fileInput').setInputFiles(file);
  await w.locator('#loading').waitFor({ state: 'hidden' });
  const initial = await w.evaluate(() => window.pageNotes.getState());
  assert.equal(initial.total, 60);
  id = initial.document.id;
  for (let p = 1; p <= 60; p++) {
    const markdown = `# Note ${p}\n\nPage-specific persisted text.\n\n$$\nw^T x+b=0\n$$\n\n\`\`\`python\nx = ${p}\nprint(x)\n\`\`\`\n\n\`\`\`mermaid\nflowchart LR\n A[Input] --> B[Output]\n\`\`\``;
    expected.set(p, markdown);
    await w.evaluate(
      async ({ id, p, markdown }) => {
        const r = await fetch(`/api/documents/${id}/notes/${p}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ markdown }),
        });
        if (!r.ok) throw Error('seed note failed: ' + r.status);
      },
      { id, p, markdown },
    );
  }
  await w.evaluate((id) => window.pageNotes.syncNotes(id), id);
  // Existing course and renderer resources remain usable without WAN access.
  await w.context().route('**/*', (route) => {
    const u = new URL(route.request().url());
    return ['http:', 'https:'].includes(u.protocol) &&
      !['localhost', '127.0.0.1'].includes(u.hostname)
      ? route.abort()
      : route.continue();
  });
  while (Date.now() - start < minutes * 60000) {
    const p = (iterations % 60) + 1;
    await w.evaluate(({ id, p }) => window.pageNotes.open(id, p), { id, p });
    await w.waitForFunction(
      (p) => document.querySelector('#preview h1')?.textContent === `Note ${p}`,
      p,
    );
    await w.locator('#preview .katex').first().waitFor();
    await w.locator('#preview .diagram-ready svg').first().waitFor();
    const state = await w.evaluate(() => window.pageNotes.getState());
    assert.equal(state.page, p);
    assert.match(
      state.sourceText,
      new RegExp(`Page ${p}(?:\\s|$)`),
      'PDF and note page match',
    );
    assert.equal(state.markdown, expected.get(p));
    if (iterations % 6 === 0) {
      if (
        (await w.evaluate(() => window.pageNotes.getState())).mode !== 'edit'
      ) {
        await w.evaluate(() => window.pageNotes.menuAction('chooseEditor'));
        await w.locator('.note-pane .pane-heading').click();
      }
      // Type in an ordinary paragraph rather than after a noneditable diagram.
      await w.locator('#preview p').first().click();
      await w.keyboard.press('End');
      await w.keyboard.insertText(` Saved iteration ${iterations}.`);
      await w.evaluate(() => window.pageNotes.finishEditing());
      const saved = await w.evaluate(() => window.pageNotes.getState());
      assert.ok(saved.markdown.includes(`Saved iteration ${iterations}.`));
      expected.set(p, saved.markdown);
    }
    if (iterations % 12 === 0) {
      const metrics = await app.evaluate(({ app }) =>
        app.getAppMetrics().map((m) => ({
          type: m.type,
          workingSetKB: m.memory.workingSetSize,
        })),
      );
      samples.push({
        elapsedSeconds: Math.round((Date.now() - start) / 1000),
        iterations,
        workingSetMB: Math.round(
          metrics.reduce((n, m) => n + m.workingSetKB, 0) / 1024,
        ),
        metrics,
      });
      await fs.writeFile(
        path.join(output, 'soak-progress.json'),
        JSON.stringify(
          { requestedMinutes: minutes, profile, samples, iterations, errors },
          null,
          2,
        ),
      );
      console.log('SOAK', JSON.stringify(samples.at(-1)));
    }
    iterations++;
    await new Promise((resolve) => setTimeout(resolve, 5000));
  }
  await w.evaluate(() => window.pageNotes.flush());
  await app.close();
  app = await launch();
  w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await w.evaluate((id) => window.pageNotes.open(id, 1), id);
  const persisted = await w.evaluate(() =>
    window.pageNotes.readPages(Array.from({ length: 60 }, (_, i) => i + 1)),
  );
  assert.equal(persisted.length, 60);
  for (const note of persisted)
    assert.equal(
      note.markdown,
      expected.get(note.page),
      `page ${note.page} survives restart`,
    );
  assert.deepEqual(errors, []);
  const result = {
    passed: true,
    requestedMinutes: minutes,
    elapsedSeconds: Math.round((Date.now() - start) / 1000),
    pages: 60,
    iterations,
    samples,
    errors,
    executablePath,
  };
  await fs.writeFile(
    path.join(output, 'soak-result.json'),
    JSON.stringify(result, null, 2),
  );
  console.log('PASS', JSON.stringify({ ...result, samples: samples.length }));
} catch (error) {
  await fs.writeFile(
    path.join(output, 'soak-failure.json'),
    JSON.stringify(
      { error: String(error.stack || error), iterations, samples, errors },
      null,
      2,
    ),
  );
  throw error;
} finally {
  await app.close();
}

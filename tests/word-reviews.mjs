import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
const root = path.resolve(import.meta.dirname, '..'),
  folder = path.join(root, 'tests', `word-reviews-${Date.now()}`),
  executable =
    process.env.DESKTOP_EXE ||
    path.join(
      root,
      process.platform === 'darwin'
        ? 'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron'
        : 'node_modules/electron/dist/electron.exe',
    );
await fs.mkdir(folder, { recursive: true });
const app = await electron.launch({
  executablePath: executable,
  args: [root],
  env: { ...process.env, PAGE_NOTES_USER_DATA: folder },
  timeout: 40000,
});
try {
  const w = await app.firstWindow();
  w.on('pageerror', (e) => console.error('PAGE ERROR', e.message));
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  const chineseFile = path.join(folder, '中文编辑测试.docx');
  await fs.copyFile(path.join(root, 'tests/fixtures/sample.docx'), chineseFile);
  await w.locator('#fileInput').setInputFiles(chineseFile);
  await w.locator('#loading').waitFor({ state: 'hidden', timeout: 120000 });
  assert.ok(await w.locator('#markdownTools').isHidden());
  assert.ok(await w.locator('#wordTools').isHidden());
  assert.ok((await w.locator('.textLayer span').count()) > 0);
  await w.locator('#documentEditButton').click();
  await w.locator('#wordTools').waitFor({ state: 'visible', timeout: 120000 });
  await w.waitForTimeout(1200);
  const capture = await app.evaluate(
    async ({ desktopCapturer, BrowserWindow }) => {
      const window = BrowserWindow.getAllWindows().find((w) => w.isVisible());
      const sources = await desktopCapturer.getSources({
        types: ['window'],
        thumbnailSize: { width: 1440, height: 960 },
      });
      const source = sources.find((s) => s.id === window.getMediaSourceId());
      return source?.thumbnail.toPNG().toString('base64');
    },
  );
  if (capture)
    await fs.writeFile(
      path.join(root, 'tests/word-native-preview.png'),
      Buffer.from(capture, 'base64'),
    );
  const connection = JSON.parse(
    await fs.readFile(path.join(folder, 'ai-connection.json'), 'utf8'),
  );
  const ai = async (action, args = {}) => {
    const response = await fetch(connection.url + '/api/ai/call', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer ' + connection.token,
      },
      body: JSON.stringify({ action, args }),
    });
    const result = await response.json();
    if (!response.ok) throw Error(result.error);
    return result;
  };
  const nativeImage = await ai('screenshot', { scope: 'word' });
  assert.ok(nativeImage.imageBase64.length > 10000);
  await fs.writeFile(
    path.join(root, 'tests/word-live-preview.png'),
    Buffer.from(nativeImage.imageBase64, 'base64'),
  );
  const source = await ai('read_word');
  assert.ok(source.text.includes('Document parsing test'));
  assert.ok(source.text.includes('Feature'));
  await app.evaluate(async ({ Menu }) => {
    await Menu.getApplicationMenu()
      .items.find((i) => i.label === '编辑')
      .submenu.items.find((i) => i.label === '全选')
      .click();
  });
  await w.locator('#wordSelectionReview').click();
  await w.locator('#reviewDialog').waitFor({ state: 'visible' });
  assert.ok(
    (await w.locator('#reviewQuote').innerText()).includes(
      'Document parsing test',
    ),
  );
  await w.locator('#reviewCancel').click();
  await w.locator('#reviewClose').click();
  const changed = await ai('patch_word', {
    start: source.text.indexOf('Document parsing test'),
    search: 'Document parsing test',
    replacement: 'Word editing roundtrip verified 中文内容 x^2',
    expectedRevision: source.revision,
  });
  assert.ok(changed.saved);
  assert.ok(changed.text.includes('中文内容'), JSON.stringify(changed.text));
  await w.locator('#wordTools').waitFor({ state: 'hidden', timeout: 120000 });
  const state = await w.evaluate(() => window.pageNotes.getState());
  assert.ok(state.sourceText.includes('Word editing roundtrip verified'));
  assert.ok(
    state.sourceText.replace(/\s/g, '').includes('中文内容'),
    JSON.stringify(state.sourceText),
  );
  assert.ok(state.sourceText.includes('Feature'), 'table retained');
  const repeated = await ai('read_word');
  const precise = await ai('patch_word', {
    search: 'page',
    start: repeated.rangeEnd - 6,
    replacement: 'unit',
    expectedRevision: repeated.revision,
  });
  assert.ok(precise.text.includes('must follow this unit.'));
  assert.ok(precise.text.includes('First page:'));
  const tail = await ai('patch_word', {
    search: 'Independent notes must follow this unit.',
    replacement: 'Independent notes must follow this unit. Verified.',
    expectedRevision: precise.revision,
  });
  assert.ok(tail.text.includes('unit. Verified.'));
  const caret = await ai('patch_word', {
    search: 'x^2',
    replacement: 'x squared',
    expectedRevision: tail.revision,
  });
  assert.ok(caret.text.includes('x squared'));
  await ai('read_word');
  const status = await w.evaluate(async () => {
    const r = await fetch('/api/desktop/word', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'status' }),
    });
    return r.json();
  });
  await promisify(execFile)(
    'powershell.exe',
    [
      '-NoProfile',
      '-File',
      path.join(root, 'tests/native-word-close.ps1'),
      '-WindowHandle',
      status.handle,
    ],
    { windowsHide: true },
  );
  await w.locator('#wordTools').waitFor({ state: 'hidden', timeout: 120000 });
  assert.ok(
    (await w.evaluate(() => window.pageNotes.getState())).sourceText
      .replace(/\s/g, '')
      .includes('中文内容'),
    'native close preserves saved source',
  );
  await w.locator('#noteEditButton').click();
  await w
    .locator('#editor')
    .fill('第一段：复杂公式需要解释。\n\n第二段：保持原样。');
  await w.evaluate(() => window.pageNotes.flush());
  await w.locator('[data-mode="read"]').click();
  await w.locator('#aiReviewButton').click();
  await w.locator('#reviewOverall').click();
  await w.locator('#reviewScope').selectOption('document');
  await w
    .locator('#reviewInstruction')
    .fill('整体解释公式太多，请增加直观讲述。');
  await w.locator('#reviewSave').click();
  await w.locator('.review-card').waitFor();
  let records = await ai('read_ai_reviews', { status: 'open' });
  assert.equal(records.records.length, 1);
  assert.equal(records.records[0].scope, 'document');
  assert.equal(records.records[0].side, 'note');
  await w.evaluate(() => {
    const n = document.querySelector('#preview p').firstChild,
      r = document.createRange();
    r.selectNodeContents(n);
    const s = getSelection();
    s.removeAllRanges();
    s.addRange(r);
  });
  await w.locator('#reviewSelection').click();
  await w.locator('#reviewInstruction').fill('这一段我没懂，请补充例子。');
  await w.locator('#reviewSave').click();
  await w.waitForFunction(
    () => document.querySelectorAll('.review-card').length === 2,
  );
  records = await ai('read_ai_reviews');
  assert.ok(records.records[1].quote.includes('复杂公式'));
  assert.equal(records.records[1].number, 2);
  let page = (await ai('read_pages', { pages: [1] })).pages[0];
  await ai('patch_note', {
    page: 1,
    search: '段',
    start: page.markdown.lastIndexOf('段'),
    replacement: '部分',
    expectedRevision: page.revision,
  });
  page = (await ai('read_pages', { pages: [1] })).pages[0];
  await ai('patch_note', {
    page: 1,
    search: '第一段：复杂公式需要解释。',
    replacement: '第一段：先用直观例子解释，再展示公式。',
    expectedRevision: page.revision,
  });
  await ai('update_ai_review', {
    recordId: records.records[1].id,
    status: 'applied',
  });
  assert.equal((await ai('read_ai_reviews')).records[1].status, 'applied');
  await w.reload();
  await dismissWelcome(w);
  await w.locator('#aiReviewButton').click();
  assert.equal(await w.locator('.review-card').count(), 2);
  assert.ok((await w.locator('#preview').innerText()).includes('直观例子'));
  await w.locator('#reviewNote').click();
  await w.locator('[data-ink-tool="highlight"]').click();
  const r = await w.locator('#noteBody').boundingBox();
  await w.mouse.move(r.x + 40, r.y + 60);
  await w.mouse.down();
  await w.mouse.move(r.x + 240, r.y + 60, { steps: 8 });
  await w.mouse.up();
  await w.locator('#reviewDialog').waitFor({ state: 'visible' });
  await w.locator('#reviewInstruction').fill('这处改得再通俗一点');
  await w.locator('#reviewSave').click();
  await w.waitForFunction(
    () => document.querySelectorAll('.review-card').length === 3,
  );
  assert.equal((await ai('read_ai_reviews')).records[2].kind, 'region');
  await w.screenshot({ path: path.join(root, 'tests/reviews-preview.png') });
  console.log(
    'PASS: embedded native Word; text editing and PDF refresh; table preserved; contextual tools; overall/selection/region numbered reviews; MCP reads/applies/status; persistence.',
  );
} finally {
  await app.close();
}

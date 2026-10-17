import { _electron as electron, expect } from '@playwright/test';
import { dismissWelcome } from './dismiss-welcome.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const profile = path.join(root, 'tmp', 'theme-data-' + Date.now());
await fs.mkdir(profile, { recursive: true });
const fixture = path.join(profile, 'theme.md');
const markdown =
  '# Dark mode\n\nReadable notes with [a link](https://example.com).\n\n> A quoted explanation.\n\n| Topic | Value |\n| --- | --- |\n| Theme | Dark |\n\n```js\nconst greeting = "hello";\nconsole.log(greeting);\n```\n\n$$x^2 + y^2 = 1$$\n\n```mermaid\nflowchart LR\n  A[Read] --> B[Note]\n```';
await fs.writeFile(fixture, markdown);
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
  // Playwright defaults to emulating light, which masks Electron's native theme.
  await w.emulateMedia({ colorScheme: null });
  w.on('pageerror', (error) => errors.push(error.message));
  await dismissWelcome(w);
  await w.bringToFront();
}
async function appearance(value) {
  await w.evaluate(() => document.getElementById('settings').showModal());
  await w.locator('#themeSetting').selectOption(value);
  await w.waitForFunction(
    (choice) => window.pageNotes.getTheme() === choice,
    value,
  );
  await w.locator('#closeSettings').click();
}
async function darkSurface(selector) {
  const colors = await w
    .locator(selector)
    .first()
    .evaluate((element) => ({
      bg: getComputedStyle(element).backgroundColor,
      fg: getComputedStyle(element).color,
    }));
  const rgb = (color) =>
    color
      .match(/[\d.]+/g)
      .slice(0, 3)
      .map(Number);
  const lightness = (color) =>
    rgb(color).reduce((sum, channel) => sum + channel, 0) / 3;
  assert.ok(
    lightness(colors.bg) < 85,
    selector + ' uses a dark surface: ' + JSON.stringify(colors),
  );
  assert.ok(
    lightness(colors.fg) > 155,
    selector + ' has readable foreground: ' + JSON.stringify(colors),
  );
  const luminance = (color) =>
    rgb(color)
      .map((value) => {
        const v = value / 255;
        return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
      })
      .reduce(
        (sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index],
        0,
      );
  assert.ok(
    (luminance(colors.fg) + 0.05) / (luminance(colors.bg) + 0.05) >= 4.5,
    selector + ' meets normal-text contrast',
  );
}
try {
  await ready();
  assert.equal(
    await w.evaluate(() => window.pageNotes.getTheme?.()),
    'system',
    'new profiles follow the system',
  );
  await w.evaluate(() => window.pageNotes.setLanguage('zh-CN'));
  await appearance('dark');
  await expect(w.locator('html')).toHaveAttribute('data-theme', 'dark');
  assert.equal(
    await app.evaluate(({ nativeTheme }) => nativeTheme.themeSource),
    'dark',
  );
  await darkSurface('#documentScroll');
  await darkSurface('#noteBody');
  await darkSurface('#aiSharedTools');

  await w.locator('#fileInput').setInputFiles(fixture);
  await w.locator('.markdown-page').first().waitFor();
  await w.evaluate(async (text) => {
    const state = await window.pageNotes.getState();
    const response = await fetch(
      '/api/documents/' + state.document.id + '/notes/1',
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ markdown: text }),
      },
    );
    if (!response.ok) throw Error('Could not create theme fixture');
    await window.pageNotes.syncNotes(state.document.id);
    await window.pageNotes.waitVisuals();
  }, markdown);
  await darkSurface('.markdown-page');
  await darkSurface('#preview');
  await darkSurface('#preview pre');
  await expect(w.locator('#preview .katex')).not.toHaveCount(0);
  await expect(w.locator('#preview .mermaid-diagram svg')).toHaveCount(1);
  const original = await w.evaluate(() => window.pageNotes.getState());
  await w.screenshot({ path: path.join(profile, 'dark-reading.png') });
  await w.locator('#aiSharedTools [data-ai-pointer="pen"]').click();
  await w.locator('[data-ai-pen="black"]').click();
  const ink = w.locator('#noteBody > .ink-layer');
  const bounds = await ink.boundingBox();
  await w.mouse.move(bounds.x + 40, bounds.y + 40);
  await w.mouse.down();
  await w.mouse.move(bounds.x + 160, bounds.y + 65, { steps: 4 });
  await w.mouse.up();
  const stroke = ink.locator('path[data-item]').first();
  await expect(stroke).toHaveAttribute('stroke', '#222222');
  await expect(stroke).toHaveCSS('stroke', 'rgb(231, 234, 240)');
  await w.locator('#aiSharedTools [data-ai-pointer="mouse"]').click();

  await w.evaluate(() => window.pageNotes.menuAction('chooseEditor'));
  await w.locator('.note-pane .pane-heading').click();
  await expect(w.locator('#preview')).toHaveAttribute(
    'contenteditable',
    'true',
  );
  await darkSurface('#preview');
  await darkSurface('.rich-code-block');
  await w.locator('#noteEditButton').click();
  await w.evaluate(() => document.getElementById('settings').showModal());
  await darkSurface('#settings');
  await darkSurface('#themeSetting');
  await w.screenshot({ path: path.join(profile, 'dark-settings.png') });
  await w.locator('#closeSettings').click();
  await w.evaluate(() => window.pageNotes.menuAction('reviewLeft'));
  await darkSurface('.ai-workbench[data-side="document"]');
  await w.screenshot({ path: path.join(profile, 'dark-tools.png') });
  await w.locator('.ai-workbench[data-side="document"] .board-close').click();

  await appearance('light');
  await expect(w.locator('html')).toHaveAttribute('data-theme', 'light');
  await expect(stroke).toHaveAttribute('stroke', '#222222');
  await expect(stroke).toHaveCSS('stroke', 'rgb(34, 34, 34)');
  assert.equal(
    await app.evaluate(({ nativeTheme }) => nativeTheme.themeSource),
    'light',
  );
  await appearance('system');
  await app.evaluate(({ nativeTheme }) => {
    nativeTheme.themeSource = 'dark';
  });
  await expect(w.locator('html')).toHaveAttribute('data-theme', 'dark');
  await app.evaluate(({ nativeTheme }) => {
    nativeTheme.themeSource = 'light';
  });
  await expect(w.locator('html')).toHaveAttribute('data-theme', 'light');
  assert.equal(await w.evaluate(() => window.pageNotes.getTheme()), 'system');
  assert.equal(
    (await w.evaluate(() => window.pageNotes.getState())).markdown,
    original.markdown,
  );

  // Hold an earlier response after the real server has saved it; a later
  // appearance selection must remain the visible and persisted choice.
  let releaseEarlier;
  let earlierSaved;
  const held = new Promise((resolve) => (releaseEarlier = resolve));
  const savedEarlier = new Promise((resolve) => (earlierSaved = resolve));
  let intercept = true;
  await w.route('**/api/preferences', async (route) => {
    if (
      intercept &&
      route.request().method() === 'PUT' &&
      route.request().postDataJSON().theme === 'dark'
    ) {
      intercept = false;
      const response = await route.fetch();
      earlierSaved();
      await held;
      await route.fulfill({ response });
    } else await route.continue();
  });
  const earlier = w.evaluate(() => window.pageNotes.setTheme('dark'));
  await savedEarlier;
  await w.evaluate(() => window.pageNotes.setTheme('light'));
  releaseEarlier();
  await earlier;
  await w.unroute('**/api/preferences');
  assert.equal(await w.evaluate(() => window.pageNotes.getTheme()), 'light');
  await expect(w.locator('html')).toHaveAttribute('data-theme', 'light');

  await appearance('dark');
  await w.evaluate(() => window.pageNotes.setLanguage('en-US'));
  await w.reload();
  await dismissWelcome(w);
  await expect(w.locator('html')).toHaveAttribute('data-theme', 'dark');
  await app.close();
  app = await launch();
  await ready();
  assert.equal(await w.evaluate(() => window.pageNotes.getTheme()), 'dark');
  assert.equal(await w.evaluate(() => window.pageNotes.getLanguage()), 'en-US');
  assert.equal(
    await app.evaluate(({ nativeTheme }) => nativeTheme.themeSource),
    'dark',
  );

  const saved = await w.evaluate(async () => {
    const update = (body) =>
      fetch('/api/preferences', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
    const responses = await Promise.all([
      update({ theme: 'dark' }),
      update({ language: 'zh-CN' }),
    ]);
    if (responses.some((r) => !r.ok))
      throw Error('Concurrent settings write failed');
    const invalid = await update({ theme: 'invalid' });
    return {
      preferences: await (await fetch('/api/preferences')).json(),
      rejected: invalid.status,
    };
  });
  assert.equal(saved.preferences.theme, 'dark');
  assert.equal(saved.preferences.language, 'zh-CN');
  assert.equal(saved.rejected, 400);

  // Source pixels and hidden exports remain faithful to the original document.
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.locator('.document-sheet canvas').first().waitFor();
  await w.locator('.document-sheet[data-painted="1"] canvas').first().waitFor();
  const pixel = await w
    .locator('.document-sheet canvas')
    .first()
    .evaluate((canvas) => [
      ...canvas.getContext('2d').getImageData(3, 3, 1, 1).data,
    ]);
  assert.ok(
    pixel.slice(0, 3).every((v) => v > 240),
    'PDF paper stays white in dark mode',
  );
  assert.equal(
    await w
      .locator('.document-sheet canvas')
      .first()
      .evaluate((canvas) => getComputedStyle(canvas).filter),
    'none',
  );
  await w
    .locator('.document-sheet > .ink-layer')
    .first()
    .waitFor({ state: 'attached' });
  await w.evaluate(() =>
    window.pageNotes.pasteContent('document', {
      html: '<h2>PDF annotation</h2><p>Readable text on white paper with <a href="https://example.com">a link</a>.</p>',
    }),
  );
  const rich = w.locator('.document-sheet .ink-rich-content').first();
  await expect(rich).toHaveCSS('color', 'rgb(37, 50, 71)');
  await expect(rich.locator('h2')).toHaveCSS('color', 'rgb(37, 50, 71)');
  await expect(rich.locator('a')).toHaveCSS('color', 'rgb(24, 90, 189)');
  await w.screenshot({ path: path.join(profile, 'dark-pdf-annotations.png') });
  const connection = JSON.parse(
    await fs.readFile(path.join(profile, 'ai-connection.json'), 'utf8'),
  );
  const shot = await fetch(connection.url + '/api/ai/call', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer ' + connection.token,
    },
    body: JSON.stringify({
      action: 'screenshot',
      args: { page: 1, scope: 'workspace' },
    }),
  });
  assert.equal(
    shot.status,
    200,
    'a real hidden screenshot works while the reader is dark',
  );
  const inspector = await app.evaluate(async ({ BrowserWindow }) => {
    const hidden = BrowserWindow.getAllWindows().find((window) =>
      window.webContents.getURL().includes('capture=1'),
    );
    return hidden.webContents.executeJavaScript(
      '({ theme: document.documentElement.dataset.theme, paper: getComputedStyle(document.getElementById("noteBody")).backgroundColor, welcome: document.getElementById("helloOverlay").open })',
    );
  });
  assert.deepEqual(inspector, {
    theme: 'light',
    paper: 'rgb(255, 255, 255)',
    welcome: false,
  });
  await expect(w.locator('html')).toHaveAttribute('data-theme', 'dark');
  const capture = new URL(w.url());
  capture.searchParams.set('capture', '1');
  await w.goto(capture.href);
  await w.waitForFunction(() => !!window.pageNotes);
  await w.evaluate(() => window.pageNotes.ready);
  await expect(w.locator('html')).toHaveAttribute('data-theme', 'light');
  assert.deepEqual(errors, []);
  console.log(
    'PASS system/light/dark appearance, live system changes, native theme, Markdown/code/math/diagrams, dialogs/tools, persistence with language, unchanged PDF and light capture renderers.',
  );
  console.log('Previews: ' + profile);
} finally {
  await app.close();
}

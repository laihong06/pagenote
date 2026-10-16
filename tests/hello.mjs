import { _electron as electron, expect } from '@playwright/test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { createServer } from 'node:net';

const root = path.resolve(import.meta.dirname, '..');
const profile = path.join(root, 'tmp', 'hello-data-' + Date.now());
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
await fs.mkdir(profile, { recursive: true });
let app = await launch();
let portBlocker;
const pageErrors = [];
try {
  let w = await app.firstWindow();
  w.on('pageerror', (error) => pageErrors.push(error.message));
  const ready = async () => {
    await w.waitForFunction(() => !!window.pageNotes);
    await w.evaluate(() => window.pageNotes.ready);
    await w.bringToFront();
  };
  await ready();
  const overlay = () => w.locator('#helloOverlay');
  // Missing auto-open or a webdriver bypass must fail this first-run check.
  await expect(overlay()).toBeVisible();
  await expect(w.locator('#helloEnter')).toBeFocused();
  await expect(w.locator('#helloEnter')).toHaveCSS('opacity', '1');
  // Freeze at pen-down: upcoming letters must not leak round-cap dots.
  await w.locator('#helloScript').evaluate((svg) => {
    for (const animation of svg.getAnimations({ subtree: true })) {
      animation.pause();
      animation.currentTime = 0;
    }
  });
  await w.screenshot({ path: path.join(profile, 'hello-before-writing.png') });
  assert.ok(
    await w
      .locator('#helloScript path')
      .evaluateAll((paths) =>
        paths.every((p) => getComputedStyle(p).opacity === '0'),
      ),
    'upcoming strokes must not show stray dots',
  );
  await w.locator('#helloScript').evaluate((svg) => {
    for (const animation of svg.getAnimations({ subtree: true }))
      animation.currentTime = 500;
  });
  assert.ok(
    await w
      .locator('#helloScript path')
      .evaluateAll(
        (paths) =>
          getComputedStyle(paths[0]).opacity === '1' &&
          paths.slice(1).every((p) => getComputedStyle(p).opacity === '0'),
      ),
    'only the currently drawn stroke is visible',
  );
  await w.screenshot({ path: path.join(profile, 'hello-writing.png') });
  await w.locator('#helloScript').evaluate((svg) => {
    for (const animation of svg.getAnimations({ subtree: true }))
      animation.play();
  });
  await w.keyboard.press('Tab');
  assert.ok(
    await w.evaluate(
      () => document.activeElement.closest('#helloOverlay') !== null,
    ),
    'focus stays in the welcome dialog',
  );
  await w.locator('#helloEnter').click();
  await expect(overlay()).toBeHidden();
  await w.reload();
  await ready();
  await expect(overlay()).toBeHidden();
  const originalPort = Number(new URL(w.url()).port);
  await app.close();
  portBlocker = createServer();
  await new Promise((resolve, reject) => {
    portBlocker.once('error', reject);
    portBlocker.listen(originalPort, '127.0.0.1', resolve);
  });
  app = await launch();
  w = await app.firstWindow();
  w.on('pageerror', (error) => pageErrors.push(error.message));
  await ready();
  assert.notEqual(
    Number(new URL(w.url()).port),
    originalPort,
    'restart uses another origin',
  );
  await expect(overlay()).toBeHidden();
  await new Promise((resolve) => portBlocker.close(resolve));
  await app.close();
  app = await launch();
  w = await app.firstWindow();
  w.on('pageerror', (error) => pageErrors.push(error.message));
  await ready();
  if (originalPort === 4174) assert.equal(Number(new URL(w.url()).port), 4174);
  await expect(overlay()).toBeHidden();

  await w.evaluate(() => window.pageNotes.setLanguage('zh-CN'));
  await app.evaluate(({ Menu }) =>
    Menu.getApplicationMenu()
      .items.find((item) => item.label === '帮助')
      .submenu.items.find((item) => item.label === '使用说明')
      .click(),
  );
  await expect(w.locator('#help')).toBeVisible();
  await w.locator('#reopenHello').click();
  await expect(w.locator('#help')).toBeHidden();
  await expect(overlay()).toBeVisible();
  await w
    .waitForFunction(() =>
      [...document.querySelectorAll('#helloScript path')].every(
        (p) => getComputedStyle(p).strokeDashoffset === '0px',
      ),
    )
    .catch(async (error) => {
      console.error(
        await w.evaluate(() => ({
          visibility: document.visibilityState,
          strokes: [...document.querySelectorAll('#helloScript path')].map(
            (p) => ({
              offset: getComputedStyle(p).strokeDashoffset,
              opacity: getComputedStyle(p).opacity,
              animations: p
                .getAnimations()
                .map((a) => ({ time: a.currentTime, state: a.playState })),
            }),
          ),
        })),
      );
      await w.screenshot({ path: path.join(profile, 'hello-stalled.png') });
      throw error;
    });
  await w.screenshot({ path: path.join(profile, 'hello-desktop.png') });
  await w.keyboard.press('Escape');
  await expect(overlay()).toBeHidden();
  await expect(w.locator('#help')).toBeVisible();
  await expect(w.locator('#reopenHello')).toBeFocused();
  await w.locator('#closeHelp').click();

  // The direct native Help entry remains available after dismissal.
  await w.evaluate(() => window.pageNotes.setLanguage('en-US'));
  await app.evaluate(({ Menu }) =>
    Menu.getApplicationMenu()
      .items.find((item) => item.label === 'Help')
      .submenu.items.find((item) => item.label === 'Welcome')
      .click(),
  );
  await expect(overlay()).toBeVisible();
  await expect(w.locator('#helloEnter')).toHaveText('Get started');
  await w.emulateMedia({ reducedMotion: 'reduce' });
  const motion = await w.locator('#helloScript path').evaluateAll((paths) =>
    paths.map((p) => ({
      animation: getComputedStyle(p).animationName,
      offset: getComputedStyle(p).strokeDashoffset,
    })),
  );
  assert.ok(motion.every((p) => p.animation === 'none' && p.offset === '0px'));
  await w.setViewportSize({ width: 480, height: 420 });
  const button = await w.locator('#helloEnter').boundingBox();
  assert.ok(
    button.x >= 0 && button.y >= 0 && button.y + button.height <= 420,
    'the enter button fits a small window',
  );
  await w.screenshot({ path: path.join(profile, 'hello-small.png') });
  await w.locator('#helloEnter').click();
  await expect(overlay()).toBeHidden();

  // A fresh screenshot renderer must neither show nor mark the welcome seen.
  await fs.unlink(path.join(profile, 'documents-data', 'welcome-seen.json'));
  const captureURL = new URL(w.url());
  captureURL.searchParams.set('capture', '1');
  await w.goto(captureURL.href);
  await ready();
  await expect(overlay()).toBeHidden();
  assert.equal(
    await w.evaluate(
      async () => (await (await fetch('/api/welcome')).json()).seen,
    ),
    false,
  );
  assert.equal(await w.evaluate(() => window.pageNotes.showWelcome()), false);
  captureURL.searchParams.delete('capture');
  await w.goto(captureURL.href);
  await ready();
  await expect(overlay()).toBeVisible();
  await w.locator('#helloEnter').click();
  await expect(overlay()).toBeHidden();
  assert.deepEqual(
    pageErrors,
    [],
    'welcome flows introduce no renderer errors',
  );
  console.log(
    'PASS first-run welcome, no stray dots, persistence across reload/restart/fallback ports, Help replay, keyboard focus, reduced motion, small window and capture exclusion.',
  );
  console.log('Previews: ' + profile);
} finally {
  await app.close();
  if (portBlocker?.listening)
    await new Promise((resolve) => portBlocker.close(resolve));
}

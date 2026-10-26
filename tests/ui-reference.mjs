import { chromium } from '@playwright/test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const root = path.resolve(import.meta.dirname, '..');
const url = pathToFileURL(path.join(root, 'docs/ui-reference/index.html')).href;
const output = path.join(root, 'release');
await fs.mkdir(output, { recursive: true });
const browser = await chromium.launch();
const failures = [];
try {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const page = await context.newPage();
  page.on('pageerror', (error) => failures.push(error.message));
  page.on('request', (request) => {
    if (/^https?:/.test(request.url()))
      failures.push('Unexpected remote request: ' + request.url());
  });
  await page.goto(url);
  await page.locator('.book[data-book]').last().waitFor();
  assert.equal(await page.locator('.book[data-book]').count(), 6);
  await page.waitForFunction(() =>
    [...document.querySelectorAll('.cover img')].every(
      (im) => im.complete && im.naturalWidth > 0,
    ),
  );
  await page.locator('#book-search').fill('模式');
  assert.equal(await page.locator('.book[data-book]').count(), 1);
  await page.locator('#book-search').fill('');
  await page.screenshot({
    path: path.join(output, 'ui-reference-shelf.png'),
    fullPage: true,
  });
  await page.getByRole('button', { name: '切换网格或列表' }).click();
  assert.ok(
    await page
      .locator('#books')
      .evaluate((el) => el.classList.contains('list')),
  );
  await page.getByRole('button', { name: '切换网格或列表' }).click();
  await page
    .locator('[data-book="notebook"] [data-action="customize"]')
    .click();
  await page.locator('#cover-title').fill('改过名字的笔记');
  await page.getByRole('button', { name: 'rose配色' }).click();
  await page.locator('#cover-form button[type="submit"]').click();
  assert.ok(
    await page.getByRole('button', { name: '打开 改过名字的笔记' }).isVisible(),
  );
  await page.locator('[data-action="new"]').first().click();
  await page.locator('#project-name').fill('样板检查本');
  await page.locator('#new-form button[type="submit"]').click();
  assert.equal(await page.locator('.book[data-book]').count(), 7);
  await page.reload();
  assert.ok(
    await page.getByRole('button', { name: '打开 样板检查本' }).isVisible(),
  );
  await page.getByRole('button', { name: '打开 样板检查本' }).first().click();
  assert.match(
    await page.locator('#page-jump').getAttribute('aria-label'),
    /共 1 页/,
  );
  assert.ok(await page.locator('[data-action="next"]').isDisabled());
  await page.locator('#page-jump').click();
  assert.equal(await page.locator('.page-preview').count(), 1);
  assert.ok(await page.locator('.markdown-miniature').isVisible());
  await page.keyboard.press('Escape');
  await page.locator('#scene').selectOption('shelf');
  await page
    .locator('[data-book="lecture"] [data-action="open-book"]')
    .first()
    .click();
  await page.locator('#note-preview .katex').first().waitFor();
  await page.getByRole('tab', { name: '编辑', exact: true }).click();
  await page
    .locator('#note-editor')
    .fill('# 保存测试\n\n**样板笔记**\n\n$P(A)$');
  await page.locator('[data-action="save-note"]').click();
  await page.locator('[data-action="next"]').click();
  assert.equal(await page.locator('#page-jump strong').textContent(), '2');
  await page.locator('[data-action="prev"]').click();
  assert.match(await page.locator('#note-editor').inputValue(), /保存测试/);
  await page.getByRole('tab', { name: '预览', exact: true }).click();
  assert.ok(await page.locator('#note-preview strong').isVisible());
  await page.getByRole('button', { name: '圆珠笔', exact: true }).click();
  assert.equal(
    await page.locator('[data-tool="ballpoint"]').getAttribute('aria-pressed'),
    'true',
  );
  assert.ok(await page.locator('#pen-options').isVisible());
  await page
    .locator('.writing-rail')
    .getByRole('button', { name: '红色', exact: true })
    .click();
  assert.equal(
    await page
      .locator('.writing-rail [data-color="#e35259"]')
      .getAttribute('aria-pressed'),
    'true',
  );
  await page.locator('#pen-width').fill('3.5');
  assert.equal(await page.locator('#pen-width-value').textContent(), '3.5');
  assert.ok(
    await page.locator('#stroke-preview').evaluate((canvas) => {
      const pixels = canvas
        .getContext('2d')
        .getImageData(0, 0, canvas.width, canvas.height).data;
      return pixels.some((value, index) => index % 4 === 3 && value > 0);
    }),
    'pen preview contains a visible stroke',
  );
  await page.getByRole('button', { name: '形状', exact: true }).click();
  await page.getByRole('button', { name: '椭圆', exact: true }).click();
  assert.equal(
    await page.locator('[data-shape="ellipse"]').getAttribute('aria-pressed'),
    'true',
  );
  await page.keyboard.press('Escape');
  assert.ok(await page.locator('#pen-options').isHidden());
  await page.getByRole('button', { name: '钢笔', exact: true }).click();
  await page.screenshot({
    path: path.join(output, 'ui-reference-pen-options.png'),
    fullPage: true,
  });
  await page.locator('#page-jump').click();
  assert.ok(await page.locator('#pen-options').isHidden());
  assert.equal(await page.locator('.page-preview').count(), 3);
  await page.waitForFunction(() =>
    [...document.querySelectorAll('.page-miniature img')].every(
      (img) => img.complete && img.naturalWidth > 0,
    ),
  );
  assert.equal(
    await page
      .locator('.page-preview[aria-current="page"]')
      .getAttribute('data-page'),
    '0',
  );
  await page.screenshot({
    path: path.join(output, 'ui-reference-page-drawer.png'),
    fullPage: true,
  });
  await page.locator('#page-preview-search').fill('风险');
  assert.equal(await page.locator('.page-preview').count(), 1);
  await page.locator('[data-action="choose-page"]').click();
  assert.equal(await page.locator('#page-jump strong').textContent(), '3');
  assert.ok(await page.locator('#page-drawer').isHidden());
  await page.locator('#page-jump').click();
  await page.mouse.click(300, 220);
  assert.ok(
    await page.locator('#page-drawer').isHidden(),
    'outside click dismisses previews',
  );
  await page.locator('#page-jump').click();
  await page.keyboard.press('Escape');
  await page.waitForFunction(
    () =>
      document.getElementById('page-jump').getAttribute('aria-expanded') ===
      'false',
  );
  assert.equal(
    await page.locator('#page-jump').getAttribute('aria-expanded'),
    'false',
  );
  assert.ok(
    await page
      .locator('#page-jump')
      .evaluate((el) => document.activeElement === el),
  );
  await page.locator('[data-action="prev"]').click();
  await page.locator('[data-action="prev"]').click();
  assert.match(await page.locator('#note-editor').inputValue(), /保存测试/);
  const oldWidth = await page
    .locator('.document-pane')
    .evaluate((el) => el.getBoundingClientRect().width);
  const handle = await page.locator('#splitter').boundingBox();
  await page.mouse.move(handle.x + 4, handle.y + 100);
  await page.mouse.down();
  await page.mouse.move(handle.x + 100, handle.y + 100);
  await page.mouse.up();
  const newWidth = await page
    .locator('.document-pane')
    .evaluate((el) => el.getBoundingClientRect().width);
  assert.ok(newWidth > oldWidth + 50, 'dragging changes pane ratio');
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({
    path: path.join(output, 'ui-reference-reader.png'),
    fullPage: true,
  });
  await page.locator('#scene').selectOption('rain');
  assert.ok(await page.locator('#rain-new').isDisabled());
  await page.locator('#rain-consent').check();
  assert.ok(await page.locator('#rain-new').isEnabled());
  await page.locator('#rain-new').click();
  assert.ok(await page.locator('#new-consent').isVisible());
  await page.getByRole('button', { name: '关闭对话框' }).click();
  await page.locator('#scene').selectOption('settings');
  await page.getByRole('button', { name: '阅读', exact: true }).click();
  await page.locator('#setting-font').fill('20');
  assert.equal(await page.locator('#font-value').textContent(), '20');
  assert.ok(
    await page.locator('[data-action="save-settings"]').evaluate((button) => {
      const style = getComputedStyle(button);
      return style.color !== style.backgroundColor;
    }),
    'primary action text remains visible',
  );
  await page.locator('#reference-toggle').click();
  await page
    .locator('#reference-file')
    .setInputFiles(path.join(root, 'docs/ui-reference/assets/pdf-cover.png'));
  await page.waitForFunction(
    () => document.getElementById('reference-image').naturalWidth > 0,
  );
  await page.locator('#reference-mode').selectOption('overlay');
  assert.ok(await page.locator('#reference-overlay').isVisible());
  await page.getByRole('button', { name: '关闭参考截图' }).click();
  await page.locator('[data-platform-choice="desktop"]').click();
  await page.locator('#theme-toggle').click();
  await page.locator('#scene').selectOption('reader');
  assert.ok(await page.locator('.desktop-menu').isVisible());
  assert.equal(
    await page
      .locator('#note-preview')
      .evaluate((el) => getComputedStyle(el).fontSize),
    '20px',
  );
  await page.screenshot({
    path: path.join(output, 'ui-reference-desktop.png'),
    fullPage: true,
  });
  for (const viewport of [
    { width: 1280, height: 800 },
    { width: 390, height: 844 },
  ]) {
    const mobile = await browser.newPage({ viewport });
    mobile.on('pageerror', (error) => failures.push(error.message));
    await mobile.goto(url);
    assert.ok(
      await mobile.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth + 1,
      ),
      'shelf has no horizontal overflow',
    );
    await mobile
      .locator('[data-book="lecture"] [data-action="open-book"]')
      .first()
      .click();
    await mobile.locator('#page-jump').click();
    assert.ok(await mobile.locator('#page-drawer').isVisible());
    const drawerBounds = await mobile.locator('#page-drawer').boundingBox();
    assert.ok(
      drawerBounds.x >= 0 &&
        drawerBounds.x + drawerBounds.width <= viewport.width + 1,
    );
    await mobile.getByRole('button', { name: '关闭页面预览' }).click();
    assert.ok(
      await mobile.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth + 1,
      ),
      'reader has no horizontal overflow',
    );
    await mobile.locator('#scene').selectOption('settings');
    assert.ok(
      await mobile.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth + 1,
      ),
      'settings have no horizontal overflow',
    );
    if (viewport.width === 390)
      await mobile.screenshot({
        path: path.join(output, 'ui-reference-phone.png'),
        fullPage: true,
      });
    await mobile.close();
  }
  assert.deepEqual(failures, []);
  console.log(
    'PASS offline HTML reference: shelf, forms, notes/math, pane dragging, reference images, themes and responsive layouts',
  );
} finally {
  await browser.close();
}

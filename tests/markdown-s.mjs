import { dismissWelcome } from './dismiss-welcome.mjs';
import { build } from 'esbuild';
import { _electron as electron } from '@playwright/test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
const root = path.resolve(import.meta.dirname, '..');
const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-mds-'));
const bundle = await build({
  stdin: {
    contents:
      "export {renderMarkdown} from './src/markdown.js'; export {htmlToMarkdown} from './src/editing.js';",
    resolveDir: root,
  },
  bundle: true,
  format: 'iife',
  globalName: 'PNTest',
  write: false,
  loader: { '.css': 'empty' },
  logLevel: 'silent',
});
const app = await electron.launch({
  executablePath: path.join(
    root,
    process.platform === 'darwin'
      ? 'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron'
      : 'node_modules/electron/dist/electron.exe',
  ),
  args: [root],
  env: {
    ...process.env,
    PAGE_NOTES_USER_DATA: path.join(tmp, 'profile'),
    PAGE_NOTES_DATA: path.join(tmp, 'data'),
  },
});
try {
  const page = await app.firstWindow();
  await page.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(page);
  await page.addScriptTag({ content: bundle.outputFiles[0].text });
  const source =
    '<button id="probe">Run</button><script>document.querySelector("#probe").onclick=()=>document.body.dataset.clicked="yes";try{parent.document.body.dataset.escaped="yes"}catch(e){document.body.dataset.blocked="yes"}</script>';
  const markdown = '# Before\n\n```mds-html\n' + source + '\n```\n\nAfter';
  await page.evaluate((md) => {
    const h = document.createElement('div');
    h.id = 'mds-test';
    h.style.cssText =
      'position:fixed;inset:20px;z-index:99999;overflow:auto;background:white';
    document.body.append(h);
    PNTest.renderMarkdown(md, h);
  }, markdown);
  assert.equal(
    await page.locator('#mds-test iframe').count(),
    0,
    'no automatic execution',
  );
  assert.ok(
    (
      await page.evaluate(() =>
        PNTest.htmlToMarkdown(document.querySelector('#mds-test').innerHTML),
      )
    ).includes(source),
    'source survives serialization',
  );
  await page
    .locator('#mds-test button')
    .filter({ hasText: '运行' })
    .click({ force: true });
  console.log(await page.locator('#mds-test').innerText());
  console.log(page.frames().map((f) => ({ url: f.url(), name: f.name() })));
  const preview = page.frameLocator('#mds-test iframe');
  await preview.locator('#probe').click();
  assert.equal(
    await preview.locator('body').getAttribute('data-clicked'),
    'yes',
  );
  assert.equal(
    await preview.locator('body').getAttribute('data-blocked'),
    'yes',
  );
  assert.equal(await page.locator('body').getAttribute('data-escaped'), null);
  await page
    .locator('#mds-test button')
    .filter({ hasText: '停止' })
    .click({ force: true });
  assert.equal(await page.locator('#mds-test iframe').count(), 0);
  console.log(
    'PASS Markdown S: explicit execution, source round trip, real sandbox interaction, parent isolation and stop',
  );
} finally {
  await app.close();
  await fs.rm(tmp, { recursive: true, force: true });
}

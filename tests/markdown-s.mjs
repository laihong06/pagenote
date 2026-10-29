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
      "export {renderMarkdown} from './src/markdown.js'; export {htmlToMarkdown} from './src/editing.js'; export {createRichEditor} from './src/rich-editor.js';",
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
  await page.evaluate(() =>
    PNTest.renderMarkdown(
      '\x60\x60\x60mermaid\nflowchart LR\n A-->B\n\x60\x60\x60',
      document.querySelector('#mds-test'),
    ),
  );
  await page.locator('#mds-test .diagram-ready svg').waitFor();
  await page.evaluate(() =>
    PNTest.renderMarkdown(
      '\x60\x60\x60mermaid\nnot a valid diagram !\n\x60\x60\x60',
      document.querySelector('#mds-test'),
    ),
  );
  await page.locator('#mds-test .diagram-error').waitFor();
  await page.evaluate(() =>
    PNTest.renderMarkdown(
      '\x60\x60\x60mermaid\nflowchart LR\n C-->D\n\x60\x60\x60',
      document.querySelector('#mds-test'),
    ),
  );
  await page.locator('#mds-test .diagram-ready svg').waitFor();
  console.log(
    'PASS real Mermaid queue: render, invalid input and subsequent recovery',
  );
  const engines = [
    [
      'mds-svg',
      '<svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="30" fill="orange"/></svg>',
      'svg circle',
    ],
    [
      'mds-canvas',
      '<canvas width="100" height="100"></canvas><script>const c=document.querySelector("canvas");const x=c.getContext("2d");x.fillStyle="rgb(255,0,0)";x.fillRect(0,0,100,100);document.body.dataset.pixel=x.getImageData(1,1,1,1).data[0];</script>',
      'canvas',
    ],
    [
      'mds-chart',
      JSON.stringify({
        type: 'bar',
        title: '路线长度',
        labels: ['A', 'B'],
        values: [120, 82],
      }),
      'svg rect',
    ],
    [
      'mds-three',
      '<script>const scene=new THREE.Scene();scene.add(new THREE.Mesh(new THREE.BoxGeometry(),new THREE.MeshBasicMaterial({color:0xff8800})));const renderer=new THREE.WebGLRenderer({preserveDrawingBuffer:true});renderer.setSize(200,200);document.body.append(renderer.domElement);const camera=new THREE.PerspectiveCamera(60,1,.1,100);camera.position.z=3;renderer.render(scene,camera);const pixels=new Uint8Array(4);renderer.getContext().readPixels(100,100,1,1,renderer.getContext().RGBA,renderer.getContext().UNSIGNED_BYTE,pixels);document.body.dataset.pixel=pixels[0];document.body.dataset.three=scene.children.length;</script><p>Three scene</p>',
      'p',
    ],
  ];
  for (const [engine, source, selector] of engines) {
    await page.evaluate(
      ({ engine, source }) =>
        PNTest.renderMarkdown(
          '\x60\x60\x60' + engine + '\n' + source + '\n\x60\x60\x60',
          document.querySelector('#mds-test'),
        ),
      { engine, source },
    );
    await page.locator('#mds-test button').filter({ hasText: '运行' }).click();
    const frame = page.frameLocator('#mds-test iframe');
    await frame.locator(selector).first().waitFor();
    if (engine === 'mds-canvas')
      assert.equal(
        await frame.locator('body').getAttribute('data-pixel'),
        '255',
      );
    if (engine === 'mds-three') {
      assert.equal(await frame.locator('body').getAttribute('data-three'), '1');
      assert.equal(
        await frame.locator('body').getAttribute('data-pixel'),
        '255',
      );
    }
    assert.equal(await frame.locator('#mds-runtime-error').count(), 0);
    await page.locator('#mds-test button').filter({ hasText: '停止' }).click();
  }
  console.log(
    'PASS SVG, Canvas pixel drawing, chart and offline Three scene; WebGL scene rendering',
  );
  await page.evaluate(() => {
    const root = document.querySelector('#mds-test');
    window.mdsSaved = '';
    window.mdsEditor = PNTest.createRichEditor({
      root,
      changed: (value) => (window.mdsSaved = value),
      canEdit: () => true,
      toast: () => {},
    });
    mdsEditor.load('\x60\x60\x60mds-html\n<p>Before edit</p>\n\x60\x60\x60');
  });
  await page.locator('#mds-test figcaption strong').dblclick();
  await page
    .getByRole('textbox', { name: 'Markdown S 源码' })
    .fill('<p>After edit</p>');
  assert.ok(await page.evaluate(() => mdsSaved.includes('<p>After edit</p>')));
  await page.evaluate(() => mdsEditor.undo());
  assert.equal(
    await page
      .locator('#mds-test .markdown-s-block')
      .getAttribute('data-source'),
    '<p>Before edit</p>\n',
  );
  await page.evaluate(() => mdsEditor.redo());
  assert.equal(
    await page
      .locator('#mds-test .markdown-s-block')
      .getAttribute('data-source'),
    '<p>After edit</p>\n',
  );
  console.log('PASS Markdown S source editing, undo and redo');
  await page.evaluate(() => {
    const fence = '\x60\x60\x60';
    PNTest.renderMarkdown(
      Array.from(
        { length: 3 },
        () => fence + 'mds-html\n<p>Concurrent demo</p>\n' + fence,
      ).join('\n\n'),
      document.querySelector('#mds-test'),
    );
  });
  const cards = page.locator('#mds-test .markdown-s-block');
  for (let i = 0; i < 3; i++)
    await cards
      .nth(i)
      .getByRole('button', { name: '运行', exact: true })
      .click();
  assert.equal(await page.locator('#mds-test iframe').count(), 2);
  assert.ok((await cards.nth(2).innerText()).includes('最多同时运行两个'));
  await cards.nth(0).getByRole('button', { name: '停止', exact: true }).click();
  await cards.nth(2).getByRole('button', { name: '运行', exact: true }).click();
  assert.equal(await page.locator('#mds-test iframe').count(), 2);
  await page.evaluate(() =>
    PNTest.renderMarkdown(
      'Replacement note',
      document.querySelector('#mds-test'),
    ),
  );
  assert.equal(await page.locator('#mds-test iframe').count(), 0);
  let forbiddenRequests = 0;
  page.on('request', (r) => {
    if (r.url().includes('/mds-forbidden')) forbiddenRequests++;
  });
  await page.evaluate(() => {
    const source =
      '<p id="safe">Sandbox</p><script>fetch(' +
      JSON.stringify(location.origin + '/mds-forbidden') +
      ').catch(()=>document.body.dataset.network="blocked");setTimeout(()=>{window.name="renamed";location.href=' +
      JSON.stringify(location.origin + '/mds-forbidden') +
      '},100);</script>';
    PNTest.renderMarkdown(
      '\x60\x60\x60mds-html\n' + source + '\n\x60\x60\x60',
      document.querySelector('#mds-test'),
    );
  });
  await page.locator('#mds-test button').filter({ hasText: '运行' }).click();
  const isolated = page.frameLocator('#mds-test iframe');
  await isolated.locator('#safe').waitFor();
  await page.waitForFunction(() => document.querySelector('#mds-test iframe'));
  await new Promise((resolve) => setTimeout(resolve, 300));
  assert.equal(
    await isolated.locator('body').getAttribute('data-network'),
    'blocked',
  );
  assert.equal(
    await isolated.locator('#safe').count(),
    1,
    'self navigation remains blocked after frame rename',
  );
  assert.equal(
    forbiddenRequests,
    0,
    'component must not reach the host service',
  );
  console.log(
    'PASS two-preview limit, replacement cleanup, CSP network and renamed-frame navigation isolation',
  );
  await page.evaluate(() => {
    window.removedPreview = document.querySelector(
      '#mds-test .markdown-s-block',
    );
    removedPreview.remove();
  });
  await page.waitForFunction(
    () => !window.removedPreview.querySelector('iframe'),
  );
  assert.equal(
    await page.evaluate(
      () => removedPreview.querySelector('figcaption button').disabled,
    ),
    false,
  );
  console.log(
    'PASS removed component disposes live iframe and releases run state',
  );
  console.log(
    'PASS Markdown S: explicit execution, source round trip, real sandbox interaction, parent isolation and stop',
  );
} finally {
  await app.close();
  await fs.rm(tmp, { recursive: true, force: true });
}

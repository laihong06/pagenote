import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron } from '@playwright/test';
import path from 'node:path';
import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
const root = path.resolve(import.meta.dirname, '..'),
  profile = path.join(root, 'tests', `rich-data-${Date.now()}`),
  executable =
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
  env: { ...process.env, PAGE_NOTES_USER_DATA: profile },
  timeout: 40000,
});
try {
  const w = await app.firstWindow();
  const tool = async (name) => {
    const button = w.locator('[data-md="' + name + '"]');
    await button.evaluate((n) => {
      n.closest('details').open = true;
    });
    await button.click();
  };
  w.on('pageerror', (e) => console.error(e.message));
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.waitForFunction(() => !!window.pageNotes.getSummary().document);
  await w.locator('#loading').waitFor({ state: 'hidden' });
  await w.evaluate(async () => {
    const s = await window.pageNotes.getState();
    await fetch('/api/documents/' + s.document.id + '/notes/' + s.page, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ markdown: '' }),
    });
    await window.pageNotes.syncNotes(s.document.id);
  });
  assert.equal(
    await w.locator('[data-mode]').count(),
    0,
    'no reading/edit/split selector',
  );
  assert.ok(await w.locator('#editor').isHidden());
  assert.equal(
    await w.locator('#preview').getAttribute('contenteditable'),
    'false',
  );
  await w.evaluate(() => window.pageNotes.menuAction('chooseEditor'));
  await w.locator('.note-pane .pane-heading').click();
  await w.locator('#preview').click();
  await w.keyboard.insertText('直接编辑的笔记');
  await w.evaluate(() => window.pageNotes.flush());
  assert.ok(
    (await w.evaluate(() => window.pageNotes.getState())).markdown.includes(
      '直接编辑',
    ),
    await w.locator('#preview').evaluate((e) =>
      JSON.stringify({
        html: e.innerHTML,
        model: document.querySelector('#editor').value,
        active: document.activeElement.id,
      }),
    ),
  );
  await tool('code');
  const code = w.locator('#preview pre code').last();
  await w
    .locator('#preview .rich-code-tools select')
    .last()
    .selectOption('python');
  await code.pressSequentially('print("hello")');
  assert.ok(
    (await code.locator('.hljs-built_in').count()) > 0,
    'highlight updates while typing before blur',
  );
  await code.press('Enter');
  await code.pressSequentially('x = 1');
  await w
    .locator('#preview .rich-code-tools select')
    .last()
    .selectOption('python');
  await w.evaluate(() => window.pageNotes.flush());
  let state = await w.evaluate(() => window.pageNotes.getState());
  assert.ok(
    state.markdown.includes('```python\nprint("hello")\nx = 1'),
    state.markdown,
  );
  assert.ok(
    !(await w.locator('#preview').innerText()).includes('```'),
    'fences remain hidden',
  );
  assert.ok((await code.locator('.hljs-built_in').count()) > 0);
  await w.locator('#preview .rich-code-tools button').last().click();
  await w.keyboard.type('代码后的正文');
  await w.evaluate(() => window.pageNotes.flush());
  assert.ok(
    (await w.evaluate(() => window.pageNotes.getState())).markdown.includes(
      '代码后的正文',
    ),
  );
  await tool('math');
  await w.locator('#richBlockSource').fill('\\mu = 18');
  await w.locator('#richBlockApply').click();
  await w.locator('#preview .katex').waitFor();
  assert.equal(await w.locator('[data-md="mermaid"]').count(), 0);
  await w.evaluate(() => {
    const root = document.querySelector('#preview'),
      p = document.createElement('p');
    p.innerHTML = '<br>';
    root.append(p);
    root.focus();
    const r = document.createRange();
    r.selectNodeContents(p);
    r.collapse(true);
    getSelection().removeAllRanges();
    getSelection().addRange(r);
  });
  await w.keyboard.insertText('```mermaid');
  await w.keyboard.press('Enter');
  assert.ok(await w.locator('#richBlockDialog').isHidden());
  await w
    .locator('.rich-diagram-source textarea')
    .fill('flowchart LR\n A[自定义开始] --> B[自定义结束]');
  await w
    .locator('#preview .diagram-ready')
    .waitFor({ timeout: 10000 })
    .catch(async (e) => {
      console.log(
        await w.evaluate(() => ({
          figures: [...document.querySelectorAll('#preview figure')].map((n) =>
            n.outerHTML.slice(0, 1800),
          ),
          markdown: document.querySelector('#editor').value,
        })),
      );
      throw e;
    });
  await w.locator('#preview .diagram-ready').dblclick();
  await w
    .locator('.rich-diagram-source textarea')
    .fill('flowchart LR\n A[实时修改] --> B[自定义结束]');
  await w.waitForFunction(() =>
    document
      .querySelector('#preview .diagram-ready')
      ?.textContent.includes('实时修改'),
  );
  await tool('table');
  await w.locator('#preview td').first().fill('表格内容');
  await w.evaluate(() => window.pageNotes.flush());
  state = await w.evaluate(() => window.pageNotes.getState());
  assert.ok(state.markdown.includes('\\mu = 18'));
  assert.ok(state.markdown.includes('```mermaid'));
  assert.ok(state.markdown.includes('表格内容'));
  const documentId = state.document.id;
  await w.reload();
  await dismissWelcome(w);
  await w.evaluate((id) => window.pageNotes.open(id), documentId);
  await w
    .locator('#preview .diagram-ready')
    .waitFor({ timeout: 10000 })
    .catch(async (e) => {
      console.log(
        'RELOAD',
        await w.evaluate(() => ({
          state: window.pageNotes.getState(),
          html: document.querySelector('#preview').innerHTML,
        })),
      );
      throw e;
    });
  assert.ok(
    (await w.locator('#preview pre code').first().textContent()).includes(
      'print("hello")',
    ),
  );
  assert.ok((await w.locator('#preview .katex').count()) > 0);
  assert.ok((await w.locator('#preview').innerText()).includes('表格内容'));
  await w.evaluate(() => window.pageNotes.menuAction('chooseEditor'));
  await w.locator('.note-pane .pane-heading').click();
  assert.ok(await w.locator('#markdownTools summary').first().isVisible());
  await tool('code');
  const newBlock = w.locator('#preview pre[data-rich-id]');
  await newBlock.locator('code').pressSequentially('graph LR; X-->Y');
  await newBlock.locator('select').selectOption('mermaid');
  await w
    .waitForFunction(
      () => document.querySelectorAll('#preview .diagram-ready').length >= 2,
      {},
      { timeout: 8000 },
    )
    .catch(async (e) => {
      console.log('CONVERT', await w.locator('#preview').innerHTML());
      throw e;
    });
  await w.evaluate(() => {
    const root = document.querySelector('#preview'),
      p = document.createElement('p');
    root.append(p);
    root.focus();
    const r = document.createRange();
    r.selectNodeContents(p);
    getSelection().removeAllRanges();
    getSelection().addRange(r);
  });
  await w.keyboard.insertText('```mermaid');
  await w.keyboard.press('Enter');
  await w
    .locator('.rich-diagram-source textarea')
    .last()
    .fill('graph TD; Typed-->Works');
  await w.waitForFunction(
    () => document.querySelectorAll('#preview .diagram-ready').length >= 3,
  );
  await w.evaluate(() => {
    const root = document.querySelector('#preview');
    root.focus();
    const r = document.createRange();
    r.selectNodeContents(root);
    r.collapse(false);
    getSelection().removeAllRanges();
    getSelection().addRange(r);
    const data = new DataTransfer();
    data.setData('text/plain', '```mermaid\ngraph LR; Paste-->Works\n```');
    root.dispatchEvent(
      new ClipboardEvent('paste', {
        clipboardData: data,
        bubbles: true,
        cancelable: true,
      }),
    );
  });
  await w.waitForFunction(
    () => document.querySelectorAll('#preview .diagram-ready').length >= 4,
  );
  await w.locator('#noteEditButton').click();
  assert.ok(await w.locator('#markdownTools summary').first().isHidden());
  assert.equal(
    await w.locator('#preview').getAttribute('contenteditable'),
    'false',
    'paper becomes read-only when editing finishes',
  );
  console.log(
    'PASS single editable paper; direct rendered code insertion, multiline input, language/highlighting; formula/diagram/table preservation; persistence; explicit edit entry and read-only return without mode selector.',
  );
} finally {
  await app.close();
}

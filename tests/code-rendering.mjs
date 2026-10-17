import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron } from '@playwright/test';
import assert from 'node:assert/strict';
import path from 'node:path';
const root = path.resolve(import.meta.dirname, '..');
const executable =
  process.env.DESKTOP_EXE ||
  path.join(
    root,
    process.platform === 'darwin'
      ? 'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron'
      : 'node_modules/electron/dist/electron.exe',
  );

const examples = [
  [
    'Java',
    'public class Demo { public static void main(String[] args) { System.out.println(1); } }',
  ],
  ['Python3', 'def average(xs):\n    return sum(xs) / len(xs)'],
  ['C++', '#include <iostream>\nint main() { std::cout << 42; }'],
  ['C#', 'public class Demo { static int Value = 42; }'],
  ['go', 'package main\nfunc main() { println(42) }'],
  ['rust', 'fn main() { let answer = 42; println!("{}", answer); }'],
  ['MATLAB', 'function y = square(x)\n y = x.^2;\nend'],
  ['R', 'values <- c(1, 2, 3)\nmean(values)'],
  ['ps1', 'function Hello { Write-Host "Hello" }'],
  ['yaml', 'name: pagenote\nitems:\n  - example'],
  ['unknown-language', '<script>window.untrustedCode = true;</script>'],
];
const markdown =
  examples
    .map(([lang, text]) => '```' + lang + '\n' + text + '\n```')
    .join('\n\n') +
  '\n\n```MERMAID\nflowchart LR\n A[Input] --> B[Output]\n```\n\nAfter all code blocks.';
const app = await electron.launch({
  executablePath: executable,
  args: [root],
  env: {
    ...process.env,
    PAGE_NOTES_USER_DATA: path.join(
      root,
      'tests/code-rendering-data-' + Date.now(),
    ),
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
      !['127.0.0.1', 'localhost'].includes(u.hostname)
    ) {
      remote.push(u.hostname);
      return route.abort();
    }
    return route.continue();
  });
  await w
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.pdf'));
  await w.locator('#loading').waitFor({ state: 'hidden' });
  await w.evaluate(async (markdown) => {
    const s = await window.pageNotes.getState();
    await fetch('/api/documents/' + s.document.id + '/notes/1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ markdown }),
    });
    await window.pageNotes.syncNotes(s.document.id);
  }, markdown);
  await w.locator('#preview .mermaid-diagram svg').waitFor();
  const blocks = w.locator('#preview pre > code');
  assert.equal(await blocks.count(), examples.length);
  for (let i = 0; i < examples.length; i++) {
    assert.equal((await blocks.nth(i).textContent()).trim(), examples[i][1]);
    if (i < examples.length - 1)
      assert.ok(
        await blocks.nth(i).locator('[class*="hljs-"]').count(),
        'highlight ' + examples[i][0],
      );
  }
  assert.equal(await w.evaluate(() => !!window.untrustedCode), false);
  assert.ok(
    (await w.locator('#preview').innerText()).includes(
      'After all code blocks.',
    ),
  );
  const color = await blocks.first().evaluate((el) => ({
    plain: getComputedStyle(el).color,
    keyword: getComputedStyle(el.querySelector('.hljs-keyword')).color,
  }));
  assert.notEqual(color.plain, color.keyword);
  await w.evaluate(() => window.pageNotes.menuAction('chooseEditor'));
  await w.locator('.note-pane .pane-heading').click();
  const java = w.locator('#preview pre code').first();
  await java.click();
  await java.press('End');
  await java.pressSequentially(' // edited');
  assert.ok(await java.locator('[class*="hljs-"]').count());
  await w.evaluate(() => window.pageNotes.flush());
  assert.ok(
    (await w.evaluate(() => window.pageNotes.getState())).markdown.includes(
      '// edited',
    ),
  );
  assert.deepEqual(remote, []);
  console.log(
    'PASS offline Java/Python3/C++/C#/Go/Rust/MATLAB/R/PowerShell/YAML, token colors, uppercase Mermaid, unknown-code preservation, safe HTML and live editing',
  );
} finally {
  await app.close();
}

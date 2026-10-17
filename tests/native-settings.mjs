import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { createRequire } from 'node:module';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
const require = createRequire(import.meta.url);
const root = path.resolve(import.meta.dirname, '..');
if (process.platform !== 'darwin') {
  console.log('SKIP native macOS settings');
  process.exit(0);
}
const { createMacSettings } = require('../desktop/mac-settings.cjs');
const profile = await fs.mkdtemp(path.join(root, 'tmp', 'native-settings-'));
const state = {
  version: '1.0.1-dev.3',
  language: 'zh-CN',
  theme: 'system',
  markdownTheme: 'classic',
  noteAnimation: true,
  expandEditor: true,
};
const prompts = {
  system: { text: 'Original prompt', defaultText: 'Default prompt' },
};
const errors = [];
const controller = createMacSettings({
  executable: path.join(
    process.env.DESKTOP_EXE
      ? path.join(
          path.dirname(path.dirname(process.env.DESKTOP_EXE)),
          'Resources',
        )
      : path.join(root, 'build/mac-settings'),
    'pagenote Settings.app/Contents/MacOS/pagenote-settings',
  ),
  paneFile: path.join(profile, 'pane.json'),
  readState: async () => ({ ...state }),
  applySetting: async (key, value) => {
    state[key] = value;
  },
  readPrompt: async (module) => prompts[module],
  savePrompt: async (module, text) => {
    prompts[module].text = text;
  },
  onError: (error) => errors.push(error.message),
});
try {
  await assert.rejects(controller.open('untrusted-pane'), /pane/i);
  const first = await controller.open('appearance');
  assert.equal(first.native, true);
  assert.ok(first.windowNumber > 0, 'a real AppKit window has a window number');
  assert.ok(first.pid > 0);
  const second = await controller.open('reading');
  assert.equal(
    first.pid,
    second.pid,
    'repeated entry reuses the same native process',
  );
  assert.equal(second.pane, 'reading');
  await controller.sync();
  assert.equal(
    await controller.prepareQuit(),
    true,
    'clean native window allows quit',
  );
  assert.throws(
    () => process.kill(first.pid, 0),
    /ESRCH/,
    'the helper exits on close',
  );
  const remembered = JSON.parse(
    await fs.readFile(path.join(profile, 'pane.json'), 'utf8'),
  );
  assert.equal(remembered.pane, 'reading');
  const reopened = await controller.open();
  assert.equal(reopened.pane, 'reading', 'last native pane is restored');
  controller.dispose();
  const replacement = await controller.open('appearance');
  assert.notEqual(
    replacement.pid,
    reopened.pid,
    'opening during disposal starts a fresh helper',
  );
  controller.dispose();
  await new Promise((resolve) => setTimeout(resolve, 300));
  assert.throws(
    () => process.kill(reopened.pid, 0),
    /ESRCH/,
    'parent disposal leaves no orphan helper',
  );
  assert.deepEqual(errors, []);
  const missingErrors = [];
  const missing = createMacSettings({
    executable: path.join(profile, 'does-not-exist'),
    paneFile: path.join(profile, 'missing.json'),
    readState: async () => state,
    onError: (error) => missingErrors.push(error.message),
  });
  await assert.rejects(missing.open(), /ENOENT/);
  await new Promise((resolve) => setTimeout(resolve, 100));
  assert.deepEqual(
    missingErrors,
    [],
    'missing helper is reported to its caller without a second broken-pipe error dialog',
  );
  missing.dispose();
  const brokenPipe = path.join(profile, 'broken-pipe');
  // A native child closes only its input while remaining alive: a real pipe
  // failure, without Node's special handling for its own fd 0.
  await fs.writeFile(
    brokenPipe + '.c',
    `#include <unistd.h>
int main(void) {
 char input[8192]; if (read(0,input,sizeof input)<=0) return 1; close(0);
 const char output[]="{\\\"event\\\":\\\"shown\\\",\\\"requestId\\\":\\\"electron-1\\\",\\\"windowNumber\\\":1,\\\"pane\\\":\\\"general\\\"}\\n";
 write(1,output,sizeof output-1); sleep(30); return 0;
}`,
  );
  await promisify(execFile)('xcrun', [
    'clang',
    brokenPipe + '.c',
    '-o',
    brokenPipe,
  ]);
  const pipeErrors = [];
  const broken = createMacSettings({
    executable: brokenPipe,
    paneFile: path.join(profile, 'broken.json'),
    readState: async () => state,
    onError: (error) => pipeErrors.push(error.message),
  });
  try {
    const opened = await broken.open();
    await broken.sync();
    for (let i = 0; i < 100 && !pipeErrors.length; i++)
      await new Promise((r) => setTimeout(r, 10));
    assert.match(
      pipeErrors.join(' '),
      /EPIPE|broken pipe|write|connection closed/i,
      'broken native pipe is handled as an error instead of crashing the parent',
    );
    await new Promise((r) => setTimeout(r, 100));
    assert.throws(
      () => process.kill(opened.pid, 0),
      /ESRCH/,
      'failed pipe does not orphan a helper',
    );
  } finally {
    broken.dispose();
  }
  console.log(
    'PASS real native AppKit window, single process reuse, pane memory, graceful close and parent cleanup',
  );
} finally {
  controller.dispose();
}

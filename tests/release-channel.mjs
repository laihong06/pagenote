import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { _electron as electron } from '@playwright/test';
const root = path.resolve(import.meta.dirname, '..');
const temp = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-channel-'));
const bootstrap = path.join(temp, 'bootstrap.cjs');
const pkg = JSON.parse(
  await fs.readFile(path.join(root, 'package.json'), 'utf8'),
);
await fs.writeFile(
  path.join(temp, 'package.json'),
  JSON.stringify({
    name: 'pagenote',
    version: pkg.version,
    main: 'bootstrap.cjs',
  }),
);
await fs.writeFile(
  bootstrap,
  `const {app}=require('electron');app.setPath('appData',${JSON.stringify(temp)});app.setAppPath(${JSON.stringify(root)});require(${JSON.stringify(path.join(root, 'desktop/main.cjs'))});`,
);
const app = await electron.launch({
  executablePath: path.join(root, 'node_modules/electron/dist/electron.exe'),
  args: [temp],
  env: { ...process.env, PAGE_NOTES_USER_DATA: '' },
});
try {
  const w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await w.evaluate(() => window.pageNotes.ready);
  const state = await app.evaluate(({ app }) => ({
    name: app.getName(),
    data: app.getPath('userData'),
    version: app.getVersion(),
  }));
  assert.equal(state.name, 'pagenote Beta');
  assert.equal(state.data, path.join(temp, 'pagenote-beta'));
  assert.match(state.version, /-beta\./);
  console.log(
    'PASS beta default data directory and application identity isolated from stable',
  );
} finally {
  await app.close();
}

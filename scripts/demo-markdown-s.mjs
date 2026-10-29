import fs from 'node:fs/promises';
import path from 'node:path';
import { randomBytes } from 'node:crypto';
import { spawn } from 'node:child_process';

const root = path.resolve(import.meta.dirname, '..');
if (!['win32', 'darwin'].includes(process.platform))
  throw Error('This desktop demo currently supports Windows and macOS.');
await fs.mkdir(path.join(root, 'tmp'), { recursive: true });
const demo = await fs.mkdtemp(path.join(root, 'tmp', 'markdown-s-demo-'));
const project = path.join(demo, 'project');
await fs.mkdir(path.join(project, 'src'), { recursive: true });
const file = path.join(project, 'project.pagenote');
await fs.writeFile(
  file,
  JSON.stringify(
    {
      format: 'pagenote-project',
      version: 1,
      id: randomBytes(32).toString('hex'),
      name: 'Markdown S 离线演示',
      left: 'src/source.md',
      right: 'src/notes.md',
    },
    null,
    2,
  ),
);
await fs.writeFile(
  path.join(project, 'src/source.md'),
  '# Markdown S 离线演示\n\n右侧是可以操作的学习笔记。\n\n1. 点击运行，尝试滑块、图表与三维场景。\n2. 点击停止，释放预览。\n3. 编辑模式双击标题修改源码。\n\n这是独立演示项目，关闭后可以再次打开 project.pagenote。\n',
);
await fs.copyFile(
  path.join(root, 'examples/markdown-s.md'),
  path.join(project, 'src/notes.md'),
);
const executablePath = path.join(
  root,
  process.platform === 'darwin'
    ? 'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron'
    : 'node_modules/electron/dist/electron.exe',
);
const env = {
  ...process.env,
  PAGE_NOTES_USER_DATA: path.join(demo, 'profile'),
  PAGE_NOTES_DATA: path.join(demo, 'data'),
};
if (process.argv.includes('--verify')) {
  const { _electron } = await import('@playwright/test');
  const { dismissWelcome } = await import('../tests/dismiss-welcome.mjs');
  const app = await _electron.launch({
    executablePath,
    args: [root, file],
    env,
  });
  try {
    const page = await app.firstWindow();
    await page.waitForFunction(() => !!window.pageNotes);
    await dismissWelcome(page);
    const card = page.locator('#preview .markdown-s-block').first();
    await card.waitFor();
    await card.getByRole('button', { name: '运行', exact: true }).click();
    await card.frameLocator('iframe').locator('input').waitFor();
    await card.getByRole('button', { name: '停止', exact: true }).click();
    console.log(
      'PASS demo command opens the real project and runs/stops its interactive note',
    );
  } finally {
    await app.close();
  }
} else {
  const child = spawn(executablePath, [root, file], {
    cwd: root,
    env,
    detached: true,
    stdio: 'ignore',
    windowsHide: true,
  });
  child.on('error', (error) => {
    console.error(error);
    process.exitCode = 1;
  });
  child.unref();
}
console.log('Demo project: ' + file);

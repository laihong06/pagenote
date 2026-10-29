import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { execFileSync } from 'node:child_process';
import { _electron as electron } from '@playwright/test';
import { dismissWelcome } from '../tests/dismiss-welcome.mjs';
import { loadPlatform } from './platform-config.mjs';

const root = path.resolve(import.meta.dirname, '..');
const id = process.platform === 'darwin' ? 'macos' : 'windows';
if (!['win32', 'darwin'].includes(process.platform))
  throw Error('Native desktop host required');
const out = path.resolve(
  process.argv[2] || path.join(root, 'docs', id, 'guide/images/current'),
);
await fs.mkdir(out, { recursive: true });
const temp = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-guide-'));
const project = path.join(temp, 'library', '学习示例');
await fs.mkdir(path.join(project, 'src'), { recursive: true });
await fs.writeFile(
  path.join(project, 'project.pagenote'),
  JSON.stringify({
    format: 'pagenote-project',
    version: 1,
    id: 'c'.repeat(64),
    name: '学习示例',
    left: 'src/source.md',
    right: 'src/notes.md',
  }),
);
await fs.writeFile(
  path.join(project, 'src/source.md'),
  '# 学习示例：蚁群算法\n\n虚拟蚂蚁逐步构造路线，依据距离和信息素选择下一座城市。\n\n## 三个核心问题\n\n1. 下一步怎样选？\n2. 优秀路线怎样影响后续搜索？\n3. 信息素为什么需要挥发？\n\n## 自测\n\n请合上笔记，解释一轮搜索结束后发生了什么。',
);
await fs.writeFile(
  path.join(project, 'src/notes.md'),
  '# 本页讲解（离线示例）\n\n这段内容是演示笔记，不是在线 AI 输出。\n\n## 选择下一座城市\n\n$$\np(i,j)=\\frac{\\tau_{ij}^{\\alpha}\\eta_{ij}^{\\beta}}{\\sum_{k\\in U}\\tau_{ik}^{\\alpha}\\eta_{ik}^{\\beta}}\n$$\n\n距离较短、信息素较多的边通常有更高概率。\n\n```mermaid\nflowchart LR\n  A[构造路线] --> B[评价长度]\n  B --> C[挥发与强化]\n  C --> A\n```\n\n```python\nweight = pheromone ** alpha / distance ** beta\n```\n\n| 要素 | 用途 |\n| --- | --- |\n| 信息素 | 共享搜索经验 |\n| 距离启发 | 偏向较近城市 |',
);
const executablePath =
  process.env.DESKTOP_EXE ||
  path.join(
    root,
    process.platform === 'darwin'
      ? 'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron'
      : 'node_modules/electron/dist/electron.exe',
  );
const app = await electron.launch({
  executablePath,
  args: process.env.DESKTOP_EXE ? [] : [root],
  env: {
    ...process.env,
    PAGE_NOTES_USER_DATA: path.join(temp, 'profile'),
    PAGE_NOTES_DATA: path.join(temp, 'data'),
  },
});
const captures = [];
try {
  const page = await app.firstWindow();
  page.setDefaultTimeout(20000);
  await dismissWelcome(page);
  await app.evaluate(({ BrowserWindow }) =>
    BrowserWindow.getAllWindows()[0].setSize(1440, 960),
  );
  await page.evaluate(() => window.pageNotes.setLanguage('zh-CN'));
  const snap = async (name) => {
    await page.locator('#toast').waitFor({ state: 'hidden' });
    await page.evaluate(async () => {
      await document.fonts.ready;
    });
    await page.screenshot({ path: path.join(out, name + '.png') });
    captures.push(name);
  };
  await app.evaluate(
    async ({ BrowserWindow, dialog }, library) => {
      const previous = dialog.showOpenDialog;
      dialog.showOpenDialog = async () => ({
        canceled: false,
        filePaths: [library],
      });
      try {
        await BrowserWindow.getAllWindows()[0].webContents.executeJavaScript(
          'window.pageNotes.chooseBookshelfDirectory()',
        );
      } finally {
        dialog.showOpenDialog = previous;
      }
    },
    path.join(temp, 'library'),
  );
  await page.locator('[data-book-key]').first().waitFor();
  await snap('bookshelf');
  await page.locator('.book-title', { hasText: '学习示例' }).click();
  await page.locator('#bookshelf').waitFor({ state: 'hidden' });
  await page.locator('.katex').first().waitFor();
  await page.locator('.diagram-ready').first().waitFor();
  await snap('reading');
  await page.evaluate(() => window.pageNotes.menuAction('chooseEditor'));
  await page.locator('.note-pane').click({ position: { x: 100, y: 100 } });
  await snap('editing');
  await page.locator('#noteEditButton').click();
  await page.locator('#aiSharedTools [data-ai-pointer=pen]').click();
  await snap('ink-tools');
  await page.locator('#aiSharedTools [data-ai-pointer=mouse]').click();
  await page.keyboard.press('Escape');
  await page.evaluate(() => window.pageNotes.menuAction('reviewRight'));
  await snap('ai-workbench');
  await fs.writeFile(
    path.join(out, 'provenance.json'),
    JSON.stringify(
      {
        platform: id,
        os: os.release(),
        architecture: process.arch,
        viewport: await page.evaluate(() => ({
          width: innerWidth,
          height: innerHeight,
          density: devicePixelRatio,
        })),
        capturedAt: new Date().toISOString(),
        sourceCommit: execFileSync('git', ['rev-parse', 'HEAD'], {
          cwd: root,
          encoding: 'utf8',
        }).trim(),
        workingTree: execFileSync('git', ['status', '--porcelain'], {
          cwd: root,
          encoding: 'utf8',
        }).trim()
          ? 'modified integration worktree'
          : 'clean',
        version: await app.evaluate(({ app }) => app.getVersion()),
        targetPackageVersion: (await loadPlatform(root, id)).developmentVersion,
        sample: 'synthetic, no account or network AI request',
        captures,
      },
      null,
      2,
    ) + '\n',
  );
  console.log('Captured native ' + id + ': ' + captures.join(', '));
} finally {
  await app.close();
  await fs.rm(temp, { recursive: true, force: true });
}

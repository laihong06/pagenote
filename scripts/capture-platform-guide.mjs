import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';
import { _electron as electron } from '@playwright/test';
import { dismissWelcome } from '../tests/dismiss-welcome.mjs';
import { loadPlatform } from './platform-config.mjs';

const root = path.resolve(import.meta.dirname, '..');
const id =
  process.platform === 'darwin'
    ? 'macos'
    : process.platform === 'linux'
      ? 'linux'
      : 'windows';
if (!['win32', 'darwin', 'linux'].includes(process.platform))
  throw Error('Native desktop host required');
const out = path.resolve(
  process.argv[2] || path.join(root, 'docs', id, 'guide/images/current'),
);
await fs.mkdir(out, { recursive: true });
const temp = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-guide-'));
const project = path.join(temp, 'library', '学习示例');
const nativeFile = path.join(
  project,
  'src',
  process.env.PAGENOTE_NATIVE_SOURCE ? 'source.md' : 'notes.md',
);
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
      : process.platform === 'linux'
        ? 'node_modules/electron/dist/electron'
        : 'node_modules/electron/dist/electron.exe',
  );
if (process.env.PAGENOTE_GUIDE_MARKDOWN)
  await fs.copyFile(
    path.resolve(process.env.PAGENOTE_GUIDE_MARKDOWN),
    path.join(project, 'src/notes.md'),
  );
if (process.env.PAGENOTE_NATIVE_IMPORT) {
  const markdown = await fs.readFile(
    path.join(project, 'src/notes.md'),
    'utf8',
  );
  await fs.writeFile(
    path.join(project, 'src/notes.md'),
    markdown.replace(/```markdowns\s*\n[\s\S]*?\n```/g, ''),
  );
}
if (process.env.PAGENOTE_NATIVE_SOURCE) {
  await fs.copyFile(
    path.resolve(process.env.PAGENOTE_GUIDE_MARKDOWN),
    path.join(project, 'src/source.md'),
  );
  await fs.appendFile(
    path.join(project, 'src/source.md'),
    '\r\n\r\n<!-- pagebreak -->\r\n\r\n# 第二页原文\r\n\r\n保留中文、空行与原始分页符。\r\n',
  );
  await fs.writeFile(
    path.join(project, 'src/notes.md'),
    '# 阅读笔记\n\n左侧为原生 Markdown 交互课件，右侧笔记保持不变。\n',
  );
}
const launchOptions = {
  executablePath,
  args: process.env.DESKTOP_EXE ? [] : [root],
  env: {
    ...process.env,
    PAGE_NOTES_USER_DATA: path.join(temp, 'profile'),
    PAGE_NOTES_DATA: path.join(temp, 'data'),
  },
};
let app = await electron.launch(launchOptions);
const captures = [];
const captureViewports = {};
let expectedNativeState;
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
      await new Promise((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(resolve)),
      );
    });
    captureViewports[name] = await page.evaluate(() => ({
      width: innerWidth,
      height: innerHeight,
      density: devicePixelRatio,
      fullscreen: !!document.fullscreenElement,
    }));
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
  if (process.env.PAGENOTE_GUIDE_NATIVE) {
    if (
      process.env.PAGENOTE_NATIVE_IMPORT &&
      !process.env.PAGENOTE_NATIVE_SOURCE
    )
      await page
        .locator('#noteInput')
        .setInputFiles(process.env.PAGENOTE_NATIVE_IMPORT);
    const native = page.locator(
      process.env.PAGENOTE_NATIVE_SOURCE
        ? '#paper .markdown-s-native'
        : '#preview .markdown-s-native',
    );
    await native.waitFor();
    await page.evaluate(() => window.pageNotes.flush());
    const nativeBefore = await fs.readFile(nativeFile, 'utf8');
    assert.equal(await native.locator('iframe').count(), 0);
    const initialFirst = JSON.parse(await native.getAttribute('data-source'))
      .genes[0];
    await native.getByRole('button', { name: '专注作品', exact: true }).click();
    await page.waitForFunction(() =>
      document.fullscreenElement?.classList.contains('markdown-s-native'),
    );
    await snap('native-focus-overview');
    await native.getByRole('button', { name: '基因 1', exact: true }).click();
    await native.getByRole('button', { name: '返回笔记', exact: true }).click();
    await page.waitForFunction(() => !document.fullscreenElement);
    await native.getByRole('button', { name: '基因 1', exact: true }).focus();
    await page.keyboard.press('Space');
    await page.keyboard.press('Space');
    await native.getByRole('slider', { name: '采样位置' }).fill('80');
    await page.evaluate(() => window.pageNotes.flush());
    const saved = await fs.readFile(nativeFile, 'utf8');
    assert.ok(saved.includes('"position": 80'));
    const persisted = JSON.parse(
      saved.match(/```markdowns\s*\n([\s\S]*?)\n```/)[1],
    );
    assert.equal(persisted.genes[0], 1 - initialFirst);
    const stateOf = async () =>
      JSON.parse(await native.getAttribute('data-source'));
    await native
      .locator('figcaption')
      .evaluate((el) => el.scrollIntoView({ block: 'start' }));
    await snap('native-svg');
    await native
      .locator('label')
      .first()
      .evaluate((el) => el.scrollIntoView({ block: 'start' }));
    await snap('native-canvas');
    await native.getByRole('button', { name: '个体 2', exact: true }).click();
    assert.equal((await stateOf()).selectedIndividual, 1);
    await native
      .getByRole('heading', { name: '02 · 种群与个体', exact: true })
      .evaluate((el) => el.scrollIntoView({ block: 'start' }));
    await snap('native-population');
    const wheel = native.getByLabel('适应度选择轮盘，可拖动或使用左右方向键');
    await wheel.scrollIntoViewIfNeeded();
    const wheelBox = await wheel.boundingBox();
    await page.mouse.move(
      wheelBox.x + wheelBox.width * 0.7,
      wheelBox.y + wheelBox.height * 0.5,
    );
    await page.mouse.down();
    await page.mouse.move(
      wheelBox.x + wheelBox.width * 0.35,
      wheelBox.y + wheelBox.height * 0.75,
      { steps: 8 },
    );
    await page.mouse.up();
    assert.ok(Number.isInteger((await stateOf()).selectedIndividual));
    await wheel.focus();
    await page.keyboard.press('ArrowRight');
    await native
      .getByRole('heading', { name: '04 · 选择轮盘', exact: true })
      .evaluate((el) => el.scrollIntoView({ block: 'start' }));
    await snap('native-wheel');
    await native.getByRole('slider', { name: '交叉切点' }).fill('8');
    assert.equal((await stateOf()).cut, 8);
    await native
      .getByRole('heading', { name: '05 · 交叉切点', exact: true })
      .evaluate((el) => el.scrollIntoView({ block: 'start' }));
    await snap('native-crossover');
    await native.getByRole('slider', { name: '变异概率' }).fill('20');
    assert.equal((await stateOf()).mutationRate, 0.2);
    assert.ok((await native.innerText()).includes('2.40'));
    await native
      .getByRole('heading', { name: '06 · 变异概率', exact: true })
      .evaluate((el) => el.scrollIntoView({ block: 'start' }));
    await snap('native-mutation');
    await native.getByRole('slider', { name: '查看世代' }).fill('40');
    assert.ok(
      (
        await native
          .locator('input[aria-label="查看世代"] + output')
          .innerText()
      ).includes('99.97'),
    );
    await native
      .getByRole('heading', { name: '迭代轨迹 · 数据图表', exact: true })
      .evaluate((el) => el.scrollIntoView({ block: 'start' }));
    await snap('native-chart');
    const scene = native.locator('.markdown-native-scene');
    await scene.locator('canvas').waitFor();
    await scene.scrollIntoViewIfNeeded();
    await native.getByRole('button', { name: '继续旋转', exact: true }).click();
    await native.getByRole('button', { name: '暂停旋转', exact: true }).click();
    const beforeAngle = (await stateOf()).angle;
    const sceneBox = await scene.locator('canvas').boundingBox();
    await page.mouse.move(
      sceneBox.x + sceneBox.width * 0.5,
      sceneBox.y + sceneBox.height * 0.5,
    );
    await page.mouse.down();
    await page.mouse.move(
      sceneBox.x + sceneBox.width * 0.5 + 65,
      sceneBox.y + sceneBox.height * 0.5 + 10,
      { steps: 8 },
    );
    await page.mouse.up();
    assert.ok(Math.abs((await stateOf()).angle - beforeAngle) > 0.1);
    await native
      .getByRole('heading', {
        name: '三维染色体 · 拖动旋转／点击基因',
        exact: true,
      })
      .evaluate((el) => el.scrollIntoView({ block: 'start' }));
    await snap('native-three');
    await native.getByRole('button', { name: '专注作品', exact: true }).click();
    await native
      .getByRole('button', { name: '08 三维染色体', exact: true })
      .click();
    await scene.locator('canvas').waitFor();
    await snap('native-focus-three');
    await native.getByRole('button', { name: '返回笔记', exact: true }).click();
    const geneSvg = native.locator('svg').first();
    await geneSvg.scrollIntoViewIfNeeded();
    const genesBefore = [...(await stateOf()).genes],
      geneBox = await geneSvg.boundingBox();
    await page.mouse.move(
      geneBox.x + (geneBox.width * 45) / 600,
      geneBox.y + geneBox.height * 0.5,
    );
    await page.mouse.down();
    await page.mouse.move(
      geneBox.x + (geneBox.width * 185) / 600,
      geneBox.y + geneBox.height * 0.5,
      { steps: 15 },
    );
    await page.mouse.up();
    const genesAfter = (await stateOf()).genes;
    assert.ok(genesAfter.filter((g, i) => g !== genesBefore[i]).length >= 3);
    await native
      .locator('figcaption')
      .evaluate((el) => el.scrollIntoView({ block: 'start' }));
    await snap('native-brush');
    await page.evaluate(() => window.pageNotes.flush());
    const finalFile = await fs.readFile(nativeFile, 'utf8');
    const withoutNativeData = (text) =>
      text.replace(/```markdowns\s*\n[\s\S]*?\n```/, '[native data]');
    assert.equal(
      withoutNativeData(finalFile),
      withoutNativeData(nativeBefore),
      'native changes preserve every byte outside the data fence',
    );
    expectedNativeState = JSON.parse(
      finalFile.match(/\x60\x60\x60markdowns\s*\n([\s\S]*?)\n\x60\x60\x60/)[1],
    );
    assert.deepEqual(expectedNativeState, await stateOf());
    assert.ok(
      !(await fs.readdir(path.join(project, 'src'))).some((name) =>
        name.endsWith('.html'),
      ),
    );
    await scene.scrollIntoViewIfNeeded();
    await scene.locator('canvas').waitFor();
    const canLose = await scene.locator('canvas').evaluate((canvas) => {
      const ext = canvas
        .getContext('webgl2')
        ?.getExtension('WEBGL_lose_context');
      if (!ext) return false;
      ext.loseContext();
      return true;
    });
    if (canLose) {
      await native.locator('.markdown-native-scene[data-gpu="lost"]').waitFor();
      await scene.scrollIntoViewIfNeeded();
      await snap('native-gpu-fallback');
    }
    console.log(
      'PASS native population, roulette drag/keyboard, crossover, mutation, 40-generation fitness, 3D drag and brush; shared state saved in Markdown',
    );
    console.log(
      'PASS native SVG/Canvas without iframe; interactions write into the real Markdown project file',
    );
  } else if (process.env.PAGENOTE_GUIDE_MARKDOWN) {
    await page
      .locator('#preview .diagram-ready')
      .first()
      .scrollIntoViewIfNeeded();
    await snap('markdown-s-mermaid');
    await page
      .locator('#preview .katex-display')
      .first()
      .scrollIntoViewIfNeeded();
    await snap('markdown-s-math');
    if (!process.env.PAGENOTE_GUIDE_REFERENCE) {
      await page
        .locator('#preview code.language-python')
        .scrollIntoViewIfNeeded();
      await snap('markdown-s-code');
    }
    const blocks = page.locator('#preview .markdown-s-block');
    for (const [index, name] of (process.env.PAGENOTE_GUIDE_REFERENCE
      ? ['reference-in-pagenote']
      : ['interactive', 'svg', 'canvas', 'chart', 'three']
    ).entries()) {
      const block = blocks.nth(index);
      await block.scrollIntoViewIfNeeded();
      await block.getByRole('button', { name: '运行', exact: true }).click();
      await block.locator('iframe').waitFor();
      const inner = block.frameLocator('iframe');
      await inner
        .locator(
          process.env.PAGENOTE_GUIDE_REFERENCE
            ? '.hero canvas'
            : index === 0
              ? 'input'
              : index === 1 || index === 3
                ? 'svg'
                : 'canvas',
        )
        .waitFor();
      if (process.env.PAGENOTE_GUIDE_REFERENCE) {
        const before = await inner
          .locator('#mutationProbabilityText')
          .textContent();
        await inner.locator('#mutationRate').evaluate((input) => {
          input.value = '20';
          input.dispatchEvent(new Event('input', { bubbles: true }));
        });
        assert.equal(await inner.locator('#mutationRate').inputValue(), '20');
        assert.notEqual(
          await inner.locator('#mutationProbabilityText').textContent(),
          before,
        );
        assert.ok(
          (
            await inner.locator('#mutationProbabilityText').textContent()
          ).includes('2.40'),
        );
        await inner.locator('body').evaluate(() => scrollTo(0, 0));
      } else if (index === 0 || index === 2) {
        const before = await inner.locator('output').textContent();
        await inner.locator('input').evaluate((input) => {
          input.value = '80';
          input.dispatchEvent(new Event('input', { bubbles: true }));
        });
        assert.notEqual(await inner.locator('output').textContent(), before);
      } else if (index === 1) {
        const before = await inner.locator('output').textContent();
        await inner.locator('[data-index="0"]').click();
        assert.notEqual(await inner.locator('output').textContent(), before);
      } else if (index === 3) {
        await inner.getByRole('button', { name: '下一项' }).click();
        assert.ok(
          (await inner.locator('output').textContent()).includes('第10代'),
        );
      } else {
        await inner.getByRole('button', { name: '暂停旋转' }).click();
        await inner.getByRole('button', { name: '继续旋转' }).waitFor();
      }
      await block.scrollIntoViewIfNeeded();
      await block.getByRole('button', { name: '展开', exact: true }).click();
      await page.waitForFunction(() => !!document.fullscreenElement);
      await page.waitForFunction(() => {
        const frame = document.fullscreenElement?.querySelector('iframe');
        return frame && frame.getBoundingClientRect().width > innerWidth * 0.8;
      });
      await snap('markdown-s-' + name);
      if (!process.env.PAGENOTE_GUIDE_REFERENCE) {
        await inner.locator('body').evaluate(() => scrollTo(0, 0));
        await page.evaluate(() => document.exitFullscreen());
        await app.evaluate(({ BrowserWindow }) =>
          BrowserWindow.getAllWindows()[0].setSize(1000, 900),
        );
        await block.scrollIntoViewIfNeeded();
        await snap('markdown-s-' + name + '-narrow');
        await app.evaluate(({ BrowserWindow }) =>
          BrowserWindow.getAllWindows()[0].setSize(1440, 960),
        );
      }
      if (await page.evaluate(() => !!document.fullscreenElement))
        await page.evaluate(() => document.exitFullscreen());
      await block.getByRole('button', { name: '停止', exact: true }).click();
    }
  }
  if (!process.env.PAGENOTE_GUIDE_NATIVE) {
    await page.evaluate(() => window.pageNotes.menuAction('chooseEditor'));
    await page.locator('.note-pane').click({ position: { x: 100, y: 100 } });
    await snap('editing');
    if (process.env.PAGENOTE_VERIFY_PERSISTENCE) {
      await page
        .locator('#preview .markdown-s-block figcaption strong')
        .first()
        .dblclick();
      await page
        .getByRole('textbox', { name: 'Markdown S 源码' })
        .fill('<p>Persisted Markdown S marker</p>');
      await page.evaluate(() => window.pageNotes.flush());
      const saved = await fs.readFile(
        path.join(project, 'src/notes.md'),
        'utf8',
      );
      assert.ok(
        saved.includes('<p>Persisted Markdown S marker</p>'),
        'component source reaches the real project file',
      );
      assert.ok(saved.includes('mds-three'), 'other engines survive editing');
    }
    await page.locator('#noteEditButton').click();
    await page.locator('#aiSharedTools [data-ai-pointer=pen]').click();
    await snap('ink-tools');
    await page.locator('#aiSharedTools [data-ai-pointer=mouse]').click();
    await page.keyboard.press('Escape');
    await page.evaluate(() => window.pageNotes.menuAction('reviewRight'));
    await snap('ai-workbench');
  } else {
    await page.evaluate(() => window.pageNotes.flush());
    await snap('native-note-saved');
  }
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
        captureViewports,
      },
      null,
      2,
    ) + '\n',
  );
  console.log('Captured native ' + id + ': ' + captures.join(', '));
  if (process.env.PAGENOTE_GUIDE_NATIVE) {
    await app.close();
    app = await electron.launch(launchOptions);
    const reopened = await app.firstWindow();
    await reopened.waitForFunction(() => !!window.pageNotes);
    await dismissWelcome(reopened);
    if (await reopened.locator('#bookshelf').isVisible())
      await reopened.locator('.book-title', { hasText: '学习示例' }).click();
    const native = reopened.locator(
      process.env.PAGENOTE_NATIVE_SOURCE
        ? '#paper .markdown-s-native'
        : '#preview .markdown-s-native',
    );
    await native.waitFor();
    const restored = JSON.parse(await native.getAttribute('data-source'));
    assert.deepEqual(restored, expectedNativeState);
    assert.equal(await native.locator('iframe').count(), 0);
    console.log(
      'PASS native Markdown state restores after a full application restart, without an HTML file',
    );
  }
  if (process.env.PAGENOTE_VERIFY_PERSISTENCE) {
    await app.close();
    app = await electron.launch(launchOptions);
    const reopened = await app.firstWindow();
    await reopened.waitForFunction(() => !!window.pageNotes);
    await dismissWelcome(reopened);
    if (await reopened.locator('#bookshelf').isVisible())
      await reopened.locator('.book-title', { hasText: '学习示例' }).click();
    await reopened.locator('#preview .markdown-s-block').first().waitFor();
    assert.ok(
      (
        await reopened
          .locator('#preview .markdown-s-block')
          .first()
          .getAttribute('data-source')
      ).includes('Persisted Markdown S marker'),
    );
    assert.equal(
      await reopened.locator('#preview .mds-frame').count(),
      0,
      'restart does not auto-execute saved components',
    );
    console.log(
      'PASS real project file save, full application restart, source preservation and no auto-execution',
    );
  }
} finally {
  await app.close();
  await fs.rm(temp, { recursive: true, force: true });
}

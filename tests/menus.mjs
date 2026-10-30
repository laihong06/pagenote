import { _electron as electron } from '@playwright/test';
import path from 'node:path';
import assert from 'node:assert/strict';
const root = path.resolve(import.meta.dirname, '..');
const executable =
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
  env: {
    ...process.env,
    PAGE_NOTES_USER_DATA: path.join(root, 'tests', 'menus-' + Date.now()),
  },
});
try {
  const w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await w.evaluate(() => window.pageNotes.ready);
  await w.evaluate(() => window.pageNotes.setLanguage('zh-CN'));
  const menus = await app.evaluate(({ Menu }) =>
    Menu.getApplicationMenu().items.map((item) => ({
      label: item.label,
      role: item.role,
      children: item.submenu?.items.map((i) => i.label),
    })),
  );
  assert.ok(
    menus.find((m) => m.label === '文件').children.includes('导入文档（左侧）'),
  );
  assert.ok(
    menus.find((m) => m.label === '文件').children.includes('导入笔记（右侧）'),
  );
  const editMenu = menus.find((m) => m.label === '编辑');
  assert.ok(editMenu.children.includes('选择编辑区域'));
  if (process.platform === 'darwin') {
    assert.equal(menus[0].role, 'appmenu');
    assert.ok(menus.some((menu) => menu.role === 'windowmenu'));
    assert.ok(
      editMenu.children.length > 1,
      'standard Mac editing commands are available',
    );
  } else assert.deepEqual(editMenu.children, ['选择编辑区域']);
  assert.ok(
    menus.find((m) => m.label === '文件').children.includes('版本历史'),
  );
  assert.ok(
    menus.find((m) => m.label === 'AI 标注').children.includes('文档标注画布'),
  );
  const copiedSkill = await app.evaluate(async ({ Menu, clipboard }) => {
    const menu = Menu.getApplicationMenu().items.find(
      (item) => item.label === 'AI 标注',
    );
    menu.submenu.items
      .find((item) => item.label === '复制逐页精讲提示词')
      .click();
    const deadline = Date.now() + 5000;
    while (
      !(await clipboard.readText()).includes('name: pagenote-page-tutor') &&
      Date.now() < deadline
    )
      await new Promise((resolve) => setTimeout(resolve, 50));
    return clipboard.readText();
  });
  assert.ok(copiedSkill.includes('name: pagenote-page-tutor'));
  assert.ok(copiedSkill.includes('page-notes://prompts/index'));
  assert.ok(copiedSkill.includes('tag-edit'));
  assert.ok(await w.locator('.titlebar').isHidden());
  assert.ok(await w.locator('.note-pane .pane-footer #saveStatus').isVisible());
  assert.ok(await w.locator('#helpButton').isHidden());
  await w.evaluate(() => window.pageNotes.menuAction('newProject'));
  await w.locator('#newName').fill('新的学习项目');
  await w.locator('#newConfirm').click();
  await w.locator('#newConfirm').waitFor({ state: 'hidden' });
  await w.locator('.document-sheet').first().waitFor();
  assert.ok(
    (
      await w.evaluate(() => window.pageNotes.getState())
    ).document.name.includes('新的学习项目'),
  );
  assert.equal(await w.locator('.ai-workbench:visible').count(), 0);
  await w.evaluate(() => window.pageNotes.menuAction('reviewLeft'));
  assert.ok(await w.locator('.ai-workbench[data-side=document]').isVisible());
  assert.ok(await w.locator('.ai-workbench[data-side=note]').isHidden());
  for (const factor of [0.8, 1, 1.25]) {
    await app.evaluate(
      ({ BrowserWindow }, zoom) =>
        BrowserWindow.getAllWindows()[0].webContents.setZoomFactor(zoom),
      factor,
    );
    const alignment = await w.evaluate(() => {
      const rail = document
        .querySelector('#aiSharedTools')
        .getBoundingClientRect();
      const workspace = document
        .querySelector('#workspace')
        .getBoundingClientRect();
      const first = document
        .querySelector('#aiSharedTools > button')
        .getBoundingClientRect();
      return {
        railTop: rail.top,
        workspaceTop: workspace.top,
        buttonGap: first.top - rail.top,
      };
    });
    assert.ok(
      Math.abs(alignment.railTop - alignment.workspaceTop) <= 1,
      'tool rail aligns with workspace at scale ' + factor,
    );
    assert.ok(
      alignment.buttonGap <= 7,
      'no doubled top spacer at scale ' + factor,
    );
  }
  await app.evaluate(({ BrowserWindow }) =>
    BrowserWindow.getAllWindows()[0].webContents.setZoomFactor(1),
  );
  await w.locator('.ai-workbench[data-side=document] .board-close').click();
  await w.locator('#aiSharedTools [data-ai-pointer=mouse]').click();
  assert.ok(await w.locator('.mouse-options').isVisible());
  await w.locator('#aiSharedTools [data-ai-pointer=hand]').click();
  assert.ok(await w.locator('.mouse-options').isHidden());
  await w.locator('#aiSharedTools [data-ai-pointer=pen]').click();
  assert.ok(await w.locator('#aiPenColor').isVisible());
  assert.ok(await w.locator('[data-ai-pen=highlight]').isVisible());
  await w.evaluate(() => window.pageNotes.menuAction('chooseEditor'));
  assert.ok(
    await w
      .locator('body')
      .evaluate((e) => e.classList.contains('choose-editor')),
  );
  await w.locator('.note-pane .pane-heading').click();
  assert.equal(
    (await w.evaluate(() => window.pageNotes.getState())).mode,
    'edit',
  );
  await w.locator('#noteEditButton').click();
  await w.evaluate(() => window.pageNotes.menuAction('chooseEditor'));
  await w.locator('.document-pane .pane-heading').click();
  assert.equal(
    await w.locator('.markdown-page').first().getAttribute('contenteditable'),
    'true',
  );
  await w.locator('.markdown-page').first().click();
  await w.keyboard.insertText('编辑左侧文稿');
  await w.locator('#documentEditButton').click();
  assert.ok(
    (await w.evaluate(() => window.pageNotes.getState())).sourceText.includes(
      '编辑左侧文稿',
    ),
  );
  await w.screenshot({ path: path.join(root, 'tests/new-layout-preview.png') });
  console.log(
    'PASS single native menus; left/right imports; new project; edit-area selection; vertical icon tools and contextual choices; workbenches hidden until requested; footer navigation.',
  );
} finally {
  await app.close();
}

import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron } from '@playwright/test';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { PNG } from 'pngjs';
import path from 'node:path';
import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
const root = path.resolve(import.meta.dirname, '..');
const userData = path.join(root, 'tests', 'mcp-data-' + Date.now());
await fs.mkdir(userData, { recursive: true });
const executable =
  process.env.DESKTOP_EXE ||
  path.join(
    root,
    process.platform === 'darwin'
      ? 'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron'
      : 'node_modules/electron/dist/electron.exe',
  );
const launch = () =>
  electron.launch({
    executablePath: executable,
    args: [root],
    env: { ...process.env, PAGE_NOTES_USER_DATA: userData },
    timeout: 40000,
  });
let app = await launch();
let client;
function json(result) {
  assert.ok(!result.isError, result.content[0]?.text);
  return JSON.parse(result.content[0].text);
}
try {
  const window = await app.firstWindow();
  window.setDefaultTimeout(30000);
  await window.waitForSelector('#noteScroll');
  await window.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(window);
  const descriptor = JSON.parse(
    await fs.readFile(path.join(userData, 'ai-connection.json'), 'utf8'),
  );
  const denied = await fetch(descriptor.url + '/api/ai/call', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ action: 'get_state' }),
  });
  assert.equal(denied.status, 401);
  client = new Client({ name: 'page-notes-test', version: '1.0' });
  const config = JSON.parse(
    await fs.readFile(path.join(userData, 'mcp', 'mcp-config.json'), 'utf8'),
  ).mcpServers.page_notes;
  await client.connect(new StdioClientTransport({ ...config, stderr: 'pipe' }));
  assert.ok(client.getInstructions().includes('先识别用户当前任务'));
  assert.ok(client.getInstructions().includes('其他任务按用户要求执行'));
  const tools = await client.listTools();
  assert.equal(tools.tools.length, 24);
  const resources = await client.listResources();
  assert.ok(
    resources.resources.some((r) => r.uri === 'page-notes://skills/page-tutor'),
  );
  const skill = await client.readResource({
    uri: 'page-notes://skills/page-tutor',
  });
  const canonicalSkill = await fs.readFile(
    path.join(root, 'skills/pagenote-page-tutor/SKILL.md'),
    'utf8',
  );
  assert.equal(skill.contents[0].text, canonicalSkill);
  assert.ok(canonicalSkill.includes('上层 Agent 自主安排会话'));
  assert.ok(canonicalSkill.includes('逐页指成果对应原文页面'));
  const prompts = await client.listPrompts();
  assert.ok(prompts.prompts.some((p) => p.name === 'pagenote-page-tutor'));
  const prompt = await client.getPrompt({
    name: 'pagenote-page-tutor',
    arguments: {
      learner: '大三，学过一点计算机网络',
      pages: '6-7',
      goal: '陈述式逐层解释',
    },
  });
  assert.ok(prompt.messages[0].content.text.includes(canonicalSkill));
  assert.ok(
    prompt.messages[0].content.text.includes('大三，学过一点计算机网络'),
  );
  assert.ok(prompt.messages[0].content.text.includes('6-7'));
  const valid = json(
    await client.callTool({
      name: 'page_notes_validate_note',
      arguments: {
        markdown:
          '解释\n$$\n\\mu=1\n$$\n后文\n\n```mermaid\nflowchart LR\n A["样本"] --> B["估计"]\n```',
      },
    }),
  );
  assert.equal(valid.valid, true, JSON.stringify(valid.errors));
  assert.equal(valid.counts.formulas, 1);
  assert.equal(valid.counts.diagrams, 1);
  assert.ok(valid.normalizedMarkdown.includes('解释\n\n$$'));
  for (const markdown of [
    '```python\nprint(1)',
    '$$\n\\notARealCommand{1}\n$$',
    '```mermaid\nflowchart LR\n A -->[\n```',
    '![missing](/attachments/00000000-0000-0000-0000-000000000000.png)',
  ]) {
    const invalid = json(
      await client.callTool({
        name: 'page_notes_validate_note',
        arguments: { markdown },
      }),
    );
    assert.equal(invalid.valid, false, markdown);
    assert.ok(invalid.errors.length);
  }
  const emptyState = json(
    await client.callTool({ name: 'page_notes_get_state', arguments: {} }),
  );
  assert.ok(
    !emptyState.document,
    'preflight does not create or activate a document',
  );
  const imported = json(
    await client.callTool({
      name: 'page_notes_import_document',
      arguments: {
        filePath: path.join(root, 'tests', 'fixtures', 'sample.pdf'),
      },
    }),
  );
  const state = json(
    await client.callTool({
      name: 'page_notes_get_state',
      arguments: { detail: 'full' },
    }),
  );
  assert.equal(state.document.id, imported.id);
  assert.equal(state.total, 3);
  assert.ok(state.sourceText.includes('Pattern Recognition'));
  const first = json(
    await client.callTool({
      name: 'page_notes_read_pages',
      arguments: { documentId: imported.id, pages: [1, 2], include: 'both' },
    }),
  );
  console.log('PASS MCP reading source pages');
  const text =
    '# 无限长笔记纸张\n\n' +
    '这是一段较长的解释，纸张宽度固定，正文自动换行。\n\n'.repeat(90) +
    '\n```python\n' +
    'long_word_'.repeat(150) +
    '\n```\n\n```mermaid\nflowchart LR\n A[原文] --> B[理解] --> C[笔记]\n```';
  await window.evaluate(() => {
    window.notePublication = [];
    const sync = window.pageNotes.syncNotes;
    window.pageNotes.syncNotes = async (id) => {
      await sync(id);
      window.notePublication.push(await window.pageNotes.readPages([1, 2]));
    };
  });
  json(
    await client.callTool({
      name: 'page_notes_write_notes',
      arguments: {
        documentId: imported.id,
        updates: [
          {
            page: 1,
            markdown: text,
            expectedRevision: first.pages[0].revision,
          },
          { page: 2, markdown: '# 第二页\n\n$$\\mu=18$$' },
        ],
      },
    }),
  );
  const published = await window.evaluate(() => window.notePublication);
  console.log('PASS MCP per-page publication');
  assert.equal(published.length, 2, 'batch writes publish each completed page');
  assert.equal(published[0][0].markdown, text);
  assert.equal(published[1][1].markdown, '# 第二页\n\n$$\\mu=18$$');

  const layout = json(
    await client.callTool({
      name: 'page_notes_set_layout',
      arguments: {
        noteWidth: 420,
        padding: 28,
        fontSize: 16,
        leftPercent: 45,
        customCSS: '#preview h1 { color: #185abd; }',
      },
    }),
  );
  assert.equal(layout.noteWidth, 420);
  const paper = await window.locator('#noteBody').evaluate((e) => ({
    width: e.getBoundingClientRect().width,
    scroll: e.scrollWidth,
    height: e.scrollHeight,
  }));
  assert.ok(paper.width <= 421);
  assert.ok(
    paper.scroll <= paper.width + 2,
    'paper has no horizontal overflow',
  );
  assert.ok(paper.height > 2000, 'paper grows vertically');
  assert.equal(
    await window
      .locator('#preview h1')
      .evaluate((e) => getComputedStyle(e).color),
    'rgb(24, 90, 189)',
  );
  const updated = json(
    await client.callTool({
      name: 'page_notes_read_pages',
      arguments: { documentId: imported.id, pages: [1], include: 'both' },
    }),
  );
  assert.equal(
    updated.pages[0].markdown,
    text,
    'AI reader refreshes after mutation',
  );
  const compact = json(
    await client.callTool({ name: 'page_notes_get_state', arguments: {} }),
  );
  assert.ok(
    JSON.stringify(compact).length * 5 < JSON.stringify(state).length,
    'compact state avoids repeating long content',
  );
  assert.equal(compact.document.id, imported.id);
  assert.ok(
    !('markdown' in compact) &&
      !('sourceText' in compact) &&
      !('geometry' in compact),
  );
  const sourceOnly = json(
    await client.callTool({ name: 'page_notes_read_pages', arguments: {} }),
  );
  assert.equal(sourceOnly.pages[0].page, 1);
  assert.ok(sourceOnly.pages[0].sourceText.includes('Pattern Recognition'));
  assert.ok(!('markdown' in sourceOnly.pages[0]));
  const noteOnly = json(
    await client.callTool({
      name: 'page_notes_read_pages',
      arguments: { include: 'note' },
    }),
  );
  assert.equal(noteOnly.pages[0].markdown, text);
  assert.ok(!('sourceText' in noteOnly.pages[0]));
  const metadata = json(
    await client.callTool({
      name: 'page_notes_read_pages',
      arguments: { include: 'metadata' },
    }),
  );
  assert.equal(metadata.pages[0].noteLength, text.length);
  assert.equal(metadata.pages[0].revision, updated.pages[0].revision);
  assert.ok(
    !('markdown' in metadata.pages[0]) && !('sourceText' in metadata.pages[0]),
  );
  const cached = json(
    await client.callTool({
      name: 'page_notes_read_pages',
      arguments: {
        include: 'both',
        knownRevisions: {
          1: {
            source: sourceOnly.pages[0].sourceRevision,
            note: updated.pages[0].revision,
          },
        },
      },
    }),
  );
  assert.ok(cached.pages[0].sourceUnchanged && cached.pages[0].noteUnchanged);
  assert.ok(
    !('markdown' in cached.pages[0]) && !('sourceText' in cached.pages[0]),
  );
  const partial = json(
    await client.callTool({
      name: 'page_notes_read_pages',
      arguments: {
        include: 'note',
        noteRange: { start: 10, maxChars: 35 },
      },
    }),
  );
  assert.equal(partial.pages[0].markdown, text.slice(10, 45));
  assert.equal(partial.pages[0].revision, updated.pages[0].revision);
  assert.deepEqual(partial.pages[0].range, {
    start: 10,
    end: 45,
    totalChars: text.length,
  });
  const savedHandoff = json(
    await client.callTool({
      name: 'page_notes_write_handoff',
      arguments: {
        documentId: imported.id,
        page: 1,
        summary: '已建立样本、均值与密度的联系；下一页沿用样本记号。',
        expectedRevision: updated.pages[0].revision,
        sourceRevision: sourceOnly.pages[0].sourceRevision,
      },
    }),
  );
  assert.equal(savedHandoff.saved, true);
  assert.ok(!('summary' in savedHandoff));
  const handoff = json(
    await client.callTool({
      name: 'page_notes_read_handoff',
      arguments: { documentId: imported.id, page: 1 },
    }),
  );
  assert.equal(handoff.stale, false);
  assert.ok(handoff.handoff.summary.includes('样本'));
  const badHandoff = await client.callTool({
    name: 'page_notes_write_handoff',
    arguments: {
      documentId: imported.id,
      page: 1,
      summary: '旧版本交接不能覆盖',
      expectedRevision: '0'.repeat(64),
    },
  });
  assert.ok(badHandoff.isError);
  const wrongSource = await client.callTool({
    name: 'page_notes_write_handoff',
    arguments: {
      documentId: imported.id,
      page: 1,
      summary: '原文冲突',
      expectedRevision: updated.pages[0].revision,
      sourceRevision: '0'.repeat(64),
    },
  });
  assert.ok(wrongSource.isError);
  console.log(
    'PASS MCP compact state, selective reads, cache, ranges and handoffs',
  );
  const readingPosition = await window.evaluate(() => {
    const scroll = document.querySelector('#noteScroll');
    scroll.scrollTop = 500;
    window.readingHeading = document.querySelector('#preview h1');
    const selection = getSelection();
    selection.selectAllChildren(window.readingHeading);
    return { top: scroll.scrollTop, selected: selection.toString() };
  });
  console.log('Testing silent background updates');
  json(
    await client.callTool({
      name: 'page_notes_write_notes',
      arguments: {
        documentId: imported.id,
        updates: [{ page: 3, markdown: '# 第三页已生成\n\n后台逐页讲解。' }],
      },
    }),
  );
  assert.deepEqual(
    await window.evaluate(() => ({
      page: document.querySelector('#pageNumber').value,
      top: document.querySelector('#noteScroll').scrollTop,
      selected: getSelection().toString(),
      sameHeading:
        window.readingHeading === document.querySelector('#preview h1'),
    })),
    {
      page: '1',
      top: readingPosition.top,
      selected: readingPosition.selected,
      sameHeading: true,
    },
    'background pages preserve reading position, selection and DOM',
  );
  json(
    await client.callTool({
      name: 'page_notes_write_notes',
      arguments: {
        documentId: imported.id,
        updates: [{ page: 1, markdown: text + '\n\n本页实时补充。' }],
      },
    }),
  );
  assert.ok(
    (await window.locator('#preview').innerText()).includes('本页实时补充'),
  );
  const staleHandoff = json(
    await client.callTool({
      name: 'page_notes_read_handoff',
      arguments: { documentId: imported.id, page: 1 },
    }),
  );
  assert.equal(
    staleHandoff.stale,
    true,
    'edited notes invalidate the saved handoff',
  );
  assert.equal(await window.locator('#pageNumber').inputValue(), '1');
  assert.ok(
    Math.abs(
      (await window.locator('#noteScroll').evaluate((e) => e.scrollTop)) -
        readingPosition.top,
    ) < 2,
    'updating the viewed page preserves scroll',
  );
  await window.locator('#pageNumber').fill('3');
  await window.locator('#pageNumber').dispatchEvent('change');
  try {
    await window.waitForFunction(() =>
      document.querySelector('#preview').textContent.includes('第三页已生成'),
    );
  } catch (error) {
    console.log(
      'Navigation diagnostic',
      await window.evaluate(() => ({
        summary: window.pageNotes.getSummary(),
        input: document.querySelector('#pageNumber').value,
        preview: document.querySelector('#preview').textContent.slice(0, 150),
        scroll: {
          top: document.querySelector('#documentScroll').scrollTop,
          height: document.querySelector('#documentScroll').clientHeight,
          total: document.querySelector('#documentScroll').scrollHeight,
        },
        sheets: [...document.querySelectorAll('.document-sheet')].map((e) => ({
          page: e.dataset.page,
          top: e.getBoundingClientRect().top,
          bottom: e.getBoundingClientRect().bottom,
        })),
      })),
    );
    await window.screenshot({
      path: path.join(root, 'tmp/mcp-navigation-failure.png'),
    });
    throw error;
  }
  await window.locator('#pageNumber').fill('1');
  await window.locator('#pageNumber').dispatchEvent('change');
  await window.waitForFunction(() =>
    document.querySelector('#preview').textContent.includes('本页实时补充'),
  );
  console.log('PASS MCP silent updates and preserved reading position');
  await window.evaluate(() => window.pageNotes.lockEditing());
  await window.locator('#next').click();
  await window.waitForFunction(
    () => document.querySelector('#pageNumber').value === '2',
  );
  assert.ok(
    await window.locator('#editor').isDisabled(),
    'AI edit lock survives navigation',
  );
  await window.evaluate(() => window.pageNotes.unlockEditing());
  await window.locator('#prev').click();
  await window.waitForFunction(
    () => document.querySelector('#pageNumber').value === '1',
  );
  const conflict = await client.callTool({
    name: 'page_notes_write_notes',
    arguments: {
      documentId: imported.id,
      updates: [
        {
          page: 1,
          markdown: 'should not overwrite',
          expectedRevision: 'wrong',
        },
      ],
    },
  });
  assert.ok(conflict.isError);
  json(
    await client.callTool({
      name: 'page_notes_patch_note',
      arguments: { documentId: imported.id, page: 2, append: '追加解释' },
    }),
  );
  await client.callTool({
    name: 'page_notes_navigate',
    arguments: { documentId: imported.id, page: 2 },
  });
  const activeBefore = await window.locator('#pageNumber').inputValue();
  const screenshot = await client.callTool({
    name: 'page_notes_screenshot',
    arguments: {
      documentId: imported.id,
      page: 1,
      scope: 'note',
      fullNote: true,
      maxHeight: 2400,
    },
  });
  const meta = json(screenshot);
  const image = screenshot.content.find((c) => c.type === 'image');
  assert.ok(image);
  const png = PNG.sync.read(Buffer.from(image.data, 'base64'));
  assert.ok(png.height >= 2300);
  assert.ok(meta.truncated);
  await fs.copyFile(meta.path, path.join(root, 'tests', 'mcp-long-note.png'));
  assert.equal(
    await window.locator('#pageNumber').inputValue(),
    activeBefore,
    'hidden screenshots do not navigate visible window',
  );
  const sheet = await client.callTool({
    name: 'page_notes_contact_sheet',
    arguments: {
      documentId: imported.id,
      pages: [1, 2, 3],
      columns: 2,
      scope: 'workspace',
    },
  });
  const sheetMeta = json(sheet);
  assert.deepEqual(sheetMeta.pages, [1, 2, 3]);
  assert.ok(sheet.content.some((c) => c.type === 'image'));
  await fs.copyFile(
    sheetMeta.path,
    path.join(root, 'tests', 'mcp-contact-sheet.png'),
  );
  const created = json(
    await client.callTool({
      name: 'page_notes_create_markdown',
      arguments: {
        name: 'AI 创建的文档',
        markdown: '# 第一页\n\n说明\n\n---\n\n# 第二页',
        activate: false,
      },
    }),
  );
  assert.equal(created.kind, 'markdown');
  const createdPage = json(
    await client.callTool({
      name: 'page_notes_read_pages',
      arguments: {
        documentId: created.id,
        pages: [1],
        include: 'both',
      },
    }),
  ).pages[0];
  json(
    await client.callTool({
      name: 'page_notes_write_handoff',
      arguments: {
        documentId: created.id,
        page: 1,
        summary: '原文第一版的知识交接。',
        expectedRevision: createdPage.revision,
        sourceRevision: createdPage.sourceRevision,
      },
    }),
  );
  const sourceChanged = await window.evaluate(async (id) => {
    const response = await fetch('/api/documents/' + id + '/markdown', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        markdown: '# 第一页（修订）\n\n新说明\n\n---\n\n# 第二页',
      }),
    });
    return response.ok;
  }, created.id);
  assert.equal(sourceChanged, true);
  const changedPage = json(
    await client.callTool({
      name: 'page_notes_read_pages',
      arguments: {
        documentId: created.id,
        pages: [1],
        knownRevisions: { 1: { source: createdPage.sourceRevision } },
      },
    }),
  ).pages[0];
  assert.ok(changedPage.sourceText.includes('修订'));
  assert.notEqual(changedPage.sourceRevision, createdPage.sourceRevision);
  const changedHandoff = json(
    await client.callTool({
      name: 'page_notes_read_handoff',
      arguments: { documentId: created.id, page: 1 },
    }),
  );
  assert.equal(
    changedHandoff.stale,
    true,
    'source changes invalidate handoffs even with unchanged notes',
  );
  console.log('PASS MCP source changes invalidate cached content and handoffs');
  const resource = await client.readResource({ uri: 'page-notes://state' });
  assert.equal(JSON.parse(resource.contents[0].text).page, 2);
  assert.ok(
    !('markdown' in JSON.parse(resource.contents[0].text)),
    'state resource is compact too',
  );
  json(
    await client.callTool({
      name: 'page_notes_set_layout',
      arguments: {
        noteWidth: 1000,
        padding: 28,
        fontSize: 15,
        leftPercent: 42,
        customCSS: '#preview h1 { color: #185abd; }',
      },
    }),
  );
  const wide = await window.locator('#noteBody').evaluate((paper) => {
    const viewport = document.getElementById('noteScroll');
    const style = getComputedStyle(viewport);
    return {
      width: paper.getBoundingClientRect().width,
      available:
        viewport.clientWidth -
        parseFloat(style.paddingLeft) -
        parseFloat(style.paddingRight),
    };
  });
  assert.ok(
    Math.abs(wide.width - Math.min(1000, wide.available)) <= 3,
    'configured paper fills available writing space while staying bounded',
  );
  const mainWindow = await app.browserWindow(window);
  const screenshotMain = await mainWindow.evaluate(async (main) => {
    return (await main.webContents.capturePage(undefined, { stayAwake: true }))
      .toPNG()
      .toString('base64');
  });
  await fs.writeFile(
    path.join(root, 'tests', 'paper-preview.png'),
    Buffer.from(screenshotMain, 'base64'),
  );
  await app.close();
  app = await launch();
  const restarted = await app.firstWindow();
  await restarted.waitForSelector('#noteScroll');
  await restarted.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(restarted);
  const afterRestart = json(
    await client.callTool({
      name: 'page_notes_read_pages',
      arguments: { documentId: imported.id, pages: [2], include: 'note' },
    }),
  );
  assert.ok(
    afterRestart.pages[0].markdown.includes('追加解释'),
    'same MCP client reconnects after desktop restart',
  );
  const restartedHandoff = json(
    await client.callTool({
      name: 'page_notes_read_handoff',
      arguments: { documentId: imported.id, page: 1 },
    }),
  );
  assert.equal(restartedHandoff.handoff.summary, handoff.handoff.summary);
  assert.equal(restartedHandoff.stale, true);
  await fs.writeFile(
    path.join(root, 'tests', 'mcp-results.json'),
    JSON.stringify(
      {
        ok: true,
        tools: 20,
        checks: [
          'stdio MCP handshake',
          'tool discovery',
          'authenticated desktop bridge',
          'source and layout inspection',
          'batch writes',
          'compact state and selective source/note/metadata reads',
          'revision cache and partial note reads',
          'persistent handoffs with revision conflicts and staleness',
          'revision conflicts',
          'append',
          'layout persistence and custom CSS',
          'fixed-width long paper',
          'single long-note image',
          'three-page collage',
          'screenshot does not disturb reading',
          'Markdown creation',
          'MCP resource',
        ],
      },
      null,
      2,
    ),
  );
  console.log(
    'PASS MCP: 24 tools; selective reads/cache; persistent handoffs; compact state; silent writes; hidden screenshots and collage; restart with live MCP client.',
  );
} finally {
  await client?.close();
  await app.close();
}

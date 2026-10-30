import { dismissWelcome } from './dismiss-welcome.mjs';
import { useWebSettings } from './web-settings.mjs';
import { _electron as electron } from '@playwright/test';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
const root = path.resolve(import.meta.dirname, '..');
const profile = path.join(root, 'tests/workflows-data-' + Date.now());
const app = await electron.launch({
  executablePath:
    process.env.DESKTOP_EXE ||
    path.join(
      root,
      process.platform === 'darwin'
        ? 'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron'
        : 'node_modules/electron/dist/electron.exe',
    ),
  args: process.env.DESKTOP_EXE ? [] : [root],
  env: { ...process.env, PAGE_NOTES_USER_DATA: profile },
});
let client;
const unpack = (result) => {
  assert.ok(!result.isError, result.content[0]?.text);
  return JSON.parse(result.content[0].text);
};
try {
  const w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  const config = JSON.parse(
    await fs.readFile(path.join(profile, 'mcp/mcp-config.json'), 'utf8'),
  ).mcpServers.page_notes;
  client = new Client({ name: 'workflow-test', version: '1' });
  await client.connect(new StdioClientTransport({ ...config, stderr: 'pipe' }));
  const call = async (name, args) =>
    unpack(await client.callTool({ name, arguments: args }));
  const index = JSON.parse(
    (await client.readResource({ uri: 'page-notes://prompts/index' }))
      .contents[0].text,
  );
  const tools = (await client.listTools()).tools.map((t) => t.name);
  assert.equal(index.schemaVersion, 3);
  const shared = ['learning', 'representation', 'output', 'learner', 'profile'];
  for (const scenario of ['generation', 'revision'])
    for (const id of shared)
      assert.ok(
        index.routes.find((r) => r.scenario === scenario).modules.includes(id),
      );
  assert.deepEqual(
    index.intentRoutes.find((r) => r.intent === 'explain').modules,
    shared,
  );
  assert.deepEqual(index.capabilities.representation.native.kinds, [
    'genetic-lab',
  ]);
  const representation = await call('page_notes_read_prompt', {
    id: 'representation',
  });
  assert.ok(representation.text.includes('理解障碍'));
  const unchangedRepresentation = await call('page_notes_read_prompt', {
    id: 'representation',
    knownRevision: representation.revision,
  });
  assert.equal(unchangedRepresentation.unchanged, true);
  const nativeData = {
    version: 1,
    id: 'workflow-native',
    kind: 'genetic-lab',
    genes: [0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0],
  };
  const nativeMarkdown =
    '```markdowns\n' + JSON.stringify(nativeData) + '\n```';
  const validNative = await call('page_notes_validate_note', {
    markdown: nativeMarkdown,
  });
  assert.equal(validNative.valid, true);
  assert.equal(validNative.counts.enhancedBlocks, 1);
  assert.equal(validNative.visualBlocks[0].runtimeVerified, false);
  assert.equal(validNative.normalizedMarkdown, nativeMarkdown);
  const duplicates = await call('page_notes_validate_note', {
    markdown: nativeMarkdown + '\n\n' + nativeMarkdown,
  });
  assert.equal(duplicates.valid, false);
  assert.ok(
    duplicates.visualBlocks.every((b) =>
      b.errors.some((e) => e.includes('重复')),
    ),
  );
  const invalidKind = await call('page_notes_validate_note', {
    markdown: nativeMarkdown.replace('genetic-lab', 'invented-kind'),
  });
  assert.equal(invalidKind.valid, false);
  const unknownEngine = await call('page_notes_validate_note', {
    markdown: '```mds-invented\n{}\n```',
  });
  assert.equal(unknownEngine.valid, false);
  const invalidChart = await call('page_notes_validate_note', {
    markdown: '```mds-chart\n{"type":"pie"}\n```',
  });
  assert.equal(invalidChart.valid, false);
  const oversized = await call('page_notes_validate_note', {
    markdown:
      '```markdowns\n' +
      JSON.stringify({ ...nativeData, metadata: '汉'.repeat(70000) }) +
      '\n```',
  });
  assert.equal(oversized.valid, false);
  assert.ok(oversized.visualBlocks[0].sourceBytes > 200 * 1024);
  assert.ok(oversized.errors.some((e) => e.includes('200 KiB')));
  assert.ok(index.entry.includes('不规定会话数量'));
  for (const capability of Object.values(index.capabilities)) {
    if (capability.tool) assert.ok(tools.includes(capability.tool));
    for (const name of capability.tools || []) assert.ok(tools.includes(name));
  }
  const imported = await call('page_notes_import_document', {
    filePath: path.join(root, 'tests/fixtures/sample.pdf'),
  });
  await call('page_notes_write_notes', {
    documentId: imported.id,
    updates: [{ page: 2, markdown: 'Original explanation. '.repeat(30) }],
  });
  await w.evaluate(() => {
    document.querySelector('#documentScroll').scrollTop = 17;
    document.querySelector('#noteScroll').scrollTop = 0;
  });
  const before = await w.evaluate(() => ({
    page: window.pageNotes.getSummary().page,
    scroll: document.querySelector('#documentScroll').scrollTop,
  }));
  const task = await call('page_notes_prepare_task', {
    documentId: imported.id,
    page: 2,
    scenario: 'generation',
    maxChars: 100,
  });
  assert.deepEqual(
    task.sources.map((s) => s.page),
    [1, 2, 3],
  );
  assert.equal(task.sources[1].role, 'current');
  for (const id of shared) assert.ok(task.route.modules.includes(id));
  assert.ok(task.note.hasMore);
  assert.equal(
    task.note.range.totalChars,
    'Original explanation. '.repeat(30).length,
  );
  assert.ok(task.note.continueWith.noteRange.start === 100);
  const { tool: continuationTool, ...continuationArgs } =
    task.note.continueWith;
  const continuation = await call(continuationTool, continuationArgs);
  assert.equal(continuation.pages[0].range.start, 100);
  assert.equal(continuation.pages[0].revision, task.note.revision);

  const cached = await call('page_notes_prepare_task', {
    documentId: imported.id,
    page: 2,
    scenario: 'generation',
    neighbors: false,
  });
  assert.equal(cached.sources.length, 1);
  assert.equal(cached.sources[0].cached, true);
  const boundary = await call('page_notes_prepare_task', {
    documentId: imported.id,
    page: 1,
    scenario: 'generation',
  });
  assert.deepEqual(
    boundary.sources.map((s) => s.page),
    [1, 2],
  );
  const review = await w.evaluate(async (id) => {
    const r = await fetch(`/api/documents/${id}/ai-reviews`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        side: 'note',
        kind: 'overall',
        page: 2,
        scope: 'page',
        instruction: '这页先讲直觉，再给公式。',
      }),
    });
    if (!r.ok) throw Error('Tag creation failed');
    return r.json();
  }, imported.id);
  const revision = await call('page_notes_prepare_task', {
    documentId: imported.id,
    page: 2,
    scenario: 'revision',
    reviewIds: [review.id],
    neighbors: false,
  });
  assert.equal(revision.reviews[0].instruction, review.instruction);
  for (const id of shared) assert.ok(revision.route.modules.includes(id));
  const spelling = await call('page_notes_prepare_task', {
    documentId: imported.id,
    page: 2,
    scenario: 'revision',
    intent: 'edit',
    instruction: '只改错字',
    neighbors: false,
  });
  assert.ok(!spelling.route.modules.includes('learning'));
  assert.ok(!spelling.route.modules.includes('representation'));
  assert.equal(revision.note.revision, task.note.revision);
  assert.ok(revision.note.markdown.startsWith('Original explanation'));
  const wrongPage = await client.callTool({
    name: 'page_notes_prepare_task',
    arguments: {
      documentId: imported.id,
      page: 1,
      scenario: 'revision',
      reviewIds: [review.id],
      neighbors: false,
    },
  });
  assert.ok(wrongPage.isError);
  const initialProfile = await call('page_notes_read_prompt', {
    id: 'profile',
  });
  const preferenceArgs = {
    documentId: imported.id,
    preference: '适合时先建立直觉，再展开公式。',
    reviewIds: [review.id],
    expectedRevision: initialProfile.revision,
  };
  assert.ok(
    (
      await client.callTool({
        name: 'page_notes_update_profile',
        arguments: preferenceArgs,
      })
    ).isError,
    'unfinished modifications cannot become evidence',
  );
  await call('page_notes_update_ai_review', {
    documentId: imported.id,
    recordId: review.id,
    status: 'applied',
  });
  const saved = await call('page_notes_update_profile', preferenceArgs);
  assert.equal(saved.status, 'candidate');
  const updated = await call('page_notes_read_prompt', { id: 'profile' });
  assert.ok(updated.text.includes(review.instruction));
  assert.ok(updated.text.includes('未由用户确认'));
  assert.ok(
    (
      await client.callTool({
        name: 'page_notes_update_profile',
        arguments: { ...preferenceArgs, preference: 'Another preference' },
      })
    ).isError,
    'stale profile revision rejected',
  );
  const duplicate = await call('page_notes_update_profile', {
    ...preferenceArgs,
    expectedRevision: updated.revision,
  });
  assert.ok(duplicate.duplicate);
  const direct = await call('page_notes_prepare_task', {
    documentId: imported.id,
    page: 2,
    scenario: 'revision',
    neighbors: false,
    instruction: '只调整语言风格',
  });
  assert.equal(direct.instruction, '只调整语言风格');
  assert.deepEqual(direct.reviews, []);
  const directArgs = {
    documentId: imported.id,
    evidenceId: direct.evidenceId,
    preference: '调整表达风格时保留知识结构。',
    expectedRevision: updated.revision,
  };
  assert.ok(
    (
      await client.callTool({
        name: 'page_notes_update_profile',
        arguments: directArgs,
      })
    ).isError,
  );
  await call('page_notes_patch_note', {
    documentId: imported.id,
    page: 2,
    append: '\n风格已调整。',
    expectedRevision: direct.note.revision,
  });
  const directProfile = await call('page_notes_update_profile', directArgs);
  assert.equal(directProfile.status, 'candidate');

  assert.deepEqual(
    await w.evaluate(() => ({
      page: window.pageNotes.getSummary().page,
      scroll: document.querySelector('#documentScroll').scrollTop,
    })),
    before,
    'preparation and preferences preserve visible reading position',
  );
  await useWebSettings(app);
  await w.evaluate(() => window.pageNotes.openAISettings());
  await w.locator('#aiPromptModule').selectOption('representation');
  await w.waitForFunction(() =>
    document.querySelector('#aiSystemPrompt').value.includes('# 表达与交互'),
  );
  const gallery = path.join(
    root,
    'docs',
    process.platform === 'darwin' ? 'macos' : 'windows',
    'guide/images/ai-representation',
  );
  await fs.mkdir(gallery, { recursive: true });
  await w.screenshot({ path: path.join(gallery, 'settings.png') });
  await fs.writeFile(
    path.join(gallery, 'provenance.json'),
    JSON.stringify(
      {
        platform: process.platform,
        capturedAt: new Date().toISOString(),
        runtime:
          'Electron source build; uncommitted representation changes based on e8bce49',
        scope:
          'Real AI settings showing the editable representation module; not model behavior evaluation',
        viewport: await w.evaluate(() => ({
          width: innerWidth,
          height: innerHeight,
          density: devicePixelRatio,
        })),
      },
      null,
      2,
    ) + '\n',
  );
  await w.locator('#aiPromptModule').selectOption('entry');
  await w.waitForFunction(() =>
    document.querySelector('#aiSystemPrompt').value.includes('# 入口与路由'),
  );
  const entryText = await w.locator('#aiSystemPrompt').inputValue();
  await w.locator('#aiSystemPrompt').fill(entryText + '\n测试入口自定义。');
  const response = w.waitForResponse(
    (r) =>
      r.url().endsWith('/api/ai-prompts/entry') &&
      r.request().method() === 'PUT',
  );
  await w.locator('#aiSettingsSave').click();
  assert.ok((await response).ok());
  const liveIndex = JSON.parse(
    (await client.readResource({ uri: 'page-notes://prompts/index' }))
      .contents[0].text,
  );
  assert.ok(liveIndex.entry.includes('测试入口自定义。'));
  assert.notEqual(liveIndex.revision, index.revision);
  await w.screenshot({ path: path.join(root, 'tmp/workflow-settings.png') });
  await w.locator('#aiSettingsCancel').click();
  await w.screenshot({ path: path.join(root, 'tmp/workflow-page.png') });
  assert.equal(await w.locator('.document-sheet.is-current').count(), 1);
  await w.reload();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  assert.equal(
    (await call('page_notes_read_prompt', { id: 'profile' })).revision,
    directProfile.revision,
  );
  console.log(
    'PASS workflow routing/catalog, local neighbor preparation/cache/pagination, scoped review data, evidence-backed profile/CAS/persistence, editable live entry and silent UI',
  );
} finally {
  await client?.close();
  await app.close();
}

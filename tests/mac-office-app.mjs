import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron } from '@playwright/test';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';

if (process.platform !== 'darwin') {
  console.log('SKIP Mac Office application test on this platform.');
  process.exit(0);
}
const root = path.resolve(import.meta.dirname, '..');
const profile = path.join(root, 'tests', 'mac-office-app-data-' + Date.now());
const desktop = await electron.launch({
  executablePath:
    process.env.DESKTOP_EXE ||
    path.join(
      root,
      'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron',
    ),
  args: [root],
  env: { ...process.env, PAGE_NOTES_USER_DATA: profile },
  timeout: 40000,
});
let client;
let window;
function json(result) {
  assert.ok(!result.isError, result.content[0]?.text);
  return JSON.parse(result.content.find((item) => item.type === 'text').text);
}
try {
  window = await desktop.firstWindow();
  window.setDefaultTimeout(120000);
  await window.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(window);
  const config = JSON.parse(
    await fs.readFile(path.join(profile, 'mcp/mcp-config.json'), 'utf8'),
  ).mcpServers.page_notes;
  client = new Client({ name: 'mac-office-app-test', version: '1.0' });
  await client.connect(new StdioClientTransport({ ...config, stderr: 'pipe' }));
  const call = (name, args = {}) =>
    client.callTool(
      { name: 'page_notes_' + name, arguments: args },
      undefined,
      { timeout: 180000 },
    );
  const word = json(
    await call('import_document', {
      filePath: path.join(root, 'tests/fixtures/sample.docx'),
    }),
  );
  let state = await window.evaluate(() => window.pageNotes.getState());
  assert.equal(state.total, 2);
  assert.ok(state.sourceText.includes('Document parsing test'));
  const before = json(await call('read_word', { documentId: word.id }));
  assert.ok(before.text.includes('Feature'));
  assert.equal(
    await window
      .locator('body')
      .evaluate((e) => e.classList.contains('word-active')),
    false,
    'external Mac Word does not use embedded-window layout',
  );
  const stale = await call('patch_word', {
    documentId: word.id,
    search: 'Document parsing test',
    replacement: 'wrong',
    expectedRevision: 'stale',
  });
  assert.ok(stale.isError, 'stale Word revision must reject replacement');
  const screenshot = await call('screenshot', {
    documentId: word.id,
    scope: 'word',
  });
  assert.equal(json(screenshot).renderedFrom, 'pdf');
  assert.ok(
    screenshot.content.find((item) => item.type === 'image')?.data.length >
      1000,
  );
  const replacement = 'Mac Office roundtrip 中文 "quotes" \\ paths';
  const changed = json(
    await call('patch_word', {
      documentId: word.id,
      search: 'Document parsing test',
      replacement,
      start: 0,
      expectedRevision: before.revision,
    }),
  );
  assert.ok(changed.saved);
  state = await window.evaluate(() => window.pageNotes.getState());
  assert.ok(state.sourceText.includes('Mac Office roundtrip'));
  const repeated = json(await call('read_word', { documentId: word.id }));
  assert.ok(repeated.text.includes(replacement));
  assert.ok(
    repeated.text.includes('Feature'),
    'table survives the Word round trip',
  );
  await window.evaluate(() => window.pageNotes.finishEditing());

  const powerpoint = json(
    await call('import_document', {
      filePath: path.join(root, 'tests/fixtures/sample.pptx'),
    }),
  );
  state = await window.evaluate(() => window.pageNotes.getState());
  assert.equal(state.document.id, powerpoint.id);
  assert.equal(state.total, 3);
  assert.ok(state.sourceText.includes('Density Estimation'));

  await window.evaluate(() => window.pageNotes.menuAction('newProject'));
  await window.locator('#newName').fill('Mac Office project 中文');
  await window.locator('#newParent').fill(path.join(profile, 'projects'));
  await window.locator('#newConfirm').click();
  await window.locator('#newConfirm').waitFor({ state: 'hidden' });
  await window
    .locator('#fileInput')
    .setInputFiles(path.join(root, 'tests/fixtures/sample.docx'));
  await window.waitForFunction(async () => {
    const s = await window.pageNotes.getState();
    return !!s.document?.projectRoot && s.document.ext === '.docx';
  });
  state = await window.evaluate(() => window.pageNotes.getState());
  const projectId = state.document.id;
  const projectFile = state.document.projectFile;
  json(
    await call('write_notes', {
      documentId: projectId,
      updates: [{ page: 1, markdown: 'Project notes must remain.' }],
    }),
  );
  const projectWord = json(await call('read_word', { documentId: projectId }));
  const projectManifest = JSON.parse(await fs.readFile(projectFile, 'utf8'));
  const linkedSource = path.join(
    state.document.projectRoot,
    projectManifest.left,
  );
  const cachedProjectSource = await fs.readFile(linkedSource);
  await fs.writeFile(linkedSource, 'External source change during Word edit');
  await call('get_state');
  assert.equal(
    await fs.readFile(linkedSource, 'utf8'),
    'External source change during Word edit',
    'an MCP read while Word is active must not publish stale cached source',
  );
  await fs.writeFile(linkedSource, cachedProjectSource);
  const projectImage = await call('screenshot', {
    documentId: projectId,
    scope: 'word',
  });
  assert.ok(projectImage.content.some((item) => item.type === 'image'));
  json(
    await call('patch_word', {
      documentId: projectId,
      search: projectWord.text.split('\r')[0],
      replacement: 'Project Word change persisted',
      expectedRevision: projectWord.revision,
    }),
  );
  await window.evaluate(
    (file) => window.pageNotes.openProject(file),
    projectFile,
  );
  state = await window.evaluate(() => window.pageNotes.getState());
  assert.ok(
    state.sourceText.includes('Project Word change persisted'),
    'reopening linked project must not restore the old Word source',
  );
  assert.ok(state.markdown.includes('Project notes must remain.'));
  console.log(
    'PASS Mac Office app/MCP: Word and PPT imports, revision guards, Chinese range edits, live PDF screenshots, table retention and project source/note persistence.',
  );
} finally {
  if (window)
    await window
      .evaluate(() => window.pageNotes.finishEditing())
      .catch(() => {});
  await client?.close().catch(() => {});
  await desktop.close();
}

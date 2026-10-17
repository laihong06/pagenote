import { dismissWelcome } from './dismiss-welcome.mjs';
import { useWebSettings } from './web-settings.mjs';
import { _electron as electron } from '@playwright/test';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
const root = path.resolve(import.meta.dirname, '..'),
  profile = path.join(root, 'tests/ai-settings-data-' + Date.now());
const launch = () =>
  electron.launch({
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
let app = await launch(),
  client;
try {
  let w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await useWebSettings(app);
  await w.evaluate(
    (locale) => window.pageNotes.setLanguage(locale),
    process.env.TEST_LOCALE || 'zh-CN',
  );
  await w.evaluate(() => window.pageNotes.openAISettings());
  const defaults = await w.locator('#aiSystemPrompt').inputValue();
  if (process.env.AI_SETTINGS_SCREENSHOT)
    await w.screenshot({ path: process.env.AI_SETTINGS_SCREENSHOT });
  assert.ok(defaults.includes('不把所有任务都变成逐页讲课'));
  const custom =
    '先遵守本次任务范围。用简洁中文解释；遇到论文先区分结论与证据。';
  await w.locator('#aiSystemPrompt').fill(custom);
  const savePrompt = async () => {
    const saved = w.waitForResponse(
      (response) =>
        /\/api\/(?:ai-settings|ai-prompts\/[^/]+)$/.test(
          new URL(response.url()).pathname,
        ) && response.request().method() === 'PUT',
    );
    await w.locator('#aiSettingsSave').click();
    assert.ok((await saved).ok(), 'prompt save request succeeds');
    await w.waitForFunction(
      () => !document.querySelector('#aiSettingsSave').disabled,
    );
    assert.ok((await w.locator('#aiSettingsStatus').textContent()).trim());
  };
  await savePrompt();
  assert.equal(
    JSON.parse(
      await fs.readFile(
        path.join(profile, 'documents-data/ai-settings.json'),
        'utf8',
      ),
    ).systemPrompt,
    custom,
  );
  const config = JSON.parse(
    await fs.readFile(path.join(profile, 'mcp/mcp-config.json'), 'utf8'),
  ).mcpServers.page_notes;
  client = new Client({ name: 'ai-settings-test', version: '1' });
  await client.connect(new StdioClientTransport({ ...config, stderr: 'pipe' }));
  assert.ok(client.getInstructions().includes(custom));
  assert.ok(!client.getInstructions().includes(defaults));
  const skill = await client.readResource({
    uri: 'page-notes://skills/page-tutor',
  });
  assert.ok(skill.contents[0].text.includes('不等待上一页讲解完成'));
  const indexBefore = JSON.parse(
    (await client.readResource({ uri: 'page-notes://prompts/index' }))
      .contents[0].text,
  );
  assert.equal(indexBefore.modules.length, 8);
  assert.ok(indexBefore.routes.some((r) => r.modules.includes('tag-edit')));
  await w.locator('#aiPromptModule').selectOption('page-tutor');
  await w.waitForFunction(() =>
    document.querySelector('#aiSystemPrompt').value.includes('floor('),
  );
  const moduleDefault = await w.locator('#aiSystemPrompt').inputValue();
  const moduleCustom = moduleDefault + '\n本机测试偏好：先给直觉。';
  await w.locator('#aiSystemPrompt').fill(moduleCustom);
  await savePrompt();
  const indexAfter = JSON.parse(
    (await client.readResource({ uri: 'page-notes://prompts/index' }))
      .contents[0].text,
  );
  assert.notEqual(indexAfter.revision, indexBefore.revision);
  const loaded = JSON.parse(
    (
      await client.callTool({
        name: 'page_notes_read_prompt',
        arguments: { id: 'page-tutor' },
      })
    ).content[0].text,
  );
  assert.equal(
    loaded.text,
    moduleCustom,
    'live MCP sees customized module without reconnect',
  );
  const cached = JSON.parse(
    (
      await client.callTool({
        name: 'page_notes_read_prompt',
        arguments: { id: 'page-tutor', knownRevision: loaded.revision },
      })
    ).content[0].text,
  );
  assert.equal(cached.unchanged, true);
  assert.ok(!('text' in cached));
  assert.ok(!('defaultText' in cached));
  await w.locator('#aiPromptReset').click();
  assert.equal(await w.locator('#aiSystemPrompt').inputValue(), moduleDefault);
  await w.locator('#aiPromptModule').selectOption('system');
  await w.waitForFunction(
    () =>
      document.querySelector('#aiSystemPrompt').value ===
      '先遵守本次任务范围。用简洁中文解释；遇到论文先区分结论与证据。',
  );
  await client.close();
  client = null;
  await w.locator('#aiPromptReset').click();
  assert.equal(await w.locator('#aiSystemPrompt').inputValue(), defaults);
  await w.locator('#aiSettingsCancel').click();
  await w.evaluate(() => window.pageNotes.openAISettings());
  assert.equal(
    await w.locator('#aiSystemPrompt').inputValue(),
    custom,
    'unsaved reset must not overwrite saved preferences',
  );
  const invalid = await w.evaluate(async () => {
    const r = await fetch('/api/ai-settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ systemPrompt: 42 }),
    });
    return r.status;
  });
  assert.equal(invalid, 400);
  await app.close();
  app = await launch();
  w = await app.firstWindow();
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  await useWebSettings(app);
  await w.evaluate(() => window.pageNotes.openAISettings());
  assert.equal(await w.locator('#aiSystemPrompt').inputValue(), custom);
  await w.locator('#aiPromptReset').click();
  await savePrompt();
  assert.equal(
    JSON.parse(
      await fs.readFile(
        path.join(profile, 'documents-data/ai-settings.json'),
        'utf8',
      ),
    ).systemPrompt,
    defaults,
  );
  await w.locator('#aiSettingsCancel').click();
  await w.evaluate(() => window.pageNotes.setLanguage('en-US'));
  await w.evaluate(() => window.pageNotes.openAISettings());
  assert.ok(
    (await w.locator('#aiSettings').innerText()).includes(
      'Default system prompt',
    ),
  );
  console.log(
    'PASS AI prompt edit/save/restart, MCP instructions, general skill routing, unsaved reset/cancel, reset/save, validation and English UI',
  );
} finally {
  await client?.close();
  await app.close();
}

import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import settings from '../desktop/ai-settings.cjs';
import prompts from '../desktop/prompts.cjs';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const store = prompts.createPromptStore(
  path.join(root, 'skills'),
  path.join(root, 'tmp/prompt-export-defaults'),
);
let text =
  '# 当前内置提示词完整清单\n\n由 scripts/export-prompts.mjs 从默认源码导出；本机 AI 设置中的自定义覆盖不包含在此清单。\n\n## 默认系统提示词\n\n' +
  settings.defaultSystemPrompt +
  '\n\n## 统一入口\n\n' +
  prompts.entry +
  '\n\n';
for (const route of store.index().routes)
  text +=
    '- ' +
    route.task +
    ' → ' +
    (route.modules.join('、') || '按本次要求') +
    '。' +
    route.first +
    '。\n';
text += '\n## 表达意图路由（与工作流合并）\n\n```json\n' + JSON.stringify(store.index().intentRoutes,null,2) + '\n```\n';
for (const item of prompts.modules) {
  const value = store.read(item.id);
  text +=
    '\n## ' +
    item.title +
    '（' +
    item.id +
    '）\n\n' +
    value.defaultText.replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, '') +
    '\n';
}
text +=
  '\n## MCP 能力与本地材料入口\n\n```json\n' +
  JSON.stringify(store.index().capabilities, null, 2) +
  '\n```\n';
await fs.writeFile(
  path.join(root, 'docs/PROMPTS.zh-CN.md'),
  text.trimEnd() + '\n',
);

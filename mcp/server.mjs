import fs from 'node:fs/promises';
import path from 'node:path';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { z } from 'zod';
import packageInfo from '../package.json' with { type: 'json' };
import aiSettingsStore from '../desktop/ai-settings.cjs';
import promptStoreModule from '../desktop/prompts.cjs';

const i = process.argv.indexOf('--connection-file');
const connectionFile =
  i >= 0
    ? process.argv[i + 1]
    : process.env.PAGE_NOTES_CONNECTION_FILE ||
      path.join(
        process.env.APPDATA || process.env.HOME || '.',
        'pagenote',
        'ai-connection.json',
      );
const server = new McpServer(
  { name: 'pagenote', version: packageInfo.version },
  {
    instructions:
      '控制本机pagenote桌面软件。先读取状态或文档列表；修改前读取页面并保留 revision，用 expectedRevision 避免覆盖用户的新修改。截图和拼图返回 image/png 图片内容，多模态模型可直接查看。操作只影响软件的文档、笔记和布局。截图在隐藏窗口生成，不切换用户的阅读页。\n\n' +
      aiSettingsStore.readAISettings(
        path.join(
          path.dirname(connectionFile),
          'documents-data',
          'ai-settings.json',
        ),
      ).systemPrompt +
      '\n先读取 page-notes://prompts/index，按模块用途与revision选择性读取；其他任务按用户要求执行。',
  },
);
const documentId = z
  .string()
  .regex(/^[a-f0-9]{64}$/)
  .optional();
const page = z.number().int().min(1).max(10000);
const pages = z.array(page).min(1).max(40);
async function call(action, args) {
  try {
    const descriptor = JSON.parse(await fs.readFile(connectionFile, 'utf8'));
    const response = await fetch(descriptor.url + '/api/ai/call', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer ' + descriptor.token,
      },
      body: JSON.stringify({ action, args }),
      signal: AbortSignal.timeout(120000),
    });
    const result = await response.json();
    if (!response.ok) throw Error(result.error || 'pagenote请求失败');
    if (result.imageBase64) {
      const { imageBase64, ...meta } = result;
      return {
        content: [
          { type: 'text', text: JSON.stringify(meta) },
          { type: 'image', data: imageBase64, mimeType: 'image/png' },
        ],
      };
    }
    return {
      content: [{ type: 'text', text: JSON.stringify(result) }],
    };
  } catch (error) {
    return {
      isError: true,
      content: [
        {
          type: 'text',
          text:
            'pagenote操作失败：' +
            error.message +
            '。如果软件未打开，请先启动桌面版。',
        },
      ],
    };
  }
}
function tool(name, description, inputSchema, action, readOnly = false) {
  server.registerTool(
    name,
    {
      description,
      inputSchema,
      annotations: {
        readOnlyHint: readOnly,
        destructiveHint: false,
        openWorldHint: false,
      },
    },
    (args) => call(action, args),
  );
}
tool(
  'page_notes_list_documents',
  '列出本机已导入的课件和 Markdown 文档。',
  {},
  'list_documents',
  true,
);
tool(
  'page_notes_get_state',
  '默认只返回当前文档、页码和模式；需要正文、笔记和界面尺寸时显式选 detail=full。',
  { detail: z.enum(['compact', 'full']).default('compact') },
  'get_state',
  true,
);
tool(
  'page_notes_prepare_task',
  '按生成或修改场景准备本地材料：当前/相邻源页、缓存OCR及图片入口、当前笔记revision、选定Tag和直接修改意见。返回分页续读位置，不导航，不规定Agent调度。',
  {
    documentId,
    page: page.optional(),
    scenario: z.enum(['generation', 'revision']),
    neighbors: z.boolean().optional(),
    reviewIds: z.array(z.string().uuid()).max(50).optional(),
    instruction: z.string().max(20000).optional(),
    maxChars: z.number().int().min(100).max(20000).optional(),
  },
  'prepare_task',
  true,
);
tool(
  'page_notes_update_profile',
  '从已完成Tag或已保存修改的任务意见凭据提炼候选偏好，保留来源；需要当前profile revision。没有依据时不更新。',
  {
    documentId,
    preference: z.string().min(1).max(2000),
    reviewIds: z.array(z.string().uuid()).min(1).max(20).optional(),
    evidenceId: z.string().uuid().optional(),
    expectedRevision: z.string().regex(/^[a-f0-9]{64}$/),
  },
  'update_profile',
);
tool(
  'page_notes_read_ai_reviews',
  '读取用户编号的 AI 批注、整体要求、框选坐标、选中文字、上下文和创建时 revision。根据要求修改后，再标为 applied。',
  { documentId, status: z.enum(['open', 'applied', 'dismissed']).optional() },
  'read_ai_reviews',
  true,
);
tool(
  'page_notes_update_ai_review',
  '更新批注要求或处理状态。只有实际完成对应修改后才标记 applied。',
  {
    documentId,
    recordId: z.string().uuid(),
    status: z.enum(['open', 'applied', 'dismissed']).optional(),
    instruction: z.string().max(20000).optional(),
  },
  'update_ai_review',
);
tool(
  'page_notes_read_word',
  '打开本机 Word 编辑器并读取真实 Word 全文及 revision。此操作会进入用户可见的 Word 编辑模式。',
  { documentId },
  'read_word',
);
tool(
  'page_notes_patch_word',
  '通过 Word 修改原文。必须提供当前 expectedRevision；重复文字可用批注 anchor.wordStart 作为 start 精确定位，否则原文须唯一。保存前备份文档并更新阅读 PDF。复杂格式可用 Word 原生工具。',
  {
    documentId,
    search: z.string().min(1),
    replacement: z.string(),
    start: z.number().int().min(0).optional(),
    expectedRevision: z.string(),
  },
  'patch_word',
);
tool(
  'page_notes_read_pages',
  '按需读取页面，省略 pages 时读取当前页。默认 include=source 只取原文；note 只取笔记，both 同时读取，metadata 只取笔记 revision/长度。knownRevisions 命中时省略未变化正文。noteRange 可只取笔记局部；扫描或复杂图表再用截图。',
  {
    documentId,
    pages: pages.optional(),
    include: z.enum(['source', 'note', 'both', 'metadata']).default('source'),
    knownRevisions: z
      .record(
        z.string(),
        z.object({
          source: z.string().optional(),
          note: z.string().optional(),
        }),
      )
      .optional(),
    noteRange: z
      .object({
        start: z.number().int().min(0),
        maxChars: z.number().int().min(1).max(100000),
      })
      .optional(),
  },
  'read_pages',
  true,
);
tool(
  'page_notes_read_handoff',
  '读取指定页保存的简短知识交接及是否过期，供下一页承接。过期时按需重读相关内容，不直接沿用。',
  { documentId, page },
  'read_handoff',
  true,
);
tool(
  'page_notes_write_handoff',
  '保存本页知识交接：概念、符号、例子、结论和未解依赖，建议约1000字以内。须提供最新笔记 expectedRevision；可附 sourceRevision 跟踪原文变化。仅返回保存状态。',
  {
    documentId,
    page,
    summary: z.string().min(1).max(4000),
    expectedRevision: z.string(),
    sourceRevision: z.string().optional(),
  },
  'write_handoff',
);
tool(
  'page_notes_navigate',
  '在用户可见窗口打开指定文档和页面。',
  { documentId, page },
  'navigate',
);
tool(
  'page_notes_prepare_page',
  '本地准备一页供AI读取：PDF文字层、必要时中文/英文OCR、坐标与置信度、完整页面PNG；Markdown表格和Mermaid转成结构/图像。结果缓存到项目src/prepared。默认只返回摘要；output=text/blocks/tables/image按需读取，长文字用start续读。ocr=always可补识别图片内文字，region按页面1000×1000裁剪。OCR与图表解释不保证无损，始终保留原文/原图并检查警告；不自动恢复PDF表格/公式/图表数据。不会切换用户页面。',
  {
    documentId,
    page: page.optional(),
    ocr: z.enum(['auto', 'always', 'never']).default('auto'),
    output: z
      .enum(['summary', 'text', 'blocks', 'tables', 'image'])
      .default('summary'),
    region: z
      .object({
        x: z.number().min(0).max(1000),
        y: z.number().min(0).max(1000),
        width: z.number().positive().max(1000),
        height: z.number().positive().max(1000),
      })
      .optional(),
    start: z.number().int().min(0).optional(),
    maxChars: z.number().int().min(1).max(20000).optional(),
    imageIndex: z.number().int().min(0).max(100).optional(),
    refresh: z.boolean().optional(),
  },
  'prepare_page',
);
tool(
  'page_notes_write_notes',
  '替换单页或批量笔记；可用 expectedRevision 检测冲突。逐页保存后静默刷新，不切换用户当前页面。生成讲解时完成一页即调用本工具保存一页，不等整份文档完成；检查用后台截图，不调用 navigate。修改前自动保存旧笔记到历史目录。',
  {
    documentId,
    updates: z
      .array(
        z.object({
          page,
          markdown: z.string().max(10000000),
          expectedRevision: z.string().optional(),
        }),
      )
      .min(1)
      .max(40),
  },
  'write_notes',
);
tool(
  'page_notes_patch_note',
  '追加或替换指定页笔记。传 search/replacement；可用批注 anchor.start 精确定位重复文字。追加时传 append。',
  {
    documentId,
    page,
    append: z.string().optional(),
    search: z.string().optional(),
    replacement: z.string().optional(),
    start: z.number().int().min(0).optional(),
    replaceAll: z.boolean().optional(),
    expectedRevision: z.string().optional(),
  },
  'patch_note',
);
tool(
  'page_notes_get_layout',
  '读取纸宽、边距、字号、行距、左右比例和自定义 CSS。',
  {},
  'get_layout',
  true,
);
tool(
  'page_notes_set_layout',
  '修改并保存界面布局。customCSS 可调整笔记、图表等视觉样式；不执行 JavaScript。',
  {
    noteWidth: z.number().min(320).max(1400).optional(),
    padding: z.number().min(12).max(100).optional(),
    fontSize: z.number().min(10).max(32).optional(),
    lineHeight: z.number().min(1.2).max(3).optional(),
    leftPercent: z.number().min(25).max(75).optional(),
    customCSS: z.string().max(100000).optional(),
    noteAnimation: z.boolean().optional(),
    expandEditor: z.boolean().optional(),
  },
  'set_layout',
);
tool(
  'page_notes_import_document',
  '从本机绝对路径导入 PDF、Word、PPT 或 Markdown，可选择是否在当前窗口打开。',
  {
    filePath: z.string(),
    activate: z.boolean().optional(),
    page: page.optional(),
  },
  'import_document',
);
tool(
  'page_notes_create_markdown',
  '创建新的 Markdown 源文档，使用独立一行 --- 划分页面单位。',
  {
    name: z.string(),
    markdown: z.string().max(10000000),
    activate: z.boolean().optional(),
  },
  'create_markdown',
);
tool(
  'page_notes_attach_image',
  '向指定页追加本地图片，传 filePath 或 base64；name 必须有 png/jpg 等图片扩展名。',
  {
    documentId,
    page,
    name: z.string().optional(),
    filePath: z.string().optional(),
    base64: z.string().max(30000000).optional(),
  },
  'attach_image',
);
tool(
  'page_notes_screenshot',
  '返回 PNG 图片和保存路径。scope 可选 workspace/document/note；word 截取当前 Word 原生编辑器（需先 read_word，不指定页码）。fullNote 截取长笔记，超长时用 nextOffset 继续。',
  {
    documentId,
    page: page.optional(),
    scope: z.enum(['workspace', 'document', 'note', 'word']).optional(),
    fullNote: z.boolean().optional(),
    maxHeight: z.number().int().min(400).max(16000).optional(),
    startOffset: z.number().int().min(0).optional(),
  },
  'screenshot',
  true,
);
tool(
  'page_notes_contact_sheet',
  '将指定页截图合并为带页码的 PNG 拼图，直接返回图像供多模态模型查看。',
  {
    documentId,
    pages: z.array(page).min(1).max(12),
    columns: z.number().int().min(1).max(4).optional(),
    scope: z.enum(['workspace', 'document', 'note']).optional(),
  },
  'contact_sheet',
  true,
);
server.registerResource(
  'current-state',
  'page-notes://state',
  {},
  async (uri) => {
    const result = await call('get_state', { detail: 'compact' });
    return {
      contents: [
        {
          uri: uri.href,
          mimeType: 'application/json',
          text: result.content[0].text,
        },
      ],
    };
  },
);
const promptStore = promptStoreModule.createPromptStore(
  path.join(path.dirname(connectionFile), 'skills'),
  path.join(path.dirname(connectionFile), 'documents-data'),
);
const readTutorSkill = () => promptStore.read('page-tutor').text;
server.registerResource(
  'prompt-index',
  'page-notes://prompts/index',
  { title: '提示词索引 / Prompt index', mimeType: 'application/json' },
  async (uri) => ({
    contents: [
      {
        uri: uri.href,
        mimeType: 'application/json',
        text: JSON.stringify(promptStore.index()),
      },
    ],
  }),
);
server.registerTool(
  'page_notes_read_prompt',
  {
    description:
      '按索引模块id读取当前提示词；knownRevision相同时省略正文。每次读取检查磁盘更新，无需重新连接。',
    inputSchema: {
      id: z.enum([
        'entry',
        'generation',
        'learning',
        'page-tutor',
        'output',
        'learner',
        'profile',
        'tag-edit',
      ]),
      knownRevision: z.string().max(64).optional(),
    },
  },
  async ({ id, knownRevision }) => {
    const { defaultText, ...value } = promptStore.read(id, knownRevision);
    return { content: [{ type: 'text', text: JSON.stringify(value) }] };
  },
);
tool(
  'page_notes_validate_note',
  '提交前本地预检Markdown公式、Mermaid、代码围栏和附件，不保存、不导航；返回规范化正文和错误。',
  { markdown: z.string().max(200000) },
  'validate_note',
  true,
);
server.registerResource(
  'page-tutor-skill',
  'page-notes://skills/page-tutor',
  {
    title: '逐页精讲 / Page-by-page tutor',
    description:
      '内置学习技能：陈述式、逐页成果、递进讲解与多模态教学；Agent自主调度。',
    mimeType: 'text/markdown',
  },
  async (uri) => ({
    contents: [
      {
        uri: uri.href,
        mimeType: 'text/markdown',
        text: await readTutorSkill(),
      },
    ],
  }),
);
server.registerPrompt(
  'pagenote-page-tutor',
  {
    title: '逐页精讲 / Page-by-page tutor',
    description:
      '使用内置学习技能解释当前文档，逐页递进并承接前后页；会话与子代理由上层Agent自主调度。',
    argsSchema: {
      learner: z
        .string()
        .max(4000)
        .optional()
        .describe('学习者已有基础 / Learner background'),
      pages: z
        .string()
        .max(1000)
        .optional()
        .describe('实际页码范围；未指定时先确认任务范围 / Page range'),
      goal: z
        .string()
        .max(4000)
        .optional()
        .describe('学习目标或额外要求 / Learning goal'),
    },
  },
  async (args) => ({
    description: '逐页精讲：先读取当前文档，再执行页级学习工作流。',
    messages: [
      {
        role: 'user',
        content: {
          type: 'text',
          text:
            (await readTutorSkill()) +
            '\n\n## 本次任务\n' +
            JSON.stringify(
              {
                learner: args.learner || '依据用户已给出的学习背景',
                pages: args.pages || '依据当前用户请求确定，不擅自扩大范围',
                goal:
                  args.goal ||
                  '陈述式逐页递进精讲，结合合适的图像、流程图、代码和公式',
              },
              null,
              2,
            ),
        },
      },
    ],
  }),
);
server.connect(new StdioServerTransport()).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

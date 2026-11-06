const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');
const { moveFile } = require('./file-ops.cjs');
const modules = [
  {
    id: 'entry',
    folder: 'pagenote-entry',
    title: '入口与路由',
    when: '任务入口、场景选择、数据与工具位置',
  },
  {
    id: 'generation',
    folder: 'pagenote-generation',
    title: '初次生成',
    when: '初次生成或补讲指定页的执行策略',
  },
  {
    id: 'profile',
    folder: 'pagenote-profile',
    title: '用户偏好',
    when: '生成与修改共享的可编辑偏好和候选记录',
  },
  {
    id: 'tag-edit',
    folder: 'pagenote-tag-edit',
    title: 'AI Tag 修改',
    when: '按AI Tag、批注或圈选要求修改已有内容',
  },
  {
    id: 'learning',
    folder: 'pagenote-learning',
    title: '学习讲解',
    when: '解释、教学或重讲文档知识',
  },
  {
    id: 'representation',
    folder: 'pagenote-representation',
    title: '表达与交互',
    when: '所有解释、补讲和知识性修改共享的表达选择，不依赖学科',
  },
  {
    id: 'page-tutor',
    folder: 'pagenote-page-tutor',
    title: '逐页精讲',
    when: '逐页教学的知识递进与多模态表达，不规定 Agent 会话调度',
  },
  {
    id: 'output',
    folder: 'pagenote-output',
    title: '输出格式',
    when: '生成或修改含公式、代码、图表、图片的笔记',
  },
  {
    id: 'learner',
    folder: 'pagenote-learner',
    title: '画像使用与更新规则',
    when: '需要采用用户明确提供的基础、目标和偏好',
  },
];
const entry = `从本索引进入，分别判断 workflow 与 intent，按 routes 和 intentRoutes 合并模块。生成、按意见修改和临时问答中的解释共用 learning、representation、output、learner、profile，不降低修改任务的讲解标准；纯纠错不扩写知识。按 capabilities 发现实际表达能力与工具，不虚构未实现的原生模板。尊重上层 Agent 的原生调度与上下文管理，不规定会话数量或材料必须一次装入；逐页是成果约定。模块按 revision 复用，可分批读取。用户当前要求优先。入口正文可在 AI 设置中修改。`;
const qualityModules = [
  'learning',
  'representation',
  'output',
  'learner',
  'profile',
];
const intentRoutes = [
  {
    intent: 'explain',
    workflows: ['generation', 'revision', 'direct'],
    modules: qualityModules,
  },
  { intent: 'summarize', modules: ['output'] },
  { intent: 'translate', modules: ['output'] },
  { intent: 'edit', modules: ['output', 'learner', 'profile'] },
  { intent: 'annotate', modules: ['output'] },
];
const routes = [
  {
    task: '初始生成或补充指定页讲解',
    scenario: 'generation',
    modules: [
      'generation',
      'learning',
      'representation',
      'page-tutor',
      'output',
      'learner',
      'profile',
    ],
    tool: 'page_notes_prepare_task',
    first: '读取用户范围与当前/相邻源页；不要先读取全部历史讲稿',
  },
  {
    task: '按AI Tag或批注修改',
    scenario: 'revision',
    modules: ['tag-edit', ...qualityModules],
    tool: 'page_notes_prepare_task',
    first: '定位意见与已有内容；涉及理解时必读共享教学和表达模块，纯纠错不扩写',
  },
  {
    task: '临时解释、举例、演示或重讲',
    workflow: 'direct',
    intent: 'explain',
    modules: qualityModules,
    first: '按用户范围解释；与生成和知识性修改共用质量标准',
  },
  {
    task: '普通文稿修改或格式修复',
    modules: ['output'],
    first: '定位用户指定段落并读取现有内容；不自动重生成整章',
  },
  {
    task: '摘要、翻译、查阅或其他任务',
    modules: [],
    first: '直接依照本次用户要求，按需使用文档工具',
  },
];
function resolvePromptRoute(workflow, intent = 'explain') {
  if (!['generation', 'revision', 'direct'].includes(workflow))
    throw Error('Invalid workflow');
  const expression = intentRoutes.find((route) => route.intent === intent);
  if (!expression) throw Error('Invalid expression intent');
  const execution =
    workflow === 'generation'
      ? ['generation', 'page-tutor']
      : workflow === 'revision'
        ? ['tag-edit']
        : [];
  return {
    workflow,
    intent,
    modules: [...new Set([...execution, ...expression.modules])],
  };
}
function createPromptStore(skillsRoot, data) {
  let writes = Promise.resolve();
  const file = path.join(data, 'ai-prompts.json');
  const overrides = () => {
    try {
      return JSON.parse(fs.readFileSync(file, 'utf8'));
    } catch (e) {
      if (e.code === 'ENOENT') return {};
      throw e;
    }
  };
  function read(id, knownRevision) {
    const item = modules.find((m) => m.id === id);
    if (!item) {
      const e = Error('Unknown prompt module');
      e.status = 404;
      throw e;
    }
    const defaultText = fs.readFileSync(
      path.join(skillsRoot, item.folder, 'SKILL.md'),
      'utf8',
    );
    const text = overrides()[id] ?? defaultText;
    const revision = createHash('sha256').update(text).digest('hex');
    return {
      ...item,
      revision,
      unchanged: knownRevision === revision,
      ...(knownRevision === revision ? {} : { text }),
      defaultText,
    };
  }
  const capabilities = {
    representation: {
      module: 'representation',
      platform: 'desktop',
      purpose: '依据理解障碍选择最简有效表达；所有解释路径共享',
      static: ['Markdown', 'LaTeX', 'Mermaid', 'local-images', 'code'],
      native: {
        sourceLimitBytes: 200 * 1024,
        resources: '内置离线渲染器；数据围栏不执行用户HTML/JS，不引用CDN或远程脚本',
        syntax: ['markdowns', 'mds-native'],
        version: 1,
        kinds: ['genetic-lab'],
        execution:
          'PageNote 原生渲染；交互状态自动保存至同一 Markdown；不是用户点击运行 HTML',
        contract:
          '读取 output 模块；JSON 数据包含唯一 id 和12个二进制 genes。不允许任意 HTML、脚本或未实现 kind。',
        scope:
          '目前仅有遗传算法原生模板；不将其强套其他知识，未支持的交互改用静态图表、代码或计算绘图。',
        platformLimit:
          '仅本开发桌面版本；Android 不宣称执行，旧版保留源码围栏。',
      },
      legacy: {
        policy:
          'mds-html/svg/canvas/three/chart 为历史沙盒兼容；非默认表达和原生能力，不为主动展示而新增整页HTML。',
      },
      validation: 'page_notes_validate_note：静态格式与真实运行分别报告',
    },
    task: {
      tool: 'page_notes_prepare_task',
      input:
        'documentId, page, scenario, intent?, reviewIds?, instruction?, neighbors?',
      returns:
        '逐页预处理文字、OCR警告、原图入口、笔记revision和对应意见；正文分页返回续读位置',
    },
    source: {
      tool: 'page_notes_prepare_page',
      outputs: ['summary', 'text', 'blocks', 'tables', 'image'],
      cache:
        '本地按源版本缓存；manifestPath与续读位置随返回提供，Agent无需自行定位文件夹',
    },
    notes: {
      tool: 'page_notes_read_pages',
      include: 'note',
      continuation: 'noteRange.start/maxChars',
    },
    attachments: {
      tool: 'page_notes_attach_image',
      purpose: '将实际生成的图片保存为本地项目附件，再引用工具返回地址',
    },
    sourceEdit: {
      tools: ['page_notes_read_word', 'page_notes_patch_word'],
      purpose:
        'Word 原文先读取实际 revision 再修改；PDF/PPT 原文使用默认应用，不把笔记修改冒充原文编辑',
    },
    reviews: {
      tool: 'page_notes_read_ai_reviews',
      filter: 'documentId, status；任务工具还可按编号筛选',
    },
    visuals: {
      tools: [
        'page_notes_prepare_page',
        'page_notes_screenshot',
        'page_notes_contact_sheet',
      ],
      purpose: '本地图片直接返回多模态内容，不要求AI转录或重截图已有图像',
    },
    save: {
      tools: [
        'page_notes_validate_note',
        'page_notes_write_notes',
        'page_notes_patch_note',
      ],
      rule: '检查后带expectedRevision静默提交；Tag成功修改后才标applied',
    },
    profile: {
      read: 'page_notes_read_prompt(id=profile)',
      update: 'page_notes_update_profile',
      edit: 'AI设置 → 用户偏好',
    },
    external:
      '生图、代码执行、检索与子代理调度使用上层Agent自身能力；本软件不提供模型推理服务',
  };
  function index() {
    const items = modules.map((m) => {
      const v = read(m.id);
      return { ...m, revision: v.revision };
    });
    return {
      entry: read('entry').text,
      bootstrap: entry,
      routes,
      intentRoutes,
      qualityModules,
      capabilities,
      schemaVersion: 3,
      locations: {
        index: 'page-notes://prompts/index',
        prompts: file,
        prepared:
          '项目 src/prepared 或本机 documents/<id>/prepared；以 manifestPath 为准',
        reviews: '项目 src/ai-reviews.json；通过工具读取最新数据',
      },
      revision: createHash('sha256')
        .update(
          entry +
            JSON.stringify(routes) +
            JSON.stringify(intentRoutes) +
            JSON.stringify(items) +
            JSON.stringify(capabilities),
        )
        .digest('hex'),
      modules: items,
    };
  }
  async function write(id, text, expectedRevision) {
    const current = read(id);
    if (expectedRevision && current.revision !== expectedRevision) {
      const e = Error('提示词已更新，请重新读取后保存');
      e.status = 409;
      throw e;
    }
    if (text !== null && (typeof text !== 'string' || text.length > 32768)) {
      const e = Error('Prompt must be text of at most 32768 characters');
      e.status = 400;
      throw e;
    }
    const saved = overrides();
    if (text === null) delete saved[id];
    else saved[id] = text;
    await fs.promises.mkdir(data, { recursive: true });
    const temp = file + '.' + require('node:crypto').randomUUID() + '.tmp';
    await fs.promises.writeFile(temp, JSON.stringify(saved, null, 2));
    await moveFile(temp, file);
    return read(id);
  }
  function save(id, text, expectedRevision) {
    const next = writes.then(() => write(id, text, expectedRevision));
    writes = next.catch(() => {});
    return next;
  }
  return { read, index, save };
}
module.exports = { createPromptStore, modules, entry, resolvePromptRoute };

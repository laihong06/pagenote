import fs from 'node:fs/promises';
const esc = (s) =>
  s.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
for (const lang of ['zh', 'en']) {
  const zh = lang === 'zh',
    t = (a, b) => (zh ? a : b);
  let svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1320" height="760" viewBox="0 0 1320 760" role="img" aria-labelledby="title desc"><title id="title">${t('Pagenote 提示词与数据路由', 'Pagenote prompt and data routing')}</title><desc id="desc">${t('入口选择生成或修改，本地准备材料，通用教学与用户偏好支持上层 Agent 自主执行，保存后更新候选偏好。', 'An editable entry routes generation and revisions to local prepared data. Shared teaching rules and preferences support host-Agent execution and revision-protected saves.')}</desc><defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0L10 5L0 10" fill="#8799ae"/></marker></defs><style>text{font-family:Segoe UI,Microsoft YaHei,PingFang SC,sans-serif;fill:#24364b}.heading{font-size:30px;font-weight:700}.sub{font-size:15px;fill:#65788d}.label{font-size:18px;font-weight:650}.body{font-size:14px;fill:#52687e}.tiny{font-size:12px;fill:#6c7f94}.edge{fill:none;stroke:#8799ae;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;marker-end:url(#arrow)}</style><rect width="1320" height="760" rx="20" fill="#f6f8fc"/><text x="44" y="55" class="heading">${t('从学习需求，到逐页理解', 'From a learning task to page-linked understanding')}</text><text x="44" y="86" class="sub">${t('软件准备数据与教学约定 · 上层 Agent 自主规划、并发与管理上下文', 'Local data and teaching guidance · Host Agent owns planning, delegation and context')}</text>`;
  const card = (x, y, w, h, color, title, lines) => {
    svg += `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="14" fill="white" stroke="#dce4ef"/><rect x="${x}" y="${y + 16}" width="4" height="26" rx="2" fill="${color}"/><text x="${x + 20}" y="${y + 35}" class="label" style="font-size:${w < 200 ? 14 : 18}px">${esc(title)}</text>`;
    lines.forEach(
      (s, i) =>
        (svg += `<text x="${x + 20}" y="${y + 65 + i * 24}" class="body" style="font-size:${w < 200 ? 12 : 14}px">${esc(s)}</text>`),
    );
  };
  const edge = (d) => (svg += `<path d="${d}" class="edge"/>`);
  card(44, 172, 240, 154, '#557fbc', t('01 统一入口', '01 Editable entry'), [
    t('识别任务、页码与修改范围', 'Task, page and target scope'),
    t('模块版本 · MCP 能力与数据位置', 'Revisions, tools and data map'),
    t('page-notes://prompts/index', 'page-notes://prompts/index'),
  ]);
  card(
    342,
    125,
    240,
    122,
    '#557fbc',
    t('初次生成 / 补讲', 'Generate / extend'),
    [
      t('generation → 通用教学', 'generation → shared teaching'),
      t('逐页充分解释、自然承接', 'Thorough, coherent page notes'),
    ],
  );
  card(342, 285, 240, 122, '#c18b46', t('按意见修改', 'Revise from feedback'), [
    t('tag-edit → 保留未涉及内容', 'tag-edit → preserve other text'),
    t('Tag 或直接文字修改要求', 'Tags or direct instructions'),
  ]);
  card(
    640,
    172,
    272,
    204,
    '#368d88',
    t('02 本地材料准备', '02 Local preparation'),
    [
      t('原文提取 · OCR · 图像缓存', 'Source text, OCR, visual cache'),
      t('生成：当前页 + 相邻源页', 'Generate: current + neighbors'),
      t('修改：原文 + 笔记 + 意见', 'Revise: source + notes + feedback'),
      t('版本标识、警告与按需续读', 'Revisions, warnings, continuations'),
      'page_notes_prepare_task',
    ],
  );
  card(
    970,
    172,
    306,
    204,
    '#8b74b0',
    t('03 上层 Agent 执行', '03 Host Agent executes'),
    [
      t('自主安排会话、子代理与上下文', 'Owns sessions, agents and context'),
      t('按需使用准备材料与教学技能', 'Uses prepared data and skills'),
      t('生图 · 代码 · 公式 · 流程图', 'Images, code, math and diagrams'),
      t('逐页是成果约定，不限制调用次数', 'Page-linked output, flexible calls'),
    ],
  );
  edge('M284 226 H312 V186 H342');
  edge('M312 226 V346 H342');
  edge('M582 186 H610 V232 H640');
  edge('M582 346 H610 V302 H640');
  edge('M912 270 H970');
  svg += `<rect x="342" y="460" width="570" height="164" rx="14" fill="#edf2f9"/><text x="362" y="490" class="label">${t('共享教学层 · 软件内可编辑、可恢复默认', 'Shared guidance · editable and resettable in the app')}</text>`;
  card(358, 507, 170, 98, '#557fbc', t('教学技能', 'Teaching skills'), [
    'learning / page-tutor',
    t('递进解释与多模态表达', 'Explain + illustrate'),
  ]);
  card(542, 507, 164, 98, '#368d88', t('输出约定', 'Output rules'), [
    'output',
    t('Markdown 与附件', 'Markdown + assets'),
  ]);
  card(720, 507, 176, 98, '#c18b46', t('用户画像', 'Learner preferences'), [
    'learner / profile',
    t('偏好、范围与反馈依据', 'Style + feedback'),
  ]);
  card(
    970,
    460,
    306,
    164,
    '#368d88',
    t('04 检查并静默保存', '04 Validate and save'),
    [
      t('格式预检 + revision 冲突检查', 'Syntax + revision conflict checks'),
      t('保存对应页，不打断阅读位置', 'Save without moving the reader'),
      t('成功后完成 Tag、提炼候选偏好', 'Resolve Tags; derive preferences'),
    ],
  );
  edge('M912 492 H940 V395 H1123 V376');
  edge('M1123 376 V460');
  edge('M970 590 H942 V660 H808 V605');
  svg += `<text x="850" y="685" text-anchor="middle" class="tiny">${t('修改成功后回流：带来源的候选偏好，可查看、修改或清空', 'After saved edits: evidence-linked preferences, editable or removable')}</text><path d="M44 713 H1276" stroke="#dce4ef"/><text x="44" y="740" class="tiny">${t('本次用户要求优先于历史偏好；模型、生图与子代理能力来自外部 Agent。', 'Current instructions override stored preferences. Models, image generation and agents are provided by the host.')}</text></svg>`;
  await fs.writeFile(`docs/images/prompt-routing-${lang}.svg`, svg + '\n');
}

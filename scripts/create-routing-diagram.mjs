import fs from 'node:fs/promises';
const esc = (s) =>
  s.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
for (const lang of ['zh', 'en']) {
  const t = (a, b) => (lang === 'zh' ? a : b);
  let s = `<svg xmlns="http://www.w3.org/2000/svg" width="1240" height="1730" viewBox="0 0 1240 1730" role="img" aria-labelledby="title desc"><title id="title">${t('Pagenote 提示词路由与教学工作流', 'Pagenote prompt routing and teaching workflow')}</title><desc id="desc">${t('生成与修改两条路径，经本地数据准备交给上层 Agent；教学技能、输出约定和用户画像提供共享指导，设置可以编辑各提示词。', 'Generation and revision paths join local preparation and host-Agent execution, supported by editable teaching, output and learner modules.')}</desc><defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M0 0L10 5L0 10" fill="#8297ac"/></marker></defs><style>text{font-family:Segoe UI,Microsoft YaHei,PingFang SC,sans-serif;fill:#175b92}.title{font-size:29px;font-weight:700}.label{font-size:19px;font-weight:650}.body{font-size:15px;fill:#486a86}.small{font-size:13px;fill:#6e8193}.edge{fill:none;stroke:#8297ac;stroke-width:1.7;stroke-linejoin:round;marker-end:url(#arrow)}.config{stroke:#aab8c5;stroke-dasharray:5 5;stroke-width:1.4}</style><rect width="1240" height="1730" fill="white"/><text x="50" y="48" class="title">${t('提示词路由与逐页教学', 'Prompt routing and page-linked teaching')}</text><text x="50" y="78" class="body">${t('原厂 Agent 自主调度会话、并发和上下文；软件提供技能与准备好的材料', 'The host Agent owns sessions, concurrency and context; the app supplies skills and prepared data')}</text><rect x="30" y="120" width="790" height="1510" rx="20" fill="#f7fafc"/><text x="50" y="152" class="small">${t('任务主流程', 'TASK FLOW')}</text><text x="870" y="152" class="small">${t('可编辑的共享指导', 'EDITABLE SHARED GUIDANCE')}</text>`;
  const edge = (d, config = false) =>
    (s += `<path d="${d}" class="edge${config ? ' config' : ''}"/>`);
  // Draw connectors first, keeping them behind cards.
  edge('M425 255V295');
  edge('M425 385V425H225V455');
  edge('M425 425H625V455');
  edge('M225 530V570');
  edge('M625 530V570');
  edge('M225 690V725H425V755');
  edge('M625 690V725H425');
  edge('M425 840V865H225V895');
  edge('M425 865H625V895');
  edge('M225 1005V1030H425V1065');
  edge('M625 1005V1030H425');
  edge('M425 1180V1220');
  edge('M425 1305V1345');
  edge('M425 1430V1470');
  edge('M600 1515H870');
  edge('M870 1550H835V1150H600');
  edge('M870 520H850V1100H600');
  edge('M870 800H865V1125H600');
  // A single settings spine replaces a tangle of dashed cross-page links.
  edge('M1190 230H1210V1545', true);
  for (const y of [340, 520, 800, 1545]) edge(`M1210 ${y}H1190`, true);
  const card = (x, y, w, h, title, lines = [], fill = '#e6f3ff') => {
    s += `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="15" fill="${fill}" stroke="#cfdde9" stroke-width="1.3"/>`;
    const gap = 23,
      top = y + (h - (lines.length * gap + 22)) / 2 + 17;
    s += `<text x="${x + w / 2}" y="${top}" text-anchor="middle" class="label">${esc(title)}</text>`;
    lines.forEach(
      (l, i) =>
        (s += `<text x="${x + w / 2}" y="${top + 29 + i * gap}" text-anchor="middle" class="body">${esc(l)}</text>`),
    );
  };
  card(250, 180, 350, 75, t('用户提出需求', 'User request'));
  card(250, 295, 350, 90, t('统一入口与路由索引', 'Entry and routing index'), [
    t('识别任务、范围与目标文档', 'Identify task, scope and document'),
    t(
      '模块版本 · MCP 工具与数据位置',
      'Module revisions · tools · data locations',
    ),
  ]);
  card(
    50,
    455,
    350,
    75,
    t('初次生成 / 补讲指定页', 'Generate / extend selected pages'),
  );
  card(
    450,
    455,
    350,
    75,
    t('按修改意见调整已有内容', 'Revise existing content from feedback'),
  );
  card(50, 570, 350, 120, t('生成策略', 'Generation strategy'), [
    t(
      '逐页成果 · 并发由 Agent 决定',
      'Page-linked output · Agent-owned concurrency',
    ),
    t('相邻源页衔接 · 逐页静默保存', 'Neighbor-page continuity · silent saves'),
  ]);
  card(450, 570, 350, 120, t('修改策略', 'Revision strategy'), [
    t(
      '读取 Tag 或直接要求与当前内容',
      'Read Tags / direct requests and current content',
    ),
    t(
      '定位修改范围，保留未涉及内容',
      'Locate the scope; preserve other content',
    ),
  ]);
  card(250, 755, 350, 85, t('本地数据准备层', 'Local data preparation'), [
    'page_notes_prepare_task',
  ]);
  card(50, 895, 350, 110, t('生成材料包', 'Generation material bundle'), [
    t(
      '当前源页 + 上一源页 + 下一源页',
      'Current + previous + next source page',
    ),
    t('缓存文字、OCR 与页面图像', 'Cached text, OCR and page visuals'),
  ]);
  card(450, 895, 350, 110, t('修改材料包', 'Revision material bundle'), [
    t('当前原文 + 原笔记 + 修改意见', 'Source + existing notes + feedback'),
    t('定位信息、版本与附件', 'Locations, revisions and attachments'),
  ]);
  card(
    250,
    1065,
    350,
    115,
    t('上层 AI / Agent 执行', 'Host AI / Agent executes'),
    [
      t('自主规划、分工与管理上下文', 'Plans, delegates and manages context'),
      t(
        '按需读取材料与调用教学能力',
        'Reads and uses teaching tools as needed',
      ),
    ],
  );
  card(250, 1220, 350, 85, t('本地提交检查', 'Local submission checks'), [
    t(
      '语法、附件引用、内容版本冲突',
      'Syntax, asset references, revision conflicts',
    ),
  ]);
  card(250, 1345, 350, 85, t('保存对应页', 'Save to the matching page'), [
    t('保持用户当前阅读位置', 'Preserve the current reading position'),
  ]);
  card(250, 1470, 350, 90, t('修改成功后', 'After successful revisions'), [
    t(
      '完成对应 Tag，提炼候选偏好',
      'Resolve Tags; derive candidate preferences',
    ),
  ]);
  card(870, 180, 320, 100, t('软件内 AI 设置', 'In-app AI settings'), [
    t('查看、编辑、保存、恢复默认', 'View, edit, save and restore defaults'),
  ]);
  card(870, 295, 320, 90, t('入口与场景提示词', 'Entry and scenario prompts'), [
    t('统一入口 · 生成策略 · 修改策略', 'Entry · generation · revision'),
  ]);
  card(870, 455, 320, 130, t('通用教学技能', 'Shared teaching skills'), [
    t('递进解释、示例、图像化理解', 'Progressive explanation and examples'),
    t('生图、代码块、公式、流程图', 'Images, code, formulas and diagrams'),
    t('依知识点需要选用表达方式', 'Choose formats that fit the subject'),
  ]);
  card(870, 755, 320, 100, t('输出约定', 'Output rules'), [
    t('Markdown、附件引用', 'Markdown and asset references'),
    t('公式与图表格式', 'Math and diagram syntax'),
  ]);
  card(870, 1470, 320, 130, t('全局用户画像', 'Global learner preferences'), [
    t(
      '学习基础、表达偏好、教学风格',
      'Background, preferences, teaching style',
    ),
    t(
      '候选偏好保留来源，可修改或清空',
      'Evidence-linked; editable or removable',
    ),
  ]);
  s += `<text x="1030" y="950" text-anchor="middle" class="small">${t('实线：材料与执行流程', 'Solid: data and execution')}</text><text x="1030" y="975" text-anchor="middle" class="small">${t('虚线：设置中可编辑的提示词', 'Dashed: prompts editable in settings')}</text><path d="M50 1655H1190" stroke="#dce5ed"/><text x="50" y="1688" class="small">${t('入口只负责找路；本次明确要求优先于历史偏好。逐页是成果单位，不是会话或工具调用次数限制。', 'The entry routes tasks. Current instructions override stored preferences. Pages are output units, not session or tool-call limits.')}</text></svg>`;
  await fs.writeFile(`docs/images/prompt-routing-${lang}.svg`, s);
}

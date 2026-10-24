/* Standalone UI reference. No production API, account or project directory access. */
(() => {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const key = 'pagenote-ui-reference-v1';
  const initial = {
    library: 'pagenote',
    theme: 'light',
    platform: 'tablet',
    view: 'grid',
    sort: 'updated',
    books: [
      {
        id: 'lecture',
        title: '模式识别 · 课堂笔记',
        type: 'pdf',
        color: 'sage',
        updated: 6,
        date: '今天 10:24',
        cover: 'assets/lecture-page.png',
      },
      {
        id: 'notebook',
        title: '课堂随记',
        type: 'md',
        color: 'sage',
        updated: 5,
        date: '今天 09:08',
      },
      {
        id: 'scratch',
        title: '推导与练习',
        type: 'md',
        color: 'rose',
        updated: 4,
        date: '昨天 21:16',
      },
      {
        id: 'pdf-example',
        title: 'PDF 阅读示例',
        type: 'pdf',
        color: 'sky',
        updated: 3,
        date: '10月22日',
        cover: 'assets/pdf-cover.png',
      },
      {
        id: 'reading',
        title: '阅读摘录',
        type: 'md',
        color: 'sky',
        updated: 2,
        date: '10月20日',
      },
      {
        id: 'ideas',
        title: '想法与问题',
        type: 'md',
        color: 'graphite',
        updated: 1,
        date: '10月19日',
      },
    ],
    notes: {},
    settings: {
      language: 'zh-CN',
      font: 16,
      prepare: 3,
      animation: true,
      outside: false,
    },
  };
  let saved;
  try {
    saved = JSON.parse(localStorage.getItem(key));
  } catch {
    /* Private browsing can disable storage. */
  }
  const state = {
    ...structuredClone(initial),
    ...saved,
    query: '',
    scene: 'shelf',
    book: 'lecture',
    page: 0,
    noteMode: 'preview',
    sidebar: false,
    tool: 'pen',
    ink: '#1683c4',
    penWidth: 2,
    shape: 'line',
    dirty: false,
    rain: false,
  };
  let referenceUrl = '',
    toastTimer;
  const pages = [
    {
      title: '贝叶斯决策',
      sub: '从观察到分类',
      body: String.raw`<p>给定观察到的特征 $x$，我们希望找到最可能的类别。贝叶斯公式把先验知识与观察证据联系起来。</p><h3>后验概率</h3><div>$$P(\omega_i \mid x)=\frac{p(x\mid\omega_i)P(\omega_i)}{p(x)}$$</div><ul><li>$P(\omega_i)$：类别的先验概率</li><li>$p(x\mid\omega_i)$：类条件概率密度</li><li>$P(\omega_i\mid x)$：观察到特征后的判断</li></ul><canvas class="distribution" width="720" height="300" aria-label="两个类别的概率密度示意图"></canvas>`,
    },
    {
      title: '最小错误率分类',
      sub: '比较后验概率',
      body: String.raw`<p>在错误代价相同的情况下，选择后验概率最大的类别。</p><div>$$\hat{\omega}(x)=\arg\max_i P(\omega_i\mid x)$$</div><h3>二分类的判别函数</h3><div>$$g(x)=\ln\frac{p(x\mid\omega_1)}{p(x\mid\omega_2)}+\ln\frac{P(\omega_1)}{P(\omega_2)}$$</div><p>当 $g(x)>0$ 时，选择 $\omega_1$；否则选择 $\omega_2$。</p><canvas class="distribution" width="720" height="300" aria-label="分类边界示意图"></canvas>`,
    },
    {
      title: '风险与决策',
      sub: '不相同的错误代价',
      body: String.raw`<p>当不同错误的代价并不相同时，最小错误率不再等同于最小风险。</p><h3>条件风险</h3><div>$$R(\alpha_i\mid x)=\sum_j\lambda(\alpha_i,\omega_j)P(\omega_j\mid x)$$</div><h3>最小风险决策</h3><div>$$\alpha^*(x)=\arg\min_i R(\alpha_i\mid x)$$</div><p>在医学筛查中，漏诊与误诊的代价可能不同。损失矩阵让这种差异进入决策规则。</p>`,
    },
  ];
  const notes = [
    String.raw`# 贝叶斯决策

## 核心思路

我们看到的是特征 $x$，要推断的是它属于哪个类别。

> 后验概率 = 似然 × 先验 / 证据。

$$P(\omega_i\mid x)=\frac{p(x\mid\omega_i)P(\omega_i)}{p(x)}$$

## 三个量的区别

| 符号 | 含义 |
| --- | --- |
| $P(\omega_i)$ | 观察前，对类别的认识 |
| $p(x\mid\omega_i)$ | 该类别生成特征的可能性 |
| $P(\omega_i\mid x)$ | 观察后，对类别的判断 |

### 记一个问题

先验概率变化时，分类边界会如何移动？`,
    '# 最小错误率分类\n\n同一个特征下，比较每一类的后验概率，取最大的那一类。\n\n## 注意\n\n这条规则隐含了各类错误代价相同的假设。',
    '# 风险与决策\n\n损失矩阵记录不同错误的代价。\n\n- 先计算每个动作的条件风险。\n- 再选择风险最小的动作。',
  ];
  const esc = (value) =>
    String(value ?? '').replace(
      /[&<>"']/g,
      (c) =>
        ({
          '&': '&amp;',
          '<': '&lt;',
          '>': '&gt;',
          '"': '&quot;',
          "'": '&#39;',
        })[c],
    );
  const icon = (name) => `<i data-lucide="${name}" aria-hidden="true"></i>`;
  const ib = (action, name, title, extra = '') =>
    `<button type="button" class="icon-button" data-action="${action}" aria-label="${title}" title="${title}" ${extra}>${icon(name)}</button>`;
  const btn = (action, title, name, cls = '') =>
    `<button type="button" class="text-button ${cls}" data-action="${action}">${name ? icon(name) : ''}${title}</button>`;
  const activeBook = () =>
    state.books.find((b) => b.id === state.book) || state.books[0];
  const pageCount = () => (activeBook()?.type === 'pdf' ? 3 : 1);
  const penTypes = [
    ['pen', '钢笔'],
    ['ballpoint', '圆珠笔'],
    ['highlight', '荧光笔'],
    ['laser', '激光笔'],
    ['eraser', '橡皮擦'],
  ];
  const inkColors = [
    ['#24343b', '墨黑'],
    ['#1683c4', '蓝色'],
    ['#e35259', '红色'],
    ['#43a67e', '绿色'],
    ['#f1c84b', '黄色'],
    ['#ffffff', '白色'],
  ];
  const noteKey = () => `${state.book}:${state.page}`;
  const noteText = () =>
    state.notes[noteKey()] ??
    (activeBook()?.type === 'pdf'
      ? notes[state.page]
      : '# ' + activeBook()?.title + '\n\n');
  function persist() {
    const { books, notes, library, theme, platform, view, sort, settings } =
      state;
    try {
      localStorage.setItem(
        key,
        JSON.stringify({
          books,
          notes,
          library,
          theme,
          platform,
          view,
          sort,
          settings,
        }),
      );
    } catch {
      notify('浏览器存储不可用，本次更改仍保留在当前页面。');
    }
  }
  function icons(root = document) {
    lucide.createIcons({ root, attrs: { 'stroke-width': 1.7 } });
  }
  function notify(message) {
    clearTimeout(toastTimer);
    $('toast').textContent = message;
    $('toast').hidden = false;
    toastTimer = setTimeout(() => {
      $('toast').hidden = true;
    }, 2400);
  }
  function theme() {
    document.documentElement.dataset.theme = state.theme;
    document.documentElement.dataset.platform = state.platform;
    document
      .querySelectorAll('[data-platform-choice]')
      .forEach((b) =>
        b.setAttribute(
          'aria-pressed',
          String(b.dataset.platformChoice === state.platform),
        ),
      );
    $('theme-toggle').innerHTML = icon(state.theme === 'dark' ? 'sun' : 'moon');
    icons();
  }
  function math(root) {
    renderMathInElement(root, {
      delimiters: [
        { left: '$$', right: '$$', display: true },
        { left: '$', right: '$', display: false },
      ],
      throwOnError: false,
      strict: 'ignore',
      trust: false,
    });
  }
  function cover(book) {
    const image =
      book.customCover || (book.coverMode !== 'paper' && book.cover);
    return image
      ? `<img src="${esc(image)}" alt="${esc(book.title)}封面" />`
      : '<strong>Note</strong><small>pagenote</small>';
  }
  function renderShelfBooks() {
    const visible = state.books
      .filter((b) => b.title.toLowerCase().includes(state.query.toLowerCase()))
      .sort((a, b) =>
        state.sort === 'name'
          ? a.title.localeCompare(b.title, 'zh-CN')
          : b.updated - a.updated,
      );
    $('book-count').textContent = `${visible.length} 本笔记`;
    $('books').className = `shelf-grid ${state.view === 'list' ? 'list' : ''}`;
    $('books').innerHTML =
      `<article class="book new-book"><button class="cover blank" data-action="new" aria-label="新建项目">${icon('plus')}</button><div class="shelf-board"></div><div class="book-caption"><button class="book-title" data-action="new">新建项目</button></div></article>` +
      visible
        .map(
          (book) =>
            `<article class="book" data-book="${esc(book.id)}"><button class="cover ${book.customCover || (book.cover && book.coverMode !== 'paper') ? '' : 'paper'}" data-paper="${esc(book.color)}" data-action="open-book" data-id="${esc(book.id)}" aria-label="打开 ${esc(book.title)}">${cover(book)}</button><div class="shelf-board"></div><div class="book-caption"><button class="book-title" data-action="open-book" data-id="${esc(book.id)}">${esc(book.title)}</button>${ib('customize', 'ellipsis-vertical', '自定义封面', `data-id="${esc(book.id)}"`)}<small>${esc(book.date)} · ${book.type === 'pdf' ? 'PDF' : 'Markdown'}</small></div></article>`,
        )
        .join('') +
      (!visible.length ? '<p class="empty-state">没有匹配的笔记本</p>' : '');
    icons();
  }
  function shelf() {
    $('app').innerHTML =
      `<section class="shelf"><header class="shelf-head"><h1>${icon('book-open')}书架</h1><label class="search-field">${icon('search')}<input id="book-search" type="search" placeholder="搜索笔记本" aria-label="搜索笔记本" value="${esc(state.query)}" /></label><div class="shelf-tools">${ib('view', state.view === 'grid' ? 'list' : 'layout-grid', '切换网格或列表')}${ib('refresh', 'refresh-cw', '刷新书架')}${ib('rain', 'cloud-download', '雨课堂')}${ib('settings', 'settings', '设置')}</div></header><div class="library-row"><button class="library-path" data-action="folder" title="更改书架目录">${icon('folder-open')}<span>${esc(state.library)}</span></button><span id="book-count"></span><select id="book-sort" aria-label="排序"><option value="updated">最近更新</option><option value="name">名称</option></select>${btn('new', '新建项目', 'plus')}${btn('import', '导入文档', 'download')}</div><div id="books" class="shelf-grid"></div></section>`;
    $('book-sort').value = state.sort;
    $('book-sort').onchange = (e) => {
      state.sort = e.target.value;
      persist();
      renderShelfBooks();
    };
    $('book-search').oninput = (e) => {
      state.query = e.target.value;
      renderShelfBooks();
    };
    renderShelfBooks();
  }
  function source() {
    if (activeBook()?.type !== 'pdf')
      return `<article class="source-paper"><div class="lecture-kicker"><span>MARKDOWN</span><span>left.md</span></div><h2>${esc(activeBook()?.title)}</h2><p>2026年10月25日</p><hr /><p>记录课堂中的想法与问题。</p></article>`;
    const page = pages[state.page];
    return `<article class="source-paper"><div class="lecture-kicker"><span>PATTERN RECOGNITION</span><span>02 · 概率与分类</span></div><h2>${page.title}</h2><h3>${page.sub}</h3>${page.body}<footer>pagenote 阅读示例 · ${state.page + 1}</footer></article><div class="source-caption">${icon('file-text')}PPT 1 · 原第 ${state.page + 1} 页</div>`;
  }
  function toolRail() {
    return `<aside class="writing-rail" aria-label="书写工具" style="--selected-ink:${state.ink}">
      <div class="pen-types" role="group" aria-label="笔的类型">
        ${penTypes
          .map(
            ([
              id,
              name,
            ]) => `<button class="pen-choice" data-action="tool" data-tool="${id}" title="${name}" aria-label="${name}" aria-pressed="${state.tool === id}" aria-controls="pen-options">
          <span class="pen-model ${id}" aria-hidden="true"><span class="pen-barrel"></span><span class="pen-grip"></span><span class="pen-nib"></span></span>
        </button>`,
          )
          .join('')}
      </div>
      <div class="rail-group">
        ${ib('tool', 'mouse-pointer-2', '选择', `data-tool="select" aria-pressed="${state.tool === 'select'}"`)}
        ${ib('shapes', 'shapes', '形状', `aria-pressed="${state.tool === 'shape'}" aria-controls="pen-options"`)}
        ${ib('pen-options', 'sliders-horizontal', '笔迹设置', 'aria-controls="pen-options"')}
      </div>
      <div class="rail-widths" role="group" aria-label="笔迹粗细">
        ${[1, 2, 4].map((width) => `<button data-action="pen-width" data-width="${width}" aria-label="${width}毫米" title="${width}毫米" aria-pressed="${state.penWidth === width}"><span style="--stroke:${width * 1.5}px"></span></button>`).join('')}
      </div>
      <div class="rail-colors" role="group" aria-label="笔迹颜色">
        ${inkColors.map(([color, name]) => `<button data-action="ink" data-color="${color}" style="--ink:${color}" aria-label="${name}" title="${name}" aria-pressed="${state.ink === color}">${icon('check')}</button>`).join('')}
        <label class="custom-ink" title="自定义颜色">${icon('plus')}<input id="custom-ink" type="color" value="${state.ink}" aria-label="自定义笔迹颜色" /></label>
      </div>
    </aside>`;
  }
  function updateTools() {
    document.querySelectorAll('[data-tool]').forEach((el) => {
      el.setAttribute('aria-pressed', String(el.dataset.tool === state.tool));
    });
    document
      .querySelector('[data-action="shapes"]')
      ?.setAttribute('aria-pressed', String(state.tool === 'shape'));
    document.querySelectorAll('[data-action="ink"]').forEach((el) => {
      el.setAttribute('aria-pressed', String(el.dataset.color === state.ink));
    });
    document.querySelectorAll('[data-action="pen-width"]').forEach((el) => {
      el.setAttribute(
        'aria-pressed',
        String(Number(el.dataset.width) === state.penWidth),
      );
    });
    document
      .querySelector('.writing-rail')
      ?.style.setProperty('--selected-ink', state.ink);
    if ($('custom-ink')) $('custom-ink').value = state.ink;
    if ($('pen-width-value'))
      $('pen-width-value').textContent = state.penWidth.toFixed(1);
    if ($('pen-width')) $('pen-width').value = state.penWidth;
    drawStrokePreview();
  }
  function penOptions() {
    const panel = $('pen-options');
    const title =
      penTypes.find(([id]) => id === state.tool)?.[1] ||
      (state.tool === 'shape' ? '形状' : '笔迹');
    panel.innerHTML = `<header><h2>${title}</h2>${ib('close-pen-options', 'x', '关闭笔迹设置')}</header>
      <canvas id="stroke-preview" width="480" height="160" aria-label="当前笔迹预览"></canvas>
      ${
        state.tool === 'shape'
          ? `<div class="shape-options" role="group" aria-label="形状类型">${[
              ['line', 'minus', '直线'],
              ['arrow', 'move-up-right', '箭头'],
              ['rectangle', 'square', '矩形'],
              ['ellipse', 'circle', '椭圆'],
              ['triangle', 'triangle', '三角形'],
            ]
              .map(([shape, glyph, name]) =>
                ib(
                  'shape',
                  glyph,
                  name,
                  `data-shape="${shape}" aria-pressed="${state.shape === shape}"`,
                ),
              )
              .join('')}</div>`
          : ''
      }
      <label class="pen-width-field"><span>${state.tool === 'eraser' ? '橡皮大小' : '粗细'}</span><output id="pen-width-value">${state.penWidth.toFixed(1)}</output><span>mm</span><input id="pen-width" type="range" min="0.5" max="8" step="0.5" value="${state.penWidth}" /></label>
      <div class="pen-color-options" role="group" aria-label="笔迹设置颜色">${inkColors.map(([color, name]) => `<button data-action="ink" data-color="${color}" style="--ink:${color}" title="${name}" aria-label="${name}" aria-pressed="${state.ink === color}">${icon('check')}</button>`).join('')}</div>`;
    panel.hidden = false;
    $('pen-width').oninput = (e) => {
      state.penWidth = Number(e.target.value);
      updateTools();
    };
    document
      .querySelector('[data-action="pen-options"]')
      .setAttribute('aria-expanded', 'true');
    icons(panel);
    drawStrokePreview();
  }
  function closePenOptions() {
    if ($('pen-options')) $('pen-options').hidden = true;
    document
      .querySelector('[data-action="pen-options"]')
      ?.setAttribute('aria-expanded', 'false');
  }
  function drawStrokePreview() {
    const canvas = $('stroke-preview');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, 480, 160);
    ctx.strokeStyle = state.ink;
    ctx.fillStyle = state.ink;
    ctx.lineWidth = state.penWidth * (state.tool === 'highlight' ? 8 : 2);
    ctx.lineCap = state.tool === 'highlight' ? 'butt' : 'round';
    ctx.lineJoin = 'round';
    ctx.globalAlpha = state.tool === 'highlight' ? 0.45 : 1;
    ctx.shadowColor = state.ink;
    ctx.shadowBlur = state.tool === 'laser' ? 12 : 0;
    ctx.beginPath();
    if (state.tool === 'shape') {
      if (state.shape === 'ellipse')
        ctx.ellipse(240, 80, 140, 46, 0, 0, Math.PI * 2);
      else if (state.shape === 'rectangle') ctx.rect(80, 34, 320, 92);
      else if (state.shape === 'triangle') {
        ctx.moveTo(240, 26);
        ctx.lineTo(380, 132);
        ctx.lineTo(100, 132);
        ctx.closePath();
      } else {
        ctx.moveTo(70, 112);
        ctx.lineTo(410, 48);
        if (state.shape === 'arrow') {
          ctx.moveTo(380, 32);
          ctx.lineTo(410, 48);
          ctx.lineTo(390, 76);
        }
      }
    } else if (state.tool === 'eraser') {
      ctx.strokeStyle = '#b8c2c4';
      ctx.lineWidth = 3;
      ctx.setLineDash([9, 8]);
      ctx.ellipse(
        240,
        80,
        28 + state.penWidth * 4,
        28 + state.penWidth * 4,
        0,
        0,
        Math.PI * 2,
      );
    } else {
      ctx.setLineDash([]);
      ctx.moveTo(50, 108);
      ctx.bezierCurveTo(120, 112, 125, 40, 195, 50);
      ctx.bezierCurveTo(260, 60, 215, 126, 290, 110);
      ctx.bezierCurveTo(340, 100, 360, 52, 430, 58);
    }
    ctx.stroke();
    ctx.setLineDash([]);
  }
  function renderPagePreviews(query = '') {
    const isPdf = activeBook()?.type === 'pdf';
    const entries = Array.from({ length: pageCount() }, (_, index) => ({
      index,
      title: isPdf ? pages[index].title : activeBook().title,
    })).filter(
      ({ index, title }) =>
        String(index + 1) === query.trim() || title.includes(query.trim()),
    );
    $('page-preview-list').innerHTML =
      entries
        .map(
          ({
            index,
            title,
          }) => `<button class="page-preview" data-action="choose-page" data-page="${index}" aria-label="第 ${index + 1} 页：${esc(title)}" aria-current="${index === state.page ? 'page' : 'false'}">
      <span class="page-miniature">${isPdf ? `<img src="assets/lecture-page${index ? '-' + (index + 1) : ''}.png" alt="" />` : `<span class="markdown-miniature"><strong>${esc(title)}</strong><span>记录课堂中的想法与问题。</span></span>`}</span>
      <span class="preview-caption"><strong>${index + 1}</strong><span>${esc(title)}</span>${index === state.page ? icon('check') : ''}</span>
    </button>`,
        )
        .join('') || '<p class="empty-state">没有匹配的页面</p>';
    icons($('page-preview-list'));
  }
  function openPagePreviews() {
    closePenOptions();
    const drawer = $('page-drawer');
    $('page-preview-search').value = '';
    renderPagePreviews();
    drawer.style.setProperty(
      '--drawer-top',
      Math.max(
        0,
        document.querySelector('.reader').getBoundingClientRect().top,
      ) + 'px',
    );
    $('page-jump').setAttribute('aria-expanded', 'true');
    drawer.showModal();
    drawer
      .querySelector('[aria-current="page"]')
      ?.scrollIntoView({ block: 'nearest' });
    drawer
      .querySelector('[aria-current="page"]')
      ?.focus({ preventScroll: true });
  }
  function reader() {
    const book = activeBook();
    const menu = (action, label, glyph) => btn(action, label, glyph);
    const count = pageCount();
    $('app').innerHTML = `<section class="reader">
        <aside class="reader-sidebar" ${!state.sidebar ? 'hidden' : ''}>
          <header class="sidebar-heading"><strong>菜单</strong>${ib('sidebar', 'chevron-left', '收起菜单')}</header>
          <section class="menu-group"><h3>文件</h3>
            ${menu('shelf', '返回书架', 'library')}
            ${menu('rain', '雨课堂导入与实时采集', 'cloud-download')}
            ${menu('new', '新建项目', 'file-plus-2')}
            ${menu('import', '打开文档', 'folder-open')}
            ${menu('attachments', '附件', 'paperclip')}
            ${menu('export', '导出笔记', 'download')}
            ${menu('history', '历史', 'history')}
          </section>
          <section class="menu-group"><h3>AI 批注</h3>
            ${menu('explain', 'AI 讲解', 'sparkles')}
            ${menu('annotation', '批注', 'message-square')}
            ${menu('ocr', '区域 OCR', 'scan-text')}
          </section>
          <section class="menu-group"><h3>设置</h3>${menu('settings', '阅读器设置', 'settings')}</section>
        </aside>
        <div class="reader-main">
          <header class="reader-top">
            ${ib('sidebar', 'panel-left', '展开或收起菜单')}${ib('shelf', 'library', '返回书架')}
            <h1 class="reader-name">${esc(book?.title || '课堂笔记')}</h1>
            <nav class="page-navigation" aria-label="翻页">
              ${ib('prev', 'arrow-left', '上一页', state.page === 0 ? 'disabled' : '')}
              <button id="page-jump" class="page-picker" data-action="page-previews" aria-label="页面预览，当前第 ${state.page + 1} 页，共 ${count} 页" aria-haspopup="dialog" aria-controls="page-drawer" aria-expanded="false" title="页面预览"><strong>${state.page + 1}</strong><span>/ ${count}</span>${icon('chevron-down')}</button>
              ${ib('next', 'arrow-right', '下一页', state.page === count - 1 ? 'disabled' : '')}
            </nav>
          </header>
          <nav class="desktop-menu">${btn('new', '文件', null)}${btn('edit-mode', '编辑', null)}${btn('view', '视图', null)}${btn('annotation', 'AI 批注', null)}${btn('settings', '设置', null)}</nav>
          <div class="reader-content">
            ${toolRail()}
            <section id="pen-options" class="pen-options" aria-label="笔迹设置" hidden></section>
            <div class="reader-body" id="reader-body">
            <section class="document-pane">
              <header class="pane-head"><strong>${book?.type === 'pdf' ? 'document.pdf' : 'left.md'} · ${state.page + 1}</strong>
                <div class="segments">${ib('zoom-out', 'zoom-out', '缩小')}${ib('zoom-in', 'zoom-in', '放大')}${ib('fit', 'maximize', '适合页面')}</div>
              </header>
              <div class="document-scroll" id="document-scroll">${source()}</div>
            </section>
            <div class="reader-splitter" id="splitter" role="separator" tabindex="0" aria-label="调整文档和笔记比例" aria-orientation="${innerWidth <= 760 ? 'horizontal' : 'vertical'}" aria-valuemin="25" aria-valuemax="75" aria-valuenow="50"></div>
            <section class="note-pane" style="--note-font:${state.settings.font}px">
              <header class="pane-head"><strong>第 ${state.page + 1} 页笔记</strong>
                <div class="segments">${ib('reload-note', 'rotate-ccw', '重载笔记')}${ib('history', 'history', '笔记历史')}${btn('save-note', '保存', 'save')}</div>
              </header>
              <div class="note-tabs" role="tablist" aria-label="笔记模式">
                <button role="tab" aria-selected="${state.noteMode === 'edit'}" data-action="edit-mode">编辑</button>
                <button role="tab" aria-selected="${state.noteMode === 'preview'}" data-action="preview-mode">预览</button>
              </div>
              <div class="note-format">
                ${ib('format-bold', 'bold', '加粗')}${ib('format-italic', 'italic', '斜体')}${ib('format-heading', 'heading-2', '标题')}${ib('format-list', 'list', '列表')}${ib('format-formula', 'sigma', '公式')}
                <span class="spacer"></span>${ib('explain', 'sparkles', 'AI 讲解')}
              </div>
              <div class="note-content">
                <textarea id="note-editor" aria-label="笔记内容" spellcheck="false" ${state.noteMode === 'preview' ? 'hidden' : ''}>${esc(noteText())}</textarea>
                <article id="note-preview" class="note-preview" ${state.noteMode === 'edit' ? 'hidden' : ''}></article>
              </div>
              <div id="note-status" class="note-status">${state.dirty ? '尚未保存' : '已保存到浏览器样板'}</div>
            </section>
            </div>
          </div>
          <footer class="reader-foot">
            ${ib('ocr', 'scan-text', '区域 OCR')}${ib('annotation', 'message-square', '批注')}
            <output id="zoom-value">100%</output>
          </footer>
        </div>
        <dialog id="page-drawer" class="page-drawer" aria-labelledby="page-drawer-title">
          <header><div><h2 id="page-drawer-title">页面预览</h2><small>${count} 页</small></div>${ib('close-page-previews', 'x', '关闭页面预览')}</header>
          <label class="page-preview-search">${icon('search')}<input id="page-preview-search" type="search" aria-label="搜索页码或标题" placeholder="搜索页码或标题" /></label>
          <nav id="page-preview-list" aria-label="文档页面"></nav>
        </dialog>
      </section>`;
    const editor = $('note-editor');
    editor.oninput = () => {
      state.notes[noteKey()] = editor.value;
      state.dirty = true;
      $('note-status').textContent = '尚未保存';
    };
    $('custom-ink').oninput = (e) => {
      state.ink = e.target.value;
      updateTools();
    };
    $('page-preview-search').oninput = (e) =>
      renderPagePreviews(e.target.value);
    $('page-drawer').onclick = (e) => {
      const bounds = e.currentTarget.getBoundingClientRect();
      if (
        e.target === e.currentTarget &&
        (e.clientX < bounds.left ||
          e.clientX > bounds.right ||
          e.clientY < bounds.top ||
          e.clientY > bounds.bottom)
      )
        e.currentTarget.close();
    };
    $('page-drawer').onclose = () =>
      $('page-jump')?.setAttribute('aria-expanded', 'false');
    updateTools();
    renderNote();
    math($('document-scroll'));
    drawChart();
    attachSplitter();
  }
  function renderNote() {
    const root = $('note-preview');
    if (!root) return;
    root.innerHTML = DOMPurify.sanitize(marked.parse(noteText()));
    math(root);
  }
  function drawChart() {
    const canvas = document.querySelector('.distribution');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, 720, 300);
    ctx.lineWidth = 1;
    ctx.strokeStyle = '#dce4e1';
    for (let y = 45; y < 250; y += 50) {
      ctx.beginPath();
      ctx.moveTo(40, y);
      ctx.lineTo(680, y);
      ctx.stroke();
    }
    ctx.strokeStyle = '#85978e';
    ctx.beginPath();
    ctx.moveTo(40, 25);
    ctx.lineTo(40, 250);
    ctx.lineTo(680, 250);
    ctx.stroke();
    for (const [mean, color, label] of [
      [275, '#245e59', 'ω₁'],
      [460, '#b77945', 'ω₂'],
    ]) {
      ctx.beginPath();
      ctx.moveTo(40, 250);
      for (let x = 40; x <= 680; x += 3)
        ctx.lineTo(x, 250 - 190 * Math.exp(-((x - mean) ** 2) / 12000));
      ctx.strokeStyle = color;
      ctx.lineWidth = 3;
      ctx.stroke();
      ctx.lineTo(680, 250);
      ctx.closePath();
      ctx.fillStyle = color + '12';
      ctx.fill();
      ctx.fillStyle = color;
      ctx.font = '19px sans-serif';
      ctx.fillText(label, mean - 10, 40);
    }
    ctx.font = '15px sans-serif';
    ctx.fillStyle = '#687b74';
    ctx.fillText('x', 684, 255);
    ctx.fillText('p(x | ω)', 40, 18);
  }
  function attachSplitter() {
    const splitter = $('splitter'),
      body = $('reader-body');
    function resize(event) {
      const bounds = body.getBoundingClientRect(),
        vertical = matchMedia('(max-width: 760px)').matches;
      const ratio = Math.max(
        25,
        Math.min(
          75,
          Math.round(
            100 *
              (vertical
                ? (event.clientY - bounds.top) / bounds.height
                : (event.clientX - bounds.left) / bounds.width),
          ),
        ),
      );
      if (vertical)
        body.style.gridTemplateRows = `${ratio}% 8px minmax(110px, 1fr)`;
      else body.style.setProperty('--left-pane', ratio + '%');
      splitter.setAttribute('aria-valuenow', ratio);
    }
    splitter.onpointerdown = (event) => {
      splitter.setPointerCapture(event.pointerId);
      resize(event);
    };
    splitter.onpointermove = (event) => {
      if (splitter.hasPointerCapture(event.pointerId)) resize(event);
    };
    splitter.onpointerup = (event) => {
      if (splitter.hasPointerCapture(event.pointerId))
        splitter.releasePointerCapture(event.pointerId);
    };
    splitter.onkeydown = (event) => {
      if (
        !['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)
      )
        return;
      event.preventDefault();
      const delta = ['ArrowRight', 'ArrowDown'].includes(event.key) ? 5 : -5;
      const value = Math.max(
        25,
        Math.min(75, Number(splitter.getAttribute('aria-valuenow')) + delta),
      );
      if (innerWidth <= 760)
        body.style.gridTemplateRows = `${value}% 8px minmax(110px,1fr)`;
      else body.style.setProperty('--left-pane', value + '%');
      splitter.setAttribute('aria-valuenow', value);
    };
  }
  function saveNote() {
    if ($('note-editor')) state.notes[noteKey()] = $('note-editor').value;
    state.dirty = false;
    persist();
    if ($('note-status')) $('note-status').textContent = '已保存到浏览器样板';
  }
  function navigatePage(page) {
    saveNote();
    state.page = Math.max(0, Math.min(pageCount() - 1, page));
    reader();
    icons();
  }
  function pageShell(title, content) {
    return `<section class="standalone"><header>${ib('shelf', 'arrow-left', '返回书架')}<h1>${title}</h1></header>${content}</section>`;
  }
  function newForm(title = '新建项目', live = false) {
    return `<form id="new-form" class="form-stack"><label class="field"><span>项目名称</span><input id="project-name" name="name" value="${live ? 'test · 课堂笔记' : ''}" placeholder="未命名" maxlength="100" required /></label><label class="field"><span>保存目录</span><div class="field-line"><input value="${esc(state.library)}" aria-label="项目保存目录" readonly />${ib('folder', 'folder-open', '更改目录')}</div></label><label class="field"><span>原始文档 <span class="optional">可选</span></span><input id="project-file" type="file" accept=".pdf,.md,.markdown" /></label>${live ? '<p class="warning">连接课堂可能产生考勤或签到记录。</p><label class="check-line"><input id="new-consent" type="checkbox" required />我已了解，允许连接当前课堂并可能产生考勤记录。</label>' : ''}<div class="form-error" id="form-error" role="alert"></div><div class="form-actions">${btn('close-modal', '取消', null)}<button class="primary" type="submit">${title}</button></div></form>`;
  }
  function attachNewForm(live = false) {
    $('new-form').onsubmit = (e) => {
      e.preventDefault();
      const name = $('project-name').value.trim();
      if (!name || /[<>:"/\\|?*]/.test(name)) {
        $('form-error').textContent = '请输入有效的项目名称';
        return;
      }
      if (state.books.some((b) => b.title === name)) {
        $('form-error').textContent = '已有同名项目，请换一个名称';
        return;
      }
      const file = $('project-file').files[0];
      const book = {
        id: crypto.randomUUID(),
        title: name,
        type: file?.name.toLowerCase().endsWith('.pdf') || live ? 'pdf' : 'md',
        color: 'sage',
        date: '刚刚',
        updated: Date.now(),
        ...(file?.name.toLowerCase().endsWith('.pdf') || live
          ? { cover: 'assets/lecture-page.png' }
          : {}),
      };
      state.books.push(book);
      persist();
      $('modal').close();
      go('shelf');
      notify('已加入样板书架');
    };
  }
  function rainContent() {
    return `<div class="rain-account"><span class="account-name">${icon('circle-user-round')}已登录：示例账号</span>${btn('rain-login', '应用内登录', 'qr-code')}${btn('rain-refresh', '刷新课程', 'refresh-cw')}</div><div class="rain-fields"><label class="field"><span>课程</span><select><option>test</option><option>模式识别</option></select></label><label class="field"><span>历史课堂</span><select><option>test · 2 份 PPT · 78 页</option><option>10月22日 · 贝叶斯决策</option></select></label></div><div class="form-actions">${btn('rain-import', '导入为新项目', 'download', 'primary')}</div><section class="rain-section"><h3>实时采集</h3><label class="field"><span>当前课堂</span><select><option>test · 示例课堂</option></select></label><p class="warning" style="margin-top:18px">连接课堂可能产生考勤或签到记录。</p><label class="check-line"><input id="rain-consent" type="checkbox" />我已了解，允许连接当前课堂并可能产生考勤记录。</label><div class="status-line"><span class="status-dot ${state.rain ? 'active' : ''}"></span><span id="rain-status">${state.rain ? '示例采集中 · 已收集 3 页' : '未连接课堂'}</span></div><div class="control-samples"><button id="rain-new" class="primary" data-action="rain-new" disabled>新建项目并采集</button><button id="rain-append" class="outlined" data-action="rain-append" disabled>追加到当前 PDF</button>${btn('rain-stop', '停止', 'square')}</div><div class="rain-log"><div><span>PPT 1</span><span>原第 1、3、6 页</span></div><div><span>PPT 2</span><span>原第 1、4 页</span></div></div></section>`;
  }
  function attachRain() {
    $('rain-consent').onchange = (e) => {
      $('rain-new').disabled = !e.target.checked;
      $('rain-append').disabled = !e.target.checked;
    };
  }
  function settingsContent() {
    return `<div class="settings-layout"><nav class="settings-nav"><button class="selected" data-action="settings-tab" data-target="general">${icon('settings-2')}通用</button><button data-action="settings-tab" data-target="reading">${icon('book-open')}阅读</button><button data-action="settings-tab" data-target="ai">${icon('sparkles')}AI 接口</button></nav><div><section class="settings-section" data-setting-section="general"><h3>通用</h3><div class="setting-row"><div><span>书架目录</span><small id="settings-folder">${esc(state.library)}</small></div>${btn('folder', '更改目录', 'folder-open')}</div><label class="setting-row"><span>应用外观</span><select id="setting-theme"><option value="light">浅色</option><option value="dark">深色</option></select></label><label class="setting-row"><span>语言</span><select><option>简体中文</option><option>English</option></select></label></section><section class="settings-section" data-setting-section="reading" hidden><h3>阅读</h3><label class="setting-row"><span>Markdown 主题</span><select><option>经典</option><option>纸页</option></select></label><label class="setting-row range-row"><span>笔记字号</span><input id="setting-font" type="range" min="12" max="24" value="${state.settings.font}" /><output id="font-value">${state.settings.font}</output></label><label class="setting-row range-row"><span>预解析页数</span><input id="setting-prepare" type="range" min="0" max="10" value="${state.settings.prepare}" /><output id="prepare-value">${state.settings.prepare}</output></label><label class="setting-row"><span>笔记翻页动画</span><input class="switch" id="setting-animation" type="checkbox" ${state.settings.animation ? 'checked' : ''} /></label><label class="setting-row"><span>允许页外书写</span><input class="switch" id="setting-outside" type="checkbox" ${state.settings.outside ? 'checked' : ''} /></label></section><section class="settings-section" data-setting-section="ai" hidden><h3>OpenAI 兼容接口</h3><div class="form-stack"><label class="field"><span>Base URL</span><input placeholder="https://api.example.com/v1" /></label><label class="field"><span>API Key</span><input type="password" placeholder="不在样板中保存密钥" autocomplete="off" /></label><label class="field"><span>模型</span><input placeholder="模型名称" /></label>${btn('ai-demo', '测试连接', 'plug-zap', 'outlined')}</div></section><div class="form-actions">${btn('save-settings', '完成', 'check', 'primary')}</div></div></div>`;
  }
  function attachSettings() {
    $('setting-theme').value = state.theme;
    $('setting-theme').onchange = (e) => {
      state.theme = e.target.value;
      theme();
      persist();
    };
    for (const [name, output] of [
      ['font', 'font-value'],
      ['prepare', 'prepare-value'],
    ])
      $('setting-' + name).oninput = (e) => {
        state.settings[name] = Number(e.target.value);
        $(output).textContent = e.target.value;
        persist();
      };
    for (const name of ['animation', 'outside'])
      $('setting-' + name).onchange = (e) => {
        state.settings[name] = e.target.checked;
        persist();
      };
  }
  function spec() {
    const colors = [
      ['主色', '--accent'],
      ['强调底色', '--accent-soft'],
      ['背景', '--background'],
      ['表面', '--surface'],
      ['主文字', '--text'],
      ['次文字', '--muted'],
      ['边框', '--border'],
      ['警告', '--warning'],
    ];
    $('app').innerHTML = pageShell(
      '基础规范',
      `<p class="muted">现有界面的可编辑基线 · 2026-10-25</p><section class="token-section"><h2>颜色</h2><div class="token-swatches">${colors.map(([name, variable]) => `<div class="token-swatch"><div style="--swatch:var(${variable})"></div><strong>${name}</strong><code>${variable}</code></div>`).join('')}</div></section><section class="token-section"><h2>字体与尺寸</h2><table class="spec-table"><thead><tr><th>层级</th><th>尺寸</th><th>使用位置</th></tr></thead><tbody><tr><td>页面标题</td><td>25px / 600</td><td>书架</td></tr><tr><td>对话框标题</td><td>22px / 600</td><td>新建、封面</td></tr><tr><td>正文</td><td>14px</td><td>菜单、输入项</td></tr><tr><td>次要文字</td><td>12px</td><td>目录、日期、状态</td></tr><tr><td>图标按钮</td><td>38 × 38px</td><td>常用工具</td></tr><tr><td>封面比例</td><td>3 : 4</td><td>PDF / Note</td></tr></tbody></table></section><section class="token-section"><h2>纸本封面</h2><div class="token-swatches">${['sage', 'rose', 'sky', 'graphite'].map((c) => `<div class="token-swatch"><div style="--swatch:var(--${c})"></div><strong>${c}</strong><code>Note</code></div>`).join('')}</div></section><section class="token-section"><h2>控件</h2><div class="control-samples">${btn('new', '新建项目', 'plus', 'primary')}${btn('rain', '雨课堂', 'cloud-download', 'outlined')}${ib('settings', 'settings', '设置')}<input class="switch" type="checkbox" aria-label="开关样例" checked /><div class="segments"><button aria-pressed="true">编辑</button><button aria-pressed="false">预览</button></div></div></section><section class="token-section"><h2>对应源码</h2><table class="spec-table"><tbody><tr><td>Android 书架</td><td>BookshelfScreen.kt</td></tr><tr><td>Android 颜色 / 设置</td><td>ReaderSettingsDialog.kt</td></tr><tr><td>桌面书架</td><td>src/bookshelf.css</td></tr><tr><td>桌面阅读器</td><td>src/style.css / theme.css</td></tr><tr><td>本样板规范</td><td>tokens.css / reference.css</td></tr></tbody></table></section>`,
    );
  }
  function modal(title, content) {
    $('modal').innerHTML =
      `<header class="modal-head"><h2 id="modal-title">${title}</h2>${ib('close-modal', 'x', '关闭对话框')}</header>${content}`;
    if (!$('modal').open) $('modal').showModal();
    icons();
  }
  function customize(id) {
    const book = state.books.find((b) => b.id === id);
    if (!book) return;
    modal(
      '自定义封面',
      `<form id="cover-form" class="form-stack"><label class="field"><span>显示书名</span><input id="cover-title" value="${esc(book.title)}" maxlength="200" required /></label><label class="field"><span>封面</span><select id="cover-mode"><option value="default">默认封面</option><option value="paper">Note 纸本</option><option value="image">自定义图片</option></select></label><div><span class="muted">纸本配色</span><div class="paper-options">${['sage', 'rose', 'sky', 'graphite'].map((c) => `<button type="button" data-action="cover-color" data-color="${c}" style="--swatch:var(--${c})" title="${c}" aria-label="${c}配色" aria-pressed="${book.color === c}">${book.color === c ? '✓' : ''}</button>`).join('')}</div></div><label class="field" id="cover-upload-field"><span>自定义图片</span><input id="cover-file" type="file" accept="image/png,image/jpeg,image/webp" /></label><div class="form-actions">${btn('close-modal', '取消', null)}<button type="submit" class="primary">保存</button></div></form>`,
    );
    let color = book.color;
    $('cover-mode').value = book.customCover
      ? 'image'
      : book.coverMode || 'default';
    $('cover-form').onclick = (e) => {
      const b = e.target.closest('[data-action="cover-color"]');
      if (!b) return;
      color = b.dataset.color;
      $('cover-mode').value = 'paper';
      document.querySelectorAll('.paper-options button').forEach((el) => {
        el.setAttribute('aria-pressed', String(el === b));
        el.textContent = el === b ? '✓' : '';
      });
    };
    $('cover-form').onsubmit = async (e) => {
      e.preventDefault();
      const file = $('cover-file').files[0];
      const mode = $('cover-mode').value;
      if (
        file &&
        (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type) ||
          file.size > 2 * 1024 * 1024)
      ) {
        notify('请选择 2 MB 以内的 PNG、JPEG 或 WebP 图片');
        return;
      }
      let custom = book.customCover;
      if (file)
        custom = await new Promise((resolve) => {
          const reader = new FileReader();
          reader.onload = () => resolve(reader.result);
          reader.readAsDataURL(file);
        });
      if (mode === 'image' && !custom) {
        notify('请先选择封面图片');
        return;
      }
      book.title = $('cover-title').value.trim() || book.title;
      book.color = color;
      book.coverMode = mode;
      book.customCover = mode === 'image' ? custom : undefined;
      persist();
      $('modal').close();
      render();
    };
  }
  function render() {
    $('scene').value = state.scene;
    if (state.scene === 'shelf') shelf();
    else if (state.scene === 'reader') reader();
    else if (state.scene === 'new') {
      $('app').innerHTML = pageShell(
        '新建项目',
        `<div class="form-page">${newForm()}</div>`,
      );
      attachNewForm();
    } else if (state.scene === 'rain') {
      $('app').innerHTML = pageShell('雨课堂', rainContent());
      attachRain();
    } else if (state.scene === 'settings') {
      $('app').innerHTML = pageShell('设置', settingsContent());
      attachSettings();
    } else spec();
    icons();
  }
  function go(scene) {
    if (state.scene === 'reader') saveNote();
    state.scene = scene;
    location.hash = scene;
    render();
  }
  const actions = {
    shelf: () => go('shelf'),
    reader: () => go('reader'),
    rain: () => go('rain'),
    settings: () => go('settings'),
    new: () => {
      modal('新建项目', newForm());
      attachNewForm();
    },
    import: () => {
      modal('导入文档', newForm('导入为新项目'));
      attachNewForm();
    },
    'open-book': (b) => {
      state.book = b.dataset.id;
      state.page = 0;
      state.dirty = false;
      go('reader');
    },
    customize: (b) => customize(b.dataset.id),
    view: () => {
      if (state.scene !== 'shelf') return notify('阅读视图保持当前布局');
      state.view = state.view === 'grid' ? 'list' : 'grid';
      persist();
      shelf();
    },
    refresh: () => notify('样板书架已刷新'),
    folder: () => {
      modal(
        '书架目录',
        `<form id="folder-form"><label class="field"><span>目录名称</span><input id="folder-name" value="${esc(state.library)}" required /></label><div class="form-actions">${btn('close-modal', '取消', null)}<button class="primary" type="submit">绑定目录</button></div></form>`,
      );
      $('folder-form').onsubmit = (e) => {
        e.preventDefault();
        state.library = $('folder-name').value.trim() || 'pagenote';
        persist();
        $('modal').close();
        render();
      };
    },
    'close-modal': () => {
      if ($('modal').open) $('modal').close();
      else go('shelf');
    },
    sidebar: () => {
      state.sidebar = !state.sidebar;
      $('app').querySelector('.reader-sidebar').hidden = !state.sidebar;
    },
    prev: () => navigatePage(state.page - 1),
    next: () => navigatePage(state.page + 1),
    'page-previews': openPagePreviews,
    'close-page-previews': () => $('page-drawer').close(),
    'choose-page': (b) => {
      $('page-drawer').close();
      navigatePage(Number(b.dataset.page));
      $('page-jump').focus();
    },
    'save-note': () => {
      saveNote();
      notify('示例笔记已保存');
    },
    'reload-note': () => {
      renderNote();
      notify('当前示例笔记已重载');
    },
    'edit-mode': () => {
      state.noteMode = 'edit';
      reader();
      icons();
    },
    'preview-mode': () => {
      state.noteMode = 'preview';
      reader();
      icons();
    },
    tool: (b) => {
      const close = state.tool === b.dataset.tool && !$('pen-options').hidden;
      state.tool = b.dataset.tool;
      updateTools();
      if (state.tool === 'select' || close) closePenOptions();
      else penOptions();
    },
    'pen-options': () => {
      if ($('pen-options').hidden) penOptions();
      else closePenOptions();
    },
    'close-pen-options': closePenOptions,
    'pen-width': (b) => {
      state.penWidth = Number(b.dataset.width);
      updateTools();
    },
    shapes: () => {
      state.tool = 'shape';
      updateTools();
      penOptions();
    },
    shape: (b) => {
      state.shape = b.dataset.shape;
      document
        .querySelectorAll('[data-shape]')
        .forEach((el) => el.setAttribute('aria-pressed', String(el === b)));
      drawStrokePreview();
    },
    ink: (b) => {
      state.ink = b.dataset.color;
      updateTools();
    },
    'zoom-in': () => zoom(0.1),
    'zoom-out': () => zoom(-0.1),
    fit: () => zoom(0),
    'settings-tab': (b) => {
      document.querySelectorAll('[data-setting-section]').forEach((el) => {
        el.hidden = el.dataset.settingSection !== b.dataset.target;
      });
      document
        .querySelectorAll('[data-action="settings-tab"]')
        .forEach((el) => el.classList.toggle('selected', el === b));
    },
    'save-settings': () => {
      persist();
      notify('样板设置已保存');
    },
    'ai-demo': () => notify('此样板不连接模型，也不保存 API Key'),
    'rain-login': () =>
      modal(
        '雨课堂登录',
        '<p class="muted">界面样板不连接真实账号。正式应用的登录过程保留在应用内。</p><div class="form-actions">' +
          btn('close-modal', '关闭', null, 'primary') +
          '</div>',
      ),
    'rain-refresh': () => notify('已显示示例课程'),
    'rain-import': () => {
      modal('导入为新项目', newForm('导入为新项目'));
      $('project-name').value = 'test · 课堂笔记';
      attachNewForm();
    },
    'rain-new': () => {
      modal('新建项目并采集', newForm('新建项目并采集', true));
      attachNewForm(true);
    },
    'rain-append': () => {
      state.rain = true;
      $('rain-status').textContent = '示例采集中 · 已收集 3 页';
      document.querySelector('.status-dot').classList.add('active');
    },
    'rain-stop': () => {
      state.rain = false;
      $('rain-status').textContent = '已停止';
      document.querySelector('.status-dot').classList.remove('active');
    },
    explain: () =>
      modal(
        'AI 讲解',
        '<p>贝叶斯公式把先验和证据合并为后验。比较同一个特征下的不同类别时，分母相同，可以只比较“似然 × 先验”。</p><div class="form-actions">' +
          btn('close-modal', '关闭', null, 'primary') +
          '</div>',
      ),
    annotation: () =>
      modal(
        '批注',
        '<label class="field"><span>批注内容</span><textarea rows="4" placeholder="写下问题或补充说明"></textarea></label><div class="form-actions">' +
          btn('close-modal', '取消', null) +
          btn('save-annotation', '保存批注', 'check', 'primary') +
          '</div>',
      ),
    'save-annotation': () => {
      $('modal').close();
      notify('批注交互样例已完成');
    },
    ocr: () =>
      modal(
        '区域 OCR',
        '<h3>识别结果</h3><p>后验概率与先验概率、类条件概率有关。</p><small>示例结果 · 未调用 OCR 服务</small><div class="form-actions">' +
          btn('close-modal', '关闭', null, 'primary') +
          '</div>',
      ),
    attachments: () =>
      modal(
        '附件',
        '<p class="muted">暂无附件</p><div class="form-actions">' +
          btn('close-modal', '关闭', null) +
          '</div>',
      ),
    history: () =>
      modal(
        '笔记历史',
        '<div class="status-line">当前示例笔记</div><p class="muted">' +
          esc(noteText().slice(0, 120)) +
          '</p><div class="form-actions">' +
          btn('close-modal', '关闭', null) +
          '</div>',
      ),
    export: () => {
      const url = URL.createObjectURL(
        new Blob([noteText()], { type: 'text/markdown' }),
      );
      const a = document.createElement('a');
      a.href = url;
      a.download = 'pagenote-示例笔记.md';
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    },
    'close-reference': () => setReference(false),
    'clear-reference': () => {
      clearReference();
    },
  };
  function zoom(delta) {
    const paper = document.querySelector('.source-paper');
    const current = Number(paper.dataset.zoom || 1);
    const next =
      delta === 0 ? 1 : Math.max(0.7, Math.min(1.5, current + delta));
    paper.style.zoom = next;
    paper.dataset.zoom = next;
    $('zoom-value').textContent = Math.round(next * 100) + '%';
  }
  function format(action) {
    if (state.noteMode !== 'edit') {
      state.noteMode = 'edit';
      reader();
    }
    const editor = $('note-editor');
    editor.focus();
    const [before, after] = {
      'format-bold': ['**', '**'],
      'format-italic': ['*', '*'],
      'format-heading': ['\n## ', ''],
      'format-list': ['\n- ', ''],
      'format-formula': ['\n$$', '$$\n'],
    }[action];
    const selected =
      editor.value.slice(editor.selectionStart, editor.selectionEnd) || '内容';
    editor.setRangeText(
      before + selected + after,
      editor.selectionStart,
      editor.selectionEnd,
      'select',
    );
    editor.dispatchEvent(new Event('input'));
    icons();
  }
  document.addEventListener('click', (e) => {
    if (!e.target.closest('.writing-rail, .pen-options')) closePenOptions();
    const button = e.target.closest('[data-action]');
    if (!button || button.disabled) return;
    const action = button.dataset.action;
    if (action.startsWith('format-')) format(action);
    else actions[action]?.(button);
    icons();
  });
  function setReference(show) {
    $('reference-panel').hidden = !show;
    $('reference-toggle').setAttribute('aria-expanded', String(show));
    updateOverlay();
  }
  function updateOverlay() {
    const overlay =
      !!referenceUrl &&
      !$('reference-panel').hidden &&
      $('reference-mode').value === 'overlay';
    $('reference-overlay').hidden = !overlay;
    $('reference-overlay').style.opacity =
      Number($('reference-opacity').value) / 100;
  }
  function clearReference() {
    if (referenceUrl) URL.revokeObjectURL(referenceUrl);
    referenceUrl = '';
    $('reference-image').removeAttribute('src');
    $('reference-image').hidden = true;
    $('reference-overlay').removeAttribute('src');
    $('reference-empty').hidden = false;
    $('reference-options').hidden = true;
    $('reference-file').value = '';
    updateOverlay();
  }
  $('reference-file').onchange = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    if (
      !['image/png', 'image/jpeg', 'image/webp'].includes(file.type) ||
      file.size > 20 * 1024 * 1024
    ) {
      notify('请选择 20 MB 以内的 PNG、JPEG 或 WebP 图片');
      return;
    }
    clearReference();
    referenceUrl = URL.createObjectURL(file);
    $('reference-image').src = referenceUrl;
    $('reference-image').hidden = false;
    $('reference-overlay').src = referenceUrl;
    $('reference-empty').hidden = true;
    $('reference-options').hidden = false;
    updateOverlay();
  };
  $('reference-mode').onchange = updateOverlay;
  $('reference-opacity').oninput = updateOverlay;
  $('reference-toggle').onclick = () =>
    setReference($('reference-panel').hidden);
  function clean(value) {
    document.body.classList.toggle('clean', value);
    $('restore-tools').hidden = !value;
  }
  $('clean-toggle').onclick = () => clean(true);
  $('restore-tools').onclick = () => clean(false);
  $('theme-toggle').onclick = () => {
    state.theme = state.theme === 'light' ? 'dark' : 'light';
    theme();
    persist();
  };
  document.querySelectorAll('[data-platform-choice]').forEach((b) => {
    b.onclick = () => {
      state.platform = b.dataset.platformChoice;
      theme();
      persist();
    };
  });
  $('scene').onchange = (e) => go(e.target.value);
  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape' || $('modal').open || $('page-drawer')?.open) return;
    if ($('pen-options') && !$('pen-options').hidden) {
      closePenOptions();
      document.querySelector('[data-action="pen-options"]')?.focus();
    } else clean(false);
  });
  window.addEventListener('hashchange', () => {
    const scene = location.hash.slice(1);
    if (
      ['shelf', 'reader', 'new', 'rain', 'settings', 'spec'].includes(scene) &&
      scene !== state.scene
    ) {
      if (state.scene === 'reader') saveNote();
      state.scene = scene;
      render();
    }
  });
  window.addEventListener('beforeunload', () => {
    if (state.scene === 'reader') saveNote();
  });
  state.scene = ['reader', 'new', 'rain', 'settings', 'spec'].includes(
    location.hash.slice(1),
  )
    ? location.hash.slice(1)
    : 'shelf';
  theme();
  render();
})();

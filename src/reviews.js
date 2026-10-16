import { createAIWorkbench } from './workbench.js';
export function createReviews({
  request,
  getContext,
  getRegionContext,
  annotations,
  toast,
}) {
  const panel = document.createElement('aside');
  panel.id = 'aiReviewPanel';
  panel.hidden = true;
  panel.innerHTML =
    '<div class="review-heading"><strong>AI 批注</strong><button id="reviewClose" aria-label="关闭 AI 批注">✕</button></div><p class="review-hint">保存修改要求后，上层 AI 可通过 MCP 读取并处理。</p><div class="review-actions"><button id="reviewOverall">整体要求</button><button id="reviewSelection">选中文字</button></div><div class="review-actions"><button id="reviewDocument">框选原文</button><button id="reviewNote">框选讲解</button></div><p class="review-hint">选择框选后，使用矩形或荧光笔标出范围，松开鼠标填写要求。</p><div id="reviewRecords"></div>';
  document.body.append(panel);
  new ResizeObserver(() => {
    panel.style.top =
      document.querySelector('#workspace').getBoundingClientRect().top + 'px';
  }).observe(document.querySelector('.command-area'));
  const dialog = document.createElement('dialog');
  dialog.id = 'reviewDialog';
  dialog.innerHTML =
    '<h2 id="reviewDraftTitle">添加 AI 批注</h2><label>针对 <select id="reviewSide"><option value="note">右侧讲解</option><option value="document">左侧原文</option></select></label><label id="reviewScopeLabel">范围 <select id="reviewScope"><option value="page">本页</option><option value="document">整份文档</option></select></label><blockquote id="reviewQuote"></blockquote><textarea id="reviewInstruction" rows="5" placeholder="例如：这一段我没懂，请补充直观例子。"></textarea><p class="review-hint">颜色和框选用于定位；这里的文字告诉 AI 应该怎样修改。</p><div class="history-actions"><button id="reviewCancel">取消</button><button id="reviewSave" class="primary">保存批注</button></div>';
  document.body.append(dialog);
  const $ = (id) => document.getElementById(id);
  let records = [],
    draft = null,
    armed = false,
    documentId = null;
  const board = createAIWorkbench({
    getRecords: () => records,
    toast,
    edit: (record) => openDraft({ ...record, editing: record.id }),
    add: async (side) => {
      try {
        const context = await getContext(false, side);
        openDraft({ ...context, kind: 'overall', quote: '' });
      } catch (e) {
        toast(e.message);
      }
    },
    region: async (region) => {
      try {
        const context = await getRegionContext(region);
        openDraft({
          ...context,
          kind: 'region',
          anchor: {
            shape: region.shape,
            coordinateSystem: 'width-1000',
            ...context.anchor,
          },
        });
      } catch (e) {
        toast(e.message);
      }
    },
    savePosition: async (record, boardPosition) => {
      await request(
        '/api/documents/' + documentId + '/ai-reviews/' + record.id,
        {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ boardPosition }),
        },
      );
      record.boardPosition = boardPosition;
    },
    select: async (range, side) => {
      try {
        const quote = range.toString(),
          element =
            range.startContainer.nodeType === 1
              ? range.startContainer
              : range.startContainer.parentElement,
          host =
            side === 'document'
              ? element.closest('.document-sheet')
              : document.querySelector('#noteBody'),
          rect = range.getBoundingClientRect(),
          bounds = host.getBoundingClientRect(),
          scale = 1000 / bounds.width,
          anchor = {
            shape: {
              type: 'rect',
              points: [
                [
                  (rect.left - bounds.left) * scale,
                  (rect.top - bounds.top) * scale,
                ],
                [
                  (rect.right - bounds.left) * scale,
                  (rect.bottom - bounds.top) * scale,
                ],
              ],
              color: '#f5d870',
              width: 1,
            },
            coordinateSystem: 'width-1000',
            text: quote,
          };
        const textRoot =
            side === 'document'
              ? host.querySelector('.textLayer') || host
              : document.querySelector('#preview'),
          prefix = document.createRange();
        prefix.selectNodeContents(textRoot);
        prefix.setEnd(range.startContainer, range.startOffset);
        const before = document.createElement('div');
        before.append(prefix.cloneContents());
        before.querySelectorAll('.rich-code-tools').forEach((n) => n.remove());
        anchor.textStart = before.textContent.length;
        anchor.textEnd = anchor.textStart + quote.length;
        const context = await getContext(true, side);
        openDraft({
          ...context,
          quote,
          kind: 'selection',
          anchor,
          page: side === 'document' ? Number(host.dataset.page) : context.page,
        });
      } catch (e) {
        toast(e.message);
      }
    },
  });
  function show(side = 'note') {
    board.open(side);
    render();
  }
  function hide() {
    board.hide();
    armed = false;
    panel.hidden = true;
    document.body.classList.remove('review-open');
    annotations.activate(null);
    document.dispatchEvent(new Event('review-layout'));
  }
  function render() {
    board.render();
    const host = $('reviewRecords');
    host.replaceChildren();
    for (const record of records) {
      const card = document.createElement('article');
      card.className = 'review-card';
      card.dataset.recordId = record.id;
      const title = document.createElement('strong');
      title.textContent = `${record.number}  · ${record.side === 'note' ? '讲解' : '原文'} · ${record.scope === 'document' ? '整份文档' : `第 ${record.page} 页`}`;
      const quote = document.createElement('blockquote');
      quote.textContent = record.quote || '整体要求';
      const text = document.createElement('p');
      text.textContent = record.instruction;
      const status = document.createElement('span');
      status.className = 'review-status';
      status.textContent = {
        open: '待 AI 处理',
        applied: '已处理',
        dismissed: '已取消',
      }[record.status];
      card.append(title, quote, text, status);
      const edit = document.createElement('button');
      edit.textContent = '修改要求';
      edit.onclick = () => openDraft({ ...record, editing: record.id });
      const dismiss = document.createElement('button');
      dismiss.textContent = record.status === 'dismissed' ? '重新启用' : '取消';
      dismiss.onclick = async () => {
        try {
          await request(
            `/api/documents/${documentId}/ai-reviews/${record.id}`,
            {
              method: 'PATCH',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                status: record.status === 'dismissed' ? 'open' : 'dismissed',
              }),
            },
          );
          await reload();
        } catch (e) {
          toast(e.message);
        }
      };
      card.append(edit, dismiss);
      host.append(card);
    }
    if (!records.length)
      host.textContent = '尚无批注。可以提出整体要求，或选择具体内容。';
    paintNumbers();
  }
  function selectionRects(record, host) {
    const root =
      record.side === 'document'
        ? host.querySelector('.textLayer') ||
          host.querySelector('.markdown-page')
        : document.querySelector('#preview');
    if (!root) return [];
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode: (n) =>
        n.parentElement.closest(
          '.rich-code-tools,.review-marker,.review-highlight',
        )
          ? NodeFilter.FILTER_REJECT
          : NodeFilter.FILTER_ACCEPT,
    });
    const nodes = [];
    let text = '',
      node;
    while ((node = walker.nextNode())) {
      nodes.push({ node, start: text.length });
      text += node.textContent;
    }
    const quote = record.anchor?.text || record.quote;
    if (!quote) return [];
    let start = record.anchor?.textStart;
    if (
      !Number.isInteger(start) ||
      text.slice(start, start + quote.length) !== quote
    )
      start = text.indexOf(quote);
    if (start < 0) return [];
    const end = start + quote.length,
      a = nodes.find((n) => n.start + n.node.length > start),
      b = nodes.find((n) => n.start + n.node.length >= end);
    if (!a || !b) return [];
    const range = document.createRange();
    range.setStart(a.node, start - a.start);
    range.setEnd(b.node, end - b.start);
    return [...range.getClientRects()].filter(
      (r) => r.width > 0 && r.height > 0,
    );
  }
  function paintNumbers() {
    const kept = new Set();
    for (const record of records) {
      if (
        record.status === 'dismissed' ||
        !record.anchor?.shape?.points?.length
      )
        continue;
      const host =
        record.side === 'document'
          ? document.querySelector(
              '.document-sheet[data-page="' + record.page + '"]',
            )
          : document.querySelector('#noteBody');
      if (
        !host ||
        (record.side === 'note' &&
          Number(document.querySelector('#pageNumber').value) !== record.page)
      )
        continue;
      const bounds = host.getBoundingClientRect(),
        // Range/viewport rectangles already include CSS page magnification.
        // Absolute children need local CSS pixels, relative to the padding box.
        magnification = Number(getComputedStyle(host).zoom) || 1,
        localX = (x) => (x - bounds.left) / magnification - host.clientLeft,
        localY = (y) => (y - bounds.top) / magnification - host.clientTop,
        point = record.anchor.shape.points[0],
        badge =
          [...host.querySelectorAll('.review-marker')].find(
            (e) => e.dataset.recordId === record.id,
          ) || document.createElement('button');
      badge.dataset.recordId = record.id;
      badge.className = 'review-marker';
      if (badge.textContent !== String(record.number))
        badge.textContent = record.number;
      let tagLeft = localX(bounds.left + (point[0] * bounds.width) / 1000) + 3,
        tagTop = localY(bounds.top + (point[1] * bounds.width) / 1000) - 20;
      badge.title = record.instruction;
      badge.ondblclick = () => {
        show(record.side);
        const card = document.querySelector(
            '.ai-workbench[data-side="' +
              record.side +
              '"] [data-record-id="' +
              record.id +
              '"]',
          ),
          viewport = card?.closest('.board-viewport');
        if (viewport)
          viewport.scrollTo({
            left: Math.max(0, card.offsetLeft - 20),
            top: Math.max(0, card.offsetTop - 20),
          });
      };
      if (record.kind === 'selection') {
        const rects = selectionRects(record, host);
        if (!rects.length) continue;
        for (const [index, r] of rects.entries()) {
          const mark =
            [...host.querySelectorAll('.review-highlight')].find(
              (e) =>
                e.dataset.recordId === record.id &&
                e.dataset.line === String(index),
            ) || document.createElement('span');
          mark.dataset.recordId = record.id;
          mark.dataset.line = String(index);
          mark.className = 'review-highlight';
          kept.add(mark);
          mark.style.left = localX(r.left) + 'px';
          mark.style.top = localY(r.top) + 'px';
          mark.style.width = r.width / magnification + 'px';
          mark.style.height = r.height / magnification + 'px';
          if (mark.parentNode !== host) host.append(mark);
        }
        const r = rects[0];
        tagLeft = localX(r.right) + 3;
        tagTop = localY(r.top) - 20;
      }
      const left = tagLeft + 'px',
        top = tagTop + 'px';
      if (badge.style.left !== left) badge.style.left = left;
      if (badge.style.top !== top) badge.style.top = top;
      badge.oncontextmenu = (e) => {
        e.preventDefault();
        showDelete(record, e.clientX, e.clientY);
      };
      kept.add(badge);
      if (badge.parentNode !== host) host.append(badge);
    }
    document
      .querySelectorAll('.review-marker,.review-highlight')
      .forEach((e) => {
        if (!kept.has(e)) e.remove();
      });
  }
  let refreshFrame;
  const schedule = () => {
    cancelAnimationFrame(refreshFrame);
    refreshFrame = requestAnimationFrame(paintNumbers);
  };
  new ResizeObserver(schedule).observe(document.querySelector('#noteBody'));
  new ResizeObserver(schedule).observe(
    document.querySelector('#documentScroll'),
  );
  new MutationObserver(schedule).observe(document.querySelector('#preview'), {
    childList: true,
    subtree: true,
  });
  async function reload(id = documentId) {
    documentId = id;
    records = id ? await request(`/api/documents/${id}/ai-reviews`) : [];
    render();
  }
  function openDraft(value) {
    if (value.documentId) documentId = value.documentId;
    draft = value;
    $('reviewDraftTitle').textContent = value.editing
      ? `修改 ${value.number} 号批注`
      : '添加 AI 批注';
    $('reviewSide').value = value.side || 'note';
    $('reviewSide').disabled = value.kind !== 'overall';
    $('reviewScopeLabel').hidden = value.kind !== 'overall';
    $('reviewScope').value = value.scope || 'page';
    $('reviewQuote').textContent = value.quote || '整体要求';
    $('reviewInstruction').value = value.instruction || '';
    if (!dialog.open) dialog.show();
    dialog.dataset.side = value.side || 'note';
    positionDraft();
    $('reviewInstruction').focus({ preventScroll: true });
  }
  const deleteMenu = document.createElement('button');
  deleteMenu.id = 'deleteTagMenu';
  deleteMenu.textContent = '删除 Tag';
  deleteMenu.hidden = true;
  document.body.append(deleteMenu);
  let deleteRecord = null;
  function showDelete(record, x, y) {
    deleteRecord = record;
    deleteMenu.style.left = Math.min(innerWidth - 130, x) + 'px';
    deleteMenu.style.top = Math.min(innerHeight - 45, y) + 'px';
    deleteMenu.hidden = false;
  }
  deleteMenu.onclick = async () => {
    const record = deleteRecord;
    deleteMenu.hidden = true;
    try {
      await request(
        '/api/documents/' + documentId + '/ai-reviews/' + record.id,
        { method: 'DELETE' },
      );
      if (draft?.editing === record.id) dialog.close();
      await reload();
    } catch (e) {
      toast(e.message);
    }
  };
  document.addEventListener('pointerdown', (e) => {
    if (e.target !== deleteMenu) deleteMenu.hidden = true;
  });
  document.addEventListener('review-context', (e) =>
    showDelete(e.detail.record, e.detail.x, e.detail.y),
  );
  function paneBounds() {
    return document
      .querySelector(
        dialog.dataset.side === 'document' ? '.document-pane' : '.note-pane',
      )
      .getBoundingClientRect();
  }
  function clampDraft(x, y) {
    const pane = paneBounds(),
      width = dialog.offsetWidth,
      height = dialog.offsetHeight;
    dialog.style.left =
      Math.max(pane.left + 10, Math.min(pane.right - width - 10, x)) + 'px';
    dialog.style.top =
      Math.max(pane.top + 10, Math.min(pane.bottom - height - 10, y)) + 'px';
  }
  function positionDraft() {
    if (!dialog.open) return;
    const pane = paneBounds();
    dialog.style.width = Math.min(360, Math.max(240, pane.width - 24)) + 'px';
    dialog.style.maxHeight = Math.max(160, pane.height - 24) + 'px';
    let rects = [];
    const host =
      draft?.side === 'document'
        ? document.querySelector(
            '.document-sheet[data-page="' + draft.page + '"]',
          )
        : document.querySelector('#noteBody');
    if (host && draft?.kind === 'selection')
      rects = selectionRects(draft, host);
    else if (host && draft?.anchor?.shape?.points) {
      const bounds = host.getBoundingClientRect(),
        pts = draft.anchor.shape.points,
        scale = bounds.width / 1000;
      rects = [
        {
          left: bounds.left + Math.min(...pts.map((p) => p[0])) * scale,
          right: bounds.left + Math.max(...pts.map((p) => p[0])) * scale,
          top: bounds.top + Math.min(...pts.map((p) => p[1])) * scale,
          bottom: bounds.top + Math.max(...pts.map((p) => p[1])) * scale,
        },
      ];
    }
    const w = dialog.offsetWidth,
      h = dialog.offsetHeight,
      candidates = [
        [pane.right - w - 12, pane.top + 12],
        [pane.right - w - 12, pane.bottom - h - 12],
        [pane.left + 12, pane.bottom - h - 12],
        [pane.left + 12, pane.top + 12],
      ];
    if (rects.length) {
      const r = rects[0];
      candidates.unshift(
        [r.right + 12, r.top],
        [r.left, r.bottom + 12],
        [r.left, r.top - h - 12],
      );
    }
    const options = candidates.map(([x, y]) => {
      x = Math.max(pane.left + 10, Math.min(pane.right - w - 10, x));
      y = Math.max(pane.top + 10, Math.min(pane.bottom - h - 10, y));
      return {
        x,
        y,
        score: rects.reduce(
          (sum, r) =>
            sum +
            Math.max(0, Math.min(x + w, r.right) - Math.max(x, r.left)) *
              Math.max(0, Math.min(y + h, r.bottom) - Math.max(y, r.top)),
          0,
        ),
      };
    });
    options.sort((a, b) => a.score - b.score);
    clampDraft(options[0].x, options[0].y);
  }
  $('reviewDraftTitle').onpointerdown = (e) => {
    if (e.button !== 0) return;
    e.preventDefault();
    const heading = e.currentTarget,
      r = dialog.getBoundingClientRect(),
      x = e.clientX,
      y = e.clientY;
    heading.setPointerCapture(e.pointerId);
    heading.onpointermove = (m) =>
      clampDraft(r.left + m.clientX - x, r.top + m.clientY - y);
    heading.onpointerup = heading.onpointercancel = () => {
      heading.onpointermove = null;
    };
  };
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && dialog.open) {
      dialog.close();
      e.preventDefault();
    }
  });
  $('reviewSide').addEventListener('change', () => {
    dialog.dataset.side = $('reviewSide').value;
    positionDraft();
  });
  window.addEventListener('resize', () => {
    if (dialog.open) {
      const r = dialog.getBoundingClientRect();
      const pane = paneBounds();
      dialog.style.width = Math.min(360, Math.max(240, pane.width - 24)) + 'px';
      dialog.style.maxHeight = Math.max(160, pane.height - 24) + 'px';
      clampDraft(r.left, r.top);
    }
  });
  async function selection() {
    try {
      armed = false;
      const context = await getContext(true);
      if (!context.quote?.trim())
        throw Error(
          '请先在原文或讲解中选中文字；Word 编辑中可使用 Word 的文字选择。',
        );
      openDraft({ ...context, kind: 'selection' });
    } catch (e) {
      toast(e.message);
    }
  }
  $('reviewClose').onclick = hide;
  $('reviewOverall').onclick = async () => {
    try {
      const context = await getContext(false);
      openDraft({ ...context, kind: 'overall', quote: '' });
    } catch (e) {
      toast(e.message);
    }
  };
  $('reviewSelection').onmousedown = (e) => e.preventDefault();
  $('reviewSelection').onclick = selection;
  for (const [id, side] of [
    ['reviewDocument', 'document'],
    ['reviewNote', 'note'],
  ])
    $(id).onclick = () => {
      armed = true;
      document.dispatchEvent(new CustomEvent('review-ink', { detail: side }));
      document.querySelector('[data-ink-tool="rect"]').click();
    };
  $('reviewCancel').onclick = () => dialog.close();
  $('reviewSave').onclick = async () => {
    if (!$('reviewInstruction').value.trim()) {
      toast('请写下给 AI 的要求');
      return;
    }
    $('reviewSave').disabled = true;
    try {
      const side = $('reviewSide').value,
        context = draft.side === side ? draft : await getContext(false, side);
      const body = {
        ...draft,
        ...context,
        side,
        scope: $('reviewScope').value,
        instruction: $('reviewInstruction').value,
      };
      await request(
        `/api/documents/${documentId}/ai-reviews${draft.editing ? '/' + draft.editing : ''}`,
        {
          method: draft.editing ? 'PATCH' : 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(
            draft.editing
              ? { instruction: body.instruction, status: 'open' }
              : body,
          ),
        },
      );
      dialog.close();
      await reload();
      toast('改进点已保存');
    } catch (e) {
      toast(e.message);
    } finally {
      $('reviewSave').disabled = false;
    }
  };
  return {
    show: (side = 'note') => {
      show(side);
      document.dispatchEvent(new Event('review-layout'));
    },
    hide,
    reload,
    refresh: paintNumbers,
    get open() {
      return board.isOpen;
    },
    async region(region) {
      if (!armed) return;
      armed = false;
      annotations.activate(null);
      document.dispatchEvent(new Event('review-tools'));
      try {
        const context = await getRegionContext(region);
        openDraft({
          ...context,
          kind: 'region',
          anchor: {
            shape: region.shape,
            coordinateSystem: 'width-1000',
            ...context.anchor,
          },
        });
      } catch (e) {
        toast(e.message);
      }
    },
    get records() {
      return records;
    },
  };
}

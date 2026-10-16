import DOMPurify from 'dompurify';
const ns = 'http://www.w3.org/2000/svg',
  clone = (x) => JSON.parse(JSON.stringify(x));
const el = (name, attrs = {}) => {
  const e = document.createElementNS(ns, name);
  for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, String(v));
  return e;
};
export function createAnnotations({
  request,
  toast,
  getDocument,
  getPage,
  onSaveStatus = () => {},
  onRegion = () => {},
}) {
  let all = { document: {}, note: {} },
    id = null,
    active = null,
    tool = 'pen',
    color = '#dc3545',
    width = 3,
    lastSide = 'note',
    lastPage = 1,
    selected = null,
    gesture = null,
    timer,
    saving = Promise.resolve(),
    dirty = new Map();
  const layers = new Map(),
    undo = new Map(),
    redo = new Map();
  const key = (side, p) => `${side}:${p}`,
    items = (side, p) => ((all[side] ||= {})[p] ||= []);
  function bounds(layer) {
    const r = layer.svg.getBoundingClientRect();
    return { r, scale: 1000 / Math.max(1, r.width) };
  }
  function point(layer, e) {
    const { r, scale } = bounds(layer);
    return [
      Math.max(0, (e.clientX - r.left) * scale),
      Math.max(0, (e.clientY - r.top) * scale),
    ];
  }
  function remember(side, p) {
    const k = key(side, p);
    (undo.get(k) || undo.set(k, []).get(k)).push(clone(items(side, p)));
    if (undo.get(k).length > 80) undo.get(k).shift();
    redo.set(k, []);
  }
  function changed(side, p) {
    const snapshot = { id, side, page: p, items: clone(items(side, p)) };
    dirty.set(key(side, p), snapshot);
    try {
      localStorage.setItem(
        `page-notes-ink:${id}:${side}:${p}`,
        JSON.stringify(snapshot.items),
      );
    } catch {}
    onSaveStatus('批注等待保存…');
    clearTimeout(timer);
    timer = setTimeout(() => flush(false).catch((e) => toast(e.message)), 500);
    paintAll();
  }
  async function flush(finishDraft = true) {
    if (finishDraft) finish();
    clearTimeout(timer);
    const snapshots = [...dirty.values()];
    dirty.clear();
    const job = saving
      .catch(() => {})
      .then(async () => {
        for (const snapshot of snapshots) {
          try {
            await request(
              `/api/documents/${snapshot.id}/annotations/${snapshot.side}/${snapshot.page}`,
              {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ items: snapshot.items }),
              },
            );
            const k = `page-notes-ink:${snapshot.id}:${snapshot.side}:${snapshot.page}`;
            if (localStorage.getItem(k) === JSON.stringify(snapshot.items))
              localStorage.removeItem(k);
          } catch (e) {
            if (
              id === snapshot.id &&
              !dirty.has(key(snapshot.side, snapshot.page))
            )
              dirty.set(key(snapshot.side, snapshot.page), snapshot);
            onSaveStatus('批注保存失败 · 已保留本地恢复副本');
            throw e;
          }
        }
        if (snapshots.length && !dirty.size) onSaveStatus('批注已保存到本地');
      });
    saving = job;
    await job;
  }
  function shape(item, index) {
    const [a, b = a] = item.points,
      base = {
        stroke: item.color,
        'stroke-width': item.width,
        fill: 'none',
        'stroke-linecap': 'round',
        'stroke-linejoin': 'round',
        'data-item': index,
      };
    let node;
    if (item.type === 'pen' || item.type === 'highlight')
      node = el('path', {
        ...base,
        d:
          item.points.length === 1
            ? `M${a} l.1 .1`
            : item.points
                .map((p, i) => (i ? 'L' : 'M') + p.join(' '))
                .join(' '),
        opacity: item.type === 'highlight' ? 0.35 : 1,
      });
    else if (item.type === 'line' || item.type === 'arrow') {
      let d = `M${a.join(' ')} L${b.join(' ')}`;
      if (item.type === 'arrow') {
        const angle = Math.atan2(b[1] - a[1], b[0] - a[0]),
          size = 14 + item.width * 2;
        d += ` M${b[0] - size * Math.cos(angle - 0.45)} ${b[1] - size * Math.sin(angle - 0.45)} L${b} L${b[0] - size * Math.cos(angle + 0.45)} ${b[1] - size * Math.sin(angle + 0.45)}`;
      }
      node = el('path', { ...base, d });
    } else if (item.type === 'rect')
      node = el('rect', {
        ...base,
        x: Math.min(a[0], b[0]),
        y: Math.min(a[1], b[1]),
        width: Math.abs(a[0] - b[0]),
        height: Math.abs(a[1] - b[1]),
      });
    else if (item.type === 'ellipse')
      node = el('ellipse', {
        ...base,
        cx: (a[0] + b[0]) / 2,
        cy: (a[1] + b[1]) / 2,
        rx: Math.abs(a[0] - b[0]) / 2,
        ry: Math.abs(a[1] - b[1]) / 2,
      });
    else if (item.type === 'image')
      node = el('image', {
        'data-item': index,
        x: a[0],
        y: a[1],
        width: Math.max(10, b[0] - a[0]),
        height: Math.max(10, b[1] - a[1]),
        href: item.url,
        preserveAspectRatio: 'xMidYMid meet',
      });
    else if (item.type === 'html') {
      node = el('foreignObject', {
        'data-item': index,
        x: a[0],
        y: a[1],
        width: Math.max(50, b[0] - a[0]),
        height: Math.max(50, b[1] - a[1]),
      });
      const body = document.createElement('div');
      body.className = 'prose ink-rich-content';
      body.innerHTML = DOMPurify.sanitize(item.html, {
        ADD_TAGS: ['annotation'],
      });
      node.append(body);
    } else {
      node = el('text', {
        'data-item': index,
        x: a[0],
        y: a[1],
        fill: item.color,
        'font-size': Math.max(20, item.width * 5),
        'font-family': 'Segoe UI, Microsoft YaHei, sans-serif',
      });
      String(item.text)
        .split('\n')
        .forEach((line, i) => {
          const t = el('tspan', { x: a[0], dy: i ? '1.4em' : 0 });
          t.textContent = line;
          node.append(t);
        });
    }
    return node;
  }
  function noteBlocks(layer) {
    return [
      ...layer.host.querySelectorAll(
        '#preview h1,#preview h2,#preview h3,#preview p,#preview pre,#preview table,#preview blockquote,#preview figure',
      ),
    ].map((node, index) => {
      const copy = node.cloneNode(true);
      copy.querySelectorAll('.rich-code-tools').forEach((n) => n.remove());
      return {
        node,
        index,
        text: copy.textContent.trim().slice(0, 500),
        rect: node.getBoundingClientRect(),
      };
    });
  }
  function project(item, layer, blocks) {
    if (layer.side !== 'note' || !blocks.length) return item;
    const bounds = layer.host.getBoundingClientRect(),
      scale = 1000 / bounds.width;
    if (!item.layoutAnchor) {
      const p = item.points[0],
        y = bounds.top + p[1] / scale;
      const block = blocks.reduce(
        (best, b) =>
          Math.abs(b.rect.top - y) < Math.abs(best.rect.top - y) ? b : best,
        blocks[0],
      );
      item.layoutAnchor = {
        text: block.text,
        index: block.index,
        x: (block.rect.left - bounds.left) * scale,
        y: (block.rect.top - bounds.top) * scale,
        width: layer.host.offsetWidth,
        fontSize: parseFloat(getComputedStyle(block.node).fontSize),
      };
    }
    const a = item.layoutAnchor,
      block = blocks.find((b) => b.text === a.text) || blocks[a.index];
    if (!block) return item;
    const ratio =
        (parseFloat(getComputedStyle(block.node).fontSize) / a.fontSize) *
        (a.width / Math.max(1, layer.host.offsetWidth)),
      x = (block.rect.left - bounds.left) * scale,
      y = (block.rect.top - bounds.top) * scale;
    return {
      ...item,
      points: item.points.map((p) => [
        x + (p[0] - a.x) * ratio,
        y + (p[1] - a.y) * ratio,
      ]),
      width: item.width * ratio,
    };
  }
  function paint(layer) {
    const height =
      (layer.host.offsetHeight / Math.max(1, layer.host.offsetWidth)) * 1000;
    layer.svg.setAttribute('viewBox', `0 0 1000 ${Math.max(1, height)}`);
    const blocks = layer.side === 'note' ? noteBlocks(layer) : [];
    layer.svg.replaceChildren(
      ...items(layer.side, layer.page).map((item, index) =>
        shape(project(item, layer, blocks), index),
      ),
    );
    layer.svg.classList.toggle(
      'ink-active',
      active === 'both' || layer.side === active,
    );
    layer.svg.dataset.tool = tool;
    if (
      selected?.side === layer.side &&
      selected.page === layer.page &&
      (active === 'both' || active === layer.side)
    ) {
      const node = layer.svg.querySelector(`[data-item="${selected.index}"]`);
      if (node) {
        const b = node.getBBox();
        layer.svg.append(
          el('rect', {
            x: b.x - 5,
            y: b.y - 5,
            width: b.width + 10,
            height: b.height + 10,
            class: 'ink-selection',
            'pointer-events': 'none',
          }),
          el('circle', {
            cx: b.x + b.width + 5,
            cy: b.y + b.height + 5,
            r: 8,
            class: 'ink-handle',
            'data-resize': '1',
          }),
        );
      }
    }
  }
  function paintAll() {
    for (const layer of layers.values()) paint(layer);
    document.querySelectorAll('[data-ink-tool]').forEach((b) => {
      b.classList.toggle('selected', b.dataset.inkTool === tool);
      b.setAttribute('aria-pressed', String(b.dataset.inkTool === tool));
    });
  }
  function start(layer, e) {
    if ((active !== 'both' && active !== layer.side) || e.button > 0) return;
    lastSide = layer.side;
    lastPage = layer.page;
    e.preventDefault();
    layer.svg.setPointerCapture(e.pointerId);
    const p = point(layer, e),
      pIndex = Number(e.target.closest('[data-item]')?.dataset.item);
    const index = Number.isNaN(pIndex) ? -1 : pIndex;
    if (tool === 'text') {
      textAt(layer, p);
      return;
    }
    if (tool === 'erase') {
      if (index >= 0) {
        remember(layer.side, layer.page);
        items(layer.side, layer.page).splice(index, 1);
        selected = null;
        changed(layer.side, layer.page);
      }
      return;
    }
    if (tool === 'select') {
      if (e.target.dataset.resize && selected) {
        const item = items(layer.side, layer.page)[selected.index];
        const node = layer.svg.querySelector(`[data-item="${selected.index}"]`),
          box = node.getBBox();
        remember(layer.side, layer.page);
        gesture = {
          layer,
          start: p,
          index: selected.index,
          original: clone(item.points),
          box,
          resize: true,
        };
        return;
      }
      selected =
        index >= 0 ? { side: layer.side, page: layer.page, index } : null;
      if (selected) {
        remember(layer.side, layer.page);
        gesture = {
          layer,
          start: p,
          index,
          original: clone(items(layer.side, layer.page)[index].points),
        };
      }
      paintAll();
      return;
    }
    remember(layer.side, layer.page);
    selected = null;
    const entry = {
      type: tool,
      color,
      width: tool === 'highlight' ? Math.max(12, width * 5) : width,
      points: [p],
    };
    if (!['pen', 'highlight'].includes(tool)) entry.points.push(p);
    items(layer.side, layer.page).push(entry);
    gesture = {
      layer,
      start: p,
      index: items(layer.side, layer.page).length - 1,
    };
    paint(layer);
  }
  function move(layer, e) {
    if (
      (active === 'both' || active === layer.side) &&
      tool === 'erase' &&
      e.buttons === 1
    ) {
      const node = document
        .elementFromPoint(e.clientX, e.clientY)
        ?.closest('[data-item]');
      if (node && layer.svg.contains(node)) {
        remember(layer.side, layer.page);
        items(layer.side, layer.page).splice(Number(node.dataset.item), 1);
        selected = null;
        changed(layer.side, layer.page);
      }
      return;
    }
    if (!gesture || gesture.layer !== layer) return;
    e.preventDefault();
    const p = point(layer, e),
      entry = items(layer.side, layer.page)[gesture.index];
    if (!entry) return;
    if (gesture.original) {
      if (gesture.resize) {
        const b = gesture.box,
          sx = Math.max(0.1, (p[0] - b.x) / Math.max(1, b.width)),
          sy = Math.max(0.1, (p[1] - b.y) / Math.max(1, b.height));
        entry.points = gesture.original.map((q) => [
          b.x + (q[0] - b.x) * sx,
          b.y + (q[1] - b.y) * sy,
        ]);
      } else
        entry.points = gesture.original.map((q) => [
          q[0] + p[0] - gesture.start[0],
          q[1] + p[1] - gesture.start[1],
        ]);
    } else if (['pen', 'highlight'].includes(entry.type)) {
      if (entry.points.length < 30000) entry.points.push(p);
    } else entry.points[1] = p;
    paint(layer);
  }
  function finish() {
    if (!gesture) return;
    const { layer, index, original } = gesture;
    gesture = null;
    changed(layer.side, layer.page);
    if (!original) {
      const entry = items(layer.side, layer.page)[index];
      if (entry && ['rect', 'highlight', 'pen', 'ellipse'].includes(entry.type))
        onRegion({ side: layer.side, page: layer.page, shape: clone(entry) });
    }
  }
  function mount(host, side, p) {
    const k = key(side, p),
      previous = layers.get(k);
    if (previous?.host === host) {
      paint(previous);
      return;
    }
    previous?.svg.remove();
    const svg = el('svg', {
      preserveAspectRatio: 'none',
      class: 'ink-layer',
      role: 'img',
      'aria-label':
        side === 'document' ? `第 ${p} 页文档批注` : `第 ${p} 页笔记批注`,
    });
    host.append(svg);
    const layer = { host, svg, side, page: p };
    layers.set(k, layer);
    svg.addEventListener('pointerdown', (e) => start(layer, e));
    svg.addEventListener('pointermove', (e) => move(layer, e));
    svg.addEventListener('pointerup', finish);
    svg.addEventListener('pointercancel', finish);
    paint(layer);
  }
  let textTarget;
  function textAt(layer, p) {
    textTarget = { side: layer.side, page: layer.page, p };
    document.getElementById('inkTextInput').value = '';
    document.getElementById('inkTextDialog').showModal();
    document.getElementById('inkTextInput').focus();
  }
  document.getElementById('inkTextApply').onclick = () => {
    const text = document.getElementById('inkTextInput').value;
    if (text && textTarget) {
      const { side, page, p } = textTarget;
      remember(side, page);
      items(side, page).push({ type: 'text', color, width, points: [p], text });
      changed(side, page);
    }
    document.getElementById('inkTextDialog').close();
  };
  document.getElementById('inkTextCancel').onclick = () =>
    document.getElementById('inkTextDialog').close();
  function destination(side) {
    const p = getPage(),
      layer = layers.get(key(side, p));
    if (!layer) throw Error('当前页尚未准备好');
    const scroller = document.getElementById(
        side === 'document' ? 'documentScroll' : 'noteScroll',
      ),
      s = scroller.getBoundingClientRect(),
      r = layer.svg.getBoundingClientRect();
    return {
      page: p,
      p: [
        80,
        Math.max(90, ((s.top - r.top + 80) * 1000) / Math.max(1, r.width)),
      ],
    };
  }
  function addText(side, text) {
    const dest = destination(side);
    remember(side, dest.page);
    items(side, dest.page).push({
      type: 'text',
      color,
      width,
      points: [dest.p],
      text: text.slice(0, 10000),
    });
    changed(side, dest.page);
  }
  async function addImage(side, file) {
    const targetId = id,
      dest = destination(side),
      form = new FormData();
    form.append('file', file);
    const result = await request('/api/attachments', {
      method: 'POST',
      body: form,
    });
    if (id !== targetId) throw Error('文档已切换，请在原文档重新粘贴');
    const im = new Image();
    im.src = result.url;
    await im.decode();
    const w = 350,
      h = (w * im.naturalHeight) / im.naturalWidth;
    remember(side, dest.page);
    items(side, dest.page).push({
      type: 'image',
      color,
      width,
      points: [dest.p, [dest.p[0] + w, dest.p[1] + h]],
      url: result.url,
    });
    changed(side, dest.page);
  }
  function addRich(side, html) {
    const dest = destination(side),
      box = document.createElement('div');
    box.className = 'prose ink-rich-content';
    box.style.cssText =
      'position:fixed;left:-10000px;top:0;width:500px;visibility:hidden';
    box.innerHTML = DOMPurify.sanitize(html, { ADD_TAGS: ['annotation'] });
    document.body.append(box);
    const height = Math.max(60, box.scrollHeight + 20),
      safe = box.innerHTML;
    box.remove();
    remember(side, dest.page);
    items(side, dest.page).push({
      type: 'html',
      color,
      width,
      points: [dest.p, [dest.p[0] + 500, dest.p[1] + height]],
      html: safe.slice(0, 100000),
    });
    changed(side, dest.page);
  }
  function undoRedo(back) {
    if (!active) return;
    const side = active === 'both' ? lastSide : active,
      p = active === 'both' ? lastPage : getPage(),
      k = key(side, p),
      from = (back ? undo : redo).get(k);
    if (!from?.length) return;
    const to = back ? redo : undo;
    (to.get(k) || to.set(k, []).get(k)).push(clone(items(side, p)));
    all[side][p] = from.pop();
    selected = null;
    changed(side, p);
  }
  async function copySelection() {
    if (!selected) throw Error('请先使用选择工具选中批注');
    const item = items(selected.side, selected.page)[selected.index];
    await navigator.clipboard.writeText(
      'PAGE_NOTES_ANNOTATION:\n' + JSON.stringify(item),
    );
    toast('批注已复制，可粘贴到另一侧');
  }
  function pasteObject(side, text) {
    if (!text.startsWith('PAGE_NOTES_ANNOTATION:\n')) return false;
    const item = JSON.parse(text.slice(23));
    if (
      ![
        'pen',
        'highlight',
        'line',
        'arrow',
        'rect',
        'ellipse',
        'text',
        'image',
        'html',
      ].includes(item.type) ||
      !Array.isArray(item.points)
    )
      throw Error('不是有效的批注');
    const dest = destination(side),
      origin = item.points[0];
    item.points = item.points.map((p) => [
      p[0] - origin[0] + dest.p[0],
      p[1] - origin[1] + dest.p[1],
    ]);
    remember(side, dest.page);
    items(side, dest.page).push(item);
    changed(side, dest.page);
    return true;
  }
  document.querySelectorAll('[data-ink-tool]').forEach(
    (b) =>
      (b.onclick = () => {
        tool = b.dataset.inkTool;
        paintAll();
      }),
  );
  document.getElementById('inkColor').oninput = (e) => {
    color = e.target.value;
    if (selected) {
      remember(selected.side, selected.page);
      items(selected.side, selected.page)[selected.index].color = color;
      changed(selected.side, selected.page);
    }
  };
  document.getElementById('inkWidth').onchange = (e) => {
    width = Number(e.target.value);
    if (selected) {
      remember(selected.side, selected.page);
      items(selected.side, selected.page)[selected.index].width = width;
      changed(selected.side, selected.page);
    }
  };
  document.getElementById('inkUndo').onclick = () => undoRedo(true);
  document.getElementById('inkRedo').onclick = () => undoRedo(false);
  document.getElementById('inkCopy').onclick = () =>
    copySelection().catch((e) => toast(e.message));
  document.getElementById('inkDelete').onclick = () => {
    if (selected) {
      remember(selected.side, selected.page);
      items(selected.side, selected.page).splice(selected.index, 1);
      changed(selected.side, selected.page);
      selected = null;
      paintAll();
    }
  };
  document.addEventListener('keydown', (e) => {
    if (
      !active ||
      e.target.isContentEditable ||
      e.target.closest('dialog,input,textarea')
    )
      return;
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') {
      e.preventDefault();
      undoRedo(!e.shiftKey);
    } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'y') {
      e.preventDefault();
      undoRedo(false);
    } else if (selected && (e.key === 'Delete' || e.key === 'Backspace')) {
      e.preventDefault();
      document.getElementById('inkDelete').click();
    }
  });
  return {
    async load(nextId) {
      await flush();
      id = nextId;
      all = await request(`/api/documents/${id}/annotations`);
      undo.clear();
      redo.clear();
      selected = null;
      for (const layer of layers.values()) layer.svg.remove();
      layers.clear();
      for (const side of ['document', 'note']) {
        all[side] ||= {};
        for (let i = 0; i < localStorage.length; i++) {
          const k = localStorage.key(i),
            prefix = `page-notes-ink:${id}:${side}:`;
          if (k.startsWith(prefix)) {
            const p = Number(k.slice(prefix.length));
            try {
              all[side][p] = JSON.parse(localStorage.getItem(k));
              changed(side, p);
            } catch {}
          }
        }
      }
    },
    mountDocument(sheets) {
      for (const [k, l] of layers)
        if (l.side === 'document' && !l.host.isConnected) layers.delete(k);
      sheets.forEach((s, i) => mount(s, 'document', i + 1));
    },
    mountNote(host, p) {
      for (const [k, l] of layers)
        if (l.side === 'note') {
          l.svg.remove();
          layers.delete(k);
        }
      mount(host, 'note', p);
    },
    setPen(type, nextColor, nextWidth) {
      tool = type;
      color = nextColor;
      width = nextWidth;
      paintAll();
    },
    activate(side) {
      finish();
      active = side;
      selected = null;
      paintAll();
    },
    refresh: paintAll,
    flush,
    undo: () => undoRedo(true),
    redo: () => undoRedo(false),
    addText,
    addImage,
    addRich,
    pasteObject,
    copySelection,
    get active() {
      return active;
    },
    get drawing() {
      return !!gesture;
    },
    getState: () => ({ active, tool, items: clone(all) }),
  };
}

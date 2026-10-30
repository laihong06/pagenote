const { BrowserWindow } = require('electron');
const fs = require('node:fs/promises');
const path = require('node:path');
const { randomUUID, createHash } = require('node:crypto');
const { PNG } = require('pngjs');
const revision = (text) => createHash('sha256').update(text).digest('hex');
const idPattern = /^[a-f0-9]{64}$/;

function pastePNG(
  target,
  source,
  x,
  y,
  sourceY = 0,
  height = source.height - sourceY,
) {
  for (let row = 0; row < height; row++)
    source.data.copy(
      target.data,
      ((y + row) * target.width + x) * 4,
      (sourceY + row) * source.width * 4,
      ((sourceY + row) * source.width + source.width) * 4,
    );
}
function pageLabel(png, x, y, page) {
  const digits = [
    '111101101101111',
    '010110010010111',
    '111001111100111',
    '111001111001111',
    '101101111001001',
    '111100111001111',
    '111100111101111',
    '111001001001001',
    '111101111101111',
    '111101111001111',
  ];
  for (const [n, d] of [...String(page)].entries())
    for (let row = 0; row < 5; row++)
      for (let col = 0; col < 3; col++)
        if (digits[+d][row * 3 + col] === '1')
          for (let yy = 0; yy < 2; yy++)
            for (let xx = 0; xx < 2; xx++) {
              const i =
                ((y + row * 2 + yy) * png.width + x + n * 9 + col * 2 + xx) * 4;
              png.data[i] = 40;
              png.data[i + 1] = 66;
              png.data[i + 2] = 104;
              png.data[i + 3] = 255;
            }
}

exports.createAIController = async function ({ win, url, data, word }) {
  let inspector,
    readerSource,
    queue = Promise.resolve();
  const invoke = (window, method, ...args) =>
    window.webContents.executeJavaScript(
      `window.pageNotes.${method}(...${JSON.stringify(args)})`,
    );
  async function readyInspector() {
    if (!inspector || inspector.isDestroyed()) {
      inspector = new BrowserWindow({
        show: false,
        width: 1280,
        height: 900,
        useContentSize: true,
        webPreferences: {
          sandbox: true,
          nodeIntegration: false,
          contextIsolation: true,
          backgroundThrottling: false,
          partition: 'capture-' + randomUUID(),
        },
      });
      inspector.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
      await inspector.loadURL(url + '/?capture=1');
      await inspector.webContents.executeJavaScript('window.pageNotes.ready');
    }
    return inspector;
  }
  async function api(route, options) {
    const response = await fetch(url + route, options);
    const result = await response.json();
    if (!response.ok) throw Error(result.error || '请求失败');
    return result;
  }
  async function activeId(id) {
    if (id) {
      if (!idPattern.test(id)) throw Error('文档 ID 无效');
      return id;
    }
    const state = await invoke(win, 'getState');
    if (!state.document) throw Error('请先打开文档，或者传入 documentId');
    return state.document.id;
  }
  async function inspect(id, page = 1, sourceUrl) {
    const window = await readyInspector();
    const layout = await api('/api/layout');
    await invoke(window, 'prepareCapture', id, page, layout, sourceUrl);
    await invoke(window, 'syncNotes', id);
    await invoke(window, 'waitVisuals');
    return window;
  }
  async function getNotes(id) {
    return api(`/api/documents/${id}/notes`);
  }
  async function readWindow(id) {
    const window = await readyInspector();
    const dir = path.join(data, 'documents', id);
    const meta = JSON.parse(
      await fs.readFile(path.join(dir, 'meta.json'), 'utf8'),
    );
    const file = path.join(
      dir,
      meta.kind === 'pdf' ? 'view.pdf' : 'original' + meta.ext,
    );
    const stat = await fs.stat(file);
    const signature = `${id}:${stat.size}:${stat.mtimeMs}:${stat.ctimeMs}`;
    const changed = readerSource?.signature !== signature;
    const needsOpen =
      changed || (await invoke(window, 'getSummary')).document?.id !== id;
    if (needsOpen) await invoke(window, 'open', id, 1, changed);
    if (needsOpen) {
      // Opening a portable project refreshes its cached source copy. Cache the
      // resulting fingerprint so later reads do not repeatedly reopen it.
      const current = await fs.stat(file);
      readerSource = {
        signature: `${id}:${current.size}:${current.mtimeMs}:${current.ctimeMs}`,
        hash: revision(await fs.readFile(file)),
      };
    }
    return window;
  }
  const sourceRevision = (text) => revision(readerSource.hash + '\n' + text);
  const preparePage = require('./prepared.cjs').createPagePreparer({
    data,
    describe: async (id, page) => {
      const window = await readWindow(id);
      const [source] = await invoke(window, 'readPages', [page], 'source');
      const meta = JSON.parse(
        await fs.readFile(
          path.join(data, 'documents', id, 'meta.json'),
          'utf8',
        ),
      );
      return {
        sourceRevision: sourceRevision(source.sourceText),
        projectRoot: meta.projectRoot,
      };
    },
    snapshot: async (id, page) => {
      const window = await readWindow(id);
      const source = await invoke(window, 'preparePageData', page);
      return { ...source, sourceRevision: sourceRevision(source.nativeText) };
    },
  });
  async function writeNotes(id, updates) {
    await invoke(win, 'lockEditing');
    try {
      const all = await getNotes(id);
      const window = await readWindow(id);
      const { total } = await invoke(window, 'getState');
      const seen = new Set();
      for (const update of updates) {
        if (
          !Number.isInteger(update.page) ||
          update.page < 1 ||
          update.page > total ||
          typeof update.markdown !== 'string'
        )
          throw Error('页码或笔记内容无效');
        if (seen.has(update.page)) throw Error('批量写入不能重复同一页');
        seen.add(update.page);
        if (
          update.expectedRevision &&
          revision(all[update.page] || '') !== update.expectedRevision
        )
          throw Error(`第 ${update.page} 页已发生变化，请重新读取后再修改`);
      }
      const history = path.join(data, 'documents', id, 'history');
      await fs.mkdir(history, { recursive: true });
      for (const update of updates) {
        await fs.writeFile(
          path.join(
            history,
            `${Date.now()}-${randomUUID()}-page${update.page}.md`,
          ),
          all[update.page] || '',
        );
        await api(`/api/documents/${id}/notes/${update.page}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            markdown: update.markdown,
            expectedRevision:
              update.expectedRevision || revision(all[update.page] || ''),
          }),
        });
        // Publish each completed page without navigating the visible window.
        await invoke(win, 'syncNotes', id);
      }
      return {
        documentId: id,
        updated: updates.map((u) => ({
          page: u.page,
          revision: revision(u.markdown),
        })),
        historyDirectory: history,
      };
    } finally {
      await invoke(win, 'unlockEditing');
    }
  }
  async function upload(bytes, name) {
    const form = new FormData();
    form.append('file', new Blob([bytes]), name);
    return api('/api/documents', { method: 'POST', body: form });
  }
  async function capture(
    id,
    page,
    scope = 'workspace',
    fullNote = false,
    maxHeight = 12000,
    startOffset = 0,
    sourceUrl,
  ) {
    const window = await inspect(id, page, sourceUrl);
    page = (await invoke(window, 'getState')).page;
    const cssKey =
      scope === 'note' && fullNote
        ? await window.webContents.insertCSS(
            '#noteScroll{scrollbar-width:none}#noteScroll::-webkit-scrollbar{display:none}',
          )
        : null;
    await invoke(window, 'waitVisuals');
    const metrics = await invoke(window, 'captureMetrics', scope);
    const [vw, vh] = window.getContentSize();
    const rect = {
      x: Math.max(0, Math.floor(metrics.x)),
      y: Math.max(0, Math.floor(metrics.y)),
      width: Math.min(
        vw - Math.max(0, Math.floor(metrics.x)),
        Math.floor(metrics.width),
      ),
      height: Math.min(
        vh - Math.max(0, Math.floor(metrics.y)),
        Math.floor(metrics.height),
      ),
    };
    if (rect.width < 1 || rect.height < 1) throw Error('截图区域为空');
    async function frame() {
      const image = await window.webContents.capturePage(rect, {
        stayHidden: true,
        stayAwake: true,
      });
      return PNG.sync.read(image.toPNG({ scaleFactor: 1 }));
    }
    if (scope !== 'note' || !fullNote)
      return { png: await frame(), page, truncated: false };
    const start = Math.max(0, Math.floor(startOffset));
    if (start >= metrics.scrollHeight) {
      if (cssKey) await window.webContents.removeInsertedCSS(cssKey);
      throw Error('截图起始位置超出笔记长度');
    }
    const desiredHeight = Math.min(maxHeight, metrics.scrollHeight - start);
    let output;
    try {
      for (let offset = 0; offset < desiredHeight; ) {
        const actual = await invoke(window, 'scrollNote', start + offset);
        const tile = await frame();
        const ratio = tile.height / rect.height;
        if (!output) {
          output = new PNG({
            width: tile.width,
            height: Math.ceil(desiredHeight * ratio),
          });
          output.data.fill(255);
        }
        const sourceY = Math.max(
          0,
          Math.round((start + offset - actual) * ratio),
        );
        const rows = Math.min(
          tile.height - sourceY,
          output.height - Math.round(offset * ratio),
        );
        if (rows <= 0) break;
        pastePNG(output, tile, 0, Math.round(offset * ratio), sourceY, rows);
        offset += rows / ratio;
      }
    } finally {
      if (cssKey) await window.webContents.removeInsertedCSS(cssKey);
    }
    return {
      png: output,
      page,
      truncated: metrics.scrollHeight - start > maxHeight,
      nextOffset: start + desiredHeight,
      totalHeight: metrics.scrollHeight,
    };
  }
  async function saveImage(png, details) {
    if (png.width * png.height > 32000000)
      throw Error('截图过大，请减少页数或最大高度');
    const folder = path.join(data, 'screenshots');
    await fs.mkdir(folder, { recursive: true });
    const file = path.join(folder, `${Date.now()}-${randomUUID()}.png`);
    const buffer = PNG.sync.write(png);
    await fs.writeFile(file, buffer);
    return {
      ...details,
      path: file,
      width: png.width,
      height: png.height,
      mimeType: 'image/png',
      imageBase64: buffer.toString('base64'),
    };
  }
  async function perform({ action, args = {} }) {
    await win.webContents.executeJavaScript('window.pageNotes.ready');
    if (action === 'list_documents') return api('/api/documents');
    if (action === 'validate_note') {
      if (typeof args.markdown !== 'string' || args.markdown.length > 200000)
        throw Error('Invalid Markdown or more than 200000 characters');
      return invoke(await readyInspector(), 'validateNote', args.markdown);
    }
    if (action === 'get_state') {
      await invoke(win, 'flush');
      if (args.detail === 'compact') return invoke(win, 'getSummary');
      const state = await invoke(win, 'getState');
      return { ...state, revision: revision(state.markdown) };
    }
    if (action === 'get_layout') return api('/api/layout');
    if (action === 'set_layout') return invoke(win, 'setLayout', args);
    if (action === 'import_document') {
      const file = path.resolve(args.filePath);
      const stat = await fs.stat(file);
      if (!stat.isFile() || stat.size > 200 * 1024 * 1024)
        throw Error('文件无效或超过 200MB');
      const meta = await upload(await fs.readFile(file), path.basename(file));
      if (args.activate !== false)
        await invoke(win, 'open', meta.id, args.page || 1);
      else await invoke(win, 'refreshLibrary');
      return meta;
    }
    if (action === 'create_markdown') {
      const name = args.name.endsWith('.md') ? args.name : args.name + '.md';
      const meta = await upload(
        Buffer.from(args.markdown, 'utf8'),
        path.basename(name),
      );
      if (args.activate !== false) await invoke(win, 'open', meta.id, 1);
      else await invoke(win, 'refreshLibrary');
      return meta;
    }
    const id = await activeId(args.documentId);
    if (action === 'prepare_page') {
      await invoke(win, 'flush');
      const state = await invoke(win, 'getSummary');
      const targetPage =
        args.page ?? (state.document?.id === id ? state.page : 1);
      return preparePage(id, targetPage, args);
    }
    if (action === 'read_ai_reviews')
      return {
        documentId: id,
        records: (await api(`/api/documents/${id}/ai-reviews`)).filter(
          (r) => !args.status || r.status === args.status,
        ),
        guidance:
          '根据 side/kind/scope/quote/context/anchor 理解要求；框选坐标以页面宽度1000为单位。扫描件的 quote 可能为空，请结合截图。笔记修改使用 expectedRevision；Word 修改需先读取当前 Word revision。',
      };
    if (action === 'update_ai_review') {
      const result = await api(
        `/api/documents/${id}/ai-reviews/${args.recordId}`,
        {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            status: args.status,
            instruction: args.instruction,
          }),
        },
      );
      await invoke(win, 'reloadReviews');
      return result;
    }
    if (action === 'read_word' || action === 'patch_word') {
      if (word.active && word.active !== id)
        throw Error('请先结束正在编辑的另一份 Word');
      if (!word.active) {
        await invoke(win, 'open', id, 1);
        await invoke(win, 'beginWord');
      }
      if (action === 'read_word') return word.call('read', { documentId: id });
      const result = await word.call('replace', {
        documentId: id,
        search: args.search,
        replacement: args.replacement,
        start: args.start,
        expectedRevision: args.expectedRevision,
      });
      await invoke(win, 'endWord');
      return { ...result, documentId: id, saved: true };
    }
    if (action === 'navigate') {
      await invoke(win, 'open', id, args.page);
      return invoke(win, 'getSummary');
    }
    if (action === 'read_pages') {
      await invoke(win, 'flush');
      const active = await invoke(win, 'getSummary');
      const requested = args.pages || [
        active.document?.id === id ? active.page : 1,
      ];
      const window = await readWindow(id);
      const include = args.include || 'both';
      const result = await invoke(
        window,
        'readPages',
        requested,
        ['source', 'both'].includes(include) ? 'source' : 'metadata',
      );
      const all = include !== 'source' ? await getNotes(id) : null;
      return {
        documentId: id,
        pages: result.map((p) => {
          const known = args.knownRevisions?.[p.page] || {};
          if ('sourceText' in p) {
            p.sourceRevision = sourceRevision(p.sourceText);
            if (known.source === p.sourceRevision) {
              delete p.sourceText;
              p.sourceUnchanged = true;
            }
          }
          if (all) {
            const markdown = all[p.page] || '';
            if (include !== 'metadata') p.markdown = markdown;
            p.revision = revision(markdown);
            p.noteLength = markdown.length;
            if (known.note === p.revision) {
              delete p.markdown;
              p.noteUnchanged = true;
            } else if ('markdown' in p && args.noteRange) {
              const start = Math.min(args.noteRange.start, markdown.length);
              const end = Math.min(
                start + args.noteRange.maxChars,
                markdown.length,
              );
              p.markdown = markdown.slice(start, end);
              p.range = { start, end, totalChars: markdown.length };
            }
          }
          return p;
        }),
      };
    }
    if (action === 'read_handoff' || action === 'write_handoff') {
      await invoke(win, 'flush');
      const window = await readWindow(id);
      const [source] = await invoke(window, 'readPages', [args.page], 'source');
      const currentSource = sourceRevision(source.sourceText);
      if (action === 'write_handoff') {
        if (args.sourceRevision && args.sourceRevision !== currentSource)
          throw Error('原文已变化，请重新读取后保存交接');
        return api(`/api/documents/${id}/handoffs/${args.page}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            summary: args.summary,
            expectedRevision: args.expectedRevision,
            sourceRevision: currentSource,
          }),
        });
      }
      const result = await api(`/api/documents/${id}/handoffs/${args.page}`);
      if (result.handoff)
        result.stale =
          result.stale || result.handoff.sourceRevision !== currentSource;
      return result;
    }
    if (action === 'write_notes') return writeNotes(id, args.updates);
    if (action === 'patch_note') {
      await invoke(win, 'flush');
      const old = (await getNotes(id))[args.page] || '';
      if (args.expectedRevision && revision(old) !== args.expectedRevision)
        throw Error('笔记已变化，请重新读取');
      let text;
      if (args.append !== undefined)
        text = old + (old ? '\n\n' : '') + args.append;
      else {
        if (typeof args.replacement !== 'string')
          throw Error('替换操作必须提供 replacement');
        if (!args.search || !old.includes(args.search))
          throw Error('没有找到要替换的原文');
        if (args.start !== undefined) {
          if (
            !Number.isInteger(args.start) ||
            args.start < 0 ||
            old.slice(args.start, args.start + args.search.length) !==
              args.search
          )
            throw Error('选区位置与原文不一致');
          text =
            old.slice(0, args.start) +
            args.replacement +
            old.slice(args.start + args.search.length);
        } else
          text = args.replaceAll
            ? old.split(args.search).join(args.replacement)
            : old.replace(args.search, () => args.replacement);
      }
      return writeNotes(id, [
        { page: args.page, markdown: text, expectedRevision: revision(old) },
      ]);
    }
    if (action === 'attach_image') {
      const all = await getNotes(id);
      const name = args.name || 'ai-image.png';
      const bytes = args.filePath
        ? await fs.readFile(path.resolve(args.filePath))
        : Buffer.from(args.base64, 'base64');
      if (
        bytes.length > 20 * 1024 * 1024 ||
        !/^\.(png|jpe?g|gif|webp)$/i.test(path.extname(name))
      )
        throw Error('图片无效或超过 20MB');
      const form = new FormData();
      form.append('file', new Blob([bytes]), name);
      const item = await api('/api/attachments', {
        method: 'POST',
        body: form,
      });
      if (!item.image) throw Error('必须上传图片');
      return writeNotes(id, [
        {
          page: args.page,
          markdown:
            (all[args.page] || '') +
            `\n\n![${name.replace(/[\[\]]/g, '')}](${item.url})\n`,
          expectedRevision: revision(all[args.page] || ''),
        },
      ]);
    }
    if (action === 'screenshot') {
      if (args.scope === 'word') {
        if (word.active !== id)
          throw Error('请先使用 read_word 打开对应 Word 编辑器');
        if (word.captureKind === 'pdf') {
          const preview = await word.call('export-preview', { documentId: id });
          const selected = await word.call('selection', { documentId: id });
          readerSource = undefined;
          try {
            const result = await capture(
              id,
              selected.page || 1,
              'document',
              false,
              12000,
              0,
              preview.url,
            );
            return saveImage(result.png, {
              documentId: id,
              page: result.page,
              scope: 'word',
              renderedFrom: 'pdf',
              truncated: false,
            });
          } finally {
            // The inspector temporarily rendered an uncommitted working copy.
            // Force later source reads to reopen the committed document.
            readerSource = undefined;
          }
        }
        return word.call('screenshot', { documentId: id });
      }
      await invoke(win, 'flush');
      const active = await invoke(win, 'getState');
      const targetPage =
        args.page ?? (active.document?.id === id ? active.page : 1);
      const result = await capture(
        id,
        targetPage,
        args.scope || 'workspace',
        args.fullNote || false,
        args.maxHeight || 12000,
        args.startOffset || 0,
      );
      return saveImage(result.png, {
        documentId: id,
        page: result.page,
        scope: args.scope || 'workspace',
        truncated: result.truncated,
        nextOffset: result.nextOffset,
        totalHeight: result.totalHeight,
      });
    }
    if (action === 'contact_sheet') {
      await invoke(win, 'flush');
      const tiles = [];
      for (const p of args.pages) {
        const result = await capture(id, p, args.scope || 'workspace');
        tiles.push(result.png);
      }
      const columns = Math.min(args.columns || 2, tiles.length),
        width = Math.max(...tiles.map((p) => p.width)),
        height = Math.max(...tiles.map((p) => p.height)) + 24,
        gap = 12;
      const rows = Math.ceil(tiles.length / columns);
      if ((width + gap) * columns * (height + gap) * rows > 32000000)
        throw Error('拼图过大，请减少页数或列数');
      const png = new PNG({
        width: columns * (width + gap) + gap,
        height: rows * (height + gap) + gap,
      });
      png.data.fill(245);
      for (let i = 3; i < png.data.length; i += 4) png.data[i] = 255;
      for (let i = 0; i < tiles.length; i++) {
        const x = gap + (i % columns) * (width + gap),
          y = gap + Math.floor(i / columns) * (height + gap);
        pageLabel(png, x + 8, y + 5, args.pages[i]);
        pastePNG(png, tiles[i], x, y + 24);
      }
      return saveImage(png, {
        documentId: id,
        pages: args.pages,
        columns,
        scope: args.scope || 'workspace',
        cells: args.pages.map((page, i) => ({
          page,
          column: i % columns,
          row: Math.floor(i / columns),
        })),
      });
    }
    throw Error('未知 AI 操作');
  }
  return {
    call: (body) => {
      const job = queue.then(() => perform(body));
      queue = job.catch(() => {});
      return job;
    },
    dispose: () => {
      if (inspector && !inspector.isDestroyed()) inspector.destroy();
    },
  };
};

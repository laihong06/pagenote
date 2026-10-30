import { initializeLanguage, getLanguage, setLanguage, t } from './i18n.js';
import './style.css';
import './desktop.css';
import 'katex/dist/katex.min.css';
import * as pdfjs from 'pdfjs-dist';
import worker from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import { renderMarkdown, validateNote } from './markdown.js';
import { createAnnotations } from './annotations.js';
import { setupMarkdownTools, htmlToMarkdown } from './editing.js';
import { createReviews } from './reviews.js';
import { createAITools } from './ai-tools.js';
import { createRichEditor } from './rich-editor.js';
import { preparePageData } from './page-data.js';

pdfjs.GlobalWorkerOptions.workerSrc = worker;
const $ = (id) => document.getElementById(id);
let layout = {
    noteWidth: 1000,
    padding: 28,
    fontSize: 15,
    lineHeight: 1.9,
    leftPercent: 42,
    customCSS: '',
    noteAnimation: true,
    expandEditor: true,
  },
  noteAnimation,
  returnSplit = 42,
  focusFreeze = 0;
function applyLayout(value) {
  layout = { ...layout, ...value };
  const style = document.documentElement.style;
  for (const [name, value] of Object.entries({
    '--note-width': layout.noteWidth + 'px',
    '--note-padding': layout.padding + 'px',
    '--note-font-size': layout.fontSize + 'px',
    '--note-line-height': layout.lineHeight,
    '--left': layout.leftPercent + '%',
  }))
    style.setProperty(name, String(value));
  let custom = $('customLayout');
  if (!custom) {
    custom = document.createElement('style');
    custom.id = 'customLayout';
    document.head.append(custom);
  }
  custom.textContent = layout.customCSS;
  if ($('noteAnimationSetting'))
    $('noteAnimationSetting').checked = layout.noteAnimation;
  if ($('expandEditorSetting'))
    $('expandEditorSetting').checked = layout.expandEditor;
  if ($('noteFontTool')) {
    const value = String(layout.fontSize);
    if (![...$('noteFontTool').options].some((o) => o.value === value))
      $('noteFontTool').add(new Option(value, value));
    $('noteFontTool').value = value;
  }
  if (!layout.noteAnimation) {
    noteAnimation?.cancel();
    noteAnimation = null;
  }
  growEditor();
}
function growEditor() {
  const e = $('editor');
  if (!e || e.disabled) return;
  e.style.height = 'auto';
  e.style.height = Math.max(480, e.scrollHeight + 2) + 'px';
}
let doc = null,
  pdf = null,
  mdPages = [],
  page = 1,
  total = 0,
  notes = {},
  zoom = 1,
  mode = 'read',
  saveTimer,
  toastTimer,
  saving = Promise.resolve(),
  dirty = null,
  aiEditing = false;
const noteBases = new Map(),
  pendingNoteSaves = new Map();
const noteKey = (id, p) => `${id}:${p}`;
async function noteRevision(text) {
  if (text === null) return '0'.repeat(64); // Legacy drafts have an unknown base.
  const bytes = await crypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(text),
  );
  return Array.from(new Uint8Array(bytes), (b) =>
    b.toString(16).padStart(2, '0'),
  ).join('');
}
function noteSaveStatus(text, conflict = false, failed = false) {
  $('saveStatus').textContent = text;
  $('saveStatus').title = text;
  $('resolveNoteConflict').hidden = !conflict;
  $('retryNoteSave').hidden = !failed;
}
let wordActive = false,
  wordBusy = false,
  wordFinishing = false,
  reviews,
  rich,
  aiTools,
  documentMarkdownEditing = false;
const annotations = createAnnotations({
  request,
  toast,
  getDocument: () => doc,
  getPage: () => page,
  onSaveStatus: (text) => {
    if (!dirty && pendingNoteSaves.size === 0)
      $('saveStatus').textContent = text;
  },
  onRegion: (region) => {
    if (document.body.dataset.aiPointer === 'rect')
      document.dispatchEvent(new CustomEvent('ai-box-tag', { detail: region }));
    else reviews?.region(region);
  },
});
function expandPanel(side) {
  if (
    !doc ||
    !layout.expandEditor ||
    new URLSearchParams(location.search).has('capture')
  )
    return;
  if (!$('workspace').dataset.focus)
    returnSplit = parseFloat(
      getComputedStyle(document.documentElement).getPropertyValue('--left'),
    );
  focusFreeze = Date.now() + 650;
  $('workspace').dataset.focus = side;
}
function restorePanels() {
  if (!$('workspace').dataset.focus) return;
  focusFreeze = Date.now() + 650;
  delete $('workspace').dataset.focus;
  document.documentElement.style.setProperty('--left', returnSplit + '%');
}
function editTools() {
  const ink = annotations.active;
  $('inkTools').hidden = !ink || ink === 'both';
  $('markdownTools').hidden = !!ink || mode === 'read' || wordActive;
  $('inkSideLabel').textContent = ink === 'document' ? '原文批注' : '笔记批注';
  $('documentInkButton').classList.toggle('selected', ink === 'document');
  $('noteInkButton').classList.toggle('selected', ink === 'note');
  document.querySelector('.command-area').dataset.editing =
    wordActive || wordFinishing ? 'word' : mode === 'read' ? 'read' : 'note';
  $('wordTools').hidden = !wordActive && !wordFinishing;
  $('wordDone').disabled = wordBusy;
  $('wordSelectionReview').disabled = wordBusy;
  aiTools?.refresh();
  document.body.classList.toggle(
    'ai-reading',
    mode === 'read' && !wordActive && !documentMarkdownEditing,
  );
}
function toast(message) {
  $('toast').textContent = message;
  $('toast').hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => ($('toast').hidden = true), 6500);
}
async function request(url, options) {
  const res = await fetch(url, options);
  if (!res.ok) {
    let info;
    try {
      info = await res.json();
    } catch {}
    throw Object.assign(Error(info?.error || `请求失败 ${res.status}`), {
      status: res.status,
      details: info,
    });
  }
  return res.json();
}
function updatePreview() {
  const text = $('editor').value;
  if (rich) rich.load(text);
  else renderMarkdown(text, $('preview'));
  $('wordCount').textContent =
    `${text.replace(/\s/g, '').length.toLocaleString()} 字`;
  growEditor();
}
function setMode(next) {
  mode = next;
  annotations.activate(null);
  $('noteBody').className = 'mode-' + mode;
  document.querySelectorAll('[data-mode]').forEach((b) => {
    b.classList.toggle('selected', b.dataset.mode === mode);
    b.setAttribute('aria-pressed', String(b.dataset.mode === mode));
  });
  if (mode === 'read') restorePanels();
  else expandPanel('note');
  updateControls();
  $('noteEditButton').textContent = mode === 'edit' ? '完成编辑' : '编辑';
  editTools();
  growEditor();
  annotations.refresh();
  if (mode === 'edit') $('preview').focus();
}
function recoveryKey(id, p) {
  return `page-notes:${id}:${p}`;
}
function editChanged(fromRich = false) {
  if (!doc) return;
  const text = $('editor').value;
  notes[page] = text;
  dirty = { id: doc.id, page, text };
  try {
    localStorage.setItem(recoveryKey(doc.id, page), text);
    const base = noteBases.get(noteKey(doc.id, page));
    if (base !== null && base !== undefined)
      localStorage.setItem(recoveryKey(doc.id, page) + ':base', base);
  } catch {}
  noteSaveStatus('等待保存…');
  if (!fromRich) updatePreview();
  else
    $('wordCount').textContent =
      `${text.replace(/\s/g, '').length.toLocaleString()} 字`;
  clearTimeout(saveTimer);
  saveTimer = setTimeout(
    () => flush(false).catch((e) => toast(e.message)),
    500,
  );
}
async function flush(finishDraft = true) {
  await annotations.flush(finishDraft);
  clearTimeout(saveTimer);
  if (!dirty) {
    await saving;
    return;
  }
  const snapshot = dirty;
  const key = recoveryKey(snapshot.id, snapshot.page),
    baseKey = noteKey(snapshot.id, snapshot.page);
  pendingNoteSaves.set(baseKey, (pendingNoteSaves.get(baseKey) || 0) + 1);
  dirty = null;
  const job = saving
    .catch(() => {})
    .then(async () => {
      try {
        noteSaveStatus('正在保存…');
        await request(`/api/documents/${snapshot.id}/notes/${snapshot.page}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            markdown: snapshot.text,
            expectedRevision: await noteRevision(
              noteBases.get(baseKey) ?? (noteBases.has(baseKey) ? null : ''),
            ),
          }),
        });
        noteBases.set(baseKey, snapshot.text);
        if (
          dirty?.id === snapshot.id &&
          dirty.page === snapshot.page &&
          dirty.text === snapshot.text
        )
          dirty = null;
        try {
          if (
            localStorage.getItem(key) === snapshot.text ||
            (doc?.id === snapshot.id && notes[snapshot.page] === snapshot.text)
          ) {
            localStorage.removeItem(key);
            localStorage.removeItem(key + ':base');
          } else if (localStorage.getItem(key) !== null) {
            localStorage.setItem(key + ':base', snapshot.text);
          }
        } catch {
          /* A recovery-cache failure must not undo an acknowledged save. */
        }
        if (
          doc?.id === snapshot.id &&
          !dirty &&
          pendingNoteSaves.get(baseKey) === 1
        )
          noteSaveStatus('已保存到本地');
      } catch (e) {
        if (!dirty)
          dirty = {
            ...snapshot,
            text:
              doc?.id === snapshot.id
                ? (notes[snapshot.page] ?? snapshot.text)
                : snapshot.text,
          };
        const conflict = e.details?.code === 'NOTE_CONFLICT';
        let hasRecovery = false;
        try {
          hasRecovery = localStorage.getItem(key) === dirty.text;
        } catch {}
        noteSaveStatus(
          conflict
            ? '保存冲突 · 草稿已备份到历史'
            : hasRecovery
              ? '保存失败 · 已保留本地恢复副本'
              : '保存失败 · 草稿仍在编辑区，请导出备份',
          conflict,
          !conflict,
        );
        throw e;
      } finally {
        const count = pendingNoteSaves.get(baseKey) - 1;
        if (count) pendingNoteSaves.set(baseKey, count);
        else pendingNoteSaves.delete(baseKey);
      }
    });
  saving = job;
  await job;
}
$('retryNoteSave').onclick = () => flush().catch((e) => toast(e.message));
$('resolveNoteConflict').onclick = async () => {
  try {
    await flush();
  } catch (e) {
    if (e.details?.code !== 'NOTE_CONFLICT') {
      toast(e.message);
      return;
    }
    $('noteConflictDraft').value = dirty?.text || '';
    $('noteConflictSaved').value = e.details.currentMarkdown;
    $('noteConflictDialog').showModal();
  }
};
$('closeNoteConflict').onclick = () => $('noteConflictDialog').close();
$('downloadConflictDraft').onclick = () =>
  download(
    `草稿-第${page}页.md`,
    $('noteConflictDraft').value,
    'text/markdown',
  );
$('reloadConflictSaved').onclick = async () => {
  $('reloadConflictSaved').disabled = true;
  try {
    // Confirm the latest draft has reached history before clearing recovery data.
    try {
      await flush();
    } catch (e) {
      if (e.details?.code !== 'NOTE_CONFLICT') throw e;
    }
    const snapshot = dirty;
    if (!snapshot) {
      $('noteConflictDialog').close();
      return;
    }
    const incoming = await request(`/api/documents/${snapshot.id}/notes`);
    if (doc?.id !== snapshot.id || page !== snapshot.page || dirty !== snapshot)
      throw Error('编辑内容已变化，请重新查看保存冲突');
    notes = incoming;
    saving = Promise.resolve();
    for (const [p, text] of Object.entries(incoming))
      noteBases.set(noteKey(doc.id, p), text);
    noteBases.set(noteKey(doc.id, page), incoming[page] || '');
    dirty = null;
    localStorage.removeItem(recoveryKey(doc.id, page));
    localStorage.removeItem(recoveryKey(doc.id, page) + ':base');
    loadNote();
    noteSaveStatus('已载入保存内容 · 草稿可在历史版本中查看');
    $('noteConflictDialog').close();
  } catch (e) {
    toast(e.message);
  } finally {
    $('reloadConflictSaved').disabled = false;
  }
};
function updateControls() {
  const active = !!doc;
  $('noteEditButton').disabled = !active || wordBusy;
  $('openButton').disabled = wordBusy;
  $('library').disabled = wordBusy;
  $('prev').disabled = !active || page <= 1;
  $('next').disabled = !active || page >= total;
  $('pageNumber').disabled = !active;
  $('pageNumber').max = total || 1;
  $('pageNumber').value = page;
  $('pageCount').textContent = `/ ${total || '—'}`;
  $('pageDescription').textContent = active
    ? `第 ${page} 页 · 共 ${total} 页`
    : '每一页，都有自己的笔记';
  $('noteTitle').textContent = active
    ? `第 ${page} 页 · 知识与讲解`
    : '本页笔记';
  $('editor').disabled = !active || aiEditing || wordBusy;
  $('preview').contentEditable = String(
    active &&
      mode === 'edit' &&
      !aiEditing &&
      !wordBusy &&
      !new URLSearchParams(location.search).has('capture'),
  );
  rich?.setEditable(
    active &&
      mode === 'edit' &&
      !aiEditing &&
      !wordBusy &&
      !new URLSearchParams(location.search).has('capture'),
  );
  $('preview').dataset.placeholder =
    '直接输入或粘贴讲解，点击工具栏插入代码、公式和表格。';
  [
    'attachment',
    'importNote',
    'historyButton',
    'documentInkButton',
    'noteInkButton',
  ].forEach((id) => ($(id).disabled = !active || aiEditing));
  $('exportNote').disabled = !active;
  ['zoomIn', 'zoomOut', 'fit'].forEach((id) => ($(id).disabled = !active));
  document.querySelectorAll('[data-mode]').forEach((b) => {
    b.disabled = !active || wordBusy;
    b.setAttribute('aria-pressed', String(b.dataset.mode === mode));
  });
}
function loadNote(direction = 0) {
  let text = notes[page] || '';
  const key = noteKey(doc.id, page);
  if (!noteBases.has(key)) noteBases.set(key, text);
  const restored = localStorage.getItem(recoveryKey(doc.id, page));
  if (restored !== null) {
    const base = localStorage.getItem(recoveryKey(doc.id, page) + ':base');
    noteBases.set(key, base === null && restored === text ? text : base);
    text = restored;
    notes[page] = text;
    dirty = { id: doc.id, page, text };
    flush().catch((e) => toast(e.message));
  }
  $('editor').value = text;
  updatePreview();
  $('noteScroll').scrollTop = 0;
  $('editor').scrollTop = 0;
  annotations.mountNote($('noteBody'), page);
  reviews?.refresh();
  noteAnimation?.cancel();
  noteAnimation = null;
  if (
    direction &&
    layout.noteAnimation &&
    !new URLSearchParams(location.search).has('capture')
  )
    noteAnimation = $('noteBody').animate(
      [
        {
          opacity: 0.25,
          transform: `translateY(${direction > 0 ? 14 : -14}px)`,
        },
        { opacity: 1, transform: 'translateY(0)' },
      ],
      { duration: 240, easing: 'cubic-bezier(.22,1,.36,1)' },
    );
}
let pageSheets = [],
  pageTasks = new Map(),
  documentBuild = 0,
  scrollFrame = 0,
  switching = false,
  scrollSuspended = false,
  lastDocumentScroll = 0,
  userScrollUntil = 0;
function currentSheet() {
  return pageSheets[page - 1];
}
function documentZoomAnchor(event) {
  const scroller = $('documentScroll'),
    sheet = event?.target.closest('.document-sheet') || currentSheet();
  if (!sheet) return null;
  const r = sheet.getBoundingClientRect(),
    s = scroller.getBoundingClientRect();
  const filled =
    r.width >= scroller.clientWidth - 57 ||
    r.height >= scroller.clientHeight - 57;
  const pointer = event && filled;
  const x = pointer
    ? Math.max(r.left, Math.min(r.right, event.clientX))
    : r.left + r.width / 2;
  const y = pointer
    ? Math.max(r.top, Math.min(r.bottom, event.clientY))
    : r.top + r.height / 2;
  return {
    page: Number(sheet.dataset.page),
    x: (x - r.left) / r.width,
    y: (y - r.top) / r.height,
    screenX: x - s.left,
    screenY: y - s.top,
  };
}
function markCurrentSheet() {
  document
    .querySelectorAll('#pdfCanvas,#markdownDocument')
    .forEach((e) => e.removeAttribute('id'));
  const sheet = currentSheet();
  if (sheet)
    sheet.firstElementChild.id =
      doc.kind === 'pdf' ? 'pdfCanvas' : 'markdownDocument';
}
async function paintSheet(index) {
  const sheet = pageSheets[index];
  if (!sheet || sheet.dataset.painted || pageTasks.has(sheet)) return;
  const epoch = documentBuild;
  const child = sheet.firstElementChild;
  if (doc.kind === 'markdown') {
    sheet.dataset.painted = '1';
    renderMarkdown(mdPages[index], child);
    return;
  }
  const source = pdf;
  const p = await source.getPage(index + 1);
  if (epoch !== documentBuild || !sheet.isConnected || pageTasks.has(sheet))
    return;
  const viewport = p.getViewport({ scale: Number(sheet.dataset.scale) }),
    ratio = window.devicePixelRatio || 1;
  child.width = Math.floor(viewport.width * ratio);
  child.height = Math.floor(viewport.height * ratio);
  const task = p.render({
    canvasContext: child.getContext('2d'),
    viewport,
    transform: ratio === 1 ? null : [ratio, 0, 0, ratio, 0, 0],
  });
  pageTasks.set(sheet, task);
  try {
    await task.promise;
    if (epoch === documentBuild) sheet.dataset.painted = '1';
  } catch (e) {
    if (e.name !== 'RenderingCancelledException') throw e;
  } finally {
    if (pageTasks.get(sheet) === task) pageTasks.delete(sheet);
  }
  if (epoch === documentBuild && !sheet.querySelector('.textLayer')) {
    const layer = document.createElement('div');
    layer.className = 'textLayer';
    layer.style.setProperty('--total-scale-factor', sheet.dataset.scale);
    sheet.append(layer);
    await new pdfjs.TextLayer({
      textContentSource: await p.getTextContent(),
      container: layer,
      viewport,
    }).render();
  }
}
function visiblePage() {
  const scroller = $('documentScroll'),
    middle = scroller.getBoundingClientRect().top + scroller.clientHeight / 2;
  for (let i = 0; i < pageSheets.length - 1; i++) {
    const bottom = pageSheets[i].getBoundingClientRect().bottom,
      top = pageSheets[i + 1].getBoundingClientRect().top;
    if (middle < (bottom + top) / 2) return i + 1;
  }
  return total;
}
async function syncScrollPage() {
  if (
    switching ||
    scrollSuspended ||
    !doc ||
    !pageSheets.length ||
    $('workspace').dataset.focus === 'note' ||
    Date.now() < focusFreeze ||
    Date.now() > userScrollUntil
  )
    return;
  const next = visiblePage();
  if (next === page) return;
  switching = true;
  try {
    await flush();
    if (scrollSuspended) return;
    const previous = page;
    page = visiblePage();
    localStorage.setItem('last-page:' + doc.id, String(page));
    markCurrentSheet();
    loadNote(page - previous);
    updateControls();
  } finally {
    switching = false;
  }
}
function paintVisible() {
  const scroller = $('documentScroll'),
    bounds = scroller.getBoundingClientRect();
  pageSheets.forEach((sheet, i) => {
    const r = sheet.getBoundingClientRect();
    if (
      r.bottom > bounds.top - scroller.clientHeight &&
      r.top < bounds.bottom + scroller.clientHeight
    )
      paintSheet(i).catch((e) => toast(e.message));
  });
}
async function endMarkdownDocument() {
  if (!documentMarkdownEditing) return;
  const root = currentSheet()?.querySelector('.markdown-page');
  if (root) {
    mdPages[page - 1] = htmlToMarkdown(root.innerHTML);
    root.contentEditable = 'false';
    root.oninput = null;
  }
  doc = await request('/api/documents/' + doc.id + '/markdown', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ markdown: mdPages.join('\n\n---\n\n') }),
  });
  documentMarkdownEditing = false;
  document.body.classList.remove('document-markdown-editing');
  $('documentEditButton').textContent = '编辑';
  restorePanels();
  editTools();
  await renderPage();
}
async function renderPage(anchor = null) {
  if (documentMarkdownEditing) return;
  const epoch = ++documentBuild;
  $('zoomLabel').textContent = Math.round(zoom * 100) + '%';
  if (!doc) return;
  scrollSuspended = true;
  userScrollUntil = 0;
  const scroller = $('documentScroll'),
    old = currentSheet();
  const relative = old
    ? (scroller.scrollTop + scroller.clientHeight / 2 - old.offsetTop) /
      Math.max(1, old.offsetHeight)
    : 0;
  const canceled = [...pageTasks.values()];
  for (const task of canceled) task.cancel();
  await Promise.all(canceled.map((task) => task.promise.catch(() => {})));
  if (epoch !== documentBuild) return;
  pageTasks.clear();
  const sheets = [],
    availableW = Math.max(250, scroller.clientWidth - 56),
    availableH = Math.max(250, scroller.clientHeight - 56);
  for (let i = 0; i < total; i++) {
    const sheet =
      pageSheets[i]?.dataset.documentId === doc.id
        ? pageSheets[i]
        : document.createElement('section');
    sheet.className = 'document-sheet';
    sheet.dataset.page = i + 1;
    sheet.dataset.documentId = doc.id;
    const child =
      sheet.firstElementChild ||
      document.createElement(doc.kind === 'pdf' ? 'canvas' : 'article');
    if (!sheet.firstElementChild) sheet.append(child);
    sheet.querySelector('.textLayer')?.remove();
    delete sheet.dataset.painted;
    if (doc.kind === 'pdf') {
      const p = await pdf.getPage(i + 1);
      if (epoch !== documentBuild) return;
      const base = p.getViewport({ scale: 1 }),
        scale = Math.max(
          0.15,
          Math.min(availableW / base.width, availableH / base.height) * zoom,
        ),
        view = p.getViewport({ scale });
      sheet.dataset.scale = scale;
      sheet.style.width = view.width + 'px';
      sheet.style.height = view.height + 'px';
      child.style.width = view.width + 'px';
      child.style.height = view.height + 'px';
    } else {
      sheet.style.width = availableW * zoom + 'px';
      child.className = 'prose markdown-page';
      renderMarkdown(mdPages[i], child);
      sheet.dataset.painted = '1';
    }
    sheets.push(sheet);
  }
  if (epoch !== documentBuild) return;
  pageSheets = sheets;
  if (
    sheets.length !== $('paper').children.length ||
    sheets.some((s, i) => $('paper').children[i] !== s)
  )
    $('paper').replaceChildren(...sheets);
  $('paper').style.width = 'max-content';
  $('paper').hidden = false;
  markCurrentSheet();
  annotations.mountDocument(sheets);
  const edge = Math.max(
    28,
    (scroller.clientHeight - currentSheet().offsetHeight) / 2,
  );
  scroller.style.paddingTop = edge + 'px';
  scroller.style.paddingBottom = edge + 'px';
  const target = anchor && pageSheets[anchor.page - 1];
  scroller.scrollTo({
    top: Math.max(
      0,
      target
        ? target.offsetTop + anchor.y * target.offsetHeight - anchor.screenY
        : currentSheet().offsetTop +
            (old
              ? relative * currentSheet().offsetHeight -
                scroller.clientHeight / 2
              : -edge),
    ),
    left: target
      ? target.offsetLeft + anchor.x * target.offsetWidth - anchor.screenX
      : scroller.scrollLeft,
    behavior: 'instant',
  });
  await paintSheet(page - 1);
  if (epoch !== documentBuild) return;
  scrollSuspended = false;
  paintVisible();
  reviews?.refresh();
}
async function goTo(next, smooth = false) {
  if (!doc) return;
  next = Math.max(1, Math.min(total, Math.floor(Number(next) || 1)));
  if (smooth && $('workspace').dataset.focus === 'note')
    return goTo(next, false);
  const sheet = pageSheets[next - 1];
  if (!sheet) return;
  const targetTop =
    sheet.offsetTop -
    Math.max(28, ($('documentScroll').clientHeight - sheet.offsetHeight) / 2);
  if (smooth) {
    focusFreeze = 0;
    userScrollUntil = Date.now() + 3000;
    $('documentScroll').scrollTo({ top: targetTop, behavior: 'smooth' });
    return;
  }
  userScrollUntil = 0;
  scrollSuspended = true;
  try {
    await flush();
    const previous = page;
    page = next;
    localStorage.setItem('last-page:' + doc.id, String(page));
    markCurrentSheet();
    loadNote(page - previous);
    updateControls();
    $('documentScroll').scrollTo({ top: targetTop, behavior: 'instant' });
    await paintSheet(page - 1);
  } finally {
    scrollSuspended = false;
  }
  paintVisible();
}
let documentOpening = 0;
async function openDocument(meta, fromWord = false) {
  documentOpening++;
  try {
    return await performOpenDocument(meta, fromWord);
  } finally {
    documentOpening--;
  }
}
async function performOpenDocument(meta, fromWord = false) {
  if (wordBusy && !fromWord) await waitWordIdle();
  if (wordActive) await endWord();
  await flush();
  if (meta.projectFile && !new URLSearchParams(location.search).has('capture'))
    meta = await request('/api/projects/open', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ filePath: meta.projectFile }),
    });
  $('loading').hidden = false;
  try {
    let newPdf = null,
      newPages = [];
    if (meta.kind === 'pdf')
      newPdf = await pdfjs.getDocument({
        enableScripting: false,
        isEvalSupported: false,
        url:
          meta.url +
          (meta.modified ? '?v=' + encodeURIComponent(meta.modified) : ''),
        cMapUrl: '/pdf-assets/cmaps/',
        cMapPacked: true,
        standardFontDataUrl: '/pdf-assets/standard_fonts/',
        wasmUrl: '/pdf-assets/wasm/',
      }).promise;
    else
      newPages = (await (await fetch(meta.url)).text()).split(
        /\n\s*(?:---|<!--\s*pagebreak\s*-->)\s*\r?\n/g,
      );
    const newNotes = await request(`/api/documents/${meta.id}/notes`);
    scrollSuspended = true;
    for (const task of pageTasks.values()) task.cancel();
    pageTasks.clear();
    pageSheets = [];
    if (pdf) await pdf.loadingTask.destroy();
    doc = meta;
    pdf = newPdf;
    mdPages = newPages;
    notes = newNotes;
    for (const key of noteBases.keys())
      if (key.startsWith(meta.id + ':')) noteBases.delete(key);
    for (const [p, text] of Object.entries(newNotes))
      noteBases.set(noteKey(meta.id, p), text);
    total = pdf?.numPages || mdPages.length;
    page = Math.min(
      total,
      Math.max(1, Number(localStorage.getItem('last-page:' + doc.id)) || 1),
    );
    zoom = 1;
    await annotations.load(doc.id);
    setMode('read');
    $('welcome').hidden = true;
    $('docName').textContent = meta.name;
    $('documentType').textContent =
      meta.ext.slice(1).toUpperCase() + ' · 连续滚动';
    $('library').value = meta.id;
    localStorage.setItem('last-document', meta.id);
    loadNote();
    updateControls();
    await renderPage();
    if (!dirty && pendingNoteSaves.size === 0)
      noteSaveStatus('已打开 · 本地保存');
    await reviews.reload(doc.id);
    $('documentEditButton').disabled = false;
    $('documentEditButton').title =
      meta.kind === 'markdown'
        ? t('编辑 Markdown 文稿')
        : t('使用系统默认应用编辑源文件');
  } finally {
    $('loading').hidden = true;
  }
}
async function refreshLibrary() {
  const docs = await request('/api/documents');
  const selected = doc?.id || $('library').value;
  $('library').replaceChildren(
    new Option('最近文档', ''),
    ...docs.map((d) => new Option(d.name, d.id)),
  );
  if (docs.some((d) => d.id === selected)) $('library').value = selected;
  return docs;
}
async function importFile(file) {
  if (!file) return;
  await endMarkdownDocument();
  if (wordBusy) await waitWordIdle();
  if (wordActive) await endWord();
  await flush();
  $('loading').hidden = false;
  $('loading').textContent = /\.(docx?|pptx?)$/i.test(file.name)
    ? '正在通过 Office 转换并分页，请稍候…'
    : '正在解析文档…';
  try {
    const form = new FormData();
    form.append('file', file);
    if (doc?.projectRoot) form.append('projectId', doc.id);
    const meta = await request('/api/documents', {
      method: 'POST',
      body: form,
    });
    await refreshLibrary();
    await openDocument(meta);
  } finally {
    $('loading').hidden = true;
    $('fileInput').value = '';
  }
}
function insertText(text) {
  if (aiEditing) {
    toast('AI 正在更新笔记，请稍后再插入内容');
    return;
  }
  if (mode !== 'edit') setMode('edit');
  rich.insertMarkdown(text);
}
async function attach(file) {
  if (!doc || !file) return;
  await flush();
  const targetId = doc.id,
    targetPage = page;
  const form = new FormData();
  form.append('file', file);
  const item = await request('/api/attachments', {
    method: 'POST',
    body: form,
  });
  if (doc.id !== targetId || page !== targetPage) {
    toast('页面已切换，请在原页重新插入附件');
    return;
  }
  const label = item.name.replace(/[\[\]]/g, '');
  insertText(
    `\n${item.image || /\.(md|markdown)$/i.test(item.name) ? '!' : ''}[${label}](${item.url})\n`,
  );
}
function download(name, text, type = 'text/plain') {
  const url = URL.createObjectURL(new Blob([text], { type }));
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function safely(fn) {
  return (...args) =>
    Promise.resolve()
      .then(() => fn(...args))
      .catch((e) => toast(e.message));
}
const wordCall = (action, args = {}) =>
  request('/api/desktop/word', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ action, args }),
  });
function wordRect() {
  const r = $('documentScroll').getBoundingClientRect();
  return {
    x: r.x,
    y: r.y,
    width: r.width,
    height: r.height,
    scale: devicePixelRatio,
  };
}
async function resizeWord() {
  if (wordActive && !wordBusy) await wordCall('resize', { rect: wordRect() });
}
async function waitWordIdle() {
  const deadline = Date.now() + 120000;
  while (wordBusy && Date.now() < deadline)
    await new Promise((r) => setTimeout(r, 50));
  if (wordBusy) throw Error('Word 操作尚未完成');
}
async function beginWord() {
  if (!doc || wordActive || wordBusy) return;
  wordBusy = true;
  updateControls();
  try {
    await flush();
    setMode('read');
    expandPanel('document');
    await new Promise((resolve) => setTimeout(resolve, 320));
    const opened = await wordCall('open', {
      documentId: doc.id,
      rect: wordRect(),
    });
    wordActive = true;
    document.body.classList.toggle('word-active', opened.embedded !== false);
    if (opened.embedded === false) restorePanels();
    editTools();
    await new Promise((resolve) => requestAnimationFrame(resolve));
    await wordCall('resize', { rect: wordRect() });
    $('saveStatus').textContent = 'Word 编辑中 · 结束后更新阅读视图';
  } finally {
    wordBusy = false;
    updateControls();
    editTools();
  }
}
async function endWord() {
  if (!wordActive || wordBusy) return;
  wordBusy = true;
  wordFinishing = true;
  updateControls();
  editTools();
  try {
    const closed = await wordCall('close', { save: true });
    wordActive = false;
    document.body.classList.remove('word-active');
    editTools();
    restorePanels();
    if (doc?.projectRoot && !closed.projectPersisted)
      await request('/api/projects/' + doc.id + '/sync', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ right: false }),
      });
    const list = await refreshLibrary(),
      meta = list.find((d) => d.id === doc.id);
    await openDocument(meta, true);
    toast('Word 内容已保存，阅读视图已更新');
  } finally {
    wordBusy = false;
    wordFinishing = false;
    updateControls();
    editTools();
  }
}
new ResizeObserver(() => resizeWord().catch((e) => toast(e.message))).observe(
  $('documentScroll'),
);
setInterval(() => {
  if (wordActive && !wordBusy)
    wordCall('status')
      .then((status) => {
        if (!status.alive) return endWord();
      })
      .catch((e) => toast(e.message));
}, 1500);
new MutationObserver(() => {
  if (wordActive)
    wordCall('visibility', {
      visible: !document.querySelector('dialog[open]'),
    }).catch((e) => toast(e.message));
}).observe(document.body, {
  subtree: true,
  attributes: true,
  attributeFilter: ['open'],
});
$('documentEditButton').onclick = safely(async () => {
  if (doc?.kind === 'markdown') {
    if (documentMarkdownEditing) {
      await endMarkdownDocument();
      return;
    }
    documentMarkdownEditing = true;
    document.body.classList.add('document-markdown-editing');
    annotations.activate(null);
    expandPanel('document');
    aiTools.refresh();
    document.body.classList.remove('ai-reading');
    const root = currentSheet().querySelector('.markdown-page');
    root.contentEditable = 'true';
    root.focus();
    root.oninput = () => {
      mdPages[page - 1] = htmlToMarkdown(root.innerHTML);
    };
    $('documentEditButton').textContent = '完成编辑';
  } else {
    await flush();
    await request('/api/documents/' + doc.id + '/open-external', {
      method: 'POST',
    });
    toast(t('已在默认应用打开。保存后回到 pagenote 会更新文档。'));
  }
});
$('wordDone').onclick = safely(endWord);
$('wordReturn').onclick = restorePanels;
$('noteEditButton').onclick = safely(async () => {
  if (wordActive) await endWord();
  setMode(mode === 'edit' ? 'read' : 'edit');
  $('noteEditButton').textContent = mode === 'edit' ? '完成编辑' : '编辑';
});
async function reviewContext(selection = false, side) {
  if (wordBusy) await waitWordIdle();
  if (!doc) throw Error('请先打开文档');
  await flush();
  if (wordActive && (!side || side === 'document')) {
    const whole = await wordCall('read'),
      selected = selection ? await wordCall('selection') : {};
    return {
      documentId: doc.id,
      side: 'document',
      page: selected.page || page,
      quote: selected.text || '',
      context: whole.text,
      revision: whole.revision,
      anchor: selection
        ? { wordStart: selected.start, wordEnd: selected.end }
        : null,
    };
  }
  const selected = window.getSelection(),
    inside = selected?.anchorNode?.parentElement?.closest('.document-pane'),
    target = side || (inside ? 'document' : 'note'),
    targetPage =
      selection && target === 'document'
        ? Number(
            selected?.anchorNode?.parentElement?.closest('.document-sheet')
              ?.dataset.page,
          ) || page
        : page;
  const context =
    target === 'note' ? $('editor').value : await sourceText(targetPage);
  const quote = selection
    ? document.activeElement === $('editor')
      ? $('editor').value.slice(
          $('editor').selectionStart,
          $('editor').selectionEnd,
        )
      : selected?.toString() || ''
    : '';
  const digest = await crypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(context),
  );
  return {
    documentId: doc.id,
    side: target,
    page: targetPage,
    quote,
    context,
    revision: [...new Uint8Array(digest)]
      .map((x) => x.toString(16).padStart(2, '0'))
      .join(''),
    anchor:
      document.activeElement === $('editor')
        ? { start: $('editor').selectionStart, end: $('editor').selectionEnd }
        : null,
  };
}
async function regionContext(region) {
  const context = await reviewContext(false, region.side);
  context.page = region.page;
  const pts = region.shape.points,
    x = Math.min(...pts.map((p) => p[0])),
    y = Math.min(...pts.map((p) => p[1])),
    right = Math.max(...pts.map((p) => p[0])),
    bottom = Math.max(...pts.map((p) => p[1]));
  const host =
    region.side === 'document' ? pageSheets[region.page - 1] : $('noteBody');
  const r = host.getBoundingClientRect(),
    scale = r.width / 1000,
    box = {
      left: r.left + x * scale,
      right: r.left + right * scale,
      top: r.top + y * scale,
      bottom: r.top + bottom * scale,
    };
  const texts = [];
  const root =
    region.side === 'document'
      ? host.querySelector('.textLayer') || host
      : $('preview');
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = walker.nextNode())) {
    if (!node.textContent.trim()) continue;
    const range = document.createRange();
    range.selectNodeContents(node);
    if (
      [...range.getClientRects()].some(
        (p) =>
          p.right >= box.left &&
          p.left <= box.right &&
          p.bottom >= box.top &&
          p.top <= box.bottom,
      )
    )
      texts.push(node.textContent);
  }
  context.quote = texts.join(' ').slice(0, 100000);
  if (region.page !== page) {
    context.context =
      region.side === 'document'
        ? await sourceText(region.page)
        : notes[region.page] || '';
    const digest = await crypto.subtle.digest(
      'SHA-256',
      new TextEncoder().encode(context.context),
    );
    context.revision = [...new Uint8Array(digest)]
      .map((x) => x.toString(16).padStart(2, '0'))
      .join('');
  }
  return context;
}
reviews = createReviews({
  request,
  getContext: reviewContext,
  getRegionContext: regionContext,
  annotations,
  toast,
});
aiTools = createAITools({
  annotations,
  isReading: () => mode === 'read' && !wordActive && !documentMarkdownEditing,
  hasDocument: () => !!doc,
  toast,
});
aiTools.refresh();
document.body.classList.add('ai-reading');
$('aiReviewButton').onmousedown = (e) => e.preventDefault();
$('aiReviewButton').onclick = () =>
  reviews.open ? reviews.hide() : reviews.show();
$('wordSelectionReview').onclick = safely(async () => {
  reviews.show();
  $('reviewSelection').click();
});
document.addEventListener(
  'review-ink',
  safely(async (event) => {
    if (wordActive) await endWord();
    setMode('read');
    annotations.activate(event.detail);
    editTools();
  }),
);
document.addEventListener('review-tools', editTools);
document.addEventListener('review-layout', () =>
  resizeWord().catch((e) => toast(e.message)),
);
$('noteFontTool').onchange = safely(async (e) =>
  applyLayout(
    await request('/api/layout', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...layout, fontSize: Number(e.target.value) }),
    }),
  ),
);
$('openButton').onclick = $('welcomeOpen').onclick = () =>
  $('fileInput').click();
$('fileInput').onchange = safely((e) => importFile(e.target.files[0]));
$('library').onchange = safely(async () => {
  const selected = $('library').value;
  if (!selected) return;
  const docs = await request('/api/documents');
  await openDocument(docs.find((d) => d.id === selected));
});
$('prev').onclick = safely(() => goTo(page - 1, true));
$('next').onclick = safely(() => goTo(page + 1, true));
$('pageNumber').onchange = safely(() => goTo($('pageNumber').value, true));
document.querySelectorAll('[data-mode]').forEach(
  (b) =>
    (b.onclick = safely(async () => {
      if (wordActive) await endWord();
      setMode(b.dataset.mode);
    })),
);
$('editor').addEventListener('input', editChanged);
$('zoomOut').onclick = safely(() => {
  const anchor = documentZoomAnchor();
  zoom = Math.max(0.4, zoom - 0.1);
  return renderPage(anchor);
});
$('zoomIn').onclick = safely(() => {
  const anchor = documentZoomAnchor();
  zoom = Math.min(3, zoom + 0.1);
  return renderPage(anchor);
});
$('fit').onclick = safely(() => {
  zoom = 1;
  return renderPage();
});
$('attachment').onclick = () => $('attachmentInput').click();
$('attachmentInput').onchange = safely((e) => attach(e.target.files[0]));
$('importNote').onclick = () => $('noteInput').click();
$('noteInput').onchange = safely(async (e) => {
  const file = e.target.files[0];
  if (!file) return;
  const text = await file.text();
  if (/\.json$/i.test(file.name)) {
    const backup = JSON.parse(text);
    if (!backup.notes || typeof backup.notes !== 'object')
      throw Error('不是有效的pagenote笔记备份');
    if (!confirm('导入将覆盖备份中对应页码的现有笔记，是否继续？')) return;
    await flush();
    for (const [p, value] of Object.entries(backup.notes)) {
      if (
        !/^\d+$/.test(p) ||
        Number(p) < 1 ||
        Number(p) > total ||
        typeof value !== 'string'
      )
        continue;
      await request(`/api/documents/${doc.id}/notes/${p}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ markdown: value }),
      });
      notes[p] = value;
      noteBases.set(noteKey(doc.id, p), value);
    }
    loadNote();
    toast('整份笔记已导入');
  } else {
    insertText(text);
    toast('Markdown 已插入本页');
  }
  e.target.value = '';
});
$('exportNote').onclick = safely(async () => {
  await flush();
  download(
    doc.name + '-逐页笔记.json',
    JSON.stringify(
      { version: 1, document: doc.name, pages: total, notes },
      null,
      2,
    ),
    'application/json',
  );
  download(doc.name + `-第${page}页.md`, $('editor').value, 'text/markdown');
});
$('helpButton').onclick = () => $('help').showModal();
$('closeHelp').onclick = () => $('help').close();
$('settingsButton').onclick = () => $('settings').showModal();
$('closeSettings').onclick = () => $('settings').close();
let defaultAIPrompt = '';
let promptDrafts = {},
  selectedPrompt = 'system';
async function loadPromptModule(id) {
  selectedPrompt = id;
  if (!promptDrafts[id]) {
    const value = await request('/api/ai-prompts/' + id);
    promptDrafts[id] = { text: value.text, defaultText: value.defaultText };
  }
  defaultAIPrompt = promptDrafts[id].defaultText;
  $('aiSystemPrompt').value = promptDrafts[id].text;
  $('aiSettingsStatus').textContent = '';
}
$('aiPromptModule').onchange = safely(async () => {
  promptDrafts[selectedPrompt].text = $('aiSystemPrompt').value;
  await loadPromptModule($('aiPromptModule').value);
});
async function openAISettings() {
  const settings = await request('/api/ai-settings');
  promptDrafts = {
    system: {
      text: settings.systemPrompt,
      defaultText: settings.defaultSystemPrompt,
    },
  };
  selectedPrompt = 'system';
  $('aiPromptModule').value = 'system';
  defaultAIPrompt = settings.defaultSystemPrompt;
  $('aiSystemPrompt').value = settings.systemPrompt;
  $('aiSettingsStatus').textContent = '';
  if ($('settings').open) $('settings').close();
  $('aiSettings').showModal();
}
$('aiSettingsButton').onclick = safely(openAISettings);
$('aiSettingsCancel').onclick = () => $('aiSettings').close();
$('aiPromptReset').onclick = () => {
  $('aiSystemPrompt').value = defaultAIPrompt;
  $('aiSettingsStatus').textContent = t('已恢复默认内容，保存后生效');
};
$('aiSettingsSave').onclick = async () => {
  $('aiSettingsSave').disabled = true;
  try {
    await request(
      selectedPrompt === 'system'
        ? '/api/ai-settings'
        : '/api/ai-prompts/' + selectedPrompt,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(
          selectedPrompt === 'system'
            ? { systemPrompt: $('aiSystemPrompt').value }
            : { text: $('aiSystemPrompt').value },
        ),
      },
    );
    promptDrafts[selectedPrompt].text = $('aiSystemPrompt').value;
    $('aiSettingsStatus').textContent = t(
      selectedPrompt === 'system'
        ? '已保存，请重新连接 AI 客户端'
        : '已保存，AI 下次读取模块时生效',
    );
  } catch (error) {
    $('aiSettingsStatus').textContent = error.message;
  } finally {
    $('aiSettingsSave').disabled = false;
  }
};
$('expandEditorSetting').onchange = safely(async () => {
  const next = $('expandEditorSetting').checked;
  $('expandEditorSetting').disabled = true;
  try {
    applyLayout(
      await request('/api/layout', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...layout, expandEditor: next }),
      }),
    );
    if (!next) restorePanels();
  } catch (e) {
    $('expandEditorSetting').checked = layout.expandEditor;
    throw e;
  } finally {
    $('expandEditorSetting').disabled = false;
  }
});
function activateInk(side) {
  if (!doc || aiEditing) return;
  if (annotations.active === side) {
    annotations.activate(null);
    restorePanels();
    editTools();
    return;
  }
  if (side === 'note') setMode('read');
  document.activeElement?.blur();
  annotations.activate(side);
  expandPanel(side);
  editTools();
}
$('documentInkButton').onclick = safely(async () => {
  if (wordActive) await endWord();
  activateInk('document');
});
$('noteInkButton').onclick = safely(async () => {
  if (wordActive) await endWord();
  activateInk('note');
});
$('inkDone').onclick = () => {
  annotations.activate(null);
  restorePanels();
  editTools();
};
$('inkReturn').onclick = $('noteReturn').onclick = restorePanels;
rich = createRichEditor({
  root: $('preview'),
  changed: (text) => {
    $('editor').value = text;
    editChanged(true);
  },
  canEdit: () => !!doc && mode === 'edit' && !aiEditing && !wordBusy,
  toast,
});
const mdTools = setupMarkdownTools({
  editor: $('editor'),
  changed: editChanged,
  attach: () => $('attachmentInput').click(),
  paste: (side) => pasteClipboard(side).catch((e) => toast(e.message)),
  canEdit: () => !!doc && !aiEditing,
  rich,
});
$('inkPaste').onclick = () =>
  pasteClipboard(annotations.active).catch((e) => toast(e.message));
$('inkImage').onclick = () => $('inkImageInput').click();
$('inkImageInput').onchange = safely(async (e) => {
  const file = e.target.files[0];
  if (file && annotations.active)
    await annotations.addImage(annotations.active, file);
  e.target.value = '';
});
let historyTarget = null,
  historyPreviewEpoch = 0;
$('closeHistory').onclick = () => $('historyDialog').close();
$('historyButton').onclick = safely(async () => {
  await flush();
  historyTarget = { id: doc.id, page };
  $('historyContext').textContent = `${doc.name} · 第 ${page} 页`;
  $('historyList').replaceChildren();
  $('historyPreview').textContent = '';
  $('historyStatus').textContent = '正在读取历史版本…';
  $('restoreHistory').disabled = true;
  $('historyDialog').showModal();
  const result = await request(
    `/api/documents/${historyTarget.id}/notes/${historyTarget.page}/history`,
  );
  historyTarget.revision = result.currentRevision;
  const dates = new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'short',
    timeStyle: 'medium',
  });
  $('historyList').replaceChildren(
    ...result.versions.map(
      (v, i) =>
        new Option(
          `${i === 0 ? '最新 · ' : ''}${dates.format(new Date(v.created))} · ${(v.bytes / 1024).toFixed(1)} KB`,
          v.id,
        ),
    ),
  );
  $('historyStatus').textContent = result.versions.length
    ? `共 ${result.versions.length} 个版本`
    : '本页暂无历史，编辑后会自动备份';
  if (result.versions.length) {
    $('historyList').selectedIndex = 0;
    await showHistoryVersion();
  }
});
async function showHistoryVersion() {
  const epoch = ++historyPreviewEpoch,
    version = $('historyList').value;
  $('restoreHistory').disabled = true;
  if (!version || !historyTarget) return;
  const result = await request(
    `/api/documents/${historyTarget.id}/notes/${historyTarget.page}/history/${encodeURIComponent(version)}`,
  );
  if (epoch !== historyPreviewEpoch) return;
  if (result.markdown) renderMarkdown(result.markdown, $('historyPreview'));
  else
    $('historyPreview').innerHTML =
      '<p class="history-empty">这个版本的笔记为空。</p>';
  $('restoreHistory').disabled = false;
}
$('historyList').onchange = safely(showHistoryVersion);
$('restoreHistory').onclick = safely(async () => {
  if (!historyTarget || !$('historyList').value) return;
  if (
    !confirm(
      `将恢复第 ${historyTarget.page} 页的所选历史版本。当前内容会先自动备份，是否恢复？`,
    )
  )
    return;
  const target = { ...historyTarget },
    version = $('historyList').value;
  $('restoreHistory').disabled = true;
  $('historyList').disabled = true;
  try {
    await flush();
    const result = await request(
      `/api/documents/${target.id}/notes/${target.page}/restore`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ version, expectedRevision: target.revision }),
      },
    );
    if (doc?.id === target.id) {
      notes[target.page] = result.markdown;
      noteBases.set(noteKey(target.id, target.page), result.markdown);
      if (page === target.page) loadNote();
    }
    $('historyDialog').close();
    toast('已恢复所选版本，恢复前的内容已保留在历史中');
  } finally {
    $('historyList').disabled = false;
    $('restoreHistory').disabled = false;
  }
});
$('noteAnimationSetting').onchange = safely(async () => {
  const next = $('noteAnimationSetting').checked;
  $('noteAnimationSetting').disabled = true;
  try {
    applyLayout(
      await request('/api/layout', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...layout, noteAnimation: next }),
      }),
    );
  } catch (e) {
    $('noteAnimationSetting').checked = layout.noteAnimation;
    throw e;
  } finally {
    $('noteAnimationSetting').disabled = false;
  }
});
document.addEventListener('keydown', (e) => {
  let action;
  if (e.altKey && e.key === 'ArrowRight') action = () => goTo(page + 1, true);
  else if (e.altKey && e.key === 'ArrowLeft')
    action = () => goTo(page - 1, true);
  else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's')
    action = flush;
  if (action) {
    e.preventDefault();
    action().catch((error) => toast(error.message));
  }
});
async function pasteContent(side, { text = '', html = '', files = [] }) {
  if (!doc || aiEditing) return;
  if (text.startsWith('PAGE_NOTES_ANNOTATION:\n')) {
    const object = JSON.parse(text.slice(23));
    if (side === 'note' && !annotations.active) {
      if (object.type === 'text') insertText(object.text);
      else if (object.type === 'html') insertText(htmlToMarkdown(object.html));
      else if (object.type === 'image')
        insertText(`\n![图片](${object.url})\n`);
      else annotations.pasteObject(side, text);
    } else annotations.pasteObject(side, text);
    return;
  }
  const images = files.filter((file) => file.type.startsWith('image/'));
  if (side === 'note' && !annotations.active && html) {
    const markdown = htmlToMarkdown(html);
    if (markdown.replace(/!\[[^\]]*\]\([^)]*\)/g, '').trim()) {
      insertText(markdown);
      return;
    }
  }
  if (images.length) {
    for (const file of images) {
      if (side === 'document' || annotations.active === side)
        await annotations.addImage(side, file);
      else await attach(file);
    }
    return;
  }
  if (side === 'document' || annotations.active === side) {
    if (html) annotations.addRich(side, html);
    else if (text) {
      const container = document.createElement('article');
      container.className = 'prose';
      container.style.cssText = 'position:fixed;left:-10000px;width:500px';
      document.body.append(container);
      try {
        renderMarkdown(text, container);
        await waitVisuals();
        annotations.addRich(side, container.innerHTML);
      } finally {
        container.remove();
      }
    }
  } else if (html) insertText(htmlToMarkdown(html));
  else if (text) insertText(text);
}
async function pasteClipboard(side) {
  const content = { files: [], text: '', html: '' };
  try {
    for (const item of await navigator.clipboard.read()) {
      for (const type of item.types) {
        const blob = await item.getType(type);
        if (type.startsWith('image/'))
          content.files.push(
            new File([blob], `粘贴图片-${Date.now()}.${type.split('/')[1]}`, {
              type,
            }),
          );
        else if (type === 'text/html') content.html = await blob.text();
        else if (type === 'text/plain') content.text = await blob.text();
      }
    }
  } catch {
    content.text = await navigator.clipboard.readText();
  }
  if (!content.files.length && !content.text && !content.html) {
    toast('剪贴板为空，请先复制内容');
    return;
  }
  await pasteContent(side, content);
}
document.addEventListener('paste', (e) => {
  if (!doc || aiEditing || e.target.closest('dialog')) return;
  const side = e.target.closest('.document-pane')
    ? 'document'
    : e.target.closest('.note-pane')
      ? 'note'
      : annotations.active;
  if (!side) return;
  const files = [...(e.clipboardData?.files || [])].filter((file) =>
    file.type.startsWith('image/'),
  );
  const text = e.clipboardData?.getData('text/plain') || '',
    html = e.clipboardData?.getData('text/html') || '';
  if (
    !files.length &&
    !html &&
    !text.startsWith('PAGE_NOTES_ANNOTATION:\n') &&
    side === 'note' &&
    !annotations.active
  )
    return;
  e.preventDefault();
  const images = files.map((file) =>
    /\.(png|jpe?g|gif|webp)$/i.test(file.name)
      ? file
      : new File(
          [file],
          `粘贴图片-${Date.now()}.${file.type === 'image/jpeg' ? 'jpg' : file.type.split('/')[1]}`,
          { type: file.type },
        ),
  );
  pasteContent(side, { files: images, text, html }).catch((err) =>
    toast(err.message),
  );
});
document.addEventListener('dragover', (e) => {
  if (e.dataTransfer.types.includes('Files')) e.preventDefault();
});
document.addEventListener('drop', (e) => {
  const files = [...e.dataTransfer.files];
  if (!files.length) return;
  e.preventDefault();
  (async () => {
    const side = e.target.closest('.document-pane')
      ? 'document'
      : e.target.closest('.note-pane')
        ? 'note'
        : null;
    if (doc && side && annotations.active === side) {
      for (const file of files)
        if (file.type.startsWith('image/'))
          await annotations.addImage(side, file);
        else toast('批注画布可插入图片，文档请通过“打开文档”导入');
    } else if (side === 'note' && doc) {
      for (const file of files) await attach(file);
    } else await importFile(files[0]);
  })().catch((err) => toast(err.message));
});
$('documentScroll').addEventListener('scroll', () => {
  lastDocumentScroll = Date.now();
  if (scrollFrame) return;
  scrollFrame = requestAnimationFrame(() => {
    scrollFrame = 0;
    paintVisible();
    syncScrollPage().catch((e) => toast(e.message));
  });
});
$('documentScroll').addEventListener(
  'wheel',
  (e) => {
    if (!e.ctrlKey) {
      focusFreeze = 0;
      userScrollUntil = Date.now() + 2500;
    }
  },
  { passive: true },
);
$('documentScroll').addEventListener(
  'pointerdown',
  (e) => {
    if (e.target === $('documentScroll') || e.pointerType === 'touch') {
      focusFreeze = 0;
      userScrollUntil = Date.now() + 10000;
    }
  },
  { passive: true },
);
$('documentScroll').addEventListener('keydown', (e) => {
  if (
    ['ArrowDown', 'ArrowUp', 'PageDown', 'PageUp', 'Home', 'End', ' '].includes(
      e.key,
    )
  ) {
    focusFreeze = 0;
    userScrollUntil = Date.now() + 2500;
  }
});
let noteZoom = Number(localStorage.getItem('note-zoom')) || 1,
  zoomTimer;
function applyNoteZoom() {
  document.documentElement.style.setProperty('--note-zoom', noteZoom);
  localStorage.setItem('note-zoom', String(noteZoom));
  growEditor();
}
applyNoteZoom();
document.addEventListener(
  'wheel',
  (e) => {
    if (!e.ctrlKey) return;
    e.preventDefault();
    e.stopPropagation();
    if (!e.deltaY) return;
    const factor = e.deltaY < 0 ? 1.1 : 1 / 1.1;
    if (e.target.closest('.document-pane')) {
      if (!doc) return;
      const anchor = documentZoomAnchor(e);
      zoom = Math.max(0.4, Math.min(4, zoom * factor));
      $('zoomLabel').textContent = Math.round(zoom * 100) + '%';
      clearTimeout(zoomTimer);
      zoomTimer = setTimeout(
        () => renderPage(anchor).catch((err) => toast(err.message)),
        60,
      );
    } else if (e.target.closest('.note-pane')) {
      noteZoom = Math.max(0.65, Math.min(2.5, noteZoom * factor));
      applyNoteZoom();
      $('wordCount').textContent =
        `${$('editor').value.replace(/\s/g, '').length.toLocaleString()} 字 · 字号 ${Math.round(noteZoom * 100)}%`;
    }
  },
  { passive: false, capture: true },
);
async function waitVisuals() {
  await document.fonts.ready;
  const start = Date.now();
  while (
    [...document.querySelectorAll('.mermaid-diagram')].some(
      (e) =>
        !e.classList.contains('diagram-empty') &&
        !e.classList.contains('diagram-ready') &&
        !e.classList.contains('diagram-error'),
    )
  ) {
    if (Date.now() - start > 20000) throw Error('图表渲染超时');
    await new Promise((r) => setTimeout(r, 70));
  }
  await Promise.all(
    [
      ...document.querySelectorAll(
        '#preview img,#markdownDocument img,.ink-layer img',
      ),
    ].map((im) =>
      im.complete
        ? null
        : new Promise((resolve) => {
            im.addEventListener('load', resolve, { once: true });
            im.addEventListener('error', resolve, { once: true });
            setTimeout(resolve, 3000);
          }),
    ),
  );
  await Promise.all(
    [...document.querySelectorAll('.ink-layer image')].map(async (element) => {
      const im = new Image();
      im.src = element.getAttribute('href');
      await Promise.race([
        im.decode().catch(() => {}),
        new Promise((resolve) => setTimeout(resolve, 3000)),
      ]);
    }),
  );
  await new Promise((r) =>
    requestAnimationFrame(() => requestAnimationFrame(r)),
  );
}
async function sourceText(p = page) {
  if (!doc) return '';
  if (doc.kind === 'markdown') return mdPages[p - 1] || '';
  const content = await (await pdf.getPage(p)).getTextContent();
  return content.items
    .map((item) => item.str + (item.hasEOL ? '\n' : ' '))
    .join('');
}
function geometry(selector) {
  const e = document.querySelector(selector),
    r = e.getBoundingClientRect(),
    s = getComputedStyle(e);
  return {
    x: r.x,
    y: r.y,
    width: r.width,
    height: r.height,
    scrollWidth: e.scrollWidth,
    scrollHeight: e.scrollHeight,
    fontSize: s.fontSize,
    lineHeight: s.lineHeight,
    background: s.backgroundColor,
  };
}
window.pageNotes = {
  validateNote,
  openAISettings,
  finishEditing: async () => {
    await endMarkdownDocument();
    await waitWordIdle();
    if (wordActive) await endWord();
    await flush();
  },
  beginWord,
  endWord,
  reloadReviews: () => reviews.reload(),
  getAnnotations: () => annotations.getState(),
  pasteContent,
  editCommand: async (command) => {
    if (
      mode === 'edit' &&
      !annotations.active &&
      !document.activeElement?.closest('dialog') &&
      ['undo', 'redo'].includes(command)
    ) {
      rich[command]();
      return true;
    }
    if (!annotations.active || document.activeElement?.closest('dialog'))
      return false;
    try {
      if (command === 'undo') annotations.undo();
      else if (command === 'redo') annotations.redo();
      else if (command === 'paste') await pasteClipboard(annotations.active);
      else if (command === 'copy' || command === 'cut') {
        await annotations.copySelection();
        if (command === 'cut') $('inkDelete').click();
      } else return false;
    } catch (e) {
      toast(e.message);
    }
    return true;
  },
  flush: async () => {
    await endMarkdownDocument();
    await flush();
    if (doc?.projectRoot)
      await request('/api/projects/' + doc.id + '/sync', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ source: !wordActive }),
      });
  },
  resetNoteZoom: () => {
    noteZoom = 1;
    applyNoteZoom();
    updatePreview();
  },
  getSummary: () => ({
    document: doc ? { id: doc.id, name: doc.name, kind: doc.kind } : null,
    page,
    total,
    mode,
    wordEditing: wordActive,
  }),
  preparePageData: async (p) => {
    if (!Number.isInteger(p) || p < 1 || p > total) throw Error('页码超出范围');
    return preparePageData({
      pdf,
      pdfjs,
      markdown: mdPages[p - 1] || '',
      page: p,
    });
  },
  getState: async () => ({
    document: doc,
    page,
    total,
    mode,
    aiWorkspace: {
      pointer: document.body.dataset.aiPointer || 'mouse',
      reviews: reviews?.records || [],
      boards: [...document.querySelectorAll('.ai-workbench')].map((b) => ({
        side: b.dataset.side,
        open: b.classList.contains('open'),
      })),
    },
    wordEditing: wordActive,
    wordBusy,
    focusPanel: $('workspace').dataset.focus || null,
    annotations: {
      document: annotations.getState().items.document[page] || [],
      note: annotations.getState().items.note[page] || [],
      active: annotations.active,
    },
    sourceText: await sourceText(),
    markdown: $('editor').value,
    renderedText: $('preview').innerText,
    layout: {
      ...layout,
      leftPercent: parseFloat(
        getComputedStyle(document.documentElement).getPropertyValue('--left'),
      ),
      noteZoom,
      documentZoom: zoom,
    },
    geometry: {
      document: geometry('#documentScroll'),
      noteScroll: geometry('#noteScroll'),
      notePaper: geometry('#noteBody'),
      preview: geometry('#preview'),
    },
  }),
  refreshLibrary,
  open: async (id, p = 1, forceReload = false) => {
    if (wordBusy || wordActive) await window.pageNotes.finishEditing();
    const list = await refreshLibrary();
    const meta = list.find((d) => d.id === id);
    if (!meta) throw Error('文档不存在');
    if (
      doc?.id !== id ||
      forceReload ||
      (new URLSearchParams(location.search).has('capture') &&
        doc?.modified !== meta.modified)
    )
      await openDocument(meta);
    await goTo(p);
    await waitVisuals();
  },
  readPages: async (pages, include = 'both') => {
    const results = [];
    for (const p of pages) {
      if (p < 1 || p > total) throw Error('页码超出范围');
      results.push({
        page: p,
        ...(['source', 'both'].includes(include)
          ? { sourceText: await sourceText(p) }
          : {}),
        ...(['note', 'both'].includes(include)
          ? { markdown: notes[p] || '' }
          : {}),
      });
    }
    return results;
  },
  setLayout: async (value) => {
    const saved = await request('/api/layout', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...layout, ...value }),
    });
    applyLayout(saved);
    await renderPage();
    return saved;
  },
  syncNotes: async (id) => {
    if (doc?.id === id) {
      const incoming = await request(`/api/documents/${id}/notes`);
      if (doc?.id !== id) return;
      const previous = notes[page] || '';
      for (const p of new Set([
        ...Object.keys(notes),
        ...Object.keys(incoming),
        String(page),
      ])) {
        const key = noteKey(id, p);
        if (
          pendingNoteSaves.has(key) ||
          localStorage.getItem(recoveryKey(id, p)) !== null ||
          (dirty?.id === id && String(dirty.page) === p)
        )
          incoming[p] = notes[p] || '';
        else noteBases.set(key, incoming[p] || '');
      }
      notes = incoming;
      // Background writes to other pages must not rebuild the reader's DOM,
      // clear their selection or move either pane.
      if ((notes[page] || '') !== previous) {
        const scroll = $('noteScroll');
        const { scrollTop, scrollLeft } = scroll;
        const editorTop = $('editor').scrollTop;
        $('editor').value = notes[page] || '';
        updatePreview();
        annotations.mountNote($('noteBody'), page);
        reviews?.refresh();
        scroll.scrollTop = scrollTop;
        scroll.scrollLeft = scrollLeft;
        $('editor').scrollTop = editorTop;
      }
    }
  },
  lockEditing: async () => {
    aiEditing = true;
    updateControls();
    try {
      await flush();
    } catch (error) {
      aiEditing = false;
      updateControls();
      throw error;
    }
  },
  unlockEditing: () => {
    aiEditing = false;
    updateControls();
    growEditor();
  },
  prepareCapture: async (id, p, settings, sourceUrl) => {
    await window.pageNotes.ready;
    if (settings) applyLayout(settings);
    if (sourceUrl) {
      if (!new URLSearchParams(location.search).has('capture'))
        throw Error('编辑预览仅用于隐藏截图窗口');
      const meta = (await request('/api/documents')).find(
        (item) => item.id === id,
      );
      if (!meta || meta.kind !== 'pdf') throw Error('截图文档不存在');
      await openDocument({ ...meta, url: sourceUrl, modified: sourceUrl });
      await goTo(p);
    } else await window.pageNotes.open(id, p);
    setMode('read');
    await annotations.load(id);
    annotations.mountNote($('noteBody'), page);
    noteZoom = 1;
    applyNoteZoom();
    zoom = 1;
    await renderPage();
    await waitVisuals();
    return {
      page,
      total,
      geometry: {
        document: geometry('#pdfCanvas'),
        note: geometry('#noteScroll'),
      },
    };
  },
  captureMetrics: (scope) => {
    const selector =
      scope === 'document'
        ? doc.kind === 'pdf'
          ? '#pdfCanvas'
          : '#markdownDocument'
        : scope === 'note'
          ? '#noteScroll'
          : '#workspace';
    return geometry(selector);
  },
  scrollNote: async (top) => {
    $('noteScroll').scrollTop = top;
    await new Promise((r) =>
      requestAnimationFrame(() => requestAnimationFrame(r)),
    );
    return $('noteScroll').scrollTop;
  },
  waitVisuals,
};
$('splitter').onpointerdown = (e) => {
  e.preventDefault();
  $('splitter').setPointerCapture(e.pointerId);
  $('workspace').classList.add('dragging-split');
  $('splitter').onpointermove = (event) => {
    delete $('workspace').dataset.focus;
    const r = $('workspace').getBoundingClientRect(),
      percent = Math.max(
        2,
        Math.min(98, ((event.clientX - r.left) / r.width) * 100),
      );
    document.documentElement.style.setProperty('--left', percent + '%');
    returnSplit = percent;
  };
  const end = () => {
    $('splitter').onpointermove = null;
    $('workspace').classList.remove('dragging-split');
    renderPage().catch((e) => toast(e.message));
  };
  $('splitter').onpointerup = end;
  $('splitter').onpointercancel = end;
};
$('splitter').onkeydown = (e) => {
  if (!['ArrowLeft', 'ArrowRight'].includes(e.key)) return;
  const current = parseFloat(
    getComputedStyle(document.documentElement).getPropertyValue('--left'),
  );
  document.documentElement.style.setProperty(
    '--left',
    Math.max(25, Math.min(75, current + (e.key === 'ArrowRight' ? 2 : -2))) +
      '%',
  );
  renderPage().catch((e) => toast(e.message));
};
let resizeTimer;
const resized = () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    if (
      annotations.drawing ||
      scrollSuspended ||
      Date.now() - lastDocumentScroll < 200
    ) {
      resized();
      return;
    }
    annotations.refresh();
    if (doc && $('documentScroll').clientWidth > 10)
      renderPage().catch((e) => toast(e.message));
  }, 160);
};
window.addEventListener('resize', resized);
if (!new URLSearchParams(location.search).has('capture'))
  new ResizeObserver(resized).observe($('documentScroll'));
new ResizeObserver(() => {
  if (!annotations.drawing) annotations.refresh();
}).observe($('noteBody'));
window.addEventListener('beforeunload', (e) => {
  if (dirty) {
    e.preventDefault();
    e.returnValue = '';
  }
});
new MutationObserver(() => {
  const text = $('saveStatus').textContent;
  $('saveStatus').dataset.state = /失败|冲突|fail|conflict/i.test(text)
    ? 'error'
    : /等待|正在保存|waiting|saving/i.test(text)
      ? 'waiting'
      : 'saved';
}).observe($('saveStatus'), { childList: true });
updateControls();
window.pageNotes.ready = (async () => {
  applyLayout(await request('/api/layout'));
  const docs = await refreshLibrary();
  const last = docs.find((d) => d.id === localStorage.getItem('last-document'));
  if (last && !new URLSearchParams(location.search).has('capture'))
    await openDocument(last);
})().catch((e) => toast(e.message));

// Close compact menus after an action or an outside click.
document.addEventListener('click', (e) => {
  document.querySelectorAll('.compact-menu[open]').forEach((menu) => {
    if (!menu.contains(e.target) || e.target.closest('button'))
      menu.open = false;
  });
});

document.body.classList.toggle(
  'capture-view',
  new URLSearchParams(location.search).has('capture'),
);

const createDialog = document.createElement('dialog');
createDialog.innerHTML =
  '<h2 id="newTitle">新建文件</h2><label>名称 <input id="newName" value="未命名"></label><label>项目保存目录 <input id="newParent" placeholder="留空使用默认项目目录"></label><div class="history-actions"><button id="newCancel">取消</button><button id="newConfirm">创建</button></div>';
document.body.append(createDialog);
let creationKind = 'file';
document.getElementById('newCancel').onclick = () => createDialog.close();
document.getElementById('newConfirm').onclick = safely(async () => {
  const name = document.getElementById('newName').value.trim();
  if (!name) return;
  await window.pageNotes.finishEditing();
  const meta = await request('/api/projects', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name,
      parent: $('newParent').value.trim() || undefined,
    }),
  });
  await refreshLibrary();
  await openDocument(meta);
  createDialog.close();
});
window.pageNotes.menuAction = async (action) => {
  if (action === 'importRight') {
    if (!doc) {
      toast('请先导入左侧文档或新建项目');
      return;
    }
    $('noteInput').click();
  } else if (action === 'newFile' || action === 'newProject') {
    creationKind = action;
    if ($('newName').value === '未命名' || $('newName').value === 'Untitled')
      $('newName').value = t('未命名');
    document.getElementById('newTitle').textContent = '新建项目';
    createDialog.showModal();
  } else if (action === 'reviewLeft' || action === 'reviewRight')
    reviews.show(action === 'reviewLeft' ? 'document' : 'note');
  else if (action === 'chooseEditor') {
    if (!doc) return;
    document.body.classList.add('choose-editor');
    toast('点击左侧文档或右侧讲解，选择编辑区域');
  }
};
for (const [selector, button] of [
  ['.document-pane', 'documentEditButton'],
  ['.note-pane', 'noteEditButton'],
])
  document.querySelector(selector).addEventListener(
    'click',
    (e) => {
      if (!document.body.classList.contains('choose-editor')) return;
      document.body.classList.remove('choose-editor');
      e.stopPropagation();
      if (button === 'documentEditButton' && $(button).disabled)
        activateInk('document');
      else $(button).click();
    },
    true,
  );
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') document.body.classList.remove('choose-editor');
});
const navigationDescription = $('pageDescription');
document
  .querySelector('.document-pane .pane-footer')
  .replaceChildren(
    document.querySelector('.reading-commands'),
    navigationDescription,
  );
navigationDescription.hidden = true;
document.querySelector('.titlebar').hidden = true;

window.pageNotes.openProject = async (filePath) => {
  documentOpening++;
  try {
    await window.pageNotes.finishEditing();
    const meta = await request('/api/projects/open', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ filePath }),
    });
    await refreshLibrary();
    await openDocument(meta);
  } finally {
    documentOpening--;
  }
};

window.pageNotes.setLanguage = setLanguage;
window.pageNotes.getLanguage = getLanguage;
const languageSetting = document.getElementById('languageSetting');
languageSetting.value = getLanguage();
languageSetting.onchange = () =>
  setLanguage(languageSetting.value).catch((e) => toast(e.message));
document.addEventListener(
  'language-changed',
  () => (languageSetting.value = getLanguage()),
);
const originalReady = window.pageNotes.ready;
window.pageNotes.ready = Promise.all([
  originalReady,
  initializeLanguage(),
]).then(() => {
  languageSetting.value = getLanguage();
});

let externalRefreshBusy = false;
window.pageNotes.refreshExternalSource = async () => {
  if (
    !doc ||
    documentOpening ||
    externalRefreshBusy ||
    mode === 'edit' ||
    documentMarkdownEditing ||
    wordActive ||
    wordBusy
  )
    return;
  externalRefreshBusy = true;
  try {
    await flush();
    const id = doc.id,
      result = await request('/api/documents/' + id + '/refresh-external', {
        method: 'POST',
      });
    if (result.changed && doc?.id === id && !documentOpening) {
      await openDocument(result.document, true);
      toast(t('文档已更新，页面笔记已保留'));
    }
  } catch (error) {
    toast(error.message);
  } finally {
    externalRefreshBusy = false;
  }
};

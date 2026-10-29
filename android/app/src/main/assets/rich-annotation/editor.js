(() => {
  'use strict';

  // Rich text editing window for canvas HTML annotations (port batch 4).
  // Mirrors the offline renderer contract (renderer.js) with an editable
  // contenteditable host: DOMPurify sanitizes in BOTH directions — source HTML
  // is sanitized before it enters the DOM (load), and the DOM is sanitized
  // again on every read-out (command results, undo/redo, confirm) — so the
  // emitted HTML always matches what the canvas renderer accepts.
  const MAX_SOURCE_CHARS = 100000;
  const MAX_UNDO = 80;
  const TYPING_COALESCE_MS = 500;
  const host = document.getElementById('edit-host');
  const remoteResourcePattern = /(?:src|srcset|href)\s*=\s*["']?\s*(?:https?:|\/\/|file:|content:)/i;
  const safeUriPattern = /^(?:(?:data:image\/(?:png|jpeg|gif|webp);base64,)|#)/i;

  const COMMANDS = {
    bold: true,
    italic: true,
    underline: true,
    strikeThrough: true,
    justifyLeft: true,
    justifyCenter: true,
    justifyRight: true,
    insertUnorderedList: true,
    insertOrderedList: true,
    formatBlock: true,
    foreColor: true,
    fontName: true,
    removeFormat: true,
  };
  const FORMAT_BLOCKS = /^(p|h1|h2|h3|blockquote)$/i;
  const COLOR = /^#[0-9a-fA-F]{6}$/;

  window.__pagenoteRichEditResult = { requestId: '', state: 'idle', html: '', canUndo: false, canRedo: false, contentHeightPx: 0 };

  let undo = [];
  let redo = [];
  let current = '';
  let lastEdit = 0;
  let editable = false;

  const sanitize = (html) =>
    DOMPurify.sanitize(html, {
      ADD_TAGS: ['annotation'],
      ALLOWED_URI_REGEXP: safeUriPattern,
      FORBID_TAGS: ['iframe', 'object', 'embed', 'form', 'input', 'button', 'video', 'audio', 'source'],
      FORBID_ATTR: ['srcset', 'formaction']
    });

  const snapshot = () => sanitize(host.innerHTML);

  function publish(requestId, state, extra) {
    const result = Object.assign(
      {
        requestId: String(requestId),
        state,
        html: state === 'ready' ? current : '',
        canUndo: state === 'ready' && undo.length > 0,
        canRedo: state === 'ready' && redo.length > 0,
        contentHeightPx: state === 'ready' ? Math.max(1, Math.ceil(host.scrollHeight)) : 0
      },
      extra || {}
    );
    window.__pagenoteRichEditResult = result;
    document.title = `pagenote-rich-edit:${state}:${result.requestId}`;
    return result;
  }

  function restore(html) {
    host.innerHTML = html;
    current = html;
  }

  function setEditable(value) {
    editable = !!value;
    host.contentEditable = String(editable);
  }

  window.loadPagenoteRichEdit = (source, requestId, editable_) => {
    const id = String(requestId);
    window.__pagenoteRichEditResult = { requestId: id, state: 'loading', html: '', canUndo: false, canRedo: false, contentHeightPx: 0 };
    document.title = `pagenote-rich-edit:loading:${id}`;
    if (typeof source !== 'string' || source.length > MAX_SOURCE_CHARS) {
      setEditable(false);
      host.replaceChildren();
      undo = [];
      redo = [];
      current = '';
      publish(id, 'error');
      return;
    }
    try {
      // Sanitize-in: the source never reaches the DOM unsanitized.
      const sanitized = sanitize(source) || '<p><br></p>';
      host.replaceChildren();
      restore(sanitized);
      undo = [];
      redo = [];
      lastEdit = 0;
      setEditable(editable_ !== false);
      publish(id, 'ready');
    } catch (_) {
      setEditable(false);
      host.replaceChildren();
      undo = [];
      redo = [];
      current = '';
      publish(id, 'error');
    }
  };

  window.commandPagenoteRichEdit = (command, value, requestId) => {
    const id = String(requestId);
    if (!editable || typeof command !== 'string' || !COMMANDS[command]) {
      publish(id, 'error');
      return;
    }
    if (command === 'formatBlock' && !FORMAT_BLOCKS.test(String(value || ''))) {
      publish(id, 'error');
      return;
    }
    if (command === 'foreColor' && !COLOR.test(String(value || ''))) {
      publish(id, 'error');
      return;
    }
    if (command === 'fontName' && (typeof value !== 'string' || !value.trim() || value.length > 200)) {
      publish(id, 'error');
      return;
    }
    try {
      // Style actions are separate undo steps (never coalesced with typing).
      undo.push(current);
      if (undo.length > MAX_UNDO) undo.shift();
      redo = [];
      document.execCommand(command, false, value);
      current = snapshot();
      lastEdit = Date.now();
      publish(id, 'ready');
    } catch (_) {
      publish(id, 'error');
    }
  };

  window.undoPagenoteRichEdit = (requestId) => {
    const id = String(requestId);
    if (!editable || !undo.length) {
      // Nothing to undo is a graceful no-op, not an error.
      publish(id, 'ready');
      return;
    }
    try {
      redo.push(current);
      restore(undo.pop());
      publish(id, 'ready');
    } catch (_) {
      publish(id, 'error');
    }
  };

  window.redoPagenoteRichEdit = (requestId) => {
    const id = String(requestId);
    if (!editable || !redo.length) {
      // Nothing to redo is a graceful no-op, not an error.
      publish(id, 'ready');
      return;
    }
    try {
      undo.push(current);
      if (undo.length > MAX_UNDO) undo.shift();
      restore(redo.pop());
      publish(id, 'ready');
    } catch (_) {
      publish(id, 'error');
    }
  };

  // Sanitize-out on demand: the confirmed content is re-sanitized when read,
  // so an editable DOM can never leak unsanitized markup to the canvas item.
  window.retrievePagenoteRichEdit = (requestId) => {
    const id = String(requestId);
    try {
      current = snapshot();
      publish(id, 'ready');
    } catch (_) {
      publish(id, 'error');
    }
  };

  host.addEventListener('input', () => {
    if (!editable) return;
    const now = Date.now();
    const next = snapshot();
    if (next !== current) {
      if (now - lastEdit > TYPING_COALESCE_MS || !undo.length) {
        undo.push(current);
        if (undo.length > MAX_UNDO) undo.shift();
      }
      redo = [];
      current = next;
      lastEdit = now;
    }
  });
})();

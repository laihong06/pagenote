(() => {
  'use strict';

  const MAX_SOURCE_CHARS = 100000;
  const MAX_SIDE = 16384;
  const MAX_PIXELS = 4194304;
  const host = document.getElementById('render-host');
  const remoteResourcePattern = /(?:src|srcset|href)\s*=\s*["']?\s*(?:https?:|\/\/|file:|content:)/i;
  const safeUriPattern = /^(?:(?:data:image\/(?:png|jpeg|gif|webp);base64,)|#)/i;

  window.__pagenoteRichHtmlResult = { requestId: '', state: 'idle' };

  window.renderPagenoteRichHtml = (source, widthCssPx, heightCssPx, requestId) => {
    const id = String(requestId);
    const result = { requestId: id, state: 'error', sanitizedHtml: '', contentHeightPx: 0, renderHeightCssPx: 0, blockedExternalCount: 0 };
    window.__pagenoteRichHtmlResult = result;
    document.title = `pagenote-rich-html:loading:${id}`;
    host.replaceChildren();

    if (typeof source !== 'string' || source.length > MAX_SOURCE_CHARS || !Number.isFinite(widthCssPx)) {
      result.state = 'error';
      document.title = `pagenote-rich-html:error:${id}`;
      return;
    }

    try {
      const width = Math.floor(widthCssPx);
      const requestedHeight = heightCssPx == null ? null : Math.floor(heightCssPx);
      if (width < 1 || width > MAX_SIDE || (requestedHeight != null && (requestedHeight < 1 || requestedHeight > MAX_SIDE))) {
        throw new Error('dimensions');
      }
      const blockedExternalCount = (source.match(new RegExp(remoteResourcePattern.source, 'ig')) || []).length;
      const sanitized = DOMPurify.sanitize(source, {
        ADD_TAGS: ['annotation'],
        ALLOWED_URI_REGEXP: safeUriPattern,
        FORBID_TAGS: ['iframe', 'object', 'embed', 'form', 'input', 'button', 'video', 'audio', 'source'],
        FORBID_ATTR: ['srcset', 'formaction']
      });
      if (sanitized.length > MAX_SOURCE_CHARS) throw new Error('content');
      host.style.width = `${width}px`;
      host.style.height = 'auto';
      const content = document.createElement('div');
      content.className = 'ink-rich-content';
      content.innerHTML = sanitized;
      host.replaceChildren(content);
      const contentHeightPx = Math.max(1, Math.ceil(content.scrollHeight));
      const renderHeightCssPx = requestedHeight == null ? Math.max(60, contentHeightPx + 20) : requestedHeight;
      if (width * renderHeightCssPx > MAX_PIXELS) throw new Error('pixels');
      host.style.height = `${renderHeightCssPx}px`;
      content.style.height = `${renderHeightCssPx}px`;
      if (width * renderHeightCssPx > MAX_PIXELS) throw new Error('pixels');
      Object.assign(result, {
        state: 'ready',
        sanitizedHtml: content.innerHTML,
        contentHeightPx,
        renderHeightCssPx,
        blockedExternalCount
      });
      window.__pagenoteRichHtmlResult = result;
      document.title = `pagenote-rich-html:ready:${id}`;
    } catch (_) {
      host.replaceChildren();
      result.state = 'error';
      document.title = `pagenote-rich-html:error:${id}`;
    }
  };
})();

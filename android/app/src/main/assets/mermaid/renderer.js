(() => {
  'use strict';

  const MAX_SOURCE_CHARS = 100000;
  const MAX_SVG_BYTES = 256 * 1024;
  const host = document.getElementById('render-host');

  const configuration = {
    startOnLoad: false,
    securityLevel: 'strict',
    suppressErrorRendering: true,
    theme: 'default',
    look: 'classic',
    htmlLabels: false,
    flowchart: { htmlLabels: false },
    fontFamily: 'Segoe UI, Microsoft YaHei, sans-serif',
    maxTextSize: MAX_SOURCE_CHARS,
    secure: [
      'startOnLoad', 'securityLevel', 'secure', 'maxTextSize',
      'suppressErrorRendering', 'maxEdges', 'htmlLabels', 'flowchart', 'theme'
    ]
  };
  mermaid.initialize(configuration);

  window.__pagenoteMermaidResult = { requestId: '', state: 'idle', svg: '' };

  window.renderPagenoteMermaid = async (source, requestId, darkTheme = false) => {
    mermaid.initialize({ ...configuration, theme: darkTheme === true ? 'dark' : 'default' });
    const id = String(requestId);
    window.__pagenoteMermaidResult = { requestId: id, state: 'loading', svg: '' };
    document.title = `pagenote-mermaid:loading:${id}`;
    host.replaceChildren();

    if (typeof source !== 'string' || source.length === 0 || source.length > MAX_SOURCE_CHARS) {
      window.__pagenoteMermaidResult.state = source === '' ? 'empty' : 'error';
      document.title = `pagenote-mermaid:${window.__pagenoteMermaidResult.state}:${id}`;
      return;
    }

    try {
      const rendered = await mermaid.render(`pagenote-mermaid-${id}`, source, host);
      const svg = DOMPurify.sanitize(rendered.svg, {
        USE_PROFILES: { svg: true, svgFilters: true },
        FORBID_ATTR: ['href', 'xlink:href']
      });
      if (!svg || new TextEncoder().encode(svg).length > MAX_SVG_BYTES) {
        throw new Error('SVG exceeds the allowed size');
      }
      window.__pagenoteMermaidResult = { requestId: id, state: 'ready', svg };
      document.title = `pagenote-mermaid:ready:${id}`;
    } catch (_) {
      window.__pagenoteMermaidResult = { requestId: id, state: 'error', svg: '' };
      document.title = `pagenote-mermaid:error:${id}`;
    } finally {
      host.replaceChildren();
    }
  };
})();

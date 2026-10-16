import { Marked } from 'marked';
import markedKatex from 'marked-katex-extension';
import katex from 'katex';
import DOMPurify from 'dompurify';
import { codeLanguage, highlightCode } from './code-highlight';
import { normalizeMath } from './math-input';
import 'highlight.js/styles/github.css';

const md = new Marked(
  { gfm: true },
  markedKatex({ throwOnError: false, nonStandard: true }),
);
let mermaidPromise,
  diagramSequence = 0,
  diagramQueue = Promise.resolve();
const renderTimers = new WeakMap();
function getMermaid() {
  if (!mermaidPromise)
    mermaidPromise = import('mermaid').then(({ default: mermaid }) => {
      mermaid.initialize({
        startOnLoad: false,
        securityLevel: 'strict',
        suppressErrorRendering: true,
        theme: 'default',
        look: 'classic',
        htmlLabels: false,
        fontFamily: 'Segoe UI, Microsoft YaHei, sans-serif',
        flowchart: { htmlLabels: false },
        maxTextSize: 100000,
      });
      return mermaid;
    });
  return mermaidPromise;
}
async function renderDiagram(mermaid, source) {
  // Mermaid measures a temporary SVG in the DOM. Keep it out of the body's
  // grid, otherwise it adds a row and briefly pushes both pane footers up.
  // visibility:hidden preserves text/SVG measurements, unlike display:none.
  const host = document.createElement('div');
  host.setAttribute('aria-hidden', 'true');
  host.style.cssText =
    'position:fixed;left:0;top:0;width:100%;visibility:hidden;pointer-events:none';
  document.body.append(host);
  try {
    return await mermaid.render(
      'page-diagram-' + ++diagramSequence,
      source,
      host,
    );
  } finally {
    host.remove();
  }
}
function diagramError(block, source, error) {
  block.replaceChildren();
  block.classList.add('diagram-error');
  const message = document.createElement('p');
  message.textContent = '图表语法暂时无法解析，请检查 Mermaid 代码。';
  const details = document.createElement('details');
  const summary = document.createElement('summary');
  summary.textContent = '查看代码和错误';
  const pre = document.createElement('pre');
  pre.textContent =
    source + '\n\n' + String(error.message || error).slice(0, 1200);
  details.append(summary, pre);
  block.append(message, details);
}
function scheduleDiagrams(target) {
  [...target.querySelectorAll('pre > code.language-mermaid')].forEach(
    (code) => {
      const block = document.createElement('figure');
      block.className = 'mermaid-diagram';
      block.setAttribute('aria-label', 'Mermaid 图表');
      block.textContent = '正在绘制图表…';
      const source = code.textContent;
      block.dataset.source = source;
      if (code.parentElement.dataset.richId)
        block.dataset.richId = code.parentElement.dataset.richId;
      code.parentElement.replaceWith(block);
    },
  );
  target.querySelectorAll('.mermaid-diagram[data-source]').forEach((block) => {
    if (!block.dataset.source.trim()) {
      block.classList.remove('diagram-error', 'diagram-ready');
      block.classList.add('diagram-empty');
      block.replaceChildren();
      delete block.dataset.running;
    } else block.classList.remove('diagram-empty');
  });
  const blocks = [
    ...target.querySelectorAll(
      '.mermaid-diagram[data-source]:not(.diagram-ready):not(.diagram-error)',
    ),
  ]
    .filter((block) => block.dataset.source.trim() && !block.dataset.running)
    .map((block) => ({ block, source: block.dataset.source }));
  if (!blocks.length) return;
  clearTimeout(renderTimers.get(target));
  renderTimers.set(
    target,
    setTimeout(() => {
      for (const { block, source } of blocks) {
        const job = diagramQueue.then(async () => {
          if (!block.isConnected) return;
          block.dataset.running = 'true';
          try {
            const mermaid = await getMermaid();
            await document.fonts.ready;
            if (!block.isConnected) return;
            const { svg } = await renderDiagram(mermaid, source);
            if (!block.isConnected || block.dataset.source !== source) return;
            block.innerHTML = DOMPurify.sanitize(svg, {
              USE_PROFILES: { svg: true, svgFilters: true },
            });
            block.classList.add('diagram-ready');
          } catch (error) {
            if (block.isConnected && block.dataset.source === source)
              diagramError(block, source, error);
          }
        });
        diagramQueue = job.catch(() => {});
      }
    }, 160),
  );
}
export function renderDiagrams(target) {
  scheduleDiagrams(target);
}
export async function validateNote(markdown) {
  const normalizedMarkdown = normalizeMath(String(markdown));
  const errors = [],
    warnings = [],
    tokens = md.lexer(normalizedMarkdown),
    images = [],
    diagrams = [],
    mathCode = [];
  let fence = null;
  for (const line of normalizedMarkdown.split(/\r?\n/)) {
    const m = line.match(/^ {0,3}(`{3,}|~{3,})/);
    if (!fence && m) fence = m[1];
    else if (
      fence &&
      m &&
      m[1][0] === fence[0] &&
      m[1].length >= fence.length &&
      /^ {0,3}(?:`+|~+)\s*$/.test(line)
    )
      fence = null;
  }
  if (fence) errors.push('代码围栏未闭合');
  md.walkTokens(tokens, (t) => {
    if (t.type === 'blockKatex' || t.type === 'inlineKatex') {
      try {
        katex.renderToString(t.text, {
          throwOnError: true,
          displayMode: !!t.displayMode,
        });
      } catch (e) {
        errors.push('公式: ' + e.message);
      }
    }
    if (t.type === 'image') images.push(t.href);
    if (t.type === 'code' && t.lang?.trim() === 'mermaid')
      diagrams.push(t.text);
    if (t.type === 'code' && ['math', 'latex', 'tex'].includes(t.lang?.trim()))
      mathCode.push(t.text);
  });
  const target = document.createElement('div');
  target.innerHTML = DOMPurify.sanitize(
    md.parse(normalizedMarkdown).replace(/<img\b[^>]*>/gi, ''),
    { ADD_TAGS: ['annotation'] },
  );
  target
    .querySelectorAll('.katex-error')
    .forEach((e) => errors.push(e.getAttribute('title') || '公式解析失败'));
  for (const text of mathCode) {
    try {
      katex.renderToString(text, { throwOnError: true });
    } catch (e) {
      errors.push('公式: ' + e.message);
    }
  }
  const prose = target.cloneNode(true);
  prose.querySelectorAll('.katex,pre,code').forEach((e) => e.remove());
  if (
    /\$\$|\\(?:frac|theta|sigma|mu|nabla|begin|sum|partial)\b/.test(
      prose.textContent,
    )
  )
    errors.push('正文仍含未渲染的公式分隔符或LaTeX命令');
  if (diagrams.length) {
    const engine = await getMermaid();
    for (const source of diagrams) {
      try {
        await engine.parse(source);
      } catch (e) {
        errors.push('Mermaid: ' + String(e.message || e).slice(0, 600));
      }
    }
  }
  for (const href of images) {
    if (!/^\/attachments\/[a-f0-9-]+\.(png|jpe?g|webp|gif|svg)$/i.test(href)) {
      warnings.push('未自动验证非本地图片: ' + href.slice(0, 150));
      continue;
    }
    try {
      const response = await fetch(href);
      if (!response.ok) throw Error('附件不存在');
      const blob = await response.blob();
      const u = URL.createObjectURL(blob);
      try {
        const img = new Image();
        img.src = u;
        await img.decode();
      } finally {
        URL.revokeObjectURL(u);
      }
    } catch (e) {
      errors.push('图片不可加载: ' + href);
    }
  }
  return {
    valid: errors.length === 0,
    normalizedMarkdown,
    errors,
    warnings,
    counts: {
      formulas: target.querySelectorAll('.katex').length + mathCode.length,
      diagrams: diagrams.length,
      images: images.length,
    },
    scope:
      'syntax-and-local-assets; not semantic correctness or cross-device guarantee',
  };
}
export function renderMarkdown(text, target, depth = 0) {
  target.innerHTML = DOMPurify.sanitize(md.parse(normalizeMath(text)), {
    ADD_TAGS: ['annotation'],
    ADD_ATTR: ['target', 'encoding'],
  });
  target.querySelectorAll('a').forEach((a) => {
    a.target = '_blank';
    a.rel = 'noopener noreferrer';
  });
  target.querySelectorAll('pre > code').forEach((code) => {
    const language = codeLanguage(
      [...code.classList].find((c) => c.startsWith('language-'))?.slice(9),
    );
    if (language) {
      for (const name of [...code.classList])
        if (name.startsWith('language-')) code.classList.remove(name);
      code.classList.add('language-' + language);
    }
    if (language === 'mermaid') return;
    if (['math', 'latex', 'tex'].includes(language)) {
      const block = document.createElement('div');
      block.className = 'math-block';
      block.innerHTML = DOMPurify.sanitize(
        katex.renderToString(code.textContent, {
          displayMode: true,
          throwOnError: false,
        }),
        { ADD_TAGS: ['annotation'], ADD_ATTR: ['encoding'] },
      );
      code.parentElement.replaceWith(block);
    } else highlightCode(code, code.textContent, language);
  });
  scheduleDiagrams(target);
  target.querySelectorAll('a,img').forEach((node) => {
    const url = node.getAttribute(node.tagName === 'IMG' ? 'src' : 'href');
    if (!/^\/attachments\/[a-f0-9-]+\.pdf(?:#.*)?$/i.test(url || '')) return;
    const frame = document.createElement('iframe');
    frame.className = 'embed-pdf';
    frame.src = url;
    frame.title = node.textContent || node.alt || 'PDF 附件';
    frame.loading = 'lazy';
    node.replaceWith(frame);
  });
  if (depth >= 2) return;
  target.querySelectorAll('img').forEach((node) => {
    const url = node.getAttribute('src');
    if (!/^\/attachments\/[a-f0-9-]+\.(md|markdown)$/i.test(url || '')) return;
    const block = document.createElement('div');
    block.className = 'markdown-embed';
    block.dataset.url = url;
    block.textContent = '加载 Markdown 附件…';
    node.replaceWith(block);
    fetch(url)
      .then((r) => {
        if (!r.ok) throw Error('附件不存在');
        return r.text();
      })
      .then((text) => {
        if (block.isConnected) renderMarkdown(text, block, depth + 1);
      })
      .catch(() => (block.textContent = 'Markdown 附件加载失败'));
  });
}

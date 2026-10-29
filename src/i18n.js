import catalog from '../locales/catalog.json';
const supported = new Set(['zh-CN', 'en-US']);
let language =
  localStorage.getItem('pagenote.language') ||
  (navigator.language.startsWith('zh') ? 'zh-CN' : 'en-US');
if (!supported.has(language)) language = 'en-US';
const originalText = new WeakMap(),
  originalAttributes = new WeakMap();
export function t(source) {
  const key = String(source).trim(),
    entry = catalog[key],
    english = language === 'en-US';
  if (entry) return entry[english ? 1 : 0];
  let match;
  if ((match = key.match(/^第\s*(\d+)\s*页\s*·\s*知识与讲解$/)))
    return english
      ? `Page ${match[1]} · Notes`
      : `第 ${match[1]} 页 · 页面笔记`;
  if ((match = key.match(/^第\s*(\d+)\s*页\s*·\s*共\s*(\d+)\s*页$/)))
    return english
      ? `Page ${match[1]} of ${match[2]}`
      : `第 ${match[1]} 页，共 ${match[2]} 页`;
  if ((match = key.match(/^([\d,]+) 字(.*)$/)))
    return english
      ? `${match[1]} characters${match[2].replace('字号', 'Zoom')}`
      : key;
  if ((match = key.match(/^(\d+) 号 · (整体要求|第 (\d+) 页)$/)))
    return english
      ? `#${match[1]} · ${match[3] ? 'Page ' + match[3] : 'General instruction'}`
      : `${match[1]} 号 · ${match[2]}`;
  if ((match = key.match(/^修改 (\d+) 号批注$/)))
    return english ? `Edit annotation #${match[1]}` : `编辑 ${match[1]} 号标注`;
  if ((match = key.match(/^⌃ (原文|笔记) · AI 改进工作台$/)))
    return english
      ? `⌃ ${match[1] === '原文' ? 'Source' : 'Notes'} · AI annotation canvas`
      : `⌃ ${match[1] === '原文' ? '文档' : '笔记'} · AI 标注画布`;
  if ((match = key.match(/^(.+) · 连续滚动$/)))
    return english ? `${match[1]} · Continuous scrolling` : key;
  if (english && key.includes('连接上层 AI'))
    return 'Connect your AI client through MCP to read documents, edit notes and process annotations.';
  return source;
}
function isContent(element) {
  if (!element) return true;
  if (element.closest('.note-empty')) return false;
  if (element.closest('.rich-code-tools')) return false;
  if (
    element.closest('.diagram-error') &&
    element.tagName !== 'PRE' &&
    !element.closest('pre')
  )
    return false;
  return !!element.closest(
    '#preview,.markdown-page,.textLayer,#reviewQuote,#historyPreview,#docName,#library,#shelfRecent option:not(:first-child),.book-title,.book-cover,#shelfDirectory,#bookshelfDirectory,.improvement-card p,.improvement-card blockquote,.review-card p,.review-card blockquote,script,style,textarea,input,svg',
  );
}
function translateElement(root) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = walker.nextNode())) {
    if (!node.parentElement || isContent(node.parentElement)) continue;
    const previous = originalText.get(node),
      source =
        previous && node.nodeValue === previous.result
          ? previous.source
          : node.nodeValue,
      trimmed = source.trim();
    if (!trimmed) continue;
    const result = t(trimmed),
      value = result === trimmed ? source : source.replace(trimmed, result);
    originalText.set(node, { source, result: value });
    if (node.nodeValue !== value) node.nodeValue = value;
  }
  for (const element of [root, ...root.querySelectorAll('*')]) {
    if (
      element.nodeType !== 1 ||
      isContent(
        element.matches('input,textarea') ? element.parentElement : element,
      )
    )
      continue;
    const previous = originalAttributes.get(element) || {};
    for (const name of ['title', 'placeholder', 'aria-label']) {
      if (!element.hasAttribute(name)) continue;
      const current = element.getAttribute(name),
        old = previous[name],
        source = old && current === old.result ? old.source : current,
        result = t(source);
      previous[name] = { source, result };
      if (result !== current) element.setAttribute(name, result);
    }
    originalAttributes.set(element, previous);
  }
  document.documentElement.lang = language;
}
let frame;
export function applyLanguage() {
  cancelAnimationFrame(frame);
  translateElement(document.body);
}
export function getLanguage() {
  return language;
}
export async function setLanguage(next) {
  if (!supported.has(next)) throw Error('Unsupported language');
  const response = await fetch('/api/preferences', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ language: next }),
  });
  if (!response.ok) throw Error('Unable to save language');
  language = next;
  localStorage.setItem('pagenote.language', next);
  applyLanguage();
  document.dispatchEvent(new CustomEvent('language-changed', { detail: next }));
}
export function initializeLanguage() {
  applyLanguage();
  const observer = new MutationObserver((mutations) => {
    if (
      !mutations.some(
        (m) =>
          !isContent(
            m.target.nodeType === 1 ? m.target : m.target.parentElement,
          ),
      )
    )
      return;
    cancelAnimationFrame(frame);
    frame = requestAnimationFrame(() => translateElement(document.body));
  });
  observer.observe(document.body, {
    subtree: true,
    childList: true,
    characterData: true,
    attributes: true,
    attributeFilter: ['title', 'placeholder', 'aria-label'],
  });
  return fetch('/api/preferences')
    .then((r) => r.json())
    .then((preferences) => {
      if (supported.has(preferences.language)) {
        language = preferences.language;
        localStorage.setItem('pagenote.language', language);
      }
      applyLanguage();
    })
    .catch(() => applyLanguage());
}

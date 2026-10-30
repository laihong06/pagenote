import TurndownService from 'turndown';
import { gfm } from 'turndown-plugin-gfm';
import DOMPurify from 'dompurify';
const converter = new TurndownService({
  headingStyle: 'atx',
  codeBlockStyle: 'fenced',
  bulletListMarker: '-',
  emDelimiter: '*',
  blankReplacement: (content, node) =>
    node.classList?.contains('mermaid-diagram')
      ? '\n\n```mermaid\n' + (node.dataset.source || '') + '\n```\n\n'
      : node.isBlock
        ? '\n\n'
        : '',
});
converter.use(gfm);
export function codeText(code) {
  const copy = code.cloneNode(true);
  copy
    .querySelectorAll('br')
    .forEach((n) => n.replaceWith(document.createTextNode('\n')));
  copy.querySelectorAll('div,p').forEach((n) => {
    if (n.previousSibling) n.before(document.createTextNode('\n'));
  });
  return copy.textContent;
}
converter.addRule('math', {
  filter: (node) =>
    node.classList?.contains('katex') || node.tagName === 'ANNOTATION',
  replacement: (content, node) => {
    const value =
      node.querySelector('annotation[encoding="application/x-tex"]')
        ?.textContent || node.textContent;
    return node.closest('.katex-display')
      ? '\n\n$$\n' + value + '\n$$\n\n'
      : '$' + value + '$';
  },
});
converter.keep(['u']);
converter.addRule('diagram', {
  filter: (n) => n.classList?.contains('mermaid-diagram'),
  replacement: (c, n) =>
    '\n\n```mermaid\n' + (n.dataset.source || '') + '\n```\n\n',
});
converter.addRule('editorChrome', {
  filter: (n) => n.classList?.contains('rich-code-tools'),
  replacement: () => '',
});
converter.addRule('pdfAttachment', {
  filter: (n) => n.classList?.contains('embed-pdf'),
  replacement: (c, n) => '\n\n[PDF 附件](' + n.getAttribute('src') + ')\n\n',
});
converter.addRule('markdownAttachment', {
  filter: (n) => n.classList?.contains('markdown-embed') && !!n.dataset.url,
  replacement: (c, n) => '\n\n![Markdown 附件](' + n.dataset.url + ')\n\n',
});
converter.addRule('editableCode', {
  filter: (n) => n.tagName === 'PRE' && !!n.querySelector('code'),
  replacement: (c, n) => {
    const code = n.querySelector('code'),
      text = codeText(code).replace(/\n$/, ''),
      language =
        [...code.classList].find((c) => c.startsWith('language-'))?.slice(9) ||
        '',
      fence = '`'.repeat(
        Math.max(3, ...(text.match(/`+/g) || []).map((s) => s.length + 1)),
      );
    return (
      '\n\n' +
      fence +
      (language === 'plaintext' ? '' : language) +
      '\n' +
      text +
      '\n' +
      fence +
      '\n\n'
    );
  },
});
export function htmlToMarkdown(html) {
  const holder = document.createElement('div');
  holder.innerHTML = html;
  holder
    .querySelectorAll('.mermaid-diagram,.markdown-s-block,.markdown-s-native')
    .forEach((n) => {
      const pre = document.createElement('pre'),
        code = document.createElement('code');
      code.className =
        'language-' +
        (n.classList.contains('markdown-s-block') ||
        n.classList.contains('markdown-s-native')
          ? n.dataset.engine
          : 'mermaid');
      code.textContent = n.getAttribute('data-source') || '';
      pre.append(code);
      n.replaceWith(pre);
    });
  const clean = DOMPurify.sanitize(holder.innerHTML, {
    ADD_TAGS: ['annotation'],
    ADD_ATTR: ['encoding'],
  });
  return converter.turndown(clean);
}
export function setupMarkdownTools({
  editor,
  changed,
  attach,
  paste,
  canEdit,
  rich,
}) {
  function insert(before, after = '', placeholder = '文字') {
    if (!canEdit()) return;
    const start = editor.selectionStart,
      end = editor.selectionEnd,
      selected =
        placeholder === '' ? '' : editor.value.slice(start, end) || placeholder,
      value = before + selected + after;
    editor.focus();
    editor.setSelectionRange(start, end);
    if (!document.execCommand('insertText', false, value)) {
      editor.setRangeText(value, start, end, 'end');
      changed();
    }
    editor.setSelectionRange(
      start + before.length,
      start + before.length + selected.length,
    );
    changed();
  }
  function prefix(text) {
    if (!canEdit()) return;
    const start = editor.value.lastIndexOf('\n', editor.selectionStart - 1) + 1,
      found = editor.value.indexOf('\n', editor.selectionEnd),
      end = found < 0 ? editor.value.length : found;
    editor.setSelectionRange(start, end);
    const value = editor.value
      .slice(start, end)
      .split('\n')
      .map((line) => text + line)
      .join('\n');
    insert(value, '', '');
  }
  const actions = {
    bold: () => insert('**', '**'),
    italic: () => insert('*', '*'),
    strike: () => insert('~~', '~~'),
    underline: () => insert('<u>', '</u>'),
    bullet: () => prefix('- '),
    ordered: () => prefix('1. '),
    task: () => prefix('- [ ] '),
    quote: () => prefix('> '),
    code: () => insert('\n```python\n', '\n```\n', '代码'),
    math: () => insert('\n\n$$\n', '\n$$\n\n', 'E=mc^2'),
    table: () =>
      insert('\n\n| 列一 | 列二 |\n| --- | --- |\n| 内容 | 内容 |\n\n', '', ''),
    mermaid: () =>
      insert(
        '\n\n```mermaid\nflowchart LR\n  A[开始] --> B[处理] --> C[结束]\n```\n\n',
        '',
        '',
      ),
    link: () => insert('[', '](https://example.com)', '链接文字'),
  };
  document.querySelectorAll('[data-md]').forEach((b) => {
    b.onpointerdown = (e) => e.preventDefault();
    b.onclick = () =>
      rich ? rich.command(b.dataset.md) : actions[b.dataset.md]?.();
  });
  document.getElementById('headingTool').onchange = (e) => {
    if (e.target.value) {
      if (rich) rich.heading(e.target.value);
      else prefix('#'.repeat(Number(e.target.value)) + ' ');
    }
    e.target.value = '';
  };
  document.getElementById('mdImage').onclick = attach;
  document.getElementById('mdPaste').onclick = () => paste('note');
  editor.addEventListener('keydown', (e) => {
    if (!(e.ctrlKey || e.metaKey) || e.shiftKey) return;
    const action = { b: 'bold', i: 'italic', k: 'link' }[e.key.toLowerCase()];
    if (action) {
      e.preventDefault();
      actions[action]();
    }
  });
  return { insert, actions };
}

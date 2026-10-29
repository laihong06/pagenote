import DOMPurify from 'dompurify';
import { highlightCode } from './code-highlight';
import { renderMarkdown, renderDiagrams } from './markdown.js';
import { htmlToMarkdown, codeText } from './editing.js';

export function createRichEditor({ root, changed, canEdit, toast }) {
  let selection = null,
    loading = false,
    lastMarkdown = '',
    undo = [],
    redo = [],
    lastEdit = 0;
  const cleanHTML = (html) =>
    DOMPurify.sanitize(html, {
      ADD_TAGS: ['annotation'],
      ADD_ATTR: ['encoding'],
    });
  function remember() {
    const s = getSelection();
    if (s.rangeCount && root.contains(s.anchorNode))
      selection = s.getRangeAt(0).cloneRange();
  }
  document.addEventListener('selectionchange', remember);
  function restore() {
    root.focus();
    const s = getSelection();
    if (selection && root.contains(selection.startContainer)) {
      s.removeAllRanges();
      s.addRange(selection);
    }
    const node =
      s.anchorNode?.nodeType === 1 ? s.anchorNode : s.anchorNode?.parentElement;
    if (!node?.isContentEditable) {
      const r = document.createRange();
      r.selectNodeContents(root);
      r.collapse(false);
      s.removeAllRanges();
      s.addRange(r);
    }
  }
  function emit() {
    if (loading || !canEdit()) return;
    const markdown = htmlToMarkdown(root.innerHTML);
    if (markdown !== lastMarkdown) {
      if (Date.now() - lastEdit > 500 || !undo.length) undo.push(lastMarkdown);
      if (undo.length > 80) undo.shift();
      redo = [];
      lastEdit = Date.now();
      lastMarkdown = markdown;
    }
    root.dataset.empty = String(!markdown.trim());
    changed(markdown);
  }
  function decorate() {
    renderDiagrams(root);
    root
      .querySelectorAll(
        '.katex-display,.math-block,.mermaid-diagram,.markdown-embed,.markdown-s-block',
      )
      .forEach((n) => {
        n.contentEditable = 'false';
        n.title = '双击修改公式或图表';
      });
    root
      .querySelectorAll('.katex')
      .forEach((n) => (n.contentEditable = 'false'));
    root
      .querySelectorAll('input[type=checkbox]')
      .forEach((n) => (n.disabled = !canEdit()));
    root.querySelectorAll('pre').forEach((pre) => {
      const code = pre.querySelector('code');
      if (
        !code ||
        pre.closest('.markdown-s-block') ||
        pre.querySelector('.rich-code-tools')
      )
        return;
      pre.classList.add('rich-code-block');
      pre.contentEditable = 'false';
      code.contentEditable = String(canEdit());
      code.tabIndex = 0;
      code.spellcheck = false;
      const tools = document.createElement('div');
      tools.className = 'rich-code-tools';
      tools.contentEditable = 'false';
      const language = document.createElement('select');
      language.setAttribute('aria-label', '代码语言');
      for (const value of [
        'plaintext',
        'mermaid',
        'python',
        'javascript',
        'typescript',
        'cpp',
        'json',
        'bash',
        'sql',
        'html',
        'css',
      ])
        language.add(
          new Option(value === 'plaintext' ? '纯文本' : value, value),
        );
      const current =
        [...code.classList].find((c) => c.startsWith('language-'))?.slice(9) ||
        'plaintext';
      if (![...language.options].some((o) => o.value === current))
        language.add(new Option(current, current));
      language.value = current;
      language.onchange = () => {
        code.className = 'language-' + language.value;
        highlight(code);
        decorate();
        emit();
      };
      const leave = document.createElement('button');
      leave.textContent = '下一段';
      leave.onclick = () => {
        const p = document.createElement('p');
        p.innerHTML = '<br>';
        pre.after(p);
        const r = document.createRange();
        r.selectNodeContents(p);
        r.collapse(true);
        selection = r;
        restore();
        emit();
      };
      tools.append(language, leave);
      pre.prepend(tools);
    });
  }
  function editDiagram(figure) {
    if (!canEdit()) return;
    figure.contentEditable = 'false';
    root.querySelectorAll('.rich-diagram-source').forEach((n) => {
      if (n.nextElementSibling !== figure) n.remove();
    });
    let panel = figure.previousElementSibling;
    if (!panel?.classList.contains('rich-diagram-source')) {
      panel = document.createElement('div');
      panel.className = 'rich-diagram-source rich-code-tools';
      panel.contentEditable = 'false';
      const label = document.createElement('div');
      const enhanced = figure.classList.contains('markdown-s-block');
      label.textContent = enhanced ? figure.dataset.engine : 'mermaid';
      const input = document.createElement('textarea');
      input.setAttribute(
        'aria-label',
        enhanced ? 'Markdown S 源码' : 'Mermaid 代码',
      );
      input.spellcheck = false;
      input.placeholder = enhanced
        ? '输入 Markdown S 源码'
        : '输入 Mermaid 代码';
      input.rows = 2;
      input.value = figure.dataset.source || '';
      const resize = () => {
        input.style.height = 'auto';
        input.style.height = Math.max(48, input.scrollHeight) + 'px';
      };
      input.oninput = () => {
        resize();
        figure.dataset.source = input.value;
        if (enhanced) {
          figure.querySelector('figcaption button:last-child')?.click();
          figure.querySelector('details pre').textContent = input.value;
          emit();
          return;
        }
        figure.classList.remove('diagram-ready', 'diagram-error');
        delete figure.dataset.running;
        renderDiagrams(root);
        emit();
      };
      panel.append(label, input);
      figure.before(panel);
    }
    panel.hidden = false;
    const input = panel.querySelector('textarea');
    input.focus();
    input.style.height = 'auto';
    input.style.height = Math.max(48, input.scrollHeight) + 'px';
    renderDiagrams(root);
  }
  function highlight(code) {
    const current = getSelection(),
      inside =
        (document.activeElement === code || document.activeElement === root) &&
        current?.rangeCount &&
        code.contains(current.anchorNode) &&
        code.contains(current.focusNode);
    const offset = (node, pos) => {
      const r = document.createRange();
      r.selectNodeContents(code);
      r.setEnd(node, pos);
      const holder = document.createElement('div');
      holder.append(r.cloneContents());
      return codeText(holder).length;
    };
    const anchor = inside
        ? offset(current.anchorNode, current.anchorOffset)
        : 0,
      focus = inside ? offset(current.focusNode, current.focusOffset) : 0;
    const text = codeText(code),
      language = [...code.classList]
        .find((c) => c.startsWith('language-'))
        ?.slice(9);
    highlightCode(code, text, language);
    if (inside) {
      const position = (offset) => {
        const walker = document.createTreeWalker(code, NodeFilter.SHOW_TEXT);
        let node,
          last = null;
        while ((node = walker.nextNode())) {
          last = node;
          if (offset <= node.length) return [node, offset];
          offset -= node.length;
        }
        return last ? [last, last.length] : [code, 0];
      };
      const a = position(anchor),
        f = position(focus);
      current.setBaseAndExtent(a[0], a[1], f[0], f[1]);
      remember();
    }
  }
  function load(markdown) {
    loading = true;
    selection = null;
    lastMarkdown = markdown;
    undo = [];
    redo = [];
    renderMarkdown(markdown || '<p><br></p>', root);
    decorate();
    setEditable(canEdit());
    loading = false;
  }
  function insertHTML(html) {
    if (!canEdit()) return;
    restore();
    const range = getSelection().getRangeAt(0),
      holder = document.createElement('div');
    holder.innerHTML = cleanHTML(html);
    const fragment = document.createDocumentFragment();
    while (holder.firstChild) fragment.append(holder.firstChild);
    const last = fragment.lastChild;
    range.deleteContents();
    range.insertNode(fragment);
    if (last) {
      const caret = document.createRange();
      caret.selectNodeContents(last);
      caret.collapse(false);
      selection = caret;
      getSelection().removeAllRanges();
      getSelection().addRange(caret);
    }
    decorate();
    emit();
    remember();
  }
  function insertMarkdown(markdown) {
    if (!canEdit()) return;
    restore();
    const holder = document.createElement('div');
    renderMarkdown(markdown, holder);
    const range = getSelection().getRangeAt(0),
      fragment = document.createDocumentFragment();
    while (holder.firstChild) fragment.append(holder.firstChild);
    const paragraph = document.createElement('p');
    paragraph.innerHTML = '<br>';
    fragment.append(paragraph);
    range.deleteContents();
    range.insertNode(fragment);
    const r = document.createRange();
    r.selectNodeContents(paragraph);
    r.collapse(true);
    selection = r;
    getSelection().removeAllRanges();
    getSelection().addRange(r);
    decorate();
    lastEdit = 0;
    emit();
  }
  const actions = {
    copy: () => document.execCommand('copy'),
    cut: () => document.execCommand('cut'),
    selectAll: () => {
      const range = document.createRange();
      range.selectNodeContents(root);
      const s = getSelection();
      s.removeAllRanges();
      s.addRange(range);
    },
    bold: () => document.execCommand('bold'),
    italic: () => document.execCommand('italic'),
    strike: () => document.execCommand('strikeThrough'),
    underline: () => document.execCommand('underline'),
    bullet: () => document.execCommand('insertUnorderedList'),
    ordered: () => document.execCommand('insertOrderedList'),
    quote: () => document.execCommand('formatBlock', false, 'blockquote'),
    task: () =>
      insertHTML('<ul><li><input type="checkbox"> 待办事项</li></ul>'),
    code: () => {
      const selected = getSelection()?.toString() || '';
      const pre = document.createElement('pre'),
        code = document.createElement('code'),
        id = crypto.randomUUID();
      pre.dataset.richId = id;
      code.className = 'language-plaintext';
      if (selected) code.textContent = selected;
      else code.innerHTML = '<br>';
      pre.append(code);
      insertHTML(pre.outerHTML + '<p><br></p>');
      const node = root.querySelector(`[data-rich-id="${id}"] code`);
      if (node) {
        node.focus();
        const r = document.createRange();
        r.selectNodeContents(node);
        r.collapse(true);
        selection = r;
        const s = getSelection();
        s.removeAllRanges();
        s.addRange(r);
      }
    },
    table: () =>
      insertHTML(
        '<table><thead><tr><th>列一</th><th>列二</th></tr></thead><tbody><tr><td>内容</td><td>内容</td></tr></tbody></table><p><br></p>',
      ),
    math: () => editSpecial(null, 'math'),
    mermaid: () => {
      const empty = [...root.querySelectorAll('.mermaid-diagram')].find(
        (n) => !n.dataset.source?.trim(),
      );
      if (empty) {
        editDiagram(empty);
        return;
      }
      const id = crypto.randomUUID();
      insertHTML(
        '<pre data-rich-id="' +
          id +
          '"><code class="language-mermaid"><br></code></pre><p><br></p>',
      );
      const figure = root.querySelector('[data-rich-id="' + id + '"]');
      if (figure) editDiagram(figure);
    },
    link: () => editSpecial(null, 'link'),
  };
  function command(name) {
    if (!canEdit()) return;
    if (name === 'undo' || name === 'redo') {
      undoRedo(name === 'undo');
      return;
    }
    lastEdit = 0;
    restore();
    actions[name]?.();
    decorate();
    emit();
  }
  function heading(level) {
    if (!canEdit()) return;
    restore();
    document.execCommand('formatBlock', false, 'h' + level);
    emit();
  }
  const dialog = document.createElement('dialog');
  dialog.id = 'richBlockDialog';
  dialog.innerHTML =
    '<h2 id="richBlockTitle">修改内容</h2><textarea id="richBlockSource" rows="8" spellcheck="false"></textarea><div class="history-actions"><button id="richBlockCancel">取消</button><button id="richBlockApply" class="primary">应用</button></div>';
  document.body.append(dialog);
  let target = null,
    kind = null;
  function editSpecial(node, type) {
    if (!canEdit()) return;
    remember();
    target = node;
    kind = type;
    document.getElementById('richBlockTitle').textContent = {
      math: '公式',
      mermaid: '流程图 / 图表',
      link: '链接地址',
    }[type];
    document.getElementById('richBlockSource').value = node
      ? type === 'mermaid'
        ? node.dataset.source
        : node.querySelector('annotation[encoding="application/x-tex"]')
            ?.textContent || ''
      : {
          math: 'E=mc^2',
          mermaid: 'flowchart LR\n  A[开始] --> B[处理] --> C[结束]',
          link: 'https://',
        }[type];
    dialog.showModal();
  }
  document.getElementById('richBlockCancel').onclick = () => dialog.close();
  document.getElementById('richBlockApply').onclick = () => {
    const value = document.getElementById('richBlockSource').value;
    dialog.close();
    if (!canEdit()) return;
    if (kind === 'link') {
      restore();
      document.execCommand('createLink', false, value);
      emit();
      return;
    }
    const markdown =
      kind === 'math'
        ? '\n\n$$\n' + value + '\n$$\n\n'
        : '\n\n```mermaid\n' + value + '\n```\n\n';
    if (target?.isConnected) {
      const temp = document.createElement('div');
      renderMarkdown(markdown, temp);
      const fresh = temp.firstElementChild;
      target.replaceWith(fresh);
      decorate();
      emit();
    } else insertMarkdown(markdown);
  };
  root.addEventListener('dblclick', (e) => {
    const node = e.target.closest(
      '.mermaid-diagram,.markdown-s-block,.katex-display,.math-block,.katex',
    );
    if (node) {
      if (
        node.classList.contains('mermaid-diagram') ||
        node.classList.contains('markdown-s-block')
      )
        editDiagram(node);
      else editSpecial(node, 'math');
    }
  });
  root.addEventListener('input', (e) => {
    if (e.target.matches('textarea')) return;
    const code =
      e.target.closest('pre code') ||
      getSelection()?.anchorNode?.parentElement?.closest('pre code');
    if (code && !e.isComposing) highlight(code);
    decorate();
    emit();
  });
  root.addEventListener('compositionend', (e) => {
    const code =
      e.target.closest('pre code') ||
      getSelection()?.anchorNode?.parentElement?.closest('pre code');
    if (code) highlight(code);
    emit();
  });
  root.addEventListener('focusout', (e) => {
    if (e.target.matches('.rich-diagram-source textarea')) {
      const panel = e.target.parentElement;
      setTimeout(() => {
        if (panel.isConnected && !panel.contains(document.activeElement)) {
          panel.remove();
        }
      }, 0);
    }
    if (e.target.matches('pre code')) {
      highlight(e.target);
      emit();
    }
  });
  root.addEventListener('keydown', (e) => {
    if (e.target.matches('textarea')) return;
    const anchor = getSelection()?.anchorNode,
      paragraph = (
        anchor?.nodeType === 1 ? anchor : anchor?.parentElement
      )?.closest('p,div');
    const line =
      paragraph && root.contains(paragraph) && paragraph !== root
        ? paragraph
        : anchor?.nodeType === 3 && anchor.parentNode === root
          ? anchor
          : null;
    if (e.key === 'Enter' && line && /^```mermaid\s*$/.test(line.textContent)) {
      e.preventDefault();
      const figure = document.createElement('figure');
      figure.className = 'mermaid-diagram';
      figure.dataset.source = '';
      line.replaceWith(figure);
      editDiagram(figure);
      emit();
      return;
    }
    const code =
      e.target.closest('pre code') ||
      getSelection()?.anchorNode?.parentElement?.closest('pre code');
    if (code && e.key === 'Enter') {
      e.preventDefault();
      document.execCommand('insertText', false, '\n');
      emit();
    } else if (code && e.key === 'Tab') {
      e.preventDefault();
      document.execCommand('insertText', false, '  ');
      emit();
    } else if (
      (e.ctrlKey || e.metaKey) &&
      !e.shiftKey &&
      { b: 1, i: 1, k: 1 }[e.key.toLowerCase()]
    ) {
      e.preventDefault();
      command({ b: 'bold', i: 'italic', k: 'link' }[e.key.toLowerCase()]);
    }
  });
  root.addEventListener('paste', (e) => {
    if (e.target.matches('textarea')) {
      e.stopPropagation();
      return;
    }
    if (e.clipboardData.files.length) return;
    e.preventDefault();
    e.stopPropagation();
    const text = e.clipboardData.getData('text/plain'),
      html = e.clipboardData.getData('text/html');
    if (
      e.target.closest('pre code') ||
      getSelection()?.anchorNode?.parentElement?.closest('pre code')
    )
      document.execCommand('insertText', false, text);
    else if (html) insertHTML(html);
    else insertMarkdown(text);
    emit();
  });
  root.addEventListener('change', (e) => {
    if (e.target.matches('input[type=checkbox]')) {
      e.target.toggleAttribute('checked', e.target.checked);
      emit();
    }
  });
  function setEditable(value) {
    if (!value)
      root.querySelectorAll('.rich-diagram-source').forEach((n) => n.remove());
    root.contentEditable = String(value);
    root
      .querySelectorAll('pre code')
      .forEach((n) => (n.contentEditable = String(value)));
    root
      .querySelectorAll(
        '.rich-code-tools select,.rich-code-tools button,input[type=checkbox]',
      )
      .forEach((n) => (n.disabled = !value));
  }
  function undoRedo(back) {
    if (!canEdit()) return;
    const from = back ? undo : redo,
      to = back ? redo : undo;
    if (!from.length) return;
    to.push(lastMarkdown);
    lastMarkdown = from.pop();
    loading = true;
    renderMarkdown(lastMarkdown || '<p><br></p>', root);
    decorate();
    setEditable(true);
    selection = null;
    loading = false;
    changed(lastMarkdown);
  }
  return {
    load,
    command,
    heading,
    insertMarkdown,
    emit,
    decorate,
    setEditable,
    undo: () => undoRedo(true),
    redo: () => undoRedo(false),
  };
}

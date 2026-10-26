import {
  createElement,
  BookOpen,
  FolderOpen,
  Plus,
  Download,
  Settings,
  Search,
  RefreshCw,
  List,
  LayoutGrid,
  EllipsisVertical,
  ArrowLeft,
} from 'lucide';
import { getLanguage, t } from './i18n.js';
import { createBookCovers } from './book-covers.js';
import './bookshelf.css';

const $ = (id) => document.getElementById(id);
const json = (body) => ({
  method: 'PUT',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});
function icon(node) {
  return createElement(node, { width: 22, height: 22, 'aria-hidden': 'true' });
}
function button(id, node, label, action, text = false) {
  const el = document.createElement('button');
  el.id = id;
  el.type = 'button';
  el.title = label;
  el.setAttribute('aria-label', label);
  el.append(icon(node));
  if (text) {
    const span = document.createElement('span');
    span.textContent = label;
    el.append(span);
  }
  el.onclick = action;
  return el;
}
export function createBookshelf({
  request,
  openBook,
  onNew,
  onImport,
  onSettings,
  onError,
  pdfjs,
  loadRecent = async () => [],
}) {
  let snapshot = {
      config: { root: null, view: 'grid', sort: 'updated' },
      books: [],
      warnings: [],
    },
    visible = false,
    sequence = 0,
    pending;
  const covers = createBookCovers({ pdfjs });
  const root = document.createElement('section');
  root.id = 'bookshelf';
  root.hidden = true;
  root.innerHTML = `<header class="shelf-toolbar"><h1><span id="shelfLogo"></span><span>书架</span></h1><label class="shelf-search"><span id="shelfSearchIcon"></span><input id="shelfSearch" type="search" placeholder="搜索笔记本" aria-label="搜索笔记本"></label><div id="shelfTools"></div></header><div class="shelf-location"><span id="shelfDirectory"></span><span id="shelfCount"></span><div id="shelfActions"></div></div><p id="shelfStatus" role="status"></p><div id="shelfGrid" data-view="grid"></div>`;
  document.body.append(root);
  $('shelfLogo').append(icon(BookOpen));
  $('shelfSearchIcon').append(icon(Search));
  const guard =
    (fn) =>
    (...args) =>
      Promise.resolve()
        .then(() => fn(...args))
        .catch(onError);
  const sort = document.createElement('select');
  sort.id = 'shelfSort';
  sort.setAttribute('aria-label', t('排序'));
  sort.append(
    new Option(t('最近更新'), 'updated'),
    new Option(t('名称'), 'name'),
  );
  sort.onchange = guard(async () => {
    snapshot.config = await request(
      '/api/bookshelf/config',
      json({ sort: sort.value }),
    );
    render();
  });
  const recent = document.createElement('select');
  recent.id = 'shelfRecent';
  recent.setAttribute('aria-label', '最近文档');
  recent.append(new Option(t('最近文档'), ''));
  recent.onchange = guard(async () => {
    const id = recent.value;
    recent.value = '';
    if (id) await window.pageNotes.open(id);
  });
  $('shelfTools').append(
    recent,
    sort,
    button(
      'shelfView',
      List,
      '切换网格或列表',
      guard(async () => {
        snapshot.config = await request(
          '/api/bookshelf/config',
          json({ view: snapshot.config.view === 'grid' ? 'list' : 'grid' }),
        );
        render();
      }),
    ),
    button(
      'shelfRefresh',
      RefreshCw,
      '刷新书架',
      guard(() => refresh()),
    ),
    button('shelfSettings', Settings, '设置', guard(onSettings)),
  );
  $('shelfActions').append(
    button(
      'shelfBind',
      FolderOpen,
      '选择书架目录',
      guard(() => chooseDirectory()),
      true,
    ),
    button('shelfNew', Plus, '新建项目', guard(onNew), true),
    button('shelfImport', Download, '导入文档', guard(onImport), true),
  );
  const back = button(
    'bookshelfButton',
    ArrowLeft,
    '返回书架',
    guard(() => window.pageNotes.showBookshelf()),
    false,
  );
  document.querySelector('.document-pane .pane-heading').prepend(back);
  $('shelfSearch').oninput = render;
  async function chooseDirectory() {
    const selected = await window.desktopSettings?.chooseBookshelfDirectory();
    if (!selected) return;
    snapshot.config = await request(
      '/api/bookshelf/config',
      json({ root: selected }),
    );
    await refresh();
    window.desktopSettings?.sync();
  }
  function render() {
    if (!visible) return;
    covers.clear(true);
    const grid = $('shelfGrid');
    grid.replaceChildren();
    grid.dataset.view = snapshot.config.view;
    sort.value = snapshot.config.sort;
    sort.options[0].textContent = t('最近更新');
    sort.options[1].textContent = t('名称');
    recent.options[0].textContent = t('最近文档');
    $('shelfNew').disabled = !snapshot.config.root;
    $('shelfImport').disabled = !snapshot.config.root;
    $('shelfView').replaceChildren(
      icon(snapshot.config.view === 'grid' ? List : LayoutGrid),
    );
    $('shelfDirectory').textContent =
      snapshot.config.root || t('尚未绑定书架目录');
    $('shelfDirectory').title = snapshot.config.root || '';
    $('shelfBind').hidden = !!snapshot.config.root;
    const query = $('shelfSearch').value.trim().toLocaleLowerCase();
    const books = snapshot.books
      .filter((book) => book.title.toLocaleLowerCase().includes(query))
      .sort((a, b) =>
        snapshot.config.sort === 'name'
          ? a.title.localeCompare(b.title, getLanguage())
          : b.updated - a.updated,
      );
    $('shelfCount').textContent = `${books.length} ${t('本笔记')}`;
    $('shelfStatus').textContent =
      snapshot.warnings.join('\n') ||
      (!snapshot.config.root
        ? t('选择书架目录')
        : !books.length
          ? t(query ? '没有匹配的笔记本' : '书架还是空的')
          : '');
    const add = document.createElement('div');
    add.className = 'shelf-book shelf-new';
    const create = button('shelfNewCover', Plus, '新建项目', guard(onNew));
    create.disabled = !snapshot.config.root;
    create.className = 'book-cover book-add';
    const name = document.createElement('span');
    name.textContent = t('新建项目');
    name.className = 'book-title';
    add.append(create, name);
    grid.append(add);
    for (const book of books) {
      const item = document.createElement('div');
      item.className = 'shelf-book';
      const open = document.createElement('button');
      open.type = 'button';
      open.className = 'book-cover';
      open.dataset.bookKey = book.key;
      open.setAttribute('aria-label', book.title);
      open.onclick = guard(() => openBook(book));
      const details = document.createElement('div');
      details.className = 'book-details';
      const title = document.createElement('button');
      title.className = 'book-title';
      title.textContent = book.title;
      title.title = book.title;
      title.onclick = open.onclick;
      const more = button('', EllipsisVertical, '自定义封面', () =>
        customize(book),
      );
      more.className = 'book-more';
      const date = document.createElement('small');
      date.textContent = new Date(book.updated).toLocaleDateString(
        getLanguage(),
      );
      details.append(title, more, date);
      item.append(open, details);
      grid.append(item);
      covers.observe(open, book);
      if (book.warning) {
        item.title = book.warning;
        item.dataset.warning = 'true';
      }
    }
  }
  async function refresh() {
    const ticket = ++sequence;
    $('shelfStatus').textContent = t('正在扫描书架…');
    try {
      const next = await request('/api/bookshelf');
      const documents = await loadRecent().catch(() => {
        next.warnings.push(t('最近文档读取失败'));
        return [];
      });
      if (ticket !== sequence) return;
      recent.replaceChildren(
        new Option(t('最近文档'), ''),
        ...documents.map((doc) => new Option(doc.name, doc.id)),
      );
      snapshot = next;
      render();
    } catch (error) {
      if (ticket !== sequence) return;
      covers.clear();
      snapshot.books = [];
      $('shelfGrid').replaceChildren();
      $('shelfStatus').textContent = error.message;
      $('shelfBind').hidden = false;
    }
  }
  function scheduleRefresh() {
    if (!visible || pending) return;
    pending = setTimeout(() => {
      pending = null;
      refresh();
    }, 250);
  }
  window.addEventListener('focus', scheduleRefresh);
  document.addEventListener('language-changed', render);
  const dialog = document.createElement('dialog');
  dialog.id = 'bookAppearance';
  dialog.innerHTML = `<form method="dialog"><h2>自定义封面</h2><label>显示书名<input id="bookTitle" maxlength="200"></label><label>封面<select id="bookCover"><option value="default">默认封面</option><option value="paper">笔记本封面</option><option value="pdf">PDF 页面</option><option value="image">图片封面</option></select></label><label id="bookPageField">封面页码<input id="bookPage" type="number" min="1" step="1"></label><fieldset id="bookColors"><legend>纸本配色</legend></fieldset><label>封面图片<input id="bookImage" type="file" accept="image/png,image/jpeg,image/webp"></label><p id="bookError" role="alert"></p><div class="shelf-dialog-actions"><button type="button" id="bookReset">恢复默认</button><button value="cancel">取消</button><button type="button" id="bookSave" class="primary">保存</button></div></form>`;
  document.body.append(dialog);
  let editing,
    color = 'sage';
  for (const paper of ['sage', 'rose', 'sky', 'graphite']) {
    const swatch = document.createElement('button');
    swatch.type = 'button';
    swatch.dataset.paper = paper;
    swatch.title = paper;
    swatch.setAttribute('aria-label', paper);
    swatch.onclick = () => {
      color = paper;
      $('bookCover').value = 'paper';
      updateSwatches();
    };
    $('bookColors').append(swatch);
  }
  function updateSwatches() {
    dialog
      .querySelectorAll('[data-paper]')
      .forEach((el) =>
        el.setAttribute('aria-pressed', String(el.dataset.paper === color)),
      );
  }
  function customize(book) {
    editing = book;
    color = book.appearance.paper;
    $('bookTitle').value = book.appearance.title || '';
    $('bookCover').value = book.appearance.cover;
    $('bookPage').value = book.appearance.page + 1;
    $('bookImage').value = '';
    $('bookError').textContent = '';
    $('bookCover').querySelector('[value=pdf]').disabled = [
      '.md',
      '.markdown',
    ].includes(book.ext);
    updateSwatches();
    dialog.showModal();
  }
  $('bookImage').onchange = () => {
    if ($('bookImage').files.length) $('bookCover').value = 'image';
  };
  $('bookReset').onclick = () => {
    $('bookTitle').value = '';
    $('bookCover').value = 'default';
    $('bookPage').value = 1;
    color = 'sage';
    $('bookImage').value = '';
    updateSwatches();
  };
  $('bookSave').onclick = async () => {
    $('bookSave').disabled = true;
    try {
      const look = {
        version: 1,
        title: $('bookTitle').value.trim(),
        cover: $('bookCover').value,
        paper: color,
        page: Number($('bookPage').value) - 1,
      };
      const file = $('bookImage').files[0];
      if (look.cover === 'image' && file) {
        const form = new FormData();
        form.append('file', file);
        form.append('appearance', JSON.stringify(look));
        await request(`/api/bookshelf/${editing.key}/cover`, {
          method: 'POST',
          body: form,
        });
      } else {
        if (look.cover === 'image') look.asset = editing.appearance.asset;
        await request(`/api/bookshelf/${editing.key}/appearance`, json(look));
      }
      dialog.close();
      await refresh();
    } catch (error) {
      $('bookError').textContent = error.message;
    } finally {
      $('bookSave').disabled = false;
    }
  };
  return {
    async show() {
      visible = true;
      root.hidden = false;
      document.body.classList.add('bookshelf-view');
      await refresh();
    },
    hide() {
      visible = false;
      sequence++;
      root.hidden = true;
      document.body.classList.remove('bookshelf-view');
      covers.clear();
    },
    refresh,
    chooseDirectory,
    state: () => ({ ...snapshot, visible }),
  };
}

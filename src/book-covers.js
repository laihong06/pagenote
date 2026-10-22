import { t } from './i18n.js';

export function createBookCovers({ pdfjs }) {
  let epoch = 0,
    running = 0,
    bytes = 0;
  const queue = [],
    cache = new Map(),
    tasks = new Set(),
    targets = new Map();
  const observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        const item = targets.get(entry.target);
        if (!item) continue;
        if (entry.isIntersecting && !item.started) {
          item.started = true;
          queue.push(item);
          drain();
        } else if (!entry.isIntersecting && item.done) {
          entry.target.replaceChildren();
          item.started = false;
          item.done = false;
        }
      }
    },
    { rootMargin: '160px' },
  );
  const isPaper = (book) =>
    book.appearance.cover === 'paper' ||
    (['default', 'pdf'].includes(book.appearance.cover) &&
      ['.md', '.markdown'].includes(book.ext));
  function paper(el, book) {
    el.dataset.paper = book.appearance.paper;
    el.classList.add('paper-cover');
    const label = document.createElement('strong');
    label.textContent = 'Note';
    const brand = document.createElement('small');
    brand.textContent = 'pagenote';
    el.replaceChildren(label, brand);
  }
  async function make(book, token) {
    const url = `/api/bookshelf/${book.key}/cover-source?v=${book.revision}`;
    if (book.appearance.cover === 'image') {
      const controller = new AbortController();
      tasks.add(controller);
      try {
        const response = await fetch(url, { signal: controller.signal });
        if (!response.ok) throw Error(t('封面加载失败'));
        return await response.blob();
      } finally {
        tasks.delete(controller);
      }
    }
    const task = pdfjs.getDocument({
      url,
      enableScripting: false,
      isEvalSupported: false,
      cMapUrl: '/pdf-assets/cmaps/',
      cMapPacked: true,
      standardFontDataUrl: '/pdf-assets/standard_fonts/',
      wasmUrl: '/pdf-assets/wasm/',
    });
    tasks.add(task);
    try {
      const pdf = await task.promise;
      if (token !== epoch) throw Error('Canceled');
      const page = await pdf.getPage(
        (book.appearance.cover === 'pdf' ? book.appearance.page : 0) + 1,
      );
      const base = page.getViewport({ scale: 1 });
      const viewport = page.getViewport({
        scale: Math.min(360 / base.width, 480 / base.height),
      });
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.ceil(viewport.width));
      canvas.height = Math.max(1, Math.ceil(viewport.height));
      try {
        await page.render({
          canvasContext: canvas.getContext('2d'),
          viewport,
          background: 'white',
        }).promise;
        return await new Promise((resolve, reject) =>
          canvas.toBlob(
            (blob) => (blob ? resolve(blob) : reject(Error('Empty thumbnail'))),
            'image/png',
          ),
        );
      } finally {
        canvas.width = 0;
        canvas.height = 0;
      }
    } finally {
      tasks.delete(task);
      await task.destroy();
    }
  }
  function retain(key, blob) {
    const entry = { url: URL.createObjectURL(blob), size: blob.size };
    cache.set(key, entry);
    bytes += blob.size;
    while (cache.size > 128 || bytes > 48 * 1024 * 1024) {
      const [oldKey, old] = cache.entries().next().value;
      cache.delete(oldKey);
      bytes -= old.size;
      URL.revokeObjectURL(old.url);
    }
    return entry;
  }
  async function load(item) {
    const token = epoch,
      { el, book } = item,
      key = book.key + ':' + book.revision;
    try {
      let entry = cache.get(key);
      if (entry) {
        cache.delete(key);
        cache.set(key, entry);
      } else {
        const blob = await make(book, token);
        if (token !== epoch) return;
        entry = retain(key, blob);
      }
      if (token !== epoch || !el.isConnected) return;
      const image = new Image();
      image.alt = book.title;
      image.src = entry.url;
      await image.decode();
      if (token === epoch && el.isConnected) {
        el.replaceChildren(image);
        item.done = true;
      }
    } catch {
      if (token !== epoch || !el.isConnected) return;
      const failed = document.createElement('span');
      failed.className = 'cover-error';
      failed.textContent = t('封面加载失败');
      el.replaceChildren(failed);
      item.done = true;
    }
  }
  function drain() {
    while (running < 2 && queue.length) {
      const item = queue.shift();
      running++;
      load(item).finally(() => {
        running--;
        drain();
      });
    }
  }
  function clear(retainCache = false) {
    epoch++;
    observer.disconnect();
    targets.clear();
    queue.length = 0;
    for (const task of tasks) {
      if (task.abort) task.abort();
      else task.destroy().catch(() => {});
    }
    tasks.clear();
    if (!retainCache) {
      for (const entry of cache.values()) URL.revokeObjectURL(entry.url);
      cache.clear();
      bytes = 0;
    }
  }
  return {
    observe(el, book) {
      if (isPaper(book)) {
        paper(el, book);
        return;
      }
      if (
        book.appearance.cover === 'default' &&
        ['.doc', '.docx', '.ppt', '.pptx'].includes(book.ext) &&
        !book.previewAvailable
      ) {
        el.textContent = book.ext.slice(1).toUpperCase();
        return;
      }
      el.textContent = '…';
      const item = { el, book, started: false, done: false };
      targets.set(el, item);
      observer.observe(el);
    },
    clear,
    dispose: clear,
  };
}

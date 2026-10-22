const fs = require('node:fs/promises');
const path = require('node:path');
const { createHash, randomUUID } = require('node:crypto');
const { moveFile } = require('./file-ops.cjs');

const defaults = { version: 1, root: null, view: 'grid', sort: 'updated' };
const defaultAppearance = {
  version: 1,
  cover: 'default',
  page: 0,
  paper: 'sage',
};
const hash = (text) => createHash('sha256').update(text).digest('hex');
const inside = (root, file) => file.startsWith(root + path.sep);

async function safeFile(root, relative, missing = false) {
  if (
    typeof relative !== 'string' ||
    relative.includes('\\') ||
    path.isAbsolute(relative)
  )
    throw Error('项目路径无效');
  const file = path.resolve(root, relative);
  if (!inside(path.join(root, 'src'), file))
    throw Error('项目文件必须位于 src 内');
  const parts = path.relative(root, file).split(path.sep);
  let current = root;
  for (const part of parts) {
    current = path.join(current, part);
    try {
      if ((await fs.lstat(current)).isSymbolicLink())
        throw Error('项目文件不能使用符号链接');
    } catch (error) {
      if (missing && error.code === 'ENOENT') continue;
      throw error;
    }
  }
  return file;
}
async function readJson(file, limit) {
  const stat = await fs.lstat(file);
  if (!stat.isFile() || stat.isSymbolicLink() || stat.size > limit)
    throw Error('项目元数据过大或路径无效');
  return JSON.parse(await fs.readFile(file, 'utf8'));
}
function appearance(value) {
  if (!value || value.version !== 1) throw Error('封面设置版本无效');
  const result = { ...defaultAppearance, ...value };
  if (
    result.title !== undefined &&
    (typeof result.title !== 'string' || result.title.length > 200)
  )
    throw Error('书名最多 200 字符');
  if (
    !['default', 'paper', 'pdf', 'image'].includes(result.cover) ||
    !['sage', 'rose', 'sky', 'graphite'].includes(result.paper)
  )
    throw Error('封面类型或配色无效');
  if (!Number.isSafeInteger(result.page) || result.page < 0)
    throw Error('封面页码无效');
  if (
    result.asset !== undefined &&
    (typeof result.asset !== 'string' ||
      !result.asset ||
      result.asset.length > 200 ||
      /[\\/\x00-\x1f\x7f]/.test(result.asset) ||
      ['.', '..'].includes(result.asset))
  )
    throw Error('封面文件名无效');
  if (result.cover === 'image' && !result.asset) throw Error('缺少封面图片');
  return Object.fromEntries(
    ['version', 'title', 'cover', 'asset', 'page', 'paper']
      .filter((key) => result[key] !== undefined)
      .map((key) => [key, result[key]]),
  );
}
function createBookshelfStore({ data, limits = {} }) {
  const bounds = { depth: 8, directories: 2000, children: 10000, ...limits };
  const file = path.join(data, 'bookshelf.json');
  let generation = 0,
    index = new Map(),
    queue = Promise.resolve();
  async function config() {
    try {
      const value = await readJson(file, 65536);
      if (
        value.version !== 1 ||
        !['grid', 'list'].includes(value.view) ||
        !['updated', 'name'].includes(value.sort) ||
        (value.root !== null &&
          (typeof value.root !== 'string' || !path.isAbsolute(value.root)))
      )
        throw Error('书架目录设置损坏，请重新选择目录');
      return { ...defaults, ...value };
    } catch (error) {
      if (error.code === 'ENOENT') return { ...defaults };
      throw error;
    }
  }
  async function atomic(target, bytes) {
    const temp = target + '.' + randomUUID() + '.tmp';
    await fs.mkdir(path.dirname(target), { recursive: true });
    try {
      await fs.writeFile(temp, bytes);
      await moveFile(temp, target);
    } finally {
      await fs.rm(temp, { force: true }).catch(() => {});
    }
  }
  function serialize(fn) {
    const job = queue.then(fn, fn);
    queue = job.catch(() => {});
    return job;
  }
  async function checkRoot(root) {
    if (typeof root !== 'string' || !path.isAbsolute(root))
      throw Error('请选择有效的书架目录');
    try {
      const real = await fs.realpath(root);
      const dir = await fs.opendir(real);
      await dir.close();
      return real;
    } catch {
      throw Error('书架目录不可访问，请重新选择目录');
    }
  }
  async function bind(root) {
    return serialize(async () => {
      const real = await checkRoot(root);
      const previous = await config().catch(() => ({ ...defaults }));
      const next = { ...previous, root: real };
      await atomic(file, JSON.stringify(next, null, 2));
      generation++;
      index = new Map();
      return next;
    });
  }
  async function setView(values) {
    return serialize(async () => {
      if (
        (values.view !== undefined &&
          !['grid', 'list'].includes(values.view)) ||
        (values.sort !== undefined &&
          !['name', 'updated'].includes(values.sort))
      )
        throw Error('书架视图无效');
      const next = {
        ...(await config()),
        ...Object.fromEntries(
          ['view', 'sort']
            .filter((k) => values[k] !== undefined)
            .map((k) => [k, values[k]]),
        ),
      };
      await atomic(file, JSON.stringify(next, null, 2));
      return next;
    });
  }
  async function inspect(projectFile) {
    const root = path.dirname(projectFile);
    const manifest = await readJson(projectFile, 1024 * 1024);
    if (
      !['pagenote-project', 'page-notes-project'].includes(manifest.format) ||
      manifest.version !== 1 ||
      !/^[a-f0-9]{64}$/.test(manifest.id)
    )
      throw Error('项目清单无效');
    const source = await safeFile(root, manifest.left),
      right = await safeFile(root, manifest.right);
    const sourceStat = await fs.stat(source),
      rightStat = await fs.stat(right);
    if (!sourceStat.isFile() || !rightStat.isFile())
      throw Error('项目文件缺失');
    const ext = path.extname(source).toLowerCase();
    if (
      !['.md', '.markdown', '.pdf', '.doc', '.docx', '.ppt', '.pptx'].includes(
        ext,
      )
    )
      throw Error('项目文稿类型无效');
    let look = { ...defaultAppearance },
      warning = null;
    try {
      look = appearance(
        await readJson(await safeFile(root, 'src/bookshelf.json'), 65536),
      );
    } catch (error) {
      if (error.code !== 'ENOENT') warning = '封面设置损坏，已使用默认封面';
    }
    const manifestStat = await fs.stat(projectFile);
    let coverStat = null,
      previewAvailable = false;
    if (look.cover === 'image') {
      try {
        coverStat = await fs.stat(
          await safeFile(root, 'src/assets/' + look.asset),
        );
      } catch {
        warning = '封面图片不可用';
      }
    } else if (['.doc', '.docx', '.ppt', '.pptx'].includes(ext)) {
      try {
        coverStat = await fs.stat(
          await safeFile(root, manifest.preview || 'src/view.pdf'),
        );
        previewAvailable = coverStat.isFile();
      } catch {
        /* Office previews are optional; scanning never starts Office. */
      }
    }
    return {
      key: hash(projectFile),
      id: manifest.id,
      name: String(manifest.name || path.basename(root)),
      title: look.title?.trim() || String(manifest.name || path.basename(root)),
      projectFile,
      ext,
      updated: Math.max(
        sourceStat.mtimeMs,
        rightStat.mtimeMs,
        manifestStat.mtimeMs,
      ),
      revision: hash(
        JSON.stringify([
          sourceStat.mtimeMs,
          sourceStat.size,
          coverStat?.mtimeMs,
          coverStat?.size,
          look,
        ]),
      ),
      appearance: look,
      warning,
      previewAvailable,
    };
  }
  async function scan() {
    const epoch = generation,
      settings = await config();
    if (!settings.root) return { config: settings, books: [], warnings: [] };
    const root = await checkRoot(settings.root),
      found = new Map(),
      warnings = [];
    let count = 0;
    async function walk(dir, depth) {
      if (epoch !== generation) throw Error('书架目录已变更，请刷新');
      if (++count > bounds.directories) {
        if (!warnings.includes('扫描达到目录数量上限，结果不完整'))
          warnings.push('扫描达到目录数量上限，结果不完整');
        return;
      }
      const dirs = [];
      let manifest = null,
        children = 0;
      try {
        for await (const entry of await fs.opendir(dir)) {
          if (++children > bounds.children) {
            warnings.push(`${dir}: 扫描达到条目上限，结果不完整`);
            break;
          }
          if (
            entry.isFile() &&
            ['project.pagenote', 'project.page-notes.json'].includes(entry.name)
          ) {
            if (!manifest || entry.name === 'project.pagenote')
              manifest = path.join(dir, entry.name);
          } else if (entry.isDirectory()) dirs.push(path.join(dir, entry.name));
        }
        if (manifest) {
          try {
            const book = await inspect(manifest);
            found.set(book.key, book);
            if (book.warning) warnings.push(`${book.name}: ${book.warning}`);
          } catch (error) {
            warnings.push(`${dir}: ${error.message}`);
          }
          return;
        }
        if (depth >= bounds.depth) {
          if (dirs.length)
            warnings.push(`${dir}: 扫描达到深度上限，结果不完整`);
          return;
        }
        for (const child of dirs) {
          if (count >= bounds.directories) {
            warnings.push('扫描达到目录数量上限，结果不完整');
            break;
          }
          await walk(child, depth + 1);
        }
      } catch (error) {
        if (epoch !== generation) throw error;
        warnings.push(`${dir}: ${error.message}`);
      }
    }
    await walk(root, 0);
    if (epoch !== generation) throw Error('书架目录已变更，请刷新');
    index = found;
    return { config: settings, books: [...found.values()], warnings };
  }
  async function getBook(key) {
    const old = index.get(key);
    if (!old) throw Error('此项目不在当前书架，请刷新');
    const settings = await config();
    if (
      !settings.root ||
      !(
        old.projectFile === path.join(settings.root, 'project.pagenote') ||
        inside(settings.root, old.projectFile)
      )
    )
      throw Error('书架目录已变更');
    const real = await fs.realpath(old.projectFile);
    if (real !== old.projectFile) throw Error('项目路径已变更，请刷新');
    const book = await inspect(real);
    if (book.id !== old.id) throw Error('项目已替换，请刷新');
    return book;
  }
  async function writeAppearance(key, value) {
    const look = appearance(value),
      book = await getBook(key),
      root = path.dirname(book.projectFile);
    const target = await safeFile(root, 'src/bookshelf.json', true);
    await atomic(target, JSON.stringify(look, null, 2));
    return inspect(book.projectFile);
  }
  function saveAppearance(key, value) {
    return serialize(() => writeAppearance(key, value));
  }
  function saveCover(key, bytes, look) {
    return serialize(async () => {
      if (!Buffer.isBuffer(bytes) || bytes.length > 20 * 1024 * 1024)
        throw Error('封面图片最大 20 MiB');
      const book = await getBook(key),
        root = path.dirname(book.projectFile);
      const selected = appearance({
        ...book.appearance,
        ...look,
        cover: 'image',
        asset: 'pending.png',
      });
      const sharp = require('sharp');
      const image = await sharp(bytes, {
        limitInputPixels: 20000000,
        animated: false,
      })
        .rotate()
        .resize({
          width: 1200,
          height: 1600,
          fit: 'inside',
          withoutEnlargement: true,
        })
        .png()
        .toBuffer();
      const asset = `cover-${randomUUID()}.png`;
      const target = await safeFile(root, 'src/assets/' + asset, true);
      await fs.mkdir(path.dirname(target), { recursive: true });
      await safeFile(root, 'src/assets/' + asset, true);
      await atomic(target, image);
      return writeAppearance(key, { ...selected, asset });
    });
  }
  async function coverSource(key) {
    const book = await getBook(key),
      root = path.dirname(book.projectFile);
    let source, type;
    if (book.appearance.cover === 'image') {
      source = await safeFile(root, 'src/assets/' + book.appearance.asset);
      type = 'image/png';
    } else {
      const manifest = await readJson(book.projectFile, 1024 * 1024);
      if (book.ext === '.pdf') source = await safeFile(root, manifest.left);
      else if (['.doc', '.docx', '.ppt', '.pptx'].includes(book.ext))
        source = await safeFile(root, manifest.preview || 'src/view.pdf');
      else throw Error('此项目没有 PDF 封面');
      type = 'application/pdf';
    }
    const stat = await fs.stat(source);
    if (!stat.isFile() || stat.size > 512 * 1024 * 1024)
      throw Error('封面源文件过大或无效');
    return {
      file: source,
      type,
      revision: hash(JSON.stringify([book.revision, stat.mtimeMs, stat.size])),
    };
  }
  return {
    config,
    bind,
    setView,
    scan,
    getBook,
    saveAppearance,
    saveCover,
    coverSource,
  };
}
module.exports = { createBookshelfStore, appearance, safeFile };

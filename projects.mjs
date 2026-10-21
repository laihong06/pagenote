import fileOps from './desktop/file-ops.cjs';
const { moveFile, copyFileSafe } = fileOps;
import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash, randomUUID } from 'node:crypto';
import { PDFDocument } from 'pdf-lib';
export function splitPageNotes(text) {
  return text.split(
    /(?:\r?\n){2}<!--[ \t]*pagebreak[ \t]*-->(?:\r?\n){2}|^[ \t]*<!--[ \t]*pagebreak[ \t]*-->[ \t]*\r?$/gm,
  );
}
export function createProjectStore({ app, data, convertOffice }) {
  const metaPath = (id) => path.join(data, 'documents', id, 'meta.json'),
    valid = (id) => /^[a-f0-9]{64}$/.test(id);
  const readMeta = async (id) => {
    if (!valid(id)) throw Error('项目 ID 无效');
    return JSON.parse(await fs.readFile(metaPath(id), 'utf8'));
  };
  const resolveLink = (root, link) => {
    if (typeof link !== 'string' || path.isAbsolute(link))
      throw Error('项目链接必须使用相对路径');
    const result = path.resolve(root, link),
      src = path.join(root, 'src');
    if (result !== src && !result.startsWith(src + path.sep))
      throw Error('项目文件必须放在 src 文件夹中');
    return result;
  };
  const atomic = async (file, text) => {
    await fs.mkdir(path.dirname(file), { recursive: true });
    const temp = file + '.' + randomUUID() + '.tmp';
    await fs.writeFile(temp, text);
    await moveFile(temp, file);
  };
  const atomicBytes = async (file, bytes) => {
    await fs.mkdir(path.dirname(file), { recursive: true });
    const temp = file + '.' + randomUUID() + '.tmp';
    await fs.writeFile(temp, bytes);
    await moveFile(temp, file);
  };
  const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');
  const assetName = (pageId, digest, extension) => {
    const safeId =
      String(pageId)
        .replace(/[^a-zA-Z0-9_-]/g, '_')
        .slice(0, 64) || 'page';
    return `${safeId}-${digest.slice(0, 16)}.${extension}`;
  };
  async function pdfFromRainPages(pages) {
    const document = await PDFDocument.create();
    for (const page of pages) {
      const image =
        page.contentType === 'image/png'
          ? await document.embedPng(page.bytes)
          : await document.embedJpg(page.bytes);
      const scale = Math.min(960 / image.width, 720 / image.height);
      const width = Math.max(1, image.width * scale);
      const height = Math.max(1, image.height * scale);
      const target = document.addPage([width, height]);
      target.drawImage(image, { x: 0, y: 0, width, height });
    }
    return Buffer.from(await document.save());
  }
  function rainManifest(source, pages) {
    return {
      format: 'pagenote-yuketang-source',
      version: 1,
      provider: 'www.yuketang.cn',
      courseId: String(source.courseId),
      classroomId: String(source.classroomId),
      lessonId: String(source.lessonId || ''),
      presentationId: String(source.presentationId),
      pages,
    };
  }
  async function createRainProject({ name, parent, source, pages }) {
    const projectName = String(name || '').trim();
    if (!projectName || /[<>:"/\\|?*]/.test(projectName))
      throw Error('请输入有效的项目名称');
    if (!Array.isArray(pages) || !pages.length)
      throw Error('课件没有可导入的页面');
    const parentDirectory = parent
      ? path.resolve(parent)
      : path.join(data, 'projects');
    await fs.mkdir(parentDirectory, { recursive: true });
    const projectRoot = path.join(parentDirectory, projectName);
    await fs.mkdir(projectRoot);
    try {
      const src = path.join(projectRoot, 'src');
      await fs.mkdir(path.join(src, 'assets', 'yuketang'), { recursive: true });
      const pageEntries = [];
      const pdfPages = [];
      for (const [index, page] of pages.entries()) {
        const digest = sha256(page.bytes);
        const extension = page.contentType === 'image/png' ? 'png' : 'jpg';
        const filename = assetName(page.pageId, digest, extension);
        const asset = `src/assets/yuketang/${filename}`;
        await fs.writeFile(path.join(projectRoot, asset), page.bytes, {
          flag: 'wx',
        });
        pdfPages.push(page);
        pageEntries.push({
          pageId: String(page.pageId),
          presentationId: String(source.presentationId),
          sourceIndex: Number(page.sourceIndex),
          projectPage: index + 1,
          sha256: digest,
          asset,
          firstSeenAt: new Date().toISOString(),
        });
      }
      const pdfBytes = await pdfFromRainPages(pdfPages);
      const id = createHash('sha256').update(randomUUID()).digest('hex');
      const manifest = {
        format: 'pagenote-project',
        version: 1,
        id,
        name: projectName,
        created: new Date().toISOString(),
        left: 'src/document.pdf',
        right: 'src/right.md',
      };
      await atomicBytes(path.join(projectRoot, manifest.left), pdfBytes);
      await atomic(
        path.join(projectRoot, manifest.right),
        Array.from({ length: pages.length }, () => '').join(
          '\n\n<!-- pagebreak -->\n\n',
        ),
      );
      await atomic(
        path.join(src, 'yuketang-source.json'),
        JSON.stringify(rainManifest(source, pageEntries), null, 2),
      );
      const file = path.join(projectRoot, 'project.pagenote');
      await atomic(file, JSON.stringify(manifest, null, 2));
      return await open(file);
    } catch (error) {
      await fs
        .rm(projectRoot, { recursive: true, force: true })
        .catch(() => {});
      throw error;
    }
  }
  async function appendRainSlide({ id, source, page }) {
    const meta = await readMeta(id);
    if (!meta.projectRoot || meta.kind !== 'pdf' || meta.ext !== '.pdf')
      throw Error('实时雨课堂采集需要当前打开一个 PDF 项目');
    const manifestFile =
      meta.projectFile ||
      path.join(meta.projectRoot, 'project.page-notes.json');
    const projectManifest = JSON.parse(await fs.readFile(manifestFile, 'utf8'));
    const sourcePdf = resolveLink(meta.projectRoot, projectManifest.left);
    const rightFile = resolveLink(meta.projectRoot, projectManifest.right);
    const sourceManifestFile = path.join(
      meta.projectRoot,
      'src',
      'yuketang-source.json',
    );
    let sourceManifest;
    try {
      sourceManifest = JSON.parse(
        await fs.readFile(sourceManifestFile, 'utf8'),
      );
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
      sourceManifest = rainManifest(source, []);
    }
    const digest = sha256(page.bytes);
    const sourcePages = Array.isArray(sourceManifest.pages)
      ? sourceManifest.pages
      : [];
    const previous = sourcePages.find(
      (item) =>
        item.pageId === String(page.pageId) &&
        item.presentationId === String(source.presentationId) &&
        item.sha256 === digest,
    );
    if (previous) return { added: false, page: previous.projectPage };

    const sourceBytes = await fs.readFile(sourcePdf);
    const pdf = await PDFDocument.load(sourceBytes);
    const nextPageNumber = pdf.getPageCount() + 1;
    const embedded =
      page.contentType === 'image/png'
        ? await pdf.embedPng(page.bytes)
        : await pdf.embedJpg(page.bytes);
    const scale = Math.min(960 / embedded.width, 720 / embedded.height);
    const width = Math.max(1, embedded.width * scale);
    const height = Math.max(1, embedded.height * scale);
    const pdfPage = pdf.addPage([width, height]);
    pdfPage.drawImage(embedded, { x: 0, y: 0, width, height });
    const nextPdf = Buffer.from(await pdf.save());

    const originalNotes = await fs
      .readFile(rightFile, 'utf8')
      .catch((error) => {
        if (error.code === 'ENOENT') return '';
        throw error;
      });
    const notePages = splitPageNotes(originalNotes);
    if (notePages.length > nextPageNumber - 1)
      throw Error('项目笔记页数与 PDF 不一致，已停止追加以保护现有笔记');
    while (notePages.length < nextPageNumber - 1) notePages.push('');
    notePages.push('');
    const nextNotes = notePages.join('\n\n<!-- pagebreak -->\n\n');

    const extension = page.contentType === 'image/png' ? 'png' : 'jpg';
    const filename = assetName(page.pageId, digest, extension);
    const asset = `src/assets/yuketang/${filename}`;
    const assetPath = resolveLink(meta.projectRoot, asset);
    await fs.mkdir(path.dirname(assetPath), { recursive: true });
    try {
      await fs.access(assetPath);
    } catch {
      await fs.writeFile(assetPath, page.bytes, { flag: 'wx' });
    }
    const nextSource = {
      ...sourceManifest,
      format: 'pagenote-yuketang-source',
      version: 1,
      provider: 'www.yuketang.cn',
      courseId: String(source.courseId),
      classroomId: String(source.classroomId),
      lessonId: String(source.lessonId || ''),
      presentationId: String(source.presentationId),
      pages: [
        ...sourcePages,
        {
          pageId: String(page.pageId),
          presentationId: String(source.presentationId),
          sourceIndex: Number(page.sourceIndex),
          projectPage: nextPageNumber,
          sha256: digest,
          asset,
          firstSeenAt: new Date().toISOString(),
        },
      ],
    };
    const notesFile = path.join(path.dirname(metaPath(id)), 'notes.json');
    let previousNotesJson = '{}';
    try {
      previousNotesJson = await fs.readFile(notesFile, 'utf8');
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
    const notesMap = JSON.parse(previousNotesJson);
    notesMap[nextPageNumber] = '';
    const timestamp = new Date().toISOString();
    const sourceTemp = sourcePdf + '.' + randomUUID() + '.tmp';
    await fs.writeFile(sourceTemp, nextPdf);
    const oldSource = await fs.readFile(sourceManifestFile).catch(() => null);
    const oldPdf = sourceBytes;
    try {
      await moveFile(sourceTemp, sourcePdf);
      await atomic(rightFile, nextNotes);
      await atomic(notesFile, JSON.stringify(notesMap));
      await atomic(sourceManifestFile, JSON.stringify(nextSource, null, 2));
      await copyFileSafe(
        sourcePdf,
        path.join(path.dirname(metaPath(id)), 'original.pdf'),
      );
      await copyFileSafe(
        sourcePdf,
        path.join(path.dirname(metaPath(id)), 'view.pdf'),
      );
      meta.modified = timestamp;
      await atomic(metaPath(id), JSON.stringify(meta, null, 2));
      projectManifest.modified = timestamp;
      await atomic(manifestFile, JSON.stringify(projectManifest, null, 2));
    } catch (error) {
      await atomicBytes(sourcePdf, oldPdf).catch(() => {});
      await atomic(rightFile, originalNotes).catch(() => {});
      await atomic(notesFile, previousNotesJson).catch(() => {});
      if (oldSource)
        await atomic(sourceManifestFile, oldSource).catch(() => {});
      else await fs.unlink(sourceManifestFile).catch(() => {});
      throw error;
    } finally {
      await fs.unlink(sourceTemp).catch(() => {});
    }
    return {
      added: true,
      page: nextPageNumber,
      modified: timestamp,
      sourceAsset: asset,
    };
  }
  async function exportAssets(text, projectRoot) {
    for (const name of new Set(
      [...text.matchAll(/\/attachments\/([a-f0-9-]+\.[a-z0-9]+)/gi)].map(
        (m) => m[1],
      ),
    )) {
      const destination = path.join(projectRoot, 'src', 'assets', name);
      await fs.mkdir(path.dirname(destination), { recursive: true });
      try {
        await copyFileSafe(path.join(data, 'uploads', name), destination);
      } catch (e) {
        if (e.code !== 'ENOENT') throw e;
      }
    }
    return text.replace(
      /\/attachments\/([a-f0-9-]+\.[a-z0-9]+)/gi,
      'assets/$1',
    );
  }
  async function importAssets(text, projectRoot) {
    for (const name of new Set(
      [...text.matchAll(/assets\/([a-f0-9-]+\.[a-z0-9]+)/gi)].map((m) => m[1]),
    )) {
      await fs.mkdir(path.join(data, 'uploads'), { recursive: true });
      try {
        await copyFileSafe(
          resolveLink(projectRoot, 'src/assets/' + name),
          path.join(data, 'uploads', name),
        );
      } catch (e) {
        if (e.code !== 'ENOENT') throw e;
      }
    }
    return text.replace(/assets\/([a-f0-9-]+\.[a-z0-9]+)/gi, '/attachments/$1');
  }
  let writes = Promise.resolve();
  async function persist(id, source = false, writeRight = false) {
    const job = writes
      .catch(() => {})
      .then(async () => {
        const meta = await readMeta(id);
        if (!meta.projectRoot) return;
        const manifestFile =
            meta.projectFile ||
            path.join(meta.projectRoot, 'project.page-notes.json'),
          manifest = JSON.parse(await fs.readFile(manifestFile, 'utf8')),
          dir = path.dirname(metaPath(id));
        if (source) {
          if (meta.kind === 'markdown')
            await atomic(
              resolveLink(meta.projectRoot, manifest.left),
              await exportAssets(
                await fs.readFile(
                  path.join(dir, 'original' + meta.ext),
                  'utf8',
                ),
                meta.projectRoot,
              ),
            );
          else
            await copyFileSafe(
              path.join(dir, 'original' + meta.ext),
              resolveLink(meta.projectRoot, manifest.left),
            );
          if (meta.kind === 'pdf' && meta.ext !== '.pdf') {
            manifest.preview ||= 'src/view.pdf';
            await copyFileSafe(
              path.join(dir, 'view.pdf'),
              resolveLink(meta.projectRoot, manifest.preview),
            );
          }
        }
        let notes = {};
        try {
          notes = JSON.parse(
            await fs.readFile(path.join(dir, 'notes.json'), 'utf8'),
          );
        } catch (e) {
          if (e.code !== 'ENOENT') throw e;
        }
        const count = Math.max(1, ...Object.keys(notes).map(Number));
        if (writeRight)
          await atomic(
            resolveLink(meta.projectRoot, manifest.right),
            await exportAssets(
              Array.from({ length: count }, (_, i) => notes[i + 1] || '').join(
                '\n\n<!-- pagebreak -->\n\n',
              ),
              meta.projectRoot,
            ),
          );
        for (const name of [
          'notes.json',
          'annotations.json',
          'ai-reviews.json',
          'ai-handoffs.json',
        ]) {
          try {
            await atomic(
              path.join(meta.projectRoot, 'src', name),
              await exportAssets(
                await fs.readFile(path.join(dir, name), 'utf8'),
                meta.projectRoot,
              ),
            );
          } catch (e) {
            if (e.code !== 'ENOENT') throw e;
          }
        }
        manifest.modified = new Date().toISOString();
        await atomic(manifestFile, JSON.stringify(manifest, null, 2));
      });
    writes = job;
    await job;
  }
  async function open(file) {
    const root = path.dirname(path.resolve(file)),
      manifest = JSON.parse(await fs.readFile(file, 'utf8'));
    if (
      !['page-notes-project', 'pagenote-project'].includes(manifest.format) ||
      !valid(manifest.id)
    )
      throw Error('不是有效的 pagenote 项目');
    const left = resolveLink(root, manifest.left),
      right = resolveLink(root, manifest.right),
      ext = path.extname(left).toLowerCase();
    if (
      !['.md', '.markdown', '.pdf', '.doc', '.docx', '.ppt', '.pptx'].includes(
        ext,
      )
    )
      throw Error('项目左侧文件格式不支持');
    const dir = path.dirname(metaPath(manifest.id));
    await fs.mkdir(dir, { recursive: true });
    if (['.md', '.markdown'].includes(ext))
      await atomic(
        path.join(dir, 'original' + ext),
        await importAssets(await fs.readFile(left, 'utf8'), root),
      );
    else await copyFileSafe(left, path.join(dir, 'original' + ext));
    const kind = ['.md', '.markdown'].includes(ext) ? 'markdown' : 'pdf';
    if (kind === 'pdf') {
      if (ext === '.pdf') await copyFileSafe(left, path.join(dir, 'view.pdf'));
      else {
        const preview = resolveLink(root, manifest.preview || 'src/view.pdf');
        let rebuild = false;
        try {
          rebuild =
            (await fs.stat(left)).mtimeMs > (await fs.stat(preview)).mtimeMs;
        } catch (e) {
          if (e.code === 'ENOENT') rebuild = true;
          else throw e;
        }
        if (rebuild) {
          if (!convertOffice) throw Error('Office 转换不可用');
          await convertOffice(left, preview);
        }
        await copyFileSafe(preview, path.join(dir, 'view.pdf'));
      }
    }
    const notes = Object.fromEntries(
      splitPageNotes(
        await importAssets(await fs.readFile(right, 'utf8'), root),
      ).map((text, i) => [i + 1, text]),
    );
    await atomic(path.join(dir, 'notes.json'), JSON.stringify(notes));
    for (const name of [
      'annotations.json',
      'ai-reviews.json',
      'ai-handoffs.json',
    ]) {
      try {
        await atomic(
          path.join(dir, name),
          await importAssets(
            await fs.readFile(path.join(root, 'src', name), 'utf8'),
            root,
          ),
        );
      } catch (e) {
        if (e.code !== 'ENOENT') throw e;
        if (name === 'ai-handoffs.json')
          await atomic(path.join(dir, name), '{}');
      }
    }
    const meta = {
      id: manifest.id,
      name: manifest.name,
      ext,
      kind,
      projectRoot: root,
      projectFile: path.resolve(file),
      created: manifest.created,
      url:
        '/files/' +
        manifest.id +
        '/' +
        (kind === 'pdf' ? 'view.pdf' : 'original' + ext),
      modified: new Date().toISOString(),
    };
    await atomic(metaPath(meta.id), JSON.stringify(meta, null, 2));
    return meta;
  }
  async function bindLeft(meta, id) {
    if (!id) return meta;
    const project = await readMeta(id);
    if (!project.projectRoot) throw Error('当前文档不是项目');
    const file = project.projectFile,
      manifest = JSON.parse(await fs.readFile(file, 'utf8')),
      from = path.dirname(metaPath(meta.id)),
      to = path.dirname(metaPath(id));
    manifest.left = 'src/left' + meta.ext;
    manifest.leftName = meta.name;
    await copyFileSafe(
      path.join(from, 'original' + meta.ext),
      resolveLink(project.projectRoot, manifest.left),
    );
    await copyFileSafe(
      path.join(from, 'original' + meta.ext),
      path.join(to, 'original' + meta.ext),
    );
    if (meta.kind === 'pdf') {
      await copyFileSafe(
        path.join(from, 'view.pdf'),
        path.join(to, 'view.pdf'),
      );
      manifest.preview = 'src/view.pdf';
      await copyFileSafe(
        path.join(from, 'view.pdf'),
        resolveLink(project.projectRoot, manifest.preview),
      );
    }
    const updated = {
      ...project,
      ext: meta.ext,
      kind: meta.kind,
      url:
        '/files/' +
        id +
        '/' +
        (meta.kind === 'pdf' ? 'view.pdf' : 'original' + meta.ext),
      modified: new Date().toISOString(),
    };
    await atomic(file, JSON.stringify(manifest, null, 2));
    await atomic(metaPath(id), JSON.stringify(updated, null, 2));
    return updated;
  }
  app.post('/api/projects', async (req, res, next) => {
    try {
      const name = String(req.body.name || '').trim();
      if (!name || /[<>:"/\\|?*]/.test(name))
        throw Error('请输入有效的项目名称');
      const parent = req.body.parent
        ? path.resolve(req.body.parent)
        : path.join(data, 'projects');
      await fs.mkdir(parent, { recursive: true });
      const root = path.join(parent, name);
      await fs.mkdir(root);
      await fs.mkdir(path.join(root, 'src'));
      const id = createHash('sha256').update(randomUUID()).digest('hex'),
        manifest = {
          format: 'pagenote-project',
          version: 1,
          id,
          name,
          created: new Date().toISOString(),
          left: 'src/left.md',
          right: 'src/right.md',
        };
      await atomic(path.join(root, 'src', 'left.md'), '# ' + name + '\n');
      await atomic(path.join(root, 'src', 'right.md'), '');
      const file = path.join(root, 'project.pagenote');
      await atomic(file, JSON.stringify(manifest, null, 2));
      res.json(await open(file));
    } catch (e) {
      next(e);
    }
  });
  app.post('/api/projects/open', async (req, res, next) => {
    try {
      await writes;
      res.json(await open(String(req.body.filePath)));
    } catch (e) {
      next(e);
    }
  });
  app.post('/api/projects/:id/sync', async (req, res, next) => {
    try {
      await persist(
        req.params.id,
        req.body?.source !== false,
        req.body?.right !== false,
      );
      res.json({ ok: true });
    } catch (e) {
      next(e);
    }
  });
  return {
    persist,
    bindLeft,
    open,
    createRainProject,
    appendRainSlide,
    sourceFile: async (meta) => {
      if (!meta.projectRoot)
        return path.join(
          path.dirname(metaPath(meta.id)),
          'original' + meta.ext,
        );
      const manifest = JSON.parse(
        await fs.readFile(
          meta.projectFile ||
            path.join(meta.projectRoot, 'project.page-notes.json'),
          'utf8',
        ),
      );
      return resolveLink(meta.projectRoot, manifest.left);
    },
  };
}

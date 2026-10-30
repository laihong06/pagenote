import fileOps from './desktop/file-ops.cjs';
import aiSettingsStore from './desktop/ai-settings.cjs';
import promptStoreModule from './desktop/prompts.cjs';
const { moveFile, copyFileSafe } = fileOps;
import { registerExternalDocuments } from './external-documents.mjs';
import { createProjectStore } from './projects.mjs';
import macOfficeModule from './desktop/mac-office.cjs';
import express from 'express';
import multer from 'multer';
import path from 'node:path';
import fs from 'node:fs/promises';
import { randomUUID, createHash } from 'node:crypto';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const data = process.env.PAGE_NOTES_DATA || path.join(root, 'data');
await fs.mkdir(path.join(data, 'uploads'), { recursive: true });
await fs.mkdir(path.join(data, 'documents'), { recursive: true });
const macOffice =
  process.platform === 'darwin'
    ? macOfficeModule.createMacOffice({ data })
    : null;
const app = express();
// Accept requests only from this local application's origin.
app.use((req, res, next) => {
  if (!/^(127\.0\.0\.1|localhost)(:\d+)?$/i.test(req.headers.host || ''))
    return res.status(403).json({ error: '仅允许本机访问' });
  const origin = req.headers.origin;
  if (origin && origin !== `http://${req.headers.host}`)
    return res.status(403).json({ error: '不允许跨站请求' });
  next();
});
app.use(express.json({ limit: '30mb' }));
const aiSettingsFile = path.join(data, 'ai-settings.json');
const prompts = promptStoreModule.createPromptStore(
  path.join(root, 'skills'),
  data,
);
app.get('/api/ai-prompts', (req, res, next) => {
  try {
    res.json(prompts.index());
  } catch (e) {
    next(e);
  }
});
app.get('/api/ai-prompts/:id', (req, res, next) => {
  try {
    res.json(prompts.read(req.params.id));
  } catch (e) {
    next(e);
  }
});
app.put('/api/ai-prompts/:id', async (req, res, next) => {
  try {
    res.json(await prompts.save(req.params.id, req.body?.text));
  } catch (e) {
    next(e);
  }
});
app.get('/api/ai-settings', (req, res, next) => {
  try {
    res.json(aiSettingsStore.readAISettings(aiSettingsFile));
  } catch (error) {
    next(error);
  }
});
app.put('/api/ai-settings', async (req, res, next) => {
  try {
    res.json(
      await aiSettingsStore.saveAISettings(
        aiSettingsFile,
        req.body?.systemPrompt,
      ),
    );
  } catch (error) {
    next(error);
  }
});
const projects = createProjectStore({
  app,
  data,
  convertOffice: convertOffice,
});
async function convertOffice(input, output) {
  const snapshot = path.join(
    data,
    'uploads',
    `convert-${randomUUID()}${path.extname(input)}`,
  );
  await copyFileSafe(input, snapshot);
  const converter =
      process.env.PAGE_NOTES_CONVERTER || path.join(root, 'convert-office.ps1'),
    convert = () => {
      if (macOffice) return macOffice.convert(snapshot, output);
      if (process.platform !== 'win32')
        throw Error(
          'Word 和 PowerPoint 转换需要 Windows 或 macOS 上的 Microsoft Office。请先导出 PDF 再导入。',
        );
      return run(
        'powershell.exe',
        [
          '-NoProfile',
          '-NonInteractive',
          '-ExecutionPolicy',
          'Bypass',
          '-File',
          converter,
          '-InputFile',
          snapshot,
          '-OutputFile',
          output,
        ],
        { timeout: 120000, windowsHide: true, maxBuffer: 1024 * 1024 },
      );
    };
  const job = conversionQueue.then(convert, convert);
  conversionQueue = job.catch(() => {});
  try {
    await job;
  } finally {
    await fs.unlink(snapshot).catch(() => {});
  }
}
registerExternalDocuments({
  app,
  data,
  projects,
  desktop: () => {
    if (!desktopHandler) throw Error('外部编辑需要桌面版');
    return desktopHandler;
  },
  convertOffice,
});
let preferenceHandler;
export function setPreferenceHandler(handler) {
  preferenceHandler = handler;
}
const preferenceFile = path.join(data, 'preferences.json');
app.get('/api/preferences', async (req, res, next) => {
  try {
    let preferences = {};
    try {
      preferences = JSON.parse(await fs.readFile(preferenceFile, 'utf8'));
    } catch (e) {
      if (e.code !== 'ENOENT') throw e;
    }
    res.json(preferences);
  } catch (e) {
    next(e);
  }
});
app.put('/api/preferences', async (req, res, next) => {
  try {
    if (!['zh-CN', 'en-US'].includes(req.body.language))
      throw Error('Unsupported language');
    const preferences = { language: req.body.language };
    await fs.writeFile(preferenceFile + '.tmp', JSON.stringify(preferences));
    await moveFile(preferenceFile + '.tmp', preferenceFile);
    await preferenceHandler?.(preferences);
    res.json(preferences);
  } catch (e) {
    next(e);
  }
});
app.use(
  '/pdf-assets',
  express.static(path.join(root, 'node_modules', 'pdfjs-dist')),
);
const upload = multer({
  dest: path.join(data, 'uploads'),
  limits: { fileSize: 200 * 1024 * 1024 },
});
const validId = (id) => /^[a-f0-9]{64}$/.test(id);
const metaFile = (id) => path.join(data, 'documents', id, 'meta.json');
const run = promisify(execFile);
let conversionQueue = Promise.resolve();
let aiHandler;
let desktopHandler;
export function setDesktopHandler(handler) {
  desktopHandler = handler;
}
app.post('/api/desktop/word', async (req, res, next) => {
  try {
    if (!desktopHandler) throw Error('Word 编辑需要桌面版');
    if (
      ![
        'open',
        'resize',
        'read',
        'selection',
        'close',
        'visibility',
        'status',
      ].includes(req.body.action)
    )
      throw Error('不支持的操作');
    res.json(await desktopHandler(req.body.action, req.body.args || {}));
  } catch (e) {
    next(e);
  }
});
export function setAIHandler(handler) {
  aiHandler = handler;
}
app.post('/api/ai/call', async (req, res, next) => {
  if (
    !process.env.PAGE_NOTES_AI_TOKEN ||
    req.headers.authorization !== `Bearer ${process.env.PAGE_NOTES_AI_TOKEN}`
  )
    return res.status(401).json({ error: 'AI 接口需要本机连接凭据' });
  try {
    if (!aiHandler) throw Error('桌面软件尚未准备好');
    res.json(await aiHandler(req.body));
  } catch (e) {
    next(e);
  }
});
const layoutDefaults = {
  noteWidth: 1000,
  padding: 28,
  fontSize: 15,
  lineHeight: 1.9,
  leftPercent: 42,
  customCSS: '',
  noteAnimation: true,
  expandEditor: true,
};
app.get('/api/layout', async (req, res, next) => {
  try {
    let saved = {};
    try {
      saved = JSON.parse(
        await fs.readFile(path.join(data, 'layout.json'), 'utf8'),
      );
    } catch (e) {
      if (e.code !== 'ENOENT') throw e;
    }
    res.json({ ...layoutDefaults, ...saved });
  } catch (e) {
    next(e);
  }
});
app.put('/api/layout', async (req, res, next) => {
  try {
    const values = { ...layoutDefaults, ...req.body };
    for (const [key, min, max] of [
      ['noteWidth', 320, 1400],
      ['padding', 12, 100],
      ['fontSize', 10, 32],
      ['lineHeight', 1.2, 3],
      ['leftPercent', 25, 75],
    ])
      if (
        typeof values[key] !== 'number' ||
        !Number.isFinite(values[key]) ||
        values[key] < min ||
        values[key] > max
      )
        throw Error(`布局参数 ${key} 超出范围`);
    if (
      typeof values.customCSS !== 'string' ||
      values.customCSS.length > 100000
    )
      throw Error('样式无效');
    if (typeof values.noteAnimation !== 'boolean')
      throw Error('换页动画设置必须为布尔值');
    if (typeof values.expandEditor !== 'boolean')
      throw Error('编辑展开设置必须为布尔值');
    const safe = Object.fromEntries(
      Object.keys(layoutDefaults).map((key) => [key, values[key]]),
    );
    const file = path.join(data, 'layout.json');
    await fs.writeFile(file + '.tmp', JSON.stringify(safe, null, 2));
    await moveFile(file + '.tmp', file);
    res.json(safe);
  } catch (e) {
    next(e);
  }
});

app.get('/api/documents', async (req, res, next) => {
  try {
    const dirs = await fs.readdir(path.join(data, 'documents'));
    const results = await Promise.all(
      dirs.filter(validId).map(async (id) => {
        try {
          return JSON.parse(await fs.readFile(metaFile(id), 'utf8'));
        } catch {
          return null;
        }
      }),
    );
    res.json(
      results
        .filter(Boolean)
        .sort((a, b) => b.created.localeCompare(a.created)),
    );
  } catch (e) {
    next(e);
  }
});
app.post('/api/documents', upload.single('file'), async (req, res, next) => {
  try {
    if (!req.file) throw Error('请选择文件');
    const name = Buffer.from(req.file.originalname, 'latin1').toString('utf8');
    const ext = path.extname(name).toLowerCase();
    if (
      !['.pdf', '.doc', '.docx', '.ppt', '.pptx', '.md', '.markdown'].includes(
        ext,
      )
    )
      throw Error('支持 PDF、Word、PowerPoint 和 Markdown 文件');
    const bytes = await fs.readFile(req.file.path);
    const id = createHash('sha256').update(ext).update(bytes).digest('hex');
    const dir = path.join(data, 'documents', id);
    try {
      const existing = JSON.parse(await fs.readFile(metaFile(id), 'utf8'));
      await fs.unlink(req.file.path);
      return res.json(await projects.bindLeft(existing, req.body.projectId));
    } catch {}
    await fs.mkdir(dir, { recursive: true });
    const original = path.join(dir, `original${ext}`);
    await moveFile(req.file.path, original);
    const kind = ['.md', '.markdown'].includes(ext) ? 'markdown' : 'pdf';
    if (kind === 'pdf' && ext !== '.pdf') {
      try {
        await convertOffice(original, path.join(dir, 'view.pdf'));
      } catch (e) {
        throw Error(
          'Office 转换失败。请确认已安装并激活 Microsoft Office，并允许必要的自动化和文件访问。' +
            (e.stderr || e.message).slice(0, 350),
        );
      }
    } else if (kind === 'pdf')
      await copyFileSafe(original, path.join(dir, 'view.pdf'));
    const meta = {
      id,
      name,
      ext,
      kind,
      created: new Date().toISOString(),
      url: `/files/${id}/${kind === 'pdf' ? 'view.pdf' : `original${ext}`}`,
    };
    await fs.writeFile(metaFile(id), JSON.stringify(meta, null, 2));
    res.json(await projects.bindLeft(meta, req.body.projectId));
  } catch (e) {
    if (req.file) await fs.unlink(req.file.path).catch(() => {});
    next(e);
  }
});
app.put('/api/documents/:id/markdown', async (req, res, next) => {
  try {
    if (
      !validId(req.params.id) ||
      typeof req.body.markdown !== 'string' ||
      req.body.markdown.length > 10000000
    )
      throw Error('文稿内容无效');
    const meta = JSON.parse(await fs.readFile(metaFile(req.params.id), 'utf8'));
    if (meta.kind !== 'markdown') throw Error('此接口用于 Markdown 文稿');
    const file = path.join(
      path.dirname(metaFile(meta.id)),
      'original' + meta.ext,
    );
    await fs.mkdir(path.join(path.dirname(file), 'source-history'), {
      recursive: true,
    });
    await copyFileSafe(
      file,
      path.join(
        path.dirname(file),
        'source-history',
        Date.now() + '-' + randomUUID() + meta.ext,
      ),
    );
    await fs.writeFile(file + '.tmp', req.body.markdown);
    await moveFile(file + '.tmp', file);
    meta.modified = new Date().toISOString();
    await fs.writeFile(metaFile(meta.id), JSON.stringify(meta, null, 2));
    await projects.persist(meta.id, true);
    res.json(meta);
  } catch (e) {
    next(e);
  }
});
app.get('/api/documents/:id/notes', async (req, res, next) => {
  try {
    if (!validId(req.params.id)) return res.sendStatus(400);
    let notes = {};
    try {
      notes = JSON.parse(
        await fs.readFile(
          path.join(path.dirname(metaFile(req.params.id)), 'notes.json'),
          'utf8',
        ),
      );
    } catch (e) {
      if (e.code !== 'ENOENT') throw e;
    }
    res.json(notes);
  } catch (e) {
    next(e);
  }
});
let writeQueue = Promise.resolve();
async function readHandoffs(id) {
  const file = path.join(path.dirname(metaFile(id)), 'ai-handoffs.json');
  try {
    return JSON.parse(await fs.readFile(file, 'utf8'));
  } catch (e) {
    if (e.code !== 'ENOENT') throw e;
    return {};
  }
}
app.get('/api/documents/:id/handoffs/:page', async (req, res, next) => {
  try {
    const { id, page } = req.params;
    if (!validId(id) || !/^[1-9]\d{0,3}$|^10000$/.test(page))
      return res.sendStatus(400);
    await fs.access(metaFile(id));
    const handoff = (await readHandoffs(id))[page] || null;
    const current = noteRevision((await readNotes(id))[page] || '');
    res.json({
      documentId: id,
      page: Number(page),
      handoff,
      stale: handoff ? handoff.noteRevision !== current : false,
    });
  } catch (e) {
    next(e);
  }
});
app.put('/api/documents/:id/handoffs/:page', async (req, res, next) => {
  try {
    const { id, page } = req.params;
    const { summary, expectedRevision, sourceRevision } = req.body || {};
    if (
      !validId(id) ||
      !/^[1-9]\d{0,3}$|^10000$/.test(page) ||
      typeof summary !== 'string' ||
      !summary.trim() ||
      summary.length > 4000 ||
      typeof expectedRevision !== 'string' ||
      !/^[a-f0-9]{64}$/.test(expectedRevision) ||
      typeof sourceRevision !== 'string' ||
      !/^[a-f0-9]{64}$/.test(sourceRevision)
    )
      return res.status(400).json({ error: '交接内容、页码或版本无效' });
    await fs.access(metaFile(id));
    const job = writeQueue.then(async () => {
      const current = noteRevision((await readNotes(id))[page] || '');
      if (current !== expectedRevision)
        throw Error('笔记已变化，请重新读取后保存交接');
      const records = await readHandoffs(id);
      records[page] = {
        summary,
        noteRevision: current,
        sourceRevision,
        updated: new Date().toISOString(),
      };
      const file = path.join(path.dirname(metaFile(id)), 'ai-handoffs.json');
      const temp = file + '.' + randomUUID() + '.tmp';
      await fs.writeFile(temp, JSON.stringify(records));
      await moveFile(temp, file);
      await projects.persist(id);
    });
    writeQueue = job.catch(() => {});
    await job;
    res.json({ documentId: id, page: Number(page), saved: true });
  } catch (e) {
    next(e);
  }
});
async function readReviews(id) {
  if (!validId(id)) throw Error('文档 ID 无效');
  await fs.access(metaFile(id));
  try {
    return JSON.parse(
      await fs.readFile(
        path.join(path.dirname(metaFile(id)), 'ai-reviews.json'),
        'utf8',
      ),
    );
  } catch (e) {
    if (e.code === 'ENOENT') return [];
    throw e;
  }
}
app.get('/api/documents/:id/ai-reviews', async (req, res, next) => {
  try {
    res.json(await readReviews(req.params.id));
  } catch (e) {
    next(e);
  }
});
app.post('/api/documents/:id/ai-reviews', async (req, res, next) => {
  const job = writeQueue.then(async () => {
    const records = await readReviews(req.params.id),
      body = req.body;
    if (
      !['document', 'note'].includes(body.side) ||
      !['overall', 'selection', 'region'].includes(body.kind) ||
      !Number.isInteger(body.page) ||
      body.page < 1 ||
      typeof body.instruction !== 'string' ||
      !body.instruction.trim() ||
      body.instruction.length > 20000
    )
      throw Error('批注内容无效');
    const record = {
      id: randomUUID(),
      number: Math.max(0, ...records.map((r) => r.number)) + 1,
      side: body.side,
      kind: body.kind,
      page: body.page,
      scope: body.scope === 'document' ? 'document' : 'page',
      instruction: body.instruction.trim(),
      quote: String(body.quote || '').slice(0, 100000),
      context: String(body.context || '').slice(0, 1000000),
      revision: typeof body.revision === 'string' ? body.revision : null,
      anchor: body.anchor || null,
      status: 'open',
      created: new Date().toISOString(),
    };
    if (JSON.stringify(record.anchor).length > 500000) throw Error('选区过大');
    records.push(record);
    await writeReviews(req.params.id, records);
    return record;
  });
  writeQueue = job.catch(() => {});
  try {
    res.json(await job);
  } catch (e) {
    next(e);
  }
});
async function writeReviews(id, records) {
  const dir = path.dirname(metaFile(id)),
    file = path.join(dir, 'ai-reviews.json');
  await fs.mkdir(path.join(dir, 'review-history'), { recursive: true });
  try {
    await copyFileSafe(
      file,
      path.join(dir, 'review-history', `${Date.now()}-${randomUUID()}.json`),
    );
  } catch (e) {
    if (e.code !== 'ENOENT') throw e;
  }
  await fs.writeFile(file + '.tmp', JSON.stringify(records, null, 2));
  await moveFile(file + '.tmp', file);
  await projects.persist(id);
}
app.delete('/api/documents/:id/ai-reviews/:record', async (req, res, next) => {
  try {
    const job = writeQueue.then(async () => {
      const records = await readReviews(req.params.id);
      await writeReviews(
        req.params.id,
        records.filter((r) => r.id !== req.params.record),
      );
    });
    writeQueue = job.catch(() => {});
    await job;
    res.json({ ok: true });
  } catch (e) {
    next(e);
  }
});
app.patch('/api/documents/:id/ai-reviews/:record', async (req, res, next) => {
  const job = writeQueue.then(async () => {
    const records = await readReviews(req.params.id),
      record = records.find((r) => r.id === req.params.record);
    if (!record) throw Error('批注不存在');
    if (req.body.instruction !== undefined) {
      if (
        typeof req.body.instruction !== 'string' ||
        !req.body.instruction.trim() ||
        req.body.instruction.length > 20000
      )
        throw Error('批注要求无效');
      record.instruction = req.body.instruction.trim();
    }
    if (req.body.status !== undefined) {
      if (!['open', 'applied', 'dismissed'].includes(req.body.status))
        throw Error('批注状态无效');
      record.status = req.body.status;
    }
    if (req.body.boardPosition !== undefined) {
      const p = req.body.boardPosition;
      if (
        !p ||
        !Number.isFinite(p.x) ||
        !Number.isFinite(p.y) ||
        p.x < 0 ||
        p.y < 0 ||
        p.x > 100000 ||
        p.y > 100000
      )
        throw Error('画布位置无效');
      record.boardPosition = { x: p.x, y: p.y };
    }
    record.updated = new Date().toISOString();
    await writeReviews(req.params.id, records);
    return record;
  });
  writeQueue = job.catch(() => {});
  try {
    res.json(await job);
  } catch (e) {
    next(e);
  }
});
const historyName = /^(\d{13})-[a-f0-9-]{36}-page(\d+)\.md$/;
const noteRevision = (text) => createHash('sha256').update(text).digest('hex');
async function readNotes(id) {
  try {
    return JSON.parse(
      await fs.readFile(
        path.join(path.dirname(metaFile(id)), 'notes.json'),
        'utf8',
      ),
    );
  } catch (e) {
    if (e.code === 'ENOENT') return {};
    throw e;
  }
}
async function historyEntries(id, page) {
  const dir = path.join(path.dirname(metaFile(id)), 'history');
  let names = [];
  try {
    names = await fs.readdir(dir);
  } catch (e) {
    if (e.code !== 'ENOENT') throw e;
  }
  return names
    .filter(
      (name) =>
        historyName.test(name) &&
        Number(name.match(historyName)[2]) === Number(page),
    )
    .sort()
    .reverse();
}
async function saveNote(id, page, markdown, expectedRevision) {
  const notes = await readNotes(id),
    old = notes[page] || '';
  if (
    expectedRevision &&
    noteRevision(old) !== expectedRevision &&
    old !== markdown
  ) {
    const history = path.join(path.dirname(metaFile(id)), 'history');
    await fs.mkdir(history, { recursive: true });
    const latest = (await historyEntries(id, page))[0];
    let draftVersion = latest;
    if (
      !latest ||
      (await fs.readFile(path.join(history, latest), 'utf8')) !== markdown
    ) {
      const stamp = Math.max(
        Date.now(),
        latest ? Number(latest.match(historyName)[1]) + 1 : 0,
      );
      draftVersion = `${stamp}-${randomUUID()}-page${page}.md`;
      await fs.writeFile(path.join(history, draftVersion) + '.tmp', markdown);
      await moveFile(
        path.join(history, draftVersion) + '.tmp',
        path.join(history, draftVersion),
      );
    }
    throw Object.assign(Error('笔记已更新，本地草稿已备份到历史版本'), {
      status: 409,
      code: 'NOTE_CONFLICT',
      currentMarkdown: old,
      revision: noteRevision(old),
      draftVersion,
    });
  }
  if (notes[page] === markdown) return;
  const dir = path.dirname(metaFile(id)),
    history = path.join(dir, 'history');
  await fs.mkdir(history, { recursive: true });
  const latest = (await historyEntries(id, page))[0];
  let previous = latest
      ? await fs.readFile(path.join(history, latest), 'utf8')
      : null,
    stamp = Math.max(
      Date.now(),
      latest ? Number(latest.match(historyName)[1]) + 1 : 0,
    );
  for (const content of [old, markdown])
    if (content !== previous) {
      const snapshot = path.join(
        history,
        `${stamp++}-${randomUUID()}-page${page}.md`,
      );
      await fs.writeFile(snapshot + '.tmp', content);
      await moveFile(snapshot + '.tmp', snapshot);
      previous = content;
    }
  notes[page] = markdown;
  const file = path.join(dir, 'notes.json');
  await fs.writeFile(file + '.tmp', JSON.stringify(notes, null, 2));
  await moveFile(file + '.tmp', file);
  await projects.persist(id, false, true);
}
app.put('/api/documents/:id/notes/:page', async (req, res, next) => {
  try {
    const { id, page } = req.params;
    if (
      !validId(id) ||
      !/^\d+$/.test(page) ||
      Number(page) < 1 ||
      typeof req.body.markdown !== 'string' ||
      (req.body.expectedRevision !== undefined &&
        (typeof req.body.expectedRevision !== 'string' ||
          !/^[a-f0-9]{64}$/.test(req.body.expectedRevision)))
    )
      return res.sendStatus(400);
    await fs.access(metaFile(id));
    const job = writeQueue.then(() =>
      saveNote(id, page, req.body.markdown, req.body.expectedRevision),
    );
    writeQueue = job.catch(() => {});
    await job;
    res.json({ ok: true });
  } catch (e) {
    next(e);
  }
});
app.get('/api/documents/:id/notes/:page/history', async (req, res, next) => {
  try {
    const { id, page } = req.params;
    if (!validId(id) || !/^\d+$/.test(page) || Number(page) < 1)
      return res.sendStatus(400);
    await fs.access(metaFile(id));
    await writeQueue;
    const entries = await historyEntries(id, page),
      dir = path.join(path.dirname(metaFile(id)), 'history');
    const versions = await Promise.all(
      entries.map(async (name) => {
        const stat = await fs.stat(path.join(dir, name));
        return {
          id: name,
          created: new Date(Number(name.match(historyName)[1])).toISOString(),
          bytes: stat.size,
        };
      }),
    );
    res.json({
      versions,
      currentRevision: noteRevision((await readNotes(id))[page] || ''),
    });
  } catch (e) {
    next(e);
  }
});
app.get(
  '/api/documents/:id/notes/:page/history/:version',
  async (req, res, next) => {
    try {
      const { id, page, version } = req.params;
      if (
        !validId(id) ||
        !historyName.test(version) ||
        Number(version.match(historyName)[2]) !== Number(page)
      )
        return res.sendStatus(400);
      res.json({
        markdown: await fs.readFile(
          path.join(path.dirname(metaFile(id)), 'history', version),
          'utf8',
        ),
      });
    } catch (e) {
      next(e);
    }
  },
);
app.post('/api/documents/:id/notes/:page/restore', async (req, res, next) => {
  try {
    const { id, page } = req.params,
      { version, expectedRevision } = req.body;
    if (
      !validId(id) ||
      typeof version !== 'string' ||
      !historyName.test(version) ||
      Number(version.match(historyName)[2]) !== Number(page) ||
      typeof expectedRevision !== 'string' ||
      !/^[a-f0-9]{64}$/.test(expectedRevision)
    )
      return res.sendStatus(400);
    await fs.access(metaFile(id));
    const markdown = await fs.readFile(
      path.join(path.dirname(metaFile(id)), 'history', version),
      'utf8',
    );
    const job = writeQueue.then(() =>
      saveNote(id, page, markdown, expectedRevision),
    );
    writeQueue = job.catch(() => {});
    await job;
    res.json({ markdown });
  } catch (e) {
    next(e);
  }
});
function validAnnotationItems(items) {
  if (!Array.isArray(items) || items.length > 2000) return false;
  return items.every(
    (item) =>
      item &&
      [
        'pen',
        'highlight',
        'line',
        'arrow',
        'rect',
        'ellipse',
        'text',
        'image',
        'html',
      ].includes(item.type) &&
      /^#[a-f0-9]{6}$/i.test(item.color) &&
      Number.isFinite(item.width) &&
      item.width >= 0.1 &&
      item.width <= 100 &&
      Array.isArray(item.points) &&
      item.points.length >= 1 &&
      item.points.length <= 30000 &&
      item.points.every(
        (p) =>
          Array.isArray(p) &&
          p.length === 2 &&
          p.every((n) => Number.isFinite(n) && Math.abs(n) <= 1000000),
      ) &&
      (item.type !== 'text' ||
        (typeof item.text === 'string' && item.text.length <= 10000)) &&
      (item.type !== 'html' ||
        (typeof item.html === 'string' && item.html.length <= 100000)) &&
      (item.type !== 'image' ||
        (typeof item.url === 'string' &&
          /^\/attachments\/[a-f0-9-]{36}\.(png|jpe?g|gif|webp)$/i.test(
            item.url,
          ))),
  );
}
app.get('/api/documents/:id/annotations', async (req, res, next) => {
  try {
    if (!validId(req.params.id)) return res.sendStatus(400);
    await fs.access(metaFile(req.params.id));
    await writeQueue;
    let items = { document: {}, note: {} };
    try {
      items = JSON.parse(
        await fs.readFile(
          path.join(path.dirname(metaFile(req.params.id)), 'annotations.json'),
          'utf8',
        ),
      );
    } catch (e) {
      if (e.code !== 'ENOENT') throw e;
    }
    res.json(items);
  } catch (e) {
    next(e);
  }
});
app.put(
  '/api/documents/:id/annotations/:side/:page',
  async (req, res, next) => {
    try {
      const { id, side, page } = req.params;
      if (
        !validId(id) ||
        !['document', 'note'].includes(side) ||
        !/^\d+$/.test(page) ||
        Number(page) < 1 ||
        !validAnnotationItems(req.body.items)
      )
        return res.status(400).json({ error: '批注内容无效' });
      await fs.access(metaFile(id));
      const job = writeQueue.then(async () => {
        const dir = path.dirname(metaFile(id)),
          file = path.join(dir, 'annotations.json');
        let all = { document: {}, note: {} };
        try {
          all = JSON.parse(await fs.readFile(file, 'utf8'));
        } catch (e) {
          if (e.code !== 'ENOENT') throw e;
        }
        if (
          JSON.stringify(all[side]?.[page] || []) ===
          JSON.stringify(req.body.items)
        )
          return;
        const history = path.join(dir, 'annotation-history');
        await fs.mkdir(history, { recursive: true });
        const backup = path.join(history, `${Date.now()}-${randomUUID()}.json`);
        await fs.writeFile(backup + '.tmp', JSON.stringify(all));
        await moveFile(backup + '.tmp', backup);
        all[side] ||= {};
        all[side][page] = req.body.items;
        await fs.writeFile(file + '.tmp', JSON.stringify(all));
        await moveFile(file + '.tmp', file);
        await projects.persist(id);
      });
      writeQueue = job.catch(() => {});
      await job;
      res.json({ ok: true });
    } catch (e) {
      next(e);
    }
  },
);
app.post('/api/attachments', upload.single('file'), async (req, res, next) => {
  try {
    if (!req.file) throw Error('没有附件');
    const name = Buffer.from(req.file.originalname, 'latin1').toString('utf8');
    const ext = path.extname(name).toLowerCase();
    if (
      ![
        '.pdf',
        '.png',
        '.jpg',
        '.jpeg',
        '.gif',
        '.webp',
        '.mp3',
        '.mp4',
        '.md',
        '.markdown',
        '.txt',
        '.docx',
        '.pptx',
      ].includes(ext)
    )
      throw Error('此附件类型暂不支持');
    const filename = randomUUID() + ext;
    await moveFile(req.file.path, path.join(data, 'uploads', filename));
    res.json({
      name,
      url: '/attachments/' + filename,
      image: ['.png', '.jpg', '.jpeg', '.gif', '.webp'].includes(ext),
    });
  } catch (e) {
    if (req.file) await fs.unlink(req.file.path).catch(() => {});
    next(e);
  }
});
app.use(
  '/files',
  express.static(path.join(data, 'documents'), {
    dotfiles: 'deny',
    setHeaders: (res) => res.setHeader('X-Content-Type-Options', 'nosniff'),
  }),
);
app.use(
  '/attachments',
  express.static(path.join(data, 'uploads'), {
    setHeaders: (res) => res.setHeader('X-Content-Type-Options', 'nosniff'),
  }),
);
if (process.argv.includes('--dev')) {
  const { createServer } = await import('vite');
  const vite = await createServer({
    root,
    server: { middlewareMode: true },
    appType: 'spa',
  });
  app.use(vite.middlewares);
} else app.use(express.static(path.join(root, 'dist')));
app.use((e, req, res, next) =>
  res.status(e.status || 400).json({
    error: e.message,
    code: e.code,
    currentMarkdown: e.currentMarkdown,
    revision: e.revision,
    draftVersion: e.draftVersion,
  }),
);
export function startServer(port = Number(process.env.PORT || 4173)) {
  return new Promise((resolve, reject) => {
    const server = app.listen(port, '127.0.0.1');
    server.once('listening', () => resolve(server));
    server.once('error', reject);
  });
}
if (
  process.argv[1] &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  const server = await startServer();
  console.log(`pagenote: http://127.0.0.1:${server.address().port}`);
}

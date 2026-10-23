import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import express from 'express';
import { createHash } from 'node:crypto';
import { createProjectStore } from '../projects.mjs';

const workspace = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-layout-'));
const data = path.join(workspace, 'managed');
const app = express();
app.use(express.json());
const store = createProjectStore({ app, data });
app.use((error, req, res, next) =>
  res.status(400).json({ error: error.message }),
);
const server = app.listen(0, '127.0.0.1');
await new Promise((resolve) => server.once('listening', resolve));
async function post(route, body) {
  const response = await fetch(
    `http://127.0.0.1:${server.address().port}${route}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    },
  );
  return { ok: response.ok, value: await response.json() };
}
async function standalone(name, ext, text) {
  const id = createHash('sha256').update(name).update(text).digest('hex');
  const folder = path.join(data, 'documents', id);
  await fs.mkdir(folder, { recursive: true });
  const meta = {
    id,
    name,
    ext,
    kind: ext === '.md' ? 'markdown' : 'pdf',
    created: new Date().toISOString(),
    url: `/files/${id}/original${ext}`,
  };
  await fs.writeFile(path.join(folder, 'meta.json'), JSON.stringify(meta));
  await fs.writeFile(path.join(folder, 'original' + ext), text);
  if (ext === '.pdf') await fs.writeFile(path.join(folder, 'view.pdf'), text);
  return { meta, folder };
}
try {
  const parent = path.join(workspace, '用户项目');
  const created = await post('/api/projects', { name: '课程讲解', parent });
  assert.ok(created.ok);
  const meta = created.value;
  const manifest = JSON.parse(await fs.readFile(meta.projectFile));
  assert.equal(manifest.left, 'src/source.md');
  assert.equal(manifest.right, 'src/notes.md');
  assert.ok(
    (await fs.stat(path.join(meta.projectRoot, 'src/assets'))).isDirectory(),
  );
  assert.equal(
    (await post('/api/projects', { name: '课程讲解', parent })).ok,
    false,
    'never overwrite an existing project',
  );
  for (const name of ['..', '.', 'CON', 'bad/name', 'trailing.'])
    assert.equal((await post('/api/projects', { name, parent })).ok, false);
  const broken = await standalone('缺失文件.md', '.md', 'temporary');
  await fs.rename(
    path.join(broken.folder, 'original.md'),
    path.join(broken.folder, 'original.backup.md'),
  );
  assert.equal(
    (await post(`/api/projects/${broken.meta.id}/adopt`, {})).ok,
    false,
  );
  assert.equal(
    (await post('/api/projects/open', { filePath: meta.projectFile })).ok,
    true,
    'failed adoption does not block unrelated project opens',
  );

  const image = '11111111-1111-4111-8111-111111111111.png';
  await fs.mkdir(path.join(data, 'uploads'), { recursive: true });
  await fs.writeFile(path.join(data, 'uploads', image), 'image-bytes');
  const old = await standalone(
    '原来的文稿.md',
    '.md',
    '# 原文\n\n![图](/attachments/' + image + ')',
  );
  const notes = {
    1: '第一页 $x^2$\n\n![图](/attachments/' + image + ')',
    2: '',
    3: '第三页解释',
  };
  await fs.writeFile(
    path.join(old.folder, 'notes.json'),
    JSON.stringify(notes),
  );
  await fs.writeFile(
    path.join(old.folder, 'ai-reviews.json'),
    '[{"id":"tag-1"}]',
  );
  await fs.writeFile(
    path.join(old.folder, 'source-history.keep'),
    'historical backup',
  );
  const [adopted, repeated] = await Promise.all([
    store.ensureProject(old.meta),
    store.ensureProject(old.meta),
  ]);
  assert.equal(
    adopted.projectFile,
    repeated.projectFile,
    'concurrent adoption is idempotent',
  );
  assert.equal(
    await fs.readFile(path.join(old.folder, 'source-history.keep'), 'utf8'),
    'historical backup',
  );
  assert.ok(
    (
      await fs.readFile(path.join(adopted.projectRoot, 'src/source.md'), 'utf8')
    ).includes('assets/' + image),
  );
  assert.ok(
    (
      await fs.readFile(path.join(adopted.projectRoot, 'src/notes.md'), 'utf8')
    ).includes('<!-- pagebreak -->'),
  );
  assert.equal(
    await fs.readFile(
      path.join(adopted.projectRoot, 'src/assets', image),
      'utf8',
    ),
    'image-bytes',
  );
  assert.equal(
    await fs.readFile(
      path.join(adopted.projectRoot, 'src/ai-reviews.json'),
      'utf8',
    ),
    '[{"id":"tag-1"}]',
  );

  const pdf = await standalone('课件.pdf', '.pdf', '%PDF-1.4 test fixture');
  const imported = await store.bindLeft(pdf.meta);
  assert.ok(imported.projectFile, 'direct import always creates a project');
  assert.equal(
    await fs.readFile(
      path.join(imported.projectRoot, 'src/source.pdf'),
      'utf8',
    ),
    '%PDF-1.4 test fixture',
  );
  await store.bindLeft(pdf.meta, adopted.id);
  assert.equal(
    JSON.parse(await fs.readFile(adopted.projectFile)).left,
    'src/source.pdf',
  );
  assert.ok(
    (
      await fs.readFile(path.join(adopted.projectRoot, 'src/notes.md'), 'utf8')
    ).includes('第三页解释'),
    'replacing source preserves notes',
  );
  await store.bindLeft(old.meta, adopted.id);
  assert.equal(
    JSON.parse(await fs.readFile(adopted.projectFile)).preview,
    undefined,
    'switching back to Markdown removes PDF preview link',
  );

  const moved = path.join(workspace, '搬到另一台电脑');
  await fs.rename(adopted.projectRoot, moved);
  const otherData = path.join(workspace, 'fresh-profile');
  const portableStore = createProjectStore({ app: express(), data: otherData });
  const reopened = await portableStore.open(
    path.join(moved, 'project.pagenote'),
  );
  assert.equal(reopened.projectRoot, moved);
  assert.deepEqual(
    JSON.parse(
      await fs.readFile(
        path.join(otherData, 'documents', adopted.id, 'notes.json'),
      ),
    ),
    notes,
    'new profile reconstructs every note including blank pages',
  );
  assert.equal(
    await fs.readFile(path.join(otherData, 'uploads', image), 'utf8'),
    'image-bytes',
  );
  // Editing the raw Markdown is authoritative when reopening, not notes.json.
  await fs.writeFile(path.join(moved, 'src/notes.md'), '外部修改后的笔记');
  await portableStore.open(path.join(moved, 'project.pagenote'));
  assert.equal(
    JSON.parse(
      await fs.readFile(
        path.join(otherData, 'documents', adopted.id, 'notes.json'),
      ),
    )['1'],
    '外部修改后的笔记',
  );

  const legacy = path.join(workspace, 'legacy');
  await fs.mkdir(path.join(legacy, 'src'), { recursive: true });
  await fs.writeFile(path.join(legacy, 'src/left.md'), '# Legacy source');
  await fs.writeFile(path.join(legacy, 'src/right.md'), 'Legacy notes');
  const legacyFile = path.join(legacy, 'project.page-notes.json');
  const legacyManifest = {
    ...manifest,
    format: 'page-notes-project',
    left: 'src/left.md',
    right: 'src/right.md',
  };
  await fs.writeFile(legacyFile, JSON.stringify(legacyManifest));
  await portableStore.open(legacyFile);
  assert.equal(
    await fs.readFile(path.join(legacy, 'src/right.md'), 'utf8'),
    'Legacy notes',
  );
  await fs.writeFile(
    legacyFile,
    JSON.stringify({ ...legacyManifest, left: '../outside.md' }),
  );
  await assert.rejects(portableStore.open(legacyFile), /src/);
  await fs.writeFile(
    legacyFile,
    JSON.stringify({ ...legacyManifest, right: 'src/left.md' }),
  );
  await assert.rejects(portableStore.open(legacyFile), /独立/);
  console.log(
    'PASS project layout: create/import/adopt, collision safety, source replacement, notes/assets/Tags, fresh-profile relocation and legacy links.',
  );
} finally {
  await new Promise((resolve) => server.close(resolve));
  // Keep the isolated temporary fixture for debugging; no user project touched.
  console.log('Test fixtures:', workspace);
}

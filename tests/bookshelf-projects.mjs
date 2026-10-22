import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
const tmp = await fs.mkdtemp(
  path.join(os.tmpdir(), 'pagenote-shelf-projects-'),
);
process.env.PAGE_NOTES_DATA = path.join(tmp, 'data');
let server;
try {
  const library = path.join(tmp, 'library');
  await fs.mkdir(library);
  const { startServer } = await import('../server.mjs');
  server = await startServer(0);
  const base = `http://127.0.0.1:${server.address().port}`;
  async function api(url, body, method = 'POST') {
    const response = await fetch(base + url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    return { ok: response.ok, value: await response.json() };
  }
  assert.ok((await api('/api/bookshelf/config', { root: library }, 'PUT')).ok);
  const first = await api('/api/projects', { name: 'First' });
  assert.ok(first.ok);
  assert.equal(
    first.value.projectRoot,
    path.join(await fs.realpath(library), 'First'),
  );
  assert.equal((await api('/api/projects', { name: 'First' })).ok, false);
  const form = new FormData();
  form.append('file', new Blob(['# Imported notebook']), 'Imported.md');
  const uploaded = await (
    await fetch(base + '/api/documents', { method: 'POST', body: form })
  ).json();
  const imported = await api('/api/bookshelf/projects/import', {
    documentId: uploaded.id,
    name: 'Imported',
  });
  assert.ok(imported.ok);
  assert.notEqual(imported.value.id, first.value.id);
  assert.equal(
    await fs.readFile(path.join(library, 'Imported/src/left.md'), 'utf8'),
    '# Imported notebook',
  );
  assert.equal(
    await fs.readFile(path.join(library, 'First/src/left.md'), 'utf8'),
    '# First\n',
  );
  const failed = await api('/api/bookshelf/projects/import', {
    documentId: 'b'.repeat(64),
    name: 'Missing',
  });
  assert.equal(failed.ok, false);
  await assert.rejects(fs.stat(path.join(library, 'Missing')));
  const listing = await (await fetch(base + '/api/bookshelf')).json();
  assert.equal(listing.books.length, 2);
  const sourcePath = path.join(library, 'First/src/left.md');
  const outside = path.join(tmp, 'outside.md');
  await fs.writeFile(outside, 'Outside stays private');
  await fs.unlink(sourcePath);
  await fs.symlink(outside, sourcePath);
  assert.equal(
    (await api('/api/projects/open', { filePath: first.value.projectFile })).ok,
    false,
    'open revalidates links after shelf scan',
  );
  await fs.unlink(sourcePath);
  await fs.writeFile(sourcePath, '# First\n');
  const notesPath = path.join(library, 'First/src/right.md');
  await fs.unlink(notesPath);
  await fs.symlink(outside, notesPath);
  assert.equal(
    (
      await api(
        `/api/documents/${first.value.id}/notes/1`,
        { markdown: 'Must not escape' },
        'PUT',
      )
    ).ok,
    false,
  );
  assert.equal(await fs.readFile(outside, 'utf8'), 'Outside stays private');
  console.log(
    'PASS bound-root new projects, independent import, collision and failure protection',
  );
} finally {
  if (server) await new Promise((resolve) => server.close(resolve));
  await fs.rm(tmp, { recursive: true, force: true });
}

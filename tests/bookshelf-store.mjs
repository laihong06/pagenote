import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';
import bookshelf from '../desktop/bookshelf.cjs';

const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-shelf-'));
const root = path.join(tmp, 'library');
async function fixture(name, options = {}) {
  const dir = path.join(root, name);
  await fs.mkdir(path.join(dir, 'src'), { recursive: true });
  const id = options.id || createHash('sha256').update(name).digest('hex');
  const manifest = {
    format: 'pagenote-project',
    version: 1,
    id,
    name,
    left: 'src/left.md',
    right: 'src/right.md',
  };
  await fs.writeFile(path.join(dir, 'src/left.md'), '# ' + name);
  await fs.writeFile(path.join(dir, 'src/right.md'), 'Original notes');
  await fs.writeFile(
    path.join(
      dir,
      options.legacy ? 'project.page-notes.json' : 'project.pagenote',
    ),
    JSON.stringify(manifest),
  );
  return { dir, manifest };
}
try {
  const first = await fixture('First');
  await fixture('nested/Second', { legacy: true, id: first.manifest.id });
  await fixture('First/src/not-a-book');
  const bad = await fixture('Broken');
  await fs.writeFile(path.join(bad.dir, 'project.pagenote'), '{}');
  await fs.symlink(first.dir, path.join(root, 'Alias'), 'dir');
  const unsafe = await fixture('Unsafe');
  await fs.unlink(path.join(unsafe.dir, 'src/left.md'));
  await fs.symlink(
    path.join(first.dir, 'src/left.md'),
    path.join(unsafe.dir, 'src/left.md'),
  );
  const store = bookshelf.createBookshelfStore({
    data: path.join(tmp, 'data'),
  });
  assert.equal((await store.config()).root, null);
  await store.bind(root);
  const result = await store.scan();
  assert.equal(result.books.length, 2);
  assert.equal(
    new Set(result.books.map((b) => b.key)).size,
    2,
    'same manifest id is not shelf identity',
  );
  assert.ok(result.warnings.length >= 2);
  assert.equal(
    (await store.getBook(result.books[0].key)).id,
    first.manifest.id,
  );
  await store.setView({ view: 'list', sort: 'name' });
  assert.equal(
    (
      await bookshelf
        .createBookshelfStore({ data: path.join(tmp, 'data') })
        .config()
    ).view,
    'list',
  );
  await assert.rejects(store.bind(path.join(tmp, 'absent')));
  assert.equal((await store.config()).root, await fs.realpath(root));
  const bounded = bookshelf.createBookshelfStore({
    data: path.join(tmp, 'limited'),
    limits: { directories: 1 },
  });
  await bounded.bind(root);
  assert.ok((await bounded.scan()).warnings.length, 'partial scan warns');
  const other = path.join(tmp, 'other');
  await fs.mkdir(other);
  await store.bind(other);
  await assert.rejects(store.getBook(result.books[0].key));
  assert.equal((await store.scan()).books.length, 0);
  await fs.rm(other, { recursive: true });
  await assert.rejects(store.scan(), /目录|folder/i);
  await fs.writeFile(path.join(tmp, 'data/bookshelf.json'), '{bad');
  await assert.rejects(store.config());
  await store.bind(root);
  assert.equal(
    (await store.scan()).books.length,
    2,
    'explicit rebind repairs corrupt local settings',
  );
  console.log(
    'PASS bookshelf discovery, identities, bounded traversal, safe links and persisted binding',
  );
} finally {
  await fs.rm(tmp, { recursive: true, force: true });
}

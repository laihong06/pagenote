import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import sharp from 'sharp';
import shelf from '../desktop/bookshelf.cjs';
const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-cover-'));
try {
  const root = path.join(tmp, 'library'),
    dir = path.join(root, 'Book');
  await fs.mkdir(path.join(dir, 'src/assets'), { recursive: true });
  await fs.writeFile(
    path.join(dir, 'project.pagenote'),
    JSON.stringify({
      format: 'pagenote-project',
      version: 1,
      id: 'a'.repeat(64),
      name: 'Book',
      left: 'src/left.pdf',
      right: 'src/right.md',
    }),
  );
  await fs.writeFile(path.join(dir, 'src/left.pdf'), '%PDF-fixture');
  await fs.writeFile(path.join(dir, 'src/right.md'), 'Notes remain');
  const store = shelf.createBookshelfStore({ data: path.join(tmp, 'data') });
  await store.bind(root);
  const book = (await store.scan()).books[0];
  const saved = await store.saveAppearance(book.key, {
    version: 1,
    title: 'My title',
    cover: 'pdf',
    page: 2,
    paper: 'sky',
  });
  assert.equal(saved.title, 'My title');
  assert.equal((await store.coverSource(book.key)).type, 'application/pdf');
  await assert.rejects(
    store.saveAppearance(book.key, {
      version: 1,
      cover: 'image',
      asset: '../outside.png',
    }),
  );
  await assert.rejects(store.saveAppearance(book.key, { version: 2 }));
  await assert.rejects(
    store.saveAppearance(book.key, { version: 1, page: -1 }),
  );
  await assert.rejects(
    store.saveAppearance(book.key, { version: 1, title: 'x'.repeat(201) }),
  );
  const png = await sharp({
    create: { width: 30, height: 40, channels: 3, background: '#75ab97' },
  })
    .png()
    .toBuffer();
  const image = await store.saveCover(book.key, png);
  assert.equal(image.appearance.cover, 'image');
  const asset = path.join(dir, 'src/assets', image.appearance.asset);
  assert.ok((await fs.stat(asset)).size);
  assert.equal(
    (await store.coverSource(book.key)).file,
    await fs.realpath(asset),
  );
  const beforeRevision = (await store.getBook(book.key)).revision;
  await fs.utimes(asset, new Date(), new Date(Date.now() + 2000));
  assert.notEqual(
    (await store.getBook(book.key)).revision,
    beforeRevision,
    'external image changes invalidate thumbnail',
  );
  await assert.rejects(store.saveCover(book.key, Buffer.from('not an image')));
  await assert.rejects(
    store.saveCover(book.key, Buffer.alloc(20 * 1024 * 1024 + 1)),
  );
  await store.saveAppearance(book.key, { version: 1, cover: 'default' });
  assert.ok((await fs.stat(asset)).size, 'reset does not delete assets');
  await fs.writeFile(path.join(dir, 'src/bookshelf.json'), '{bad');
  const fallback = (await store.scan()).books[0];
  assert.equal(fallback.appearance.cover, 'default');
  assert.ok(fallback.warning);
  await fs.rm(path.join(dir, 'src/assets'), { recursive: true });
  await fs.mkdir(path.join(tmp, 'outside'));
  await fs.symlink(
    path.join(tmp, 'outside'),
    path.join(dir, 'src/assets'),
    process.platform === 'win32' ? 'junction' : 'dir',
  );
  await assert.rejects(store.saveCover(book.key, png), /符号链接/);
  assert.equal(
    await fs.readFile(path.join(dir, 'src/right.md'), 'utf8'),
    'Notes remain',
  );
  console.log(
    'PASS Android-compatible appearance, PDF source, safe image copy, limits and reset',
  );
} finally {
  await fs.rm(tmp, { recursive: true, force: true });
}

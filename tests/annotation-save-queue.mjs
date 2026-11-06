import assert from 'node:assert/strict';
import { createAnnotations } from '../src/annotations.js';

class MemoryStorage {
  #items = new Map();
  get length() {
    return this.#items.size;
  }
  key(index) {
    return [...this.#items.keys()][index] ?? null;
  }
  getItem(key) {
    return this.#items.get(key) ?? null;
  }
  setItem(key, value) {
    this.#items.set(key, String(value));
  }
  removeItem(key) {
    this.#items.delete(key);
  }
}

class FakeNode {
  dataset = {};
  classList = { toggle: () => {} };
  offsetWidth = 500;
  offsetHeight = 500;
  isConnected = true;
  setAttribute() {}
  append() {}
  replaceChildren() {}
  addEventListener() {}
  querySelector() {
    return null;
  }
  getBoundingClientRect() {
    return { left: 0, top: 0, width: 500, height: 500 };
  }
}

function installTestEnvironment() {
  const storage = new MemoryStorage();
  const controls = new Map();
  globalThis.localStorage = storage;
  globalThis.document = {
    querySelectorAll: () => [],
    getElementById: (id) => {
      if (!controls.has(id)) controls.set(id, new FakeNode());
      return controls.get(id);
    },
    createElementNS: () => new FakeNode(),
    addEventListener: () => {},
  };
  const ink = (x) => [
    { type: 'pen', color: '#000000', width: 3, points: [[x, x]] },
  ];
  storage.setItem('page-notes-ink:fixture:document:1', JSON.stringify(ink(1)));
  storage.setItem('page-notes-ink:fixture:document:2', JSON.stringify(ink(2)));
  return { storage, ink };
}

async function retryKeepsEveryUnwrittenPage() {
  const { storage, ink } = installTestEnvironment();
  const attempts = [];
  const saved = new Map();
  let firstFailure = true;
  const annotations = createAnnotations({
    getDocument: () => ({ id: 'fixture' }),
    getPage: () => 1,
    toast: () => {},
    request: async (url, options) => {
      if (!options) return { document: {}, note: {} };
      const page = Number(url.split('/').at(-1));
      attempts.push(page);
      if (firstFailure) {
        firstFailure = false;
        throw Error('disk unavailable');
      }
      saved.set(page, JSON.parse(options.body).items);
    },
  });
  await annotations.load('fixture');
  await assert.rejects(annotations.flush(false), /disk unavailable/);
  assert.ok(storage.getItem('page-notes-ink:fixture:document:1'));
  assert.ok(storage.getItem('page-notes-ink:fixture:document:2'));
  await annotations.flush(false);
  assert.deepEqual(attempts, [1, 1, 2]);
  assert.deepEqual(saved.get(1), ink(1));
  assert.deepEqual(saved.get(2), ink(2));
  assert.equal(storage.length, 0);
}

async function queuedFlushCannotSkipAnEarlierFailure() {
  const { ink } = installTestEnvironment();
  const saved = new Map();
  let rejectFirst;
  let firstStarted;
  const started = new Promise((resolve) => {
    firstStarted = resolve;
  });
  let failed = false;
  const annotations = createAnnotations({
    getDocument: () => ({ id: 'fixture' }),
    getPage: () => 1,
    toast: () => {},
    request: async (url, options) => {
      if (!options) return { document: {}, note: {} };
      const page = Number(url.split('/').at(-1));
      if (!failed) {
        failed = true;
        firstStarted();
        await new Promise((_, reject) => {
          rejectFirst = reject;
        });
      }
      saved.set(page, JSON.parse(options.body).items);
    },
  });
  await annotations.load('fixture');
  const first = annotations.flush(false);
  const firstRejected = assert.rejects(first, /disk unavailable/);
  await started;
  const queued = annotations.flush(false);
  rejectFirst(Error('disk unavailable'));
  await firstRejected;
  await queued;
  assert.deepEqual(
    saved.get(1),
    ink(1),
    'queued save must retry the failed page before resolving',
  );
  assert.deepEqual(
    saved.get(2),
    ink(2),
    'queued save must also write pages not yet attempted',
  );
}

async function failureInTheMiddleRetainsOnlyUnwrittenPages() {
  const { storage, ink } = installTestEnvironment();
  storage.setItem('page-notes-ink:fixture:document:3', JSON.stringify(ink(3)));
  const attempts = [];
  let shouldFail = true;
  const annotations = createAnnotations({
    getDocument: () => ({ id: 'fixture' }),
    getPage: () => 1,
    toast: () => {},
    request: async (url, options) => {
      if (!options) return { document: {}, note: {} };
      const page = Number(url.split('/').at(-1));
      attempts.push(page);
      if (page === 2 && shouldFail) {
        shouldFail = false;
        throw Error('disk unavailable');
      }
    },
  });
  await annotations.load('fixture');
  await assert.rejects(annotations.flush(false), /disk unavailable/);
  assert.equal(storage.getItem('page-notes-ink:fixture:document:1'), null);
  await annotations.flush(false);
  assert.deepEqual(attempts, [1, 2, 2, 3]);
  assert.equal(storage.length, 0);
}

async function queuedFlushPreservesNewerEdits() {
  installTestEnvironment();
  const saved = new Map();
  let rejectFirst;
  let firstStarted;
  const started = new Promise((resolve) => {
    firstStarted = resolve;
  });
  let failed = false;
  const annotations = createAnnotations({
    getDocument: () => ({ id: 'fixture' }),
    getPage: () => 2,
    toast: () => {},
    request: async (url, options) => {
      if (!options) return { document: {}, note: {} };
      const page = Number(url.split('/').at(-1));
      if (!failed) {
        failed = true;
        firstStarted();
        await new Promise((_, reject) => {
          rejectFirst = reject;
        });
      }
      saved.set(page, JSON.parse(options.body).items);
    },
  });
  await annotations.load('fixture');
  annotations.mountDocument([new FakeNode(), new FakeNode()]);
  const first = annotations.flush(false);
  const firstRejected = assert.rejects(first, /disk unavailable/);
  await started;
  annotations.addText('document', 'newer edit');
  const queued = annotations.flush(false);
  rejectFirst(Error('disk unavailable'));
  await firstRejected;
  await queued;
  assert.ok(saved.has(1), 'a pending save must retry the failed page');
  assert.equal(saved.get(2)?.at(-1)?.text, 'newer edit');
}

async function queuedFlushReportsPersistentFailure() {
  installTestEnvironment();
  let rejectFirst;
  let firstStarted;
  const started = new Promise((resolve) => {
    firstStarted = resolve;
  });
  let attempts = 0;
  let requestedOtherDocument = false;
  const annotations = createAnnotations({
    getDocument: () => ({ id: 'fixture' }),
    getPage: () => 1,
    toast: () => {},
    request: async (_url, options) => {
      if (!options) {
        if (_url.includes('/other/')) requestedOtherDocument = true;
        return { document: {}, note: {} };
      }
      if (++attempts === 1) {
        firstStarted();
        await new Promise((_, reject) => {
          rejectFirst = reject;
        });
      }
      throw Error('disk unavailable');
    },
  });
  await annotations.load('fixture');
  const first = annotations.flush(false);
  const firstRejected = assert.rejects(first, /disk unavailable/);
  await started;
  const queuedRejected = assert.rejects(
    annotations.load('other'),
    /disk unavailable/,
  );
  rejectFirst(Error('disk unavailable'));
  await firstRejected;
  await queuedRejected;
  assert.ok(
    attempts >= 2,
    'queued save must retry instead of silently reporting success',
  );
  assert.equal(
    requestedOtherDocument,
    false,
    'document switch must wait for pending ink',
  );
}

await retryKeepsEveryUnwrittenPage();
await queuedFlushCannotSkipAnEarlierFailure();
await failureInTheMiddleRetainsOnlyUnwrittenPages();
await queuedFlushPreservesNewerEdits();
await queuedFlushReportsPersistentFailure();
console.log(
  'PASS annotation save retries every page and queued saves wait for recovery',
);

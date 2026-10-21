import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const timeline = require('../docs/spec/yuketang/fixtures/live-reconnect-timeline.json');
const { createRainClassroomService } = require('../desktop/yuketang.cjs');

const sockets = [];
class FakeWebSocket {
  constructor(url) {
    assert.equal(url, 'wss://www.yuketang.cn/wsapp/');
    this.listeners = new Map();
    this.sent = [];
    sockets.push(this);
  }

  addEventListener(type, listener) {
    const listeners = this.listeners.get(type) || [];
    listeners.push(listener);
    this.listeners.set(type, listeners);
  }

  send(value) {
    this.sent.push(JSON.parse(value));
  }

  close() {
    this.emit('close', {});
  }

  emit(type, event) {
    for (const listener of this.listeners.get(type) || []) listener(event);
  }
}

const service = createRainClassroomService({
  secureStore: {
    get: async () => JSON.stringify({ cookie: 'session=fake' }),
    set: async () => {},
    clear: async () => {},
  },
  fetchImpl: async (url, options) => {
    const requestUrl = new URL(url);
    assert.equal(requestUrl.hostname, 'www.yuketang.cn');
    assert.equal(options.headers.Cookie, 'session=fake');
    if (requestUrl.pathname === '/api/v3/lesson/checkin')
      return jsonResponse({
        data: { lessonToken: 'token', identityId: 'identity' },
      });
    if (requestUrl.pathname === '/api/v3/lesson/presentation/fetch') {
      const presentationId = requestUrl.searchParams.get('presentation_id');
      const pages = {
        'fixture-presentation-a': [
          { id: 'a-0', index: 0, cover: 'https://cdn.yuketang.cn/a-0.jpg' },
          { id: 'a-1', index: 1, cover: 'https://cdn.yuketang.cn/a-1.jpg' },
        ],
        'fixture-presentation-b': [
          { id: 'b-0', index: 0, cover: 'https://cdn.yuketang.cn/b-0.jpg' },
          { id: 'b-1', index: 1, cover: 'https://cdn.yuketang.cn/b-1.jpg' },
        ],
      };
      return jsonResponse({ data: { slides: pages[presentationId] } });
    }
    throw new Error(`Unexpected request: ${requestUrl}`);
  },
  WebSocketImpl: FakeWebSocket,
});

const appended = [];
let failedOnce = false;
await service.startLive({
  lessonId: 'fixture-lesson',
  onSlide: async (page) => {
    appended.push(`${page.presentationId}:${page.sourceIndex}`);
    if (page.sourceIndex === 1 && !failedOnce) {
      failedOnce = true;
      throw new Error('fixture write failure');
    }
    return { added: true, page: appended.length };
  },
});

const first = sockets[0];
first.emit('open', {});
assert.equal(first.sent[0].op, 'hello');
first.emit('message', {
  data: JSON.stringify({
    op: 'hello',
    timeline: [{ pres: 'fixture-presentation-a', si: 0 }],
  }),
});
await waitFor(() => service.getLiveStatus().latest?.sourceIndex === 0);
assert.deepEqual(appended, ['fixture-presentation-a:0']);

first.emit('close', {});
await waitFor(() => sockets.length === 2, 3500);
const second = sockets[1];
second.emit('open', {});
second.emit('message', { data: JSON.stringify(timeline) });
await waitFor(() => service.getLiveStatus().status === 'error');
assert.equal(service.getLiveStatus().pendingSlides, 3);
assert.match(service.getLiveStatus().error, /fixture write failure/);

await service.stopLive();
assert.equal(service.getLiveStatus().status, 'stopped');
assert.equal(service.getLiveStatus().pendingSlides, 3);
await assert.rejects(
  service.startLive({ lessonId: 'next-lesson', onSlide: async () => {} }),
  /未写入的课堂页面/,
);

await service.retryLive();
assert.equal(service.getLiveStatus().status, 'stopped');
assert.equal(service.getLiveStatus().pendingSlides, 0);
assert.equal(
  service.getLiveStatus().latest.presentationId,
  'fixture-presentation-b',
);
assert.equal(service.getLiveStatus().latest.sourceIndex, 1);
assert.deepEqual(appended, [
  'fixture-presentation-a:0',
  'fixture-presentation-a:1',
  'fixture-presentation-a:1',
  'fixture-presentation-b:0',
  'fixture-presentation-b:1',
]);
service.stopLive();

function jsonResponse(value, headers = {}) {
  return new Response(JSON.stringify(value), {
    headers: { 'content-type': 'application/json', ...headers },
  });
}

async function waitFor(predicate, timeoutMs = 1500) {
  const started = Date.now();
  while (!predicate()) {
    if (Date.now() - started > timeoutMs)
      throw new Error('Timed out waiting for Rain event');
    await new Promise((resolve) => setTimeout(resolve, 10));
  }
}

console.log('Yuketang live service recovery and retry tests passed');

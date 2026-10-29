import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const ticketFixture = require('../docs/spec/yuketang/fixtures/login-ticket.json');
const { assertRainQrTicketUrl } = require('../desktop/yuketang-protocol.cjs');
const { createRainClassroomService } = require('../desktop/yuketang.cjs');

const sockets = [];
class FakeWebSocket {
  constructor() {
    sockets.push(this);
    this.listeners = new Map();
    queueMicrotask(() => this.emit('open', {}));
  }

  addEventListener(type, listener) {
    const listeners = this.listeners.get(type) || [];
    listeners.push(listener);
    this.listeners.set(type, listeners);
  }

  send(value) {
    assert.equal(JSON.parse(value).op, 'requestlogin');
    queueMicrotask(() =>
      this.emit('message', {
        data: JSON.stringify({
          ticket: ticketFixture.ticket,
        }),
      }),
    );
  }

  close() {}

  emit(type, event) {
    for (const listener of this.listeners.get(type) || []) listener(event);
  }
}

assert.equal(
  assertRainQrTicketUrl(ticketFixture.ticket).hostname,
  'mp.weixin.qq.com',
);
assert.throws(() =>
  assertRainQrTicketUrl('https://mp.weixin.qq.com.attacker.invalid/qr'),
);
assert.throws(() =>
  assertRainQrTicketUrl('http://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=x'),
);

const imageBytes = Buffer.from('fixture-image');
const service = createRainClassroomService({
  secureStore: {
    get: async () => null,
    set: async () => {},
    clear: async () => {},
  },
  fetchImpl: async (url, options) => {
    assert.equal(new URL(url).hostname, 'mp.weixin.qq.com');
    assert.equal('Cookie' in options.headers, false);
    return new Response(imageBytes, {
      headers: { 'content-type': 'image/jpg' },
    });
  },
  WebSocketImpl: FakeWebSocket,
});

const { loginId } = service.startLogin();
let state;
state = await waitForLoginState(service, loginId);

assert.equal(state.status, 'waiting_scan');
assert.equal(
  state.qrDataUrl,
  `data:image/jpeg;base64,${imageBytes.toString('base64')}`,
);
assert.equal('qrUrl' in state, false);
service.cancelLogin(loginId);

const unexpectedTypeService = createRainClassroomService({
  secureStore: {
    get: async () => null,
    set: async () => {},
    clear: async () => {},
  },
  fetchImpl: async () =>
    new Response('blocked', { headers: { 'content-type': 'text/html' } }),
  WebSocketImpl: FakeWebSocket,
});
const unexpectedTypeLogin = unexpectedTypeService.startLogin();
const failedState = await waitForLoginState(
  unexpectedTypeService,
  unexpectedTypeLogin.loginId,
);
assert.equal(failedState.status, 'failed');
assert.match(failedState.error, /text\/html/);
assert.equal(failedState.error.includes('fixture-only'), false);
unexpectedTypeService.cancelLogin(unexpectedTypeLogin.loginId);

let saved = null;
const authenticated = createRainClassroomService({
  secureStore: {
    get: async () => saved,
    set: async (value) => {
      saved = value;
    },
    clear: async () => {
      saved = null;
    },
  },
  WebSocketImpl: FakeWebSocket,
  fetchImpl: async (url, options) => {
    const parsed = new URL(url);
    if (parsed.hostname === 'mp.weixin.qq.com')
      return new Response(imageBytes, {
        headers: { 'content-type': 'image/png' },
      });
    if (parsed.pathname === '/pc/web_login')
      return new Response('{}', {
        headers: {
          'set-cookie': 'fixture=only; Secure',
          'set-auth': 'fixture-login-token',
        },
      });
    assert.equal(options.headers.Authorization, 'Bearer fixture-login-token');
    return new Response(
      JSON.stringify({ data: { id: 'fixture-user', name: 'Fixture' } }),
      {
        headers: {
          'content-type': 'application/json',
          'set-auth': 'Bearer fixture-validated-token',
        },
      },
    );
  },
});
const authLogin = authenticated.startLogin();
await waitForLoginState(authenticated, authLogin.loginId);
sockets
  .at(-1)
  .emit('message', {
    data: JSON.stringify({ UserID: 'fixture-user', Auth: 'fixture-auth' }),
  });
for (
  let index = 0;
  index < 50 &&
  authenticated.loginStatus(authLogin.loginId).status !== 'authenticated';
  index++
)
  await new Promise((resolve) => setTimeout(resolve, 5));
assert.equal(
  authenticated.loginStatus(authLogin.loginId).status,
  'authenticated',
);
assert.equal(JSON.parse(saved).authorization, 'Bearer fixture-validated-token');
authenticated.cancelLogin(authLogin.loginId);

async function waitForLoginState(service, loginId) {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    const state = service.loginStatus(loginId);
    if (state.status !== 'connecting' && state.status !== 'loading_qr')
      return state;
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
  throw new Error('Login state did not settle');
}

console.log('Yuketang in-app QR image tests passed');

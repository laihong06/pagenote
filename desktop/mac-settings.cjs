const { spawn } = require('node:child_process');
const fs = require('node:fs/promises');
const path = require('node:path');
const panes = new Set(['general', 'appearance', 'reading', 'ai']);
const promptModules = new Set([
  'system',
  'learning',
  'page-tutor',
  'output',
  'tag-edit',
  'learner',
]);
const choices = {
  language: ['zh-CN', 'en-US'],
  theme: ['system', 'light', 'dark'],
  markdownTheme: ['classic', 'paper'],
};

function createMacSettings({
  executable,
  paneFile,
  readState,
  applySetting,
  readPrompt,
  savePrompt,
  onQuit = () => {},
  onClosed = () => {},
  onError = () => {},
}) {
  let child,
    lastPane,
    sequence = 0,
    queue = Promise.resolve(),
    paneWrites = Promise.resolve();
  function fail(session, error) {
    if (session.failed) return;
    session.failed = true;
    session.exiting = true;
    for (const call of session.pending.values()) {
      clearTimeout(call.timer);
      call.reject(error);
    }
    session.pending.clear();
    session.process.stdin.destroy();
    session.process.kill();
    if (!session.disposing) onError(error);
  }
  function send(packet, session = child) {
    if (!session || session.exiting) return false;
    // A failed spawn has no PID. Its error event rejects the caller; writing
    // to the unborn process can otherwise produce a second EPIPE dialog.
    if (!session.process.pid) return true;
    if (!session.process.stdin.writable) {
      fail(session, Error('Native settings connection closed'));
      return false;
    }
    try {
      session.process.stdin.write(JSON.stringify(packet) + '\n', (error) => {
        if (error) fail(session, error);
      });
      return true;
    } catch (error) {
      fail(session, error);
      return false;
    }
  }
  async function remember(pane) {
    lastPane = pane;
    const write = async () => {
      await fs.mkdir(path.dirname(paneFile), { recursive: true });
      await fs.writeFile(paneFile + '.tmp', JSON.stringify({ pane }));
      await fs.rename(paneFile + '.tmp', paneFile);
    };
    paneWrites = paneWrites.then(write, write);
    await paneWrites;
  }
  async function handle(packet, session) {
    if (child !== session || session.exiting) return;
    if (!packet.requestId || typeof packet.requestId !== 'string') return;
    try {
      let prompt;
      if (packet.action === 'set') {
        if (Object.hasOwn(choices, packet.key)) {
          if (!choices[packet.key].includes(packet.value))
            throw Error('Unsupported setting value');
        } else if (['noteAnimation', 'expandEditor'].includes(packet.key)) {
          if (typeof packet.value !== 'boolean')
            throw Error('Setting must be boolean');
        } else throw Error('Unsupported setting');
        await applySetting(packet.key, packet.value);
      } else if (['read-prompt', 'save-prompt'].includes(packet.action)) {
        if (!promptModules.has(packet.module))
          throw Error('Unsupported prompt module');
        if (packet.action === 'save-prompt') {
          if (typeof packet.text !== 'string' || packet.text.length > 32768)
            throw Error('Prompt must be text of at most 32768 characters');
          await savePrompt(packet.module, packet.text);
        }
        prompt = await readPrompt(packet.module);
      } else throw Error('Unsupported native settings action');
      if (child === session)
        send(
          { replyTo: packet.requestId, state: await readState(), prompt },
          session,
        );
    } catch (error) {
      if (child === session)
        send(
          {
            replyTo: packet.requestId,
            error: error.message,
            state: await readState().catch(() => null),
          },
          session,
        );
    }
  }
  function start() {
    const process = spawn(executable, [], { stdio: ['pipe', 'pipe', 'pipe'] });
    let exit;
    const session = {
      process,
      pending: new Map(),
      exiting: false,
      closePending: null,
      exited: new Promise((resolve) => {
        exit = resolve;
      }),
    };
    child = session;
    const pending = session.pending;
    process.stdin.on('error', (error) => fail(session, error));
    process.stdout.setEncoding('utf8');
    process.stderr.setEncoding('utf8');
    let buffer = '',
      stderr = '';
    process.stderr.on('data', (chunk) => {
      stderr = (stderr + chunk).slice(-2000);
    });
    process.stdout.on('data', (chunk) => {
      buffer += chunk;
      if (buffer.length > 2 * 1024 * 1024) {
        process.stdin.end();
        return;
      }
      let newline;
      while ((newline = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, newline);
        buffer = buffer.slice(newline + 1);
        let packet;
        try {
          packet = JSON.parse(line);
        } catch {
          continue;
        }
        if (!packet || typeof packet !== 'object' || Array.isArray(packet))
          continue;
        if (packet.event === 'shown') {
          const call = pending.get(packet.requestId);
          if (call) {
            pending.delete(packet.requestId);
            clearTimeout(call.timer);
            call.resolve({
              native: true,
              pid: process.pid,
              windowNumber: packet.windowNumber,
              pane: packet.pane,
            });
          }
        } else if (packet.event === 'close-cancelled') {
          const call = pending.get(packet.requestId);
          if (call) {
            pending.delete(packet.requestId);
            clearTimeout(call.timer);
            call.resolve(false);
          }
        } else if (packet.event === 'pane' && panes.has(packet.pane)) {
          remember(packet.pane).catch(onError);
        } else if (packet.event === 'closed') {
          session.exiting = true;
        } else if (packet.event === 'quit') onQuit();
        else if (packet.action)
          queue = queue.then(() => handle(packet, session)).catch(onError);
      }
    });
    process.on('error', (error) => {
      session.failed = true;
      session.exiting = true;
      for (const call of pending.values()) {
        clearTimeout(call.timer);
        call.reject(error);
      }
      pending.clear();
      if (child === session) child = null;
      session.exiting = true;
      exit();
    });
    process.on('exit', (code, signal) => {
      if (child === session) child = null;
      session.exiting = true;
      for (const call of pending.values()) {
        clearTimeout(call.timer);
        if (call.command === 'close' && code === 0) call.resolve(true);
        else
          call.reject(
            Error('Native settings closed' + (stderr ? ': ' + stderr : '')),
          );
      }
      pending.clear();
      if ((code || signal) && !session.disposing && !session.failed)
        onError(
          Error('Native settings exited (' + (signal || code) + '): ' + stderr),
        );
      exit();
      onClosed();
    });
  }
  function call(command, payload = {}, session = child) {
    const requestId = 'electron-' + ++sequence;
    return new Promise((resolve, reject) => {
      const timer =
        command === 'close'
          ? null
          : setTimeout(() => {
              session.pending.delete(requestId);
              reject(Error('Native settings did not respond'));
            }, 15000);
      session.pending.set(requestId, { resolve, reject, timer, command });
      if (!send({ command, requestId, ...payload }, session)) {
        clearTimeout(timer);
        session.pending.delete(requestId);
        reject(Error('Native settings is closing'));
      }
    });
  }
  return {
    async open(pane) {
      if (pane !== undefined && !panes.has(pane))
        throw Error('Unsupported settings pane');
      if (!lastPane) {
        try {
          const saved = JSON.parse(await fs.readFile(paneFile, 'utf8'));
          if (panes.has(saved.pane)) lastPane = saved.pane;
        } catch {}
      }
      const state = await readState();
      while (child && (child.closePending || child.exiting)) {
        const previous = child;
        if (previous.closePending) await previous.closePending;
        else await previous.exited;
      }
      if (!child) start();
      return call('show', { state, pane: pane || lastPane || 'general' });
    },
    async sync() {
      const session = child;
      if (session && !session.closePending && !session.exiting) {
        const state = await readState();
        if (child === session) send({ command: 'sync', state }, session);
      }
    },
    async prepareQuit() {
      if (!child) return true;
      const session = child;
      session.closePending ||= call('close', {}, session).finally(() => {
        session.closePending = null;
      });
      const result = await session.closePending;
      await paneWrites;
      return result;
    },
    dispose() {
      if (child) {
        child.disposing = true;
        child.exiting = true;
        child.process.stdin.end();
      }
    },
  };
}
module.exports = { createMacSettings };

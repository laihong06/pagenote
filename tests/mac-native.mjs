import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron } from '@playwright/test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';

if (process.platform !== 'darwin') {
  console.log('SKIP macOS native lifecycle and editing');
  process.exit(0);
}

const root = path.resolve(import.meta.dirname, '..');
const profile = await fs.realpath(
  await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-mac-native-')),
);
async function fixture(name) {
  const directory = path.join(profile, name);
  await fs.mkdir(path.join(directory, 'src'), { recursive: true });
  await fs.writeFile(path.join(directory, 'src/left.md'), '# ' + name);
  await fs.writeFile(path.join(directory, 'src/right.md'), 'Notes for ' + name);
  const file = path.join(directory, 'project.pagenote');
  await fs.writeFile(
    file,
    JSON.stringify({
      format: 'pagenote-project',
      version: 1,
      id: createHash('sha256').update(name).digest('hex'),
      name,
      created: '2026-10-16T00:00:00.000Z',
      left: 'src/left.md',
      right: 'src/right.md',
    }),
  );
  return file;
}
const first = await fixture('Finder cold 中文');
const second = await fixture('Finder warm 中文');
const bootstrap = path.join(profile, 'cold-open.cjs');
await fs.writeFile(
  bootstrap,
  `
const { app } = require('electron');
app.setAppPath(${JSON.stringify(root)});
require(${JSON.stringify(path.join(root, 'desktop/main.cjs'))});
globalThis.nativeColdWasReady = app.isReady();
globalThis.nativeColdPrevented = false;
app.emit('open-file', { preventDefault() { globalThis.nativeColdPrevented = true; } }, ${JSON.stringify(first)});
`,
);
const app = await electron.launch({
  executablePath:
    process.env.DESKTOP_EXE ||
    path.join(
      root,
      'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron',
    ),
  args: process.env.DESKTOP_EXE ? [root, first] : [bootstrap],
  env: { ...process.env, PAGE_NOTES_USER_DATA: profile },
  timeout: 40000,
});
const failures = [];
let quitExpected = false;
async function check(name, work) {
  try {
    await work();
    console.log('PASS ' + name);
  } catch (error) {
    failures.push(error);
    console.error('FAIL ' + name + ': ' + error.message);
  }
}
try {
  const w = await app.firstWindow();
  w.setDefaultTimeout(5000);
  await w.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(w);
  const mainId = await app.evaluate(
    ({ BrowserWindow }) =>
      BrowserWindow.getAllWindows().find((win) => win.isVisible()).id,
  );
  const mainState = () =>
    app.evaluate(({ BrowserWindow }, id) => {
      const win = BrowserWindow.fromId(id);
      return (
        win && {
          id: win.id,
          visible: win.isVisible(),
          minimized: win.isMinimized(),
          focused: win.isFocused(),
        }
      );
    }, mainId);
  const waitProject = async (file) => {
    // Metadata updates before loading and the Finder-open focus cycle finish.
    // Exercise hide/close only after the project has finished opening.
    await w.waitForFunction(
      async (expected) =>
        document.querySelector('#loading').hidden &&
        (await window.pageNotes.getState()).document?.projectFile === expected,
      file,
      { timeout: 10000 },
    );
    await w.evaluate(() => window.pageNotes.waitVisuals());
  };

  await check(
    'Finder open-file queues a project before app ready',
    async () => {
      if (!process.env.DESKTOP_EXE) {
        assert.deepEqual(
          await app.evaluate(() => [
            globalThis.nativeColdWasReady,
            globalThis.nativeColdPrevented,
          ]),
          [false, true],
        );
      }
      await waitProject(first);
    },
  );
  await check(
    'Finder warm open restores and focuses the existing window',
    async () => {
      await app.evaluate(
        ({ app, BrowserWindow }, { id, file }) => {
          const win = BrowserWindow.fromId(id);
          win.minimize();
          win.hide();
          app.emit('open-file', { preventDefault() {} }, file);
        },
        { id: mainId, file: second },
      );
      await waitProject(second);
      const state = await mainState();
      assert.equal(state.visible, true);
      assert.equal(state.minimized, false);
      assert.equal(state.focused, true);
    },
  );
  await check(
    'Finder opening shows an application hidden with Cmd+H',
    async () => {
      await app.evaluate(({ app }) => app.hide());
      await assertEventually(
        () => app.evaluate(({ app }) => app.isHidden()),
        'application hides',
      );
      await app.evaluate(
        ({ app }, file) => app.emit('open-file', { preventDefault() {} }, file),
        second,
      );
      await assertEventually(
        async () =>
          !(await app.evaluate(({ app }) => app.isHidden())) &&
          (await mainState())?.focused,
        'Finder opening shows and focuses a hidden application',
      );
    },
  );
  // Continue testing menu and annotation behavior even if file-event support is absent.
  await w.evaluate((file) => window.pageNotes.openProject(file), second);
  await app.evaluate(({ BrowserWindow }, id) => {
    const win = BrowserWindow.fromId(id);
    win.restore();
    win.show();
    win.focus();
  }, mainId);
  await w.evaluate(() => window.pageNotes.setLanguage('en-US'));

  await check(
    'macOS app and window menus expose standard native roles',
    async () => {
      const menus = await app.evaluate(({ Menu }) =>
        Menu.getApplicationMenu().items.map((item) => ({
          role: item.role,
          label: item.label,
          children: item.submenu?.items.map((child) => ({
            role: child.role,
            id: child.id,
            accelerator: child.accelerator,
          })),
        })),
      );
      assert.equal(menus[0].role, 'appmenu');
      for (const role of [
        'about',
        'services',
        'hide',
        'hideothers',
        'unhide',
        'quit',
      ])
        assert.ok(
          menus[0].children.some((item) => item.role === role),
          'missing app role: ' + role,
        );
      const windowMenu = menus.find((item) => item.role === 'windowmenu');
      assert.ok(windowMenu, 'Window menu exists');
      for (const role of ['minimize', 'zoom', 'front'])
        assert.ok(
          windowMenu.children.some((item) => item.role === role),
          'missing window role: ' + role,
        );
      const edit = menus.find((item) => item.role === 'editmenu');
      assert.ok(edit.children.some((item) => item.role === 'selectall'));
      for (const id of ['undo', 'redo', 'cut', 'copy', 'paste'])
        assert.ok(
          edit.children.some(
            (item) => item.id === 'edit-' + id && item.accelerator,
          ),
          'missing edit command: ' + id,
        );
    },
  );

  async function menuCommand(command) {
    await app.evaluate(
      ({ Menu, BrowserWindow }, { command, id }) => {
        const item = Menu.getApplicationMenu().getMenuItemById(
          'edit-' + command,
        );
        assert(item, 'Missing edit menu command: ' + command);
        item.click(
          {},
          BrowserWindow.fromId(id),
          BrowserWindow.fromId(id).webContents,
        );
        function assert(value, message) {
          if (!value) throw Error(message);
        }
      },
      { command, id: mainId },
    );
  }
  async function drawAndSelect() {
    await w.locator('#aiSharedTools [data-ai-pointer=pen]').click();
    await w.evaluate(() =>
      document.querySelector('[data-ink-tool=rect]').click(),
    );
    const layer = w.locator('.document-sheet[data-page="1"] > .ink-layer');
    const bounds = await layer.boundingBox();
    await w.mouse.move(bounds.x + 100, bounds.y + 100);
    await w.mouse.down();
    await w.mouse.move(bounds.x + 220, bounds.y + 160, { steps: 6 });
    await w.mouse.up();
    await w.evaluate(() =>
      document.querySelector('[data-ink-tool=select]').click(),
    );
    const shape = await layer.locator('[data-item="0"]').boundingBox();
    await w.mouse.click(shape.x + shape.width / 2, shape.y + 1);
    return layer;
  }
  const annotationCount = () =>
    w.evaluate(
      () => window.pageNotes.getAnnotations().items.document[1]?.length || 0,
    );
  await drawAndSelect();

  await check('menu undo and redo preserve annotation history', async () => {
    // Selecting an object creates a move checkpoint; a second undo reaches its creation.
    await menuCommand('undo');
    await menuCommand('undo');
    await w.waitForFunction(
      () => !window.pageNotes.getAnnotations().items.document[1]?.length,
    );
    await menuCommand('redo');
    await w.waitForFunction(
      () => window.pageNotes.getAnnotations().items.document[1]?.length === 1,
    );
  });
  await check('Cmd+Z and Cmd+Shift+Z preserve annotation history', async () => {
    await w.keyboard.press('Meta+z');
    assert.equal(await annotationCount(), 0);
    await w.keyboard.press('Meta+Shift+z');
    assert.equal(await annotationCount(), 1);
  });
  await check(
    'Mac Backspace deletes selected annotation and leaves editable text alone',
    async () => {
      const object = w.locator(
        '.document-sheet[data-page="1"] > .ink-layer [data-item="0"]',
      );
      const shape = await object.boundingBox();
      await w.mouse.click(shape.x + shape.width / 2, shape.y + 1);
      await w.keyboard.press('Backspace');
      assert.equal(
        await annotationCount(),
        0,
        'Backspace deletes a selected annotation',
      );
      await menuCommand('undo');
      await w.waitForFunction(
        () => window.pageNotes.getAnnotations().items.document[1]?.length === 1,
      );
      await w.mouse.click(shape.x + shape.width / 2, shape.y + 1);
      await w.evaluate(() => {
        const field = document.createElement('div');
        field.id = 'native-editable';
        field.contentEditable = 'true';
        field.innerHTML = '<span>Editable text</span>';
        document.body.append(field);
      });
      const editable = w.locator('#native-editable');
      const typing = await editable.locator('span').evaluate((element) => {
        element.parentElement.focus();
        const event = new KeyboardEvent('keydown', {
          key: 'Backspace',
          bubbles: true,
          cancelable: true,
        });
        element.dispatchEvent(event);
        return event.defaultPrevented;
      });
      assert.equal(
        typing,
        false,
        'contenteditable Backspace keeps the native text behavior',
      );
      assert.equal(await annotationCount(), 1);
      const undoTyping = await editable.evaluate((element) => {
        const event = new KeyboardEvent('keydown', {
          key: 'z',
          metaKey: true,
          bubbles: true,
          cancelable: true,
        });
        element.dispatchEvent(event);
        return event.defaultPrevented;
      });
      assert.equal(
        undoTyping,
        false,
        'contenteditable Cmd+Z does not undo annotations',
      );
      await editable.evaluate((element) => element.remove());
      await w.mouse.click(shape.x + shape.width / 2, shape.y + 1);
      await w.keyboard.press('Backspace');
      assert.equal(await annotationCount(), 0);
    },
  );
  await w.locator('#aiSharedTools [data-ai-pointer=mouse]').click();

  await check(
    'edit menu falls back to native editing inside an input',
    async () => {
      await w.evaluate(() => {
        const field = document.createElement('input');
        field.id = 'native-input';
        document.body.append(field);
        field.focus();
      });
      await w.keyboard.insertText('native text');
      await menuCommand('undo');
      await w.waitForFunction(
        () => document.getElementById('native-input').value === '',
      );
      await menuCommand('redo');
      await w.waitForFunction(
        () => document.getElementById('native-input').value === 'native text',
      );
      await w.locator('#native-input').evaluate((element) => element.remove());
    },
  );
  await check(
    'closing a hidden screenshot window does not hide the main window',
    async () => {
      const result = await app.evaluate(async ({ BrowserWindow }, id) => {
        const inspector = new BrowserWindow({ show: false });
        const closed = new Promise((resolve) =>
          inspector.once('closed', resolve),
        );
        inspector.close();
        await closed;
        return {
          closed: inspector.isDestroyed(),
          visible: BrowserWindow.fromId(id).isVisible(),
        };
      }, mainId);
      assert.deepEqual(result, { closed: true, visible: true });
    },
  );
  await check(
    'Mac close saves pending notes and hides; Dock activate reuses the window',
    async () => {
      await w.evaluate(() => window.pageNotes.menuAction('chooseEditor'));
      await w.locator('.note-pane .pane-heading').click();
      await w.locator('#preview').click();
      await w.keyboard.press('Meta+a');
      await w.keyboard.insertText('Native close saves the pending note');
      await menuCommand('undo');
      await w.waitForFunction(
        () =>
          !document
            .getElementById('preview')
            .textContent.includes('Native close saves the pending note'),
      );
      await menuCommand('redo');
      await w.waitForFunction(() =>
        document
          .getElementById('preview')
          .textContent.includes('Native close saves the pending note'),
      );
      const id = (await w.evaluate(() => window.pageNotes.getState())).document
        .id;
      const notesURL = new URL('/api/documents/' + id + '/notes', w.url()).href;
      await app.evaluate(
        ({ BrowserWindow }, id) => BrowserWindow.fromId(id).close(),
        mainId,
      );
      await assertEventually(
        async () => (await mainState())?.visible === false,
        'close hides the main window',
      );
      const notes = await (await fetch(notesURL)).json();
      assert.ok(notes[1].includes('Native close saves the pending note'));
      await app.evaluate(({ app }) => app.emit('activate'));
      await assertEventually(
        async () => (await mainState())?.visible === true,
        'Dock activation shows the main window',
      );
      assert.equal((await mainState()).id, mainId);
      assert.equal((await app.windows()).length, 1);
    },
  );
  await w.locator('#preview').click();
  await w.keyboard.press('Meta+a');
  await w.keyboard.insertText('Explicit quit saves the pending note');
  quitExpected = true;
} finally {
  // Playwright closes via app.quit(); this also verifies explicit quit is not trapped by close-to-hide.
  let quitTimer;
  try {
    await Promise.race([
      app.close(),
      new Promise((_, reject) => {
        quitTimer = setTimeout(() => {
          app.process().kill('SIGKILL');
          reject(Error('Explicit quit did not exit'));
        }, 10000);
      }),
    ]);
  } finally {
    clearTimeout(quitTimer);
  }
  if (quitExpected) {
    const saved = await fs.readFile(
      path.join(path.dirname(second), 'src/right.md'),
      'utf8',
    );
    assert.ok(
      saved.includes('Explicit quit saves the pending note'),
      'explicit quit saves pending notes before exiting',
    );
  }
  await fs.rm(profile, { recursive: true, force: true });
}
if (failures.length)
  throw new AggregateError(failures, 'macOS native regressions');
console.log('PASS explicit quit exits after flushing');

async function assertEventually(predicate, message) {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (await predicate()) return;
    await new Promise((resolve) => setTimeout(resolve, 50));
  }
  assert.fail(message);
}

const {
  app,
  BrowserWindow,
  Menu,
  dialog,
  shell,
  clipboard,
  nativeTheme,
  ipcMain,
  safeStorage,
} = require('electron');
const path = require('node:path');
const fs = require('node:fs/promises');
const { pathToFileURL } = require('node:url');
const { randomBytes } = require('node:crypto');
const { execFileSync } = require('node:child_process');
const aiSettingsStore = require('./ai-settings.cjs');
const promptStoreModule = require('./prompts.cjs');
const {
  projectArgument,
  createProjectLauncher,
} = require('./project-launch.cjs');
let server,
  win,
  controller,
  word,
  macSettings,
  connectionFile,
  closing = false,
  quitting = false,
  finishWindowClose;
const isMac = process.platform === 'darwin';
function showMainWindow() {
  if (!win || win.isDestroyed()) return;
  if (win.isMinimized()) win.restore();
  win.show();
  win.focus();
}
const isBeta = /-beta\./.test(app.getVersion());
app.setName(isBeta ? 'pagenote Beta' : 'pagenote');
app.setAppUserModelId(
  isBeta ? 'io.pagenote.desktop.beta' : 'io.pagenote.desktop',
);
if (process.env.PAGE_NOTES_USER_DATA)
  app.setPath('userData', process.env.PAGE_NOTES_USER_DATA);
else if (isBeta)
  app.setPath('userData', path.join(app.getPath('appData'), 'pagenote-beta'));
const projectLauncher = createProjectLauncher({
  open: async (file) => {
    showMainWindow();
    await win.webContents.executeJavaScript(
      'window.pageNotes.ready.then(()=>window.pageNotes.openProject(' +
        JSON.stringify(file) +
        '))',
    );
    showMainWindow();
  },
  onError: (error, file) =>
    dialog.showErrorBox(
      'pagenote · Open project / 打开项目失败',
      file + '\n\n' + (error.message || String(error)),
    ),
});
// Finder can deliver a file before whenReady() and before a window exists.
app.on('open-file', (event, file) => {
  event.preventDefault();
  projectLauncher.enqueue(file);
  showMainWindow();
});
projectLauncher.enqueue(projectArgument(process.argv));
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', (_event, argv, workingDirectory) => {
    projectLauncher.enqueue(projectArgument(argv, workingDirectory));
    showMainWindow();
  });
  app.on('activate', showMainWindow);
  app
    .whenReady()
    .then(async () => {
      if (!process.env.PAGE_NOTES_USER_DATA) {
        const legacy = path.join(app.getPath('appData'), '页伴'),
          target = app.getPath('userData');
        if (!isBeta)
          await require('./migration.cjs').migrateLegacyData(target, legacy);
      }
      const data = path.join(app.getPath('userData'), 'documents-data');
      process.env.PAGE_NOTES_DATA = data;
      await fs.mkdir(app.getPath('userData'), { recursive: true });
      const keyFile = path.join(app.getPath('userData'), 'ai-key.txt');
      let token;
      try {
        token = await fs.readFile(keyFile, 'utf8');
      } catch {
        token = randomBytes(32).toString('hex');
        await fs.writeFile(keyFile, token, { mode: 0o600 });
      }
      process.env.PAGE_NOTES_AI_TOKEN = token;
      if (app.isPackaged)
        process.env.PAGE_NOTES_CONVERTER = path.join(
          process.resourcesPath,
          'convert-office.ps1',
        );
      if (!app.isPackaged) {
        try {
          await fs.access(data);
        } catch {
          await fs
            .cp(path.join(app.getAppPath(), 'data'), data, { recursive: true })
            .catch(() => {});
        }
      }
      if (app.isPackaged && isMac) {
        process.env.PAGE_NOTES_MAC_OFFICE_SCRIPT = path.join(
          process.resourcesPath,
          'mac-office.applescript',
        );
        process.env.PAGE_NOTES_MAC_POWERPOINT_SCRIPT = path.join(
          process.resourcesPath,
          'mac-powerpoint.applescript',
        );
      }
      const backend = await import(
        pathToFileURL(path.join(app.getAppPath(), 'server.mjs')).href
      );
      const rainModule = require('./yuketang.cjs');
      const rainSessionFile = path.join(
        app.getPath('userData'),
        'rain-session.bin',
      );
      backend.configureRainClassroomService(
        rainModule.createRainClassroomService({
          secureStore: {
            async get() {
              try {
                if (!safeStorage.isEncryptionAvailable())
                  throw Error('安全存储不可用');
                return safeStorage.decryptString(
                  await fs.readFile(rainSessionFile),
                );
              } catch (error) {
                if (error.code === 'ENOENT') return null;
                throw error;
              }
            },
            async set(value) {
              if (!safeStorage.isEncryptionAvailable())
                throw Error('系统安全存储不可用，无法保存雨课堂登录状态');
              const temp = rainSessionFile + '.tmp';
              await fs.writeFile(temp, safeStorage.encryptString(value), {
                mode: 0o600,
              });
              await fs.rename(temp, rainSessionFile);
            },
            async clear() {
              await fs.unlink(rainSessionFile).catch((error) => {
                if (error.code !== 'ENOENT') throw error;
              });
            },
          },
        }),
      );
      try {
        server = await backend.startServer(4174);
      } catch (e) {
        if (e.code !== 'EADDRINUSE') throw e;
        server = await backend.startServer(0);
      }
      const url = `http://127.0.0.1:${server.address().port}`;
      const initialPreferences = await backend
        .readPreferences()
        .catch(() => ({}));
      nativeTheme.themeSource = ['system', 'light', 'dark'].includes(
        initialPreferences.theme,
      )
        ? initialPreferences.theme
        : 'system';
      const mcpFolder = path.join(app.getPath('userData'), 'mcp');
      await fs.mkdir(mcpFolder, { recursive: true });
      const tutorFolder = path.join(
        app.getPath('userData'),
        'skills',
        'pagenote-page-tutor',
      );
      await fs.mkdir(tutorFolder, { recursive: true });
      const tutorSkillFile = path.join(tutorFolder, 'SKILL.md');
      await fs.copyFile(
        path.join(
          app.getAppPath(),
          'skills',
          'pagenote-page-tutor',
          'SKILL.md',
        ),
        tutorSkillFile,
      );
      // Copy explicit files: recursive fs.cp is not supported reliably inside ASAR.
      for (const item of promptStoreModule.modules) {
        const folder = path.join(
          app.getPath('userData'),
          'skills',
          item.folder,
        );
        await fs.mkdir(folder, { recursive: true });
        await fs.copyFile(
          path.join(app.getAppPath(), 'skills', item.folder, 'SKILL.md'),
          path.join(folder, 'SKILL.md'),
        );
      }
      const adapter = path.join(mcpFolder, 'page-notes-mcp.cjs');
      await fs.copyFile(
        app.isPackaged
          ? path.join(process.resourcesPath, 'page-notes-mcp.cjs')
          : path.join(app.getAppPath(), 'mcp-dist', 'page-notes-mcp.cjs'),
        adapter,
      );
      connectionFile = path.join(app.getPath('userData'), 'ai-connection.json');
      await fs.writeFile(
        connectionFile,
        JSON.stringify({ url, token, pid: process.pid }, null, 2),
        { mode: 0o600 },
      );
      let nodeCommand = 'node';
      if (process.platform === 'win32') {
        try {
          nodeCommand = execFileSync('where.exe', ['node.exe'], {
            encoding: 'utf8',
            windowsHide: true,
          })
            .trim()
            .split(/\r?\n/)[0];
        } catch {}
      }
      if (app.isPackaged) {
        const runtime = JSON.parse(
          await fs.readFile(
            path.join(process.resourcesPath, 'node-runtime.json'),
            'utf8',
          ),
        );
        // Older bundles recorded a bare "node" that was never the resource
        // name, so probe every name a runtime has shipped under.
        const candidates = [
          runtime.executable,
          process.platform === 'win32' ? 'mcp-node.exe' : 'mcp-node',
          process.platform === 'win32' ? 'node.exe' : 'node',
        ].filter(Boolean);
        let bundled;
        for (const candidate of candidates) {
          try {
            await fs.access(path.join(process.resourcesPath, candidate));
            bundled = candidate;
            break;
          } catch {}
        }
        if (!bundled)
          throw Error('打包内缺少 MCP 运行时：' + candidates.join(' / '));
        nodeCommand = path.join(
          mcpFolder,
          `node-${runtime.version}${isMac && runtime.architectures?.length > 1 ? '-universal' : ''}${path.extname(bundled)}`,
        );
        try {
          await fs.access(nodeCommand);
        } catch {
          await fs.copyFile(
            path.join(process.resourcesPath, bundled),
            nodeCommand,
          );
          // macOS strips the execute bit when copying out of resources.
          if (process.platform !== 'win32') await fs.chmod(nodeCommand, 0o755);
        }
        await fs.copyFile(
          path.join(process.resourcesPath, 'NODE-LICENSE.txt'),
          path.join(mcpFolder, 'NODE-LICENSE.txt'),
        );
      }
      const mcpConfig = {
        mcpServers: {
          page_notes: {
            command: nodeCommand,
            args: [adapter, '--connection-file', connectionFile],
          },
        },
      };
      const configFile = path.join(mcpFolder, 'mcp-config.json');
      await fs.writeFile(configFile, JSON.stringify(mcpConfig, null, 2));
      win = new BrowserWindow({
        title: 'pagenote',
        width: 1440,
        height: 960,
        minWidth: 900,
        minHeight: 620,
        backgroundColor: nativeTheme.shouldUseDarkColors
          ? '#17191d'
          : '#f5f8fc',
        show: false,
        webPreferences: {
          preload: path.join(__dirname, 'preload.cjs'),
          contextIsolation: true,
          nodeIntegration: false,
          sandbox: true,
          plugins: true,
        },
      });
      win.webContents.setVisualZoomLevelLimits(1, 1);
      // Reader gestures own pane zoom; never magnify the toolbar/window.
      win.webContents.setZoomMode?.('manual');
      nativeTheme.on('updated', () => {
        if (!win.isDestroyed())
          win.setBackgroundColor(
            nativeTheme.shouldUseDarkColors ? '#17191d' : '#f5f8fc',
          );
      });
      word = require('./word.cjs').createWordHost({
        win,
        data,
        helper: app.isPackaged
          ? path.join(process.resourcesPath, 'word-host.ps1')
          : path.join(app.getAppPath(), 'desktop/word-host.ps1'),
        converter: app.isPackaged
          ? path.join(process.resourcesPath, 'convert-office.ps1')
          : path.join(app.getAppPath(), 'convert-office.ps1'),
      });
      backend.setDesktopHandler(async (action, args) => {
        if (action === 'open-external') {
          const error = await shell.openPath(args.filePath);
          if (error) throw Error(error);
          return { opened: true };
        }
        return word.call(action, args);
      });
      win.on('focus', () =>
        win.webContents
          .executeJavaScript('window.pageNotes?.refreshExternalSource?.()')
          .catch(() => {}),
      );
      controller = await require('./ai.cjs').createAIController({
        win,
        url,
        data,
        word,
      });
      backend.setAIHandler((body) => controller.call(body));
      if (isMac) {
        const api = async (route, options) => {
          const response = await fetch(url + route, options);
          const result = await response.json();
          if (!response.ok)
            throw Error(result.error || 'Settings request failed');
          return result;
        };
        macSettings = require('./mac-settings.cjs').createMacSettings({
          executable: path.join(
            app.isPackaged
              ? process.resourcesPath
              : path.join(app.getAppPath(), 'build/mac-settings'),
            'pagenote Settings.app/Contents/MacOS/pagenote-settings',
          ),
          paneFile: path.join(app.getPath('userData'), 'settings-window.json'),
          readState: async () => ({
            ...(await win.webContents.executeJavaScript(
              'window.pageNotes.ready.then(()=>window.pageNotes.getSettingsState())',
            )),
            version: app.getVersion(),
          }),
          applySetting: (key, value) =>
            win.webContents.executeJavaScript(
              `window.pageNotes.applySetting(${JSON.stringify(key)},${JSON.stringify(value)})`,
            ),
          readPrompt: async (module) => {
            if (module !== 'system') return api('/api/ai-prompts/' + module);
            const result = await api('/api/ai-settings');
            return {
              text: result.systemPrompt,
              defaultText: result.defaultSystemPrompt,
            };
          },
          savePrompt: (module, text, expectedRevision) =>
            api(
              module === 'system'
                ? '/api/ai-settings'
                : '/api/ai-prompts/' + module,
              {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(
                  module === 'system'
                    ? { systemPrompt: text }
                    : { text, expectedRevision },
                ),
              },
            ),
          onQuit: () => app.quit(),
          onClosed: () => {
            if (!quitting && !closing) showMainWindow();
          },
          onError: (error) =>
            dialog.showErrorBox('pagenote · Settings', error.message),
        });
      }
      ipcMain.handle('pagenote:settings-open', (event, pane) => {
        if (event.sender !== win.webContents)
          throw Error('Invalid settings sender');
        return macSettings ? macSettings.open(pane) : { native: false };
      });
      ipcMain.on('pagenote:settings-sync', (event) => {
        if (event.sender === win?.webContents)
          macSettings?.sync().catch(() => {});
      });
      win.webContents.setWindowOpenHandler(({ url: target }) => {
        if (/^https?:|^mailto:/i.test(target)) shell.openExternal(target);
        return { action: 'deny' };
      });
      win.webContents.on('will-navigate', (event, target) => {
        if (new URL(target).origin !== url) {
          event.preventDefault();
          if (/^https?:/i.test(target)) shell.openExternal(target);
        }
      });
      let closePending;
      const completeClose = async () => {
        // A normal close can become Quit while note flushing is pending.
        // Consult native drafts at the last asynchronous boundary before exit.
        if (quitting && macSettings && !(await macSettings.prepareQuit())) {
          quitting = false;
          return;
        }
        if (quitting) {
          closing = true;
          app.quit();
        } else if (isMac) win.hide();
        else {
          closing = true;
          win.close();
        }
      };
      finishWindowClose = () => {
        if (closePending) return closePending;
        closePending = (async () => {
          await win.webContents.executeJavaScript(
            'window.pageNotes?.finishEditing?.()',
          );
          await completeClose();
        })()
          .catch(async () => {
            const result = await dialog.showMessageBox(win, {
              type: 'warning',
              buttons: [translate('继续编辑'), translate('关闭软件')],
              defaultId: 0,
              cancelId: 0,
              message: translate('笔记尚未保存成功'),
              detail: translate('建议继续编辑并重试保存。恢复副本仍会保留。'),
            });
            if (result.response === 1) await completeClose();
            else quitting = false;
          })
          .finally(() => {
            closePending = null;
          });
        return closePending;
      };
      win.on('close', (event) => {
        if (closing) return;
        event.preventDefault();
        finishWindowClose();
      });
      async function editCommand(command) {
        const target = BrowserWindow.getFocusedWindow() || win;
        if (!target || target.isDestroyed()) return;
        const contents = target.webContents;
        const handled =
          target === win &&
          (await contents
            .executeJavaScript(
              `(() => {
          const active = document.activeElement;
          const input = active?.closest('input,textarea');
          if (input || (active?.isContentEditable && window.pageNotes?.getAnnotations?.().active)) return false;
          return window.pageNotes?.editCommand?.(${JSON.stringify(command)}) || false;
        })()`,
            )
            .catch(() => false));
        if (!handled && !contents.isDestroyed()) contents[command]?.();
      }
      if (isMac)
        win.webContents.on('before-input-event', (event, input) => {
          if (
            input.type !== 'keyDown' ||
            !input.meta ||
            input.alt ||
            input.control
          )
            return;
          const key = input.key.toLowerCase();
          const command =
            key === 'z'
              ? input.shift
                ? 'redo'
                : 'undo'
              : !input.shift && { x: 'cut', c: 'copy', v: 'paste' }[key];
          if (!command) return;
          event.preventDefault();
          editCommand(command).catch(() => {});
        });
      const catalog = require('../locales/catalog.json');
      let locale = app.getLocale().startsWith('zh') ? 'zh-CN' : 'en-US';
      try {
        const saved = JSON.parse(
          await fs.readFile(path.join(data, 'preferences.json'), 'utf8'),
        );
        if (['zh-CN', 'en-US'].includes(saved.language))
          locale = saved.language;
      } catch {}
      const translate = (label) =>
        catalog[label]?.[locale === 'en-US' ? 1 : 0] || label;
      function localize(items) {
        return items.map((item) => ({
          ...item,
          label: item.label ? translate(item.label) : item.label,
          submenu: item.submenu ? localize(item.submenu) : undefined,
        }));
      }
      function buildMenu() {
        Menu.setApplicationMenu(
          Menu.buildFromTemplate(
            localize([
              ...(isMac
                ? [
                    {
                      role: 'appMenu',
                      label: app.name,
                      submenu: [
                        { role: 'about' },
                        { type: 'separator' },
                        {
                          label: '设置',
                          accelerator: 'CmdOrCtrl+,',
                          click: () =>
                            win.webContents.executeJavaScript(
                              'window.pageNotes.openSettings()',
                            ),
                        },
                        { type: 'separator' },
                        { role: 'services' },
                        { type: 'separator' },
                        { role: 'hide' },
                        { role: 'hideOthers' },
                        { role: 'unhide' },
                        { type: 'separator' },
                        { role: 'quit' },
                      ],
                    },
                  ]
                : []),
              {
                label: '文件',
                submenu: [
                  {
                    label: '雨课堂导入与实时采集…',
                    click: () =>
                      win.webContents.executeJavaScript(
                        'window.pageNotes.menuAction("rainClassroom")',
                      ),
                  },
                  { type: 'separator' },
                  {
                    label: '导入左侧',
                    click: () =>
                      win.webContents.executeJavaScript(
                        'document.getElementById("fileInput").click()',
                      ),
                  },
                  {
                    label: '导入右侧',
                    click: () =>
                      win.webContents.executeJavaScript(
                        'window.pageNotes.menuAction("importRight")',
                      ),
                  },
                  {
                    label: '新建项目',
                    accelerator: 'CmdOrCtrl+N',
                    click: () =>
                      win.webContents.executeJavaScript(
                        'window.pageNotes.menuAction("newProject")',
                      ),
                  },
                  {
                    label: '打开项目',
                    accelerator: 'CmdOrCtrl+O',
                    click: async () => {
                      const chosen = await dialog.showOpenDialog(win, {
                        title: translate('打开项目'),
                        properties: ['openFile'],
                        filters: [
                          {
                            name:
                              locale === 'en-US'
                                ? 'pagenote Project'
                                : 'pagenote 项目',
                            extensions: ['pagenote', 'json'],
                          },
                        ],
                      });
                      if (!chosen.canceled)
                        await win.webContents.executeJavaScript(
                          'window.pageNotes.openProject(' +
                            JSON.stringify(chosen.filePaths[0]) +
                            ')',
                        );
                    },
                  },
                  {
                    label: '保存',
                    accelerator: 'CmdOrCtrl+S',
                    click: () => {
                      if (word.active) word.call('save', {}).catch(() => {});
                      else
                        win.webContents
                          .executeJavaScript('window.pageNotes.flush()')
                          .catch(() => {});
                    },
                  },
                  {
                    label: '历史版本',
                    click: () =>
                      win.webContents.executeJavaScript(
                        'document.getElementById("historyButton").click()',
                      ),
                  },
                  { type: 'separator' },
                  {
                    label: '打开数据文件夹',
                    click: () => shell.openPath(data),
                  },
                  { type: 'separator' },
                  ...(isMac
                    ? [{ role: 'close' }]
                    : [{ role: 'quit', label: '退出' }]),
                ],
              },
              {
                label: '编辑',
                role: isMac ? 'editMenu' : undefined,
                submenu: [
                  ...(isMac
                    ? [
                        ...[
                          ['undo', '撤销', 'Undo', 'CmdOrCtrl+Z'],
                          ['redo', '重做', 'Redo', 'CmdOrCtrl+Shift+Z'],
                          ['cut', '剪切', 'Cut', 'CmdOrCtrl+X'],
                          ['copy', '复制', 'Copy', 'CmdOrCtrl+C'],
                          ['paste', '粘贴', 'Paste', 'CmdOrCtrl+V'],
                        ].map(([command, zh, en, accelerator]) => ({
                          id: 'edit-' + command,
                          label: locale === 'en-US' ? en : zh,
                          accelerator,
                          click: () => editCommand(command).catch(() => {}),
                        })),
                        { role: 'selectAll' },
                        { type: 'separator' },
                      ]
                    : []),
                  {
                    label: '文件编辑',
                    click: () =>
                      win.webContents.executeJavaScript(
                        'window.pageNotes.menuAction("chooseEditor")',
                      ),
                  },
                ],
              },
              {
                label: '查看',
                submenu: [
                  {
                    label: '适合窗口',
                    click: () =>
                      win.webContents.executeJavaScript(
                        'document.getElementById("fit").click()',
                      ),
                  },
                  {
                    label: '加宽笔记区域',
                    click: () =>
                      win.webContents
                        .executeJavaScript(
                          'window.pageNotes.setLayout({noteWidth:1000,padding:28,leftPercent:42})',
                        )
                        .catch(() => {}),
                  },
                  {
                    label: '笔记纸张',
                    submenu: [
                      {
                        label: '宽纸张（1000）',
                        click: () =>
                          win.webContents
                            .executeJavaScript(
                              'window.pageNotes.setLayout({noteWidth:1000})',
                            )
                            .catch(() => {}),
                      },
                      {
                        label: '紧凑纸张（760）',
                        click: () =>
                          win.webContents
                            .executeJavaScript(
                              'window.pageNotes.setLayout({noteWidth:760})',
                            )
                            .catch(() => {}),
                      },
                      {
                        label: '铺满右侧',
                        click: () =>
                          win.webContents
                            .executeJavaScript(
                              'window.pageNotes.setLayout({noteWidth:1400,padding:24})',
                            )
                            .catch(() => {}),
                      },
                    ],
                  },
                  {
                    label: '重置笔记字号',
                    click: () =>
                      win.webContents.executeJavaScript(
                        'window.pageNotes.resetNoteZoom()',
                      ),
                  },
                  { type: 'separator' },
                  { role: 'togglefullscreen', label: '全屏' },
                ],
              },
              {
                label: '修改意见',
                submenu: [
                  {
                    label: '左侧修改意见',
                    click: () =>
                      win.webContents.executeJavaScript(
                        'window.pageNotes.menuAction("reviewLeft")',
                      ),
                  },
                  {
                    label: '右侧修改意见',
                    click: () =>
                      win.webContents.executeJavaScript(
                        'window.pageNotes.menuAction("reviewRight")',
                      ),
                  },
                  { type: 'separator' },
                  {
                    label: '复制逐页精讲提示词',
                    click: async () =>
                      clipboard.writeText(
                        '# 默认系统提示词 / Default AI instructions\n\n' +
                          aiSettingsStore.readAISettings(
                            path.join(data, 'ai-settings.json'),
                          ).systemPrompt +
                          '\n\n' +
                          JSON.stringify(
                            promptStoreModule
                              .createPromptStore(
                                path.join(app.getPath('userData'), 'skills'),
                                data,
                              )
                              .index(),
                            null,
                            2,
                          ) +
                          '\n\n' +
                          promptStoreModule
                            .createPromptStore(
                              path.join(app.getPath('userData'), 'skills'),
                              data,
                            )
                            .read('page-tutor').text,
                      ),
                  },
                  {
                    label: '打开内置学习技能目录',
                    click: () => shell.openPath(path.dirname(tutorFolder)),
                  },
                  {
                    label: '复制 MCP 配置',
                    click: () =>
                      clipboard.writeText(JSON.stringify(mcpConfig, null, 2)),
                  },
                  {
                    label: '打开 MCP 配置目录',
                    click: () => shell.openPath(mcpFolder),
                  },
                  {
                    label: '接口能力',
                    click: () =>
                      dialog.showMessageBox(win, {
                        message: 'pagenote MCP',
                        detail:
                          locale === 'en-US'
                            ? 'Read source documents, notes and layout; edit notes; adjust paper width and styles; import files; capture screenshots and multi-page contact sheets.\n\nCopy the MCP configuration to connect your AI client. The required Node.js runtime is included.'
                            : '读取原文、笔记和布局；修改单页或批量笔记；调整纸宽、字体和样式；导入文件；获取截图和多页拼图。\n\n从「复制 MCP 配置」取得上层 AI 的连接设置。桌面版已附带 MCP 所需运行时，无需另外安装 Node.js。',
                      }),
                  },
                ],
              },
              {
                label: '设置',
                submenu: [
                  {
                    label: 'AI 设置',
                    click: () =>
                      win.webContents.executeJavaScript(
                        'window.pageNotes.openAISettings()',
                      ),
                  },
                  {
                    label: '语言 / Language',
                    submenu: [
                      {
                        label: '简体中文',
                        type: 'radio',
                        checked: locale === 'zh-CN',
                        click: () =>
                          win.webContents.executeJavaScript(
                            'window.pageNotes.setLanguage("zh-CN")',
                          ),
                      },
                      {
                        label: 'English',
                        type: 'radio',
                        checked: locale === 'en-US',
                        click: () =>
                          win.webContents.executeJavaScript(
                            'window.pageNotes.setLanguage("en-US")',
                          ),
                      },
                    ],
                  },
                  {
                    label: '设置',
                    click: () =>
                      win.webContents.executeJavaScript(
                        'window.pageNotes.openSettings()',
                      ),
                  },
                  {
                    label: '使用说明',
                    click: () =>
                      win.webContents.executeJavaScript(
                        'document.getElementById("help").showModal()',
                      ),
                  },
                ],
              },
              ...(isMac
                ? [
                    {
                      role: 'windowMenu',
                      submenu: [
                        { role: 'minimize' },
                        { role: 'zoom' },
                        { type: 'separator' },
                        { role: 'front' },
                      ],
                    },
                  ]
                : []),
              {
                label: '帮助',
                submenu: [
                  {
                    label: '欢迎页',
                    click: () =>
                      win.webContents.executeJavaScript(
                        'window.pageNotes?.showWelcome()',
                      ),
                  },
                  {
                    label: '使用说明',
                    click: () =>
                      win.webContents.executeJavaScript(
                        'document.getElementById("help").showModal()',
                      ),
                  },
                  {
                    label: '关于pagenote',
                    click: () =>
                      dialog.showMessageBox(win, {
                        message: 'pagenote ' + app.getVersion(),
                        detail:
                          locale === 'en-US'
                            ? 'Page-linked Markdown notes and AI annotations\nCtrl + mouse wheel: zoom the pane under the pointer\nDocuments and notes are stored locally.'
                            : '逐页 Markdown 笔记与 AI 标注\nCtrl + 鼠标滚轮：缩放鼠标所在区域\n文件和笔记保存在本机。',
                      }),
                  },
                ],
              },
            ]),
          ),
        );
      }
      buildMenu();
      backend.setPreferenceHandler(async (preferences) => {
        if (['zh-CN', 'en-US'].includes(preferences.language))
          locale = preferences.language;
        nativeTheme.themeSource = ['system', 'light', 'dark'].includes(
          preferences.theme,
        )
          ? preferences.theme
          : 'system';
        buildMenu();
      });
      await win.loadURL(url);
      win.show();
      await projectLauncher.ready();
      win.on('closed', () => {
        win = null;
        if (!isMac || quitting || closing) app.quit();
      });
    })
    .catch((e) => {
      dialog.showErrorBox('pagenote启动失败', e.stack || e.message);
      closing = true;
      app.quit();
    });
  app.on('before-quit', (event) => {
    if (closing || !win || win.isDestroyed() || !finishWindowClose) return;
    event.preventDefault();
    quitting = true;
    finishWindowClose();
  });
  app.on('will-quit', () => {
    macSettings?.dispose();
    controller?.dispose();
    server?.close();
  });
  app.on('window-all-closed', () => {
    if (!isMac) app.quit();
  });
}

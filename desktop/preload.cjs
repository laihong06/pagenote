const { contextBridge, ipcRenderer } = require('electron');
// Keep the renderer bridge narrow: it can open settings and notify a refresh,
// but cannot execute native code or access the filesystem.
contextBridge.exposeInMainWorld('desktopSettings', {
  open: (pane) => ipcRenderer.invoke('pagenote:settings-open', pane),
  sync: () => ipcRenderer.send('pagenote:settings-sync'),
  chooseRainImportDirectory: () =>
    ipcRenderer.invoke('pagenote:rain-import-directory'),
  chooseBookshelfDirectory: () =>
    ipcRenderer.invoke('pagenote:bookshelf-directory'),
});

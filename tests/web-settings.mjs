// Exercise the actual browser/Windows fallback controls without launching the
// macOS helper. Only the native-availability boundary is replaced.
export async function useWebSettings(app) {
  await app.evaluate(({ ipcMain }) => {
    ipcMain.removeHandler('pagenote:settings-open');
    ipcMain.handle('pagenote:settings-open', () => ({ native: false }));
  });
}

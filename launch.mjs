import { spawn, execFile } from 'node:child_process';
import path from 'node:path';
const url = 'http://127.0.0.1:4173';
async function ready() {
  try {
    const response = await fetch(url + '/api/documents', {
      signal: AbortSignal.timeout(1000),
    });
    return response.ok && Array.isArray(await response.json());
  } catch {
    return false;
  }
}
let child;
if (!(await ready())) {
  child = spawn(
    process.execPath,
    [path.join(import.meta.dirname, 'server.mjs')],
    { cwd: import.meta.dirname, stdio: 'inherit', windowsHide: true },
  );
  let stopped = false;
  child.on('exit', () => (stopped = true));
  for (let i = 0; i < 60 && !stopped; i++) {
    if (await ready()) break;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  if (!(await ready())) {
    console.error('启动失败，请检查 4173 端口是否被占用。');
    child.kill();
    process.exitCode = 1;
  }
}
if (await ready()) {
  console.log('pagenote 已就绪：' + url);
  console.log('关闭此窗口或按 Ctrl+C 可停止本次启动的服务。');
  if (!process.argv.includes('--no-browser')) {
    const command =
      process.platform === 'win32'
        ? 'rundll32.exe'
        : process.platform === 'darwin'
          ? 'open'
          : 'xdg-open';
    const args =
      process.platform === 'win32'
        ? ['url.dll,FileProtocolHandler', url]
        : [url];
    execFile(command, args, { windowsHide: true }, (error) => {
      if (error) console.error('请在浏览器打开：' + url);
    });
  }
}

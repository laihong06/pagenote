// Convert a private snapshot, using an isolated LibreOffice profile per job.
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { execFile } = require('node:child_process');
const { promisify } = require('node:util');
const run = promisify(execFile);
exports.convertOffice = async function (input, output) {
  const temp = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-office-'));
  try {
    await run(
      process.env.PAGE_NOTES_LIBREOFFICE || 'libreoffice',
      [
        '-env:UserInstallation=' +
          pathToFileURL(path.join(temp, 'profile')).href,
        '--headless',
        '--convert-to',
        'pdf',
        '--outdir',
        temp,
        path.resolve(input),
      ],
      { timeout: 120000, maxBuffer: 1024 * 1024 },
    );
    const pdf = path.join(temp, path.parse(input).name + '.pdf');
    await fs.access(pdf);
    await fs.copyFile(pdf, output);
  } catch (error) {
    if (error.code === 'ENOENT' && error.syscall?.startsWith('spawn'))
      throw Error(
        'Linux 文档转换需要 LibreOffice。请安装 libreoffice，或先导出 PDF 再导入。',
      );
    throw Error('LibreOffice 转换失败：' + error.message);
  } finally {
    await fs.rm(temp, { recursive: true, force: true });
  }
};

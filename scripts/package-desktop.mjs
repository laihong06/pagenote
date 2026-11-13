import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const cache =
  process.env.ELECTRON_BUILDER_CACHE || path.join(root, '.builder-cache');
await fs.mkdir(cache, { recursive: true });
const dir = process.argv.includes('--dir');
const platform = process.argv.includes('--linux')
  ? 'linux'
  : process.argv.includes('--win')
    ? 'win'
    : process.argv.includes('--mac')
      ? 'mac'
      : process.platform === 'linux'
        ? 'linux'
        : process.platform === 'darwin'
          ? 'mac'
          : 'win';
// Generate the platform-specific electron-builder config first so Mac keeps
// its AppleScript bridge, Darwin OCR natives and universal MCP runtime while
// Windows keeps its own helpers.
const config = spawn(
  process.execPath,
  [path.join(root, 'scripts/build-config.mjs'), platform],
  { cwd: root, stdio: 'inherit', windowsHide: true },
);
await new Promise((resolve, reject) => {
  config.on('error', reject);
  config.on('exit', (code) =>
    code === 0 ? resolve() : reject(Error('build-config exited with ' + code)),
  );
});
const args =
  platform === 'linux'
    ? [
        '--linux',
        ...(dir ? ['dir'] : ['AppImage', 'deb']),
        '--config',
        'electron-builder.json',
      ]
    : platform === 'mac'
      ? dir
        ? ['--mac', 'dir', '--arm64', '--config', 'electron-builder.json']
        : [
            '--mac',
            'dmg',
            'zip',
            '--arm64',
            '--config',
            'electron-builder.json',
          ]
      : dir
        ? ['--win', 'dir', '--x64', '--config', 'electron-builder.json']
        : [
            '--win',
            'nsis',
            'zip',
            '--x64',
            '--config',
            'electron-builder.json',
          ];

const child = spawn(
  process.execPath,
  [
    path.join(root, 'node_modules/electron-builder/cli.js'),
    ...args,
    ...(process.env.PAGENOTE_ELECTRON_DIST
      ? ['--config.electronDist=' + process.env.PAGENOTE_ELECTRON_DIST]
      : []),
    '--publish',
    'never',
  ],
  {
    cwd: root,
    stdio: 'inherit',
    windowsHide: true,
    env: { ...process.env, ELECTRON_BUILDER_CACHE: cache },
  },
);
child.on('error', (error) => {
  console.error(error.message);
  process.exitCode = 1;
});
child.on('exit', (code) => {
  process.exitCode = code ?? 1;
});

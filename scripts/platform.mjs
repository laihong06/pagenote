import path from 'node:path';
import { spawn, spawnSync } from 'node:child_process';
import { loadPlatform } from './platform-config.mjs';

const root = path.resolve(import.meta.dirname, '..');
const [id, action = 'info', ...rest] = process.argv.slice(2);
const config = await loadPlatform(root, id);
if (action === 'info') {
  console.log(JSON.stringify(config, null, 2));
} else {
  if (!['windows', 'macos'].includes(id))
    throw Error('Android: use android/gradlew or android/gradlew.bat');
  if (
    (id === 'windows' && process.platform !== 'win32') ||
    (id === 'macos' && process.platform !== 'darwin')
  )
    throw Error('Build and test this platform on its own operating system');
  const scripts = {
    check: ['scripts/check-platforms.mjs'],
    build: ['scripts/platform-build.mjs'],
    package: [
      'scripts/package-desktop.mjs',
      ...(id === 'macos' ? ['--mac'] : []),
      ...rest,
    ],
  };
  if (!scripts[action]) throw Error('Unknown platform action: ' + action);
  if (action === 'package') {
    const result = spawnSync(process.execPath, ['scripts/platform-build.mjs'], {
      cwd: root,
      stdio: 'inherit',
      windowsHide: true,
      env: { ...process.env, PAGENOTE_PLATFORM: id },
    });
    if (result.error) throw result.error;
    if (result.status !== 0) process.exit(result.status ?? 1);
  }
  const child = spawn(process.execPath, scripts[action], {
    cwd: root,
    stdio: 'inherit',
    windowsHide: true,
    env: { ...process.env, PAGENOTE_PLATFORM: id },
  });
  child.on('error', (error) => {
    console.error(error);
    process.exitCode = 1;
  });
  child.on('exit', (code) => {
    process.exitCode = code ?? 1;
  });
}

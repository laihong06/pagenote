import { spawnSync } from 'node:child_process';
for (const script of [
  'scripts/export-prompts.mjs',
  'scripts/create-icon.mjs',
  'scripts/notices.mjs',
  'scripts/build-mac-settings.mjs',
  'scripts/build-markdown-s.mjs',
  'node_modules/vite/bin/vite.js',
  'scripts/build-mcp.mjs',
]) {
  const result = spawnSync(
    process.execPath,
    [script, ...(script.includes('vite') ? ['build'] : [])],
    { stdio: 'inherit', windowsHide: true },
  );
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}

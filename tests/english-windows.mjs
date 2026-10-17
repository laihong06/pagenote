import { dismissWelcome } from './dismiss-welcome.mjs';
import { spawnSync } from 'node:child_process';
const result = spawnSync(process.execPath, ['tests/ai-settings.mjs'], {
  stdio: 'inherit',
  windowsHide: true,
  env: { ...process.env, TEST_LOCALE: 'en-US' },
});
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;

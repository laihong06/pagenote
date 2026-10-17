import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { checkReleaseVersion } from '../scripts/check-release-version.mjs';
const root = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-version-test-'));
const version = '2.0.0-beta.5';
try {
  await fs.writeFile(
    path.join(root, 'package.json'),
    JSON.stringify({ version }),
  );
  const lock = { version, packages: { '': { version } } };
  await fs.writeFile(
    path.join(root, 'package-lock.json'),
    JSON.stringify(lock),
  );
  for (const name of ['README.md', 'README.en.md'])
    await fs.writeFile(
      path.join(root, name),
      '[Download](https://github.com/example/app/releases/download/v' +
        version +
        '/app.zip)',
    );
  await fs.writeFile(
    path.join(root, 'CHANGELOG.md'),
    '## ' + version + ' — 2026-10-16\n',
  );
  await checkReleaseVersion(root, { release: true, tag: 'v' + version });
  const readmePath = path.join(root, 'README.md');
  const validReadme = await fs.readFile(readmePath, 'utf8');
  await fs.writeFile(
    readmePath,
    validReadme + '\n当前公开版本：2.0.0-beta.4。',
  );
  await assert.rejects(
    checkReleaseVersion(root, { release: true }),
    /public version text/,
  );
  await fs.writeFile(readmePath, validReadme);
  await assert.rejects(
    checkReleaseVersion(root, { release: true, tag: 'v2.0.0-beta.4' }),
    /tag/,
  );
  await fs.writeFile(
    path.join(root, 'README.md'),
    '[Download](https://github.com/example/app/releases/download/v2.0.0-beta.4/app.zip)',
  );
  await assert.rejects(checkReleaseVersion(root, { release: true }), /README/);
  await checkReleaseVersion(root);
  lock.packages[''].version = '2.0.0-beta.4';
  await fs.writeFile(
    path.join(root, 'package-lock.json'),
    JSON.stringify(lock),
  );
  await assert.rejects(checkReleaseVersion(root), /lock/);
  const buildConfig = path.resolve(
    import.meta.dirname,
    '../scripts/build-config.mjs',
  );
  for (const [source, expected] of [
    ['1.0.1-dev.5', '1.0.1-dev.5'],
    ['1.0.0', '1.0.0-beta.1'],
  ]) {
    await fs.writeFile(
      path.join(root, 'package.json'),
      JSON.stringify({ version: source, build: { files: [] } }),
    );
    await promisify(execFile)(process.execPath, [buildConfig, 'mac'], {
      cwd: root,
    });
    const config = JSON.parse(
      await fs.readFile(path.join(root, 'electron-builder.json'), 'utf8'),
    );
    assert.equal(
      config.extraMetadata.version,
      expected,
      'Mac packaging preserves development versions and keeps stable sources experimental',
    );
  }
  console.log('PASS Mac package development version and experimental channel');
  console.log(
    'PASS version, lockfile, release links and tag drift gates; unreleased source may differ from public downloads',
  );
} finally {
  const relative = path.relative(os.tmpdir(), root);
  assert.ok(
    relative.startsWith('pagenote-version-test-') &&
      !relative.includes(path.sep),
  );
  await fs.rm(root, { recursive: true, force: true });
}

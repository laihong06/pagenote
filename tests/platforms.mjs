import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import {
  loadPlatform,
  releaseTag,
  platformIds,
} from '../scripts/platform-config.mjs';

const root = path.resolve(import.meta.dirname, '..');
const temp = await fs.mkdtemp(
  path.join(os.tmpdir(), 'pagenote-platform-test-'),
);
try {
  const pkg = JSON.parse(await fs.readFile(path.join(root, 'package.json')));
  await fs.writeFile(path.join(temp, 'package.json'), JSON.stringify(pkg));
  await fs.cp(path.join(root, 'platforms'), path.join(temp, 'platforms'), {
    recursive: true,
  });
  const macBefore = await loadPlatform(temp, 'macos');
  const win = await loadPlatform(temp, 'windows');
  win.developmentVersion = '9.0.0-beta.1-dev.1';
  await fs.writeFile(
    path.join(temp, 'platforms/windows/version.json'),
    JSON.stringify(win),
  );
  for (const [id, arg] of [
    ['windows', 'win'],
    ['macos', 'mac'],
  ]) {
    execFileSync(
      process.execPath,
      [path.join(root, 'scripts/build-config.mjs'), arg],
      { cwd: temp },
    );
    const build = JSON.parse(
      await fs.readFile(path.join(temp, 'electron-builder.json')),
    );
    assert.equal(
      build.extraMetadata.version,
      (await loadPlatform(temp, id)).developmentVersion,
    );
    assert.equal(build.extraMetadata.main, `apps/desktop/${id}/main.cjs`);
    assert.equal(build.appId, 'io.pagenote.desktop.beta');
    assert.equal(build.fileAssociations, undefined);
    assert.ok(
      build.files.includes('desktop/**'),
      'preserve all shared desktop features',
    );
    assert.ok(build.files.includes('apps/desktop/**'));
    if (id === 'macos') {
      assert.equal(build.executableName, undefined);
      assert.equal(build.mac.extendInfo.CFBundleDocumentTypes, undefined);
    }
  }
  assert.deepEqual(
    await loadPlatform(temp, 'macos'),
    macBefore,
    'Windows changes do not bump Mac',
  );
  for (const id of platformIds)
    assert.equal(releaseTag(id, '1.2.0-beta.1'), `${id}/v1.2.0-beta.1`);
  assert.throws(
    () => releaseTag('windows', '1.2.0-beta.1-dev.1'),
    /non-development/,
  );
  await assert.rejects(loadPlatform(root, 'harmonyos'), /Unsupported/);
  const android = await loadPlatform(root, 'android');
  assert.ok(
    android.versionCode > 5,
    'do not reuse published Android beta.5 contents',
  );
  console.log(
    'PASS independent platform versions, packaging entries, beta isolation and unchanged shared feature inclusion',
  );
} finally {
  await fs.rm(temp, { recursive: true, force: true });
}

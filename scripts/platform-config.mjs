import fs from 'node:fs/promises';
import path from 'node:path';

export const platformIds = ['windows', 'macos', 'android'];

export async function loadPlatform(root, id) {
  if (!platformIds.includes(id)) throw Error('Unsupported platform: ' + id);
  const file = path.join(root, 'platforms', id, 'version.json');
  const value = JSON.parse(await fs.readFile(file, 'utf8'));
  if (value.platform !== id) throw Error('Platform metadata mismatch: ' + file);
  if (!/^\d+\.\d+\.\d+-beta\.\d+(?:-dev\.\d+)?$/.test(value.developmentVersion))
    throw Error('Development builds must retain beta identity: ' + id);
  for (const channel of ['stable', 'beta']) {
    const release = value.published[channel];
    if (release && (!release.version || !release.tag || !release.url))
      throw Error('Incomplete published release metadata: ' + id);
  }
  return value;
}

export function releaseTag(id, version) {
  if (!platformIds.includes(id)) throw Error('Unsupported platform: ' + id);
  if (!/^\d+\.\d+\.\d+(?:-beta\.\d+)?$/.test(version))
    throw Error('A public release needs a non-development version');
  return `${id}/v${version}`;
}

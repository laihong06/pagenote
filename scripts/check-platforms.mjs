import fs from 'node:fs/promises';
import path from 'node:path';
import { loadPlatform, platformIds } from './platform-config.mjs';
const root = path.resolve(import.meta.dirname, '..');
let links = 0;
for (const id of platformIds) {
  await loadPlatform(root, id);
  for (const name of [
    'README.md',
    'features.md',
    'guide/README.md',
    'CHANGELOG.md',
    'releases/README.md',
  ]) {
    const file = path.join(root, 'docs', id, name);
    const text = await fs.readFile(file, 'utf8');
    for (const match of text.matchAll(/\]\(([^)]+)\)/g)) {
      const url = match[1];
      if (/^(https?:|#)/.test(url)) continue;
      const destination = path.resolve(path.dirname(file), url.split('#')[0]);
      await fs.access(destination);
      links++;
    }
  }
}
for (const id of ['windows', 'macos'])
  await fs.access(path.join(root, 'apps/desktop', id, 'main.cjs'));
console.log(
  `PASS platform manifests, application entries and ${links} documentation links`,
);

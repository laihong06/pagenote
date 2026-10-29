import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { loadPlatform } from './platform-config.mjs';
let { version } = JSON.parse(
  await fs.readFile(new URL('../package.json', import.meta.url), 'utf8'),
);
if (process.env.PAGENOTE_PLATFORM)
  version = (await loadPlatform(process.cwd(), process.env.PAGENOTE_PLATFORM))
    .developmentVersion;
const directory = path.resolve(
  process.argv[2] ||
    (version.includes('-beta.') ? 'release/beta-' + version : 'release'),
);
const files = (await fs.readdir(directory)).filter((name) =>
  /^pagenote-.*\.(exe|zip|dmg)$/.test(name),
);
if (!files.length) throw Error('No release artifacts found.');
if (files.some((name) => !name.startsWith('pagenote-' + version + '-')))
  throw Error(
    'Mixed release versions; use a dedicated artifact directory for ' + version,
  );
const lines = [];
for (const name of files) {
  const hash = createHash('sha256')
    .update(await fs.readFile(path.join(directory, name)))
    .digest('hex');
  lines.push(`${hash}  ${name}`);
}
await fs.writeFile(
  path.join(directory, 'SHA256SUMS.txt'),
  lines.join('\n') + '\n',
);
console.log(`Wrote checksums for ${files.length} artifacts.`);

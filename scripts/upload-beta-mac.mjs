import fs from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import { createHash } from 'node:crypto';
import path from 'node:path';

const repository = process.env.GITHUB_REPOSITORY;
const tag = process.env.RELEASE_TAG;
if (!/^v\d+\.\d+\.\d+-beta\.\d+$/.test(tag || ''))
  throw Error('Only an existing beta tag is allowed');
const headers = {
  Authorization: 'Bearer ' + process.env.GH_TOKEN,
  Accept: 'application/vnd.github+json',
  'User-Agent': 'pagenote-mac-artifacts',
};
async function api(route) {
  const r = await fetch(`https://api.github.com/repos/${repository}${route}`, {
    headers,
  });
  if (!r.ok) throw Error('GitHub request failed: ' + r.status);
  return r.json();
}
const run = await api('/actions/runs/' + process.env.VALIDATION_RUN);
if (run.status !== 'completed' || run.conclusion !== 'success')
  throw Error('Mac validation must have completed successfully');
let ref = await api('/git/ref/tags/' + tag);
if (ref.object.type === 'tag')
  ref = { object: (await api('/git/tags/' + ref.object.sha)).object };
if (ref.object.sha !== run.head_sha)
  throw Error('Mac validation and release tag must name the same commit');
const release = (await api('/releases?per_page=100')).find(
  (item) => item.tag_name === tag,
);
if (!release) throw Error('No matching draft release');
if (!release.draft || !release.prerelease)
  throw Error('Never change a published release or a stable release');
async function files(directory) {
  const result = [];
  for (const entry of await fs.readdir(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) result.push(...(await files(file)));
    else result.push(file);
  }
  return result;
}
const available = await files('mac-artifacts');
const manifestPath = available.find(
  (f) => path.basename(f) === 'SHA256SUMS.txt',
);
if (!manifestPath) throw Error('Missing CI checksum manifest');
const manifest = await fs.readFile(manifestPath, 'utf8');
const version = tag.slice(1);
const receipts = [];
for (const ext of ['dmg', 'zip']) {
  const name = `pagenote-${version}-mac-arm64.${ext}`;
  const file = available.find((f) => path.basename(f) === name);
  if (!file) throw Error('Missing Mac package: ' + name);
  const hash = createHash('sha256')
    .update(await fs.readFile(file))
    .digest('hex');
  if (!manifest.split(/\r?\n/).includes(hash + '  ' + name))
    throw Error('CI manifest differs: ' + name);
  const existing = release.assets.find((a) => a.name === name);
  if (existing) {
    if (existing.digest !== 'sha256:' + hash)
      throw Error('Existing draft asset differs: ' + name);
  } else {
    const stat = await fs.stat(file);
    const r = await fetch(
      release.upload_url.replace(/\{.*$/, '') +
        '?name=' +
        encodeURIComponent(name),
      {
        method: 'POST',
        headers: {
          ...headers,
          'Content-Type': 'application/octet-stream',
          'Content-Length': String(stat.size),
        },
        body: createReadStream(file),
        duplex: 'half',
        signal: AbortSignal.timeout(600000),
      },
    );
    const asset = await r.json();
    if (
      !r.ok ||
      asset.state !== 'uploaded' ||
      asset.digest !== 'sha256:' + hash
    )
      throw Error('Mac upload verification failed: ' + name);
  }
  receipts.push(hash + '  ' + name);
}
console.log(
  JSON.stringify({ tag, commit: run.head_sha, macChecksums: receipts }),
);
await fs.appendFile(
  process.env.GITHUB_STEP_SUMMARY,
  'Validated Mac packages uploaded to draft ' +
    tag +
    '\n\n' +
    receipts.join('\n'),
);

import fs from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { checkReleaseVersion } from './check-release-version.mjs';

const root = process.cwd(),
  mode = process.argv[2] || 'status';
if (!['status', 'draft', 'publish'].includes(mode))
  throw Error('Use status, draft or publish');
function git(args, input) {
  const r = spawnSync('git', args, {
    cwd: root,
    input,
    encoding: 'utf8',
    windowsHide: true,
    timeout: 15000,
    env: { ...process.env, GIT_TERMINAL_PROMPT: '0' },
  });
  if (r.status !== 0) throw Error('Git operation failed: ' + args[0]);
  return r.stdout.trim();
}
const origin = git(['remote', 'get-url', 'origin']);
const match = origin.match(/github\.com[:/]([^/]+\/[^/]+?)(?:\.git)?$/);
if (!match) throw Error('Expected a GitHub origin');
const repository = match[1],
  route = '/repos/' + repository;
let token = process.env.GH_TOKEN || process.env.GITHUB_TOKEN;
if (!token) {
  const lines = git(
    ['-c', 'credential.interactive=false', 'credential', 'fill'],
    'protocol=https\nhost=github.com\n\n',
  ).split('\n');
  token = lines.find((l) => l.startsWith('password='))?.slice(9);
}
if (!token) throw Error('GitHub authentication unavailable');
const headers = {
  Authorization: 'Bearer ' + token,
  Accept: 'application/vnd.github+json',
  'X-GitHub-Api-Version': '2022-11-28',
  'User-Agent': 'pagenote-release',
};
async function api(endpoint, method = 'GET', body, allow404 = false) {
  const r = await fetch('https://api.github.com' + endpoint, {
    method,
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(45000),
  });
  if (allow404 && r.status === 404) return null;
  const v = await r.json();
  if (!r.ok) throw Error('GitHub ' + r.status + ': ' + v.message);
  return v;
}
if (mode === 'status') {
  const releases = await api(route + '/releases?per_page=10');
  console.log(
    JSON.stringify(
      releases.map((r) => ({
        tag: r.tag_name,
        draft: r.draft,
        prerelease: r.prerelease,
        url: r.html_url,
        assets: r.assets.map((a) => ({
          name: a.name,
          size: a.size,
          digest: a.digest,
        })),
      })),
      null,
      2,
    ),
  );
} else {
  const { version, tag } = await checkReleaseVersion(root, { release: true });
  if (git(['status', '--porcelain']))
    throw Error('Commit release changes before publishing');
  const commit = git(['rev-parse', 'HEAD']);
  let ref = await api(route + '/git/ref/tags/' + tag);
  if (ref.object.type === 'tag')
    ref = { object: (await api(route + '/git/tags/' + ref.object.sha)).object };
  if (ref.object.sha !== commit)
    throw Error('Remote tag must point to the exact local release commit');
  const directory = path.resolve(
    process.argv[3] || path.join('release', 'publish-' + version),
  );
  const names = [
    `pagenote-${version}-windows-x64-setup.exe`,
    `pagenote-${version}-windows-x64.zip`,
    `pagenote-${version}-source.zip`,
    ...(process.argv.includes('--with-mac')
      ? [
          `pagenote-${version}-mac-arm64.dmg`,
          `pagenote-${version}-mac-arm64.zip`,
        ]
      : []),
    'SHA256SUMS.txt',
  ];
  const hashes = {};
  for (const name of names)
    hashes[name] = createHash('sha256')
      .update(await fs.readFile(path.join(directory, name)))
      .digest('hex');
  const sums = await fs.readFile(
    path.join(directory, 'SHA256SUMS.txt'),
    'utf8',
  );
  for (const name of names.filter((name) => name !== 'SHA256SUMS.txt'))
    if (!sums.split(/\r?\n/).includes(hashes[name] + '  ' + name))
      throw Error('Checksum manifest differs: ' + name);
  const body = await fs.readFile(
    path.join(root, 'docs', 'RELEASE-NOTES-' + version + '.md'),
    'utf8',
  );
  let release =
    (await api(route + '/releases/tags/' + tag, 'GET', undefined, true)) ||
    (await api(route + '/releases?per_page=100')).find(
      (r) => r.tag_name === tag,
    );
  if (!release)
    release = await api(route + '/releases', 'POST', {
      tag_name: tag,
      target_commitish: commit,
      name: 'pagenote ' + version,
      body,
      draft: true,
      prerelease: version.includes('-'),
    });
  for (const name of names) {
    const existing = release.assets.find((a) => a.name === name),
      file = path.join(directory, name),
      stat = await fs.stat(file);
    if (existing) {
      if (
        existing.size !== stat.size ||
        (existing.digest && existing.digest !== 'sha256:' + hashes[name])
      )
        throw Error(
          'Existing asset has different contents; do not reuse published versions: ' +
            name,
        );
      continue;
    }
    if (!release.draft)
      throw Error(
        'Do not add missing assets to a published release; create a new version',
      );
    console.log('Uploading ' + name + ' (' + stat.size + ' bytes)');
    const url =
      release.upload_url.replace(/\{.*$/, '') +
      '?name=' +
      encodeURIComponent(name);
    const r = await fetch(url, {
      method: 'POST',
      headers: {
        ...headers,
        'Content-Type': name.endsWith('.zip')
          ? 'application/zip'
          : 'application/octet-stream',
        'Content-Length': String(stat.size),
      },
      body: createReadStream(file),
      duplex: 'half',
      signal: AbortSignal.timeout(900000),
    });
    const asset = await r.json();
    if (
      !r.ok ||
      asset.state !== 'uploaded' ||
      asset.size !== stat.size ||
      (asset.digest && asset.digest !== 'sha256:' + hashes[name])
    )
      throw Error(
        'Asset upload verification failed: ' + name + ' HTTP ' + r.status,
      );
  }
  release = await api(route + '/releases/' + release.id);
  for (const name of names) {
    const a = release.assets.find((v) => v.name === name);
    if (
      !a ||
      a.state !== 'uploaded' ||
      (a.digest && a.digest !== 'sha256:' + hashes[name])
    )
      throw Error('Release asset verification failed: ' + name);
  }
  if (mode === 'publish')
    release = await api(route + '/releases/' + release.id, 'PATCH', {
      draft: false,
      prerelease: version.includes('-'),
      body,
      make_latest: version.includes('-') ? 'false' : 'true',
    });
  const receipt = {
    version,
    commit,
    tag,
    url: release.html_url,
    draft: release.draft,
    prerelease: release.prerelease,
    assets: release.assets.map((a) => ({
      name: a.name,
      size: a.size,
      digest: a.digest,
      url: a.browser_download_url,
    })),
  };
  await fs.writeFile(
    path.join(directory, 'publication-receipt.json'),
    JSON.stringify(receipt, null, 2),
  );
  console.log(JSON.stringify(receipt, null, 2));
}

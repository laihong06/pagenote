import fs from 'node:fs/promises';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import sharp from 'sharp';

// All platform icons are rendered from this user-provided vector source.
const svg = await fs.readFile('build/icon.svg');
const render = (size) => sharp(svg, { density: 288 }).resize(size, size).png().toBuffer();
const sizes = [16, 32, 48, 64, 128, 256];
const images = await Promise.all(sizes.map(render));
const header = Buffer.alloc(6 + sizes.length * 16);
header.writeUInt16LE(1, 2);
header.writeUInt16LE(sizes.length, 4);
let offset = header.length;
sizes.forEach((size, i) => {
  const entry = 6 + i * 16;
  header[entry] = header[entry + 1] = size === 256 ? 0 : size;
  header.writeUInt16LE(1, entry + 4);
  header.writeUInt16LE(32, entry + 6);
  header.writeUInt32LE(images[i].length, entry + 8);
  header.writeUInt32LE(offset, entry + 12);
  offset += images[i].length;
});
await fs.writeFile('build/icon.ico', Buffer.concat([header, ...images]));
await fs.writeFile('build/icon.png', await render(512));

if (process.platform === 'darwin') {
  const iconset = path.join('build', 'icon.iconset');
  await fs.mkdir(iconset, { recursive: true });
  try {
    for (const [name, size] of [
      ['icon_16x16.png', 16], ['icon_16x16@2x.png', 32],
      ['icon_32x32.png', 32], ['icon_32x32@2x.png', 64],
      ['icon_128x128.png', 128], ['icon_128x128@2x.png', 256],
      ['icon_256x256.png', 256], ['icon_256x256@2x.png', 512],
      ['icon_512x512.png', 512], ['icon_512x512@2x.png', 1024],
    ]) await fs.writeFile(path.join(iconset, name), await render(size));
    const result = spawnSync('iconutil', ['-c', 'icns', iconset, '-o', 'build/icon.icns'], { stdio: 'inherit' });
    if (result.error) throw result.error;
    if (result.status !== 0) throw Error(`iconutil failed: ${result.status}`);
  } finally {
    await fs.rm(iconset, { recursive: true, force: true });
  }
}

// Explicit opt-in keeps ordinary desktop builds from rewriting Android resources.
if (process.argv.includes('--android')) {
  for (const [density, size] of [['mdpi', 48], ['hdpi', 72], ['xhdpi', 96], ['xxhdpi', 144], ['xxxhdpi', 192]]) {
    const dir = path.join('android/app/src/main/res', `mipmap-${density}`);
    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(path.join(dir, 'ic_launcher.png'), await render(size));
    const canvas = Math.round(size * 108 / 48);
    const foreground = Math.round(canvas * 2 / 3);
    const image = await render(foreground);
    const padded = await sharp({ create: { width: canvas, height: canvas, channels: 4, background: '#00000000' } })
      .composite([{ input: image, gravity: 'centre' }]).png().toBuffer();
    await fs.writeFile(path.join(dir, 'ic_launcher_foreground.png'), padded);
  }
}

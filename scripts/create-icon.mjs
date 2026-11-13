import fs from 'node:fs/promises';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import sharp from 'sharp';

// Brand icon source: the "A · 灵感记录" design — an ivory page whose folded
// corner forms a paper plane with a hidden pen nib, and a warm handwritten
// stroke, on an indigo-violet rounded square. The build renders this SVG
// into every platform artifact instead of drawing shapes procedurally, so
// future redesigns only need to replace assets/brand/pagenote-icon.svg.
const sourceSvg = path.resolve(
  path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')),
  '../assets/brand/pagenote-icon.svg',
);
const svg = await fs.readFile(sourceSvg);

async function raster(size) {
  return sharp(svg, { density: 384 })
    .resize(size, size, {
      fit: 'contain',
      background: { r: 0, g: 0, b: 0, alpha: 0 },
    })
    .png()
    .toBuffer();
}

// Windows ICO with one PNG-compressed image per size (supported since Vista).
const icoSizes = [16, 32, 48, 64, 128, 256];
const icoImages = await Promise.all(icoSizes.map(raster));
const header = Buffer.alloc(6 + 16 * icoSizes.length);
header.writeUInt16LE(0, 0); // reserved
header.writeUInt16LE(1, 2); // resource type: icon
header.writeUInt16LE(icoSizes.length, 4);
let offset = header.length;
icoSizes.forEach((size, i) => {
  const e = 6 + i * 16;
  header[e] = header[e + 1] = size === 256 ? 0 : size; // 0 encodes 256
  header.writeUInt16LE(1, e + 4); // color planes
  header.writeUInt16LE(32, e + 6); // bit depth
  header.writeUInt32LE(icoImages[i].length, e + 8);
  header.writeUInt32LE(offset, e + 12);
  offset += icoImages[i].length;
});

await fs.mkdir('build', { recursive: true });
await fs.writeFile('build/icon.ico', Buffer.concat([header, ...icoImages]));
await fs.writeFile('build/icon.png', icoImages.at(-1));
await fs.copyFile(sourceSvg, 'build/icon.svg');
if (process.platform === 'darwin') {
  // iconutil only accepts fixed iconset names, so write each size it expects.
  const iconset = path.join('build', 'icon.iconset');
  await fs.rm(iconset, { recursive: true, force: true });
  await fs.mkdir(iconset, { recursive: true });
  for (const [name, size] of [
    ['icon_16x16.png', 16],
    ['icon_16x16@2x.png', 32],
    ['icon_32x32.png', 32],
    ['icon_32x32@2x.png', 64],
    ['icon_128x128.png', 128],
    ['icon_128x128@2x.png', 256],
    ['icon_256x256.png', 256],
    ['icon_256x256@2x.png', 512],
    ['icon_512x512.png', 512],
    ['icon_512x512@2x.png', 1024],
  ])
    await fs.writeFile(path.join(iconset, name), await raster(size));
  const result = spawnSync(
    'iconutil',
    ['-c', 'icns', iconset, '-o', 'build/icon.icns'],
    { stdio: 'inherit' },
  );
  if (result.error) throw result.error;
  if (result.status !== 0)
    throw Error(`iconutil 失败，退出码 ${result.status}`);
  await fs.rm(iconset, { recursive: true, force: true });
  console.log('Collected icon.icns');
}
console.log('Icon built from', path.relative('.', sourceSvg));

import fs from 'node:fs/promises';
import path from 'node:path';
const seen = new Set(),
  notices = [],
  bundled = [
    '@gutenye/ocr-node',
    'sharp',
    'express',
    'multer',
    'pngjs',
    'pdfjs-dist',
    '@modelcontextprotocol/sdk',
    'zod',
    'dompurify',
    'highlight.js',
    'katex',
    'lucide',
    'marked',
    'marked-katex-extension',
    'mermaid',
    'three',
    'turndown',
    'turndown-plugin-gfm',
  ];
async function visit(name, parent = '.') {
  let directory = path.join(parent, 'node_modules', name);
  try {
    await fs.access(path.join(directory, 'package.json'));
  } catch {
    directory = path.join('node_modules', name);
  }
  let pkg;
  try {
    pkg = JSON.parse(
      await fs.readFile(path.join(directory, 'package.json'), 'utf8'),
    );
  } catch {
    return;
  }
  const key = pkg.name + '@' + pkg.version;
  if (seen.has(key)) return;
  seen.add(key);
  let license = '';
  for (const file of await fs.readdir(directory)) {
    if (/^licen[cs]e(?:\.|$)/i.test(file)) {
      try {
        license +=
          '\n' + (await fs.readFile(path.join(directory, file), 'utf8'));
      } catch {}
    }
  }
  notices.push(
    `${key}\nDeclared license: ${typeof pkg.license === 'string' ? pkg.license : JSON.stringify(pkg.license || 'NOT DECLARED')}\n${license || 'License text not found at package root; review required.'}\n`,
  );
  for (const dependency of Object.keys(pkg.dependencies || {}))
    await visit(dependency, directory);
}
for (const name of bundled) await visit(name);
notices.push(
  'Guten OCR source license\n' +
    (await fs.readFile('build/licenses/Guten-OCR-LICENSE.txt', 'utf8')),
);
notices.push(
  'PaddleOCR PP-OCRv4 models and dictionary\nBundled via @gutenye/ocr-models 1.4.2. Model weights/dictionary originate from PaddlePaddle/PaddleOCR and retain their Apache-2.0 license, separate from Guten OCR MIT source.\nhttps://github.com/PaddlePaddle/PaddleOCR\n' +
    (await fs.readFile('build/licenses/PaddleOCR-LICENSE.txt', 'utf8')),
);
await fs.writeFile(
  'THIRD-PARTY-NOTICES.txt',
  'pagenote third-party notices\nGenerated package inventory; review additional font, binary and asset notices before distribution.\n\n' +
    notices.sort().join('\n------------------------------\n'),
);
console.log(`Collected ${seen.size} package notices.`);

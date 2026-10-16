import { dismissWelcome } from './dismiss-welcome.mjs';
import { _electron as electron } from '@playwright/test';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import sharp from 'sharp';
import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { deflateSync } from 'node:zlib';
import assert from 'node:assert/strict';
const root = path.resolve(import.meta.dirname, '..');
const profile = path.join(root, 'tests', 'ocr-data-' + Date.now());
await fs.mkdir(profile, { recursive: true });
const scanFile = path.join(profile, 'scanned.pdf');
const { data: rgb, info } = await sharp(
  path.join(root, 'tests/fixtures/ocr-chinese-english.png'),
)
  .removeAlpha()
  .raw()
  .toBuffer({ resolveWithObject: true });
const stream = deflateSync(rgb);
const content = Buffer.from('q 550 0 0 150 0 0 cm /Im0 Do Q');
const objects = [
  Buffer.from('<< /Type /Catalog /Pages 2 0 R >>'),
  Buffer.from('<< /Type /Pages /Kids [3 0 R] /Count 1 >>'),
  Buffer.from(
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 550 150] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>',
  ),
  Buffer.concat([
    Buffer.from(
      `<< /Type /XObject /Subtype /Image /Width ${info.width} /Height ${info.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length ${stream.length} >>\nstream\n`,
    ),
    stream,
    Buffer.from('\nendstream'),
  ]),
  Buffer.concat([
    Buffer.from(`<< /Length ${content.length} >>\nstream\n`),
    content,
    Buffer.from('\nendstream'),
  ]),
];
let pdf = Buffer.from('%PDF-1.4\n'),
  offsets = [0];
objects.forEach((object, i) => {
  offsets.push(pdf.length);
  pdf = Buffer.concat([
    pdf,
    Buffer.from(`${i + 1} 0 obj\n`),
    object,
    Buffer.from('\nendobj\n'),
  ]);
});
const xref = pdf.length;
pdf = Buffer.concat([
  pdf,
  Buffer.from(
    `xref\n0 6\n0000000000 65535 f \n${offsets
      .slice(1)
      .map((o) => String(o).padStart(10, '0') + ' 00000 n \n')
      .join(
        '',
      )}trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`,
  ),
]);
await fs.writeFile(scanFile, pdf);
const hash = (value) => createHash('sha256').update(value).digest('hex');
const app = await electron.launch({
  executablePath:
    process.env.DESKTOP_EXE ||
    path.join(
      root,
      process.platform === 'darwin'
        ? 'node_modules/electron/dist/Electron.app/Contents/MacOS/Electron'
        : 'node_modules/electron/dist/electron.exe',
    ),
  args: process.env.DESKTOP_EXE ? [] : [root],
  env: {
    ...process.env,
    PAGE_NOTES_USER_DATA: profile,
    HTTP_PROXY: 'http://127.0.0.1:1',
    HTTPS_PROXY: 'http://127.0.0.1:1',
    NO_PROXY: '127.0.0.1,localhost',
  },
});
let client;
try {
  const window = await app.firstWindow();
  window.setDefaultTimeout(40000);
  await window.waitForFunction(() => !!window.pageNotes);
  await dismissWelcome(window);
  // Block all non-loopback renderer network requests during preparation.
  await app.context().route('**/*', (route) => {
    const u = new URL(route.request().url());
    return ['127.0.0.1', 'localhost'].includes(u.hostname) ||
      ['data:', 'blob:'].includes(u.protocol)
      ? route.continue()
      : route.abort();
  });
  const config = JSON.parse(
    await fs.readFile(path.join(profile, 'mcp/mcp-config.json'), 'utf8'),
  ).mcpServers.page_notes;
  client = new Client({ name: 'prepared-test', version: '1' });
  await client.connect(new StdioClientTransport({ ...config, stderr: 'pipe' }));
  const call = async (name, args = {}) => {
    const result = await client.callTool({ name, arguments: args }, undefined, {
      timeout: 120000,
    });
    assert.ok(!result.isError, result.content[0]?.text);
    return { value: JSON.parse(result.content[0].text), result };
  };
  const native = (
    await call('page_notes_import_document', {
      filePath: path.join(root, 'tests/fixtures/sample.pdf'),
    })
  ).value;
  await call('page_notes_navigate', { documentId: native.id, page: 2 });
  const before = await window.evaluate(() => window.pageNotes.getSummary());
  const originalHash = hash(
    await fs.readFile(path.join(root, 'tests/fixtures/sample.pdf')),
  );
  const nativePrepared = (
    await call('page_notes_prepare_page', { documentId: native.id, page: 1 })
  ).value;
  assert.ok(
    ['not-needed', 'completed'].includes(nativePrepared.ocr.status),
    JSON.stringify(nativePrepared.ocr),
  );
  assert.ok(
    nativePrepared.nativeCharacters > 20 && nativePrepared.originalPreserved,
  );
  const nativeText = (
    await call('page_notes_prepare_page', {
      documentId: native.id,
      page: 1,
      output: 'text',
      maxChars: 35,
    })
  ).value;
  assert.equal(nativeText.cached, true);
  assert.ok(nativeText.hasMore && nativeText.text.length === 35);
  const nativeRest = (
    await call('page_notes_prepare_page', {
      documentId: native.id,
      page: 1,
      output: 'text',
      start: nativeText.nextOffset,
      maxChars: 20000,
    })
  ).value;
  assert.equal(
    nativeText.text + nativeRest.text,
    await fs.readFile(
      path.join(path.dirname(nativePrepared.manifestPath), 'text.txt'),
      'utf8',
    ),
  );
  const nativeImage = (
    await call('page_notes_prepare_page', {
      documentId: native.id,
      page: 1,
      output: 'image',
    })
  ).result;
  assert.ok(nativeImage.content.some((c) => c.type === 'image'));
  assert.equal(
    hash(
      await fs.readFile(
        path.join(
          profile,
          'documents-data/documents',
          native.id,
          'original.pdf',
        ),
      ),
    ),
    originalHash,
  );
  console.log(
    'PASS prepared native text, image, pagination, cache and unchanged original',
  );
  const originalManifest = await fs.readFile(
    nativePrepared.manifestPath,
    'utf8',
  );
  const poisoned = JSON.parse(originalManifest);
  poisoned.visuals[0].file = '../../original.pdf';
  await fs.writeFile(nativePrepared.manifestPath, JSON.stringify(poisoned));
  const deniedImage = await client.callTool({
    name: 'page_notes_prepare_page',
    arguments: { documentId: native.id, page: 1, output: 'image' },
  });
  assert.ok(
    deniedImage.isError,
    'cached images cannot traverse out of the prepared directory',
  );
  await fs.writeFile(nativePrepared.manifestPath, originalManifest);
  const scanned = (
    await call('page_notes_import_document', {
      filePath: scanFile,
      activate: false,
    })
  ).value;
  const scan = (
    await call('page_notes_prepare_page', { documentId: scanned.id, page: 1 })
  ).value;
  assert.equal(scan.nativeCharacters, 0);
  assert.equal(scan.ocr.status, 'completed', JSON.stringify(scan.ocr));
  const text = (
    await call('page_notes_prepare_page', {
      documentId: scanned.id,
      page: 1,
      output: 'text',
    })
  ).value;
  assert.ok(
    text.text.includes('概率密度') && text.text.includes('参数估计'),
    text.text,
  );
  assert.ok(/Pattern\s*Recognition/i.test(text.text), text.text);
  assert.ok(text.warnings.length && text.visualReviewRequired);
  const blocks = (
    await call('page_notes_prepare_page', {
      documentId: scanned.id,
      page: 1,
      output: 'blocks',
    })
  ).value.items;
  assert.ok(
    blocks.length >= 2 &&
      blocks.every(
        (b) =>
          b.confidence > 0.5 &&
          b.bbox.x >= 0 &&
          b.bbox.y >= 0 &&
          b.bbox.x + b.bbox.width <= 1001,
      ),
  );
  const region = { x: 0, y: 0, width: 1000, height: 500 };
  const crop = (
    await call('page_notes_prepare_page', {
      documentId: scanned.id,
      page: 1,
      region,
      output: 'text',
    })
  ).value;
  assert.ok(
    crop.text.includes('概率密度') && !crop.text.includes('Quantile'),
    crop.text,
  );
  const invalid = await client.callTool({
    name: 'page_notes_prepare_page',
    arguments: {
      documentId: scanned.id,
      page: 1,
      region: { x: 900, y: 0, width: 200, height: 100 },
    },
  });
  assert.ok(invalid.isError);
  const state = await window.evaluate(() => window.pageNotes.getSummary());
  assert.equal(state.document.id, before.document.id);
  assert.equal(state.page, before.page);
  console.log(
    'PASS local Chinese/English OCR, confidence/boxes, region, offline resources and silent preparation',
  );
  const project = await window.evaluate(
    async (parent) =>
      (
        await fetch('/api/projects', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name: '准备数据测试', parent }),
        })
      ).json(),
    profile,
  );
  const markdown =
    '# 表格与流程\n\n| 名称 | 数值 |\n|---|---|\n| 均值 | 18 |\n\n```mermaid\nflowchart LR\n A[样本] --> B[估计]\n```';
  await window.evaluate(
    async ({ id, markdown }) => {
      const r = await fetch('/api/documents/' + id + '/markdown', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ markdown }),
      });
      if (!r.ok) throw Error(await r.text());
    },
    { id: project.id, markdown },
  );
  const prepared = (
    await call('page_notes_prepare_page', { documentId: project.id, page: 1 })
  ).value;
  assert.equal(prepared.ocr.status, 'native-markdown');
  assert.equal(prepared.tableCount, 1);
  assert.ok(
    prepared.visuals.some((v) => v.type === 'mermaid'),
    JSON.stringify(prepared.warnings),
  );
  assert.ok(
    prepared.manifestPath.startsWith(
      path.join(project.projectRoot, 'src', 'prepared'),
    ),
  );
  const tables = (
    await call('page_notes_prepare_page', {
      documentId: project.id,
      page: 1,
      output: 'tables',
    })
  ).value.items;
  assert.deepEqual(tables[0].rows, [['均值', '18']]);
  assert.ok(
    (
      await call('page_notes_prepare_page', {
        documentId: project.id,
        page: 1,
        output: 'image',
      })
    ).result.content.some((c) => c.type === 'image'),
  );
  const moved = path.join(profile, 'copied-project');
  await fs.cp(project.projectRoot, moved, { recursive: true });
  await window.evaluate(
    (file) => window.pageNotes.openProject(file),
    path.join(moved, 'project.pagenote'),
  );
  const portable = (
    await call('page_notes_prepare_page', { documentId: project.id, page: 1 })
  ).value;
  assert.equal(portable.cached, true);
  assert.ok(portable.manifestPath.startsWith(moved));
  console.log(
    'PASS native Markdown tables, Mermaid PNG/SVG/source and portable prepared cache',
  );
  if (process.env.OCR_REAL_PDF) {
    const real = (
      await call('page_notes_import_document', {
        filePath: process.env.OCR_REAL_PDF,
        activate: false,
      })
    ).value;
    const realResult = (
      await call('page_notes_prepare_page', {
        documentId: real.id,
        page: 6,
        ocr: 'auto',
      })
    ).value;
    assert.equal(
      realResult.ocr.status,
      'completed',
      JSON.stringify(realResult.ocr),
    );
    console.log(
      'PASS user course page 6 local preparation; native/OCR character counts:',
      realResult.nativeCharacters,
      realResult.ocrCharacters,
    );
  }
} finally {
  await client?.close();
  await app.close();
}

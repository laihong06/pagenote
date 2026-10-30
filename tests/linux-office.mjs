import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { convertOffice } from '../desktop/linux-office.cjs';
if (process.platform !== 'linux') process.exit(0);
const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-test-'));
try {
  const input = path.join(dir, '中文 空格.odt');
  // Flat OpenDocument XML is accepted by LibreOffice regardless of extension.
  await fs.writeFile(
    input,
    `<?xml version="1.0"?><office:document xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" office:mimetype="application/vnd.oasis.opendocument.text" office:version="1.2"><office:body><office:text><text:p>Linux conversion test</text:p></office:text></office:body></office:document>`,
  );
  const output = path.join(dir, 'result.pdf');
  await convertOffice(input, output);
  assert.equal((await fs.readFile(output)).subarray(0, 5).toString(), '%PDF-');
  for (const ext of ['docx', 'pptx']) {
    const source = path.join(import.meta.dirname, 'fixtures', 'sample.' + ext);
    const copied = path.join(dir, '中文 空格.' + ext);
    await fs.copyFile(source, copied);
    await convertOffice(copied, output);
    assert.equal((await fs.readFile(output)).subarray(0, 5).toString(), '%PDF-');
  }
  const original = process.env.PAGE_NOTES_LIBREOFFICE;
  process.env.PAGE_NOTES_LIBREOFFICE = path.join(dir, 'missing');
  try {
    await assert.rejects(convertOffice(input, output), /需要 LibreOffice/);
  } finally {
    if (original === undefined) delete process.env.PAGE_NOTES_LIBREOFFICE;
    else process.env.PAGE_NOTES_LIBREOFFICE = original;
  }
  console.log(
    'PASS Linux LibreOffice ODT/DOCX/PPTX conversion, Unicode paths and missing dependency message',
  );
} finally {
  await fs.rm(dir, { recursive: true, force: true });
}

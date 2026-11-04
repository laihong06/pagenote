import { util } from '@kit.ArkTS';

/**
 * 最小图片型 PDF 生成器：每页一张 JPEG（DCTDecode 直接嵌入，不重解码）。
 * 雨课堂导入用；页面尺寸与 Android RainProjectWriter 一致（≤960×720 等比缩放）。
 * 输入的 jpegBytes 必须是合法的 JPEG 文件字节（调用方用 ImagePacker 统一重编码）。
 */
export interface PdfImagePage {
  width: number;
  height: number;
  jpegBytes: ArrayBuffer;
}

class ByteSink {
  private chunks: Uint8Array[] = [];
  private encoder: util.TextEncoder = util.TextEncoder.create('utf-8');
  length: number = 0;

  text(value: string): void {
    const bytes = this.encoder.encodeInto(value);
    this.chunks.push(bytes);
    this.length += bytes.length;
  }

  binary(value: ArrayBuffer): void {
    const bytes = new Uint8Array(value);
    this.chunks.push(bytes);
    this.length += bytes.length;
  }

  concat(): ArrayBuffer {
    const out = new Uint8Array(this.length);
    let offset = 0;
    for (const chunk of this.chunks) {
      out.set(chunk, offset);
      offset += chunk.length;
    }
    return out.buffer;
  }
}

export class PdfBuilder {
  /** 由 JPEG 页数组生成完整 PDF 文件字节。 */
  static build(pages: PdfImagePage[]): ArrayBuffer {
    if (pages.length === 0) {
      throw new Error('雨课堂课件没有可导入的页面');
    }
    // 对象编号：1=Catalog 2=Pages；每页 3 个对象（page/image/content）
    const pageObjId = (i: number): number => 3 + i * 3;
    const imageObjId = (i: number): number => 4 + i * 3;
    const contentObjId = (i: number): number => 5 + i * 3;
    const totalObjects = 2 + pages.length * 3;

    const body = new ByteSink();
    body.text('%PDF-1.4\n');
    const offsets: number[] = new Array<number>(totalObjects + 1).fill(0);

    const beginObj = (id: number): void => {
      offsets[id] = body.length;
      body.text(`${id} 0 obj\n`);
    };

    beginObj(1);
    body.text('<< /Type /Catalog /Pages 2 0 R >>\nendobj\n');

    beginObj(2);
    const kids = pages.map((_: PdfImagePage, i: number): string => `${pageObjId(i)} 0 R`).join(' ');
    body.text(`<< /Type /Pages /Kids [${kids}] /Count ${pages.length} >>\nendobj\n`);

    for (let i = 0; i < pages.length; i++) {
      const page = pages[i];
      const w = Math.round(page.width * 100) / 100;
      const h = Math.round(page.height * 100) / 100;

      beginObj(pageObjId(i));
      body.text(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${w} ${h}] `
        + `/Resources << /XObject << /Im0 ${imageObjId(i)} 0 R >> >> `
        + `/Contents ${contentObjId(i)} 0 R >>\nendobj\n`);

      beginObj(imageObjId(i));
      body.text(`<< /Type /XObject /Subtype /Image /Width ${Math.round(page.width)} `
        + `/Height ${Math.round(page.height)} /ColorSpace /DeviceRGB /BitsPerComponent 8 `
        + `/Filter /DCTDecode /Length ${page.jpegBytes.byteLength} >>\nstream\n`);
      body.binary(page.jpegBytes);
      body.text('\nendstream\nendobj\n');

      beginObj(contentObjId(i));
      const content = `q\n${w} 0 0 ${h} 0 0 cm\n/Im0 Do\nQ\n`;
      body.text(`<< /Length ${content.length} >>\nstream\n${content}endstream\nendobj\n`);
    }

    const xrefStart = body.length;
    body.text(`xref\n0 ${totalObjects + 1}\n`);
    body.text('0000000000 65535 f \n');
    for (let id = 1; id <= totalObjects; id++) {
      body.text(`${(offsets[id]).toString().padStart(10, '0')} 00000 n \n`);
    }
    body.text(`trailer\n<< /Size ${totalObjects + 1} /Root 1 0 R >>\nstartxref\n${xrefStart}\n%%EOF\n`);

    return body.concat();
  }
}

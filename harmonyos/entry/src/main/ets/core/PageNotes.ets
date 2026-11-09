import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { util } from '@kit.ArkTS';

/**
 * 逐页 Markdown 笔记的分页与 revision 规则。
 * 与 Android core-project 的 PageNotes.kt、docs 中 page-notes-examples.md 逐条对齐。
 */
export class PageNotes {
  static readonly SEPARATOR: string = '<!-- pagebreak -->';

  /**
   * 切分整份 notes.md 为页数组。空文本返回单页空字符串；空页保留占位；页号从 1 开始。
   */
  static split(text: string): string[] {
    if (text.length === 0) {
      return [''];
    }
    const pattern: RegExp =
      /(?:\r?\n){2}<!--[ \t]*pagebreak[ \t]*-->(?:\r?\n){2}|^[ \t]*<!--[ \t]*pagebreak[ \t]*-->[ \t]*$/gm;
    return text.split(pattern);
  }

  /** 合并页数组为 notes.md 文本，保持页数不变。 */
  static join(pages: string[]): string {
    return pages.join('\n\n<!-- pagebreak -->\n\n');
  }

  /** 整页笔记 UTF-8 的 SHA-256，64 位小写 hex。空页为空字符串的 SHA-256。 */
  static async revision(markdown: string): Promise<string> {
    return PageNotes.sha256Hex(markdown);
  }

  static async sha256Hex(text: string): Promise<string> {
    // 空串直接返回其定义上的摘要值：设备上 encodeInto('') 可能返回 undefined（length 访问即崩溃）
    if (text.length === 0) {
      return 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855';
    }
    const md = cryptoFramework.createMd('SHA256');
    const bytes = new util.TextEncoder().encodeInto(text);
    await md.update({ data: bytes });
    const digest = await md.digest();
    let hex = '';
    for (let i = 0; i < digest.data.length; i++) {
      hex += digest.data[i].toString(16).padStart(2, '0');
    }
    return hex;
  }
}

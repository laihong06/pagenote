import { ProjectStore } from './ProjectStore';

/**
 * 手写批注数据，与共享契约 annotations.schema.json 及桌面 src/annotations.js 对齐：
 * 顶层固定 { document, note }，两侧各自按「页码字符串 -> 批注项数组」组织；
 * 批注项至少含 type / color(#rrggbb) / width；pen 轨迹点用 1000 比例坐标系
 * （x = (触摸点x - 层左缘) * 1000 / 层宽，y 同规则，y 上限随页面纵横比）。
 */

export interface AnnotationItem {
  type: string;
  color: string;
  width: number;
  points: number[][];
  text?: string; // type=text 的文字内容
  url?: string; // type=image 的载荷（桌面契约；鸿蒙端不透丢）
  html?: string; // type=html 的载荷（桌面契约；鸿蒙端不透丢）
}

export interface AnnotationBook {
  document: Record<string, AnnotationItem[]>;
  note: Record<string, AnnotationItem[]>;
}

export type AnnotationSide = 'document' | 'note';

export class Annotations {
  static empty(): AnnotationBook {
    return { document: {}, note: {} };
  }

  /** 读取项目 src/annotations.json；不存在或损坏时返回空结构（不吞数据：损坏时报错）。 */
  static load(path: string): AnnotationBook {
    let text: string;
    try {
      text = ProjectStore.readText(path);
    } catch (e) {
      return Annotations.empty();
    }
    let root: object;
    try {
      root = JSON.parse(text) as object;
    } catch (e) {
      throw new Error('invalid annotations JSON');
    }
    const record = root as Record<string, Object>;
    const book = Annotations.empty();
    for (const side of ['document', 'note']) {
      const sideObj = record[side] as Record<string, Object> | undefined;
      if (sideObj === undefined || sideObj === null) {
        continue;
      }
      const target: Record<string, AnnotationItem[]> = side === 'document' ? book.document : book.note;
      Object.keys(sideObj).forEach((pageKey: string) => {
        const arr = sideObj[pageKey] as Array<Record<string, Object>>;
        if (!Array.isArray(arr)) {
          return;
        }
        const items: AnnotationItem[] = [];
        for (const raw of arr) {
          const item = Annotations.decodeItem(raw);
          if (item !== null) {
            items.push(item);
          }
        }
        target[pageKey] = items;
      });
    }
    return book;
  }

  private static decodeItem(raw: Record<string, Object>): AnnotationItem | null {
    const type = raw['type'] as string;
    const color = raw['color'] as string;
    const width = raw['width'] as number;
    if (typeof type !== 'string' || typeof color !== 'string' || typeof width !== 'number') {
      return null;
    }
    if (!/^#[a-fA-F0-9]{6}$/.test(color) || width < 0.1) {
      return null;
    }
    const points = raw['points'] as Array<Array<number>>;
    const validPoints: number[][] = [];
    if (Array.isArray(points)) {
      for (const p of points) {
        if (Array.isArray(p) && p.length >= 2 && typeof p[0] === 'number' && typeof p[1] === 'number') {
          validPoints.push([p[0], p[1]]);
        }
      }
    }
    const item: AnnotationItem = { type: type, color: color, width: width, points: validPoints };
    const text = raw['text'];
    if (typeof text === 'string' && text.length > 0) {
      item.text = text;
    }
    // 桌面契约的 image/html 载荷必须透传：鸿蒙端不渲染但绝不能在保存时剥掉（审查 §4-2 跨端数据损坏）
    const url = raw['url'];
    if (typeof url === 'string' && url.length > 0) {
      item.url = url;
    }
    const html = raw['html'];
    if (typeof html === 'string' && html.length > 0) {
      item.html = html;
    }
    return item;
  }

  static pageItems(book: AnnotationBook, side: AnnotationSide, page: number): AnnotationItem[] {
    const sideBook = side === 'document' ? book.document : book.note;
    const key = `${page}`;
    return sideBook[key] ?? [];
  }

  static setPageItems(book: AnnotationBook, side: AnnotationSide, page: number,
    items: AnnotationItem[]): void {
    const sideBook = side === 'document' ? book.document : book.note;
    const key = `${page}`;
    if (items.length === 0) {
      // ArkTS 不支持 delete，用浅拷贝剔除该页键
      const next: Record<string, AnnotationItem[]> = {};
      Object.keys(sideBook).forEach((k: string) => {
        if (k !== key) {
          next[k] = sideBook[k];
        }
      });
      if (side === 'document') {
        book.document = next;
      } else {
        book.note = next;
      }
    } else {
      sideBook[key] = items;
    }
  }

  /** 原子写回 annotations.json。 */
  static save(path: string, book: AnnotationBook): void {
    ProjectStore.atomicWrite(path, JSON.stringify(book, null, 2) + '\n');
  }
}

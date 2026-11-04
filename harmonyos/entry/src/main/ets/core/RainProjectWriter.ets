import { fileIo as fs } from '@kit.CoreFileKit';
import { image } from '@kit.ImageKit';
import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { util } from '@kit.ArkTS';
import { PdfBuilder, PdfImagePage } from './PdfBuilder';
import { PageNotes } from './PageNotes';
import { ProjectStore, OpenProject } from './ProjectStore';
import { ProjectManifest } from './ProjectManifest';
import { RainCourse, RainLesson, RainSlideImage } from './RainProtocol';

/**
 * 雨课堂项目写入：图片页 → 图片型 PDF + 分页空笔记 + 来源清单 + 项目清单。
 * 与 Android RainProjectWriter.create 对齐：
 * - 页面尺寸 ≤960×720 等比缩放，JPEG 质量 88 重编码；
 * - 原图存 src/assets/yuketang/<pageId>-<sha256前16位>.<jpg|png>；
 * - yuketang-source.json 顶层课件 ID 用第一份真实课件 ID，各页保留实际课件与页面 ID；
 * - 失败时删除整个项目目录，不留半成品。
 */
export interface RainSourceEntry {
  pageId: string;
  courseId: string;
  classroomId: string;
  lessonId: string;
  presentationId: string;
  sourceIndex: number;
  projectPage: number;
  sha256: string;
  asset: string;
  firstSeenAt: string;
}

export interface RainSourceManifest {
  format: string;
  version: number;
  provider: string;
  courseId: string;
  classroomId: string;
  lessonId: string;
  presentationId: string;
  pages: RainSourceEntry[];
}

export class RainProjectWriter {
  private static async sha256Bytes(bytes: ArrayBuffer): Promise<string> {
    const md = cryptoFramework.createMd('SHA256');
    await md.update({ data: new Uint8Array(bytes) });
    const digest = await md.digest();
    let hex = '';
    for (let i = 0; i < digest.data.length; i++) {
      hex += digest.data[i].toString(16).padStart(2, '0');
    }
    return hex;
  }

  /** 任意格式图片统一解码后按 JPEG 88 重编码，返回 PDF 页与缩放后的页面尺寸。 */
  private static async toPdfPage(imageBytes: ArrayBuffer): Promise<PdfImagePage> {
    const source = image.createImageSource(imageBytes);
    try {
      const pixelMap = await source.createPixelMap();
      const info = await pixelMap.getImageInfo();
      const scale = Math.min(960 / info.size.width, 720 / info.size.height, 1);
      const packer = image.createImagePacker();
      const jpeg = await packer.packing(pixelMap, { format: 'image/jpeg', quality: 88 });
      pixelMap.release();
      return {
        width: Math.max(1, Math.round(info.size.width * scale)),
        height: Math.max(1, Math.round(info.size.height * scale)),
        jpegBytes: jpeg
      };
    } finally {
      source.release();
    }
  }

  /**
   * 创建雨课堂项目。pages 必须已按导入顺序排好；onProgress 在每页处理完成后回调（从 1 起）。
   */
  static async create(root: string, name: string, course: RainCourse, lesson: RainLesson,
    pages: RainSlideImage[], onProgress?: (done: number, total: number) => void): Promise<OpenProject> {
    if (pages.length === 0) {
      throw new Error('雨课堂课件没有可导入的页面');
    }
    const dir = `${root}/${name}`;
    try {
      fs.mkdirSync(`${dir}/src/assets/yuketang`, true);
    } catch (e) {
      throw new Error('所选目录中已存在同名项目或无法创建目录');
    }
    try {
      const pdfPages: PdfImagePage[] = [];
      const entries: RainSourceEntry[] = [];
      for (let i = 0; i < pages.length; i++) {
        const page = pages[i];
        const pdfPage = await RainProjectWriter.toPdfPage(page.bytes);
        pdfPages.push(pdfPage);
        const digest = await RainProjectWriter.sha256Bytes(page.bytes);
        const extension = page.contentType === 'image/png' ? 'png' : 'jpg';
        const safeId = page.slide.pageId.replace(/[^a-zA-Z0-9_-]/g, '_').substring(0, 64) || 'page';
        const assetName = `${safeId}-${digest.substring(0, 16)}.${extension}`;
        const assetPath = `${dir}/src/assets/yuketang/${assetName}`;
        if (!RainProjectWriter.exists(assetPath)) {
          const file = fs.openSync(assetPath, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
          try {
            fs.writeSync(file.fd, page.bytes);
          } finally {
            fs.closeSync(file);
          }
        }
        const entry: RainSourceEntry = {
          pageId: page.slide.pageId,
          courseId: course.courseId,
          classroomId: course.classroomId,
          lessonId: lesson.lessonId ?? '',
          presentationId: page.slide.presentationId,
          sourceIndex: page.slide.sourceIndex,
          projectPage: i + 1,
          sha256: digest,
          asset: `src/assets/yuketang/${assetName}`,
          firstSeenAt: new Date().toISOString()
        };
        entries.push(entry);
        if (onProgress !== undefined) {
          onProgress(i + 1, pages.length);
        }
      }

      const pdf = PdfBuilder.build(pdfPages);
      const pdfFile = fs.openSync(`${dir}/src/document.pdf`,
        fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
      try {
        fs.writeSync(pdfFile.fd, pdf);
      } finally {
        fs.closeSync(pdfFile);
      }

      const emptyPages: string[] = [];
      for (let i = 0; i < pages.length; i++) {
        emptyPages.push('');
      }
      ProjectStore.atomicWrite(`${dir}/src/right.md`, PageNotes.join(emptyPages));

      const sourceManifest: RainSourceManifest = {
        format: 'pagenote-yuketang-source',
        version: 1,
        provider: 'www.yuketang.cn',
        courseId: course.courseId,
        classroomId: course.classroomId,
        lessonId: lesson.lessonId ?? '',
        presentationId: entries[0].presentationId,
        pages: entries
      };
      ProjectStore.atomicWrite(`${dir}/src/yuketang-source.json`, JSON.stringify(sourceManifest, null, 2));

      const manifest: ProjectManifest = {
        format: 'pagenote-project',
        version: 1,
        id: await PageNotes.sha256Hex(util.generateRandomUUID()),
        name: name,
        created: new Date().toISOString(),
        modified: null,
        left: 'src/document.pdf',
        right: 'src/right.md',
        leftName: null,
        preview: null
      };
      ProjectStore.saveManifest(dir, manifest);
      return ProjectStore.open(dir);
    } catch (e) {
      RainProjectWriter.removeDir(dir);
      if (e instanceof Error) {
        throw e;
      }
      throw new Error(String(e));
    }
  }

  private static exists(path: string): boolean {
    try {
      fs.statSync(path);
      return true;
    } catch (e) {
      return false;
    }
  }

  private static removeDir(path: string): void {
    try {
      const entries = fs.listFileSync(path);
      for (const entry of entries) {
        const child = `${path}/${entry}`;
        try {
          if (fs.statSync(child).isDirectory()) {
            RainProjectWriter.removeDir(child);
          } else {
            fs.unlink(child);
          }
        } catch (e) {
          // 尽力清理
        }
      }
      fs.rmdirSync(path);
    } catch (e) {
      // 尽力清理
    }
  }
}

import { fileIo as fs } from '@kit.CoreFileKit';
import { image } from '@kit.ImageKit';
import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { util } from '@kit.ArkTS';
import { PdfBuilder, PdfImagePage } from './PdfBuilder';
import { PageNotes } from './PageNotes';
import { ProjectStore, OpenProject } from './ProjectStore';
import { ProjectManifest, ProjectValidation } from './ProjectManifest';
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

export interface RainSourceSection {
  classroomId: string;
  presentationId: string;
  title: string;
  startPage: number;
  endPage: number;
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
  sections: RainSourceSection[];
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
        pages: entries,
        sections: []
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

  /**
   * 向已有雨课堂项目追加一页（实时采集用）。幂等：同一（classroomId+presentationId+pageId+sha256）
   * 不重复插入。排序规则对齐 Android RainPageOrder：按（classroomId, presentationId）分组、
   * 组内按 sourceIndex，组间按首次出现顺序。PDF 由全部来源图片按最终页序重建，
   * 笔记与批注按 oldToNew 重映射，失败不写盘。
   * 返回新页码；重复页返回 null。
   */
  static async insert(projectDir: string, course: RainCourse, lesson: RainLesson,
    page: RainSlideImage): Promise<number | null> {
    const project = ProjectStore.open(projectDir);
    if (ProjectValidation.kindOf(project.manifest.left) !== 'pdf') {
      throw new Error('实时雨课堂采集需要 PDF 项目');
    }
    const manifestPath = `${projectDir}/src/yuketang-source.json`;
    if (!RainProjectWriter.exists(manifestPath)) {
      throw new Error('该雨课堂项目缺少来源记录，已停止插页');
    }
    const source = RainProjectWriter.readSourceManifest(manifestPath);
    const oldPageCount = source.pages.length;
    const digest = await RainProjectWriter.sha256Bytes(page.bytes);

    // 幂等去重：身份完全一致（含内容哈希）视为已有页
    for (const entry of source.pages) {
      const entryClassroom = entry.classroomId.length > 0 ? entry.classroomId : source.classroomId;
      if (entryClassroom === course.classroomId && entry.presentationId === page.slide.presentationId
        && entry.pageId === page.slide.pageId && entry.sha256 === digest) {
        return null;
      }
    }

    const incoming: RainSourceEntry = {
      pageId: page.slide.pageId,
      courseId: course.courseId,
      classroomId: course.classroomId,
      lessonId: lesson.lessonId ?? '',
      presentationId: page.slide.presentationId,
      sourceIndex: page.slide.sourceIndex,
      projectPage: oldPageCount + 1,
      sha256: digest,
      asset: '',
      firstSeenAt: new Date().toISOString()
    };

    // RainPageOrder 语义：按（classroomId, presentationId）分组，组内 sourceIndex 升序
    const all: RainSourceEntry[] = [...source.pages, incoming];
    const groupOrder: string[] = [];
    const groups = new Map<string, RainSourceEntry[]>();
    for (const entry of all) {
      const key = `${entry.classroomId}|${entry.presentationId}`;
      let group = groups.get(key);
      if (group === undefined) {
        group = [];
        groups.set(key, group);
        groupOrder.push(key);
      }
      group.push(entry);
    }
    const ordered: RainSourceEntry[] = [];
    for (const key of groupOrder) {
      const group = groups.get(key)!;
      group.sort((a: RainSourceEntry, b: RainSourceEntry): number => a.sourceIndex - b.sourceIndex);
      ordered.push(...group);
    }
    const oldToNew = new Map<number, number>();
    ordered.forEach((entry: RainSourceEntry, index: number) => {
      oldToNew.set(entry.projectPage, index + 1);
    });

    // 写新页图片资源
    const extension = page.contentType === 'image/png' ? 'png' : 'jpg';
    const safeId = page.slide.pageId.replace(/[^a-zA-Z0-9_-]/g, '_').substring(0, 64) || 'page';
    const assetName = `${safeId}-${digest.substring(0, 16)}.${extension}`;
    const assetPath = `${projectDir}/src/assets/yuketang/${assetName}`;
    fs.mkdirSync(`${projectDir}/src/assets/yuketang`, true);
    incoming.asset = `src/assets/yuketang/${assetName}`;

    // 全部内容先在内存就绪，再统一落盘
    const pdfPages: PdfImagePage[] = [];
    for (const entry of ordered) {
      const entryBytes = entry.projectPage === oldPageCount + 1
        ? page.bytes
        : RainProjectWriter.readBytes(`${projectDir}/${entry.asset}`);
      pdfPages.push(await RainProjectWriter.toPdfPage(entryBytes));
    }
    const pdf = PdfBuilder.build(pdfPages);

    const oldNotesText = RainProjectWriter.exists(project.rightPath)
      ? ProjectStore.readText(project.rightPath) : '';
    const oldNotes = PageNotes.split(oldNotesText);
    if (oldNotes.length > oldPageCount) {
      throw new Error('项目笔记页数与 PDF 不一致，已停止追加以保护现有笔记');
    }
    const newNotes: string[] = [];
    for (const entry of ordered) {
      if (entry.projectPage <= oldPageCount) {
        newNotes.push(oldNotes[entry.projectPage - 1] ?? '');
      } else {
        newNotes.push('');
      }
    }
    const notesText = PageNotes.join(newNotes);

    const annotationsPath = `${projectDir}/src/annotations.json`;
    let annotationsText: string | null = null;
    if (RainProjectWriter.exists(annotationsPath)) {
      annotationsText = RainProjectWriter.remapAnnotations(
        ProjectStore.readText(annotationsPath), oldToNew, oldPageCount);
    }

    const sections: RainSourceSection[] = [];
    groupOrder.forEach((key: string, index: number) => {
      const group = groups.get(key)!;
      const positions = group.map((entry: RainSourceEntry): number => oldToNew.get(entry.projectPage)!);
      const section: RainSourceSection = {
        classroomId: group[0].classroomId,
        presentationId: group[0].presentationId,
        title: `PPT ${index + 1}`,
        startPage: Math.min(...positions),
        endPage: Math.max(...positions)
      };
      sections.push(section);
    });
    ordered.forEach((entry: RainSourceEntry) => {
      entry.projectPage = oldToNew.get(entry.projectPage)!;
    });
    const nextManifest: RainSourceManifest = {
      format: 'pagenote-yuketang-source',
      version: 1,
      provider: 'www.yuketang.cn',
      courseId: course.courseId,
      classroomId: course.classroomId,
      lessonId: lesson.lessonId ?? '',
      presentationId: incoming.presentationId,
      pages: ordered,
      sections: sections
    };
    const manifestText = JSON.stringify(nextManifest, null, 2);

    // 落盘：图片 → PDF → 笔记 → 批注 → 来源清单
    if (!RainProjectWriter.exists(assetPath)) {
      const assetFile = fs.openSync(assetPath, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
      try {
        fs.writeSync(assetFile.fd, page.bytes);
      } finally {
        fs.closeSync(assetFile);
      }
    }
    const pdfFile = fs.openSync(project.leftPath, fs.OpenMode.READ_WRITE | fs.OpenMode.TRUNC);
    try {
      fs.writeSync(pdfFile.fd, pdf);
    } finally {
      fs.closeSync(pdfFile);
    }
    ProjectStore.atomicWrite(project.rightPath, notesText);
    if (annotationsText !== null) {
      ProjectStore.atomicWrite(annotationsPath, annotationsText);
    }
    ProjectStore.atomicWrite(manifestPath, manifestText);
    return oldToNew.get(oldPageCount + 1)!;
  }

  private static readSourceManifest(path: string): RainSourceManifest {
    const parsed = JSON.parse(ProjectStore.readText(path)) as Record<string, Object | null>;
    const pagesValue = parsed['pages'];
    if (!Array.isArray(pagesValue)) {
      throw new Error('雨课堂来源记录无效，已停止插页');
    }
    const pages: RainSourceEntry[] = [];
    const seen = new Set<number>();
    for (const element of pagesValue as Array<Object | null>) {
      const item = element as Record<string, Object | null>;
      const entry: RainSourceEntry = {
        pageId: item['pageId']?.toString() ?? '',
        courseId: item['courseId']?.toString() ?? parsed['courseId']?.toString() ?? '',
        classroomId: item['classroomId']?.toString() ?? parsed['classroomId']?.toString() ?? '',
        lessonId: item['lessonId']?.toString() ?? '',
        presentationId: item['presentationId']?.toString() ?? '',
        sourceIndex: Number(item['sourceIndex'] ?? -1),
        projectPage: Number(item['projectPage'] ?? 0),
        sha256: item['sha256']?.toString() ?? '',
        asset: item['asset']?.toString() ?? '',
        firstSeenAt: item['firstSeenAt']?.toString() ?? ''
      };
      if (entry.presentationId.length === 0 || entry.sourceIndex < 0 || entry.projectPage < 1) {
        throw new Error('雨课堂来源页码无效，已停止插页');
      }
      if (seen.has(entry.projectPage)) {
        throw new Error('雨课堂来源页码重复，已停止插页');
      }
      seen.add(entry.projectPage);
      pages.push(entry);
    }
    return {
      format: 'pagenote-yuketang-source',
      version: 1,
      provider: parsed['provider']?.toString() ?? 'www.yuketang.cn',
      courseId: parsed['courseId']?.toString() ?? '',
      classroomId: parsed['classroomId']?.toString() ?? '',
      lessonId: parsed['lessonId']?.toString() ?? '',
      presentationId: parsed['presentationId']?.toString() ?? '',
      pages: pages,
      sections: []
    };
  }

  /** 批注页码按 oldToNew 重映射；超出旧页数的批注保留原页码防呆。 */
  private static remapAnnotations(text: string, oldToNew: Map<number, number>, oldPageCount: number): string {
    const root = JSON.parse(text) as Record<string, Object | null>;
    const remapSide = (side: Object | null | undefined): Object | null | undefined => {
      if (side === null || side === undefined || typeof side !== 'object' || Array.isArray(side)) {
        return side;
      }
      const map = side as Record<string, Object | null>;
      const next: Record<string, Object | null> = {};
      const keys = Object.keys(map);
      for (const key of keys) {
        const page = Number(key);
        if (Number.isInteger(page) && page >= 1 && page <= oldPageCount) {
          next[String(oldToNew.get(page) ?? page)] = map[key];
        } else {
          next[key] = map[key];
        }
      }
      return next;
    };
    root['document'] = remapSide(root['document']) ?? null;
    root['note'] = remapSide(root['note']) ?? null;
    return JSON.stringify(root);
  }

  private static readBytes(path: string): ArrayBuffer {
    const stat = fs.statSync(path);
    const file = fs.openSync(path, fs.OpenMode.READ_ONLY);
    try {
      const buffer = new ArrayBuffer(stat.size);
      fs.readSync(file.fd, buffer);
      return buffer;
    } finally {
      fs.closeSync(file);
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

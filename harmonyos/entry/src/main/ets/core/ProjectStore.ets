import { fileIo as fs } from '@kit.CoreFileKit';
import { util } from '@kit.ArkTS';
import { PageNotes } from './PageNotes';
import { ProjectManifest, ProjectManifestCodec, ProjectValidation } from './ProjectManifest';

/** 打开的项目：清单 + 目录与关键文件绝对路径。 */
export interface OpenProject {
  dir: string;
  manifest: ProjectManifest;
  leftPath: string;
  rightPath: string;
}

export interface ProjectSummary {
  dir: string;
  name: string;
  id: string;
  modified: string | null;
  kind: string;
}

const BAD_NAME: RegExp = /[<>:"/\\|?*]/;
const MANIFEST_NAMES: string[] = ['project.pagenote', 'project.page-notes.json'];

/**
 * 项目库：在应用沙箱 projects/ 下创建、列举、打开与保存项目。
 * 与 Android core-project 的 ProjectStore.kt 对齐；写文件一律原子写。
 */
export class ProjectStore {
  static projectsRoot(filesDir: string): string {
    return `${filesDir}/projects`;
  }

  private static exists(path: string): boolean {
    try {
      fs.statSync(path);
      return true;
    } catch (e) {
      return false;
    }
  }

  /** 读取 UTF-8 文本文件；不存在时抛异常，调用方自行判断。 */
  static readText(path: string): string {
    return fs.readTextSync(path);
  }

  /** 原子写：先写同目录临时文件再 rename，避免半写状态。 */
  static atomicWrite(path: string, text: string): void {
    const slash = path.lastIndexOf('/');
    const dir = slash >= 0 ? path.substring(0, slash) : path;
    const name = slash >= 0 ? path.substring(slash + 1) : path;
    if (!ProjectStore.exists(dir)) {
      fs.mkdirSync(dir, true);
    }
    const tmp = `${dir}/${name}.${util.generateRandomUUID()}.tmp`;
    const file = fs.openSync(tmp, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
    try {
      fs.writeSync(file.fd, text);
    } finally {
      fs.closeSync(file);
    }
    fs.renameSync(tmp, path);
  }

  static async newManifest(name: string): Promise<ProjectManifest> {
    if (name.trim().length === 0 || BAD_NAME.test(name)) {
      throw new Error('bad project name');
    }
    const id = await PageNotes.sha256Hex(util.generateRandomUUID());
    return {
      format: 'pagenote-project',
      version: 1,
      id: id,
      name: name,
      created: new Date().toISOString(),
      modified: null,
      left: 'src/left.md',
      right: 'src/right.md',
      leftName: null,
      preview: null
    };
  }

  /** 新建项目目录。重名时报错，不覆盖已有目录。 */
  static async create(root: string, name: string): Promise<OpenProject> {
    const manifest = await ProjectStore.newManifest(name);
    const dir = `${root}/${name}`;
    if (ProjectStore.exists(dir)) {
      throw new Error('project already exists');
    }
    fs.mkdirSync(`${dir}/src/assets`, true);
    ProjectStore.atomicWrite(`${dir}/src/left.md`, `# ${name}\n`);
    ProjectStore.atomicWrite(`${dir}/src/right.md`, '');
    ProjectValidation.validate(manifest);
    ProjectStore.atomicWrite(`${dir}/project.pagenote`, ProjectManifestCodec.encode(manifest));
    return ProjectStore.open(dir);
  }

  /** 打开项目目录，读取入口清单并解析左右文件绝对路径。 */
  static open(dir: string): OpenProject {
    let manifestPath: string | null = null;
    for (const candidate of MANIFEST_NAMES) {
      const p = `${dir}/${candidate}`;
      if (ProjectStore.exists(p)) {
        manifestPath = p;
        break;
      }
    }
    if (manifestPath === null) {
      throw new Error('project manifest not found');
    }
    const manifest = ProjectManifestCodec.decode(ProjectStore.readText(manifestPath));
    return {
      dir: dir,
      manifest: manifest,
      leftPath: `${dir}/${ProjectValidation.resolveLinkSegments(manifest.left).join('/')}`,
      rightPath: `${dir}/${ProjectValidation.resolveLinkSegments(manifest.right).join('/')}`
    };
  }

  /** 列举沙箱项目库下的全部项目，坏目录跳过。 */
  static list(root: string): ProjectSummary[] {
    const result: ProjectSummary[] = [];
    if (!ProjectStore.exists(root)) {
      return result;
    }
    const names = fs.listFileSync(root);
    for (const name of names) {
      const dir = `${root}/${name}`;
      try {
        if (!fs.statSync(dir).isDirectory()) {
          continue;
        }
        const project = ProjectStore.open(dir);
        result.push({
          dir: dir,
          name: project.manifest.name,
          id: project.manifest.id,
          modified: project.manifest.modified ?? project.manifest.created,
          kind: ProjectValidation.kindOf(project.manifest.left)
        });
      } catch (e) {
        continue;
      }
    }
    return result;
  }

  /** 读取整份右侧笔记并按页拆分，返回页码（1 起）到内容的映射。 */
  static readNotes(rightPath: string): Map<number, string> {
    const text = ProjectStore.exists(rightPath) ? ProjectStore.readText(rightPath) : '';
    const pages = PageNotes.split(text);
    const notes = new Map<number, string>();
    for (let i = 0; i < pages.length; i++) {
      notes.set(i + 1, pages[i]);
    }
    return notes;
  }

  static writeNotes(rightPath: string, notes: Map<number, string>): void {
    let count = 1;
    notes.forEach((value, key) => {
      if (key > count) {
        count = key;
      }
    });
    const pages: string[] = [];
    for (let i = 1; i <= count; i++) {
      pages.push(notes.get(i) ?? '');
    }
    ProjectStore.atomicWrite(rightPath, PageNotes.join(pages));
  }

  /**
   * 保存单页笔记。带 expectedRevision 时先比对当前 revision，不一致拒绝覆盖。
   * 返回是否发生了写入。
   */
  static async saveNote(rightPath: string, page: number, markdown: string,
    expectedRevision: string | null): Promise<boolean> {
    if (page < 1) {
      throw new Error('bad page');
    }
    const notes = ProjectStore.readNotes(rightPath);
    const old = notes.get(page) ?? '';
    if (expectedRevision !== null) {
      const current = await PageNotes.revision(old);
      if (current !== expectedRevision) {
        throw new Error('note changed, read again');
      }
    }
    if (old === markdown) {
      return false;
    }
    notes.set(page, markdown);
    ProjectStore.writeNotes(rightPath, notes);
    return true;
  }

  /** 更新入口清单（例如导入左侧文档后改写 left 链接），原子写回 project.pagenote。 */
  static saveManifest(dir: string, manifest: ProjectManifest): void {
    manifest.modified = new Date().toISOString();
    ProjectValidation.validate(manifest);
    ProjectStore.atomicWrite(`${dir}/project.pagenote`, ProjectManifestCodec.encode(manifest));
  }
}

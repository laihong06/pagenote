import { fileIo as fs } from '@kit.CoreFileKit';
import { util } from '@kit.ArkTS';
import { PageNotes } from './PageNotes';

export interface NoteVersion {
  id: string;
  created: number;
  bytes: number;
}

const NAME_PATTERN: RegExp = /^(\d{13})-[a-f0-9\-]{36}-page(\d+)\.md$/;

/**
 * 单页笔记历史快照，与 Android core-project 的 NoteHistory.kt 对齐。
 * 快照存放在项目 src/history/ 下（调用方传入目录），文件名：
 * <13位毫秒时间戳>-<uuid>-page<页码>.md
 */
export class NoteHistory {
  static fileName(page: number, stamp?: number): string {
    if (page < 1) {
      throw new Error('bad page');
    }
    const ts = stamp ?? Date.now();
    return `${ts}-${util.generateRandomUUID()}-page${page}.md`;
  }

  private static exists(path: string): boolean {
    try {
      fs.statSync(path);
      return true;
    } catch (e) {
      return false;
    }
  }

  static snapshot(historyDir: string, page: number, markdown: string): string {
    if (page < 1) {
      throw new Error('bad page');
    }
    if (!NoteHistory.exists(historyDir)) {
      fs.mkdirSync(historyDir, true);
    }
    const file = `${historyDir}/${NoteHistory.fileName(page)}`;
    const fd = fs.openSync(file, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
    try {
      fs.writeSync(fd.fd, markdown);
    } finally {
      fs.closeSync(fd);
    }
    return file;
  }

  /** 与最新快照内容相同则跳过，返回 null；否则写快照并返回路径。 */
  static snapshotOnce(historyDir: string, page: number, markdown: string): string | null {
    if (page < 1) {
      throw new Error('bad page');
    }
    const list = NoteHistory.entries(historyDir, page);
    if (list.length > 0) {
      const latest = list[0];
      if (fs.readTextSync(`${historyDir}/${latest.id}`) === markdown) {
        return null;
      }
    }
    return NoteHistory.snapshot(historyDir, page, markdown);
  }

  /** 某页的全部快照，新的在前。 */
  static entries(historyDir: string, page: number): NoteVersion[] {
    if (page < 1) {
      throw new Error('bad page');
    }
    const result: NoteVersion[] = [];
    if (!NoteHistory.exists(historyDir)) {
      return result;
    }
    const files = fs.listFileSync(historyDir);
    for (const name of files) {
      const match = NAME_PATTERN.exec(name);
      if (match === null) {
        continue;
      }
      if (parseInt(match[2], 10) !== page) {
        continue;
      }
      const stat = fs.statSync(`${historyDir}/${name}`);
      result.push({ id: name, created: parseInt(match[1], 10), bytes: stat.size });
    }
    result.sort((a, b) => b.created - a.created);
    return result;
  }

  /** 恢复某页到指定快照。先校验当前内容 revision，不一致拒绝。 */
  static async restore(historyDir: string, version: string, page: number,
    expectedRevision: string, current: string): Promise<string> {
    const list = NoteHistory.entries(historyDir, page);
    const entry = list.find((item) => item.id === version);
    if (entry === undefined) {
      throw new Error('version not found');
    }
    const currentRevision = await PageNotes.revision(current);
    if (currentRevision !== expectedRevision) {
      throw new Error('note changed, read again');
    }
    return fs.readTextSync(`${historyDir}/${entry.id}`);
  }

  static read(historyDir: string, versionId: string): string {
    return fs.readTextSync(`${historyDir}/${versionId}`);
  }
}

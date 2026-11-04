import { PageNotes } from './PageNotes';

/**
 * 笔记导出/导入负载，与 Android NoteExportPayload / note-import 契约对齐：
 * - 全量备份 JSON v1：{version:1, document, pages, notes:{"1":"…",…}}（页码从 1 起的字符串键）；
 * - 当前页导出为完整 Markdown 草稿；
 * - 导入 JSON 备份时：合法页覆盖（空字符串清空该页），缺失/非法页不动，与既有内容合并；
 * - 导入 Markdown/TXT 按纯文本由调用方插入光标处。
 */

export interface NoteTransferFile {
  mimeType: string;
  fileName: string;
  content: string;
}

/** JSON v1 备份结构（与桌面/Android 逐字段一致）。 */
export interface NoteBackup {
  version: number;
  document: string;
  pages: number;
  notes: Record<string, string>;
}

export class NoteTransfer {
  /** 全量备份：当前编辑中的脏草稿（如有）覆盖对应页后再打包。 */
  static fullBackup(projectName: string, sourcePageCount: number, notesMarkdown: string,
    currentPage?: number, currentDraft?: string): NoteTransferFile {
    if (projectName.trim().length === 0 || sourcePageCount < 1) {
      throw new Error('export requires project name and page count');
    }
    const pages = PageNotes.split(notesMarkdown);
    const notes: Record<string, string> = {};
    for (let i = 0; i < pages.length; i++) {
      notes[`${i + 1}`] = pages[i];
    }
    if (currentDraft !== undefined && currentPage !== undefined && currentPage > 0) {
      notes[`${currentPage}`] = currentDraft;
    }
    const backup: NoteBackup = {
      version: 1,
      document: projectName,
      pages: sourcePageCount,
      notes: notes
    };
    return {
      mimeType: 'application/json',
      fileName: `${projectName}-逐页笔记.json`,
      content: JSON.stringify(backup, null, 2) + '\n'
    };
  }

  static currentPage(projectName: string, page: number, markdown: string): NoteTransferFile {
    if (projectName.trim().length === 0 || page < 1) {
      throw new Error('export requires project name and page');
    }
    return {
      mimeType: 'text/markdown',
      fileName: `${projectName}-第${page}页.md`,
      content: markdown
    };
  }

  /** 文件内容是否为 JSON 笔记备份（含 version+notes 对象）。 */
  static isBackupJson(text: string): boolean {
    try {
      const parsed = JSON.parse(text) as Record<string, Object | null>;
      const notes = parsed['notes'];
      return notes !== null && typeof notes === 'object' && !Array.isArray(notes);
    } catch (e) {
      return false;
    }
  }

  /**
   * 合并 JSON 备份到现有整份笔记：合法页（页码 ≥1 的字符串键）覆盖；空字符串清空该页；
   * 缺失页不动。返回合并后的整份 notes.md 文本。
   */
  static mergeBackup(existingMarkdown: string, backupText: string): string {
    let parsed: Record<string, Object | null>;
    try {
      parsed = JSON.parse(backupText) as Record<string, Object | null>;
    } catch (e) {
      throw new Error('不是有效的笔记备份文件');
    }
    const notesValue = parsed['notes'];
    if (notesValue === null || typeof notesValue !== 'object' || Array.isArray(notesValue)) {
      throw new Error('备份文件缺少 notes 页表');
    }
    const pages = PageNotes.split(existingMarkdown);
    const notes = notesValue as Record<string, Object | null>;
    const keys = Object.keys(notes);
    for (const key of keys) {
      const page = Number(key);
      if (!Number.isInteger(page) || page < 1) {
        continue;
      }
      const value = notes[key];
      if (typeof value !== 'string') {
        continue;
      }
      while (pages.length < page) {
        pages.push('');
      }
      pages[page - 1] = value;
    }
    return PageNotes.join(pages);
  }
}

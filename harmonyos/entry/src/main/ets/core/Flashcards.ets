import { PageNotes } from './PageNotes';
import { ProjectStore } from './ProjectStore';
import { util } from '@kit.ArkTS';

/**
 * 闪卡：从笔记制卡、SM-2 简化排程复习、卡片库。
 * 与 Android flashcard/ 对齐：FlashcardCapture（选区/段落捕获语义）、
 * ReviewScheduler（AGAIN/HARD/GOOD/EASY 四档，常数一致）。
 * 存储用项目内 src/flashcards.json（Android 在 Room 里，不跨端；JSON 便于检查与迁移）。
 */

export interface FlashcardItem {
  id: string;
  page: number;
  sourceKind: string; // note_selection | note_paragraph
  front: string;
  back: string;
  sourceQuote: string;
  anchorStart: number;
  anchorEnd: number;
  revision: string;
  tags: string[];
  createdAt: number;
  dueAt: number;
  intervalDays: number;
  easeX1000: number;
  reps: number;
  lapses: number;
}

export type ReviewOutcome = 'again' | 'hard' | 'good' | 'easy';

export interface FlashcardSchedule {
  dueAt: number;
  intervalDays: number;
  easeX1000: number;
  reps: number;
  lapses: number;
}

/** SM-2 简化版排程，常数与 Android ReviewScheduler 逐项一致。 */
export class ReviewScheduler {
  static readonly MIN_EASE: number = 1300;
  static readonly MAX_EASE: number = 3500;
  static readonly DEFAULT_EASE: number = 2500;
  static readonly MAX_INTERVAL_DAYS: number = 365;
  static readonly RELEARN_DELAY_MINUTES: number = 10;
  static readonly MIN_INTERVAL_DAYS: number = 1;
  private static readonly DAY_MS: number = 24 * 60 * 60 * 1000;

  static initial(now: number): FlashcardSchedule {
    return {
      dueAt: now, intervalDays: 0, easeX1000: ReviewScheduler.DEFAULT_EASE, reps: 0, lapses: 0
    };
  }

  static schedule(current: FlashcardSchedule, outcome: ReviewOutcome, now: number): FlashcardSchedule {
    let eased = current.easeX1000;
    if (outcome === 'again') {
      eased -= 200;
    } else if (outcome === 'hard') {
      eased -= 50;
    } else if (outcome === 'easy') {
      eased += 50;
    }
    eased = Math.min(ReviewScheduler.MAX_EASE, Math.max(ReviewScheduler.MIN_EASE, eased));

    const scaleInterval = (currentDays: number, factor: number): number => {
      const scaled = Math.max(ReviewScheduler.MIN_INTERVAL_DAYS, Math.floor(currentDays * factor));
      return Math.min(ReviewScheduler.MAX_INTERVAL_DAYS, scaled);
    };
    let intervalDays: number;
    if (outcome === 'again') {
      intervalDays = 0;
    } else if (outcome === 'hard') {
      intervalDays = scaleInterval(current.intervalDays, 1.2);
    } else if (outcome === 'good') {
      intervalDays = current.intervalDays <= 0 ? ReviewScheduler.MIN_INTERVAL_DAYS
        : scaleInterval(current.intervalDays, current.easeX1000 / 1000);
    } else {
      intervalDays = current.intervalDays <= 0 ? 3
        : scaleInterval(current.intervalDays, current.easeX1000 / 1000 * 1.3);
    }
    const dueAt = outcome === 'again'
      ? now + ReviewScheduler.RELEARN_DELAY_MINUTES * 60 * 1000
      : now + intervalDays * ReviewScheduler.DAY_MS;
    return {
      dueAt: dueAt,
      intervalDays: intervalDays,
      easeX1000: eased,
      reps: current.reps + 1,
      lapses: current.lapses + (outcome === 'again' ? 1 : 0)
    };
  }

  static scheduleOf(card: FlashcardItem): FlashcardSchedule {
    return {
      dueAt: card.dueAt, intervalDays: card.intervalDays,
      easeX1000: card.easeX1000, reps: card.reps, lapses: card.lapses
    };
  }
}

/** 制卡捕获：选区/段落边界语义与 Android FlashcardCapture 一致（UTF-16 偏移 + 页 revision）。 */
export class FlashcardCapture {
  static readonly MAX_TEXT_LENGTH: number = 20000;
  static readonly MAX_TAGS: number = 12;
  static readonly MAX_TAG_LENGTH: number = 32;

  static async fromSelection(page: number, context: string, start: number,
    end: number): Promise<FlashcardDraft> {
    if (page < 1) {
      throw new Error('page must be one-based');
    }
    const s = Math.min(start, end);
    const e = Math.max(start, end);
    if (s < 0 || e > context.length || s >= e) {
      throw new Error('selection is outside note page or empty');
    }
    const quote = context.substring(s, e);
    const front = quote.trim();
    if (front.length === 0 || front.length > FlashcardCapture.MAX_TEXT_LENGTH) {
      throw new Error('selection must contain non-whitespace text within size limit');
    }
    return {
      page: page,
      sourceKind: 'note_selection',
      front: front,
      sourceQuote: quote,
      anchorStart: s,
      anchorEnd: e,
      revision: await PageNotes.revision(context)
    };
  }

  /** 光标所在「空行分隔块」的字符区间；光标在空行/无段落返回 null。 */
  static paragraphAround(context: string, offset: number): number[] | null {
    if (offset < 0 || context.length === 0) {
      return null;
    }
    let cursor = 0;
    let blockStart = -1;
    let blockEnd = -1;
    while (cursor <= context.length) {
      const newline = context.indexOf('\n', cursor);
      const lineEnd = newline < 0 ? context.length : newline;
      const line = context.substring(cursor, lineEnd);
      if (line.trim().length === 0) {
        if (blockStart >= 0 && offset >= blockStart && offset <= blockEnd) {
          return [blockStart, blockEnd];
        }
        blockStart = -1;
        blockEnd = -1;
      } else {
        if (blockStart < 0) {
          blockStart = cursor;
        }
        blockEnd = lineEnd;
      }
      if (newline < 0) {
        break;
      }
      cursor = newline + 1;
    }
    if (blockStart >= 0 && offset >= blockStart && offset <= blockEnd) {
      return [blockStart, blockEnd];
    }
    return null;
  }

  static async fromParagraph(page: number, context: string, offset: number): Promise<FlashcardDraft> {
    const range = FlashcardCapture.paragraphAround(context, offset);
    if (range === null) {
      throw new Error('光标所在处没有可制卡的段落');
    }
    return FlashcardCapture.fromSelection(page, context, range[0], range[1])
      .then((draft: FlashcardDraft): FlashcardDraft => {
        draft.sourceKind = 'note_paragraph';
        if (draft.front.length > FlashcardCapture.MAX_TEXT_LENGTH) {
          draft.front = draft.front.substring(0, FlashcardCapture.MAX_TEXT_LENGTH);
        }
        return draft;
      });
  }
}

export interface FlashcardDraft {
  page: number;
  sourceKind: string;
  front: string;
  sourceQuote: string;
  anchorStart: number;
  anchorEnd: number;
  revision: string;
}

interface FlashcardsFile {
  format: string;
  version: number;
  cards: FlashcardItem[];
}

/** 闪卡存储：项目 src/flashcards.json，原子写。 */
export class FlashcardStore {
  static load(path: string): FlashcardItem[] {
    let text: string;
    try {
      text = ProjectStore.readText(path);
    } catch (e) {
      return [];
    }
    try {
      const parsed = JSON.parse(text) as Record<string, Object | null>;
      const cards = parsed['cards'];
      if (!Array.isArray(cards)) {
        return [];
      }
      return cards as FlashcardItem[];
    } catch (e) {
      return [];
    }
  }

  static save(path: string, cards: FlashcardItem[]): void {
    const file: FlashcardsFile = { format: 'pagenote-flashcards', version: 1, cards: cards };
    ProjectStore.atomicWrite(path, JSON.stringify(file, null, 2) + '\n');
  }

  static newCard(draft: FlashcardDraft, back: string, tags: string[], now: number): FlashcardItem {
    const schedule = ReviewScheduler.initial(now);
    return {
      id: util.generateRandomUUID(),
      page: draft.page,
      sourceKind: draft.sourceKind,
      front: draft.front,
      back: back,
      sourceQuote: draft.sourceQuote,
      anchorStart: draft.anchorStart,
      anchorEnd: draft.anchorEnd,
      revision: draft.revision,
      tags: tags.slice(0, FlashcardCapture.MAX_TAGS),
      createdAt: now,
      dueAt: schedule.dueAt,
      intervalDays: schedule.intervalDays,
      easeX1000: schedule.easeX1000,
      reps: schedule.reps,
      lapses: schedule.lapses
    };
  }

  /** 到期卡片：dueAt <= now，按到期时间升序。 */
  static dueCards(cards: FlashcardItem[], now: number): FlashcardItem[] {
    return cards.filter((card: FlashcardItem): boolean => card.dueAt <= now)
      .sort((a: FlashcardItem, b: FlashcardItem): number => a.dueAt - b.dueAt);
  }

  /** 评分后更新排程字段。 */
  static applyReview(card: FlashcardItem, outcome: ReviewOutcome, now: number): void {
    const next = ReviewScheduler.schedule(ReviewScheduler.scheduleOf(card), outcome, now);
    card.dueAt = next.dueAt;
    card.intervalDays = next.intervalDays;
    card.easeX1000 = next.easeX1000;
    card.reps = next.reps;
    card.lapses = next.lapses;
  }
}

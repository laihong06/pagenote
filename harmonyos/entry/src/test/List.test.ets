import { describe, it, expect } from '@ohos/hypium';
import { PageNotes } from '../main/ets/core/PageNotes';
import { ProjectManifestCodec, ProjectManifest } from '../main/ets/core/ProjectManifest';
import { NoteTransfer } from '../main/ets/core/NoteTransfer';
import { ReviewScheduler, FlashcardStore, FlashcardItem } from '../main/ets/core/Flashcards';
import { normalizeHistoryLessons, rainObjectData, normalizeCurrentLessons } from '../main/ets/core/RainProtocol';

/**
 * 核心层纯逻辑单元测试（Local Test，不依赖设备能力）。
 * 与 Android core-project / 桌面契约相同的用例语义。
 */
function manifestOf(id: string, left: string): ProjectManifest {
  return {
    format: 'pagenote-project', version: 1, id: id, name: '测试',
    created: null, modified: null,
    left: left, right: 'src/right.md', leftName: null, preview: null
  };
}

function expectThrows(fn: () => void): void {
  let threw = false;
  try {
    fn();
  } catch (e) {
    threw = true;
  }
  expect(threw).assertTrue();
}

export default function coreTest(): void {
  describe('coreTest', () => {
    it('pageNotesSplitJoinRoundtrip', 0, () => {
      const text = ['第一页', '第二页'].join('\n\n<!-- pagebreak -->\n\n');
      const pages = PageNotes.split(text);
      expect(pages.length).assertEqual(2);
      expect(pages[0]).assertEqual('第一页');
      expect(pages[1]).assertEqual('第二页');
      expect(PageNotes.join(pages)).assertEqual(text);
    });

    it('pageNotesEmptyAndBlankPages', 0, () => {
      expect(PageNotes.split('').length).assertEqual(1);
      expect(PageNotes.split('')[0]).assertEqual('');
      const joined = PageNotes.join(['a', '', 'c']);
      const pages = PageNotes.split(joined);
      expect(pages.length).assertEqual(3);
      expect(pages[1]).assertEqual('');
    });

    it('pageNotesRevisionEmpty', 0, async () => {
      const rev = await PageNotes.revision('');
      expect(rev).assertEqual('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
    });

    it('manifestCodecRoundtrip', 0, () => {
      const id = 'a'.repeat(64);
      const manifest: ProjectManifest = {
        format: 'pagenote-project', version: 1, id: id, name: '测试',
        created: '2026-01-01T00:00:00Z', modified: null,
        left: 'src/left.pdf', right: 'src/right.md', leftName: null, preview: null
      };
      const decoded = ProjectManifestCodec.decode(ProjectManifestCodec.encode(manifest));
      expect(decoded.id).assertEqual(id);
      expect(decoded.left).assertEqual('src/left.pdf');
      expect(decoded.name).assertEqual('测试');
    });

    it('manifestRejectsBadValues', 0, () => {
      expectThrows(() => ProjectManifestCodec.encode(manifestOf('abc', 'src/left.pdf')));
      expectThrows(() => ProjectManifestCodec.encode(manifestOf('a'.repeat(64), '/etc/passwd')));
      expectThrows(() => ProjectManifestCodec.encode(manifestOf('a'.repeat(64), 'src/../secret')));
    });

    it('mergeBackupOverridesClearsAppends', 0, () => {
      const existing = ['第一页', '第二页'].join('\n\n<!-- pagebreak -->\n\n');
      const backup = '{"version":1,"document":"x","pages":2,'
        + '"notes":{"1":"覆盖第一页","3":"新增第三页","0":"非法页","x":"非数字键"}}';
      const merged = NoteTransfer.mergeBackup(existing, backup);
      const pages = PageNotes.split(merged);
      expect(pages.length).assertEqual(3);
      expect(pages[0]).assertEqual('覆盖第一页');
      expect(pages[1]).assertEqual('第二页');
      expect(pages[2]).assertEqual('新增第三页');
      const cleared = NoteTransfer.mergeBackup(merged,
        '{"version":1,"document":"x","pages":3,"notes":{"2":""}}');
      expect(PageNotes.split(cleared)[1]).assertEqual('');
    });

    it('reviewSchedulerOutcomes', 0, () => {
      const now = 1000000;
      const init = ReviewScheduler.initial(now);
      expect(init.intervalDays).assertEqual(0);
      expect(init.easeX1000).assertEqual(2500);
      const good = ReviewScheduler.schedule(init, 'good', now);
      expect(good.intervalDays).assertEqual(1);
      expect(good.dueAt).assertEqual(now + 24 * 60 * 60 * 1000);
      expect(good.reps).assertEqual(1);
      const again = ReviewScheduler.schedule(good, 'again', now);
      expect(again.intervalDays).assertEqual(0);
      expect(again.easeX1000).assertEqual(2300);
      expect(again.lapses).assertEqual(1);
      expect(again.dueAt).assertEqual(now + 10 * 60 * 1000);
      const easy = ReviewScheduler.schedule(init, 'easy', now);
      expect(easy.intervalDays).assertEqual(3);
      expect(easy.easeX1000).assertEqual(2550);
    });

    it('rainHistoryTypeSemantics', 0, () => {
      const text = '{"code":0,"data":{"activities":['
        + '{"type":3,"courseware_id":"111","id":"999","title":"课堂A"},'
        + '{"type":14,"courseware_id":"222","id":"998","title":"课堂B"},'
        + '{"type":8,"id":"997","title":"测验不导入"},'
        + '{"presentation_id":"333","title":"直连课件"}]}}';
      const payload = JSON.parse(text) as Record<string, Object | null>;
      const lessons = normalizeHistoryLessons(payload, '55');
      expect(lessons.length).assertEqual(3);
      expect(lessons[0].lessonId).assertEqual('111');
      expect(lessons[1].lessonId).assertEqual('222');
      expect(lessons[2].lessonId).assertEqual(null);
      expect(lessons[2].presentationId).assertEqual('333');
    });

    it('rainObjectDataLessonOngoing', 0, () => {
      const payload = JSON.parse('{"errcode":1,"errmsg":"LESSON_ON"}') as Record<string, Object | null>;
      expectThrows(() => rainObjectData(payload));
    });

    it('rainCurrentLessonsStatusFilter', 0, () => {
      const text = '{"code":0,"data":{"onLessonClassrooms":['
        + '{"lessonId":"1","name":"进行中无status"},'
        + '{"lessonId":"2","name":"显式进行中","status":1},'
        + '{"lessonId":"3","name":"已结束","status":0}]}}';
      const payload = JSON.parse(text) as Record<string, Object | null>;
      const lessons = normalizeCurrentLessons(payload);
      expect(lessons.length).assertEqual(2);
      expect(lessons[1].lessonId).assertEqual('2');
    });

    // 注：Local Test 宿主环境的 fileIo 不支持任意目录写入（mkdir 即报 No such file or directory），
    // ProjectStore.saveNote 的 revision 校验链改为设备实测覆盖（空页保存修复验证）。

    it('flashcardDueFiltering', 0, () => {
      const now = 1000000;
      const makeCard = (id: string, dueAt: number): FlashcardItem => {
        return {
          id: id, page: 1, sourceKind: 'note_paragraph', front: id, back: '',
          sourceQuote: '', anchorStart: 0, anchorEnd: 0, revision: '',
          tags: [], createdAt: now, dueAt: dueAt,
          intervalDays: 0, easeX1000: 2500, reps: 0, lapses: 0
        };
      };
      const cards: FlashcardItem[] = [
        makeCard('future', now + 100000),
        makeCard('due-late', now - 100),
        makeCard('due-early', now - 500)
      ];
      const due = FlashcardStore.dueCards(cards, now);
      expect(due.length).assertEqual(2);
      expect(due[0].id).assertEqual('due-early');
      expect(due[1].id).assertEqual('due-late');
    });

    it('noteTransferIsBackupJson', 0, () => {
      expect(NoteTransfer.isBackupJson('{"version":1,"document":"x","pages":1,"notes":{"1":"a"}}')).assertTrue();
      expect(NoteTransfer.isBackupJson('# 普通 markdown')).assertFalse();
      expect(NoteTransfer.isBackupJson('{"foo":1}')).assertFalse();
      expect(NoteTransfer.isBackupJson('{"notes":[1,2]}')).assertFalse();
    });
  });
}

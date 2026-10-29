package io.pagenote.android.flashcard

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 闪卡来源类别（移植批次 11 / 差距清单 #20）。导图（MIND_MAP）为占位，本批次不开放创建入口。 */
enum class FlashcardSourceKind(val key: String) {
  NOTE_SELECTION("note_selection"),
  NOTE_PARAGRAPH("note_paragraph"),
  MIND_MAP("mind_map"),
  ;

  companion object {
    fun fromKey(key: String): FlashcardSourceKind? = entries.firstOrNull { it.key == key }
  }
}

/**
 * 闪卡表（Room，复用批次 7 的 search-index.db，版本 2）。
 * [anchorStart]/[anchorEnd] 为捕获时笔记页内 UTF-16 偏移（markdown-source 坐标），
 * 与选区审阅/AI 动作的 anchor 语义一致；[sourceQuote] 保留捕获原文供回跳定位。
 * 排程字段由 [ReviewScheduler]（SM-2 简化）维护：[dueAt] 毫秒时间戳、
 * [intervalDays] 当前间隔天数、[easeX1000] 难度系数（permille）、[reps]/[lapses] 计数。
 */
@Entity(
  tableName = "flashcards",
  indices = [Index(value = ["projectId", "dueAt"])],
)
data class FlashcardEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val projectId: String,
  val page: Int,
  val sourceKind: String,
  val front: String,
  val back: String,
  val sourceQuote: String,
  val anchorStart: Int,
  val anchorEnd: Int,
  val revision: String,
  val createdAt: Long,
  val dueAt: Long,
  val intervalDays: Int,
  val easeX1000: Int,
  val reps: Int,
  val lapses: Int,
)

/** 标签表：项目内名称唯一（大小写敏感，与创建输入的规范化结果一致）。 */
@Entity(
  tableName = "tags",
  indices = [Index(value = ["projectId", "name"], unique = true)],
)
data class TagEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val projectId: String,
  val name: String,
)

/** 闪卡 ↔ 标签 关联表：随卡片/标签级联删除。 */
@Entity(
  tableName = "flashcard_tags",
  primaryKeys = ["cardId", "tagId"],
  foreignKeys = [
    ForeignKey(
      entity = FlashcardEntity::class,
      parentColumns = ["id"],
      childColumns = ["cardId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = TagEntity::class,
      parentColumns = ["id"],
      childColumns = ["tagId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["tagId"])],
)
data class FlashcardTagCrossRef(
  val cardId: Long,
  val tagId: Long,
)

/** 复习记录表：统计（今日复习/正确率/连续天数）的数据来源。 */
@Entity(
  tableName = "flashcard_reviews",
  indices = [
    Index(value = ["cardId"]),
    Index(value = ["projectId", "reviewedAt"]),
  ],
)
data class FlashcardReviewEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val cardId: Long,
  val projectId: String,
  val reviewedAt: Long,
  val outcome: String,
  val intervalAfter: Int,
)

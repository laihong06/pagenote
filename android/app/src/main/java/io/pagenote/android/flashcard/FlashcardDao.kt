package io.pagenote.android.flashcard

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/** 卡片排程字段的最小更新载荷（复习后由 [ReviewScheduler] 计算产出）。 */
data class FlashcardScheduleUpdate(
  val dueAt: Long,
  val intervalDays: Int,
  val easeX1000: Int,
  val reps: Int,
  val lapses: Int,
)

@Dao
interface FlashcardDao {

  // --- 创建（卡片 + 标签 + 关联，同一事务） -----------------------------------

  @Insert
  suspend fun insertCard(card: FlashcardEntity): Long

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertTagIfAbsent(tag: TagEntity): Long

  @Query("SELECT id FROM tags WHERE projectId = :projectId AND name = :name")
  suspend fun tagId(projectId: String, name: String): Long?

  @Insert
  suspend fun insertCardTags(refs: List<FlashcardTagCrossRef>)

  /** 业务级创建：同名标签复用已有行，空标签列表跳过关联写入。 */
  @Transaction
  suspend fun createCard(card: FlashcardEntity, tagNames: List<String>): Long {
    val cardId = insertCard(card)
    if (tagNames.isNotEmpty()) {
      val refs = tagNames.map { name ->
        insertTagIfAbsent(TagEntity(projectId = card.projectId, name = name))
        FlashcardTagCrossRef(cardId = cardId, tagId = requireNotNull(tagId(card.projectId, name)))
      }
      insertCardTags(refs)
    }
    return cardId
  }

  // --- 复习（排程更新 + 记录，同一事务） ---------------------------------------

  @Query(
    "UPDATE flashcards SET dueAt = :dueAt, intervalDays = :intervalDays, " +
      "easeX1000 = :easeX1000, reps = :reps, lapses = :lapses WHERE id = :cardId",
  )
  suspend fun updateSchedule(
    cardId: Long,
    dueAt: Long,
    intervalDays: Int,
    easeX1000: Int,
    reps: Int,
    lapses: Int,
  )

  @Insert
  suspend fun insertReview(review: FlashcardReviewEntity): Long

  @Transaction
  suspend fun applyReview(cardId: Long, update: FlashcardScheduleUpdate, review: FlashcardReviewEntity) {
    updateSchedule(
      cardId = cardId,
      dueAt = update.dueAt,
      intervalDays = update.intervalDays,
      easeX1000 = update.easeX1000,
      reps = update.reps,
      lapses = update.lapses,
    )
    insertReview(review)
  }

  // --- 查询 -------------------------------------------------------------------

  @Query("SELECT * FROM flashcards WHERE projectId = :projectId ORDER BY createdAt DESC, id DESC")
  suspend fun cardsForProject(projectId: String): List<FlashcardEntity>

  @Query(
    "SELECT * FROM flashcards WHERE projectId = :projectId AND dueAt <= :now " +
      "ORDER BY dueAt, id LIMIT :limit",
  )
  suspend fun dueCards(projectId: String, now: Long, limit: Int): List<FlashcardEntity>

  @Query("SELECT COUNT(*) FROM flashcards WHERE projectId = :projectId")
  suspend fun countForProject(projectId: String): Int

  @Query("SELECT COUNT(*) FROM flashcard_reviews WHERE projectId = :projectId")
  suspend fun reviewCountForProject(projectId: String): Int

  @Query(
    "SELECT * FROM flashcard_reviews WHERE projectId = :projectId AND reviewedAt >= :since " +
      "ORDER BY reviewedAt",
  )
  suspend fun reviewsSince(projectId: String, since: Long): List<FlashcardReviewEntity>

  @Query(
    "SELECT tags.name FROM tags JOIN flashcard_tags ON tags.id = flashcard_tags.tagId " +
      "WHERE flashcard_tags.cardId = :cardId ORDER BY tags.name",
  )
  suspend fun tagsForCard(cardId: Long): List<String>

  // --- 删除 -------------------------------------------------------------------

  @Query("DELETE FROM flashcards WHERE id = :cardId")
  suspend fun deleteCard(cardId: Long)

  @Query("DELETE FROM flashcards WHERE projectId = :projectId")
  suspend fun deleteProject(projectId: String)

  @Query("DELETE FROM tags WHERE projectId = :projectId AND id NOT IN (SELECT tagId FROM flashcard_tags)")
  suspend fun deleteOrphanTags(projectId: String)

  /** 删除卡片后清理失去引用的项目内标签。 */
  @Transaction
  suspend fun deleteCardAndOrphanTags(cardId: Long, projectId: String) {
    deleteCard(cardId)
    deleteOrphanTags(projectId)
  }
}

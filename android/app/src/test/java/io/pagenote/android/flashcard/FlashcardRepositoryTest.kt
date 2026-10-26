package io.pagenote.android.flashcard

import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 闪卡仓储（移植批次 11）：以内存 fake [FlashcardDao] 驱动（JVM 单测无 Room 运行时），
 * 覆盖创建（标签复用）、到期队列、评分落库与统计聚合。
 */
class FlashcardRepositoryTest {
  private val now = 1_792_011_360_000L
  private val day = 24L * 60 * 60 * 1000

  private class FakeFlashcardDao : FlashcardDao {
    val cards = mutableListOf<FlashcardEntity>()
    val tags = mutableListOf<TagEntity>()
    val cardTags = mutableListOf<FlashcardTagCrossRef>()
    val reviews = mutableListOf<FlashcardReviewEntity>()
    private var nextId = 1L

    override suspend fun insertCard(card: FlashcardEntity): Long {
      val id = nextId++
      cards += card.copy(id = id)
      return id
    }

    override suspend fun insertTagIfAbsent(tag: TagEntity): Long {
      val existing = tags.firstOrNull { it.projectId == tag.projectId && it.name == tag.name }
      if (existing != null) return -1L
      val id = nextId++
      tags += tag.copy(id = id)
      return id
    }

    override suspend fun tagId(projectId: String, name: String): Long? =
      tags.firstOrNull { it.projectId == projectId && it.name == name }?.id

    override suspend fun insertCardTags(refs: List<FlashcardTagCrossRef>) {
      cardTags += refs
    }

    override suspend fun updateSchedule(
      cardId: Long,
      dueAt: Long,
      intervalDays: Int,
      easeX1000: Int,
      reps: Int,
      lapses: Int,
    ) {
      val index = cards.indexOfFirst { it.id == cardId }
      if (index >= 0) {
        cards[index] = cards[index].copy(
          dueAt = dueAt,
          intervalDays = intervalDays,
          easeX1000 = easeX1000,
          reps = reps,
          lapses = lapses,
        )
      }
    }

    override suspend fun insertReview(review: FlashcardReviewEntity): Long {
      val id = nextId++
      reviews += review.copy(id = id)
      return id
    }

    override suspend fun cardsForProject(projectId: String): List<FlashcardEntity> =
      cards.filter { it.projectId == projectId }
        .sortedWith(compareByDescending<FlashcardEntity> { it.createdAt }.thenByDescending { it.id })

    override suspend fun dueCards(projectId: String, now: Long, limit: Int): List<FlashcardEntity> =
      cards.filter { it.projectId == projectId && it.dueAt <= now }
        .sortedWith(compareBy<FlashcardEntity> { it.dueAt }.thenBy { it.id })
        .take(limit)

    override suspend fun countForProject(projectId: String): Int =
      cards.count { it.projectId == projectId }

    override suspend fun reviewCountForProject(projectId: String): Int =
      reviews.count { it.projectId == projectId }

    override suspend fun reviewsSince(projectId: String, since: Long): List<FlashcardReviewEntity> =
      reviews.filter { it.projectId == projectId && it.reviewedAt >= since }.sortedBy { it.reviewedAt }

    override suspend fun tagsForCard(cardId: Long): List<String> =
      cardTags.filter { it.cardId == cardId }
        .mapNotNull { ref -> tags.firstOrNull { it.id == ref.tagId }?.name }
        .sorted()

    override suspend fun deleteCard(cardId: Long) {
      cards.removeAll { it.id == cardId }
      cardTags.removeAll { it.cardId == cardId }
    }

    override suspend fun deleteProject(projectId: String) {
      cards.removeAll { it.projectId == projectId }
      cardTags.removeAll { ref -> cards.none { it.id == ref.cardId } }
    }

    override suspend fun deleteOrphanTags(projectId: String) {
      val used = cardTags.mapTo(mutableSetOf()) { it.tagId }
      tags.removeAll { it.projectId == projectId && it.id !in used }
    }
  }

  private fun draft(page: Int = 1, front: String = "正面前提") = FlashcardCapture.Draft(
    page = page,
    sourceKind = FlashcardSourceKind.NOTE_SELECTION,
    front = front,
    sourceQuote = front,
    anchorStart = 0,
    anchorEnd = front.length,
    revision = "rev",
  )

  @Test
  fun `create persists card with normalized tags and reuses tag rows`() = runBlocking {
    val dao = FakeFlashcardDao()
    val repository = FlashcardRepository(dao)
    val cardId = repository.create("p1", draft(), back = " 答案 ", tagsRaw = "算法，阅读, 算法 ", now = now)

    assertEquals(1, dao.cards.size)
    val card = dao.cards.single()
    assertEquals(cardId, card.id)
    assertEquals("答案", card.back)
    assertEquals(ReviewScheduler.initial(now).dueAt, card.dueAt)
    assertEquals(listOf("算法", "阅读"), dao.tagsForCard(cardId))
    // 同项目内同名标签只存一行。
    assertEquals(2, dao.tags.size)
  }

  @Test
  fun `create rejects oversized back and empty project`() = runBlocking {
    val repository = FlashcardRepository(FakeFlashcardDao())
    try {
      repository.create("p1", draft(), back = "x".repeat(FlashcardCapture.MAX_TEXT_LENGTH + 1), tagsRaw = "", now = now)
      throw AssertionError("expected IllegalArgumentException")
    } catch (_: IllegalArgumentException) {
      // 预期
    }
    try {
      repository.create("  ", draft(), back = "ok", tagsRaw = "", now = now)
      throw AssertionError("expected IllegalArgumentException")
    } catch (_: IllegalArgumentException) {
      // 预期
    }
  }

  @Test
  fun `dueCards returns only due cards ordered by dueAt with limit`() = runBlocking {
    val dao = FakeFlashcardDao()
    val repository = FlashcardRepository(dao)
    repository.create("p1", draft(front = "早"), back = "", tagsRaw = "", now = now - 2 * day)
    repository.create("p1", draft(front = "晚"), back = "", tagsRaw = "", now = now - day)
    repository.create("p1", draft(front = "未到期"), back = "", tagsRaw = "", now = now)
    // 未到期卡：手动推后。
    dao.cards[2] = dao.cards[2].copy(dueAt = now + day)

    val due = repository.dueCards("p1", now)
    assertEquals(listOf("早", "晚"), due.map { it.front })
    assertEquals(1, repository.dueCards("p1", now, limit = 1).size)
    assertTrue(repository.dueCards("p2", now).isEmpty())
  }

  @Test
  fun `review reschedules card and records review log`() = runBlocking {
    val dao = FakeFlashcardDao()
    val repository = FlashcardRepository(dao)
    val cardId = repository.create("p1", draft(), back = "", tagsRaw = "", now = now - day)
    val card = repository.allCards("p1").single()

    val result = repository.review(card, ReviewScheduler.ReviewOutcome.GOOD, now)
    assertEquals(now + day, result.schedule.dueAt)

    val updated = repository.allCards("p1").single()
    assertEquals(now + day, updated.entity.dueAt)
    assertEquals(1, updated.entity.intervalDays)
    assertEquals(1, updated.entity.reps)
    assertEquals(1, dao.reviews.size)
    assertEquals("GOOD", dao.reviews.single().outcome)
    assertEquals(now + day, updated.entity.dueAt)
  }

  @Test
  fun `stats aggregates cards and review log`() = runBlocking {
    val dao = FakeFlashcardDao()
    val repository = FlashcardRepository(dao)
    repository.create("p1", draft(front = "a"), back = "", tagsRaw = "", now = now - day)
    repository.create("p1", draft(front = "b"), back = "", tagsRaw = "", now = now - day)
    val cards = repository.allCards("p1")
    // 只复习第一张：另一张仍处于到期状态（dueAt = 创建时刻）。
    repository.review(cards[0], ReviewScheduler.ReviewOutcome.GOOD, now)

    val stats = repository.stats("p1", now, ZoneOffset.UTC)
    assertEquals(2, stats.totalCards)
    assertEquals(1, stats.dueNow)
    assertEquals(1, stats.reviewedToday)
    assertEquals(1, stats.correctToday)
    assertEquals(1, stats.lifetimeReviews)
  }

  @Test
  fun `deleteCard removes card and orphan tags keep shared tags`() = runBlocking {
    val dao = FakeFlashcardDao()
    val repository = FlashcardRepository(dao)
    val first = repository.create("p1", draft(front = "a"), back = "", tagsRaw = "共享,仅A", now = now)
    repository.create("p1", draft(front = "b"), back = "", tagsRaw = "共享", now = now)

    val cardA = repository.allCards("p1").first { it.id == first }
    repository.deleteCard(cardA)

    assertEquals(1, dao.cards.size)
    val tagNames = dao.tags.map { it.name }.sorted()
    // 「仅A」随卡片删除；「共享」仍被第二张卡引用。
    assertEquals(listOf("共享"), tagNames)
    assertNull(dao.tags.firstOrNull { it.name == "仅A" })
  }
}

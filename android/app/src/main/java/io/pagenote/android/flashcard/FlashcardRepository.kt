package io.pagenote.android.flashcard

import java.time.ZoneId

/** 闪卡领域模型：实体 + 标签名（UI/回跳使用）。 */
data class FlashcardCard(
  val entity: FlashcardEntity,
  val tags: List<String>,
) {
  val id: Long get() = entity.id
  val page: Int get() = entity.page
  val front: String get() = entity.front
  val back: String get() = entity.back
  val dueAt: Long get() = entity.dueAt

  fun sourceKind(): FlashcardSourceKind? = FlashcardSourceKind.fromKey(entity.sourceKind)

  /** 回跳锚点（来源定位）。 */
  fun anchor(): CardSourceLocator.Anchor = CardSourceLocator.Anchor(
    page = entity.page,
    anchorStart = entity.anchorStart,
    anchorEnd = entity.anchorEnd,
    sourceQuote = entity.sourceQuote,
  )
}

/** 复习结果（一次评分后的新排程，供 UI 展示下次到期时间）。 */
data class FlashcardReviewResult(val schedule: ReviewScheduler.Schedule)

/**
 * 闪卡仓储（移植批次 11 / 差距清单 #20）：Room [FlashcardDao] 之上的业务层。
 * 排程计算委托 [ReviewScheduler]，统计委托 [ReviewStats]。
 */
class FlashcardRepository(private val dao: FlashcardDao) {

  /** 制卡：front 取捕获草稿，back/标签由创建对话框提供。返回新卡 id。 */
  suspend fun create(
    projectId: String,
    draft: FlashcardCapture.Draft,
    back: String,
    tagsRaw: String,
    now: Long,
  ): Long {
    require(projectId.isNotBlank()) { "projectId is required" }
    val trimmedBack = back.trim()
    require(trimmedBack.length <= FlashcardCapture.MAX_TEXT_LENGTH) { "card back is too large" }
    val tags = FlashcardCapture.normalizeTags(tagsRaw)
    val schedule = ReviewScheduler.initial(now)
    val entity = FlashcardEntity(
      projectId = projectId,
      page = draft.page,
      sourceKind = draft.sourceKind.key,
      front = draft.front,
      back = trimmedBack,
      sourceQuote = draft.sourceQuote,
      anchorStart = draft.anchorStart,
      anchorEnd = draft.anchorEnd,
      revision = draft.revision,
      createdAt = now,
      dueAt = schedule.dueAt,
      intervalDays = schedule.intervalDays,
      easeX1000 = schedule.easeX1000,
      reps = schedule.reps,
      lapses = schedule.lapses,
    )
    return dao.createCard(entity, tags)
  }

  /** 到期卡片（最多 [limit] 张，按到期时间升序）。 */
  suspend fun dueCards(projectId: String, now: Long, limit: Int = DEFAULT_REVIEW_LIMIT): List<FlashcardCard> {
    require(limit > 0) { "review limit must be positive" }
    return dao.dueCards(projectId, now, limit).map { it.toCard() }
  }

  /** 全部卡片（卡片库/统计）。 */
  suspend fun allCards(projectId: String): List<FlashcardCard> =
    dao.cardsForProject(projectId).map { it.toCard() }

  suspend fun countForProject(projectId: String): Int = dao.countForProject(projectId)

  /** 评分：计算新排程并与复习记录同事务落库。 */
  suspend fun review(card: FlashcardCard, outcome: ReviewScheduler.ReviewOutcome, now: Long): FlashcardReviewResult {
    val schedule = ReviewScheduler.schedule(
      current = ReviewScheduler.Schedule(
        dueAt = card.entity.dueAt,
        intervalDays = card.entity.intervalDays,
        easeX1000 = card.entity.easeX1000,
        reps = card.entity.reps,
        lapses = card.entity.lapses,
      ),
      outcome = outcome,
      now = now,
    )
    dao.applyReview(
      cardId = card.id,
      update = FlashcardScheduleUpdate(
        dueAt = schedule.dueAt,
        intervalDays = schedule.intervalDays,
        easeX1000 = schedule.easeX1000,
        reps = schedule.reps,
        lapses = schedule.lapses,
      ),
      review = FlashcardReviewEntity(
        cardId = card.id,
        projectId = card.entity.projectId,
        reviewedAt = now,
        outcome = outcome.name,
        intervalAfter = schedule.intervalDays,
      ),
    )
    return FlashcardReviewResult(schedule)
  }

  /** 统计与成就输入。 */
  suspend fun stats(projectId: String, now: Long, zone: ZoneId): ReviewStats.Stats {
    val cards = dao.cardsForProject(projectId).map {
      ReviewStats.CardInput(dueAt = it.dueAt, intervalDays = it.intervalDays)
    }
    val reviews = dao.reviewsSince(projectId, since = 0).map {
      ReviewStats.ReviewInput(reviewedAt = it.reviewedAt, outcome = it.outcome)
    }
    return ReviewStats.compute(cards, reviews, now, zone)
  }

  suspend fun deleteCard(card: FlashcardCard) = dao.deleteCardAndOrphanTags(card.id, card.entity.projectId)

  private suspend fun FlashcardEntity.toCard(): FlashcardCard =
    FlashcardCard(entity = this, tags = dao.tagsForCard(id))

  companion object {
    const val DEFAULT_REVIEW_LIMIT = 50
  }
}

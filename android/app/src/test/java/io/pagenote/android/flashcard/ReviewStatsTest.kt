package io.pagenote.android.flashcard

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** 复习统计与成就（移植批次 11）：固定 UTC 时区的纯计算。 */
class ReviewStatsTest {
  private val zone = ZoneOffset.UTC
  // 2026-10-26 12:00 UTC。
  private val now = 1_792_011_360_000L
  private val day = 24L * 60 * 60 * 1000

  private fun review(daysAgo: Long, outcome: String) = ReviewStats.ReviewInput(
    reviewedAt = now - daysAgo * day,
    outcome = outcome,
  )

  @Test
  fun `compute aggregates due mastered and today accuracy`() {
    val stats = ReviewStats.compute(
      cards = listOf(
        ReviewStats.CardInput(dueAt = now - 1, intervalDays = 30),
        ReviewStats.CardInput(dueAt = now + day, intervalDays = 3),
        ReviewStats.CardInput(dueAt = now, intervalDays = 0),
      ),
      reviews = listOf(
        review(0, "GOOD"),
        review(0, "EASY"),
        review(0, "AGAIN"),
        review(1, "GOOD"),
      ),
      now = now,
      zone = zone,
    )
    assertEquals(3, stats.totalCards)
    assertEquals(2, stats.dueNow)
    assertEquals(1, stats.masteredCards)
    assertEquals(4, stats.lifetimeReviews)
    assertEquals(3, stats.reviewedToday)
    assertEquals(2, stats.correctToday)
    assertEquals(66, stats.accuracyTodayPercent)
    assertEquals(2, stats.streakDays)
  }

  @Test
  fun `streak counts consecutive days ending today or yesterday`() {
    fun stats(vararg reviews: ReviewStats.ReviewInput) =
      ReviewStats.compute(emptyList(), reviews.toList(), now, zone).streakDays

    // 今天有复习 → 从今天往回数。
    assertEquals(1, stats(review(0, "GOOD")))
    assertEquals(2, stats(review(0, "GOOD"), review(1, "AGAIN")))
    // 今天没有复习，但昨天有 → 不归零，从昨天起算。
    assertEquals(1, stats(review(1, "GOOD")))
    // 前天有、昨天没有 → 断档。
    assertEquals(0, stats(review(2, "GOOD")))
    // 三天连续（今天起）。
    assertEquals(3, stats(review(0, "GOOD"), review(1, "GOOD"), review(2, "GOOD")))
  }

  @Test
  fun `achievements follow thresholds`() {
    val none = ReviewStats.Stats(
      totalCards = 0, dueNow = 0, masteredCards = 0, lifetimeReviews = 0,
      reviewedToday = 0, correctToday = 0, accuracyTodayPercent = 0, streakDays = 0,
    )
    assertEquals(emptyList<ReviewStats.Achievement>(), ReviewStats.achievements(none))

    val all = ReviewStats.Stats(
      totalCards = 10, dueNow = 1, masteredCards = 10, lifetimeReviews = 1,
      reviewedToday = 3, correctToday = 3, accuracyTodayPercent = 100, streakDays = 3,
    )
    assertEquals(
      listOf(
        ReviewStats.Achievement.FIRST_CARD,
        ReviewStats.Achievement.CARD_COLLECTOR,
        ReviewStats.Achievement.FIRST_REVIEW,
        ReviewStats.Achievement.STREAK_THREE,
        ReviewStats.Achievement.MASTERED_TEN,
      ),
      ReviewStats.achievements(all),
    )

    val partial = ReviewStats.Stats(
      totalCards = 1, dueNow = 0, masteredCards = 0, lifetimeReviews = 1,
      reviewedToday = 0, correctToday = 0, accuracyTodayPercent = 0, streakDays = 2,
    )
    assertEquals(
      listOf(ReviewStats.Achievement.FIRST_CARD, ReviewStats.Achievement.FIRST_REVIEW),
      ReviewStats.achievements(partial),
    )
  }
}

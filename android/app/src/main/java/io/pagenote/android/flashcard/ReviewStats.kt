package io.pagenote.android.flashcard

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 复习统计与成就（移植批次 11 / 差距清单 #20）：纯计算层，输入为仓储查询结果，
 * 时区由调用方注入（UI 用系统时区，测试用固定时区）。
 */
object ReviewStats {

  /** 卡片统计输入（来自 flashcards 表的投影）。 */
  data class CardInput(
    val dueAt: Long,
    val intervalDays: Int,
  )

  /** 复习记录输入（来自 flashcard_reviews 表的投影）。 */
  data class ReviewInput(
    val reviewedAt: Long,
    val outcome: String,
  )

  /** 统计汇总。 */
  data class Stats(
    val totalCards: Int,
    val dueNow: Int,
    val masteredCards: Int,
    val lifetimeReviews: Int,
    val reviewedToday: Int,
    val correctToday: Int,
    val accuracyTodayPercent: Int,
    val streakDays: Int,
  )

  /** 成就判定结果（文案由 UI 层映射资源）。 */
  enum class Achievement {
    FIRST_CARD,
    CARD_COLLECTOR,
    FIRST_REVIEW,
    STREAK_THREE,
    MASTERED_TEN,
  }

  /** 掌握判定：当前间隔达到 [MASTERED_INTERVAL_DAYS] 天及以上。 */
  const val MASTERED_INTERVAL_DAYS = 21

  fun compute(
    cards: List<CardInput>,
    reviews: List<ReviewInput>,
    now: Long,
    zone: ZoneId,
  ): Stats {
    val dueNow = cards.count { it.dueAt <= now }
    val mastered = cards.count { it.intervalDays >= MASTERED_INTERVAL_DAYS }
    val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
    val todayReviews = reviews.filter { LocalDate.ofInstant(Instant.ofEpochMilli(it.reviewedAt), zone) == today }
    val correctToday = todayReviews.count { it.outcome == ReviewScheduler.ReviewOutcome.GOOD.name || it.outcome == ReviewScheduler.ReviewOutcome.EASY.name }
    val accuracy = if (todayReviews.isEmpty()) 0 else (correctToday * 100.0 / todayReviews.size).toInt()
    return Stats(
      totalCards = cards.size,
      dueNow = dueNow,
      masteredCards = mastered,
      lifetimeReviews = reviews.size,
      reviewedToday = todayReviews.size,
      correctToday = correctToday,
      accuracyTodayPercent = accuracy,
      streakDays = streakDays(reviews, today, zone),
    )
  }

  /** 连续天数：以 [today] 为锚向过去数「每天都有复习」的最长连续段；今天未复习不归零（从昨天起算）。 */
  fun streakDays(reviews: List<ReviewInput>, today: LocalDate, zone: ZoneId): Int {
    if (reviews.isEmpty()) return 0
    val days = reviews.mapTo(sortedSetOf()) {
      LocalDate.ofInstant(Instant.ofEpochMilli(it.reviewedAt), zone)
    }
    var anchor = if (today in days) today else today.minusDays(1)
    var streak = 0
    while (anchor in days) {
      streak++
      anchor = anchor.minusDays(1)
    }
    return streak
  }

  fun achievements(stats: Stats): List<Achievement> = buildList {
    if (stats.totalCards >= 1) add(Achievement.FIRST_CARD)
    if (stats.totalCards >= 10) add(Achievement.CARD_COLLECTOR)
    if (stats.lifetimeReviews >= 1) add(Achievement.FIRST_REVIEW)
    if (stats.streakDays >= 3) add(Achievement.STREAK_THREE)
    if (stats.masteredCards >= 10) add(Achievement.MASTERED_TEN)
  }
}

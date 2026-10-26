package io.pagenote.android.flashcard

import org.junit.Assert.assertEquals
import org.junit.Test

/** 复习排程（移植批次 11）：SM-2 简化版的间隔/ease/到期时间。 */
class ReviewSchedulerTest {
  private val now = 1_000_000_000_000L
  private val day = 24L * 60 * 60 * 1000

  @Test
  fun `initial schedule is due immediately with default ease`() {
    val schedule = ReviewScheduler.initial(now)
    assertEquals(now, schedule.dueAt)
    assertEquals(0, schedule.intervalDays)
    assertEquals(ReviewScheduler.DEFAULT_EASE, schedule.easeX1000)
    assertEquals(0, schedule.reps)
    assertEquals(0, schedule.lapses)
  }

  @Test
  fun `again relearns in ten minutes and drops ease`() {
    val next = ReviewScheduler.schedule(ReviewScheduler.initial(now), ReviewScheduler.ReviewOutcome.AGAIN, now)
    assertEquals(now + 10 * 60 * 1000, next.dueAt)
    assertEquals(0, next.intervalDays)
    assertEquals(ReviewScheduler.DEFAULT_EASE - 200, next.easeX1000)
    assertEquals(1, next.reps)
    assertEquals(1, next.lapses)
  }

  @Test
  fun `good on new card starts at one day`() {
    val next = ReviewScheduler.schedule(ReviewScheduler.initial(now), ReviewScheduler.ReviewOutcome.GOOD, now)
    assertEquals(now + day, next.dueAt)
    assertEquals(1, next.intervalDays)
    assertEquals(ReviewScheduler.DEFAULT_EASE, next.easeX1000)
    assertEquals(0, next.lapses)
  }

  @Test
  fun `easy on new card starts at three days`() {
    val next = ReviewScheduler.schedule(ReviewScheduler.initial(now), ReviewScheduler.ReviewOutcome.EASY, now)
    assertEquals(now + 3 * day, next.dueAt)
    assertEquals(3, next.intervalDays)
    assertEquals(ReviewScheduler.DEFAULT_EASE + 50, next.easeX1000)
  }

  @Test
  fun `hard scales interval by 1_2 and keeps at least one day`() {
    val current = ReviewScheduler.Schedule(now, 1, ReviewScheduler.DEFAULT_EASE, 1, 0)
    val next = ReviewScheduler.schedule(current, ReviewScheduler.ReviewOutcome.HARD, now)
    assertEquals(1, next.intervalDays)
    assertEquals(ReviewScheduler.DEFAULT_EASE - 50, next.easeX1000)
    assertEquals(now + day, next.dueAt)
  }

  @Test
  fun `good multiplies interval by ease`() {
    // 10 天 × 2.5 = 25 天。
    val current = ReviewScheduler.Schedule(now, 10, 2500, 3, 0)
    val next = ReviewScheduler.schedule(current, ReviewScheduler.ReviewOutcome.GOOD, now)
    assertEquals(25, next.intervalDays)
    assertEquals(now + 25 * day, next.dueAt)
  }

  @Test
  fun `easy multiplies interval by ease times bonus`() {
    // 10 天 × 2.5 × 1.3 = 32.5 → 32 天。
    val current = ReviewScheduler.Schedule(now, 10, 2500, 3, 0)
    val next = ReviewScheduler.schedule(current, ReviewScheduler.ReviewOutcome.EASY, now)
    assertEquals(32, next.intervalDays)
  }

  @Test
  fun `ease is clamped to bounds and intervals to max`() {
    val minEase = ReviewScheduler.Schedule(now, 10, ReviewScheduler.MIN_EASE + 50, 1, 0)
    val floored = ReviewScheduler.schedule(minEase, ReviewScheduler.ReviewOutcome.AGAIN, now)
    assertEquals(ReviewScheduler.MIN_EASE, floored.easeX1000)

    val maxEase = ReviewScheduler.Schedule(now, ReviewScheduler.MAX_INTERVAL_DAYS, ReviewScheduler.MAX_EASE - 50, 5, 0)
    val ceilEase = ReviewScheduler.schedule(maxEase, ReviewScheduler.ReviewOutcome.EASY, now)
    assertEquals(ReviewScheduler.MAX_EASE, ceilEase.easeX1000)

    val longStreak = ReviewScheduler.Schedule(now, ReviewScheduler.MAX_INTERVAL_DAYS, ReviewScheduler.MAX_EASE, 10, 0)
    val capped = ReviewScheduler.schedule(longStreak, ReviewScheduler.ReviewOutcome.GOOD, now)
    assertEquals(ReviewScheduler.MAX_INTERVAL_DAYS, capped.intervalDays)
  }
}

package io.pagenote.android.flashcard

/**
 * 复习排程（移植批次 11 / 差距清单 #20）：SM-2 的简化版。
 *
 * - AGAIN（未记住）：10 分钟后重学，间隔归零，ease −200（下限 [MIN_EASE]），lapses +1；
 * - HARD（勉强）：间隔 ×1.2（至少 1 天），ease −50；
 * - GOOD（记住）：新卡 1 天起步，之后间隔 ×(ease/1000)；
 * - EASY（轻松）：新卡 3 天起步，之后间隔 ×(ease/1000)×1.3，ease +50（上限 [MAX_EASE]）。
 * 间隔上限 [MAX_INTERVAL_DAYS] 天；纯函数，不感知时钟。
 */
object ReviewScheduler {
  const val MIN_EASE = 1300
  const val MAX_EASE = 3500
  const val DEFAULT_EASE = 2500
  const val MAX_INTERVAL_DAYS = 365
  const val RELEARN_DELAY_MINUTES = 10L
  const val MIN_INTERVAL_DAYS = 1

  private const val DAY_MS = 24L * 60 * 60 * 1000
  private const val HARD_FACTOR = 1.2
  private const val EASY_BONUS = 1.3

  enum class ReviewOutcome { AGAIN, HARD, GOOD, EASY }

  /** 排程状态快照（与 FlashcardEntity 的排程字段一一对应）。 */
  data class Schedule(
    val dueAt: Long,
    val intervalDays: Int,
    val easeX1000: Int,
    val reps: Int,
    val lapses: Int,
  )

  /** 新卡初始排程：立即到期、零间隔、默认 ease。 */
  fun initial(now: Long): Schedule = Schedule(
    dueAt = now,
    intervalDays = 0,
    easeX1000 = DEFAULT_EASE,
    reps = 0,
    lapses = 0,
  )

  fun schedule(current: Schedule, outcome: ReviewOutcome, now: Long): Schedule {
    val eased = when (outcome) {
      ReviewOutcome.AGAIN -> current.easeX1000 - 200
      ReviewOutcome.HARD -> current.easeX1000 - 50
      ReviewOutcome.GOOD -> current.easeX1000
      ReviewOutcome.EASY -> current.easeX1000 + 50
    }.coerceIn(MIN_EASE, MAX_EASE)
    val intervalDays = when (outcome) {
      ReviewOutcome.AGAIN -> 0
      ReviewOutcome.HARD -> scaleInterval(current.intervalDays, HARD_FACTOR)
      ReviewOutcome.GOOD ->
        if (current.intervalDays <= 0) MIN_INTERVAL_DAYS
        else scaleInterval(current.intervalDays, current.easeX1000 / 1000.0)
      ReviewOutcome.EASY ->
        if (current.intervalDays <= 0) 3
        else scaleInterval(current.intervalDays, current.easeX1000 / 1000.0 * EASY_BONUS)
    }
    val dueAt = when (outcome) {
      ReviewOutcome.AGAIN -> now + RELEARN_DELAY_MINUTES * 60 * 1000
      else -> now + intervalDays * DAY_MS
    }
    return Schedule(
      dueAt = dueAt,
      intervalDays = intervalDays,
      easeX1000 = eased,
      reps = current.reps + 1,
      lapses = current.lapses + if (outcome == ReviewOutcome.AGAIN) 1 else 0,
    )
  }

  private fun scaleInterval(currentDays: Int, factor: Double): Int {
    val scaled = (currentDays * factor).toInt().coerceAtLeast(MIN_INTERVAL_DAYS)
    return scaled.coerceAtMost(MAX_INTERVAL_DAYS)
  }
}

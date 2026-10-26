package io.pagenote.android

/**
 * 悬浮计时器纯策略（移植差距清单 #24）：时长档位、时钟格式与
 * tick 归零判定在此决定；服务 UI 只消费 [FloatingTimerEngine] 状态。
 */
object FloatingTimerPolicy {
  /** 分钟档位（悬浮面板按钮）。 */
  val MINUTE_PRESETS = listOf(1, 5, 10, 25)

  /** 默认档位（番茄钟）。 */
  const val DEFAULT_PRESET_MINUTES = 25

  /** tick 周期（毫秒）。 */
  const val TICK_INTERVAL_MS = 250L

  fun durationMillis(minutes: Int): Long {
    require(minutes > 0) { "bad timer minutes" }
    return minutes * 60_000L
  }

  /**
   * 毫秒 → 时钟文本：负值钳为 0；不足 1 小时为 `MM:SS`，达到 1 小时
   * 为 `H:MM:SS`（用于悬浮面板与完成通知）。
   */
  fun format(millis: Long): String {
    val safe = millis.coerceAtLeast(0L)
    val totalSeconds = safe / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
      String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
      String.format("%02d:%02d", minutes, seconds)
    }
  }

  /** tick 后的剩余时间；跨过 0 时钳为 0（完成判定交给引擎）。 */
  fun remainingAfterTick(remainingMillis: Long, elapsedMillis: Long): Long {
    require(elapsedMillis >= 0L) { "bad tick elapsed" }
    return (remainingMillis - elapsedMillis).coerceAtLeast(0L)
  }
}

/**
 * 计时器状态机（纯 Kotlin，时钟注入可测）：start/pause/resume/reset/tick，
 * 归零恰好上报一次 [State.finished]。
 */
class FloatingTimerEngine(private val clock: () -> Long = System::currentTimeMillis) {

  data class State(
    val running: Boolean,
    val durationMillis: Long,
    val remainingMillis: Long,
    val finished: Boolean,
  )

  private var durationMillis = 0L
  private var startedAtMillis = 0L
  private var pausedRemainingMillis = 0L
  private var running = false
  private var finished = false

  val state: State
    get() = State(
      running = running,
      durationMillis = durationMillis,
      remainingMillis = currentRemaining(),
      finished = finished,
    )

  /** 开始新倒计时（replace 任何进行中的计时）。 */
  fun start(durationMillis: Long): State {
    require(durationMillis > 0L) { "bad timer duration" }
    this.durationMillis = durationMillis
    startedAtMillis = clock()
    pausedRemainingMillis = durationMillis
    running = true
    finished = false
    return state
  }

  fun pause(): State {
    if (running && !finished) {
      pausedRemainingMillis = currentRemaining()
      running = false
    }
    return state
  }

  fun resume(): State {
    if (!running && !finished && pausedRemainingMillis > 0L && durationMillis > 0L) {
      startedAtMillis = clock()
      running = true
    }
    return state
  }

  fun reset(): State {
    running = false
    finished = false
    durationMillis = 0L
    pausedRemainingMillis = 0L
    return state
  }

  /**
   * 服务节拍调用 [FloatingTimerPolicy.TICK_INTERVAL_MS] 一次：推进剩余
   * 时间；跨过 0 时置为完成并停止（[State.finished] 恰好为 true 一次，
   * 后续 tick 保持完成态）。
   */
  fun tick(): State {
    if (!running || finished) return state
    val elapsed = clock() - startedAtMillis
    val remaining = FloatingTimerPolicy.remainingAfterTick(pausedRemainingMillis, elapsed)
    if (remaining <= 0L) {
      pausedRemainingMillis = 0L
      running = false
      finished = true
    }
    return state
  }

  private fun currentRemaining(): Long = when {
    durationMillis <= 0L -> 0L
    running -> FloatingTimerPolicy.remainingAfterTick(pausedRemainingMillis, clock() - startedAtMillis)
    else -> pausedRemainingMillis
  }
}

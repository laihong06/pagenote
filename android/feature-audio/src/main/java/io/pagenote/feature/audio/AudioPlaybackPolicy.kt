package io.pagenote.feature.audio

/**
 * 回放纯策略（移植批次 8）：进度/跳转/时钟显示的判定，MediaPlayer 真实
 * 调用在 app 层薄封装里按本类约束执行。
 */
object AudioPlaybackPolicy {
  enum class Phase { IDLE, PLAYING, PAUSED }

  /** 播放/暂停切换：IDLE 无法切换（调用方先显式 start）。 */
  fun toggle(phase: Phase, hasSource: Boolean): Phase = when {
    !hasSource -> phase
    phase == Phase.PLAYING -> Phase.PAUSED
    phase == Phase.PAUSED -> Phase.PLAYING
    else -> phase
  }

  /** 播放自然结束：回 IDLE、进度归零。 */
  fun onCompleted(phase: Phase): Phase = Phase.IDLE

  /** 跳转夹取：目标位置夹进 0..duration（duration 未知时只做非负夹取）。 */
  fun seekTarget(requestedMs: Long, durationMs: Long?): Long {
    val lower = requestedMs.coerceAtLeast(0L)
    return durationMs?.takeIf { it > 0 }?.let { lower.coerceAtMost(it) } ?: lower
  }

  /** 进度条比例（0..1）；时长未知返回 0。 */
  fun progressFraction(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0) return 0f
    return (positionMs.coerceAtLeast(0L).toDouble() / durationMs.toDouble())
      .coerceIn(0.0, 1.0).toFloat()
  }

  /** 回听跳转：转写片段的 seek 目标（片段起点；未知时长按非负处理）。 */
  fun seekTargetForSegment(segment: TranscriptSegment, durationMs: Long?): Long =
    seekTarget(segment.startMs, durationMs)

  /** 时钟标签：mm:ss（1 小时以上 h:mm:ss）。 */
  fun clockLabel(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
      "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
      "%02d:%02d".format(minutes, seconds)
    }
  }

  /** UI 进度轮询节拍（毫秒）。 */
  const val TICK_MS = 200L
}

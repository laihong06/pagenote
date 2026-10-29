package io.pagenote.feature.audio

/**
 * 录音流程纯策略（移植批次 8）：状态机与显示归一化，MediaRecorder 的真实
 * 调用在 app 层薄封装里按本类约束执行。
 */
object AudioRecordingPolicy {
  enum class Phase { IDLE, PREPARING, RECORDING }

  /**
   * 是否允许开始录音：空闲、已授权、有项目写位、配额允许。phase 与权限的
   * 组合判定集中在这一处，调用方不再各自拼写条件。
   */
  fun canStart(
    phase: Phase,
    permissionGranted: Boolean,
    quotaAllowed: Boolean,
  ): Boolean = phase == Phase.IDLE && permissionGranted && quotaAllowed

  /** 开始后进入 PREPARING；MediaRecorder.prepare 成功才转 RECORDING。 */
  fun onPrepared(phase: Phase, prepared: Boolean): Phase = when {
    phase != Phase.PREPARING -> phase
    prepared -> Phase.RECORDING
    else -> Phase.IDLE
  }

  /** 停止/取消后回到 IDLE（取消时调用方负责删除音频文件）。 */
  fun onStopped(phase: Phase): Phase =
    if (phase == Phase.RECORDING || phase == Phase.PREPARING) Phase.IDLE else phase

  /** 录音计时标签：60 分钟内 mm:ss，以上 h:mm:ss。 */
  fun elapsedLabel(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs.coerceAtLeast(0L)) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
      "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
      "%02d:%02d".format(minutes, seconds)
    }
  }

  /**
   * MediaRecorder.maxAmplitude（0..32767）→ 0..1 电平条值。对数刻度：人声
   * 动态范围大，线性刻度几乎总是贴底。输入越界按 0/1 处理。
   */
  fun amplitudeLevel(rawAmplitude: Int): Float {
    if (rawAmplitude <= 0) return 0f
    val normalized = rawAmplitude.coerceAtMost(32767).toDouble() / 32767.0
    val db = 20.0 * kotlin.math.log10(normalized)
    // -60 dB（≈1/1000 满幅）贴 0，0 dB 贴 1。
    return ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
  }

  /** UI 刷新节拍（毫秒）：计时与电平共用。 */
  const val TICK_MS = 200L
}

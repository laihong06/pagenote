package io.pagenote.feature.audio

/**
 * 录音存储限额（移植批次 8）。配额覆盖 `<project>/audio/` 全部录音二进制：
 * 录制开始前按"剩余预算 → MediaRecorder maxFileSize/maxDuration"双闸生效，
 * 转写不产生音频字节，不做二次计量。
 */
object AudioQuotaPolicy {
  /** 配额设置值域（MB）与缺省值。 */
  const val MIN_MB = 50
  const val MAX_MB = 4096
  const val DEFAULT_MB = 200

  /** 录音编码假设：AAC-LC 32kbps 单声道 → 4000 字节/秒（保守估计值）。 */
  const val RECORDING_BYTES_PER_SECOND = 4_000L

  /** 单条录音的绝对字节上限（60 分钟 × 4000 B/s，兜底防御估计失真）。 */
  const val MAX_RECORDING_BYTES = 60L * 60L * RECORDING_BYTES_PER_SECOND

  fun sanitizeMb(mb: Int): Int = mb.takeIf { it in MIN_MB..MAX_MB } ?: DEFAULT_MB

  fun validateMb(mb: Int) {
    require(mb in MIN_MB..MAX_MB) { "bad audio quota: $mb" }
  }

  fun quotaBytes(mb: Int): Long = sanitizeMb(mb).toLong() * 1024L * 1024L

  /** 剩余预算字节（已用超出配额时为 0，不为负）。 */
  fun remainingBytes(quotaMb: Int, usedBytes: Long): Long {
    require(usedBytes >= 0) { "negative used bytes" }
    return (quotaBytes(quotaMb) - usedBytes).coerceAtLeast(0L)
  }

  /** 单条新录音允许的最大字节（剩余预算与单条硬上限取小）。 */
  fun maxRecordingBytes(quotaMb: Int, usedBytes: Long): Long =
    remainingBytes(quotaMb, usedBytes).coerceAtMost(MAX_RECORDING_BYTES)

  /** 按假设码率把字节预算折算成最长可录时长（毫秒）。 */
  fun maxRecordingDurationMs(quotaMb: Int, usedBytes: Long): Long =
    maxRecordingBytes(quotaMb, usedBytes) * 1000L / RECORDING_BYTES_PER_SECOND

  /**
   * 录制开始前的预算判定：剩余预算不足以录满最短有效录音
   * （[AudioCapsulePolicy.MIN_DURATION_MS]）时拒绝开始，避免录完即弃的
   * 浪费写盘。
   */
  fun canStartRecording(quotaMb: Int, usedBytes: Long): Boolean {
    val needed = AudioCapsulePolicy.MIN_DURATION_MS * RECORDING_BYTES_PER_SECOND / 1000L
    return maxRecordingBytes(quotaMb, usedBytes) >= needed
  }

  /** 录音文件名是否计入配额（只统计 audio/ 下的 m4a）。 */
  fun countsTowardUsage(assetName: String): Boolean = AudioCapsulePolicy.isValidAssetName(assetName)
}

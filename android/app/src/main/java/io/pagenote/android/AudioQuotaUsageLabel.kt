package io.pagenote.android

/** 配额用量显示（移植批次 8）：MB 舍入 + 超 1024MB 折 GB。 */
internal object AudioQuotaUsageLabel {
  fun formatMb(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
      String.format("%.1f GB", mb / 1024.0)
    } else {
      String.format("%.0f MB", mb)
    }
  }
}

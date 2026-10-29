package io.pagenote.feature.audio

/**
 * 转写文本格式化（移植批次 8）：片段 → 全文 / 带时间戳行 / 列表预览。
 * 全文按片段顺序单换行拼接；带时间戳行供转写列表展示与回听跳转对齐。
 */
object AudioTranscriptFormatter {
  const val PREVIEW_MAX_CODE_POINTS = 80

  /** 全文：片段文本按序单换行拼接（去首尾空白）。 */
  fun transcriptText(segments: List<TranscriptSegment>): String =
    segments.map { it.text.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

  /** 带时间戳行：`[mm:ss] 文本`（1 小时以上 h:mm:ss）。 */
  fun timedLines(segments: List<TranscriptSegment>): List<String> =
    segments.map { segment -> "[${AudioPlaybackPolicy.clockLabel(segment.startMs)}] ${segment.text.trim()}" }

  fun timedTranscript(segments: List<TranscriptSegment>): String =
    timedLines(segments).joinToString("\n")

  /** 片段所在行的回听目标（列表行点击 → seek 起点）。 */
  fun seekTargetMs(segment: TranscriptSegment): Long =
    AudioPlaybackPolicy.seekTargetForSegment(segment, null)

  /** 列表预览：截断到 [PREVIEW_MAX_CODE_POINTS] 码点并补省略号。 */
  fun preview(text: String, maxCodePoints: Int = PREVIEW_MAX_CODE_POINTS): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ""
    if (trimmed.codePointCount(0, trimmed.length) <= maxCodePoints) return trimmed
    val end = trimmed.offsetByCodePoints(0, maxCodePoints)
    return trimmed.substring(0, end) + "…"
  }

  /** 请求语言/模型的展示后缀（转写列表元信息行）。 */
  fun metaLabel(language: String?, model: String?): String? {
    val languageLabel = when (language) {
      AsrLanguage.CHINESE -> "zh"
      AsrLanguage.ENGLISH -> "en"
      else -> null
    } ?: return null
    return model?.let { "$languageLabel · $it" } ?: languageLabel
  }
}

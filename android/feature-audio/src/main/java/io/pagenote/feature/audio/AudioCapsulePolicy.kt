package io.pagenote.feature.audio

import kotlin.math.abs

/**
 * 音频胶囊放置与边界策略（移植批次 8）。坐标契约与 annotations.json 一致：
 * width-1000 文档空间（x ∈ 0..1000，y 按页纵横比可超 1000，安全上限
 * [MAX_COORDINATE]）。胶囊显示框固定 [CAPSULE_WIDTH]×[CAPSULE_HEIGHT] 文档
 * 单位，锚点是显示框左上角，放置时夹取保证胶囊完整落在页面内。
 */
object AudioCapsulePolicy {
  const val MAX_COORDINATE = 1_000_000.0

  /** 胶囊显示框尺寸（width-1000 文档单位；120 ≈ 页宽 12%）。 */
  const val CAPSULE_WIDTH = 120.0
  const val CAPSULE_HEIGHT = 56.0

  /** 命中判定额外容差（文档单位）。 */
  const val HIT_TOLERANCE = 12.0

  /** 单页胶囊上限（对齐批注项密度约束的音频侧收紧值）。 */
  const val MAX_PER_PAGE = 50

  /** 有效录音时长下限：更短的录音视为误触，丢弃文件不落胶囊。 */
  const val MIN_DURATION_MS = 500L

  /** 单条录音硬上限（60 分钟）。 */
  const val MAX_DURATION_MS = 60L * 60L * 1000L

  /** 全库胶囊上限（与 codec 上限一致）。 */
  const val MAX_TOTAL = AudioCapsuleCodec.MAX_CAPSULES

  val ID_PATTERN = Regex("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")

  /** 录音资产名：`<uuid36>.m4a`（uuid 带连字符共 36 字符，AAC/MPEG-4 容器）。 */
  val ASSET_PATTERN = Regex("^[a-fA-F0-9-]{36}\\.m4a$")

  fun isValidAssetName(asset: String): Boolean = ASSET_PATTERN.matches(asset)

  fun isValidId(id: String): Boolean = ID_PATTERN.matches(id)

  /**
   * 放置夹取：锚点（显示框左上角）夹进页面可视范围（x ∈ 0..1000-width，
   * y ≥ 0）。返回 null 表示输入非法（非有限值或超安全上限）。
   */
  fun clampAnchor(anchorX: Double, anchorY: Double): Pair<Double, Double>? {
    if (!anchorX.isFinite() || !anchorY.isFinite()) return null
    if (abs(anchorX) > MAX_COORDINATE || abs(anchorY) > MAX_COORDINATE) return null
    val maxX = 1000.0 - CAPSULE_WIDTH
    return anchorX.coerceIn(0.0, maxX.coerceAtLeast(0.0)) to anchorY.coerceAtLeast(0.0)
  }

  /** 单页容量守卫。 */
  fun canPlaceOnPage(existingOnPage: Int): Boolean = existingOnPage < MAX_PER_PAGE

  /** 全库容量守卫。 */
  fun canPlaceTotal(existingTotal: Int): Boolean = existingTotal < MAX_TOTAL

  /**
   * 录音停止后的保留判定：低于 [MIN_DURATION_MS] 的录音丢弃（返回 false）。
   */
  fun shouldKeepRecording(durationMs: Long): Boolean = durationMs in MIN_DURATION_MS..MAX_DURATION_MS

  /** 录音期间 MediaRecorder 的 maxDuration 设置值（毫秒）。 */
  fun recorderMaxDurationMs(): Long = MAX_DURATION_MS

  /**
   * 转写产物回填：把引擎产出的片段/文本装进 [status] 状态的新胶囊。
   * 写侧严格：片段非法（乱序/空文本/超限）时返回 null，由调用方折叠为
   * FAILED 状态（不落脏数据）。
   */
  fun withTranscript(
    capsule: AudioCapsule,
    status: AudioTranscriptStatus,
    transcript: String?,
    segments: List<TranscriptSegment>,
    language: String?,
    model: String?,
  ): AudioCapsule? {
    val next = capsule.copy(
      transcript = transcript?.takeIf { it.isNotEmpty() },
      transcriptStatus = status,
      segments = segments,
      language = language,
      model = model,
    )
    return try {
      AudioCapsuleCodec.validate(listOf(next))
      next
    } catch (_: IllegalArgumentException) {
      null
    }
  }
}

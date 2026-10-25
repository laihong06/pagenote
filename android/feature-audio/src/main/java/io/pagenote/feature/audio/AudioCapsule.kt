package io.pagenote.feature.audio

/**
 * 音频胶囊数据模型（移植批次 8）。对应参考实现 touchnotes 的画布录音胶囊：
 * 一次录音在画布上落一个可点按回听的胶囊对象，携带录音文件、时长与
 * 离线转写产物。
 *
 * 存储决策（对齐批次 5 materials.json 先例）：胶囊清单是 **Android 侧独立
 * 文件** `<project>/audio.json`（项目根，与 annotations.json 平级），录音
 * 二进制落在 `<project>/audio/<uuid36>.m4a`。annotations.json 的 type 枚举
 * 保持桌面 9 类不变（canvas-schema-decision D1：Android 不单方面新增
 * type 值），桌面端不读取也不改写 audio.json/audio/（未知项目文件原样
 * 保留），因此音频胶囊完全不进 annotations.json。
 */
enum class AudioTranscriptStatus(val key: String) {
  /** 未转写（自动转写关闭或运行时不可用时的待手动状态）。 */
  NONE("none"),

  /** 转写排队中（录制完成后的自动转写任务尚未跑完）。 */
  PENDING("pending"),

  /** 转写成功（transcript/segments 可用；空音频时 transcript 为空串）。 */
  OK("ok"),

  /** 转写失败（引擎缺失/模型缺失/解码失败；可从转写列表手动重试）。 */
  FAILED("failed");

  companion object {
    fun fromKey(key: String?): AudioTranscriptStatus? =
      key?.let { value -> AudioTranscriptStatus.values().firstOrNull { it.key == value } }
  }
}

/** 一段带绝对时间戳的转写片段（回听跳转的目标锚点）。 */
data class TranscriptSegment(
  /** 段起始毫秒（相对录音开头，非负）。 */
  val startMs: Long,
  /** 段结束毫秒（> startMs，可超出实际时长一帧内容忍）。 */
  val endMs: Long,
  /** 段文本（非空）。 */
  val text: String,
)

data class AudioCapsule(
  /** 稳定 id（uuid36，设置持久化与索引定位用）。 */
  val id: String,
  /** 1 起页号（对应阅读器 document 面页序）。 */
  val page: Int,
  /** 锚点 x（width-1000 文档单位，胶囊显示框左上角）。 */
  val anchorX: Double,
  /** 锚点 y（width-1000 文档单位，随页纵横比可超 1000）。 */
  val anchorY: Double,
  /** 录音资产名（`<uuid36>.m4a`，位于 `<project>/audio/`）。 */
  val asset: String,
  /** 录音时长毫秒（> 0）。 */
  val durationMs: Long,
  /** 创建时间（epoch 毫秒，列表排序与命中次序用）。 */
  val createdAtMillis: Long,
  /** 转写全文（状态非 OK 时可为 null；OK 允许空串=没有可识别语音）。 */
  val transcript: String?,
  /** 转写状态。 */
  val transcriptStatus: AudioTranscriptStatus,
  /** 转写片段（仅 OK 时非空；按 startMs 升序）。 */
  val segments: List<TranscriptSegment>,
  /** 转写所用语言 id（AsrLanguage 解析结果；未转写为 null）。 */
  val language: String? = null,
  /** 转写所用模型 id（AsrModelCatalog；未转写为 null）。 */
  val model: String? = null,
) {
  val hasTranscript: Boolean
    get() = transcriptStatus == AudioTranscriptStatus.OK && !transcript.isNullOrEmpty()
}

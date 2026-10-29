package io.pagenote.feature.audio

/**
 * 离线 ASR 模型目录（移植批次 8）。参考实现的闭源 native 识别引擎不可移植，
 * Android 端选型 **sherpa-onnx**（k2-fsa，Apache-2.0）：流式 Zipformer 转置
 * 识别器 + 离线 Paraformer（短音频回退）+ Silero VAD（分段）。
 *
 * 部署契约（对齐批次 6 pix2tex 的"资产契约先行、二进制不随仓库捆绑"姿态）：
 * 模型文件放置在运行时模型根目录（app 层解析为 `<filesDir>/models/asr/`），
 * 每个条目一个子目录，文件名按下表冻结。模型二进制经 k2-fsa 官方发布页
 * （github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models）获取后放入对应
 * 子目录；缺失时 [AsrRuntimeBinding] 报告不可用状态，UI 呈现可恢复提示，
 * 录音/回放链路不受影响。
 */
data class AsrComponentFiles(
  /** 相对模型根的子目录名。 */
  val directory: String,
  /** 子目录内必需文件名（冻结契约）。 */
  val files: List<String>,
)

data class AsrModelEntry(
  /** 稳定模型 id（设置持久化用）。 */
  val id: String,
  /** 显示名。 */
  val displayName: String,
  /** 模型与运行时许可（sherpa-onnx Apache-2.0；Silero VAD MIT）。 */
  val license: String,
  /** 支持语言 id（见 [AsrLanguage]）。 */
  val languages: Set<String>,
  /** 流式识别组件（null = 该捆绑不含流式引擎）。 */
  val streaming: AsrComponentFiles?,
  /** 离线短音频组件（null = 短音频回退走流式引擎）。 */
  val offline: AsrComponentFiles?,
  /** VAD 组件（分段必需）。 */
  val vad: AsrComponentFiles,
  /** 体积估计（MB，量级参考，UI 提示用）。 */
  val sizeMbEstimate: Int,
)

object AsrModelCatalog {
  private const val STREAMING_DIR = "streaming-zipformer-bilingual-zh-en-2023-02-20"
  private const val OFFLINE_DIR = "paraformer-zh-2023-09-14"
  private const val VAD_DIR = "silero-vad"

  /** 默认捆绑：流式 Zipformer（zh+en 双语，int8）+ 离线 Paraformer + Silero VAD。 */
  val ZIPFORMER_PARAFORMER_ZH_EN = AsrModelEntry(
    id = "zipformer-paraformer-zh-en",
    displayName = "流式 Zipformer + Paraformer（中英）",
    license = "Apache-2.0 (VAD: MIT)",
    languages = setOf(AsrLanguage.CHINESE, AsrLanguage.ENGLISH),
    streaming = AsrComponentFiles(
      directory = STREAMING_DIR,
      files = listOf(
        "tokens.txt",
        "encoder-epoch-99-avg-1.onnx",
        "decoder-epoch-99-avg-1.onnx",
        "joiner-epoch-99-avg-1.onnx",
      ),
    ),
    offline = AsrComponentFiles(
      directory = OFFLINE_DIR,
      files = listOf("tokens.txt", "model.int8.onnx"),
    ),
    vad = AsrComponentFiles(directory = VAD_DIR, files = listOf("silero_vad.onnx")),
    sizeMbEstimate = 180,
  )

  /** 轻量捆绑：只带流式 Zipformer（zh+en 双语，int8）+ VAD，无离线组件。 */
  val ZIPFORMER_STREAMING_ZH_EN = AsrModelEntry(
    id = "zipformer-streaming-zh-en",
    displayName = "流式 Zipformer（中英，轻量）",
    license = "Apache-2.0 (VAD: MIT)",
    languages = setOf(AsrLanguage.CHINESE, AsrLanguage.ENGLISH),
    streaming = AsrComponentFiles(
      directory = STREAMING_DIR,
      files = listOf(
        "tokens.txt",
        "encoder-epoch-99-avg-1.onnx",
        "decoder-epoch-99-avg-1.onnx",
        "joiner-epoch-99-avg-1.onnx",
      ),
    ),
    offline = null,
    vad = AsrComponentFiles(directory = VAD_DIR, files = listOf("silero_vad.onnx")),
    sizeMbEstimate = 100,
  )

  /** 轻量捆绑：只带离线 Paraformer（中文）+ VAD，供低内存设备。 */
  val PARAFORMER_ZH = AsrModelEntry(
    id = "paraformer-zh",
    displayName = "离线 Paraformer（中文）",
    license = "Apache-2.0 (VAD: MIT)",
    languages = setOf(AsrLanguage.CHINESE),
    streaming = null,
    offline = AsrComponentFiles(
      directory = OFFLINE_DIR,
      files = listOf("tokens.txt", "model.int8.onnx"),
    ),
    vad = AsrComponentFiles(directory = VAD_DIR, files = listOf("silero_vad.onnx")),
    sizeMbEstimate = 80,
  )

  val DEFAULT_ID = ZIPFORMER_PARAFORMER_ZH_EN.id

  val ENTRIES: List<AsrModelEntry> = listOf(
    ZIPFORMER_PARAFORMER_ZH_EN,
    ZIPFORMER_STREAMING_ZH_EN,
    PARAFORMER_ZH,
  )

  fun byId(id: String): AsrModelEntry? = ENTRIES.firstOrNull { it.id == id }

  fun isValidId(id: String): Boolean = byId(id) != null

  /** 设置值 → 条目；未知 id 回落默认捆绑（回落不失败）。 */
  fun resolve(id: String): AsrModelEntry = byId(id) ?: ZIPFORMER_PARAFORMER_ZH_EN

  /**
   * 设置语言 → 承载条目：优先支持该语言的条目（按 [ENTRIES] 顺序），
   * 无专用条目回落默认捆绑（与批次 6 OcrModelCatalog.resolveForLanguage
   * 同构；默认捆绑 zh/en 双语，回落不降低能力）。
   */
  fun resolveForLanguage(language: String): AsrModelEntry {
    val normalized = language.takeIf(AsrLanguage.SETTING_KEYS::contains) ?: AsrLanguage.SYSTEM
    if (normalized == AsrLanguage.SYSTEM) return ZIPFORMER_PARAFORMER_ZH_EN
    return ENTRIES.firstOrNull { normalized in it.languages } ?: ZIPFORMER_PARAFORMER_ZH_EN
  }
}

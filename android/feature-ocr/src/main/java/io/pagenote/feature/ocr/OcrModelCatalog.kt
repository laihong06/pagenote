package io.pagenote.feature.ocr

/**
 * 文本 OCR 模型目录与识别语言设置（移植批次 6）。
 *
 * 闭源 native 手写引擎不可移植，Android 端以开源 PaddleOCR ONNX 管线
 * （[OfflineOcrEngine]）替代。本目录把"识别语言/模型"设置解析成引擎可用的
 * 模型条目：条目携带资产路径与支持语言；当前随包发布的只有 zh+en 混合
 * 识别的 PP-OCR 轻量模型（`models/det|rec/`），未来新增语言专用模型时只需
 * 在 [ENTRIES] 追加条目，不改变设置与引擎接口。
 *
 * 语言 id 独立于模型 id：设置保存的是"想要的语言"，[resolveForLanguage]
 * 解析为实际承载模型；无专用模型的语言回落到默认模型（PP-OCR 混合模型
 * 对 zh/en 均可识别，回落不降低能力时静默；否则调用方在 UI 提示）。
 */
data class OcrModelEntry(
  /** 稳定模型 id（设置持久化用）。 */
  val id: String,
  /** 支持的识别语言 id 集合（见 [OcrLanguage]）。 */
  val languages: Set<String>,
  /** 检测模型资产路径（相对 assets）。 */
  val detectionModelPath: String,
  /** 识别模型资产路径（相对 assets）。 */
  val recognitionModelPath: String,
  /** 识别字典配置资产路径（相对 assets）。 */
  val recognitionConfigPath: String,
)

object OcrLanguage {
  const val CHINESE = "zh"
  const val ENGLISH = "en"

  /** 设置层接受的语言 id（含 "system"，解析时跟随 zh 默认）。 */
  val SETTING_KEYS = setOf("system", CHINESE, ENGLISH)
}

object OcrModelCatalog {
  /** 随包发布的 PP-OCR 轻量模型（zh+en 混合识别）。 */
  val PP_OCR_BUNDLED = OcrModelEntry(
    id = "ppocr-zh-en",
    languages = setOf(OcrLanguage.CHINESE, OcrLanguage.ENGLISH),
    detectionModelPath = "models/det/inference.onnx",
    recognitionModelPath = "models/rec/inference.onnx",
    recognitionConfigPath = "models/rec/inference.yml",
  )

  val DEFAULT: OcrModelEntry = PP_OCR_BUNDLED

  val ENTRIES: List<OcrModelEntry> = listOf(PP_OCR_BUNDLED)

  fun byId(id: String): OcrModelEntry? = ENTRIES.firstOrNull { it.id == id }

  /** 解析设置语言 → 承载模型；未知语言回落默认模型。 */
  fun resolveForLanguage(language: String): OcrModelEntry {
    val normalized = language.takeIf(OcrLanguage.SETTING_KEYS::contains) ?: "system"
    if (normalized == "system") return DEFAULT
    return ENTRIES.firstOrNull { normalized in it.languages } ?: DEFAULT
  }
}

/**
 * OCR 识别设置（识别语言；文本模型由语言解析，公式模型单独选择，见
 * [LatexModelCatalog]）。持久化在 app 层 ReaderSettings，本类只做值域约束。
 */
data class OcrRecognitionSettings(
  val language: String = "system",
  val latexModelId: String = "",
) {
  fun sanitized(): OcrRecognitionSettings = OcrRecognitionSettings(
    language = language.takeIf(OcrLanguage.SETTING_KEYS::contains) ?: "system",
    latexModelId = latexModelId.takeIf(LatexModelCatalog::isValidId) ?: LatexModelCatalog.DEFAULT_ID,
  )
}

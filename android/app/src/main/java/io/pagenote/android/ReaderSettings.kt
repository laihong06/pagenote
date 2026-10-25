package io.pagenote.android

import io.pagenote.feature.audio.AsrLanguage
import io.pagenote.feature.audio.AsrModelCatalog
import io.pagenote.feature.audio.AudioQuotaPolicy
import io.pagenote.feature.ocr.LatexModelCatalog
import io.pagenote.feature.ocr.OcrLanguage
import io.pagenote.feature.ocr.OcrModelCatalog
import io.pagenote.feature.pen.NoteBackground
import kotlin.math.roundToInt

data class ReaderSettings(
  val theme: String = "system",
  val language: String = "system",
  val leftPercent: Int = 42,
  val noteAnimation: Boolean = true,
  val noteFontSize: Int = 15,
  val allowInkOutsidePage: Boolean = false,
  val prepareAheadPages: Int = 3,
  val markdownTheme: String = "classic",
  val noteBackground: String = NoteBackground.PLAIN.key,
  /** 手写 OCR 识别语言（移植批次 6；由 [OcrModelCatalog] 解析承载模型）。 */
  val ocrLanguage: String = "system",
  /** 公式识别模型 id（移植批次 6；目录见 [LatexModelCatalog]）。 */
  val latexOcrModel: String = LatexModelCatalog.DEFAULT_ID,
  /** 搜索索引范围（移植批次 7）：页文本 / 笔记 / OCR / 手写转写。 */
  val searchIndexSource: Boolean = true,
  val searchIndexNotes: Boolean = true,
  val searchIndexOcr: Boolean = true,
  val searchIndexTranscripts: Boolean = true,
  /** ASR 模型捆绑 id（移植批次 8；目录见 [AsrModelCatalog]）。 */
  val asrModel: String = AsrModelCatalog.DEFAULT_ID,
  /** ASR 识别语言（移植批次 8；[AsrLanguage] 值域，system 跟随 locale）。 */
  val asrLanguage: String = AsrLanguage.SYSTEM,
  /** 录音完成后自动排队离线转写（移植批次 8）。 */
  val asrAutoTranscribe: Boolean = true,
  /** 项目录音存储配额（MB，移植批次 8；值域见 [AudioQuotaPolicy]）。 */
  val audioQuotaMb: Int = AudioQuotaPolicy.DEFAULT_MB,
) {
  /**
   * 批次 7：设置 → 搜索索引范围（原始值；sanitize 由 [ReaderSettingsPolicy]
   * 在加载路径统一处理，validate 据此拒绝全关草稿）。
   */
  fun searchIndexSettings(): io.pagenote.core.search.SearchIndexSettings =
    io.pagenote.core.search.SearchIndexSettings(
      indexSource = searchIndexSource,
      indexNotes = searchIndexNotes,
      indexOcr = searchIndexOcr,
      indexTranscripts = searchIndexTranscripts,
    )
}

object ReaderSettingsPolicy {
  val NOTE_FONT_SIZES = listOf(12, 15, 18, 20, 24, 28, 32)
  private val THEMES = setOf("system", "light", "dark", "paper")
  private val MARKDOWN_THEMES = setOf("classic", "paper")
  private val LANGUAGES = setOf("system", "zh-CN", "en-US")
  private val NOTE_BACKGROUNDS = NoteBackground.ALL.map(NoteBackground::key).toSet()

  fun sanitize(settings: ReaderSettings): ReaderSettings {
    val defaults = ReaderSettings()
    return settings.copy(
      theme = settings.theme.takeIf(THEMES::contains) ?: defaults.theme,
      markdownTheme = settings.markdownTheme.takeIf(MARKDOWN_THEMES::contains) ?: defaults.markdownTheme,
      language = settings.language.takeIf(LANGUAGES::contains) ?: defaults.language,
      leftPercent = settings.leftPercent.takeIf { it in 20..80 } ?: defaults.leftPercent,
      noteFontSize = settings.noteFontSize.takeIf(NOTE_FONT_SIZES::contains) ?: defaults.noteFontSize,
      prepareAheadPages = settings.prepareAheadPages.takeIf { it in 0..10 } ?: defaults.prepareAheadPages,
      noteBackground = settings.noteBackground.takeIf(NOTE_BACKGROUNDS::contains) ?: defaults.noteBackground,
      ocrLanguage = settings.ocrLanguage.takeIf(OcrLanguage.SETTING_KEYS::contains) ?: defaults.ocrLanguage,
      latexOcrModel = settings.latexOcrModel.takeIf(LatexModelCatalog::isValidId) ?: defaults.latexOcrModel,
    ).let { scoped ->
      // 批次 7：全关范围回落全开（空索引范围没有意义）。
      val indexScope = io.pagenote.core.search.SearchIndexSettings.sanitize(scoped.searchIndexSettings())
      scoped.copy(
        searchIndexSource = indexScope.indexSource,
        searchIndexNotes = indexScope.indexNotes,
        searchIndexOcr = indexScope.indexOcr,
        searchIndexTranscripts = indexScope.indexTranscripts,
      )
    }.let { scoped ->
      // 批次 8：录音与转写设置（模型/语言/配额越界回落默认）。
      scoped.copy(
        asrModel = scoped.asrModel.takeIf(AsrModelCatalog::isValidId) ?: AsrModelCatalog.DEFAULT_ID,
        asrLanguage = scoped.asrLanguage.takeIf(AsrLanguage::isValidSettingKey) ?: AsrLanguage.SYSTEM,
        audioQuotaMb = AudioQuotaPolicy.sanitizeMb(scoped.audioQuotaMb),
      )
    }
  }

  fun validate(settings: ReaderSettings) {
    require(THEMES.contains(settings.theme)) { "bad theme" }
    require(MARKDOWN_THEMES.contains(settings.markdownTheme)) { "bad Markdown theme" }
    require(LANGUAGES.contains(settings.language)) { "bad language" }
    require(settings.leftPercent in 20..80) { "bad split percent" }
    require(NOTE_FONT_SIZES.contains(settings.noteFontSize)) { "bad note font size" }
    require(settings.prepareAheadPages in 0..10) { "bad preparation window" }
    require(NOTE_BACKGROUNDS.contains(settings.noteBackground)) { "bad note background" }
    require(OcrLanguage.SETTING_KEYS.contains(settings.ocrLanguage)) { "bad OCR language" }
    require(LatexModelCatalog.isValidId(settings.latexOcrModel)) { "bad LaTeX OCR model" }
    require(AsrModelCatalog.isValidId(settings.asrModel)) { "bad ASR model" }
    require(AsrLanguage.isValidSettingKey(settings.asrLanguage)) { "bad ASR language" }
    AudioQuotaPolicy.validateMb(settings.audioQuotaMb)
    io.pagenote.core.search.SearchIndexSettings.validate(settings.searchIndexSettings())
  }

  fun isTabletLandscape(widthDp: Int, heightDp: Int, orientationLandscape: Boolean): Boolean {
    if (!orientationLandscape) return false
    return maxOf(widthDp, heightDp) >= 900
  }

  fun useSideBySide(widthDp: Int, heightDp: Int, orientationLandscape: Boolean): Boolean {
    return isTabletLandscape(widthDp, heightDp, orientationLandscape)
  }
}

internal object ReaderPaneSplitPolicy {
  fun percentAfterDrag(currentPercent: Float, deltaXPx: Float, availableWidthPx: Float): Float {
    require(currentPercent.isFinite() && currentPercent in 20f..80f) { "bad split percent" }
    require(deltaXPx.isFinite()) { "bad divider drag delta" }
    require(availableWidthPx.isFinite() && availableWidthPx > 0f) { "bad pane width" }
    return (currentPercent + deltaXPx * 100f / availableWidthPx).coerceIn(20f, 80f)
  }

  fun persistedPercent(percent: Float): Int {
    require(percent.isFinite() && percent in 20f..80f) { "bad split percent" }
    return percent.roundToInt()
  }
}

package io.pagenote.android

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
)

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
    )
  }

  fun validate(settings: ReaderSettings) {
    require(THEMES.contains(settings.theme)) { "bad theme" }
    require(MARKDOWN_THEMES.contains(settings.markdownTheme)) { "bad Markdown theme" }
    require(LANGUAGES.contains(settings.language)) { "bad language" }
    require(settings.leftPercent in 20..80) { "bad split percent" }
    require(NOTE_FONT_SIZES.contains(settings.noteFontSize)) { "bad note font size" }
    require(settings.prepareAheadPages in 0..10) { "bad preparation window" }
    require(NOTE_BACKGROUNDS.contains(settings.noteBackground)) { "bad note background" }
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

package io.pagenote.android

import java.io.IOException
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

class ReaderSettingsRuntimeTest {
  @Test
  fun systemThemeFollowsDeviceAndExplicitThemesOverrideIt() {
    assertEquals(ReaderThemeMode.DARK, resolveReaderTheme("system", systemIsDark = true))
    assertEquals(ReaderThemeMode.LIGHT, resolveReaderTheme("system", systemIsDark = false))
    assertEquals(ReaderThemeMode.LIGHT, resolveReaderTheme("light", systemIsDark = true))
    assertEquals(ReaderThemeMode.DARK, resolveReaderTheme("dark", systemIsDark = false))
    assertEquals(ReaderThemeMode.PAPER, resolveReaderTheme("paper", systemIsDark = true))
  }

  @Test
  fun systemLanguageFollowsDeviceAndExplicitLanguageOverridesIt() {
    val deviceLocale = Locale("fr", "FR")

    assertEquals(deviceLocale, resolveReaderLocale("system", deviceLocale))
    assertEquals(Locale.SIMPLIFIED_CHINESE, resolveReaderLocale("zh-CN", deviceLocale))
    assertEquals(Locale.US, resolveReaderLocale("en-US", Locale.SIMPLIFIED_CHINESE))
  }

  @Test
  fun splitPercentProducesMatchingPaneWeightsAtBothBounds() {
    assertEquals(20f to 80f, readerPaneWeights(20))
    assertEquals(42f to 58f, readerPaneWeights(42))
    assertEquals(80f to 20f, readerPaneWeights(80))
  }

  @Test
  fun animationPreferenceControlsNoteTransitionDuration() {
    assertEquals(220, notePageTransitionDurationMillis(enabled = true))
    assertEquals(0, notePageTransitionDurationMillis(enabled = false))
  }

  @Test
  fun settingsReadFailureIsExplicitAndASecondCollectionCanRecover() = runBlocking {
    val failure = IOException("preferences file is corrupt")
    val recoveredSettings = ReaderSettings("dark", "en-US", 60, false)
    var subscriptions = 0
    val states = flow<ReaderSettings> {
      subscriptions += 1
      if (subscriptions == 1) throw failure
      emit(recoveredSettings)
    }.asReaderSettingsLoadStates()

    assertEquals(ReaderSettingsLoadState.Error(failure.message!!, failure), states.first())
    assertEquals(ReaderSettingsLoadState.Loaded(recoveredSettings), states.first())
    assertEquals(2, subscriptions)
  }

  @Test
  fun noteHeaderAdaptsAtBothExtremeSplitsAroundWideBreakpoint() {
    val weightedWidth = 840f - 11f
    val noteAtEightyPercentPdf =
      weightedWidth * readerPaneWeights(leftPercent = 80).second / 100f - 16f
    val noteAtTwentyPercentPdf =
      weightedWidth * readerPaneWeights(leftPercent = 20).second / 100f - 16f

    assertEquals(149.8f, noteAtEightyPercentPdf, 0.01f)
    assertEquals(647.2f, noteAtTwentyPercentPdf, 0.01f)
    assertEquals(NotePanelHeaderLayout.STACKED_CONTROLS, notePanelHeaderLayout(noteAtEightyPercentPdf))
    assertEquals(NotePanelHeaderLayout.INLINE, notePanelHeaderLayout(noteAtTwentyPercentPdf))
    assertEquals(NotePanelHeaderLayout.STACKED_ACTIONS, notePanelHeaderLayout(300f))
  }
}

package io.pagenote.android

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderMenuModelTest {
  @Test
  fun usesDrawerBelow600DpAndSidebarAt600Dp() {
    assertEquals(ReaderMenuLayout.COMPACT_DRAWER, ReaderMenuLayout.forWidth(599))
    assertEquals(ReaderMenuLayout.PERMANENT_SIDEBAR, ReaderMenuLayout.forWidth(600))
  }

  @Test
  fun keepsDesktopMenuGroupOrder() {
    assertEquals(
      listOf(
        ReaderMenuGroup.FILE,
        ReaderMenuGroup.AI_ANNOTATIONS,
        ReaderMenuGroup.EDIT,
        ReaderMenuGroup.PARAGRAPH,
        ReaderMenuGroup.FORMAT,
        ReaderMenuGroup.INSERT,
        ReaderMenuGroup.READING,
        ReaderMenuGroup.HANDWRITING,
        ReaderMenuGroup.WORD_EDITING,
        ReaderMenuGroup.SETTINGS,
        ReaderMenuGroup.HELP,
      ),
      ReaderMenuGroup.values().toList(),
    )
  }

  @Test
  fun menuUsesDesktopLightTokensWithoutChangingReaderTheme() {
    val readerScheme = readerColorScheme(ReaderThemeMode.LIGHT)
    val menuScheme = desktopReaderMenuColorScheme(readerScheme)

    assertEquals(Color(0xFF185ABD), menuScheme.primary)
    assertEquals(Color(0xFFF5F8FD), menuScheme.surface)
    assertEquals(Color(0xFF253247), menuScheme.onSurface)
    assertEquals(Color(0xFF245E59), readerScheme.primary)
  }

  @Test
  fun menuUsesDesktopDarkTokens() {
    val menuScheme = desktopReaderMenuColorScheme(readerColorScheme(ReaderThemeMode.DARK))

    assertEquals(Color(0xFF8BB8FF), menuScheme.primary)
    assertEquals(Color(0xFF22252B), menuScheme.surface)
    assertEquals(Color(0xFFE7EAF0), menuScheme.onSurface)
  }
}

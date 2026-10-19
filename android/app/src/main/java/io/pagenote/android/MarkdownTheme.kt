package io.pagenote.android

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

internal data class MarkdownContentPalette(
  val background: Color,
  val text: Color,
  val secondary: Color,
  val heading: Color,
  val rule: Color,
  val link: Color,
  val codeBackground: Color,
  val codeText: Color,
  val serif: Boolean = false,
  val isDark: Boolean = false,
)

internal fun markdownClassicContentPalette(colors: ColorScheme, isDark: Boolean): MarkdownContentPalette =
  MarkdownContentPalette(
    background = colors.surface,
    text = colors.onSurface,
    secondary = colors.onSurfaceVariant,
    heading = colors.primary,
    rule = colors.outline,
    link = colors.primary,
    codeBackground = colors.surfaceVariant,
    codeText = colors.onSurface,
    isDark = isDark,
  )

internal fun markdownContentPalette(
  theme: String,
  isDark: Boolean,
  classic: MarkdownContentPalette,
): MarkdownContentPalette {
  if (theme != "paper") return classic
  return if (isDark) {
    MarkdownContentPalette(
      background = Color(0xFF292A25),
      text = Color(0xFFE7E0CE),
      secondary = Color(0xFFB9B09D),
      heading = Color(0xFFD2B883),
      rule = Color(0xFF776E59),
      link = Color(0xFF9EC5D3),
      codeBackground = Color(0xFF36372F),
      codeText = Color(0xFFE6D8B8),
      serif = true,
      isDark = true,
    )
  } else {
    MarkdownContentPalette(
      background = Color(0xFFF5F0E3),
      text = Color(0xFF352F24),
      secondary = Color(0xFF6C6252),
      heading = Color(0xFF735339),
      rule = Color(0xFFC6B99E),
      link = Color(0xFF315C6E),
      codeBackground = Color(0xFFE9E0CB),
      codeText = Color(0xFF3D382D),
      serif = true,
    )
  }
}

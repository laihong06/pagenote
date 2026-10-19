package io.pagenote.android

internal enum class ReaderMenuLayout {
  COMPACT_DRAWER,
  PERMANENT_SIDEBAR;

  companion object {
    fun forWidth(widthDp: Int): ReaderMenuLayout =
      if (widthDp < COMPACT_WINDOW_MAX_WIDTH_DP) COMPACT_DRAWER else PERMANENT_SIDEBAR

    const val COMPACT_WINDOW_MAX_WIDTH_DP = 600
  }
}

internal enum class ReaderMenuGroup {
  FILE,
  AI_ANNOTATIONS,
  EDIT,
  PARAGRAPH,
  FORMAT,
  INSERT,
  READING,
  HANDWRITING,
  WORD_EDITING,
  SETTINGS,
  HELP,
}

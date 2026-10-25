package io.pagenote.feature.annotations

/**
 * Font catalog for text-tool annotations (port batch 4).
 *
 * The desktop renders `type: "text"` items with a system font stack
 * (`'Segoe UI, Microsoft YaHei, sans-serif'`, src/annotations.js:179) and does
 * not bundle font binaries; this catalog mirrors that approach on Android.
 * Every entry resolves to an Android system font family (no binary asset is
 * shipped) and carries the matching CSS stack used by the WebView-based rich
 * HTML renderer/editor, so the same font id renders consistently on the canvas
 * (Paint) and inside HTML annotations (WebView).
 *
 * Extension-field contract: a text item may carry `"font": "<id>"`; unknown
 * ids degrade to [DEFAULT_FONT_ID] on read instead of failing the item
 * (same degradation policy as the `shape` extension field, schema decision D5).
 */
data class TextFont(
  /** Stable id written into the `font` extension field. Never rename a released id. */
  val id: String,
  /** Android system font family name for `Typeface.create`. */
  val androidFamily: String,
  /** CSS font-family value used by the WebView renderer/editor. */
  val cssStack: String,
  /** Bilingual display labels. */
  val labelZh: String,
  val labelEn: String,
)

object TextFontCatalog {
  val DEFAULT_FONT_ID = "default"

  val FONTS: List<TextFont> = listOf(
    TextFont(
      id = DEFAULT_FONT_ID,
      androidFamily = "sans-serif",
      cssStack = "sans-serif",
      labelZh = "默认",
      labelEn = "Default",
    ),
    TextFont(
      id = "serif",
      androidFamily = "serif",
      cssStack = "serif",
      labelZh = "衬线",
      labelEn = "Serif",
    ),
    TextFont(
      id = "mono",
      androidFamily = "monospace",
      cssStack = "monospace",
      labelZh = "等宽",
      labelEn = "Monospace",
    ),
    TextFont(
      id = "cursive",
      androidFamily = "cursive",
      cssStack = "cursive",
      labelZh = "手写",
      labelEn = "Cursive",
    ),
    TextFont(
      id = "condensed",
      androidFamily = "sans-serif-condensed",
      cssStack = "sans-serif-condensed",
      labelZh = "窄体",
      labelEn = "Condensed",
    ),
    TextFont(
      id = "smallcaps",
      androidFamily = "sans-serif-smallcaps",
      cssStack = "sans-serif-smallcaps",
      labelZh = "小型大写",
      labelEn = "Small caps",
    ),
  )

  private val byIdMap: Map<String, TextFont> = FONTS.associateBy(TextFont::id)

  /** Catalog entry for [id], or null for unknown ids. Ids are case-sensitive. */
  fun byId(id: String): TextFont? = byIdMap[id]

  fun isValidFontId(id: String): Boolean = byIdMap.containsKey(id)

  /** Catalog default; every unknown id degrades to this entry. */
  val DEFAULT: TextFont = checkNotNull(byIdMap[DEFAULT_FONT_ID])

  /**
   * Resolves the Android font family for [id], degrading unknown or null ids
   * to the catalog default (mirroring the read-side degradation of unknown
   * `shape` ids).
   */
  fun androidFamilyFor(id: String?): String = (id?.let(byIdMap::get) ?: DEFAULT).androidFamily

  /** Resolves the CSS font stack for [id] with the same degradation rule. */
  fun cssStackFor(id: String?): String = (id?.let(byIdMap::get) ?: DEFAULT).cssStack

  /** Bilingual label lookup for pickers; null for unknown ids. */
  fun label(id: String): Pair<String, String>? = byIdMap[id]?.let { it.labelZh to it.labelEn }
}

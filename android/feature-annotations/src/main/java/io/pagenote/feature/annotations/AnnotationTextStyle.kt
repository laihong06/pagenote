package io.pagenote.feature.annotations

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Horizontal text alignment for `type: "text"` annotations. */
enum class AnnotationTextAlign(val key: String) {
  LEFT("left"),
  CENTER("center"),
  RIGHT("right");

  companion object {
    fun fromKey(key: String?): AnnotationTextAlign? =
      key?.let { value -> AnnotationTextAlign.values().firstOrNull { it.key == value } }
  }
}

/**
 * Text-tool style schema (port batch 4): font / font size / alignment / color
 * for `type: "text"` annotation items.
 *
 * Storage follows the canvas schema decision extension-field policy (D2/D5):
 * `color` stays the canonical desktop field, while font, size and alignment
 * ride in item extension fields the desktop validator ignores and passes
 * through untouched:
 *
 * - `font` (string): [TextFontCatalog] id; unknown ids degrade to the catalog
 *   default on read instead of failing the item.
 * - `fontSize` (number, document units): explicit size; when absent the size is
 *   derived from the canonical `width` exactly like the desktop renderer
 *   (`Math.max(20, width * 5)`, src/annotations.js:178).
 * - `align` (string): [AnnotationTextAlign.key]; when absent the text is
 *   left-aligned like the desktop `<text x=...>` anchor.
 *
 * This type is the single authority for reading, validating and writing those
 * fields; callers must not hand-roll the keys.
 */
data class AnnotationTextStyle(
  /** Canonical `color` field value (`#RRGGBB`). */
  val color: String,
  /** `font` extension value; null means the field is absent (default font). */
  val fontId: String?,
  /** `fontSize` extension value; null means the field is absent (width-derived). */
  val fontSize: Double?,
  val align: AnnotationTextAlign,
) {
  /** Extension-field entries (font/fontSize/align) with null values omitted. */
  fun extensionFields(): Map<String, Any?> = buildMap {
    fontId?.let { put(FONT_FIELD, it) }
    fontSize?.let { put(FONT_SIZE_FIELD, it) }
    put(ALIGN_FIELD, align.key)
  }

  companion object {
    const val FONT_FIELD = "font"
    const val FONT_SIZE_FIELD = "fontSize"
    const val ALIGN_FIELD = "align"

    /**
     * Explicit font sizes share the desktop-derived range: `width` is valid in
     * 0.1..100 and the desktop derivation `max(20, width*5)` yields 20..500.
     */
    const val MIN_FONT_SIZE = 20.0
    const val MAX_FONT_SIZE = 500.0

    private val COLOR = Regex("^#[a-fA-F0-9]{6}$")

    fun isValidColor(color: String): Boolean = COLOR.matches(color)

    /** Desktop parity: text size derived from the canonical `width` field. */
    fun derivedFontSize(width: Double): Double {
      require(width.isFinite() && width >= 0.0) { "bad width: $width" }
      return maxOf(MIN_FONT_SIZE, width * 5.0)
    }

    /** Effective size of an item: explicit `fontSize` when valid, else derived. */
    fun effectiveFontSize(width: Double, explicitFontSize: Double?): Double =
      explicitFontSize ?: derivedFontSize(width)

    /** Validated style; rejects unknown font ids so Android never writes one. */
    fun create(
      color: String,
      fontId: String? = null,
      fontSize: Double? = null,
      align: AnnotationTextAlign = AnnotationTextAlign.LEFT,
    ): AnnotationTextStyle {
      require(isValidColor(color)) { "bad annotation color: $color" }
      require(fontId == null || TextFontCatalog.isValidFontId(fontId)) { "unknown font id: $fontId" }
      require(fontSize == null || (fontSize.isFinite() && fontSize in MIN_FONT_SIZE..MAX_FONT_SIZE)) {
        "bad font size: $fontSize"
      }
      return AnnotationTextStyle(
        color = color.lowercase(),
        fontId = fontId,
        fontSize = fontSize,
        align = align,
      )
    }

    /**
     * Default style for newly authored text items (the "text tool default
     * font/size schema"): catalog default font, explicit size derived from the
     * current pen width, left alignment, current pen color.
     */
    fun forNewItem(color: String, penWidth: Double): AnnotationTextStyle = create(
      color = color,
      fontId = TextFontCatalog.DEFAULT_FONT_ID,
      fontSize = derivedFontSize(penWidth),
      align = AnnotationTextAlign.LEFT,
    )

    /**
     * Tolerant read of one text item. Returns null when [item] is not a text
     * item or its canonical color is invalid; unknown font ids and out-of-range
     * sizes degrade to their defaults (null), unknown align keys degrade to
     * LEFT — reading never fails an otherwise valid desktop item.
     */
    fun fromItem(item: JsonObject): AnnotationTextStyle? {
      val type = item.stringOrNull("type") ?: return null
      if (type != "text") return null
      val color = item.stringOrNull("color")?.takeIf(AnnotationTextStyle::isValidColor) ?: return null
      val width = item["width"]?.jsonPrimitive?.doubleOrNull
      if (width == null || !width.isFinite()) return null
      val fontId = item.stringOrNull(FONT_FIELD)?.takeIf(TextFontCatalog::isValidFontId)
      val fontSize = item["fontSize"]?.jsonPrimitive?.doubleOrNull
        ?.takeIf { it.isFinite() && it in MIN_FONT_SIZE..MAX_FONT_SIZE }
      val align = AnnotationTextAlign.fromKey(item.stringOrNull(ALIGN_FIELD)) ?: AnnotationTextAlign.LEFT
      return AnnotationTextStyle(
        color = color,
        fontId = fontId,
        fontSize = fontSize ?: derivedFontSize(width),
        align = align,
      )
    }

    private fun JsonObject.stringOrNull(key: String): String? =
      this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
  }
}

/**
 * Pure page mutation for text-item style updates (the data layer behind the
 * UpdateTextStyle undo action). Only `type: "text"` items accept the text
 * style schema; every other type returns null. Unchanged properties are
 * no-ops and never rewrite the item; all unrelated fields (including unknown
 * extension fields such as `pressures` or `layoutAnchor`) are preserved.
 */
object AnnotationTextStyleEditor {
  /**
   * Returns the page with the text style of item [index] updated, or null when
   * [index] is out of bounds, the item is not a text item, or a requested font
   * id is unknown. Null changes keep the current value. Returns [page] itself
   * when the edit is a content no-op.
   */
  fun updateTextStyle(
    page: JsonArray,
    index: Int,
    color: String? = null,
    fontId: String? = null,
    fontSize: Double? = null,
    align: AnnotationTextAlign? = null,
  ): JsonArray? {
    val current = page.getOrNull(index) as? JsonObject ?: return null
    val style = AnnotationTextStyle.fromItem(current) ?: return null
    if (color != null && !AnnotationTextStyle.isValidColor(color)) return null
    if (fontId != null && !TextFontCatalog.isValidFontId(fontId)) return null
    if (fontSize != null &&
      !(fontSize.isFinite() && fontSize in AnnotationTextStyle.MIN_FONT_SIZE..AnnotationTextStyle.MAX_FONT_SIZE)
    ) {
      return null
    }

    val changes = buildMap<String, JsonPrimitive> {
      val nextColor = color ?: style.color
      if (!nextColor.equals(style.color, ignoreCase = true)) {
        put("color", JsonPrimitive(nextColor.lowercase()))
      }
      val effectiveFontId = style.fontId ?: TextFontCatalog.DEFAULT_FONT_ID
      if (fontId != null && fontId != effectiveFontId) {
        put(AnnotationTextStyle.FONT_FIELD, JsonPrimitive(fontId))
      }
      if (fontSize != null && fontSize != style.fontSize) {
        put(AnnotationTextStyle.FONT_SIZE_FIELD, JsonPrimitive(fontSize))
      }
      if (align != null && align != style.align) {
        put(AnnotationTextStyle.ALIGN_FIELD, JsonPrimitive(align.key))
      }
    }
    if (changes.isEmpty()) return page

    val updated = JsonObject(current + changes)
    return JsonArray(page.mapIndexed { itemIndex, item ->
      if (itemIndex == index) updated else item
    })
  }
}

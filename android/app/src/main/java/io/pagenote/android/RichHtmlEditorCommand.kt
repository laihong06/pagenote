package io.pagenote.android

import io.pagenote.feature.annotations.TextFontCatalog

/**
 * Validated style actions for the rich text editing window (port batch 4).
 *
 * Pure JVM layer between the Compose toolbar and the WebView editor: every
 * toolbar action resolves to one whitelisted `document.execCommand` call
 * (see assets/rich-annotation/editor.js COMMANDS), with values validated here
 * before dispatch — block tags, `#RRGGBB` colors and font-family values are
 * restricted to the [TextFontCatalog] CSS stacks, so an unvalidated string can
 * never reach the editor. Invalid input yields null instead of a command.
 */
sealed class RichHtmlEditorCommand {
  /** `document.execCommand` name understood by editor.js. */
  abstract val name: String

  /** `document.execCommand` value; null passes no argument. */
  abstract val value: String?

  data object Bold : RichHtmlEditorCommand() { override val name = "bold"; override val value: String? = null }
  data object Italic : RichHtmlEditorCommand() { override val name = "italic"; override val value: String? = null }
  data object Underline : RichHtmlEditorCommand() { override val name = "underline"; override val value: String? = null }
  data object StrikeThrough : RichHtmlEditorCommand() { override val name = "strikeThrough"; override val value: String? = null }
  data object JustifyLeft : RichHtmlEditorCommand() { override val name = "justifyLeft"; override val value: String? = null }
  data object JustifyCenter : RichHtmlEditorCommand() { override val name = "justifyCenter"; override val value: String? = null }
  data object JustifyRight : RichHtmlEditorCommand() { override val name = "justifyRight"; override val value: String? = null }
  data object BulletList : RichHtmlEditorCommand() { override val name = "insertUnorderedList"; override val value: String? = null }
  data object OrderedList : RichHtmlEditorCommand() { override val name = "insertOrderedList"; override val value: String? = null }
  data object RemoveFormat : RichHtmlEditorCommand() { override val name = "removeFormat"; override val value: String? = null }

  /** Paragraph formatting via `formatBlock`; block tags are a closed set. */
  enum class Block(val tag: String) {
    PARAGRAPH("p"),
    HEADING_1("h1"),
    HEADING_2("h2"),
    HEADING_3("h3"),
    QUOTE("blockquote");

    companion object {
      fun fromTag(tag: String): Block? = values().firstOrNull { it.tag == tag }
    }
  }

  data class FormatBlock(val block: Block) : RichHtmlEditorCommand() {
    override val name = "formatBlock"
    override val value: String get() = block.tag

    companion object {
      fun fromTag(tag: String): FormatBlock? = Block.fromTag(tag)?.let(::FormatBlock)
    }
  }

  /** Text color; only `#RRGGBB` values are accepted. */
  data class ForeColor(val color: String) : RichHtmlEditorCommand() {
    override val name = "foreColor"
    override val value: String get() = color

    companion object {
      private val COLOR = Regex("^#[a-fA-F0-9]{6}$")
      fun create(color: String): ForeColor? =
        color.takeIf(COLOR::matches)?.let { ForeColor(it.lowercase()) }
    }
  }

  /**
   * Font family; font ids resolve to [TextFontCatalog] entries and dispatch
   * the catalog CSS stack, keeping canvas text and rich HTML fonts in one
   * registry.
   */
  data class FontName(val fontId: String) : RichHtmlEditorCommand() {
    override val name = "fontName"
    override val value: String get() = TextFontCatalog.cssStackFor(fontId)

    companion object {
      fun create(fontId: String): FontName? =
        fontId.takeIf(TextFontCatalog::isValidFontId)?.let { FontName(it) }
    }
  }

  /**
   * JavaScript dispatcher call for this command; [requestId] comes from
   * [RichHtmlEditorController] so stale editor results (an older request's
   * title arriving late) can be dropped.
   */
  fun toEditorScript(requestId: String): String =
    "window.commandPagenoteRichEdit(" +
      "${quoteJson(name)},${value?.let(::quoteJson) ?: "null"},${quoteJson(requestId)});"
}

/** JSON string quoting without the Android-only org.json dependency. */
internal fun quoteJson(value: String): String {
  val out = StringBuilder(value.length + 2).append('"')
  value.forEach { c ->
    when (c) {
      '"' -> out.append("\\\"")
      '\\' -> out.append("\\\\")
      '\n' -> out.append("\\n")
      '\r' -> out.append("\\r")
      '\t' -> out.append("\\t")
      '\b' -> out.append("\\b")
      '\u000C' -> out.append("\\f")
      else ->
        if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
    }
  }
  return out.append('"').toString()
}

/** Pure policies for the rich text editing window (unit-tested). */
internal object RichHtmlEditorPolicy {
  private val TAG = Regex("<[^>]*>")
  private val WHITESPACE_ENTITY = Regex("&nbsp;|&#160;|&ensp;|&emsp;|\\s")

  /** True when the editor HTML carries at least one non-whitespace character. */
  fun hasVisibleContent(html: String): Boolean =
    html.replace(TAG, "").replace(WHITESPACE_ENTITY, "").isNotEmpty()
}

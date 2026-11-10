package io.pagenote.feature.notes

data class MarkdownSBlock(
  val engine: String,
  val source: String,
  val fenceStart: Int,
  val sourceStart: Int,
  val sourceEnd: Int,
  val fenceEnd: Int,
  val closed: Boolean,
  val marker: Char,
  val markerLength: Int,
  val indent: String,
  val parseResult: MarkdownSParseResult,
)

object MarkdownSBlockParser {
  private data class Fence(val marker: Char, val length: Int, val indent: String, val info: String)

  fun parse(markdown: String): List<MarkdownSBlock> {
    val lines = mutableListOf<Triple<Int, Int, String>>()
    var cursor = 0
    while (cursor < markdown.length) {
      val lineEnd = markdown.indexOf('\n', cursor).let { if (it < 0) markdown.length else it }
      val textEnd = if (lineEnd > cursor && markdown[lineEnd - 1] == '\r') lineEnd - 1 else lineEnd
      lines += Triple(cursor, if (lineEnd < markdown.length) lineEnd + 1 else lineEnd, markdown.substring(cursor, textEnd))
      cursor = if (lineEnd < markdown.length) lineEnd + 1 else markdown.length
    }
    if (markdown.isEmpty()) return emptyList()
    val blocks = mutableListOf<MarkdownSBlock>()
    var open: Pair<Int, Fence>? = null
    lines.forEachIndexed { index, (start, end, text) ->
      val fence = fence(text)
      if (open == null && fence != null) {
        open = index to fence
      } else if (open != null && fence != null) {
        val (openIndex, opening) = open!!
        if (fence.marker == opening.marker && fence.length >= opening.length && fence.info.isBlank()) {
          addBlock(markdown, lines, blocks, openIndex, index, opening, true)
          open = null
        }
      }
    }
    open?.let { (index, opening) -> addBlock(markdown, lines, blocks, index, lines.lastIndex, opening, false) }
    return blocks
  }

  private fun addBlock(
    markdown: String,
    lines: List<Triple<Int, Int, String>>,
    blocks: MutableList<MarkdownSBlock>,
    openIndex: Int,
    closeIndex: Int,
    fence: Fence,
    closed: Boolean,
  ) {
    val engine = fence.info.trim().substringBefore(' ').lowercase()
    if (engine !in setOf("markdowns", "mds-native")) return
    val sourceStart = lines[openIndex].second
    val sourceEnd = if (closed) lines[closeIndex].first else markdown.length
    val source = markdown.substring(sourceStart.coerceAtMost(sourceEnd), sourceEnd)
    val result = try {
      MarkdownSParseResult(MarkdownSState.parse(source), null)
    } catch (error: MarkdownSInvalidStateException) {
      MarkdownSParseResult(null, error.message ?: "组件数据无效")
    }
    blocks += MarkdownSBlock(
      engine, source, lines[openIndex].first, sourceStart, sourceEnd,
      if (closed) lines[closeIndex].second else markdown.length,
      closed, fence.marker, fence.length, fence.indent, result,
    )
  }

  private fun fence(line: String): Fence? {
    val indent = line.takeWhile { it == ' ' }
    if (indent.length > 3) return null
    val rest = line.drop(indent.length)
    val marker = rest.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
    val length = rest.takeWhile { it == marker }.length
    if (length < 3) return null
    val info = rest.drop(length)
    if (marker == '`' && '`' in info) return null
    return Fence(marker, length, indent, info)
  }
}

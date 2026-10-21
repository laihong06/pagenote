package io.pagenote.feature.notes

/** GFM table extraction mirroring desktop Marked lexer semantics (headers/rows/source). */
data class MarkdownTable(val headers: List<String>, val rows: List<List<String>>, val source: String)

object MarkdownTables {
  private val DELIMITER_CELL = Regex("^:?-{1,}:?$")
  private val INLINE_CODE = Regex("`([^`]*)`")
  private val IMAGE = Regex("!\\[([^\\]]*)\\]\\([^)]*\\)")
  private val LINK = Regex("\\[([^\\]]*)\\]\\([^)]*\\)")
  private val EMPH = Regex("(\\*\\*|__)(.*?)\\1")
  private val EMPH_SINGLE = Regex("(\\*|_)(.*?)\\1")
  private val STRIKE = Regex("~~(.*?)~~")

  fun extract(markdown: String): List<MarkdownTable> {
    if (markdown.isBlank()) return emptyList()
    val lines = markdown.lines()
    val tables = mutableListOf<MarkdownTable>()
    var index = 0
    while (index < lines.size) {
      val header = splitRow(lines[index])
      val delimiter = if (index + 1 < lines.size) splitRow(lines[index + 1]) else null
      if (header != null && delimiter != null && header.size == delimiter.size &&
        delimiter.all { DELIMITER_CELL.matches(it.trim()) }
      ) {
        val rows = mutableListOf<List<String>>()
        val sourceLines = mutableListOf(lines[index], lines[index + 1])
        var cursor = index + 2
        while (cursor < lines.size) {
          val row = splitRow(lines[cursor]) ?: break
          if (row.size != header.size) break
          rows += row.map(::plainText)
          sourceLines += lines[cursor]
          cursor += 1
        }
        tables += MarkdownTable(
          headers = header.map(::plainText),
          rows = rows,
          source = sourceLines.joinToString("\n"),
        )
        index = cursor
      } else {
        index += 1
      }
    }
    return tables
  }

  private fun splitRow(line: String): List<String>? {
    val trimmed = line.trim()
    if (!trimmed.contains('|')) return null
    var body = trimmed
    if (body.startsWith('|')) body = body.drop(1)
    if (body.endsWith('|')) body = body.dropLast(1)
    if (body.isBlank()) return null
    return splitCells(body).map { it.trim() }
  }

  private fun splitCells(body: String): List<String> {
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var inCode = false
    var index = 0
    while (index < body.length) {
      val char = body[index]
      if (char == '`') inCode = !inCode
      if (char == '|' && !inCode) {
        if (index > 0 && body[index - 1] == '\\') {
          current.setLength(current.length - 1)
          current.append('|')
        } else {
          cells += current.toString()
          current.clear()
        }
      } else {
        current.append(char)
      }
      index += 1
    }
    cells += current.toString()
    return cells
  }

  private fun plainText(cell: String): String {
    var text = cell.trim()
    text = INLINE_CODE.replace(text, "$1")
    text = IMAGE.replace(text, "$1")
    text = LINK.replace(text, "$1")
    text = STRIKE.replace(text, "$1")
    text = EMPH.replace(text, "$2")
    text = EMPH_SINGLE.replace(text, "$2")
    return text.replace("\\|", "|").trim()
  }
}

package io.pagenote.android

object NotePreviewRewrite {
  const val DEFAULT_ASSET_SCHEME = "pagenote-asset"
  private val ATTACHMENT_SRC = Regex("/attachments/([a-f0-9-]+\\.[a-z0-9]+)", RegexOption.IGNORE_CASE)
  private val PROJECT_ASSET_SRC = Regex("(?<!/)assets/([a-f0-9-]+\\.[a-z0-9]+)", RegexOption.IGNORE_CASE)
  private val FENCE_OPEN = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")
  private val FENCE_CLOSE = Regex("^ {0,3}(`{3,}|~{3,})[ \\t]*$")
  private val CODE_LANGUAGE_ALIASES = mapOf(
    "python3" to "python",
    "py3" to "python",
    "c++" to "cpp",
    "c#" to "csharp",
    "javascriptreact" to "javascript",
  )

  fun forPreview(markdown: String, assetBase: String = DEFAULT_ASSET_SCHEME): String {
    if (markdown.isEmpty()) return markdown
    var text = rewriteFences(markdown)
    text = rewriteInlineMath(text)
    text = ATTACHMENT_SRC.replace(text) { "$assetBase://${it.groupValues[1]}" }
    text = PROJECT_ASSET_SRC.replace(text) { "$assetBase://${it.groupValues[1]}" }
    return text
  }

  private fun rewriteFences(markdown: String): String {
    val out = StringBuilder(markdown.length)
    var cursor = 0
    while (cursor < markdown.length) {
      val lineEnd = markdown.indexOf('\n', cursor).let { if (it < 0) markdown.length else it }
      val lineContentEnd = if (lineEnd > cursor && markdown[lineEnd - 1] == '\r') lineEnd - 1 else lineEnd
      val line = markdown.substring(cursor, lineContentEnd)
      val opening = FENCE_OPEN.matchEntire(line)
      if (opening == null) {
        val end = if (lineEnd < markdown.length) lineEnd + 1 else lineEnd
        out.append(markdown, cursor, end)
        cursor = end
        continue
      }

      val marker = opening.groupValues[1]
      val info = opening.groupValues[2].trim()
      val language = info.substringBefore(' ').lowercase()
      val close = findFenceClose(markdown, lineEnd, marker)
      if (close == null) {
        out.append(markdown, cursor, markdown.length)
        break
      }

      val sourceStart = if (lineEnd < markdown.length) lineEnd + 1 else lineEnd
      val source = markdown.substring(sourceStart, close.lineStart).trimEnd('\r', '\n')
      when (language) {
        "mermaid" -> if (source.length <= MermaidSourceCodec.MAX_SOURCE_CHARS) {
          appendMermaidImage(out, source)
        } else {
          appendCodeFence(out, markdown, cursor, close, info, marker)
        }
        "math", "latex", "tex" -> appendLatexBlock(out, source)
        else -> appendCodeFence(out, markdown, cursor, close, info, marker)
      }
      cursor = if (close.lineEnd < markdown.length) close.lineEnd + 1 else close.lineEnd
    }
    return out.toString()
  }

  private fun appendCodeFence(
    out: StringBuilder,
    markdown: String,
    start: Int,
    close: FenceClose,
    info: String,
    marker: String,
  ) {
    val alias = CODE_LANGUAGE_ALIASES[info.substringBefore(' ').lowercase()]
    val end = if (close.lineEnd < markdown.length) close.lineEnd + 1 else close.lineEnd
    if (alias == null) {
      out.append(markdown, start, end)
      return
    }
    val openingLineEnd = markdown.indexOf('\n', start).let { if (it < 0) markdown.length else it }
    val line = markdown.substring(start, openingLineEnd)
    val markerEnd = line.indexOf(marker) + marker.length
    val languageStart = markerEnd + line.substring(markerEnd).takeWhile { it == ' ' || it == '\t' }.length
    val languageEnd = languageStart + line.substring(languageStart).takeWhile { it != ' ' && it != '\t' }.length
    if (languageStart == languageEnd) {
      out.append(markdown, start, end)
      return
    }
    out.append(line, 0, languageStart)
      .append(alias)
      .append(line, languageEnd, line.length)
    if (openingLineEnd < markdown.length) out.append('\n')
    val bodyStart = if (openingLineEnd < markdown.length) openingLineEnd + 1 else openingLineEnd
    out.append(markdown, bodyStart, end)
  }

  private fun appendMermaidImage(out: StringBuilder, source: String) {
    if (out.isNotEmpty() && !out.endsWith("\n\n")) {
      if (!out.endsWith('\n')) out.append('\n')
      out.append('\n')
    }
    out.append("![Mermaid diagram](")
      .append(MermaidSourceCodec.encode(source))
      .append(")\n\n")
  }

  private fun appendLatexBlock(out: StringBuilder, source: String) {
    out.append("$$\n").append(source).append("\n$$\n")
  }

  private fun rewriteInlineMath(markdown: String): String {
    val out = StringBuilder(markdown.length)
    var cursor = 0
    while (cursor < markdown.length) {
      val atLineStart = cursor == 0 || markdown[cursor - 1] == '\n'
      val lineEnd = markdown.indexOf('\n', cursor).let { if (it < 0) markdown.length else it }
      if (atLineStart) {
        val line = markdown.substring(cursor, lineEnd).trimEnd('\r')
        val opening = FENCE_OPEN.matchEntire(line)
        if (opening != null) {
          val close = findFenceClose(markdown, lineEnd, opening.groupValues[1])
          if (close == null) {
            out.append(markdown, cursor, markdown.length)
            break
          }
          val end = if (close.lineEnd < markdown.length) close.lineEnd + 1 else close.lineEnd
          out.append(markdown, cursor, end)
          cursor = end
          continue
        }
      }

      when {
        markdown[cursor] == '\\' -> {
          out.append(markdown[cursor++])
          if (cursor < markdown.length) out.append(markdown[cursor++])
        }
        markdown[cursor] == '`' -> {
          val ticks = runLength(markdown, cursor, '`')
          val close = findBacktickClose(markdown, cursor + ticks, ticks)
          if (close < 0) {
            out.append(markdown, cursor, cursor + ticks)
            cursor += ticks
          } else {
            val end = close + ticks
            out.append(markdown, cursor, end)
            cursor = end
          }
        }
        markdown.startsWith("$$", cursor) -> {
          out.append("$$")
          cursor += 2
        }
        markdown[cursor] == '$' -> {
          val line = markdown.substring(cursor, lineEnd)
          val close = findSingleDollarClose(line, 1)
          if (close < 0) {
            out.append('$')
            cursor += 1
          } else {
            out.append("$$").append(line, 1, close).append("$$")
            cursor += close + 1
          }
        }
        else -> out.append(markdown[cursor++])
      }
    }
    return out.toString()
  }

  private fun findFenceClose(markdown: String, afterOpeningLine: Int, opening: String): FenceClose? {
    var lineStart = if (afterOpeningLine < markdown.length) afterOpeningLine + 1 else afterOpeningLine
    while (lineStart < markdown.length) {
      val newline = markdown.indexOf('\n', lineStart).let { if (it < 0) markdown.length else it }
      val lineEnd = newline
      val line = markdown.substring(lineStart, lineEnd).trimEnd('\r')
      val close = FENCE_CLOSE.matchEntire(line)?.groupValues?.get(1)
      if (close != null && close[0] == opening[0] && close.length >= opening.length) {
        return FenceClose(lineStart, lineEnd)
      }
      lineStart = if (newline < markdown.length) newline + 1 else newline
    }
    return null
  }

  private fun findSingleDollarClose(line: String, start: Int): Int {
    var cursor = start
    while (cursor < line.length) {
      if (line[cursor] == '\\') {
        cursor += 2
      } else if (line.startsWith("$$", cursor)) {
        cursor += 2
      } else if (line[cursor] == '$') {
        return cursor
      } else {
        cursor += 1
      }
    }
    return -1
  }

  private fun findBacktickClose(markdown: String, start: Int, ticks: Int): Int {
    var cursor = start
    while (cursor < markdown.length) {
      if (markdown[cursor] == '\\') {
        cursor += 2
      } else if (markdown[cursor] == '`') {
        val length = runLength(markdown, cursor, '`')
        if (length == ticks) return cursor
        cursor += length
      } else {
        cursor += 1
      }
    }
    return -1
  }

  private fun runLength(value: String, start: Int, char: Char): Int {
    var end = start
    while (end < value.length && value[end] == char) end += 1
    return end - start
  }

  private data class FenceClose(val lineStart: Int, val lineEnd: Int)
}

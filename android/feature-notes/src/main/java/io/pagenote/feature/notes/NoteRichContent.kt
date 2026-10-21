package io.pagenote.feature.notes

/** Structured passthrough for rich note blocks; rendering stays Markwon + explicit states. */
data class FormulaSpan(val start: Int, val end: Int, val display: Boolean, val source: String)
data class CodeBlock(val lang: String, val source: String, val closed: Boolean)
enum class MermaidState { READY, EMPTY, SUSPECT }
data class MermaidBlock(val source: String, val state: MermaidState)
enum class ImageKind { ATTACHMENT, REMOTE, OTHER }
data class ImageRef(val url: String, val kind: ImageKind)
enum class NoteDiagnostic { UNCLOSED_CODE_FENCE, UNTERMINATED_FORMULA }
data class NoteRichContent(
  val formulas: List<FormulaSpan>,
  val codeBlocks: List<CodeBlock>,
  val diagrams: List<MermaidBlock>,
  val images: List<ImageRef>,
  val errors: List<String>,
  val warnings: List<String>,
  val diagnostics: List<NoteDiagnostic>,
) {
  companion object {
    fun extract(markdown: String): NoteRichContent = NoteRichContentParser.parse(markdown)
  }
}

internal object NoteRichContentParser {
  private val ATTACHMENT = Regex("^/attachments/[a-f0-9-]+\\.[a-z0-9]+$", RegexOption.IGNORE_CASE)
  private val IMAGE_MD = Regex("!\\[[^\\]]*\\]\\(([^)]+)\\)")

  private val aliases = mapOf(
    "python3" to "python",
    "py3" to "python",
    "c++" to "cpp",
    "c#" to "csharp",
    "javascriptreact" to "javascript",
    "typescriptreact" to "typescript",
    "shellscript" to "bash",
    "console" to "shell",
    "ps1" to "powershell",
  )

  fun parse(markdown: String): NoteRichContent {
    val formulas = mutableListOf<FormulaSpan>()
    val codeBlocks = mutableListOf<CodeBlock>()
    val diagrams = mutableListOf<MermaidBlock>()
    val images = mutableListOf<ImageRef>()
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    val diagnostics = linkedSetOf<NoteDiagnostic>()
    if (markdown.isEmpty()) {
      return NoteRichContent(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }

    val fenceRanges = mutableListOf<IntRange>()
    val lines = markdown.lines()
    var index = 0
    var openFence: OpenFence? = null
    while (index < lines.size) {
      val line = lines[index]
      val fence = fenceOf(line)
      if (openFence == null && fence != null) {
        openFence = OpenFence(index, fence.info, fence.mark, fence.markLength)
      } else if (openFence != null && fence != null &&
        fence.mark == openFence.mark && fence.markLength >= openFence.markLength
      ) {
        val lang = openFence.info.trim().lowercase()
        val source = lines.subList(openFence.start + 1, index).joinToString("\n")
        fenceRanges += lineStart(markdown, lines, openFence.start)..lineEnd(markdown, lines, index)
        if (lang == "mermaid") {
          diagrams += classifyMermaid(source, warnings)
        } else if (lang in setOf("math", "latex", "tex")) {
          formulas += FormulaSpan(0, 0, true, source)
        } else {
          codeBlocks += CodeBlock(codeLanguage(lang), source, closed = true)
        }
        openFence = null
      }
      index += 1
    }
    if (openFence != null) {
      val open = openFence
      errors += "代码围栏未闭合"
      diagnostics += NoteDiagnostic.UNCLOSED_CODE_FENCE
      fenceRanges += lineStart(markdown, lines, open.start)..markdown.length
      val lang = open.info.trim().lowercase()
      val source = lines.subList(open.start + 1, lines.size).joinToString("\n")
      if (lang == "mermaid") diagrams += classifyMermaid(source, warnings)
      else codeBlocks += CodeBlock(codeLanguage(lang), source, closed = false)
    }

    fun inFence(offset: Int) = fenceRanges.any { offset in it }
    val codeSpans = codeSpanRanges(markdown, fenceRanges)
    val excludedMarkupRanges = fenceRanges + codeSpans
    fun findOutsideMarkup(marker: String, start: Int): Int {
      var search = start
      while (search + marker.length <= markdown.length) {
        val excluded = excludedMarkupRanges.firstOrNull { search in it }
        if (excluded != null) {
          search = excluded.last + 1
        } else if (markdown.startsWith(marker, search)) {
          return search
        } else {
          search += 1
        }
      }
      return -1
    }

    var cursor = 0
    while (cursor < markdown.length) {
      if (inFence(cursor) || codeSpans.any { cursor in it }) {
        cursor += 1
        continue
      }
      if (markdown.startsWith("$$", cursor)) {
        val close = findOutsideMarkup("$$", cursor + 2)
        if (close < 0) {
          warnings += "正文仍含未渲染的公式分隔符或LaTeX命令"
          diagnostics += NoteDiagnostic.UNTERMINATED_FORMULA
          break
        }
        formulas += FormulaSpan(cursor, close + 2, true, markdown.substring(cursor + 2, close))
        cursor = close + 2
        continue
      }
      if (markdown[cursor] == '$') {
        val close = findOutsideMarkup("$", cursor + 1)
        if (close < 0 || close == cursor + 1 || markdown.getOrNull(close + 1) == '$') {
          if (close < 0) {
            warnings += "正文仍含未渲染的公式分隔符或LaTeX命令"
            diagnostics += NoteDiagnostic.UNTERMINATED_FORMULA
          }
          cursor += 1
          continue
        }
        formulas += FormulaSpan(cursor, close + 1, false, markdown.substring(cursor + 1, close))
        cursor = close + 1
        continue
      }
      cursor += 1
    }

    IMAGE_MD.findAll(markdown).forEach { match ->
      val url = match.groupValues[1].trim()
      val kind = when {
        ATTACHMENT.matches(url) -> ImageKind.ATTACHMENT
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> {
          warnings += "未自动验证非本地图片: $url"
          ImageKind.REMOTE
        }
        else -> ImageKind.OTHER
      }
      images += ImageRef(url, kind)
    }

    return NoteRichContent(formulas, codeBlocks, diagrams, images, errors, warnings, diagnostics.toList())
  }

  fun codeLanguage(value: String): String {
    val name = value.trim().lowercase()
    return aliases[name] ?: name
  }

  private fun classifyMermaid(source: String, warnings: MutableList<String>): MermaidBlock {
    if (source.isBlank()) {
      warnings += "Mermaid 源码为空，已保留代码块"
      return MermaidBlock(source, MermaidState.EMPTY)
    }
    val suspect = source.lines().size == 1 && !source.contains("-->") && !source.contains("--") &&
      !source.contains("[") && !source.contains("{")
    if (suspect) warnings += "Mermaid 语法疑似错误，已保留源码"
    return MermaidBlock(source, if (suspect) MermaidState.SUSPECT else MermaidState.READY)
  }

  private data class Fence(val mark: Char, val markLength: Int, val info: String)
  private data class OpenFence(val start: Int, val info: String, val mark: Char, val markLength: Int)

  private fun fenceOf(line: String): Fence? {
    val match = Regex("^ {0,3}(`{3,}|~{3,})\\s*([^\\s]*)\\s*$").find(line) ?: return null
    return Fence(match.groupValues[1][0], match.groupValues[1].length, match.groupValues[2])
  }

  private fun lineStart(markdown: String, lines: List<String>, lineIndex: Int): Int {
    var offset = 0
    repeat(lineIndex) { offset += lines[it].length + 1 }
    return offset.coerceIn(0, markdown.length)
  }

  private fun lineEnd(markdown: String, lines: List<String>, lineIndex: Int): Int {
    val start = lineStart(markdown, lines, lineIndex)
    return (start + lines[lineIndex].length).coerceIn(0, markdown.length)
  }

  private fun codeSpanRanges(markdown: String, excludedRanges: List<IntRange>): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var cursor = 0
    while (cursor < markdown.length) {
      val excluded = excludedRanges.firstOrNull { cursor in it }
      if (excluded != null) {
        cursor = excluded.last + 1
        continue
      }
      if (markdown[cursor] != '`') {
        cursor += 1
        continue
      }

      val openingStart = cursor
      while (cursor < markdown.length && markdown[cursor] == '`') cursor += 1
      val delimiterLength = cursor - openingStart
      var search = cursor
      var closingEnd: Int? = null
      while (search < markdown.length) {
        if (excludedRanges.any { search in it }) break
        if (markdown[search] != '`') {
          search += 1
          continue
        }
        val runStart = search
        while (search < markdown.length && markdown[search] == '`') search += 1
        if (search - runStart == delimiterLength) {
          closingEnd = search
          break
        }
      }
      if (closingEnd != null) {
        ranges += openingStart until closingEnd
        cursor = closingEnd
      }
    }
    return ranges
  }
}

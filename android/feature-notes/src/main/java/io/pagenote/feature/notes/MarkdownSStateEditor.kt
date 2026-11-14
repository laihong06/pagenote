package io.pagenote.feature.notes

class MarkdownSBlockNotUniqueException(message: String) : IllegalStateException(message)

class MarkdownSStateConflictException(message: String) : IllegalStateException(message)

object MarkdownSStateEditor {
  fun replace(markdown: String, id: String, expected: MarkdownSState, next: MarkdownSState): String {
    next.validate()
    val matches = MarkdownSBlockParser.parse(markdown).filter { it.parseResult.state?.id == id }
    if (matches.size != 1) {
      throw MarkdownSBlockNotUniqueException("组件标识缺失或重复，未保存以免修改错误位置。")
    }
    val block = matches.single()
    if (block.parseResult.state != expected) {
      throw MarkdownSStateConflictException("组件已被其他修改更新，请重新载入后再操作。")
    }
    val eol = if (block.source.contains("\r\n")) "\r\n" else "\n"
    val trailingEol = when {
      block.source.endsWith("\r\n") -> "\r\n"
      block.source.endsWith("\n") -> "\n"
      else -> ""
    }
    val contentIndent = block.source.lineSequence().firstOrNull { it.isNotBlank() }
      ?.takeWhile { it == ' ' || it == '\t' }.orEmpty()
    val encoded = next.encode().lines().joinToString(eol) { contentIndent + it } + trailingEol
    return markdown.substring(0, block.sourceStart) + encoded + markdown.substring(block.sourceEnd)
  }
}

package io.pagenote.android

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI
import java.util.Locale

internal object HtmlToMarkdown {
  private val discardedTags = setOf(
    "script", "style", "iframe", "object", "embed", "svg", "math", "form", "input", "button",
    "textarea", "select", "option", "video", "audio", "source", "canvas", "template", "noscript",
    "head", "meta", "link",
  )
  private val safeSchemes = setOf("http", "https", "mailto")

  fun convert(html: String): String? {
    if (html.isBlank()) return null
    val document = Jsoup.parseBodyFragment(html)
    document.outputSettings().prettyPrint(false)
    val markdown = renderChildren(document.body())
      .replace("\r\n", "\n")
      .replace(Regex("[ \\t]+\\n"), "\n")
      .replace(Regex("(?m)^\\h+$"), "")
      .replace(Regex("\\n{3,}"), "\n\n")
      .trim()
    return markdown.takeIf(String::isNotBlank)
  }

  private fun render(node: Node): String = when (node) {
    is TextNode -> node.wholeText
    is Element -> renderElement(node)
    else -> ""
  }

  private fun renderChildren(element: Element): String = element.childNodes().joinToString("") { render(it) }

  private fun renderElement(element: Element): String {
    val tag = element.normalName()
    if (tag in discardedTags) return ""
    val children = renderChildren(element)
    return when (tag) {
      "br" -> "\n"
      "hr" -> "\n\n---\n\n"
      "p" -> block(children)
      "h1", "h2", "h3", "h4", "h5", "h6" ->
        block("${"#".repeat(tag.last().digitToInt())} ${children.trim()}")
      "strong", "b" -> inlineWrap(children, "**")
      "em", "i" -> inlineWrap(children, "*")
      "del", "s", "strike" -> inlineWrap(children, "~~")
      "u" -> children.trim().takeIf(String::isNotEmpty)?.let { "<u>$it</u>" }.orEmpty()
      "code" -> inlineCode(children)
      "pre" -> fencedCode(element)
      "a" -> renderLink(element, children)
      "img" -> element.attr("alt").trim()
      "blockquote" -> {
        val quoted = children.trim().lines().joinToString("\n") { line -> if (line.isEmpty()) ">" else "> $line" }
        block(quoted)
      }
      "ul", "ol" -> renderList(element, 0)
      "table" -> renderTable(element)
      "div", "section", "article", "main", "header", "footer", "address", "details", "summary" ->
        block(children)
      else -> children
    }
  }

  private fun block(content: String): String = content.trim().takeIf(String::isNotEmpty)?.let { "$it\n\n" }.orEmpty()

  private fun inlineWrap(content: String, marker: String): String =
    content.trim().takeIf(String::isNotEmpty)?.let { "$marker$it$marker" }.orEmpty()

  private fun inlineCode(content: String): String {
    val value = content.trim()
    if (value.isEmpty()) return ""
    val fence = "`".repeat((Regex("`+").findAll(value).maxOfOrNull { it.value.length } ?: 0) + 1)
    val padded = value.startsWith("`") || value.endsWith("`") || (value.startsWith(" ") && value.endsWith(" "))
    return if (padded) "$fence $value $fence" else "$fence$value$fence"
  }

  private fun fencedCode(element: Element): String {
    val code = element.selectFirst("code") ?: element
    val content = code.wholeText().trim('\n', '\r')
    val language = code.classNames()
      .firstOrNull { it.startsWith("language-") }
      ?.removePrefix("language-")
      ?.takeIf { it.matches(Regex("[A-Za-z0-9_+#.-]{1,32}")) }
      .orEmpty()
    val fence = "`".repeat((Regex("`+").findAll(content).maxOfOrNull { it.value.length } ?: 0) + 3)
    return "\n\n$fence$language\n$content\n$fence\n\n"
  }

  private fun renderLink(element: Element, children: String): String {
    val label = children.trim()
    if (label.isEmpty()) return ""
    val destination = safeDestination(element.attr("href")) ?: return label
    val escapedLabel = label.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")
    return "[$escapedLabel]($destination)"
  }

  private fun safeDestination(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty() || value.any(Char::isISOControl)) return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme != null && scheme !in safeSchemes) return null
    if (scheme == null && (value.startsWith("//") || value.startsWith('\\') || '\\' in value)) return null
    return uri.toASCIIString().replace("(", "%28").replace(")", "%29")
  }

  private fun renderList(list: Element, depth: Int): String {
    val listItems = list.children().filter { it.normalName() == "li" }
    if (listItems.isEmpty()) return block(renderChildren(list))
    var number = list.attr("start").toIntOrNull()?.coerceAtLeast(1) ?: 1
    val lines = mutableListOf<String>()
    listItems.forEach { item ->
      val nestedLists = mutableListOf<Element>()
      val main = item.childNodes().joinToString("") { child ->
        if (child is Element && child.normalName() in setOf("ul", "ol")) {
          nestedLists.add(child)
          ""
        } else {
          render(child)
        }
      }.trim()
      val marker = if (list.normalName() == "ol") "${number++}. " else "- "
      val indent = "  ".repeat(depth)
      val itemLines = main.lines().joinToString("\n") { if (it.isEmpty()) "" else "$indent$marker$it" }
      if (itemLines.isNotEmpty()) lines += itemLines
      nestedLists.forEach { nested ->
        renderList(nested, depth + 1).trimEnd().takeIf(String::isNotEmpty)?.let(lines::add)
      }
    }
    return if (lines.isEmpty()) "" else lines.joinToString("\n") + "\n\n"
  }

  private fun renderTable(table: Element): String {
    val rows = table.select("tr").map { row ->
      row.children()
        .filter { it.normalName() == "th" || it.normalName() == "td" }
        .map { cell -> renderChildren(cell).trim().replace(Regex("\\s*\\n\\s*"), " ").replace("|", "\\|") }
    }.filter(List<String>::isNotEmpty)
    if (rows.isEmpty()) return block(renderChildren(table))
    val width = rows.maxOf(List<String>::size)
    fun row(values: List<String>) = "| " + (0 until width).joinToString(" | ") { values.getOrElse(it) { "" } } + " |"
    val output = mutableListOf(row(rows.first()), "| " + List(width) { "---" }.joinToString(" | ") + " |")
    rows.drop(1).forEach { output += row(it) }
    return output.joinToString("\n") + "\n\n"
  }
}

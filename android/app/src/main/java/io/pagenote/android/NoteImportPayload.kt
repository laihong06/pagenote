package io.pagenote.android

import io.pagenote.core.project.PageNotes
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class NoteImportFormatException(cause: Throwable? = null) :
  IllegalArgumentException("不是有效的 pagenote 笔记备份", cause)

internal data class NoteTextInsertion(
  val replaceStart: Int,
  val replaceEnd: Int,
  val replacement: String,
  val cursor: Int,
)

internal object NoteImportPayload {
  private val json = Json { ignoreUnknownKeys = true }
  private val pageKeyPattern = Regex("^[0-9]+$")

  fun parseBackup(content: String, currentPageCount: Int): Map<Int, String> {
    require(currentPageCount > 0)
    val root = try {
      json.parseToJsonElement(content) as? JsonObject ?: throw NoteImportFormatException()
    } catch (error: SerializationException) {
      throw NoteImportFormatException(error)
    } catch (error: IllegalArgumentException) {
      if (error is NoteImportFormatException) throw error
      throw NoteImportFormatException(error)
    }
    val notes = root["notes"] as? JsonObject ?: throw NoteImportFormatException()
    return buildMap {
      notes.forEach { (key, value) ->
        if (!pageKeyPattern.matches(key)) return@forEach
        val page = key.toIntOrNull() ?: return@forEach
        if (page !in 1..currentPageCount) return@forEach
        val markdown = (value as? JsonPrimitive)
          ?.takeIf(JsonPrimitive::isString)
          ?.content
          ?: return@forEach
        put(page, markdown)
      }
    }
  }

  fun merge(
    currentNotes: String,
    currentPageCount: Int,
    importedPages: Map<Int, String>,
  ): String {
    require(currentPageCount > 0)
    val pages = PageNotes.split(currentNotes).toMutableList()
    importedPages.toSortedMap().forEach { (page, markdown) ->
      if (page !in 1..currentPageCount) return@forEach
      while (pages.size < page) pages += ""
      pages[page - 1] = markdown
    }
    return PageNotes.join(pages)
  }

  fun insertText(
    markdown: String,
    selectionStart: Int,
    selectionEnd: Int,
    text: String,
  ): NoteTextInsertion {
    val (start, end) = if (selectionStart < 0 || selectionEnd < 0) {
      markdown.length to markdown.length
    } else {
      minOf(selectionStart, selectionEnd).coerceIn(0, markdown.length) to
        maxOf(selectionStart, selectionEnd).coerceIn(0, markdown.length)
    }
    return NoteTextInsertion(
      replaceStart = start,
      replaceEnd = end,
      replacement = text,
      cursor = start + text.length,
    )
  }

  fun isJson(displayName: String?, mimeType: String?): Boolean =
    displayName?.endsWith(".json", ignoreCase = true) == true ||
      mimeType?.substringBefore(';')?.trim()?.equals("application/json", ignoreCase = true) == true
}

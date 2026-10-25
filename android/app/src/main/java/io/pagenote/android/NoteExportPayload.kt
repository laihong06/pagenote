package io.pagenote.android

import io.pagenote.core.project.PageNotes
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal data class NoteExportFile(
  val mimeType: String,
  val fileName: String,
  val content: String,
)

internal object NoteExportPayload {
  private val json = Json { prettyPrint = true }

  fun fullBackup(
    projectName: String,
    sourcePageCount: Int,
    notesMarkdown: String,
    currentPage: Int? = null,
    currentDraft: String? = null,
  ): NoteExportFile {
    require(projectName.isNotBlank())
    require(sourcePageCount > 0)
    if (currentDraft != null) require((currentPage ?: 0) > 0)

    val notePages = PageNotes.split(notesMarkdown)
      .mapIndexed { index, markdown -> index + 1 to markdown }
      .toMap()
      .toMutableMap()
    if (currentDraft != null) notePages[requireNotNull(currentPage)] = currentDraft

    val notes = JsonObject(notePages.toSortedMap().mapKeys { it.key.toString() }
      .mapValues { JsonPrimitive(it.value) })
    val backup = JsonObject(
      linkedMapOf<String, JsonElement>(
        "version" to JsonPrimitive(1),
        "document" to JsonPrimitive(projectName),
        "pages" to JsonPrimitive(sourcePageCount),
        "notes" to notes,
      ),
    )
    return NoteExportFile(
      mimeType = "application/json",
      fileName = "$projectName-逐页笔记.json",
      content = json.encodeToString(JsonElement.serializer(), backup),
    )
  }

  fun currentPage(
    projectName: String,
    page: Int,
    markdown: String,
  ): NoteExportFile {
    require(projectName.isNotBlank())
    require(page > 0)
    return NoteExportFile(
      mimeType = "text/markdown",
      fileName = "$projectName-第${page}页.md",
      content = markdown,
    )
  }

  /**
   * Destination name for a passphrase-encrypted export (port batch 3): the
   * container is JSON regardless of the plaintext kind, so the original
   * extension is replaced by `.encrypted.json`, e.g.
   * `Project-逐页笔记.json` → `Project-逐页笔记.encrypted.json`.
   */
  fun encryptedFileName(fileName: String): String {
    require(fileName.isNotBlank()) { "bad export file name" }
    val base = fileName.substringBeforeLast('.', fileName)
    require(base.isNotBlank()) { "bad export file name" }
    return "$base$ENCRYPTED_EXTENSION"
  }

  const val ENCRYPTED_EXTENSION = ".encrypted.json"
  const val ENCRYPTED_MIME_TYPE = "application/json"
}

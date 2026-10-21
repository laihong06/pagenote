package io.pagenote.android

import android.content.ContentResolver
import io.pagenote.agent.ToolRequest
import io.pagenote.agent.ToolResult
import io.pagenote.core.project.PageNotes
import io.pagenote.core.reader.PdfTextExtractionStatus
import io.pagenote.feature.notes.NotePage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object ProjectAgentTools {
  val READ_TOOLS = listOf("read_page", "read_review", "read_handoff")
  val WRITE_TOOLS = listOf("write_note", "patch_note", "write_review_status", "write_handoff")

  data class ParsedPatch(val start: Int, val end: Int, val replacement: String)
  internal data class RewriteEvidence(val page: Int, val expectedRevision: String)

  private const val MAX_REVIEW_ID_LENGTH = 128
  private const val MAX_REVIEW_DETAIL_BYTES = 8L * 1024 * 1024

  fun parsePatch(args: Map<String, String>, pageLength: Int): ParsedPatch {
    val replacement = args["replacement"] ?: throw IllegalArgumentException("missing replacement")
    val start = args["start"]?.toIntOrNull() ?: throw IllegalArgumentException("bad start")
    require(start in 0..pageLength) { "bad start: $start" }
    val end = args["end"]?.toIntOrNull() ?: minOf(pageLength, start + replacement.length)
    require(end in start..pageLength) { "bad end: $end" }
    return ParsedPatch(start, end, replacement)
  }

  fun parsePage(args: Map<String, String>, currentPage: Int): Int {
    val raw = args["page"] ?: return currentPage.also { require(it >= 1) { "bad page" } }
    val page = raw.toIntOrNull() ?: throw IllegalArgumentException("bad page: $raw")
    require(page >= 1) { "bad page: $raw" }
    return page
  }

  fun validReviewStatus(status: String?): Boolean =
    status == null || status == "open" || status == "applied" || status == "dismissed"

  internal fun formatReviewSummaries(records: List<JsonObject>): String {
    val fields = listOf("id", "side", "kind", "page", "status", "revision")
    return JsonArray(records.take(20).map { record ->
      buildJsonObject {
        fields.forEach { field -> put(field, boundedReviewMetadata(record[field], field)) }
        put("instruction", JsonPrimitive(reviewPreview(record, "instruction", 160)))
        put("quote", JsonPrimitive(reviewPreview(record, "quote", 320)))
      }
    }).toString()
  }

  private fun boundedReviewMetadata(value: kotlinx.serialization.json.JsonElement?, field: String) =
    when (field) {
      "page" -> {
        val primitive = value as? JsonPrimitive
        val digits = primitive?.content
        if (digits != null && digits.length in 1..10 && digits.all { it in '0'..'9' }) {
          primitive
        } else {
          JsonNull
        }
      }
      else -> {
        val limit = when (field) {
          "id", "revision" -> 128
          "side", "kind", "status" -> 16
          else -> 0
        }
        val primitive = value as? JsonPrimitive
        if (limit > 0 && primitive?.isString == true && primitive.content.length <= limit) {
          primitive
        } else {
          JsonNull
        }
      }
    }

  internal fun formatReviewDetail(record: JsonObject): String {
    val detail = Json.encodeToString(JsonObject.serializer(), record)
    if (detail.toByteArray(Charsets.UTF_8).size > MAX_REVIEW_DETAIL_BYTES) {
      throw IllegalArgumentException("review detail exceeds the 8 MiB UTF-8 output limit")
    }
    return detail
  }

  internal fun formatReviewStatusSuccess(
    id: String,
    status: String,
    recoveryCopyName: String?,
  ): String {
    val result = "review=$id status=$status"
    return recoveryCopyName?.let { "$result recoveryCopy=$it" } ?: result
  }

  internal fun rewriteEvidenceFrom(
    args: Map<String, String>,
    result: ToolResult,
  ): RewriteEvidence? {
    if (!result.ok) return null
    val page = args["page"]?.toIntOrNull()?.takeIf { it >= 1 } ?: return null
    if (result.updatedPage != page) return null
    val expectedRevision = args["expectedRevision"]?.takeIf { it.isNotBlank() } ?: return null
    return RewriteEvidence(page, expectedRevision)
  }

  private fun reviewPreview(record: JsonObject, field: String, maxLength: Int): String {
    val value = (record[field] as? JsonPrimitive)?.contentOrNull.orEmpty()
    if (value.length <= maxLength) return value
    val marker = "...[truncated]"
    var prefixLength = maxLength - marker.length
    if (prefixLength > 0 && value[prefixLength - 1].isHighSurrogate()) prefixLength--
    return value.take(prefixLength) + marker
  }

  fun handlers(
    resolver: ContentResolver,
    project: OpenedProject,
    renderer: PdfDocumentSession?,
    currentPage: Int,
  ): Map<String, suspend (ToolRequest) -> ToolResult> {
    val rewriteEvidence = mutableListOf<RewriteEvidence>()
    return mapOf(
      "read_page" to { request -> readPage(resolver, project, renderer, currentPage, request) },
      "read_review" to { request -> readReview(resolver, project, request) },
      "read_handoff" to { request -> readHandoff(resolver, project, renderer, request) },
      "write_note" to { request ->
        writeNote(resolver, project, request).also { result ->
          rewriteEvidenceFrom(request.args, result)?.let(rewriteEvidence::add)
        }
      },
      "patch_note" to { request ->
        patchNote(resolver, project, request).also { result ->
          rewriteEvidenceFrom(request.args, result)?.let(rewriteEvidence::add)
        }
      },
      "write_review_status" to { request ->
        writeReviewStatus(resolver, project, rewriteEvidence, request)
      },
      "write_handoff" to { request -> writeHandoff(resolver, project, renderer, request) },
    )
  }

  private suspend fun readPage(
    resolver: ContentResolver,
    project: OpenedProject,
    renderer: PdfDocumentSession?,
    currentPage: Int,
    request: ToolRequest,
  ): ToolResult {
    val page = try {
      parsePage(request.args, currentPage)
    } catch (error: IllegalArgumentException) {
      return ToolResult(ok = false, detail = error.message ?: "bad page")
    }
    return try {
      val notes = ProjectNoteStore(resolver, project.notesUri).read()
      val text = NotePage.pageAt(notes, page)
      val revision = PageNotes.revision(text)
      val native = renderer?.let {
        runCatching { it.extractText(page - 1) }.getOrNull()
      }
      val nativeText = if (native != null && native.status == PdfTextExtractionStatus.AVAILABLE) {
        native.fullText
      } else {
        "unavailable"
      }
      ToolResult(
        ok = true,
        detail = "page=$page revision=$revision nativeChars=${nativeText.length} noteChars=${text.length}",
        touchedPage = page,
      )
    } catch (error: Exception) {
      ToolResult(ok = false, detail = error.message ?: "read_page failed")
    }
  }

  private suspend fun readReview(
    resolver: ContentResolver,
    project: OpenedProject,
    request: ToolRequest,
  ): ToolResult {
    val status = request.args["status"]
    if (!validReviewStatus(status)) {
      return ToolResult(ok = false, detail = "bad status: $status")
    }
    return try {
      val store = ProjectReviewStore(resolver, project.sourceDirectoryUri, project.reviewsUri)
      val id = request.args["id"]
      if (id != null) {
        if (id.length > MAX_REVIEW_ID_LENGTH) {
          return ToolResult(ok = false, detail = "review id must be at most 128 characters")
        }
        val record = store.find(id) ?: return ToolResult(ok = false, detail = "review not found")
        return ToolResult(ok = true, detail = formatReviewDetail(record))
      }
      val records = store.list(null)
      val filtered = records.filter { element ->
        val obj = element as? JsonObject ?: return@filter false
        if (status == null) return@filter true
        obj["status"]?.jsonPrimitive?.content == status
      }
      val summaries = filtered.mapNotNull { it as? JsonObject }
      ToolResult(ok = true, detail = "reviews=${filtered.size}\n${formatReviewSummaries(summaries)}")
    } catch (error: Exception) {
      ToolResult(ok = false, detail = error.message ?: "read_review failed")
    }
  }

  private suspend fun readHandoff(
    resolver: ContentResolver,
    project: OpenedProject,
    renderer: PdfDocumentSession?,
    request: ToolRequest,
  ): ToolResult {
    val raw = request.args["page"] ?: return ToolResult(ok = false, detail = "missing page")
    val page = raw.toIntOrNull()?.takeIf { it >= 1 }
      ?: return ToolResult(ok = false, detail = "bad page: $raw")
    return try {
      val notes = ProjectNoteStore(resolver, project.notesUri).read()
      val noteRev = PageNotes.revision(NotePage.pageAt(notes, page))
      val srcRev = renderer?.sourceRevision.orEmpty()
      val store = ProjectHandoffStore(resolver, project.sourceDirectoryUri, project.handoffsUri)
      val view = store.view(page, noteRev, srcRev)
      val record = view.record ?: return ToolResult(ok = true, detail = "handoff=null")
      ToolResult(
        ok = true,
        detail = "stale=${view.stale} summary=${record.summary.take(500)}",
        touchedPage = page,
      )
    } catch (error: Exception) {
      ToolResult(ok = false, detail = error.message ?: "read_handoff failed")
    }
  }
}

private suspend fun writeNote(
  resolver: ContentResolver,
  project: OpenedProject,
  request: ToolRequest,
): ToolResult {
  val page = try {
    ProjectAgentTools.parsePage(request.args, -1)
  } catch (error: IllegalArgumentException) {
    return ToolResult(ok = false, detail = error.message ?: "bad page")
  }
  val markdown = request.args["markdown"] ?: return ToolResult(ok = false, detail = "missing markdown")
  val expectedRevision = request.args["expectedRevision"]
    ?: return ToolResult(ok = false, detail = "missing expectedRevision")
  return try {
    val store = ProjectNoteStore(resolver, project.notesUri)
    val latest = store.read()
    val edited = io.pagenote.feature.notes.PageNoteEditor.replacePage(
      latestMarkdown = latest, page = page, replacement = markdown, expectedRevision = expectedRevision,
    )
    val changed = edited.pageRevision != expectedRevision
    if (changed) store.write(edited.markdown, expectedOriginal = latest)
    ToolResult(
      ok = true,
      detail = "page=$page revision=${edited.pageRevision}",
      touchedPage = page,
      updatedPage = page.takeIf { changed },
    )
  } catch (error: Exception) {
    ToolResult(ok = false, detail = error.message ?: "write_note failed")
  }
}

private suspend fun patchNote(
  resolver: ContentResolver,
  project: OpenedProject,
  request: ToolRequest,
): ToolResult {
  val page = try {
    ProjectAgentTools.parsePage(request.args, -1)
  } catch (error: IllegalArgumentException) {
    return ToolResult(ok = false, detail = error.message ?: "bad page")
  }
  val expectedRevision = request.args["expectedRevision"]
    ?: return ToolResult(ok = false, detail = "missing expectedRevision")
  return try {
    val store = ProjectNoteStore(resolver, project.notesUri)
    val latest = store.read()
    val current = io.pagenote.feature.notes.NotePage.pageAt(latest, page)
    val parsed = try {
      ProjectAgentTools.parsePatch(request.args, current.length)
    } catch (error: IllegalArgumentException) {
      return ToolResult(ok = false, detail = error.message ?: "bad patch")
    }
    val patched = io.pagenote.feature.notes.NoteRange.patch(current, parsed.start, parsed.replacement, parsed.end)
    val edited = io.pagenote.feature.notes.PageNoteEditor.replacePage(
      latestMarkdown = latest, page = page, replacement = patched, expectedRevision = expectedRevision,
    )
    val changed = edited.pageRevision != expectedRevision
    if (changed) store.write(edited.markdown, expectedOriginal = latest)
    ToolResult(
      ok = true,
      detail = "page=$page revision=${edited.pageRevision}",
      touchedPage = page,
      updatedPage = page.takeIf { changed },
    )
  } catch (error: Exception) {
    ToolResult(ok = false, detail = error.message ?: "patch_note failed")
  }
}

private suspend fun writeReviewStatus(
  resolver: ContentResolver,
  project: OpenedProject,
  rewriteEvidence: Collection<ProjectAgentTools.RewriteEvidence>,
  request: ToolRequest,
): ToolResult {
  val id = request.args["id"] ?: return ToolResult(ok = false, detail = "missing id")
  val status = request.args["status"] ?: return ToolResult(ok = false, detail = "missing status")
  return try {
    val store = ProjectReviewStore(resolver, project.sourceDirectoryUri, project.reviewsUri)
    if (status == "applied") {
      store.transitionApplied(
        id,
        rewriteEvidence.map { evidence ->
          ProjectReviewStore.ApplyExpectation(evidence.page, evidence.expectedRevision)
        },
      )
    } else {
      store.transition(id, status)
    }
    ToolResult(
      ok = true,
      detail = ProjectAgentTools.formatReviewStatusSuccess(
        id,
        status,
        store.lastRetainedRecoveryCopyName,
      ),
    )
  } catch (error: Exception) {
    ToolResult(ok = false, detail = error.message ?: "write_review_status failed")
  }
}

private suspend fun writeHandoff(
  resolver: ContentResolver,
  project: OpenedProject,
  renderer: PdfDocumentSession?,
  request: ToolRequest,
): ToolResult {
  val page = try {
    ProjectAgentTools.parsePage(request.args, -1)
  } catch (error: IllegalArgumentException) {
    return ToolResult(ok = false, detail = error.message ?: "bad page")
  }
  val summary = request.args["summary"] ?: return ToolResult(ok = false, detail = "missing summary")
  val expectedRevision = request.args["expectedRevision"]
    ?: return ToolResult(ok = false, detail = "missing expectedRevision")
  val sourceRevision = request.args["sourceRevision"]
    ?: return ToolResult(ok = false, detail = "missing sourceRevision")
  return try {
    val notes = ProjectNoteStore(resolver, project.notesUri).read()
    val current = io.pagenote.feature.notes.NotePage.pageAt(notes, page)
    val currentRev = io.pagenote.core.project.PageNotes.revision(current)
    val store = ProjectHandoffStore(resolver, project.sourceDirectoryUri, project.handoffsUri)
    store.write(page, summary, expectedRevision, sourceRevision, currentRev)
    ToolResult(ok = true, detail = "handoff page=$page", touchedPage = page, updatedPage = page)
  } catch (error: Exception) {
    ToolResult(ok = false, detail = error.message ?: "write_handoff failed")
  }
}

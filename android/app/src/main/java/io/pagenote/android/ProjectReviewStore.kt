package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import io.pagenote.feature.annotations.ReviewCreate
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.CharacterCodingException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class IncompleteReviewFileInitializationException(
  cause: Throwable,
  cleanupFailure: Throwable,
) : IOException(
  "Unable to initialize review JSON file; incomplete ai-reviews.json may remain in the project folder",
  cause,
) {
  init {
    cause.addSuppressed(cleanupFailure)
  }
}

/** App-process store instances share a URI-keyed lock. SAF has no generic cross-app compare-and-swap. */
class ProjectReviewStore(
  private val resolver: ContentResolver,
  private val sourceDirectoryUri: Uri,
  reviewsUri: Uri?,
) {
  private data class ReviewSnapshot(val raw: String, val records: JsonArray)

  private val json = Json { ignoreUnknownKeys = true }
  private val state = states.computeIfAbsent(sourceDirectoryUri.toString()) { StoreState(reviewsUri) }

  @Volatile
  internal var lastRetainedRecoveryCopyName: String? = null
    private set

  internal data class ApplyExpectation(val expectedPage: Int, val expectedRevision: String)

  init {
    synchronized(state.lock) {
      if (state.reviewsUri == null) state.reviewsUri = reviewsUri
    }
  }

  fun list(page: Int? = null): JsonArray = synchronized(state.lock) {
    val uri = state.reviewsUri ?: return@synchronized JsonArray(emptyList())
    val records = readSnapshot(uri).records
    if (page == null) records else JsonArray(records.filter {
      (it as? JsonObject)?.get("page")?.jsonPrimitive?.content?.toIntOrNull() == page
    })
  }

  fun create(
    side: String,
    kind: String,
    page: Int,
    instruction: String,
    revision: String?,
    targetNoteRevision: String? = null,
  ): JsonObject {
    return create(ReviewCreate(
      side = side,
      kind = kind,
      page = page,
      instruction = instruction,
      revision = revision,
      targetNoteRevision = targetNoteRevision,
    ))
  }

  fun create(input: ReviewCreate): JsonObject {
    validate(input)
    return synchronized(state.lock) {
      val uri = ensureFile()
      val snapshot = readSnapshot(uri)
      val records = snapshot.records.toMutableList()
      val number = (records.mapNotNull {
        (it as? JsonObject)?.get("number")?.jsonPrimitive?.content?.toIntOrNull()
      }.maxOrNull() ?: 0) + 1
      val record = buildRecord(input, number)
      records.add(record)
      writeRecords(uri, JsonArray(records), snapshot.raw)
      record
    }
  }

  fun find(id: String): JsonObject? = synchronized(state.lock) {
    val uri = state.reviewsUri ?: return@synchronized null
    readSnapshot(uri).records.firstOrNull {
      (it as? JsonObject)?.get("id")?.jsonPrimitive?.content == id
    } as? JsonObject
  }

  fun transition(id: String, to: String): JsonObject = synchronized(state.lock) {
    require(to != "applied") { "applied requires same-run changed note evidence" }
    transitionLocked(id, to) { false }
  }

  /** Checks run-local page/revision evidence against the latest record under the shared URI lock. */
  internal fun transitionApplied(id: String, expectations: Collection<ApplyExpectation>): JsonObject =
    synchronized(state.lock) {
      transitionLocked(id, "applied") { current -> authorizesApplied(current, expectations) }
    }

  private fun transitionLocked(
    id: String,
    to: String,
    authorizeApplied: (JsonObject) -> Boolean,
  ): JsonObject {
    val uri = state.reviewsUri ?: throw NoSuchElementException("review not found")
    val snapshot = readSnapshot(uri)
    val records = snapshot.records.toMutableList()
    val index = records.indexOfFirst { (it as? JsonObject)?.get("id")?.jsonPrimitive?.content == id }
    if (index < 0) throw NoSuchElementException("review not found")
    val current = records[index] as? JsonObject ?: throw NoSuchElementException("review not found")
    if (to == "applied") {
      require(authorizeApplied(current)) {
        "applied requires a changed note write matching the current review page and revision"
      }
    }
    if (!ReviewSafBoundary.canTransition(current, to)) {
      throw IllegalStateException("review cannot transition from open")
    }
    val updated = JsonObject(current + mapOf(
      "status" to JsonPrimitive(to),
      "updated" to JsonPrimitive(java.time.Instant.now().toString()),
    ))
    records[index] = updated
    writeRecords(uri, JsonArray(records), snapshot.raw)
    return updated
  }

  fun delete(id: String): Boolean = synchronized(state.lock) {
    val uri = state.reviewsUri ?: return@synchronized false
    val snapshot = readSnapshot(uri)
    val records = snapshot.records
    val kept = records.filterNot { (it as? JsonObject)?.get("id")?.jsonPrimitive?.content == id }
    if (kept.size == records.size) return@synchronized false
    writeRecords(uri, JsonArray(kept), snapshot.raw)
    true
  }

  private fun validate(input: ReviewCreate) {
    require(input.side == "document" || input.side == "note") { "bad side" }
    require(input.kind == "overall" || input.kind == "selection" || input.kind == "region") { "bad kind" }
    require(input.page >= 1) { "bad page" }
    val trimmed = input.instruction.trim()
    require(trimmed.isNotEmpty() && trimmed.length <= 20000) { "bad instruction" }
    require((input.quote ?: "").length <= 100000) { "quote too large" }
    require((input.context ?: "").length <= 1000000) { "context too large" }
    input.anchor?.let {
      require(Json.encodeToString(JsonElement.serializer(), it).length <= 500000) { "anchor too large" }
    }
  }

  private fun buildRecord(input: ReviewCreate, number: Int): JsonObject {
    val trimmed = input.instruction.trim()
    val fields = mutableMapOf(
      "id" to JsonPrimitive(java.util.UUID.randomUUID().toString()),
      "number" to JsonPrimitive(number),
      "side" to JsonPrimitive(input.side),
      "kind" to JsonPrimitive(input.kind),
      "page" to JsonPrimitive(input.page),
      "scope" to JsonPrimitive(if (input.scope == "document") "document" else "page"),
      "instruction" to JsonPrimitive(trimmed),
      "quote" to JsonPrimitive(input.quote ?: ""),
      "context" to JsonPrimitive(input.context ?: ""),
      "anchor" to (input.anchor ?: JsonNull),
      "status" to JsonPrimitive("open"),
      "created" to JsonPrimitive(java.time.Instant.now().toString()),
    )
    input.targetNoteRevision?.let { fields["targetNoteRevision"] = JsonPrimitive(it) }
    return buildJsonObjectHelper(fields, input.revision)
  }

  private fun buildJsonObjectHelper(
    fields: Map<String, kotlinx.serialization.json.JsonElement>,
    revision: String?,
  ): JsonObject {
    val map = fields.toMutableMap()
    if (revision != null) map["revision"] = JsonPrimitive(revision)
    else map["revision"] = kotlinx.serialization.json.JsonNull
    return JsonObject(map)
  }

  private fun ensureFile(): Uri {
    state.reviewsUri?.let { return it }
    val created = DocumentsContract.createDocument(
      resolver, sourceDirectoryUri, "application/json", "ai-reviews.json",
    ) ?: throw IOException("无法创建项目 JSON 文件")
    try {
      overwrite(created, "[]")
    } catch (error: Exception) {
      val cleanupError = runCatching {
        if (!DocumentsContract.deleteDocument(resolver, created)) {
          throw IOException("Unable to remove incomplete review JSON file")
        }
      }.exceptionOrNull()
      if (cleanupError != null) {
        throw IncompleteReviewFileInitializationException(error, cleanupError)
      }
      throw IOException("Unable to initialize review JSON file", error)
    }
    state.reviewsUri = created
    return created
  }

  private fun readSnapshot(uri: Uri): ReviewSnapshot {
    val text = readRaw(uri)
    try {
      val element = json.parseToJsonElement(text)
      val records = element as? JsonArray ?: throw IOException("项目 JSON 文件格式无效")
      return ReviewSnapshot(text, records)
    } catch (error: IllegalArgumentException) {
      throw IOException("项目 JSON 文件格式无效", error)
    }
  }

  private fun readRaw(uri: Uri): String {
    val input = resolver.openInputStream(uri) ?: throw IOException("无法读取项目 JSON 文件")
    val output = ByteArrayOutputStream()
    input.use { stream ->
      val buffer = ByteArray(8192)
      var total = 0L
      while (true) {
        val remainingWithOverflowByte = MAX_REVIEW_JSON_BYTES + 1 - total
        val count = stream.read(
          buffer,
          0,
          minOf(buffer.size.toLong(), remainingWithOverflowByte).toInt(),
        )
        if (count < 0) break
        if (total + count > MAX_REVIEW_JSON_BYTES) {
          throw IOException("project review JSON exceeds the 64 MiB UTF-8 read limit")
        }
        output.write(buffer, 0, count)
        total += count
      }
    }
    return try {
      Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(output.toByteArray()))
        .toString()
    } catch (error: CharacterCodingException) {
      throw IOException("project review JSON contains invalid UTF-8", error)
    }
  }

  private fun writeRecords(uri: Uri, records: JsonArray, expectedOriginal: String) {
    val encoded = Json.encodeToString(JsonArray.serializer(), records)
    if (encoded.toByteArray(Charsets.UTF_8).size > MAX_REVIEW_JSON_BYTES) {
      throw IOException("project review JSON exceeds the 64 MiB UTF-8 write limit")
    }
    lastRetainedRecoveryCopyName = RecoverableDocumentWrite(
      readTarget = { readRaw(uri) },
      overwriteTarget = { content -> overwrite(uri, content) },
      createBackup = ::createRecoveryCopy,
      deleteBackup = { copy ->
        if (!DocumentsContract.deleteDocument(resolver, copy.uri)) {
          throw IOException("Unable to delete review recovery copy")
        }
      },
      backupName = ReviewRecoveryCopy::displayName,
      documentLabel = "review JSON",
    ).write(updated = encoded, expectedOriginal = expectedOriginal)
  }

  private fun overwrite(target: Uri, content: String) {
    val output = resolver.openOutputStream(target, "wt")
      ?: throw IOException("无法写入项目 JSON 文件")
    output.use { it.write(content.toByteArray(Charsets.UTF_8)) }
  }

  private fun createRecoveryCopy(original: String): RecoveryCopyPreparation<ReviewRecoveryCopy> {
    val target = state.reviewsUri ?: throw IOException("review JSON file is unavailable")
    val parentPath = DocumentsContract.findDocumentPath(resolver, target)?.path
      ?: throw IOException("Unable to determine review JSON parent")
    if (parentPath.size < 2) throw IOException("Unable to determine review JSON parent")
    val parent = DocumentsContract.buildDocumentUriUsingTree(
      target,
      parentPath[parentPath.lastIndex - 1],
    )
    val displayName = "pagenote-recovery-${UUID.randomUUID()}.json"
    val preparation = RecoveryCopyPreparation.fromProvider(
      findParent = { parent },
      supportsCreate = { supportsFlag(it, DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) },
      create = { directory ->
        DocumentsContract.createDocument(resolver, directory, "application/json", displayName)
          ?.let { ReviewRecoveryCopy(it, displayName) }
      },
    )
    val copy = when (preparation) {
      RecoveryCopyPreparation.Unsupported -> return preparation
      is RecoveryCopyPreparation.Available -> preparation.copy
    }

    val canDelete = runCatching {
      supportsFlag(copy.uri, DocumentsContract.Document.FLAG_SUPPORTS_DELETE)
    }.getOrElse { error ->
      removeRecoveryCopyOrThrow(copy, error)
      throw IOException("Unable to verify review recovery-copy cleanup: $displayName", error)
    }
    if (!canDelete) {
      removeRecoveryCopyOrThrow(copy)
      throw IOException("SAF provider cannot clean up review recovery copy: $displayName")
    }

    return try {
      overwrite(copy.uri, original)
      RecoveryCopyPreparation.Available(copy)
    } catch (error: Exception) {
      removeRecoveryCopyOrThrow(copy, error)
      throw IOException("Unable to write review recovery copy: $displayName", error)
    }
  }

  private fun supportsFlag(document: Uri, flag: Int): Boolean {
    val cursor = resolver.query(
      document,
      arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
      null,
      null,
      null,
    ) ?: throw IOException("Unable to read SAF document capabilities")
    return cursor.use {
      if (!it.moveToFirst()) throw IOException("SAF document capability query returned no row")
      val index = it.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
      if (index < 0) throw IOException("SAF document capability flags are unavailable")
      (it.getInt(index) and flag) != 0
    }
  }

  private fun removeRecoveryCopyOrThrow(copy: ReviewRecoveryCopy, cause: Throwable? = null) {
    val removed = runCatching { DocumentsContract.deleteDocument(resolver, copy.uri) }
      .getOrDefault(false)
    if (!removed) throw IOException("Unable to remove review recovery copy: ${copy.displayName}", cause)
  }

  private data class ReviewRecoveryCopy(val uri: Uri, val displayName: String)

  private class StoreState(var reviewsUri: Uri?) {
    val lock = Any()
  }

  companion object {
    private val states = ConcurrentHashMap<String, StoreState>()
    private const val MAX_REVIEW_JSON_BYTES = 64L * 1024 * 1024

    internal fun authorizesApplied(
      record: JsonObject,
      expectations: Collection<ApplyExpectation>,
    ): Boolean {
      val page = (record["page"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: return false
      val revisionField = if (
        (record["side"] as? JsonPrimitive)?.contentOrNull == "document" &&
        record["targetNoteRevision"] != null && record["targetNoteRevision"] != JsonNull
      ) {
        "targetNoteRevision"
      } else {
        "revision"
      }
      val revision = (record[revisionField] as? JsonPrimitive)?.contentOrNull
        ?.takeIf { it.isNotBlank() } ?: return false
      return expectations.any { it.expectedPage == page && it.expectedRevision == revision }
    }
  }
}

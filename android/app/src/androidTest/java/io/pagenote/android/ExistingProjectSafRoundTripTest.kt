package io.pagenote.android

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.annotations.ReviewCreate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ExistingProjectSafRoundTripTest {
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val testContext get() = InstrumentationRegistry.getInstrumentation().context
  private val resolver get() = testContext.contentResolver
  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )

  @Test
  fun existing69PageProjectRendersAndRoundTripsOnePageWithoutChangingNeighbors() = runBlocking {
    installFixture()
    val initialNotes = fixtureNotes()
    val opened = ProjectDocumentOpener(resolver).open(treeUri)
    assertEquals("Pattern recognition fixture", opened.manifest.name)
    assertEquals("pdf", opened.sourceExtension)

    val session = PdfDocumentSession.open(context, opened.sourceUri, context.cacheDir)
    try {
      assertEquals(69, session.pageCount)
      val expectedMarkers = mapOf(0 to Color.RED, 34 to Color.GREEN, 68 to Color.BLUE)
      val renderedHashes = expectedMarkers.map { (index, expectedColor) ->
        val bitmap = session.render(index)
        try {
          val marker = bitmap.getPixel((bitmap.width * 0.3f).toInt(), (bitmap.height * 0.22f).toInt())
          assertEquals("unexpected page marker at one-based page ${index + 1}", expectedColor, marker)
          bitmap.width * 31 + bitmap.height * 17 + marker
        } finally {
          bitmap.recycle()
        }
      }
      assertEquals(3, renderedHashes.distinct().size)
    } finally {
      session.close()
    }

    val noteStore = ProjectNoteStore(resolver, opened.notesUri)
    val original = noteStore.read()
    assertEquals(initialNotes, original)
    val pages = PageNotes.split(original).toMutableList()
    val firstPage = pages[0]
    val lastPage = pages[68]
    pages[34] = "# Rewritten page 35\n\nSaved from Android."
    assertNull(noteStore.write(PageNotes.join(pages), original))

    val reopened = ProjectDocumentOpener(resolver).open(treeUri)
    val savedPages = PageNotes.split(ProjectNoteStore(resolver, reopened.notesUri).read())
    assertEquals(69, savedPages.size)
    assertEquals("# Rewritten page 35\n\nSaved from Android.", savedPages[34])
    assertEquals(firstPage, savedPages[0])
    assertEquals(lastPage, savedPages[68])
  }

  @Test
  fun staleExpectedNoteIsRejectedWithoutChangingProjectNotes() {
    installFixture()
    val opened = ProjectDocumentOpener(resolver).open(treeUri)
    val store = ProjectNoteStore(resolver, opened.notesUri)
    val expectedOriginal = store.read()
    val externalUpdate = PageNotes.join(fixtureNotesPages().mapIndexed { index, note ->
      if (index == 0) "Changed by another writer" else note
    })
    resolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_REPLACE_NOTES,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, externalUpdate.toByteArray(Charsets.UTF_8))
      },
    )

    try {
      store.write("stale write", expectedOriginal)
      fail("stale note write should be rejected")
    } catch (_: NoteDocumentConflictException) {
      assertEquals(externalUpdate, store.read())
    }
  }

  @Test
  fun selectionReviewPersistsAllFieldsAndFindsExactIdsWithoutDroppingUnknownFields() {
    installFixture()
    val opened = ProjectDocumentOpener(resolver).open(treeUri)
    val reviewsUri = requireNotNull(opened.reviewsUri)
    val store = ProjectReviewStore(resolver, opened.sourceDirectoryUri, reviewsUri)
    val input = selectionReviewInput()

    val created = store.create(input)
    val id = created["id"]!!.jsonPrimitive.content
    val persistedRecords = Json.parseToJsonElement(readReviews(reviewsUri)).jsonArray
    val persisted = persistedRecords.first { (it as JsonObject)["id"]?.jsonPrimitive?.content == id }

    assertEquals("note", created["side"]?.jsonPrimitive?.content)
    assertEquals("selection", created["kind"]?.jsonPrimitive?.content)
    assertEquals("page", created["scope"]?.jsonPrimitive?.content)
    assertEquals("selected", created["quote"]?.jsonPrimitive?.content)
    assertEquals("# Page\nselected", created["context"]?.jsonPrimitive?.content)
    assertEquals("revision-abc", created["revision"]?.jsonPrimitive?.content)
    assertEquals(input.anchor, created["anchor"])
    assertEquals(created, persisted)
    assertEquals(created, store.find(id))

    val existing = requireNotNull(store.find("review-1"))
    assertEquals("preserved", existing["desktopOnly"]?.jsonObject?.get("nested")?.jsonPrimitive?.content)
    assertEquals("review-10", store.find("review-10")?.get("id")?.jsonPrimitive?.content)
    assertNull(store.find("review-1-missing"))
    assertEquals(emptyList<String>(), recoveryCopyNames())
  }

  @Test
  fun oversizedReviewJsonIsRejectedAtThe64MiBUtf8ReadLimit() {
    installFixture()
    resolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_WRITE_OVERSIZED_REVIEWS,
      null,
      Bundle().apply { putLong(ExistingProjectSafTestProvider.EXTRA_SIZE, 64L * 1024 * 1024 + 1) },
    )
    val opened = ProjectDocumentOpener(resolver).open(treeUri)
    val store = ProjectReviewStore(resolver, opened.sourceDirectoryUri, opened.reviewsUri)

    val error = org.junit.Assert.assertThrows(IOException::class.java) { store.list() }

    assertEquals(true, error.message.orEmpty().contains("64 MiB"))
  }

  @Test
  fun malformedUtf8ReviewJsonIsRejectedBeforeAnyWrite() {
    val malformed = "[{\"id\":\"bad\",\"instruction\":\"before ".toByteArray() +
      byteArrayOf(0xFF.toByte()) + " after\"}]".toByteArray()
    installFixture(reviewBytes = malformed)
    val opened = ProjectDocumentOpener(resolver).open(treeUri)
    val store = ProjectReviewStore(resolver, opened.sourceDirectoryUri, opened.reviewsUri)

    val error = org.junit.Assert.assertThrows(IOException::class.java) { store.list() }

    assertTrue(error.message.orEmpty().contains("UTF-8"))
    assertEquals(0, reviewWriteCount())
  }

  @Test
  fun partialReviewWriteRestoresOriginalAndRemovesRecoveryCopy() {
    installFixture()
    val opened = ProjectDocumentOpener(resolver).open(treeUri)
    val reviewsUri = requireNotNull(opened.reviewsUri)
    val original = readReviews(reviewsUri)
    val store = ProjectReviewStore(resolver, opened.sourceDirectoryUri, reviewsUri)
    configureReviewWriteFailures(1)

    org.junit.Assert.assertThrows(IOException::class.java) {
      store.create(selectionReviewInput(context = "c".repeat(1_000_000)))
    }

    assertEquals(original, readReviews(reviewsUri))
    assertEquals(emptyList<String>(), recoveryCopyNames())
    assertEquals(2, reviewWriteCount())
  }

  @Test
  fun failedReviewRollbackRetainsPopulatedRecoveryCopyAndReportsItsName() {
    installFixture()
    val opened = ProjectDocumentOpener(resolver).open(treeUri)
    val reviewsUri = requireNotNull(opened.reviewsUri)
    val original = readReviews(reviewsUri)
    val store = ProjectReviewStore(resolver, opened.sourceDirectoryUri, reviewsUri)
    configureReviewWriteFailures(2)

    val error = org.junit.Assert.assertThrows(IOException::class.java) {
      store.create(selectionReviewInput(context = "c".repeat(1_000_000)))
    }

    val copies = recoveryCopies()
    assertEquals(
      "expected failed rollback to retain a copy; writes=${reviewWriteCount()}, " +
        "failuresRemaining=${reviewWriteFailuresRemaining()}, error=${error.message}",
      1,
      copies.size,
    )
    val recovery = copies.single()
    assertEquals(original, recovery.second)
    assertTrue(error.message.orEmpty().contains(recovery.first))
    assertEquals(false, error.message.orEmpty().contains("note write"))
  }

  @Test
  fun successfulReviewWriteReportsRecoveryCopyWhenCleanupFails() {
    installFixture()
    val opened = ProjectDocumentOpener(resolver).open(treeUri)
    val reviewsUri = requireNotNull(opened.reviewsUri)
    val store = ProjectReviewStore(resolver, opened.sourceDirectoryUri, reviewsUri)
    configureRecoveryDeleteFailure(1)

    val created = store.create(selectionReviewInput())

    val recoveryName = requireNotNull(store.lastRetainedRecoveryCopyName)
    val id = created["id"]!!.jsonPrimitive.content
    assertEquals(created, store.find(id))
    assertTrue(recoveryName.startsWith("pagenote-recovery-"))
    assertEquals(listOf(recoveryName), recoveryCopyNames())
  }

  @Test
  fun firstReviewCreationReportsOnlyTheRetainedMutationRecoveryCopy() {
    installFixture(omitReviews = true)
    val opened = ProjectDocumentOpener(resolver).open(treeUri)
    assertNull(opened.reviewsUri)
    val isolatedSource = opened.sourceDirectoryUri.buildUpon()
      .appendQueryParameter("testState", java.util.UUID.randomUUID().toString())
      .build()
    val store = ProjectReviewStore(resolver, isolatedSource, opened.reviewsUri)
    configureRecoveryDeleteFailure(1)

    val created = store.create(selectionReviewInput())

    val recoveryName = requireNotNull(store.lastRetainedRecoveryCopyName)
    val id = created["id"]!!.jsonPrimitive.content
    assertEquals(created, store.find(id))
    assertEquals(listOf(recoveryName), recoveryCopyNames())
  }

  @Test
  fun failedInitialReviewFileCleanupExplainsThatTheIncompleteFileMayRemain() {
    installFixture(omitReviews = true)
    val opened = ProjectDocumentOpener(resolver).open(treeUri)
    val isolatedSource = opened.sourceDirectoryUri.buildUpon()
      .appendQueryParameter("testState", java.util.UUID.randomUUID().toString())
      .build()
    val store = ProjectReviewStore(resolver, isolatedSource, opened.reviewsUri)
    configureReviewWriteFailures(1)
    configureRecoveryDeleteFailure(1)

    val error = org.junit.Assert.assertThrows(IOException::class.java) {
      store.create(selectionReviewInput())
    }

    assertTrue(error.message.orEmpty().contains("incomplete ai-reviews.json may remain"))
    val residualUri = requireNotNull(ProjectDocumentOpener(resolver).open(treeUri).reviewsUri)
    assertEquals("[", readReviews(residualUri))
  }

  @Test
  fun oversizedQuoteIsRejectedBeforeAnySafWrite() {
    assertRejectedBeforeWrite(selectionReviewInput(quote = "q".repeat(100_001)))
  }

  @Test
  fun oversizedContextIsRejectedBeforeAnySafWrite() {
    assertRejectedBeforeWrite(selectionReviewInput(context = "c".repeat(1_000_001)))
  }

  @Test
  fun oversizedAnchorIsRejectedBeforeAnySafWrite() {
    val oversizedAnchor = JsonPrimitive("a".repeat(500_000))
    assertRejectedBeforeWrite(selectionReviewInput(anchor = oversizedAnchor))
  }

  private fun installFixture(reviewBytes: ByteArray? = null, omitReviews: Boolean = false) {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"a".repeat(64)}","name":"Pattern recognition fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    val pdf = createPdfFixture()
    val notes = fixtureNotes().toByteArray(Charsets.UTF_8)
    val reviews = """
      [
        {"id":"review-1","number":1,"side":"note","kind":"overall","page":1,"scope":"page","instruction":"legacy one","quote":"","context":"","revision":null,"anchor":null,"status":"open","created":"2026-01-01T00:00:00Z","desktopOnly":{"nested":"preserved"}},
        {"id":"review-10","number":10,"side":"note","kind":"overall","page":2,"scope":"page","instruction":"legacy ten","quote":"","context":"","revision":null,"anchor":null,"status":"open","created":"2026-01-01T00:00:00Z","desktopOnly":"also preserved"}
      ]
    """.trimIndent().toByteArray(Charsets.UTF_8)
    resolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, pdf)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, notes)
        putBoolean(ExistingProjectSafTestProvider.EXTRA_OMIT_REVIEWS, omitReviews)
        if (!omitReviews) putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, reviewBytes ?: reviews)
      },
    )
  }

  private fun selectionReviewInput(
    quote: String = "selected",
    context: String = "# Page\nselected",
    anchor: JsonElement = buildJsonObject {
      put("coordinateSystem", JsonPrimitive("markdown-source-utf16"))
      put("start", JsonPrimitive(7))
      put("end", JsonPrimitive(15))
    },
  ) = ReviewCreate(
    side = "note",
    kind = "selection",
    page = 3,
    instruction = "  Review this selection  ",
    scope = "page",
    quote = quote,
    context = context,
    revision = "revision-abc",
    anchor = anchor,
  )

  private fun assertRejectedBeforeWrite(input: ReviewCreate) {
    installFixture()
    val opened = ProjectDocumentOpener(resolver).open(treeUri)
    val reviewsUri = requireNotNull(opened.reviewsUri)
    val store = ProjectReviewStore(resolver, opened.sourceDirectoryUri, reviewsUri)
    val before = readReviews(reviewsUri)
    val writesBefore = reviewWriteCount()

    try {
      store.create(input)
      fail("oversized review data should be rejected")
    } catch (_: IllegalArgumentException) {
      // Expected: validation must run before opening the SAF output stream.
    }

    assertEquals(writesBefore, reviewWriteCount())
    assertEquals(before, readReviews(reviewsUri))
  }

  private fun readReviews(uri: Uri): String =
    resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
      ?: error("review JSON should be readable")

  private fun reviewWriteCount(): Int = resolver.call(
    ExistingProjectSafTestProvider.AUTHORITY,
    ExistingProjectSafTestProvider.METHOD_GET_REVIEW_WRITE_COUNT,
    null,
    null,
  )?.getInt(ExistingProjectSafTestProvider.EXTRA_COUNT) ?: -1

  private fun reviewWriteFailuresRemaining(): Int = resolver.call(
    ExistingProjectSafTestProvider.AUTHORITY,
    ExistingProjectSafTestProvider.METHOD_GET_REVIEW_WRITE_COUNT,
    null,
    null,
  )?.getInt(ExistingProjectSafTestProvider.EXTRA_FAILURES) ?: -1

  private fun configureReviewWriteFailures(count: Int) {
    resolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_CONFIGURE_REVIEW_WRITE_FAILURE,
      null,
      Bundle().apply { putInt(ExistingProjectSafTestProvider.EXTRA_FAILURES, count) },
    )
  }

  private fun configureRecoveryDeleteFailure(count: Int) {
    resolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_CONFIGURE_RECOVERY_DELETE_FAILURE,
      null,
      Bundle().apply { putInt(ExistingProjectSafTestProvider.EXTRA_FAILURES, count) },
    )
  }

  private fun recoveryCopies(): List<Pair<String, String>> {
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, ExistingProjectSafTestProvider.SRC_ID)
    return resolver.query(
      children,
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      ),
      null,
      null,
      null,
    )!!.use { cursor ->
      buildList {
        val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        while (cursor.moveToNext()) {
          val name = cursor.getString(nameColumn)
          if (name.startsWith("pagenote-recovery-")) {
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn))
            add(name to readReviews(uri))
          }
        }
      }
    }
  }

  private fun recoveryCopyNames(): List<String> = recoveryCopies().map { it.first }

  private fun fixtureNotes(): String = PageNotes.join(fixtureNotesPages())

  private fun fixtureNotesPages(): List<String> = (1..69).map { "Original notes for page $it" }

  private fun createPdfFixture(): ByteArray {
    val document = PdfDocument()
    val markers = mapOf(1 to Color.RED, 35 to Color.GREEN, 69 to Color.BLUE)
    for (pageNumber in 1..69) {
      val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, pageNumber).create())
      val canvas: Canvas = page.canvas
      canvas.drawColor(Color.WHITE)
      val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = markers[pageNumber] ?: Color.DKGRAY }
      canvas.drawRect(60f, 60f, 120f, 120f, paint)
      canvas.drawText("Pattern recognition page $pageNumber", 36f, 220f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 18f
      })
      document.finishPage(page)
    }
    return ByteArrayOutputStream().use { output ->
      try {
        document.writeTo(output)
        output.toByteArray()
      } finally {
        document.close()
      }
    }
  }
}

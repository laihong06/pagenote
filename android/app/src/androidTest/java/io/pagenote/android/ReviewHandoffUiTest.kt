package io.pagenote.android

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.agent.ToolRequest
import io.pagenote.agent.ToolResult
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.annotations.ReviewCreate
import io.pagenote.feature.notes.NotePage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewHandoffUiTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val testResolver get() = InstrumentationRegistry.getInstrumentation().context.contentResolver
  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )

  @Test
  fun reviewCanBeDismissedOrDeletedButNotManuallyApplied() {
    val project = installProject()
    val reviewStore = ProjectReviewStore(testResolver, project.sourceDirectoryUri, project.reviewsUri)
    val created = reviewStore.create(
      side = "note",
      kind = "overall",
      page = 1,
      instruction = "Review the page",
      revision = PageNotes.revision("Review fixture note"),
    )
    val id = created.getValue("id").jsonPrimitive.content
    val appliedLabel = targetContext.getString(R.string.review_applied)
    val dismissedLabel = targetContext.getString(R.string.review_dismissed)
    val deleteLabel = targetContext.getString(R.string.delete)

    composeRule.setContent {
      MaterialTheme {
        ReviewListDialog(
          page = 1,
          project = project,
          noteRevision = PageNotes.revision("Review fixture note"),
          onDismiss = {},
          onChanged = {},
        )
      }
    }

    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithText("Review the page").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithText("Review the page").assertIsDisplayed()
    assertTrue(composeRule.onAllNodesWithText(appliedLabel).fetchSemanticsNodes().isEmpty())
    composeRule.onNodeWithText(dismissedLabel).assertIsDisplayed().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithText("dismissed").fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals("dismissed", reviewStore.find(id)?.get("status")?.jsonPrimitive?.content)

    composeRule.onNodeWithText(deleteLabel).assertIsDisplayed().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithText("Review the page").fetchSemanticsNodes().isEmpty()
    }
    assertEquals(null, reviewStore.find(id))
    assertEquals(0, reviewStore.list(1).size)
  }

  @Test
  fun applyRequiresSameRunChangedWriteForTheReviewPageAndRevision() = runBlocking {
    val initialNotes = PageNotes.join(listOf("First page", "Second page"))
    val project = installProject(initialNotes)
    val noteStore = ProjectNoteStore(testResolver, project.notesUri)
    val originalNotes = noteStore.read()
    val firstPage = NotePage.pageAt(originalNotes, 1)
    val secondPage = NotePage.pageAt(originalNotes, 2)
    val firstRevision = PageNotes.revision(firstPage)
    val secondRevision = PageNotes.revision(secondPage)
    val reviewStore = ProjectReviewStore(testResolver, project.sourceDirectoryUri, project.reviewsUri)
    val firstReview = reviewStore.create("note", "overall", 1, "Review first page", firstRevision)
    val secondReview = reviewStore.create("note", "overall", 2, "Review second page", secondRevision)
    val firstId = firstReview.getValue("id").jsonPrimitive.content
    val secondId = secondReview.getValue("id").jsonPrimitive.content
    val runHandlers = ProjectAgentTools.handlers(testResolver, project, null, currentPage = 1)

    suspend fun call(
      handlers: Map<String, suspend (ToolRequest) -> ToolResult>,
      name: String,
      args: Map<String, String>,
    ): ToolResult =
      handlers.getValue(name)(ToolRequest(name, args))

    val noOp = call(
      runHandlers,
      "write_note",
      mapOf("page" to "1", "markdown" to firstPage, "expectedRevision" to firstRevision),
    )
    assertTrue(noOp.ok)
    assertEquals(null, noOp.updatedPage)
    assertFalse(
      call(
        runHandlers,
        "write_review_status",
        mapOf("id" to firstId, "status" to "applied", "noteRewritten" to "true"),
      ).ok,
    )

    val staleWrite = call(
      runHandlers,
      "write_note",
      mapOf("page" to "1", "markdown" to "stale", "expectedRevision" to "stale-revision"),
    )
    assertFalse(staleWrite.ok)
    assertFalse(
      call(
        runHandlers,
        "write_review_status",
        mapOf("id" to firstId, "status" to "applied", "noteRewritten" to "true"),
      ).ok,
    )

    val changedOtherPage = call(
      runHandlers,
      "write_note",
      mapOf("page" to "2", "markdown" to "Second page revised", "expectedRevision" to secondRevision),
    )
    assertEquals(2, changedOtherPage.updatedPage)
    assertFalse(
      call(
        runHandlers,
        "write_review_status",
        mapOf("id" to firstId, "status" to "applied"),
      ).ok,
    )

    val otherRun = ProjectAgentTools.handlers(testResolver, project, null, currentPage = 1)
    assertFalse(
      call(
        otherRun,
        "write_review_status",
        mapOf("id" to secondId, "status" to "applied", "noteRewritten" to "true"),
      ).ok,
    )
    assertTrue(
      call(
        runHandlers,
        "write_review_status",
        mapOf("id" to secondId, "status" to "applied", "noteRewritten" to "false"),
      ).ok,
    )

    val changedTargetPage = call(
      runHandlers,
      "patch_note",
      mapOf(
        "page" to "1",
        "start" to "0",
        "end" to "0",
        "replacement" to "Updated: ",
        "expectedRevision" to firstRevision,
      ),
    )
    assertEquals(1, changedTargetPage.updatedPage)
    assertTrue(
      call(
        runHandlers,
        "write_review_status",
        mapOf("id" to firstId, "status" to "applied", "noteRewritten" to "false"),
      ).ok,
    )
  }

  @Test
  fun applyRejectsReviewRevisionChangedAfterItsNoteWasRewritten() = runBlocking {
    val project = installProject("Original note")
    val noteStore = ProjectNoteStore(testResolver, project.notesUri)
    val original = noteStore.read()
    val page = NotePage.pageAt(original, 1)
    val originalRevision = PageNotes.revision(page)
    val reviewStore = ProjectReviewStore(testResolver, project.sourceDirectoryUri, project.reviewsUri)
    val review = reviewStore.create("note", "selection", 1, "Apply selected edit", originalRevision)
    val id = review.getValue("id").jsonPrimitive.content
    val handlers = ProjectAgentTools.handlers(testResolver, project, null, currentPage = 1)
    val changed = handlers.getValue("write_note")(
      ToolRequest(
        "write_note",
        mapOf("page" to "1", "markdown" to "Rewritten note", "expectedRevision" to originalRevision),
      ),
    )
    assertTrue(changed.ok)
    assertEquals(1, changed.updatedPage)

    val updatedRecord = JsonObject(
      requireNotNull(reviewStore.find(id)) + mapOf(
        "revision" to JsonPrimitive("concurrent-review-revision"),
        "concurrentMarker" to JsonPrimitive("preserve this update"),
      ),
    )
    val records = JsonArray(reviewStore.list().map { element ->
      val record = element.jsonObject
      if (record["id"]?.jsonPrimitive?.content == id) updatedRecord else record
    })
    testResolver.openOutputStream(requireNotNull(project.reviewsUri), "wt")!!.bufferedWriter(Charsets.UTF_8).use {
      it.write(Json.encodeToString(JsonArray.serializer(), records))
    }

    val applied = handlers.getValue("write_review_status")(
      ToolRequest("write_review_status", mapOf("id" to id, "status" to "applied")),
    )

    assertFalse(applied.ok)
    assertEquals("concurrent-review-revision", reviewStore.find(id)?.get("revision")?.jsonPrimitive?.content)
    assertEquals("preserve this update", reviewStore.find(id)?.get("concurrentMarker")?.jsonPrimitive?.content)
    assertEquals("open", reviewStore.find(id)?.get("status")?.jsonPrimitive?.content)
  }

  @Test
  fun separateStoreInstancesSerializeOverlappingCreates() {
    val project = installProject()
    val secondStarted = CountDownLatch(1)
    val secondThread = AtomicReference<Thread>()
    val executor = Executors.newFixedThreadPool(2)
    val source = project.sourceDirectoryUri
    val reviews = project.reviewsUri
    val firstStore = ProjectReviewStore(testResolver, source, reviews)
    val secondStore = ProjectReviewStore(testResolver, source, reviews)

    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_PAUSE_NEXT_REVIEW_READ,
      null,
      null,
    )
    val first = executor.submit {
      firstStore.create("note", "overall", 1, "first create", "revision-1")
    }
    try {
      val pause = testResolver.call(
        ExistingProjectSafTestProvider.AUTHORITY,
        ExistingProjectSafTestProvider.METHOD_WAIT_REVIEW_SNAPSHOT,
        null,
        null,
      )
      assertTrue(pause?.getBoolean("ready") == true)

      val second = executor.submit {
        secondThread.set(Thread.currentThread())
        secondStarted.countDown()
        secondStore.create("note", "overall", 1, "second create", "revision-2")
      }
      assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
      while (!second.isDone && secondThread.get()?.state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
        Thread.sleep(10)
      }
      assertTrue(second.isDone || secondThread.get()?.state == Thread.State.BLOCKED)
      testResolver.call(
        ExistingProjectSafTestProvider.AUTHORITY,
        ExistingProjectSafTestProvider.METHOD_RELEASE_REVIEW_READ,
        null,
        null,
      )
      first.get(5, TimeUnit.SECONDS)
      second.get(5, TimeUnit.SECONDS)
      assertEquals(2, ProjectReviewStore(testResolver, source, reviews).list().size)
    } finally {
      testResolver.call(
        ExistingProjectSafTestProvider.AUTHORITY,
        ExistingProjectSafTestProvider.METHOD_RELEASE_REVIEW_READ,
        null,
        null,
      )
      executor.shutdownNow()
    }
  }

  @Test
  fun readReviewHandlerReturnsDetailAndFilteredBoundedSummaries() = runBlocking {
    val project = installProject()
    val store = ProjectReviewStore(testResolver, project.sourceDirectoryUri, project.reviewsUri)
    val records = (1..23).map { index ->
      store.create(
        ReviewCreate(
          side = "note",
          kind = "selection",
          page = index,
          instruction = "instruction-$index-" + "i".repeat(180),
          quote = "quote-$index-" + "q".repeat(350),
          context = "full context $index",
          revision = "revision-$index",
          anchor = buildJsonObject {
            put("coordinateSystem", JsonPrimitive("markdown-source-utf16"))
            put("start", JsonPrimitive(0))
            put("end", JsonPrimitive(5))
          },
        ),
      )
    }
    val dismissedId = records.last().getValue("id").jsonPrimitive.content
    store.transition(dismissedId, "dismissed")
    val id = records.first().getValue("id").jsonPrimitive.content
    val handler = ProjectAgentTools.handlers(testResolver, project, null, currentPage = 1).getValue("read_review")

    val detailResult = handler(ToolRequest("read_review", mapOf("id" to id)))
    assertTrue(detailResult.ok)
    val detail = Json.parseToJsonElement(detailResult.detail).jsonObject
    assertEquals("quote-1-" + "q".repeat(350), detail["quote"]?.jsonPrimitive?.content)
    assertEquals("full context 1", detail["context"]?.jsonPrimitive?.content)
    assertEquals("revision-1", detail["revision"]?.jsonPrimitive?.content)
    assertEquals(records.first()["anchor"], detail["anchor"])

    val listResult = handler(ToolRequest("read_review", mapOf("status" to "open")))
    assertTrue(listResult.ok)
    assertTrue(listResult.detail.startsWith("reviews=22\n"))
    val summaries = Json.parseToJsonElement(listResult.detail.substringAfter('\n')).jsonArray
    assertEquals(20, summaries.size)
    assertTrue(summaries.all { it.jsonObject["status"]?.jsonPrimitive?.content == "open" })
    val first = summaries.first().jsonObject
    assertEquals(160, first["instruction"]?.jsonPrimitive?.content?.length)
    assertTrue(first["instruction"]?.jsonPrimitive?.content?.endsWith("...[truncated]") == true)
    assertEquals(320, first["quote"]?.jsonPrimitive?.content?.length)
    assertTrue(first["quote"]?.jsonPrimitive?.content?.endsWith("...[truncated]") == true)
    assertFalse("context" in first)
    assertFalse("anchor" in first)
    assertTrue(summaries.none { it.jsonObject["id"]?.jsonPrimitive?.content == dismissedId })
  }

  @Test
  fun readReviewHandlerRejectsIdsLongerThan128Characters() = runBlocking {
    val project = installProject()
    val handler = ProjectAgentTools.handlers(testResolver, project, null, currentPage = 1)
      .getValue("read_review")

    val result = handler(ToolRequest("read_review", mapOf("id" to "i".repeat(129))))

    assertFalse(result.ok)
    assertTrue(result.detail.contains("128"))
  }

  private fun installProject(): OpenedProject {
    return installProject("Review fixture note")
  }

  private fun installProject(note: String): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"a".repeat(64)}","name":"Review UI fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, "%PDF-fixture".toByteArray())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, note.toByteArray(Charsets.UTF_8))
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray(Charsets.UTF_8))
      },
    )
    return ProjectDocumentOpener(testResolver).open(treeUri)
  }
}

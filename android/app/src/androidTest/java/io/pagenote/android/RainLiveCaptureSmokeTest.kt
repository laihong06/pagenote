package io.pagenote.android

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.yuketang.RainClassroomClient
import io.pagenote.feature.yuketang.RainLesson
import io.pagenote.feature.yuketang.matchCurrentLessonCourse
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

class RainLiveCaptureSmokeTest {
  @Test(timeout = 180_000)
  fun authorizedLiveClassCreatesProjectOnFirstSlideAndAppendsLaterSlides(): Unit = runBlocking {
    assumeTrue(InstrumentationRegistry.getArguments().getString("rainLiveCaptureSmoke") == "true")
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = context.contentResolver
    val client = RainClassroomClient(context)
    assertEquals("authenticated", client.refreshLogin().status)
    val courses = client.listCourses()
    val current = client.listCurrentLessons().firstOrNull { matchCurrentLessonCourse(it, courses) != null }
      ?: error("No current classroom can be matched to the signed-in course list")
    val course = checkNotNull(matchCurrentLessonCourse(current, courses))
    val lesson = RainLesson(course.classroomId, current.lessonId, "", current.name)
    val tree = ProjectCreateSafTestProvider.treeUri()
    val name = "rain-live-smoke-${UUID.randomUUID()}"
    val capture = RainLiveProjectCapture(context, tree, name, course, lesson)
    val writes = Mutex()
    val received = Channel<RainLiveProjectWrite>(Channel.UNLIMITED)
    var closed = false
    try {
      capture.prepare()
      client.startLive(lesson) { image ->
        writes.withLock {
          if (!closed) {
            val written = capture.accept(image)
            if (written.pageNumber != null) received.trySend(written)
          }
        }
      }
      val first = withTimeoutOrNull(60_000) { received.receive() }
        ?: error("No first slide received; live status=${client.live.value.status}, pending=${client.live.value.pendingPages}")
      println("RAIN_LIVE_CAPTURE_SMOKE firstPage=${first.pageNumber}")
      val settled = withTimeoutOrNull(120_000) {
        client.live.first { (it.initialTimelineReceived && it.backfillPendingPages == 0) || it.error != null }
      } ?: error("Initial timeline backfill did not complete")
      assertNull("Initial timeline must not lose failed pages", settled.error)
      var initialLatest = first
      while (true) initialLatest = received.tryReceive().getOrNull() ?: break
      println("RAIN_LIVE_CAPTURE_SMOKE initialPages=${initialLatest.pageNumber}")
      val second = withTimeoutOrNull(10_000) { received.receive() }
      assertNull("Live capture must not hide slide-processing failures", client.live.value.error)
      assertEquals(0, client.live.value.pendingPages)
      client.stopLive()
      delay(500)
      writes.withLock {
        closed = true
        val latest = second ?: initialLatest
        val pdfPages = resolver.openInputStream(latest.project.sourceUri)!!.use { input -> PDDocument.load(input).use { it.numberOfPages } }
        val notes = ProjectNoteStore(resolver, latest.project.notesUri).read()
        val sourceUri = Uri.parse(checkNotNull(SafPreparedDocumentAccess(resolver).findChild(latest.project.sourceDirectoryUri.toString(), "yuketang-source.json")))
        val source = resolver.openInputStream(sourceUri)!!.bufferedReader().use { JSONObject(it.readText()) }
        assertTrue(pdfPages >= 1)
        assertEquals(pdfPages, PageNotes.split(notes).size)
        assertEquals(pdfPages, source.getJSONArray("pages").length())
        assertEquals(current.lessonId, source.getString("lessonId"))
        val pages = source.getJSONArray("pages")
        println("RAIN_LIVE_CAPTURE_SMOKE pdfPages=$pdfPages notePages=${PageNotes.split(notes).size} sourceEntries=${pages.length()} firstSourceIndex=${pages.getJSONObject(0).getInt("sourceIndex")} laterSlide=${second != null}")
      }
    } finally {
      client.stopLive()
      writes.withLock {
        closed = true
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        SafPreparedDocumentAccess(resolver).findChild(parent.toString(), name)?.let { DocumentsContract.deleteDocument(resolver, Uri.parse(it)) }
      }
      received.close()
    }
  }
}

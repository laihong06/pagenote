package io.pagenote.android

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.pagenote.feature.yuketang.RainClassroomClient
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Explicitly opted-in, read-only report access: never joins or signs in to a class. */
class RainTestCourseOrderSmokeTest {
  @Test(timeout = 240_000) fun endedTestCourseImportsAsOrderedPptSections(): Unit = runBlocking {
    assumeTrue(InstrumentationRegistry.getArguments().getString("rainTestCourse") == "true")
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = context.contentResolver
    val client = RainClassroomClient(context)
    assertEquals("Sign in inside pagenote before running the test-course probe", "authenticated", client.refreshLogin().status)
    val course = client.listCourses().singleOrNull { it.name.trim().equals("test", ignoreCase = true) }
      ?: error("Expected exactly one named test course; no other course will be opened")
    val lesson = client.listLessons(course.classroomId).firstOrNull { !it.lessonId.isNullOrBlank() }
      ?: error("No historical report in the named test course")
    val images = client.downloadPresentation(lesson)
    assertTrue("The test lesson report has no slides", images.isNotEmpty())
    val grouped = images.groupBy { it.slide.presentationId }
    // Replay the actual report pages late/interleaved without entering any live class.
    val replay = grouped.values.map { it.last() } + grouped.values.flatMap { it.dropLast(1).reversed() }
    var root: Uri? = null
    try {
      val target = RainProjectWriter(context).create(ProjectCreateSafTestProvider.treeUri(), "test-course-order-${UUID.randomUUID()}", course, lesson) { emit -> replay.forEach { emit(it) } }
      root = DocumentsContract.buildDocumentUriUsingTree(target.accessTreeUri, target.rootDocumentId)
      val opened = ProjectDocumentOpener(resolver).open(target.accessTreeUri, target.rootDocumentId)
      val file = Uri.parse(checkNotNull(SafPreparedDocumentAccess(resolver).findChild(opened.sourceDirectoryUri.toString(), "yuketang-source.json")))
      val source = resolver.openInputStream(file)!!.bufferedReader().use { JSONObject(it.readText()) }
      val pages = source.getJSONArray("pages")
      assertEquals(images.size, pages.length())
      val expected = grouped.values.flatMap { group -> group.sortedBy { it.slide.sourceIndex } }.map { it.slide.pageId }
      assertEquals(expected, (0 until pages.length()).map { pages.getJSONObject(it).getString("pageId") })
      assertEquals(grouped.size, source.getJSONArray("sections").length())
      resolver.openInputStream(opened.sourceUri)!!.use { input -> PDDocument.load(input).use { assertEquals(images.size, it.numberOfPages) } }
      println("RAIN_TEST_COURSE_ORDER historicalPages=${images.size} pptSections=${grouped.size} sorted=true liveClassJoined=false")
    } finally { root?.let { DocumentsContract.deleteDocument(resolver, it) } }
  }
}

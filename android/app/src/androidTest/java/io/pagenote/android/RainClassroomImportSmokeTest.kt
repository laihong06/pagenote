package io.pagenote.android

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.yuketang.RainClassroomClient
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

class RainClassroomImportSmokeTest {
  @Test(timeout = 240_000)
  fun loggedInAccountImportsHistoricalClassroomIntoStandardSafProject() = runBlocking {
    assumeTrue(InstrumentationRegistry.getArguments().getString("rainLiveSmoke") == "true")
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = context.contentResolver
    val client = RainClassroomClient(context)
    assertEquals("Sign in to Rain Classroom inside pagenote before this opt-in test", "authenticated", client.refreshLogin().status)
    val arguments = InstrumentationRegistry.getArguments()
    val courseIndex = arguments.getString("rainCourseIndex")?.toIntOrNull() ?: 0
    val lessonIndex = arguments.getString("rainLessonIndex")?.toIntOrNull() ?: 0
    val course = client.listCourses().getOrNull(courseIndex) ?: error("Selected course does not exist")
    val lesson = client.listLessons(course.classroomId).getOrNull(lessonIndex) ?: error("Selected classroom report does not exist")
    assertTrue("Select a historical classroom report", !lesson.lessonId.isNullOrBlank())
    var root: Uri? = null
    try {
      val target = RainProjectWriter(context).create(
        ProjectCreateSafTestProvider.treeUri(),
        "rain-import-smoke-${UUID.randomUUID()}",
        course,
        lesson,
      ) { emit -> client.forEachPresentationPage(lesson, emit) }
      root = DocumentsContract.buildDocumentUriUsingTree(target.accessTreeUri, target.rootDocumentId)
      val access = SafPreparedDocumentAccess(resolver)
      val src = checkNotNull(access.findChild(root.toString(), "src"))
      val pdf = Uri.parse(checkNotNull(access.findChild(src, "document.pdf")))
      val notes = Uri.parse(checkNotNull(access.findChild(src, "right.md")))
      val source = Uri.parse(checkNotNull(access.findChild(src, "yuketang-source.json")))
      val pageCount = resolver.openInputStream(pdf)!!.use { input ->
        PDDocument.load(input).use { it.numberOfPages }
      }
      val noteText = resolver.openInputStream(notes)!!.bufferedReader().use { it.readText() }
      val manifest = resolver.openInputStream(source)!!.bufferedReader().use { JSONObject(it.readText()) }
      assertTrue(pageCount > 0)
      assertEquals(pageCount, PageNotes.split(noteText).size)
      assertEquals(pageCount, manifest.getJSONArray("pages").length())
      assertEquals(lesson.lessonId, manifest.getString("lessonId"))
      assertEquals(manifest.getJSONArray("pages").getJSONObject(0).getString("presentationId"), manifest.getString("presentationId"))
      assertTrue(access.findChild(root.toString(), "project.pagenote") != null)
      println("RAIN_IMPORT_SMOKE imported=$pageCount pdfPages=$pageCount notePages=$pageCount sourceEntries=$pageCount")
    } finally {
      root?.let { DocumentsContract.deleteDocument(resolver, it) }
    }
  }
}

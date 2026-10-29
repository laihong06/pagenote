package io.pagenote.android

import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.yuketang.RainCourse
import io.pagenote.feature.yuketang.RainLesson
import io.pagenote.feature.yuketang.RainSlide
import io.pagenote.feature.yuketang.RainSlideImage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

class RainLiveProjectCaptureTest {
  @Test
  fun firstSlideCreatesProjectThenReplayAndAppendPreserveNotes(): Unit = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = context.contentResolver
    val tree = ProjectCreateSafTestProvider.treeUri()
    val name = "rain-live-${UUID.randomUUID()}"
    val course = RainCourse("course", "room", "Course")
    val lesson = RainLesson("room", "lesson", "", "Lecture")
    val capture = RainLiveProjectCapture(context, tree, name, course, lesson)
    val bitmap = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888)
    val bytes = try {
      ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
    } finally { bitmap.recycle() }
    fun slide(index: Int) = RainSlideImage(RainSlide("slide-$index", index, "deck", "https://qn-scd1.yuketang.cn/page"), "image/png", bytes)
    var root: Uri? = null
    try {
      capture.prepare()
      val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
      assertNull(SafPreparedDocumentAccess(resolver).findChild(parent.toString(), name))
      val first = capture.accept(slide(0))
      root = DocumentsContract.buildDocumentUriUsingTree(first.target.accessTreeUri, first.target.rootDocumentId)
      assertEquals(1, first.pageNumber)
      resolver.openInputStream(first.project.sourceUri)!!.use { input -> PDDocument.load(input).use { assertEquals(1, it.numberOfPages) } }
      val notes = ProjectNoteStore(resolver, first.project.notesUri)
      notes.write("Saved page-one note", notes.read())
      assertNull(capture.accept(slide(0)).pageNumber)
      val second = capture.accept(slide(1))
      assertEquals(first.target, second.target)
      assertEquals(2, second.pageNumber)
      resolver.openInputStream(second.project.sourceUri)!!.use { input -> PDDocument.load(input).use { assertEquals(2, it.numberOfPages) } }
      assertEquals(listOf("Saved page-one note", ""), PageNotes.split(notes.read()))
      val source = Uri.parse(checkNotNull(SafPreparedDocumentAccess(resolver).findChild(first.project.sourceDirectoryUri.toString(), "yuketang-source.json")))
      val manifest = resolver.openInputStream(source)!!.bufferedReader().use { JSONObject(it.readText()) }
      val pages = manifest.getJSONArray("pages")
      assertEquals(2, pages.length())
      assertEquals(listOf("slide-0", "slide-1"), (0 until pages.length()).map { pages.getJSONObject(it).getString("pageId") })
      assertEquals("lesson", manifest.getString("lessonId"))
      assertEquals("deck", manifest.getString("presentationId"))
      assertThrows(IllegalArgumentException::class.java) { runBlocking { RainLiveProjectCapture(context, tree, name, course, lesson).prepare() } }
    } finally { root?.let { DocumentsContract.deleteDocument(resolver, it) } }
  }
}

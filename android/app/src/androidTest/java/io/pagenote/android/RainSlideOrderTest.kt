package io.pagenote.android

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.yuketang.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

class RainSlideOrderTest {
  @Test fun latePagesStayInFirstSeenDeckOrderAndCarryTheirNotesAndInk(): Unit = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resolver = context.contentResolver
    val access = SafPreparedDocumentAccess(resolver)
    val course = RainCourse("course", "room", "test")
    val lesson = RainLesson("room", "lesson", "", "test")
    val capture = RainLiveProjectCapture(context, ProjectCreateSafTestProvider.treeUri(), "rain-order-${UUID.randomUUID()}", course, lesson)
    fun slide(deck: String, index: Int, color: Int): RainSlideImage {
      val bitmap = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
      val bytes = try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() } }
      finally { bitmap.recycle() }
      return RainSlideImage(RainSlide("$deck-$index", index, deck, "https://qn-scd1.yuketang.cn/fixture"), "image/png", bytes)
    }
    var root: Uri? = null
    try {
      val a5 = slide("A", 5, Color.RED)
      val first = capture.accept(a5)
      root = DocumentsContract.buildDocumentUriUsingTree(first.target.accessTreeUri, first.target.rootDocumentId)
      val noteStore = ProjectNoteStore(resolver, first.project.notesUri)
      noteStore.write("A5 note", noteStore.read())
      val second = capture.accept(slide("B", 3, Color.BLUE))
      noteStore.write(PageNotes.join(listOf("A5 note", "B3 note")), noteStore.read())
      val src = second.project.sourceDirectoryUri
      fun put(name: String, value: String) {
        val file = Uri.parse(access.createFile(src.toString(), "application/json", name))
        resolver.openOutputStream(file, "wt")!!.bufferedWriter().use { it.write(value) }
      }
      fun read(name: String): String = resolver.openInputStream(Uri.parse(checkNotNull(access.findChild(src.toString(), name))))!!.bufferedReader().use { it.readText() }
      put("annotations.json", """{"document":{"1":[{"type":"pen","tag":"A5"}],"2":[{"type":"highlight","tag":"B3"}]},"note":{"2":[{"type":"text","tag":"B3-note"}]},"extra":"keep"}""")
      put("ai-reviews.json", """[{"id":"a","page":1,"anchor":{"page":1}},{"id":"b","page":2}]""")
      put("ai-handoffs.json", """{"2":{"summary":"B3 summary","noteRevision":"keep"}}""")
      NoteHistoryMirror.snapshotPage(context.filesDir, first.project.manifest.id, 1, "A5 old note")
      NoteHistoryMirror.snapshotPage(context.filesDir, first.project.manifest.id, 2, "B3 old note")
      val originals = listOf("right.md", "annotations.json", "ai-reviews.json", "ai-handoffs.json", "yuketang-source.json").associateWith(::read)
      val pdfBefore = resolver.openInputStream(first.project.sourceUri)!!.use { it.readBytes() }
      ProjectCreateSafTestProvider.failNextWrite(resolver, "/yuketang-source.json")
      try {
        capture.accept(slide("A", 1, Color.GREEN))
        fail("sidecar failure must reject the insertion")
      } catch (expected: java.io.IOException) {
        assertArrayEquals(pdfBefore, resolver.openInputStream(first.project.sourceUri)!!.use { it.readBytes() })
        originals.forEach { (name, text) -> assertEquals(name, text, read(name)) }
        assertEquals(1, NoteHistoryMirror.entries(context.filesDir, first.project.manifest.id, 1).size)
        assertEquals(1, NoteHistoryMirror.entries(context.filesDir, first.project.manifest.id, 2).size)
      }
      assertEquals("late A page belongs before A5, not after B3", 1, capture.accept(slide("A", 1, Color.GREEN)).pageNumber)
      assertEquals(3, capture.accept(slide("B", 0, Color.YELLOW)).pageNumber)
      assertEquals(2, capture.accept(slide("A", 3, Color.MAGENTA)).pageNumber)
      assertNull(capture.accept(a5).pageNumber)
      assertEquals(3, capture.accept(a5).focusPage)
      val source = JSONObject(read("yuketang-source.json"))
      val pages = source.getJSONArray("pages")
      assertEquals(listOf("A-1", "A-3", "A-5", "B-0", "B-3"), (0 until pages.length()).map { pages.getJSONObject(it).getString("pageId") })
      assertEquals(listOf(1, 2, 3, 4, 5), (0 until pages.length()).map { pages.getJSONObject(it).getInt("projectPage") })
      assertEquals(listOf("", "", "A5 note", "", "B3 note"), PageNotes.split(noteStore.read()))
      assertEquals(1, NoteHistoryMirror.entries(context.filesDir, first.project.manifest.id, 3).size)
      assertEquals(1, NoteHistoryMirror.entries(context.filesDir, first.project.manifest.id, 5).size)
      assertEquals("A5 old note", NoteHistoryMirror.readVersion(context.filesDir, first.project.manifest.id, NoteHistoryMirror.entries(context.filesDir, first.project.manifest.id, 3).single().id))
      assertEquals("B3 old note", NoteHistoryMirror.readVersion(context.filesDir, first.project.manifest.id, NoteHistoryMirror.entries(context.filesDir, first.project.manifest.id, 5).single().id))
      val annotations = JSONObject(read("annotations.json"))
      assertEquals("A5", annotations.getJSONObject("document").getJSONArray("3").getJSONObject(0).getString("tag"))
      assertEquals("B3", annotations.getJSONObject("document").getJSONArray("5").getJSONObject(0).getString("tag"))
      assertEquals("B3-note", annotations.getJSONObject("note").getJSONArray("5").getJSONObject(0).getString("tag"))
      assertEquals("keep", annotations.getString("extra"))
      val reviews = JSONArray(read("ai-reviews.json"))
      assertEquals(3, reviews.getJSONObject(0).getInt("page"))
      assertEquals(3, reviews.getJSONObject(0).getJSONObject("anchor").getInt("page"))
      assertEquals(5, reviews.getJSONObject(1).getInt("page"))
      assertEquals("B3 summary", JSONObject(read("ai-handoffs.json")).getJSONObject("5").getString("summary"))
      resolver.openInputStream(first.project.sourceUri)!!.use { input -> PDDocument.load(input).use { pdf ->
        assertEquals(5, pdf.numberOfPages)
        val renderer = PDFRenderer(pdf)
        for ((i, color) in listOf(Color.GREEN, Color.MAGENTA, Color.RED, Color.YELLOW, Color.BLUE).withIndex()) {
          val image = renderer.renderImage(i)
          try { assertEquals(color, image.getPixel(image.width / 2, image.height / 2)) } finally { image.recycle() }
        }
        val outline = checkNotNull(pdf.documentCatalog.documentOutline)
        assertEquals("PPT 1", outline.firstChild.title)
        assertEquals("PPT 2", outline.firstChild.nextSibling.title)
      } }
    } finally {
      ProjectCreateSafTestProvider.clearTestFailures(resolver)
      root?.let { projectRoot ->
        val opened = ProjectDocumentOpener(resolver).open(ProjectCreateSafTestProvider.treeUri(), DocumentsContract.getDocumentId(projectRoot))
        NoteHistoryMirror.directory(context.filesDir, opened.manifest.id)?.deleteRecursively()
      }
      root?.let { DocumentsContract.deleteDocument(resolver, it) }
    }
  }
}

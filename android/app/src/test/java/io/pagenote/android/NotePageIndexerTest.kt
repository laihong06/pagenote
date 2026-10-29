package io.pagenote.android

import io.pagenote.core.search.SearchDocumentKind
import io.pagenote.core.search.SearchRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePageIndexerTest {
  @Test
  fun splitsNoteMarkdownIntoRevisedPageDocs() {
    val markdown = "第一页内容\n\n<!-- pagebreak -->\n\n第二页内容"
    val docs = NotePageIndexer.noteDocs("p1", markdown)
    assertEquals(2, docs.size)
    assertEquals(1, docs[0].page)
    assertEquals(SearchDocumentKind.NOTE, docs[0].kind)
    assertEquals("第一页内容", docs[0].content.trim())
    assertEquals("p1", docs[0].projectId)
    assertTrue(docs[0].revision.length == 64)
  }

  @Test
  fun blankPagesAreNotIndexed() {
    val docs = NotePageIndexer.noteDocs("p1", "有内容\n\n<!-- pagebreak -->\n\n   \n\n<!-- pagebreak -->\n\n")
    assertEquals(1, docs.size)
    assertEquals(1, docs.single().page)
  }

  @Test
  fun sourceDocsUseSourceKind() {
    val docs = NotePageIndexer.sourceDocs("p1", "源页一\n\n<!-- pagebreak -->\n\n源页二")
    assertEquals(2, docs.size)
    assertEquals(SearchDocumentKind.SOURCE, docs[1].kind)
    assertEquals(2, docs[1].page)
  }

  @Test
  fun ocrAndTranscriptDocsCarryRectAndSkipBlankText() {
    val rect = SearchRect(10, 20, 300, 60)
    val ocr = NotePageIndexer.ocrDoc("p1", 3, "识别文本", rect)!!
    assertEquals(SearchDocumentKind.OCR, ocr.kind)
    assertEquals(3, ocr.page)
    assertEquals(rect, ocr.rect)

    assertNull(NotePageIndexer.ocrDoc("p1", 3, "   "))
    val transcript = NotePageIndexer.transcriptDoc("p1", 2, "  x^2  ", rect)!!
    assertEquals(SearchDocumentKind.TRANSCRIPT, transcript.kind)
    assertEquals("x^2", transcript.content.trim())
  }

  @Test
  fun emptyMarkdownYieldsNoDocs() {
    assertTrue(NotePageIndexer.noteDocs("p1", "").isEmpty())
  }
}

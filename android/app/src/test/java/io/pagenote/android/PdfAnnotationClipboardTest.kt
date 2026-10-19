package io.pagenote.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAnnotationClipboardTest {
  private val json = Json

  @Test
  fun htmlOverlayItemsAreVisibleWithoutChangingSourceIndexes() {
    val items = PdfAnnotationOverlayItem.fromPage(json.parseToJsonElement("""
      [
        {"type":"pen","color":"#123456","width":2,"points":[[1,1]]},
        {"type":"html","color":"#123456","width":2,"points":[[2,2],[20,20]],"html":"<b>x</b>"},
        {"type":"rect","color":"#123456","width":2,"points":[[10,10],[50,50]]}
      ]
    """))

    assertEquals(2, items.size)
    assertEquals(1, items[0].sourceIndex)
    assertEquals("<b>x</b>", items[0].html)
    assertEquals(2, items[1].sourceIndex)
  }

  @Test
  fun clipboardRoundTripsDesktopMarkerAndUnknownFields() {
    val item = item("""
      {"type":"rect","color":"#123456","width":2,"points":[[10,20],[50,80]],"custom":{"keep":true}}
    """)

    val encoded = PdfAnnotationClipboard.encode(item)

    assertEquals("PAGE_NOTES_ANNOTATION:\n${item}", encoded)
    assertEquals(item, PdfAnnotationClipboard.decode(encoded))
  }

  @Test
  fun htmlClipboardRoundTripsDesktopMarkerAndUnknownFields() {
    val item = item("""
      {"type":"html","color":"#123456","width":2,"points":[[10,20],[510,140]],"html":"<h2><b>Rich</b></h2>","custom":"keep"}
    """)

    val encoded = PdfAnnotationClipboard.encode(item)

    assertEquals("PAGE_NOTES_ANNOTATION:\n${item}", encoded)
    assertEquals(item, PdfAnnotationClipboard.decode(encoded))
  }

  @Test
  fun pasteMovesEveryPointToDesktopInsetAndDropsOnlyLayoutAnchor() {
    val item = item("""
      {"type":"line","color":"#123456","width":2,"points":[[10,20],[50,80]],"layoutAnchor":{"index":2},"custom":"keep"}
    """)

    val pasted = PdfAnnotationClipboard.pasteAt(item, 80.0, 90.0)!!

    assertEquals(json.parseToJsonElement("[[80.0,90.0],[120.0,150.0]]"), pasted["points"])
    assertFalse("layoutAnchor" in pasted)
    assertEquals("keep", pasted["custom"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content })
  }

  @Test
  fun hitTestChoosesTopmostSupportedAnnotationAndIgnoresUnsupportedItems() {
    val page = json.parseToJsonElement("""
      [
        {"type":"rect","color":"#123456","width":2,"points":[[0,0],[100,100]]},
        {"type":"html","color":"#123456","width":2,"points":[[0,0],[100,100]],"html":"x"},
        {"type":"ellipse","color":"#123456","width":2,"points":[[20,20],[80,80]]}
      ]
    """) as JsonArray

    assertEquals(2, PdfAnnotationClipboard.hitTest(page, 50.0, 20.0, 5.0))
    assertEquals(1, PdfAnnotationClipboard.hitTest(page, 5.0, 5.0, 5.0))
    assertNull(PdfAnnotationClipboard.hitTest(page, 200.0, 200.0, 5.0))
  }

  @Test
  fun hitTestAndBoundsIncludeArrowHeadAndEveryLineOfMultilineText() {
    val arrow = item("""
      {"type":"arrow","color":"#123456","width":2,"points":[[10,20],[80,20]]}
    """)
    val arrowPage = JsonArray(listOf(arrow))
    val arrowBounds = PdfAnnotationClipboard.bounds(arrow)!!

    assertTrue(arrowBounds.bottom > 25.0)
    assertEquals(0, PdfAnnotationClipboard.hitTest(arrowPage, 80.0 - 18.0 * kotlin.math.cos(0.45), 20.0 + 18.0 * kotlin.math.sin(0.45), 1.0))

    val text = item("""
      {"type":"text","color":"#123456","width":4,"points":[[10,20]],"text":"first\nsecond"}
    """)
    val textPage = JsonArray(listOf(text))
    val textBounds = PdfAnnotationClipboard.bounds(text)!!

    assertTrue(textBounds.bottom > 45.0)
    assertEquals(0, PdfAnnotationClipboard.hitTest(textPage, 20.0, 45.0, 1.0))
  }

  @Test
  fun translationAndResizePreserveItemMetadata() {
    val item = item("""
      {"type":"rect","color":"#123456","width":2,"points":[[10,20],[50,80]],"custom":"keep"}
    """)
    val moved = PdfAnnotationClipboard.translate(item, 5.0, -10.0)!!
    val resized = PdfAnnotationClipboard.resize(moved, PdfAnnotationBounds(15.0, 10.0, 55.0, 70.0), 95.0, 130.0)!!

    assertEquals(json.parseToJsonElement("[[15.0,10.0],[95.0,130.0]]"), resized["points"])
    assertEquals("keep", (resized["custom"] as kotlinx.serialization.json.JsonPrimitive).content)
  }

  @Test
  fun htmlBoundsAndResizeUseTheFullRichContentRectangle() {
    val html = item("""
      {"type":"html","color":"#123456","width":2,"points":[[10,20],[510,140]],"html":"<p>rich</p>"}
    """)

    assertEquals(PdfAnnotationBounds(10.0, 20.0, 510.0, 140.0), PdfAnnotationClipboard.bounds(html))
    assertEquals(0, PdfAnnotationClipboard.hitTest(JsonArray(listOf(html)), 500.0, 130.0, 0.0))
    val resized = PdfAnnotationClipboard.resize(html, PdfAnnotationBounds(10.0, 20.0, 510.0, 140.0), 1010.0, 260.0)!!

    assertEquals(json.parseToJsonElement("[[10.0,20.0],[1010.0,260.0]]"), resized["points"])
    assertEquals("<p>rich</p>", (resized["html"] as kotlinx.serialization.json.JsonPrimitive).content)
  }

  @Test
  fun clipboardRejectsInvalidUnsupportedAndOversizedPayloads() {
    assertNull(PdfAnnotationClipboard.decode("plain text"))
    assertNull(PdfAnnotationClipboard.decode("PAGE_NOTES_ANNOTATION:\n{"))
    assertNull(PdfAnnotationClipboard.decode("PAGE_NOTES_ANNOTATION:\n${" ".repeat(PdfAnnotationClipboard.MAX_PAYLOAD_CHARS)}"))
    assertNull(PdfAnnotationClipboard.decode("""
      PAGE_NOTES_ANNOTATION:
      {"type":"html","color":"#123456","width":2,"points":[[0,0]],"html":"x"}
    """.trimIndent()))
    assertNull(PdfAnnotationClipboard.decode("""
      PAGE_NOTES_ANNOTATION:
      {"type":"rect","color":"red","width":2,"points":[[0,0],[10,10]]}
    """.trimIndent()))
  }

  @Test
  fun editHistoryIsProjectAndPageScopedAndSupportsRedoBranches() {
    val history = PdfAnnotationEditHistory()
    val empty = JsonArray(emptyList())
    val first = json.parseToJsonElement("""[{"type":"rect","color":"#123456","width":2,"points":[[0,0],[10,10]]}]""") as JsonArray
    val second = json.parseToJsonElement("""[{"type":"rect","color":"#123456","width":2,"points":[[0,0],[20,20]]}]""") as JsonArray

    history.record("project-a", 1, empty, first)
    history.record("project-a", 1, first, second)

    assertEquals(first, history.undo("project-a", 1))
    assertNull(history.undo("project-b", 1))
    assertEquals(second, history.redo("project-a", 1))
    assertEquals(first, history.undo("project-a", 1))
    history.record("project-a", 1, first, empty)
    assertFalse(history.canRedo("project-a", 1))
  }

  private fun item(value: String) = json.parseToJsonElement(value) as JsonObject
}

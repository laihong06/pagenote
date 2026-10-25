package io.pagenote.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
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
  fun selectedStylePatchPreservesLatestUnselectedItemsAndRejectsStaleTargets() {
    val selected = item("""
      {"type":"rect","color":"#185abd","width":4,"points":[[10,20],[50,80]],"custom":"keep"}
    """)
    val latestLine = item("""
      {"type":"line","color":"#dc3545","width":7,"points":[[100,120],[150,180]],"concurrent":"keep"}
    """)
    val latest = JsonArray(listOf(selected, latestLine))

    val updated = PdfAnnotationClipboard.updateSelectedStyle(
      latest,
      index = 0,
      expected = selected,
      color = "#2e7d32",
    )!!

    val updatedRect = updated[0] as JsonObject
    assertEquals("#2e7d32", updatedRect["color"]?.jsonPrimitive?.content)
    assertEquals(4.0, updatedRect["width"]?.jsonPrimitive?.doubleOrNull!!, 0.0)
    assertEquals("keep", updatedRect["custom"]?.jsonPrimitive?.content)
    assertEquals(latestLine, updated[1])
    assertNull(PdfAnnotationClipboard.updateSelectedStyle(
      JsonArray(listOf(latestLine)),
      index = 0,
      expected = selected,
      color = "#2e7d32",
    ))
  }

  @Test
  fun equivalentSelectedStyleIsANoopAndFreehandCannotBeEditedAsNativeAnnotation() {
    val uppercase = item("""
      {"type":"rect","color":"#AABBCC","width":4,"points":[[10,20],[50,80]]}
    """)
    val page = JsonArray(listOf(uppercase))

    assertEquals(page, PdfAnnotationClipboard.updateSelectedStyle(
      page,
      index = 0,
      expected = uppercase,
      color = "#aabbcc",
      width = 4.0,
    ))

    val freehand = item("""
      {"type":"pen","color":"#AABBCC","width":4,"points":[[10,20]]}
    """)
    assertNull(PdfAnnotationClipboard.updateSelectedStyle(
      JsonArray(listOf(freehand)),
      index = 0,
      expected = freehand,
      color = "#ff0000",
    ))
  }

  @Test
  fun selectedAnnotationIndexTracksDeletionBeforeOrAtSelection() {
    assertEquals(0, PdfAnnotationClipboard.selectedIndexAfterDeletion(selectedIndex = 1, deletedIndex = 0))
    assertNull(PdfAnnotationClipboard.selectedIndexAfterDeletion(selectedIndex = 1, deletedIndex = 1))
    assertEquals(1, PdfAnnotationClipboard.selectedIndexAfterDeletion(selectedIndex = 1, deletedIndex = 2))
    assertNull(PdfAnnotationClipboard.selectedIndexAfterDeletion(selectedIndex = null, deletedIndex = 0))
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

  @Test
  fun textBoundsHonorExplicitFontSizeAndAlignment() {
    val left = item(
      """{"type":"text","color":"#123456","width":4,"points":[[100,200]],"text":"hi"}""",
    )
    // Derived size for width 4 is max(20, 20) = 20; text width = 2 chars * 20 * 0.62.
    val leftBounds = PdfAnnotationClipboard.bounds(left)!!
    assertEquals(100.0, leftBounds.left, 1e-9)
    assertEquals(100.0 + 2 * 20.0 * 0.62, leftBounds.right, 1e-9)

    val styled = item(
      """{"type":"text","color":"#123456","width":4,"points":[[100,200]],"text":"hi","""
        + """"font":"mono","fontSize":40.0,"align":"center"}""",
    )
    val centerBounds = PdfAnnotationClipboard.bounds(styled)!!
    val width = 2 * 40.0 * 0.62
    assertEquals(100.0 - width / 2.0, centerBounds.left, 1e-9)
    assertEquals(100.0 + width / 2.0, centerBounds.right, 1e-9)
    assertEquals(200.0 - 40.0, centerBounds.top, 1e-9)

    val right = item(
      """{"type":"text","color":"#123456","width":4,"points":[[100,200]],"text":"hi","fontSize":40.0,"align":"right"}""",
    )
    val rightBounds = PdfAnnotationClipboard.bounds(right)!!
    assertEquals(100.0 - width, rightBounds.left, 1e-9)
    assertEquals(100.0, rightBounds.right, 1e-9)

    // Invalid explicit sizes degrade to the derived size instead of failing.
    val invalid = item(
      """{"type":"text","color":"#123456","width":4,"points":[[100,200]],"text":"hi","fontSize":9999.0}""",
    )
    assertEquals(leftBounds, PdfAnnotationClipboard.bounds(invalid))
  }

  @Test
  fun updateHtmlContentReplacesHtmlAndRefitsHeightOnly() {
    val before = json.parseToJsonElement(
      """
      [{"type":"html","color":"#123456","width":2,"points":[[80,90],[580,170]],
        "html":"<p>old</p>","custom":"keep"}]
      """.trimIndent(),
    ) as JsonArray

    val after = PdfAnnotationClipboard.updateHtmlContent(
      before, 0, before[0] as JsonObject, "<p>new</p>", 120,
    )!!

    val updated = after[0] as JsonObject
    assertEquals("<p>new</p>", updated["html"]!!.jsonPrimitive.content)
    assertEquals("keep", updated["custom"]!!.jsonPrimitive.content)
    assertEquals("#123456", updated["color"]!!.jsonPrimitive.content)
    // The measured height maps to max(60, contentHeight + 20) = 140; only the
    // second point's y moves, the width between the two points is preserved.
    assertEquals(
      json.parseToJsonElement("[[80.0,90.0],[580.0,230.0]]"),
      updated["points"],
    )
    assertEquals(updated, PdfAnnotationClipboard.decode(PdfAnnotationClipboard.encode(updated)))
  }

  @Test
  fun updateHtmlContentIsANoopWhenContentAndHeightMatch() {
    val before = json.parseToJsonElement(
      """[{"type":"html","color":"#123456","width":2,"points":[[80,90],[580,170]],"html":"<p>x</p>"}]""",
    ) as JsonArray
    // Height 60 -> max(60, 60 + 20) = 80 == 170 - 90.
    val after = PdfAnnotationClipboard.updateHtmlContent(
      before, 0, before[0] as JsonObject, "<p>x</p>", 60,
    )
    assertTrue(after === before)
  }

  @Test
  fun updateHtmlContentRejectsStaleOrInvalidTargets() {
    val page = json.parseToJsonElement(
      """
      [{"type":"html","color":"#123456","width":2,"points":[[80,90],[580,170]],"html":"<p>x</p>"},
       {"type":"rect","color":"#123456","width":2,"points":[[10,20],[30,40]]}]
      """.trimIndent(),
    ) as JsonArray
    val expected = page[0] as JsonObject

    // Stale expectation (page already changed elsewhere).
    val stale = json.parseToJsonElement(
      """[{"type":"html","color":"#123456","width":2,"points":[[80,90],[580,170]],"html":"<p>other</p>"}]""",
    ) as JsonArray
    assertNull(PdfAnnotationClipboard.updateHtmlContent(stale, 0, expected, "<p>x</p>", 100))
    // Non-html target and out-of-bounds index.
    assertNull(PdfAnnotationClipboard.updateHtmlContent(page, 1, page[1] as JsonObject, "<p>x</p>", 100))
    assertNull(PdfAnnotationClipboard.updateHtmlContent(page, 9, expected, "<p>x</p>", 100))
    // Blank / oversized content and non-positive height.
    assertNull(PdfAnnotationClipboard.updateHtmlContent(page, 0, expected, "   ", 100))
    assertNull(PdfAnnotationClipboard.updateHtmlContent(page, 0, expected, "x".repeat(100_001), 100))
    assertNull(PdfAnnotationClipboard.updateHtmlContent(page, 0, expected, "<p>x</p>", 0))
  }

  private fun item(value: String) = json.parseToJsonElement(value) as JsonObject
}

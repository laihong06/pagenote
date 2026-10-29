package io.pagenote.feature.annotations

import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port batch 1: rect-eraser geometry and AnnotationStore.eraseInRect. */
class AnnotationRectEraseTest {
  private val json = Json { ignoreUnknownKeys = true }

  private fun items(vararg raws: String): JsonArray =
    json.parseToJsonElement("[" + raws.joinToString(",") + "]").jsonArray

  private fun page(): JsonArray = items(
    // 0: polyline with a point inside (0..50, 0..50).
    """{"type":"pen","color":"#ff0000","width":2.0,"points":[[10,10],[20,20]]}""",
    // 1: polyline far away.
    """{"type":"pen","color":"#ff0000","width":2.0,"points":[[100,200],[300,400]]}""",
    // 2: polyline crossing the eraser with no point inside.
    """{"type":"pen","color":"#ff0000","width":2.0,"points":[[-100,25],[600,25]]}""",
    // 3: line using the same crossing segment.
    """{"type":"line","color":"#ff0000","width":2.0,"points":[[-100,25],[600,25]]}""",
    // 4: rect outline (0,0)-(100,100).
    """{"type":"rect","color":"#ff0000","width":2.0,"points":[[0,0],[100,100]]}""",
    // 5: text anchored at (25,25).
    """{"type":"text","color":"#ff0000","width":2.0,"points":[[25,25]],"text":"hi"}""",
    // 6: image covering the bbox (0,0)-(100,100).
    """{"type":"image","color":"#ff0000","width":2.0,"points":[[0,0],[100,100]],"url":"/attachments/00000000-0000-4000-8000-000000000000.png"}""",
    // 7: highlight rect shorthand [10,10,20,20].
    """{"type":"highlight","color":"#ffff00","width":8.0,"rect":[10,10,20,20]}""",
  )

  @Test
  fun geometryMatchesItemsByType() {
    // Corner-touching eraser (edges inclusive): everything except item 1.
    assertEquals(
      listOf(0, 2, 3, 4, 5, 6, 7),
      AnnotationGeometry.itemsIntersectingRect(page(), 0.0, 0.0, 50.0, 50.0),
    )
    // Fully interior eraser misses the rect outline and the shorthand
    // highlight but still catches crossing polylines, the text anchor and
    // the area/bbox image.
    assertEquals(
      listOf(2, 3, 5, 6),
      AnnotationGeometry.itemsIntersectingRect(page(), 25.0, 25.0, 75.0, 75.0),
    )
    // Away from everything.
    assertTrue(
      AnnotationGeometry.itemsIntersectingRect(page(), 800.0, 800.0, 900.0, 900.0).isEmpty(),
    )
  }

  @Test
  fun storeEraseInRectRemovesOnlyIntersectingItemsAndKeepsOtherPages() {
    val dir = Files.createTempDirectory("pagenote-rect-erase").toFile()
    val documentPage = items(
      """{"type":"pen","color":"#ff0000","width":2.0,"points":[[10,10],[20,20]]}""",
      """{"type":"pen","color":"#00ff00","width":2.0,"points":[[500,500],[600,600]]}""",
    )
    val notePage = items(
      """{"type":"pen","color":"#0000ff","width":2.0,"points":[[10,10],[20,20]]}""",
    )
    assertTrue(AnnotationStore.writePage(dir, "document", 1, documentPage))
    assertTrue(AnnotationStore.writePage(dir, "note", 1, notePage))

    val removed = AnnotationStore.eraseInRect(dir, "document", 1, 0.0, 0.0, 50.0, 50.0)

    assertEquals(listOf(0), removed)
    val file = java.io.File(dir, "annotations.json")
    val remaining = AnnotationStore.readPage(file, "document", 1)
    assertEquals(1, remaining.size)
    assertTrue(remaining.single().toString().contains("[500,500]"))
    assertEquals(notePage, AnnotationStore.readPage(file, "note", 1))
  }

  @Test
  fun storeEraseInRectWithoutHitsCreatesNoBackup() {
    val dir = Files.createTempDirectory("pagenote-rect-erase").toFile()
    val documentPage = items(
      """{"type":"pen","color":"#ff0000","width":2.0,"points":[[500,500],[600,600]]}""",
    )
    assertTrue(AnnotationStore.writePage(dir, "document", 1, documentPage))
    val history = java.io.File(dir, "annotation-history")
    val backups = history.listFiles()?.size ?: 0

    assertTrue(
      AnnotationStore.eraseInRect(dir, "document", 1, 0.0, 0.0, 50.0, 50.0).isEmpty(),
    )
    assertEquals(backups, history.listFiles()?.size ?: 0)
    assertEquals(
      documentPage,
      AnnotationStore.readPage(java.io.File(dir, "annotations.json"), "document", 1),
    )

    // Missing page is a no-op as well.
    assertTrue(AnnotationStore.eraseInRect(dir, "document", 9, 0.0, 0.0, 50.0, 50.0).isEmpty())
  }

  @Test
  fun storeEraseInRectAcceptsAnyCornerOrderAndRejectsNonFiniteRects() {
    val dir = Files.createTempDirectory("pagenote-rect-erase").toFile()
    val documentPage = items(
      """{"type":"pen","color":"#ff0000","width":2.0,"points":[[10,10],[20,20]]}""",
    )
    AnnotationStore.writePage(dir, "document", 1, documentPage)

    assertEquals(
      listOf(0),
      AnnotationStore.eraseInRect(dir, "document", 1, 50.0, 50.0, 0.0, 0.0),
    )
    assertTrue(
      AnnotationStore.readPage(java.io.File(dir, "annotations.json"), "document", 1).isEmpty(),
    )

    try {
      AnnotationStore.eraseInRect(dir, "document", 1, Double.NaN, 0.0, 1.0, 1.0)
      org.junit.Assert.fail("expected non-finite rect rejection")
    } catch (_: IllegalArgumentException) {
    }
  }
}

package io.pagenote.feature.pen

import org.junit.Assert.*
import org.junit.Test

class PenAreaEraserTest {
  @Test fun polylineFragmentsKeepStraightRenderingAfterSaveAndUndo() {
    val stroke = PenStroke("pen", "#000000", 2.0,
      listOf(PenPoint(0f, 0f, 1f), PenPoint(100f, 0f, 1f), PenPoint(100f, 100f, 1f)),
      extraFields = mapOf(PenStroke.POLYLINE_FIELD to true))
    val store = PenPageStore().apply { addStroke(PenSide.DOCUMENT, 1, stroke) }
    val edited = PenAreaEraser.erase(listOf(stroke), PenPoint(50f, 0f, 1f), PenPoint(50f, 0f, 1f), 4f)
    assertEquals(2, edited.size)
    assertTrue(edited.all { it.extraFields[PenStroke.POLYLINE_FIELD] == true })
    assertTrue(store.replacePage(PenSide.DOCUMENT, 1, edited))
    val loaded = PenPageStore().apply { loadAnnotationsMap(store.toAnnotationsMap()) }
    assertEquals(edited, loaded.getItems(PenSide.DOCUMENT, 1))
    assertTrue(store.undo(PenSide.DOCUMENT, 1))
    assertEquals(listOf(stroke), store.getItems(PenSide.DOCUMENT, 1))
  }
  @Test fun crossingStrokeKeepsBothSidesAndInterpolatesPressure() {
    val stroke = PenStroke("pen", "#000000", 2.0, listOf(PenPoint(0f, 0f, 0.2f), PenPoint(100f, 0f, 0.8f)))
    val result = PenAreaEraser.erase(listOf(stroke), PenPoint(50f,-10f,1f), PenPoint(50f,10f,1f), 4f)
    assertEquals(2, result.size)
    assertEquals(0f, result[0].points.first().x, 0.01f)
    assertEquals(45f, result[0].points.last().x, 0.01f)
    assertEquals(55f, result[1].points.first().x, 0.01f)
    assertEquals(100f, result[1].points.last().x, 0.01f)
    assertEquals(0.47f, result[0].points.last().pressure, 0.01f)
    assertEquals(0.53f, result[1].points.first().pressure, 0.01f)
  }
  @Test fun sweptEraserCutsMultipleStrokesEvenWithoutIntermediateEvents() {
    val strokes = listOf(20f, 50f, 80f).map { y ->
      PenStroke("pen", "#112233", 2.0, listOf(PenPoint(0f,y,1f), PenPoint(100f,y,1f)))
    }
    val result = PenAreaEraser.erase(strokes, PenPoint(50f,0f,1f), PenPoint(50f,100f,1f), 4f)
    assertEquals(6, result.size)
    result.chunked(2).forEach { pair ->
      assertEquals(45f, pair[0].points.last().x, 0.01f)
      assertEquals(55f, pair[1].points.first().x, 0.01f)
    }
  }

  @Test fun areaEditUndoRedoAndSerializationKeepUntouchedPages() {
    val stroke = PenStroke("highlight", "#ffcc00", 2.0, listOf(PenPoint(0f,0f,0.2f), PenPoint(100f,0f,0.8f)))
    val store = PenPageStore(); store.addStroke(PenSide.DOCUMENT, 1, stroke); store.addStroke(PenSide.DOCUMENT, 2, stroke)
    val edited = PenAreaEraser.erase(listOf(stroke), PenPoint(50f,0f,1f), PenPoint(50f,0f,1f), 4f)
    assertTrue(store.replacePage(PenSide.DOCUMENT, 1, edited))
    assertEquals(2, store.getItems(PenSide.DOCUMENT, 1).size)
    val reload = PenPageStore().apply { loadAnnotationsMap(store.toAnnotationsMap()) }
    assertEquals(edited, reload.getItems(PenSide.DOCUMENT, 1))
    assertEquals(listOf(stroke), reload.getItems(PenSide.DOCUMENT, 2))
    assertTrue(store.undo(PenSide.DOCUMENT, 1)); assertEquals(listOf(stroke), store.getItems(PenSide.DOCUMENT, 1))
    assertTrue(store.redo(PenSide.DOCUMENT, 1)); assertEquals(edited, store.getItems(PenSide.DOCUMENT, 1))
  }

  @Test fun outsideStrokesAreUnchangedAndCutLinesBecomePenFragments() {
    val line = PenStroke("line", "#000000", 2.0, listOf(PenPoint(0f,0f,1f), PenPoint(100f,0f,1f)))
    val freehand = line.copy(type = "pen")
    val original = listOf(line, freehand)
    assertEquals(original, PenAreaEraser.erase(original, PenPoint(50f,50f,1f), PenPoint(50f,80f,1f), 4f))
    val cut = PenAreaEraser.erase(original, PenPoint(50f,-5f,1f), PenPoint(50f,5f,1f), 4f)
    assertTrue(cut.size >= 4)
    assertTrue(cut.take(2).all { it.type == "pen" && it.extraFields[PenStroke.POLYLINE_FIELD] == true })
  }

  @Test fun sweptAreaCutsRectangleAndEllipseOutlinesIntoCompatiblePenFragments() {
    val rectangle = PenStroke("rect", "#000000", 2.0,
      listOf(PenPoint(0f, 0f, 1f), PenPoint(100f, 100f, 1f)))
    val ellipse = rectangle.copy(type = "ellipse")
    val from = PenPoint(50f, -10f, 1f)
    val to = PenPoint(50f, 110f, 1f)
    listOf(rectangle, ellipse).forEach { shape ->
      val fragments = PenAreaEraser.erase(listOf(shape), from, to, 3f)
      assertTrue("${shape.type} outline should split where the swept eraser crosses it", fragments.size >= 2)
      assertTrue(fragments.all { it.type == "pen" && it.extraFields[PenStroke.POLYLINE_FIELD] == true })
      val store = PenPageStore().apply { addStroke(PenSide.DOCUMENT, 1, shape) }
      assertTrue(store.replacePage(PenSide.DOCUMENT, 1, fragments))
      val reloaded = PenPageStore().apply { loadAnnotationsMap(store.toAnnotationsMap()) }
      assertEquals(fragments, reloaded.getItems(PenSide.DOCUMENT, 1))
      assertTrue(store.undo(PenSide.DOCUMENT, 1))
      assertEquals(listOf(shape), store.getItems(PenSide.DOCUMENT, 1))
    }
  }

}

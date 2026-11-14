package io.pagenote.android

import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenPageStore
import io.pagenote.feature.pen.PenSide
import io.pagenote.feature.pen.PenStroke
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

class AnnotationPageMergerTest {
  @Test fun appendingAndUndoingInkPreservesInterleavedAnnotationOrder() {
    val oldInk = PenStroke("pen", "#112233", 2.0, listOf(PenPoint(1f, 2f, 0.5f)))
    val newInk = PenStroke("pen", "#112233", 2.0, listOf(PenPoint(3f, 4f, 0.5f)))
    val a = mapOf("type" to "rect", "id" to "a")
    val b = mapOf("type" to "rect", "id" to "b")
    val original = listOf(a, oldInk.toAnnotationsItem(), b)
    val appended = AnnotationPageMerger.replaceFreehandItems(original, listOf(oldInk, newInk))
    assertEquals(original + newInk.toAnnotationsItem(), appended)
    assertEquals(original, AnnotationPageMerger.replaceFreehandItems(appended, listOf(oldInk)))
  }
  @Test fun deletingOneAnnotationPreservesOtherItemsRelativeOrder() {
    fun page(value: String) = Json.parseToJsonElement(value) as JsonArray
    val before = page("""[{"type":"rect","id":"a"},{"type":"pen","id":"ink"},{"type":"rect","id":"b"}]""")
    val after = page("""[{"type":"pen","id":"ink"},{"type":"rect","id":"b"}]""")
    val latest = page("""[{"type":"rect","id":"a"},{"type":"pen","id":"ink"},{"type":"rect","id":"b"},{"type":"pen","id":"new"}]""")
    assertEquals(page("""[{"type":"pen","id":"ink"},{"type":"rect","id":"b"},{"type":"pen","id":"new"}]"""),
      AnnotationPageMerger.rebaseAnnotationHistory(latest, before, after))
  }

  @Test fun replacingAndAppendingAnnotationDoNotMoveExistingInk() {
    fun page(value: String) = Json.parseToJsonElement(value) as JsonArray
    val before = page("""[{"type":"rect","id":"a","x":1},{"type":"pen","id":"ink"},{"type":"rect","id":"b"}]""")
    val latest = page("""[{"type":"rect","id":"a","x":1},{"type":"pen","id":"ink"},{"type":"rect","id":"b"},{"type":"pen","id":"new"}]""")
    val moved = page("""[{"type":"rect","id":"a","x":2},{"type":"pen","id":"ink"},{"type":"rect","id":"b"}]""")
    assertEquals(page("""[{"type":"rect","id":"a","x":2},{"type":"pen","id":"ink"},{"type":"rect","id":"b"},{"type":"pen","id":"new"}]"""),
      AnnotationPageMerger.rebaseAnnotationHistory(latest, before, moved))
    val appended = page("""[{"type":"rect","id":"a","x":1},{"type":"pen","id":"ink"},{"type":"rect","id":"b"},{"type":"line","id":"c"}]""")
    assertEquals(page("""[{"type":"rect","id":"a","x":1},{"type":"pen","id":"ink"},{"type":"rect","id":"b"},{"type":"pen","id":"new"},{"type":"line","id":"c"}]"""),
      AnnotationPageMerger.rebaseAnnotationHistory(latest, before, appended))
  }

  @Test fun deletionAndUndoPreserveInterleavedIdenticalShapes() {
    fun page(value: String) = Json.parseToJsonElement(value) as JsonArray
    val before = page("""[{"type":"rect","id":"a"},{"type":"pen","id":"ink"},{"type":"rect","id":"a"}]""")
    val after = page("""[{"type":"pen","id":"ink"},{"type":"rect","id":"a"}]""")
    assertEquals(after, AnnotationPageMerger.rebaseAnnotationHistory(before, before, after))
    assertEquals(before, AnnotationPageMerger.rebaseAnnotationHistory(after, after, before))
    val latest = page("""[{"type":"rect","id":"a"},{"type":"pen","id":"ink"},{"type":"rect","id":"a"},{"type":"pen","id":"new"}]""")
    assertEquals(null, AnnotationPageMerger.rebaseAnnotationHistory(latest, before, after))
  }

  @Test fun undoInsertionUsesSavedInkAnchorAndKeepsLaterInk() {
    fun page(value: String) = Json.parseToJsonElement(value) as JsonArray
    val afterDeletion = page("""[{"type":"pen","id":"ink"},{"type":"rect","id":"b"}]""")
    val original = page("""[{"type":"rect","id":"a"},{"type":"pen","id":"ink"},{"type":"rect","id":"b"}]""")
    val latest = page("""[{"type":"pen","id":"ink"},{"type":"rect","id":"b"},{"type":"pen","id":"new"}]""")
    assertEquals(page("""[{"type":"rect","id":"a"},{"type":"pen","id":"ink"},{"type":"rect","id":"b"},{"type":"pen","id":"new"}]"""),
      AnnotationPageMerger.rebaseAnnotationHistory(latest, afterDeletion, original))
  }

  @Test fun undoDeletionRestoresPenPositionAfterPenSaveReordersLayers() {
    fun page(value: String) = Json.parseToJsonElement(value) as JsonArray
    val afterDeletion = page("""[{"type":"pen","id":"ink"},{"type":"rect","id":"b"}]""")
    val original = page("""[{"type":"rect","id":"a"},{"type":"pen","id":"ink"},{"type":"rect","id":"b"}]""")
    // A later pen save followed by pen undo can put existing ink after shapes.
    val afterPenUndo = page("""[{"type":"rect","id":"b"},{"type":"pen","id":"ink"}]""")
    assertEquals(original, AnnotationPageMerger.rebaseAnnotationHistory(afterPenUndo, afterDeletion, original))
  }

  @Test fun duplicateShapeDeletionWithNewInterleavedInkIsRejected() {
    fun page(value: String) = Json.parseToJsonElement(value) as JsonArray
    val before = page("""[{"type":"rect","id":"a"},{"type":"rect","id":"a"}]""")
    val after = page("""[{"type":"rect","id":"a"}]""")
    val latest = page("""[{"type":"rect","id":"a"},{"type":"pen","id":"new"},{"type":"rect","id":"a"}]""")
    assertEquals(null, AnnotationPageMerger.rebaseAnnotationHistory(latest, before, after))
  }

  @Test fun uniqueSelectionCanMoveIndexWhileOtherAnnotationChanges() {
    fun page(value: String) = Json.parseToJsonElement(value) as JsonArray
    val old = page("""[{"type":"pen"},{"type":"rect","id":"a","width":3.0},{"type":"line","v":1}]""")
    val latest = page("""[{"type":"rect","id":"a","width":3},{"type":"line","v":2},{"type":"pen"}]""")
    assertEquals(0, AnnotationPageMerger.findUniqueAnnotation(old, 1, latest))
    val duplicate = page("""[{"type":"rect","id":"a","width":3},{"type":"rect","id":"a","width":3}]""")
    assertEquals(null, AnnotationPageMerger.findUniqueAnnotation(old, 1, duplicate))
    val originalDuplicate = page("""[{"type":"rect","id":"a"},{"type":"rect","id":"a"}]""")
    val oneRemaining = page("""[{"type":"rect","id":"a"}]""")
    assertEquals(null, AnnotationPageMerger.findUniqueAnnotation(originalDuplicate, 0, oneRemaining))
  }
  @Test fun remapsSelectionAfterInkItemsMoveAcrossAnnotationPositions() {
    fun page(value: String) = Json.parseToJsonElement(value) as JsonArray
    val desired = page("""[{"type":"pen"},{"type":"rect","value":1},{"type":"line","value":2}]""")
    val rebased = page("""[{"type":"rect","value":1},{"type":"line","value":2},{"type":"pen"}]""")
    assertEquals(1, AnnotationPageMerger.remapAnnotationSelection(desired, 2, rebased))
    assertEquals(null, AnnotationPageMerger.remapAnnotationSelection(desired, 0, rebased))
    val changed = page("""[{"type":"rect","value":1},{"type":"line","value":3},{"type":"pen"}]""")
    assertEquals(null, AnnotationPageMerger.remapAnnotationSelection(desired, 2, changed))
    val duplicates = page("""[{"type":"pen"},{"type":"rect","id":"a"},{"type":"rect","id":"a"}]""")
    val reorderedDuplicates = page("""[{"type":"rect","id":"a"},{"type":"pen"},{"type":"rect","id":"a"}]""")
    assertEquals(null, AnnotationPageMerger.remapAnnotationSelection(duplicates, 1, reorderedDuplicates))
  }
  @Test
  fun annotationUndoPreservesInkSavedAfterHistorySnapshot() {
    fun page(value: String) = Json.parseToJsonElement(value) as JsonArray
    val expected = page("""[{"type":"line","custom":"shape"},{"type":"pen","id":"old"}]""")
    val restored = page("""[{"type":"pen","id":"old"}]""")
    val latest = page("""[{"type":"line","custom":"shape"},{"type":"pen","id":"old"},{"type":"pen","id":"new","custom":{"v":1}}]""")
    val merged = AnnotationPageMerger.rebaseAnnotationHistory(latest, expected, restored)
    assertEquals(page("""[{"type":"pen","id":"old"},{"type":"pen","id":"new","custom":{"v":1}}]"""), merged)
  }

  @Test
  fun annotationUndoRejectsChangedShapeAndMixedInkHistory() {
    fun page(value: String) = Json.parseToJsonElement(value) as JsonArray
    val expected = page("""[{"type":"line","value":1},{"type":"pen","id":"a"}]""")
    val changed = page("""[{"type":"line","value":2},{"type":"pen","id":"a"}]""")
    val restored = page("""[{"type":"pen","id":"a"}]""")
    assertEquals(null, AnnotationPageMerger.rebaseAnnotationHistory(changed, expected, restored))
    assertEquals(null, AnnotationPageMerger.rebaseAnnotationHistory(expected, expected, page("[]")))
  }

  @Test
  fun annotationUndoComparesJsonNumbersByValueButKeepsStringsDistinct() {
    fun page(value: String) = Json.parseToJsonElement(value) as JsonArray
    val expected = page("""[{"type":"line","width":3.0,"points":[[1.0,2.0]]}]""")
    val latest = page("""[{"type":"line","width":3,"points":[[1,2]]}]""")
    assertEquals(page("[]"), AnnotationPageMerger.rebaseAnnotationHistory(latest, expected, page("[]")))
    val changed = page("""[{"type":"line","width":"3","points":[[1,2]]}]""")
    assertEquals(null, AnnotationPageMerger.rebaseAnnotationHistory(changed, expected, page("[]")))
  }

  @Test
  fun selectionFollowsSameAnnotationAcrossInkReorderingAndStyleUndo() {
    fun page(value: String) = Json.parseToJsonElement(value) as JsonArray
    val expected = page("""[{"type":"pen","id":"ink"},{"type":"rect","width":4},{"type":"line"}]""")
    val restored = page("""[{"type":"pen","id":"ink"},{"type":"rect","width":2},{"type":"line"}]""")
    val committed = page("""[{"type":"rect","width":2},{"type":"line"},{"type":"pen","id":"ink"}]""")
    assertEquals(0, AnnotationPageMerger.rebaseHistorySelection(expected, 1, expected, restored, committed))
    val removed = page("""[{"type":"pen","id":"ink"},{"type":"line"}]""")
    assertEquals(null, AnnotationPageMerger.rebaseHistorySelection(expected, 1, expected, removed, removed))
  }

  @Test
  fun mixedHistoryRestoresBothLayersOnlyWhenNeitherHasChanged() {
    fun page(value: String) = Json.parseToJsonElement(value) as JsonArray
    val expected = page("""[{"type":"text","text":"recognized"},{"type":"pen","id":"b","width":3.0}]""")
    val restored = page("""[{"type":"pen","id":"a","width":2},{"type":"pen","id":"b","width":3}]""")
    val reordered = page("""[{"type":"pen","id":"b","width":3},{"type":"text","text":"recognized"}]""")
    assertEquals(null, AnnotationPageMerger.rebaseMixedHistory(reordered, expected, restored))
    val numericallyEqual = page("""[{"type":"text","text":"recognized"},{"type":"pen","id":"b","width":3}]""")
    assertEquals(restored, AnnotationPageMerger.rebaseMixedHistory(numericallyEqual, expected, restored))
    val newInk = page("""[{"type":"text","text":"recognized"},{"type":"pen","id":"b","width":3},{"type":"pen","id":"new"}]""")
    assertEquals(
      page("""[{"type":"pen","id":"a","width":2},{"type":"pen","id":"b","width":3},{"type":"pen","id":"new"}]"""),
      AnnotationPageMerger.rebaseMixedHistory(newInk, expected, restored),
    )
    val newAnnotation = page("""[{"type":"text","text":"changed"},{"type":"pen","id":"b","width":3}]""")
    assertEquals(null, AnnotationPageMerger.rebaseMixedHistory(newAnnotation, expected, restored))
  }

  @Test
  fun mixedMoveRebasesOverNewItemsAppendedAfterGestureSnapshot() {
    fun page(value: String) = Json.parseToJsonElement(value) as JsonArray
    val before = page("""[{"type":"pen","id":"ink","x":10},{"type":"rect","id":"shape","x":20}]""")
    val moved = page("""[{"type":"pen","id":"ink","x":30},{"type":"rect","id":"shape","x":40}]""")
    val latest = page("""[{"type":"pen","id":"ink","x":10},{"type":"rect","id":"shape","x":20},{"type":"pen","id":"new","x":90}]""")

    assertEquals(
      page("""[{"type":"pen","id":"ink","x":30},{"type":"rect","id":"shape","x":40},{"type":"pen","id":"new","x":90}]"""),
      AnnotationPageMerger.rebaseMixedHistory(latest, before, moved),
    )
  }

  @Test
  fun mixedMoveRejectsEditsInsideTheGestureSnapshot() {
    fun page(value: String) = Json.parseToJsonElement(value) as JsonArray
    val expected = page("""[{"type":"pen","id":"ink","x":10},{"type":"rect","id":"shape","x":20}]""")
    val restored = page("""[{"type":"pen","id":"ink","x":10},{"type":"rect","id":"shape","x":20}]""")
    val changedSelection = page("""[{"type":"pen","id":"ink","x":99},{"type":"rect","id":"shape","x":20}]""")

    assertEquals(null, AnnotationPageMerger.rebaseMixedHistory(changedSelection, expected, restored))
  }

  @Test
  fun replacingFreehandItemsRetainsOtherAnnotationTypesAndMetadata() {
    val rectangle = mapOf("type" to "rect", "color" to "#112233", "custom" to "kept")
    val existing = listOf(
      rectangle,
      mapOf("type" to "pen", "color" to "#ff0000"),
      mapOf("type" to "highlight", "color" to "#ffff00"),
    )
    val replacement = PenStroke(
      type = "pen",
      color = "#00aa88",
      width = 3.0,
      points = listOf(PenPoint(10f, 20f, 0.7f)),
    )

    val merged = AnnotationPageMerger.replaceFreehandItems(existing, listOf(replacement))

    assertEquals(rectangle, merged.first())
    assertEquals(2, merged.size)
    assertEquals("pen", merged.last()["type"])
    val pressures = merged.last()["pressures"] as List<*>
    assertEquals(0.7, (pressures.single() as Number).toDouble(), 0.0001)
  }

  @Test
  fun emptyReplacementRemovesOnlyPenAndHighlighterItems() {
    val existing = listOf(
      mapOf("type" to "image", "url" to "/attachments/asset.png"),
      mapOf("type" to "rect", "points" to listOf(listOf(1, 2))),
      mapOf("type" to "pen", "providerMetadata" to mapOf("strokeId" to "retire-with-stroke")),
    )

    val merged = AnnotationPageMerger.replaceFreehandItems(existing, emptyList())

    assertEquals(existing.take(2), merged)
  }

  @Test
  fun replacingChangedPagePreservesNestedFieldsOnRetainedFreehandStrokes() {
    val extensionFields = mapOf("providerMetadata" to mapOf("values" to listOf<Any?>("opaque", 17, null)))
    val existingStroke: Map<String, Any?> = mapOf(
      "type" to "pen",
      "color" to "#185abd",
      "width" to 2.5,
      "points" to listOf(listOf(10.0, 20.0)),
      "pressures" to listOf(0.6),
    ) + extensionFields
    val existingPage = listOf(existingStroke)
    val notesPage = listOf(mapOf("type" to "highlight", "providerNote" to "preserve"))
    val root = mapOf(
      "document" to mapOf("1" to existingPage),
      "note" to mapOf("1" to notesPage),
    )
    val pageStore = PenPageStore().apply {
      loadAnnotationsMap(mapOf(PenSide.DOCUMENT.key to mapOf("1" to existingPage)))
    }
    val newStroke = PenStroke(
      type = "pen",
      color = "#00aa88",
      width = 3.0,
      points = listOf(PenPoint(30f, 40f, 0.9f)),
    )
    pageStore.addStroke(PenSide.DOCUMENT, 1, newStroke)

    val updated = AnnotationPageMerger.replacePage(
      root = root,
      side = PenSide.DOCUMENT.key,
      page = 1,
      strokes = pageStore.getItems(PenSide.DOCUMENT, 1),
    )
    val updatedItems = ((updated["document"] as Map<*, *>)["1"] as List<*>)
      .map { it as Map<*, *> }

    assertEquals(2, updatedItems.size)
    assertEquals(extensionFields["providerMetadata"], updatedItems.first()["providerMetadata"])
    assertEquals("pen", updatedItems.first()["type"])
    assertEquals("#185abd", updatedItems.first()["color"])
    assertEquals(2.5, (updatedItems.first()["width"] as Number).toDouble(), 0.0001)
    assertEquals(listOf(listOf(10.0, 20.0)), updatedItems.first()["points"])
    assertEquals(notesPage, (updated["note"] as Map<*, *>)["1"])
    assertEquals(newStroke.toAnnotationsItem(), updatedItems.last())
  }

  @Test
  fun extensionFieldsCannotOverrideCanonicalStrokeProperties() {
    val stroke = PenStroke(
      type = "pen",
      color = "#00aa88",
      width = 3.0,
      points = listOf(PenPoint(30f, 40f, 0.9f)),
      extraFields = mapOf(
        "type" to "html",
        "color" to "#ff0000",
        "width" to 99.0,
        "points" to emptyList<Any>(),
        "pressures" to emptyList<Any>(),
        "providerMetadata" to mapOf("source" to "tablet"),
      ),
    )

    val item = stroke.toAnnotationsItem()

    assertEquals("pen", item["type"])
    assertEquals("#00aa88", item["color"])
    assertEquals(3.0, item["width"])
    assertEquals(listOf(listOf(30.0, 40.0)), item["points"])
    assertEquals(0.9, ((item["pressures"] as List<*>).single() as Number).toDouble(), 0.0001)
    assertEquals(mapOf("source" to "tablet"), item["providerMetadata"])
  }

  @Test
  fun replacingPagePreservesUnknownFieldsAndOtherPagesAndSides() {
    val pageTwo = listOf(mapOf("type" to "pen", "custom" to "preserved"))
    val notes = mapOf("1" to listOf(mapOf("type" to "highlight")))
    val root = mapOf(
      "version" to 3,
      "document" to mapOf("2" to pageTwo),
      "note" to notes,
      "customRoot" to mapOf("enabled" to true),
    )
    val replacement = PenStroke(
      type = "pen",
      color = "#112233",
      width = 2.0,
      points = listOf(PenPoint(1f, 2f, 1f)),
    )

    val updated = AnnotationPageMerger.replacePage(root, "document", 1, listOf(replacement))

    assertEquals(3, updated["version"])
    assertEquals(root["customRoot"], updated["customRoot"])
    assertEquals(pageTwo, (updated["document"] as Map<*, *>)["2"])
    assertEquals(notes, updated["note"])
    assertEquals(listOf(replacement.toAnnotationsItem()),
      (updated["document"] as Map<*, *>)["1"])
  }
}

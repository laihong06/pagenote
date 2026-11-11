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

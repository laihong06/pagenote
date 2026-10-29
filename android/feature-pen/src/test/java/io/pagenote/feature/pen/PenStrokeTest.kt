package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class PenStrokeTest {
  private fun stroke(
    type: String = "pen",
    color: String = "#ff0000",
    width: Double = 2.0,
    points: List<PenPoint> = listOf(PenPoint(1f, 2f, 0.5f)),
    text: String? = null,
    html: String? = null,
    url: String? = null,
  ) = PenStroke(type, color, width, points, text, html, url)

  @Test
  fun validStrokePasses() {
    PenValidation.validate(stroke(), 1)
  }

  @Test
  fun badColorRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      PenValidation.validate(stroke(color = "red"), 1)
    }
  }

  @Test
  fun allDesktopTypesAccepted() {
    val imageUrl = "/attachments/123e4567-e89b-12d3-a456-426614174000.png"
    listOf("pen", "highlight", "line", "arrow", "rect", "ellipse").forEach { type ->
      PenValidation.validate(stroke(type = type), 1)
    }
    PenValidation.validate(stroke(type = "text", text = "hi"), 1)
    PenValidation.validate(stroke(type = "html", html = "<p>hi</p>"), 1)
    PenValidation.validate(stroke(type = "image", url = imageUrl), 1)
  }

  @Test
  fun unknownTypeRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      PenValidation.validate(stroke(type = "erase"), 1)
    }
  }

  @Test
  fun widthBoundsEnforced() {
    PenValidation.validate(stroke(width = 0.1), 1)
    PenValidation.validate(stroke(width = 100.0), 1)
    assertThrows(IllegalArgumentException::class.java) {
      PenValidation.validate(stroke(width = 0.05), 1)
    }
    assertThrows(IllegalArgumentException::class.java) {
      PenValidation.validate(stroke(width = 100.5), 1)
    }
  }

  @Test
  fun pointsBoundsEnforced() {
    assertThrows(IllegalArgumentException::class.java) {
      PenValidation.validate(stroke(points = emptyList()), 1)
    }
    assertThrows(IllegalArgumentException::class.java) {
      PenValidation.validate(stroke(points = List(30001) { PenPoint(0f, 0f, 0.5f) }), 1)
    }
    PenValidation.validate(
      stroke(points = listOf(PenPoint(0f, 0f, 0f), PenPoint(1f, 1f, 1f))),
      3,
    )
  }

  @Test
  fun badPageRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      PenValidation.validate(stroke(), 0)
    }
  }

  @Test
  fun pressureAndCoordinatesEnforced() {
    assertThrows(IllegalArgumentException::class.java) {
      PenValidation.validate(stroke(points = listOf(PenPoint(0f, 0f, 1.5f))), 1)
    }
    assertThrows(IllegalArgumentException::class.java) {
      PenValidation.validate(stroke(points = listOf(PenPoint(Float.NaN, 0f, 0.5f))), 1)
    }
    assertThrows(IllegalArgumentException::class.java) {
      PenValidation.validate(stroke(points = listOf(PenPoint(2_000_000f, 0f, 0.5f))), 1)
    }
  }

  @Test
  fun textRequiresText() {
    assertThrows(IllegalArgumentException::class.java) {
      PenValidation.validate(stroke(type = "text"), 1)
    }
  }

  @Test
  fun imageRequiresAttachmentUrl() {
    assertThrows(IllegalArgumentException::class.java) {
      PenValidation.validate(stroke(type = "image"), 1)
    }
    assertThrows(IllegalArgumentException::class.java) {
      PenValidation.validate(stroke(type = "image", url = "https://example.com/a.png"), 1)
    }
  }

  @Test
  fun htmlRequiresHtml() {
    assertThrows(IllegalArgumentException::class.java) {
      PenValidation.validate(stroke(type = "html"), 1)
    }
  }

  // Unknown-field passthrough contract for PenStroke.kt:16-48.

  @Test
  fun toAnnotationsItemEmitsCanonicalFieldsWithoutNullPayloads() {
    val item = stroke().toAnnotationsItem()
    assertEquals("pen", item["type"])
    assertEquals("#ff0000", item["color"])
    assertEquals(2.0, item["width"])
    assertEquals(listOf(listOf(1.0, 2.0)), item["points"])
    assertEquals(listOf(0.5), item["pressures"])
    assertFalse(item.containsKey("text"))
    assertFalse(item.containsKey("html"))
    assertFalse(item.containsKey("url"))
  }

  @Test
  fun payloadFieldsAreEmittedOnlyWhenPresent() {
    assertEquals("hi", stroke(type = "text", text = "hi").toAnnotationsItem()["text"])
    assertEquals(
      "<p>hi</p>",
      stroke(type = "html", html = "<p>hi</p>").toAnnotationsItem()["html"],
    )
    val imageUrl = "/attachments/123e4567-e89b-12d3-a456-426614174000.png"
    assertEquals(imageUrl, stroke(type = "image", url = imageUrl).toAnnotationsItem()["url"])
  }

  @Test
  fun unknownFieldsPassThroughWithNestedValues() {
    val extra = linkedMapOf<String, Any?>(
      "layoutAnchor" to mapOf("text" to "block", "index" to 3, "fontSize" to 15.0),
      "customList" to listOf(1.0, 2.0, 3.0),
    )
    val item = stroke().copy(extraFields = extra).toAnnotationsItem()
    assertEquals(extra["layoutAnchor"], item["layoutAnchor"])
    assertEquals(extra["customList"], item["customList"])
  }

  @Test
  fun extraFieldsCannotOverrideCanonicalFields() {
    val overrides = mapOf<String, Any?>(
      "type" to "highlight",
      "color" to "#00ff00",
      "width" to 99.0,
      "points" to listOf(listOf(9.0, 9.0)),
      "pressures" to listOf(0.9),
      "text" to "hijack",
      "html" to "<script>alert(1)</script>",
      "url" to "/attachments/00000000-0000-0000-0000-000000000000.png",
    )
    val item = stroke(text = "real").copy(extraFields = overrides).toAnnotationsItem()
    assertEquals("pen", item["type"])
    assertEquals("#ff0000", item["color"])
    assertEquals(2.0, item["width"])
    assertEquals(listOf(listOf(1.0, 2.0)), item["points"])
    assertEquals(listOf(0.5), item["pressures"])
    assertEquals("real", item["text"])
    assertFalse(item.containsKey("html"))
    assertFalse(item.containsKey("url"))
  }

  @Test
  fun unknownFieldsSurviveFullStoreRoundTrip() {
    val extra = linkedMapOf<String, Any?>(
      "layoutAnchor" to mapOf("text" to "quote", "x" to 12.0),
      "nested" to mapOf("list" to listOf(1, "two", 3.0)),
    )
    val store = PenPageStore()
    store.addStroke(PenSide.DOCUMENT, 1, stroke().copy(extraFields = extra))
    val reloaded = PenPageStore()
    reloaded.loadAnnotationsMap(store.toAnnotationsMap())
    assertEquals(extra, reloaded.getItems(PenSide.DOCUMENT, 1).single().extraFields)
  }

  @Test
  fun desktopWrittenItemWithoutPressuresLoadsWithDefaultsAndKeepsUnknownFields() {
    val raw: Map<String, Any?> = mapOf(
      "document" to mapOf(
        "1" to listOf(
          linkedMapOf(
            "type" to "pen",
            "color" to "#123456",
            "width" to 3.0,
            "points" to listOf(listOf(5.0, 6.0), listOf(7.0, 8.0)),
            "layoutAnchor" to mapOf("text" to "paragraph"),
          ),
        ),
      ),
      "note" to emptyMap<String, Any?>(),
    )
    val store = PenPageStore()
    store.loadAnnotationsMap(raw)
    val loaded = store.getItems(PenSide.DOCUMENT, 1).single()
    assertEquals(
      listOf(PenPoint(5f, 6f, 1.0f), PenPoint(7f, 8f, 1.0f)),
      loaded.points,
    )
    assertEquals(mapOf("layoutAnchor" to mapOf("text" to "paragraph")), loaded.extraFields)
  }
}

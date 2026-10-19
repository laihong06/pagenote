package io.pagenote.feature.pen

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
}

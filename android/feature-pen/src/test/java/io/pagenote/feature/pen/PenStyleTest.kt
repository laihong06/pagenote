package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PenStyleTest {
  @Test
  fun defaultsAndDesktopWidthChoicesMatch() {
    assertEquals("#dc3545", PenStyle.DEFAULT.color)
    assertEquals(3.0, PenStyle.DEFAULT.width, 0.0)
    assertEquals(listOf(1.5, 3.0, 6.0, 10.0), PenStyle.WIDTH_OPTIONS)
    assertEquals(
      listOf(
        "red" to "#dc3545",
        "blue" to "#185abd",
        "green" to "#2e7d32",
        "orange" to "#ef6c00",
        "purple" to "#7b1fa2",
        "dark" to "#212121",
        "amber" to "#f9a825",
        "teal" to "#00838f",
      ),
      PenStyle.COLOR_SWATCHES,
    )
  }

  @Test
  fun normalizesValidRgbColorAndAcceptsDesktopWidth() {
    val style = requireNotNull(PenStyle.create("#12AB34", 6.0))
    assertEquals("#12ab34", style.color)
    assertEquals(6.0, style.width, 0.0)
  }

  @Test
  fun rejectsInvalidRgbColorAndNonDesktopWidth() {
    listOf("red", "#123", "#12345678", "123456", "#12GG34").forEach { color ->
      assertNull(PenStyle.create(color, 3.0))
    }
    listOf(0.0, 1.0, 3.5, 101.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { width ->
      assertNull(PenStyle.create("#dc3545", width))
    }
  }
}

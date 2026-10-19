package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfAnnotationVectorDraftTest {
  @Test
  fun serializesEachToolWithOrderedPointsAndDesktopDefaults() {
    listOf("line", "arrow", "rect", "ellipse").forEach { type ->
      val draft = requireNotNull(
        PdfAnnotationVectorDraft.create(
          type = type,
          first = 400.0 to 1200.0,
          second = 650.0 to 1450.0,
        ),
      )

      assertEquals(
        mapOf(
          "type" to type,
          "color" to "#dc3545",
          "width" to 3.0,
          "points" to listOf(listOf(400.0, 1200.0), listOf(650.0, 1450.0)),
        ),
        draft.toItem(),
      )
      assertEquals(listOf(400.0 to 1200.0, 650.0 to 1450.0), draft.toOverlayItem().points)
      assertEquals(null, draft.toOverlayItem().text)
    }
  }

  @Test
  fun rejectsUnsupportedOrDegenerateShapesAndInvalidStyle() {
    val point = 10.0 to 20.0
    assertNull(PdfAnnotationVectorDraft.create("line", point, point))
    assertNull(PdfAnnotationVectorDraft.create("arrow", point, point))
    assertNull(PdfAnnotationVectorDraft.create("rect", point, 10.0 to 30.0))
    assertNull(PdfAnnotationVectorDraft.create("ellipse", point, 30.0 to 20.0))
    assertNull(PdfAnnotationVectorDraft.create("image", point, 30.0 to 40.0))
    assertNull(PdfAnnotationVectorDraft.create("line", point, 30.0 to 40.0, color = "red"))
    assertNull(PdfAnnotationVectorDraft.create("line", point, 30.0 to 40.0, width = 101.0))
  }

  @Test
  fun serializesActiveColorAndWidthForNewVectorAnnotations() {
    val draft = requireNotNull(
      PdfAnnotationVectorDraft.create(
        type = "arrow",
        first = 10.0 to 20.0,
        second = 40.0 to 50.0,
        color = "#185abd",
        width = 10.0,
      ),
    )

    assertEquals("#185abd", draft.toItem()["color"])
    assertEquals(10.0, draft.toItem()["width"])
    assertEquals("#185abd", draft.toOverlayItem().color)
    assertEquals(10.0, draft.toOverlayItem().width, 0.0)
  }
}

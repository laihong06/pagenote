package io.pagenote.android

import io.pagenote.feature.pen.PenStyle
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfImageAnnotationDraftTest {
  @Test
  fun createsDesktopWidthImageAndPreservesAspectRatioAndStyle() {
    val draft = requireNotNull(
      PdfImageAnnotationDraft.create(
        assetName = "01234567-89ab-cdef-0123-456789abcdef.png",
        pixelWidth = 800,
        pixelHeight = 400,
        visibleTopY = 140.0,
        style = requireNotNull(PenStyle.create("#185ABD", 6.0)),
      ),
    )

    assertEquals("image", draft["type"]?.jsonPrimitive?.content)
    assertEquals("#185abd", draft["color"]?.jsonPrimitive?.content)
    assertEquals(6.0, draft["width"]?.jsonPrimitive?.content?.toDouble())
    assertEquals("/attachments/01234567-89ab-cdef-0123-456789abcdef.png", draft["url"]?.jsonPrimitive?.content)
    assertEquals(
      listOf(listOf(80.0, 140.0), listOf(430.0, 315.0)),
      draft["points"]!!.jsonArray.map { point ->
        point.jsonArray.map { it.jsonPrimitive.content.toDouble() }
      },
    )
  }

  @Test
  fun visibleTopYMapsViewportInsetThroughZoomAndTranslationInPageWidthCoordinates() {
    val y = PdfImageAnnotationDraft.visibleTopY(
      bounds = PdfViewportBounds(
        viewportWidthPx = 600f,
        viewportHeightPx = 800f,
        pageWidthPx = 600f,
        pageHeightPx = 900f,
      ),
      transform = PdfViewportTransform(scale = 2f, translationX = 0f, translationY = -200f),
      density = 2f,
    )

    assertEquals(716.6667, y!!, 0.0001)
  }

  @Test
  fun visibleTopYUsesMinimumPlacementAndRejectsInvalidViewportInputs() {
    val minimum = PdfImageAnnotationDraft.visibleTopY(
      bounds = PdfViewportBounds(600f, 800f, 600f, 900f),
      transform = PdfViewportTransform(scale = 1f, translationX = 0f, translationY = 130f),
      density = 1f,
    )
    assertEquals(90.0, minimum!!, 0.0001)

    assertNull(PdfImageAnnotationDraft.visibleTopY(
      PdfViewportBounds.ZERO,
      PdfViewportTransform.DEFAULT,
      density = 1f,
    ))
    assertNull(PdfImageAnnotationDraft.visibleTopY(
      PdfViewportBounds(600f, 800f, 600f, 900f),
      PdfViewportTransform(scale = Float.NaN, translationX = 0f, translationY = 0f),
      density = 1f,
    ))
    assertNull(PdfImageAnnotationDraft.visibleTopY(
      PdfViewportBounds(600f, 800f, 600f, 900f),
      PdfViewportTransform.DEFAULT,
      density = 0f,
    ))
  }

  @Test
  fun rejectsUnsafeAssetNamesInvalidDimensionsAndUnrepresentableGeometry() {
    val validStyle = PenStyle.DEFAULT
    listOf(
      "../outside.png",
      "assets/01234567-89ab-cdef-0123-456789abcdef.png",
      "01234567-89ab-cdef-0123-456789abcdef.svg",
      "not-a-uuid.jpg",
    ).forEach { name ->
      assertNull(PdfImageAnnotationDraft.create(name, 100, 100, 90.0, validStyle))
    }
    assertNull(PdfImageAnnotationDraft.create("01234567-89ab-cdef-0123-456789abcdef.png", 0, 100, 90.0, validStyle))
    assertNull(PdfImageAnnotationDraft.create("01234567-89ab-cdef-0123-456789abcdef.png", 100, 100, Double.NaN, validStyle))
    assertNull(PdfImageAnnotationDraft.create("01234567-89ab-cdef-0123-456789abcdef.png", 1, Int.MAX_VALUE, 90.0, validStyle))
  }
}

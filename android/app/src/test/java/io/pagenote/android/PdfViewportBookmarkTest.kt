package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfViewportBookmarkTest {
  @Test
  fun fromTransformCapturesNormalizedPagePointAtViewportCenter() {
    val bookmark = PdfViewportBookmark.fromTransform(
      transform = PdfViewportTransform(scale = 2f, translationX = 100f, translationY = -200f),
      bounds = PdfViewportBounds(
        viewportWidthPx = 1000f,
        viewportHeightPx = 800f,
        pageWidthPx = 600f,
        pageHeightPx = 1000f,
      ),
    )

    assertEquals(2f, bookmark.scale, 0.0001f)
    assertEquals(5f / 12f, bookmark.centerX, 0.0001f)
    assertEquals(0.6f, bookmark.centerY, 0.0001f)
  }

  @Test
  fun fromTransformClampsNormalizedCenterToPageEdges() {
    val bookmark = PdfViewportBookmark.fromTransform(
      transform = PdfViewportTransform(scale = 2f, translationX = 1000f, translationY = -1000f),
      bounds = PdfViewportBounds(400f, 400f, 200f, 300f),
    )

    assertEquals(PdfViewportBookmark(2f, 0f, 1f), bookmark)
  }

  @Test
  fun toTransformPreservesScaleAndNormalizedFocusAcrossDifferentDimensions() {
    val transform = PdfViewportBookmark(scale = 2.5f, centerX = 0.25f, centerY = 0.75f)
      .toTransform(PdfViewportBounds(500f, 400f, 1000f, 800f))

    assertEquals(2.5f, transform.scale, 0f)
    assertEquals(625f, transform.translationX, 0.0001f)
    assertEquals(-500f, transform.translationY, 0.0001f)
  }

  @Test
  fun toTransformClampsTranslationsToVisiblePageBounds() {
    val transform = PdfViewportBookmark(scale = 2f, centerX = 0f, centerY = 1f)
      .toTransform(PdfViewportBounds(400f, 600f, 300f, 400f))

    assertEquals(PdfViewportTransform(scale = 2f, translationX = 100f, translationY = -100f), transform)
  }

  @Test
  fun invalidBoundsReturnDefaultForCaptureAndRestore() {
    val invalidBounds = listOf(
      PdfViewportBounds(0f, 400f, 300f, 400f),
      PdfViewportBounds(400f, Float.NaN, 300f, 400f),
      PdfViewportBounds(400f, 600f, Float.POSITIVE_INFINITY, 400f),
      PdfViewportBounds(400f, 600f, 300f, -1f),
    )

    invalidBounds.forEach { bounds ->
      assertEquals(
        PdfViewportBookmark.DEFAULT,
        PdfViewportBookmark.fromTransform(PdfViewportTransform.DEFAULT, bounds),
      )
      assertEquals(
        PdfViewportTransform.DEFAULT,
        PdfViewportBookmark(scale = 2f, centerX = 0.25f, centerY = 0.75f).toTransform(bounds),
      )
    }
  }

  @Test
  fun invalidTransformOrBookmarkValuesReturnDefault() {
    val bounds = PdfViewportBounds(400f, 600f, 300f, 400f)
    val invalidTransforms = listOf(
      PdfViewportTransform(scale = 0f, translationX = 0f, translationY = 0f),
      PdfViewportTransform(scale = Float.NaN, translationX = 0f, translationY = 0f),
      PdfViewportTransform(scale = 2f, translationX = Float.POSITIVE_INFINITY, translationY = 0f),
    )
    val invalidBookmarks = listOf(
      PdfViewportBookmark(scale = 0f, centerX = 0.5f, centerY = 0.5f),
      PdfViewportBookmark(scale = 2f, centerX = Float.NaN, centerY = 0.5f),
      PdfViewportBookmark(scale = 2f, centerX = 0.5f, centerY = Float.NEGATIVE_INFINITY),
    )

    invalidTransforms.forEach { transform ->
      assertEquals(PdfViewportBookmark.DEFAULT, PdfViewportBookmark.fromTransform(transform, bounds))
    }
    invalidBookmarks.forEach { bookmark ->
      assertEquals(PdfViewportTransform.DEFAULT, bookmark.toTransform(bounds))
    }
  }
}

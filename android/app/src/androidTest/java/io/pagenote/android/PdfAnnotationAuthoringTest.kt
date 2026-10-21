package io.pagenote.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pagenote.feature.pen.PenCanvasTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfAnnotationAuthoringTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun stylusOrTouchDragPreviewsAndCompletesAnOrderedArrow() {
    val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
    var viewportBounds = PdfViewportBounds.ZERO
    var transform = PdfViewportTransform.DEFAULT
    var preview by mutableStateOf<PdfAnnotationOverlayItem?>(null)
    var completed: PdfAnnotationVectorDraft? = null
    var sawPreview = false
    var addedStrokes = 0
    var transformChanges = 0

    composeRule.setContent {
      MaterialTheme {
        PdfPageViewport(
          bitmap = bitmap,
          sourceName = "test.pdf",
          page = 0,
          annotations = listOfNotNull(preview),
          loading = false,
          strokes = emptyList(),
          tool = PenCanvasTool.PEN,
          inputEnabled = true,
          onStrokeAdded = { addedStrokes++ },
          onStrokeErased = {},
          transform = transform,
          onTransformChanged = { transformChanges++; transform = it },
          onBoundsChanged = { viewportBounds = it },
          gesturesEnabled = true,
          annotationDrawTool = PdfAnnotationDrawTool.ARROW,
          onAnnotationDrag = { start, end, finished ->
            val points = mapPdfAnnotationDragToPoints(start, end, viewportBounds, transform)
            val draft = points?.let {
              PdfAnnotationVectorDraft.create("arrow", it.first, it.second)
            }
            preview = draft?.toOverlayItem()
            if (finished) completed = draft else if (draft != null) sawPreview = true
          },
          modifier = Modifier.size(240.dp).testTag("pdf_page_viewport"),
        )
      }
    }

    composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput {
      down(0, center - Offset(40f, 40f))
      moveTo(0, center + Offset(40f, 40f))
      up(0)
    }

    val draft = requireNotNull(completed)
    val pageWidth = viewportBounds.pageWidthPx
    val pageHeight = viewportBounds.pageHeightPx
    assertEquals(500.0 - 40.0 / pageWidth * 1000.0, draft.first.first, 0.01)
    assertEquals((pageHeight / 2.0 - 40.0) / pageWidth * 1000.0, draft.first.second, 0.01)
    assertEquals(500.0 + 40.0 / pageWidth * 1000.0, draft.second.first, 0.01)
    assertEquals((pageHeight / 2.0 + 40.0) / pageWidth * 1000.0, draft.second.second, 0.01)
    assertTrue(sawPreview)
    assertNotNull(preview)
    assertEquals(PdfViewportTransform.DEFAULT, transform)
    assertEquals(0, transformChanges)
    assertEquals(0, addedStrokes)

    val pixels = composeRule.onNodeWithTag("pdf_annotation_overlay").captureToImage().toPixelMap()
    assertColorNear(pixels, pixels.width / 2f, pixels.height / 2f, Color(0xFFDC3545))
    bitmap.recycle()
  }

  private fun assertColorNear(
    pixels: androidx.compose.ui.graphics.PixelMap,
    x: Float,
    y: Float,
    expected: Color,
  ) {
    val centerX = x.toInt()
    val centerY = y.toInt()
    assertTrue(
      "expected $expected near ($centerX, $centerY)",
      (-2..2).any { dy -> (-2..2).any { dx -> pixels[centerX + dx, centerY + dy] == expected } },
    )
  }
}

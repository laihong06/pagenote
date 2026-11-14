package io.pagenote.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfPageViewportNavigationTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun pinchAccumulatesEveryMovementBeforeHostRecomposition() {
    val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
    var latest = PdfViewportTransform.DEFAULT
    var hostUpdates = 0
    var bounds = PdfViewportBounds.ZERO
    var strokes = 0
    composeRule.setContent {
      MaterialTheme {
        PdfPageViewport(
          bitmap = bitmap, sourceName = "test.pdf", page = 0, loading = false,
          strokes = emptyList(), tool = PenCanvasTool.PEN, inputEnabled = true,
          onStrokeAdded = { strokes++ }, onStrokeErased = {},
          transform = PdfViewportTransform.DEFAULT,
          onTransformChanged = { latest = it; hostUpdates++ }, onBoundsChanged = { bounds = it },
          gesturesEnabled = true, pageSwipesEnabled = false, modifier = Modifier.size(240.dp),
        )
      }
    }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput {
      val offset = width * 0.15f
      down(0, center.copy(x = center.x - offset))
      down(1, center.copy(x = center.x + offset))
      repeat(12) { i ->
        val radius = offset * (1f + (i + 1) / 12f)
        moveTo(0, center.copy(x = center.x - radius), delayMillis = 1)
        moveTo(1, center.copy(x = center.x + radius), delayMillis = 1)
      }
      up(0)
      up(1)
    }
    composeRule.waitForIdle()
    assertTrue(bounds.viewportWidthPx > 0)
    assertEquals("all pinch deltas must be applied", 2f, latest.scale, 0.02f)
    assertEquals("pinching must not create handwriting", 0, strokes)
    assertEquals("publish the reading position once after the gesture", 1, hostUpdates)
    bitmap.recycle()
  }

  @Test
  fun hardwareInkStaysAlignedAndOffscreenInkAppearsAfterPanning() {
    val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
    val transform = mutableStateOf(PdfViewportTransform(2f, -200f, 0f))
    val strokes = listOf(
      PenStroke("pen", "#ff0000", 10.0, listOf(PenPoint(400f, 500f, 1f), PenPoint(600f, 500f, 1f))),
      PenStroke("highlight", "#ffff00", 40.0, listOf(PenPoint(100f, 300f, 1f), PenPoint(900f, 300f, 1f))),
      PenStroke("pen", "#00ff00", 14.0, listOf(PenPoint(190f, 500f, 1f), PenPoint(210f, 500f, 1f))),
    )
    composeRule.setContent {
      MaterialTheme {
        PdfPageViewport(bitmap = bitmap, sourceName = "test.pdf", page = 0, loading = false,
          strokes = strokes, tool = PenCanvasTool.PEN, inputEnabled = false,
          onStrokeAdded = {}, onStrokeErased = {}, transform = transform.value,
          onTransformChanged = { transform.value = it }, onBoundsChanged = {},
          modifier = Modifier.size(240.dp))
      }
    }
    composeRule.waitForIdle()
    composeRule.runOnIdle { transform.value = PdfViewportTransform(2f, 200f, 0f) }
    composeRule.waitForIdle()
    val pixels = composeRule.onNodeWithTag("pdf_page_viewport").captureToImage().toPixelMap()
    val scale = pixels.width / 1000f * 2f
    val origin = -pixels.width / 2f + 200f
    val greenX = (origin + 200f * scale).toInt()
    val centerY = pixels.height / 2
    assertTrue("previously offscreen green ink must be visible after pan", (-3..3).any { dx ->
      val c = pixels[(greenX + dx).coerceIn(0, pixels.width - 1), centerY]
      c.green > 0.8f && c.red < 0.2f && c.blue < 0.2f
    })
    val redX = (origin + 450f * scale).toInt().coerceIn(0, pixels.width - 1)
    val red = pixels[redX, centerY]
    assertTrue("red ink must use the page transform", red.red > 0.8f && red.green < 0.2f)
    val highlightY = (pixels.height / 2f + (300f - 500f) * scale).toInt()
    val yellow = pixels[pixels.width / 2, highlightY.coerceIn(0, pixels.height - 1)]
    assertTrue("highlighter opacity should survive hardware recording", yellow.red > 0.9f && yellow.green > 0.9f && yellow.blue in 0.55f..0.8f)
    bitmap.recycle()
  }

  @Test
  fun pinchInPenModeDoesNotCreateAnInkStroke() {
    val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
    val strokes = mutableListOf<PenStroke>()
    var lastTransform = PdfViewportTransform.DEFAULT
    composeRule.setContent {
      PdfPageViewport(
        bitmap = bitmap, sourceName = "test.pdf", page = 0, loading = false,
        strokes = emptyList(), tool = PenCanvasTool.PEN, inputEnabled = true,
        onStrokeAdded = strokes::add, onStrokeErased = {},
        transform = PdfViewportTransform.DEFAULT, onTransformChanged = { lastTransform = it }, onBoundsChanged = {},
        modifier = Modifier.size(240.dp).testTag("pdf_page_viewport"),
      )
    }
    composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput {
      down(0, center.copy(x = center.x - 30f))
      down(1, center.copy(x = center.x + 30f))
      moveTo(0, center.copy(x = center.x - 60f), delayMillis = 10)
      moveTo(1, center.copy(x = center.x + 60f), delayMillis = 10)
      up(1); up(0)
    }
    composeRule.waitForIdle()
    assertTrue("Two-finger zoom must not leave an accidental ink mark", strokes.isEmpty())
    assertTrue("Pinch in pen mode should zoom, got ${lastTransform.scale}", lastTransform.scale > 1.8f)
    bitmap.recycle()
  }

  @Test
  fun rapidPinchAccumulatesEveryMotionBeforeRecomposition() {
    val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
    var lastTransform = PdfViewportTransform.DEFAULT
    composeRule.setContent {
      MaterialTheme {
        PdfPageViewport(
          bitmap = bitmap, sourceName = "test.pdf", page = 0, loading = false,
          strokes = emptyList(), tool = PenCanvasTool.PEN, inputEnabled = false,
          onStrokeAdded = {}, onStrokeErased = {},
          transform = PdfViewportTransform.DEFAULT,
          onTransformChanged = { lastTransform = it }, onBoundsChanged = {},
          modifier = Modifier.size(240.dp).testTag("pdf_page_viewport"),
        )
      }
    }
    composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput {
      val midpoint = center
      down(0, midpoint.copy(x = midpoint.x - 30f))
      down(1, midpoint.copy(x = midpoint.x + 30f))
      repeat(10) { index ->
        val halfDistance = 30f + (index + 1) * 3f
        moveTo(0, midpoint.copy(x = midpoint.x - halfDistance), delayMillis = 1)
        moveTo(1, midpoint.copy(x = midpoint.x + halfDistance), delayMillis = 1)
      }
      up(1); up(0)
    }
    composeRule.waitForIdle()
    assertTrue("A 2× pinch should accumulate all motion, got ${lastTransform.scale}", lastTransform.scale > 1.9f)
    assertEquals(2f, lastTransform.scale, 0.08f)
    bitmap.recycle()
  }

  @Test
  fun verticalTouchSwipesNavigateOnePageAndHorizontalDragDoesNot() {
    val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
    var direction: PdfPageSwipeDirection? = null

    composeRule.setContent {
      MaterialTheme {
        PdfPageViewport(
          bitmap = bitmap,
          sourceName = "test.pdf",
          page = 0,
          loading = false,
          strokes = emptyList(),
          tool = PenCanvasTool.PEN,
          inputEnabled = false,
          onStrokeAdded = {},
          onStrokeErased = {},
          transform = PdfViewportTransform.DEFAULT,
          onTransformChanged = {},
          onBoundsChanged = {},
          gesturesEnabled = true,
          pageSwipesEnabled = true,
          onPageSwipe = { direction = it },
          modifier = Modifier.size(240.dp).testTag("pdf_page_viewport"),
        )
      }
    }

    composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput {
      down(0, center.copy(y = center.y + height * 0.35f))
      moveTo(0, center.copy(y = center.y - height * 0.35f), delayMillis = 60)
      up(0)
    }
    composeRule.waitForIdle()
    assertEquals(PdfPageSwipeDirection.NEXT, direction)

    direction = null
    composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput {
      down(0, center.copy(x = center.x - 60f))
      moveTo(0, center.copy(x = center.x + 60f), delayMillis = 60)
      up(0)
    }
    composeRule.waitForIdle()
    assertEquals(null, direction)
    bitmap.recycle()
  }
}

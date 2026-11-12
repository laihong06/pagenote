package io.pagenote.feature.pen

import android.graphics.Rect
import android.graphics.Matrix
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferencePenSplitInstrumentedTest {
  @Test fun referenceEllipseUsesTwoPixelPathSamplingAndTransformedBounds() {
    val transform = Matrix().apply { setTranslate(10f, 20f) }
    val result = ReferenceEllipseSplit.split(
      100f, 100f, 200f, 150f, transform, 10f, Rect(10, 70, 210, 170),
      Rect(205, 115, 215, 125), Rect(205, 115, 215, 125),
    )
    assertTrue(result.split)
    assertTrue(result.pieces.isNotEmpty())
    assertEquals(8f, result.fragmentWidth, 0.0001f)
  }

  @Test fun referenceEllipseMissLeavesShapeIntact() {
    val result = ReferenceEllipseSplit.split(
      100f, 100f, 200f, 150f, Matrix(), 10f, Rect(0, 50, 200, 150),
      Rect(300, 300, 310, 310), Rect(300, 300, 310, 310),
    )
    assertFalse(result.split)
    assertTrue(result.pieces.isEmpty())
  }
  @Test fun referenceCircleCanBeConvertedAndSplit() {
    val result = ReferenceCircleSplit.split(
      centerX = 100f, centerY = 100f, edgeX = 200f, edgeY = 100f,
      strokeWidth = 10f, shapeBounds = Rect(-5, -5, 205, 205),
      previous = Rect(195, 95, 205, 105), current = Rect(195, 95, 205, 105),
    )
    assertTrue(result.split)
    assertTrue(result.pieces.isNotEmpty())
    assertEquals(9.6f, result.fragmentWidth, 0.0001f)
  }

  @Test fun referenceCircleOutsideEraserIsNotConverted() {
    val result = ReferenceCircleSplit.split(
      centerX = 100f, centerY = 100f, edgeX = 200f, edgeY = 100f,
      strokeWidth = 10f, shapeBounds = Rect(-5, -5, 205, 205),
      previous = Rect(300, 300, 310, 310), current = Rect(300, 300, 310, 310),
    )
    assertFalse(result.split)
    assertTrue(result.pieces.isEmpty())
  }

  @Test fun referenceBoundsFeedSplitWithoutManuallyConstructedRectangles() {
    val positions = listOf(
      ReferencePenSplit.Position(0f, 0f),
      ReferencePenSplit.Position(50f, 20f),
      ReferencePenSplit.Position(100f, 0f),
    )
    val points = ReferencePenSplit.buildDefaultBounds(positions, 4f)
    assertEquals(Rect(), points[0].bounds)
    assertEquals(Rect(-5, -5, 34, 16), points[1].bounds)
    assertEquals(Rect(24, -5, 105, 16), points[2].bounds)
    val result = ReferencePenSplit.split(points, 4f, Rect(45, 15, 55, 25), Rect(45, 15, 55, 25))
    assertTrue(result.split)
    assertEquals(2, result.pieces.size)
  }

  @Test fun crossingStrokeSplitsAtSweptRectBoundaries() {
    val points = listOf(
      ReferencePenSplit.Point(0f, 0f, Rect()),
      ReferencePenSplit.Point(100f, 0f, Rect(-3, -3, 103, 3)),
    )
    val result = ReferencePenSplit.split(points, 3f, Rect(40, -5, 60, 5), Rect(40, -5, 60, 5))
    assertTrue(result.split)
    assertEquals(2, result.pieces.size)
    assertEquals(listOf(0f, 39f), result.pieces[0].map { it.x })
    assertEquals(listOf(61f, 100f), result.pieces[1].map { it.x })
  }

  @Test fun strokeOutsideEraserReportsNoSplit() {
    val points = listOf(
      ReferencePenSplit.Point(0f, 50f, Rect()),
      ReferencePenSplit.Point(100f, 50f, Rect(-3, 47, 103, 53)),
    )
    val result = ReferencePenSplit.split(points, 3f, Rect(40, -5, 60, 5), Rect(40, -5, 60, 5))
    assertFalse(result.split)
  }

  @Test fun strokeFullyInsideEraserProducesNoPieces() {
    val points = listOf(
      ReferencePenSplit.Point(0f, 0f, Rect()),
      ReferencePenSplit.Point(100f, 0f, Rect(-3, -3, 103, 3)),
    )
    val result = ReferencePenSplit.split(points, 3f, Rect(-5, -5, 105, 5), Rect(-5, -5, 105, 5))
    assertTrue(result.split)
    assertTrue(result.pieces.isEmpty())
  }

  @Test fun sweptBoundsCoverLongMovementWithoutIntermediateEvents() {
    val points = listOf(
      ReferencePenSplit.Point(0f, 50f, Rect()),
      ReferencePenSplit.Point(100f, 50f, Rect(-3, 47, 103, 53)),
    )
    val before = Rect(20, 20, 30, 30)
    val after = Rect(70, 70, 80, 80)
    val result = ReferencePenSplit.split(points, 3f, before, after)
    assertTrue(result.split)
    assertEquals(listOf(0f, 19f), result.pieces[0].map { it.x })
    assertEquals(listOf(81f, 100f), result.pieces[1].map { it.x })
    assertEquals(Rect(20, 20, 30, 30), before)
    assertEquals(Rect(70, 70, 80, 80), after)
  }
}

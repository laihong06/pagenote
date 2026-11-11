package io.pagenote.feature.pen

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferencePenSplitInstrumentedTest {
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

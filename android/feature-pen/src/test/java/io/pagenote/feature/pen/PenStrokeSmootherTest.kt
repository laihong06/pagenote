package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PenStrokeSmootherTest {
  @Test
  fun fewerThanThreePointsProduceNoSegments() {
    assertTrue(PenStrokeSmoother.segments(emptyList(), 10f).isEmpty())
    assertTrue(PenStrokeSmoother.segments(listOf(PenPoint(0f, 0f, 1f)), 10f).isEmpty())
    assertTrue(
      PenStrokeSmoother.segments(
        listOf(PenPoint(0f, 0f, 1f), PenPoint(10f, 10f, 1f)), 10f,
      ).isEmpty(),
    )
  }

  @Test
  fun threeCollinearPointsKeepTheLineAndUseMidpointEnds() {
    val segments = PenStrokeSmoother.segments(
      listOf(PenPoint(0f, 0f, 1f), PenPoint(50f, 50f, 1f), PenPoint(100f, 100f, 1f)),
      10f,
    )
    assertEquals(2, segments.size)
    val first = segments[0]
    assertEquals(0f, first.startX, 0.001f)
    assertEquals(0f, first.startY, 0.001f)
    assertEquals(50f, first.controlX, 0.001f)
    assertEquals(50f, first.controlY, 0.001f)
    assertEquals(75f, first.endX, 0.001f)
    assertEquals(75f, first.endY, 0.001f)
    val second = segments[1]
    assertEquals(first.endX, second.startX, 0.001f)
    assertEquals(first.endY, second.startY, 0.001f)
    assertEquals(100f, second.endX, 0.001f)
    assertEquals(100f, second.endY, 0.001f)
  }

  @Test
  fun chainStartsAtTheFirstSampleAndEndsAtTheLast() {
    val points = listOf(
      PenPoint(0f, 0f, 1f), PenPoint(30f, 10f, 0.8f), PenPoint(60f, 0f, 1f),
      PenPoint(90f, 10f, 0.6f), PenPoint(120f, 0f, 1f),
    )
    val segments = PenStrokeSmoother.segments(points, 10f)
    assertEquals(points.size - 1, segments.size)
    assertEquals(points.first().x, segments.first().startX, 0.001f)
    assertEquals(points.first().y, segments.first().startY, 0.001f)
    assertEquals(points.last().x, segments.last().endX, 0.001f)
    assertEquals(points.last().y, segments.last().endY, 0.001f)
    segments.zipWithNext().forEach { (left, right) ->
      assertEquals(left.endX, right.startX, 0.001f)
      assertEquals(left.endY, right.startY, 0.001f)
    }
    // Every control point is an original sample.
    points.drop(1).dropLast(1).forEachIndexed { index, point ->
      assertEquals(point.x, segments[index].controlX, 0.001f)
      assertEquals(point.y, segments[index].controlY, 0.001f)
    }
  }

  @Test
  fun segmentWidthAveragesThePressuresOfItsTwoSamples() {
    val segments = PenStrokeSmoother.segments(
      listOf(PenPoint(0f, 0f, 1f), PenPoint(50f, 0f, 0.5f), PenPoint(100f, 0f, 0.2f)),
      10f,
    )
    // Asymmetric endpoint pressures pin the (control, next) / (previous, last)
    // pairing: the (p0, p1) reading would yield 10f * (1f + 0.5f) / 2f instead.
    assertEquals(10f * (0.5f + 0.2f) / 2f, segments[0].width, 0.001f)
    assertEquals(10f * (0.5f + 0.2f) / 2f, segments[1].width, 0.001f)
  }
}

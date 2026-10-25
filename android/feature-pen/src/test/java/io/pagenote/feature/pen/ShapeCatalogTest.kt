package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 形状几何目录单测（移植批次 2）。
 * 契约依据：android/docs/spec/canvas-schema-decision.md D4（两点参数化）与 D5（shape 扩展字段）。
 */
class ShapeCatalogTest {

  private val first = ShapePoint(100f, 100f)
  private val second = ShapePoint(300f, 260f)

  @Test
  fun catalogCoversAtLeastTwentyOneKindsWithUniqueIdsAndLegalBaseTypes() {
    assertTrue("catalog size ${ShapeCatalog.kinds.size} must be >= 21", ShapeCatalog.kinds.size >= 21)
    val ids = ShapeCatalog.kinds.map(ShapeCatalogKind::id)
    assertEquals(ids.size, ids.toSet().size)
    ShapeCatalog.kinds.forEach { kind ->
      assertTrue(
        "kind ${kind.id} base type ${kind.baseType} must map to a desktop type",
        kind.baseType in setOf("line", "rect", "ellipse"),
      )
    }
    // 目录注册中心：只增不删，byId 全量可达。
    ShapeCatalog.kinds.forEach { kind ->
      assertEquals(kind, ShapeCatalog.byId(kind.id))
    }
    assertNull(ShapeCatalog.byId("no-such-kind"))
  }

  @Test
  fun catalogIncludesPolygonAxisWaveAndDashedRepresentatives() {
    assertNotNull(ShapeCatalog.byId("pentagon"))
    assertNotNull(ShapeCatalog.byId("hexagon"))
    assertNotNull(ShapeCatalog.byId("star-5"))
    assertNotNull(ShapeCatalog.byId("axis-quadrant-1"))
    assertNotNull(ShapeCatalog.byId("axis-quadrants"))
    assertNotNull(ShapeCatalog.byId("wave"))
    assertNotNull(ShapeCatalog.byId("zigzag"))
    assertEquals(ShapeStrokeStyle.DASHED, ShapeCatalog.byId("dashed-line")?.strokeStyle)
    assertEquals(ShapeStrokeStyle.DASHED, ShapeCatalog.byId("dashed-rect")?.strokeStyle)
    assertEquals(ShapeStrokeStyle.SOLID, ShapeCatalog.byId("rect")?.strokeStyle)
  }

  @Test
  fun outlineIsDeterministicForIdenticalInputs() {
    ShapeCatalog.kinds.forEach { kind ->
      assertEquals(
        ShapeCatalog.outline(kind, first, second),
        ShapeCatalog.outline(kind, first, second),
      )
    }
  }

  @Test
  fun everyKindProducesNonEmptyOutline() {
    ShapeCatalog.kinds.forEach { kind ->
      val outline = ShapeCatalog.outline(kind, first, second)
      assertTrue("kind ${kind.id} outline empty", outline.isNotEmpty())
      assertTrue(
        "kind ${kind.id} outline has non-finite coordinates",
        outline.all { it.x.isFinite() && it.y.isFinite() },
      )
    }
  }

  @Test
  fun lineFamilyPreservesDragDirection() {
    setOf(ShapeCatalogKind.LINE, ShapeCatalogKind.DASHED_LINE, ShapeCatalogKind.WAVE, ShapeCatalogKind.ZIGZAG)
      .forEach { kind ->
        val outline = ShapeCatalog.outline(kind, first, second)
        assertEquals(first, outline.first())
        assertEquals(second, outline.last())
      }
    // 箭头轮廓起于 first、止于箭头翼，方向同样保留。
    val arrow = ShapeCatalog.outline(ShapeCatalogKind.ARROW, first, second)
    assertEquals(first, arrow.first())
    assertEquals(second, arrow[1])
    // 反向拖拽得到反向轮廓（非包围盒归一化）。
    val reversed = ShapeCatalog.outline(ShapeCatalogKind.LINE, second, first)
    assertEquals(second, reversed.first())
    assertEquals(first, reversed.last())
  }

  @Test
  fun rectFamilyNormalizesBoundingBoxRegardlessOfDragDiagonal() {
    ShapeCatalog.kinds.filter { it.baseType == "rect" && it != ShapeCatalogKind.CROSS }.forEach { kind ->
      val normalDiagonal = ShapeCatalog.outline(kind, first, second)
      val reversedDiagonal = ShapeCatalog.outline(kind, second, first)
      assertEquals("kind ${kind.id} must be drag-direction agnostic", normalDiagonal, reversedDiagonal)
    }
    // CROSS 也按包围盒归一化，只是轮廓为开放十字。
    assertEquals(
      ShapeCatalog.outline(ShapeCatalogKind.CROSS, first, second),
      ShapeCatalog.outline(ShapeCatalogKind.CROSS, second, first),
    )
  }

  @Test
  fun closedKindsReturnClosedOutlines() {
    ShapeCatalog.kinds.filter(ShapeCatalogKind::closed).forEach { kind ->
      val outline = ShapeCatalog.outline(kind, first, second)
      assertEquals("kind ${kind.id} must close its outline", outline.first(), outline.last())
    }
  }

  @Test
  fun dashedKindsShareOutlineWithSolidCounterparts() {
    assertEquals(
      ShapeCatalog.outline(ShapeCatalogKind.LINE, first, second),
      ShapeCatalog.outline(ShapeCatalogKind.DASHED_LINE, first, second),
    )
    assertEquals(
      ShapeCatalog.outline(ShapeCatalogKind.RECT, first, second),
      ShapeCatalog.outline(ShapeCatalogKind.DASHED_RECT, first, second),
    )
  }

  @Test
  fun polygonVertexCountsFollowDefinitions() {
    // rect：4 顶点 + 闭合点。
    assertEquals(5, ShapeCatalog.outline(ShapeCatalogKind.RECT, first, second).size)
    // 五角星：10 顶点 + 闭合点。
    assertEquals(11, ShapeCatalog.outline(ShapeCatalogKind.STAR_5, first, second).size)
    // 正五边形：5 顶点 + 闭合点。
    assertEquals(6, ShapeCatalog.outline(ShapeCatalogKind.PENTAGON, first, second).size)
    // 正六边形：6 顶点 + 闭合点。
    assertEquals(7, ShapeCatalog.outline(ShapeCatalogKind.HEXAGON, first, second).size)
    // 星形顶点交替外内半径，外顶点应比内顶点更远离中心。
    val star = ShapeCatalog.outline(ShapeCatalogKind.STAR_5, first, second)
    val center = ShapePoint(200f, 180f)
    val outer = star[0]
    val inner = star[1]
    assertTrue(distance(outer, center) > distance(inner, center))
  }

  @Test
  fun degenerateTwoPointsFallBackToPointSemantics() {
    val point = ShapePoint(50f, 60f)
    setOf(ShapeCatalogKind.LINE, ShapeCatalogKind.DASHED_LINE, ShapeCatalogKind.WAVE, ShapeCatalogKind.ZIGZAG)
      .forEach { kind ->
        val outline = ShapeCatalog.outline(kind, point, point)
        assertTrue(outline.isNotEmpty())
        assertTrue(ShapeCatalog.hitTest(kind, point, point, point, tolerance = 1f))
        assertFalse(ShapeCatalog.hitTest(kind, point, point, ShapePoint(200f, 200f), tolerance = 1f))
      }
    // 面族零面积退化为重复点，不再抛异常（对齐桌面 D4 §4 的退化容忍）。
    ShapeCatalog.kinds.filter { it.baseType != "line" }.forEach { kind ->
      val outline = ShapeCatalog.outline(kind, point, point)
      assertTrue(outline.isNotEmpty())
    }
  }

  @Test
  fun hitTestMatchesStrokeEdgesAndRejectsInteriorAndFarPoints() {
    // 矩形边中点命中；内部与远处不命中（描边语义）。
    val rectFirst = ShapePoint(100f, 100f)
    val rectSecond = ShapePoint(300f, 260f)
    val topEdgeMid = ShapePoint(200f, 100f)
    val interior = ShapePoint(200f, 180f)
    val far = ShapePoint(500f, 500f)
    assertTrue(ShapeCatalog.hitTest(ShapeCatalogKind.RECT, rectFirst, rectSecond, topEdgeMid, tolerance = 2f))
    assertFalse(ShapeCatalog.hitTest(ShapeCatalogKind.RECT, rectFirst, rectSecond, interior, tolerance = 2f))
    assertFalse(ShapeCatalog.hitTest(ShapeCatalogKind.RECT, rectFirst, rectSecond, far, tolerance = 2f))
    // 角点命中（顶点容差）。
    assertTrue(ShapeCatalog.hitTest(ShapeCatalogKind.RECT, rectFirst, rectSecond, ShapePoint(100f, 100f), tolerance = 1f))
  }

  @Test
  fun hitTestCoversArrowHeadWingsAndAxesCrossCenter() {
    val arrowFirst = ShapePoint(0f, 0f)
    val arrowSecond = ShapePoint(100f, 0f)
    // 箭头杆中点命中。
    assertTrue(ShapeCatalog.hitTest(ShapeCatalogKind.ARROW, arrowFirst, arrowSecond, ShapePoint(50f, 0f), tolerance = 2f))
    // 翼线上的点（头部长度 = max(8, 15) = 15，翼角 0.45 rad）也应命中。
    val wing = ShapePoint(
      x = arrowSecond.x - 15f * kotlin.math.cos(0.45f),
      y = arrowSecond.y - 15f * kotlin.math.sin(0.45f),
    )
    val wingMidpoint = ShapePoint((arrowSecond.x + wing.x) / 2f, (arrowSecond.y + wing.y) / 2f)
    assertTrue(ShapeCatalog.hitTest(ShapeCatalogKind.ARROW, arrowFirst, arrowSecond, wingMidpoint, tolerance = 2f))
    // 四象限轴：中心十字交点命中，象限内部不命中。
    val axisFirst = ShapePoint(0f, 0f)
    val axisSecond = ShapePoint(200f, 200f)
    assertTrue(ShapeCatalog.hitTest(ShapeCatalogKind.AXIS_QUADRANTS, axisFirst, axisSecond, ShapePoint(100f, 100f), tolerance = 2f))
    assertFalse(ShapeCatalog.hitTest(ShapeCatalogKind.AXIS_QUADRANTS, axisFirst, axisSecond, ShapePoint(40f, 60f), tolerance = 2f))
    // 第一象限轴：x 轴中点命中，象限内偏离处不命中。
    assertTrue(ShapeCatalog.hitTest(ShapeCatalogKind.AXIS_QUADRANT_1, axisFirst, axisSecond, ShapePoint(50f, 0f), tolerance = 2f))
    assertFalse(ShapeCatalog.hitTest(ShapeCatalogKind.AXIS_QUADRANT_1, axisFirst, axisSecond, ShapePoint(50f, 80f), tolerance = 2f))
  }

  @Test
  fun waveStaysWithinAmplitudeEnvelopeAroundDragLine() {
    val outline = ShapeCatalog.outline(ShapeCatalogKind.WAVE, first, second)
    val amplitude = distance(first, second) * 0.08f + 1f
    outline.forEach { point ->
      val t = ((point.x - first.x) * (second.x - first.x) + (point.y - first.y) * (second.y - first.y)) /
        distance(first, second).let { it * it }
      val projectedX = first.x + t * (second.x - first.x)
      val projectedY = first.y + t * (second.y - first.y)
      assertTrue(
        "wave point ${point} escapes amplitude envelope",
        distance(point, ShapePoint(projectedX.toFloat(), projectedY.toFloat())) <= amplitude,
      )
    }
    // 波浪中线（距 drag 线 1px 且位于波峰波谷之间）不能全程命中——远离波峰波谷处应未命中。
    // 取 t=0.25（相位 1.25π，正弦值≈-0.707，法向偏移≈-0.707*amp），在该点法向 +amp*0.5 处探测。
    val length = distance(first, second)
    val dx = (second.x - first.x) / length
    val dy = (second.y - first.y) / length
    val probeT = 0.25f
    val baseX = first.x + (second.x - first.x) * probeT
    val baseY = first.y + (second.y - first.y) * probeT
    val probe = ShapePoint(baseX + (-dy) * length * 0.08f * 0.5f, baseY + dx * length * 0.08f * 0.5f)
    assertFalse(
      "probe between crest and trough must miss the wave stroke",
      ShapeCatalog.hitTest(ShapeCatalogKind.WAVE, first, second, probe, tolerance = 1f),
    )
  }

  @Test
  fun squareAndCircleInscribeShorterSideOfBoundingBox() {
    val tallBox = ShapePoint(100f, 100f) to ShapePoint(160f, 300f)
    val squareBounds = ShapeCatalog.bounds(ShapeCatalogKind.SQUARE, tallBox.first, tallBox.second)
    val circleBounds = ShapeCatalog.bounds(ShapeCatalogKind.CIRCLE, tallBox.first, tallBox.second)
    // 高 200 宽 60 的框 → 内接边长 60，包围盒宽高均为 60。
    assertEquals(60f, squareBounds.width, 0.5f)
    assertEquals(60f, squareBounds.height, 0.5f)
    assertEquals(60f, circleBounds.width, 0.5f)
    assertEquals(60f, circleBounds.height, 0.5f)
    // 内接方/圆与框角留白：框角点在紧容差下不命中。
    assertFalse(ShapeCatalog.hitTest(ShapeCatalogKind.SQUARE, tallBox.first, tallBox.second, ShapePoint(100f, 100f), tolerance = 2f))
    assertFalse(ShapeCatalog.hitTest(ShapeCatalogKind.CIRCLE, tallBox.first, tallBox.second, ShapePoint(100f, 300f), tolerance = 2f))
    // 椭圆基线：中心不命中，边缘命中。
    assertFalse(ShapeCatalog.hitTest(ShapeCatalogKind.ELLIPSE, first, second, ShapePoint(200f, 180f), tolerance = 2f))
    assertTrue(ShapeCatalog.hitTest(ShapeCatalogKind.ELLIPSE, first, second, ShapePoint(100f, 180f), tolerance = 4f))
  }

  @Test
  fun boundsCoverOutlineIncludingArrowHeads() {
    val arrowFirst = ShapePoint(0f, 0f)
    val arrowSecond = ShapePoint(100f, 0f)
    val bounds = ShapeCatalog.bounds(ShapeCatalogKind.ARROW, arrowFirst, arrowSecond)
    // 翼点 y 为 -15*sin(0.45)≈-6.5，包围盒须覆盖负 y。
    assertTrue(bounds.top < -5f)
    assertTrue(bounds.bottom > 5f)
    val lineBounds = ShapeCatalog.bounds(ShapeCatalogKind.LINE, arrowFirst, arrowSecond)
    assertEquals(0f, lineBounds.top, 0f)
    assertEquals(0f, lineBounds.bottom, 0f)
  }

  @Test
  fun toleranceControlsHitRadius() {
    // 线 y=10 上方 3px 的近失点：小容差未命中、大容差命中。
    val nearMiss = ShapePoint(15f, 13f)
    assertFalse(ShapeCatalog.hitTest(ShapeCatalogKind.LINE, ShapePoint(10f, 10f), ShapePoint(100f, 10f), nearMiss, tolerance = 2f))
    assertTrue(ShapeCatalog.hitTest(ShapeCatalogKind.LINE, ShapePoint(10f, 10f), ShapePoint(100f, 10f), nearMiss, tolerance = 5f))
  }

  private fun distance(first: ShapePoint, second: ShapePoint): Float =
    kotlin.math.hypot(first.x - second.x, first.y - second.y)
}

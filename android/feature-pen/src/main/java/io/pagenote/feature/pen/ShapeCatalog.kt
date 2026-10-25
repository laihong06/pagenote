package io.pagenote.feature.pen

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/**
 * 纯 JVM 形状几何目录（移植批次 2）。
 *
 * 约定对齐 `android/docs/spec/canvas-schema-decision.md`：
 * - D4：一切矢量形状由 `first` / `second` 两点参数化，不发明 center+radius / w+h 替代编码；
 *   线族保留拖拽方向（first→second），面族按对角两点取 min/max 归一化为包围盒。
 * - D5：目录 kind 序列化为 annotations.json item 的 extension 字段 `shape`（kind id），
 *   基类型 `type` 仍取桌面 9 类型封闭枚举中的 `line` / `rect` / `ellipse`；
 *   未升级的读端按基类型渲染（透传降级），不破坏 D1。
 *
 * 本对象只做几何（轮廓折线 / 包围盒 / hit-test），不含任何 Android 依赖，全部可单测。
 */
enum class ShapeCatalogKind(
  /** 线格式 id：annotations.json extension 字段 `shape` 的取值，只增不删。 */
  val id: String,
  /** 桌面 9 类型封闭枚举中的基类型（`line` / `rect` / `ellipse`）。 */
  val baseType: String,
  /** 轮廓是否闭合（闭合形状的 outline 首尾点相同）。 */
  val closed: Boolean,
  /** 描边样式（虚线族与实线族共享同一轮廓几何）。 */
  val strokeStyle: ShapeStrokeStyle = ShapeStrokeStyle.SOLID,
) {
  LINE("line", "line", closed = false),
  DASHED_LINE("dashed-line", "line", closed = false, strokeStyle = ShapeStrokeStyle.DASHED),
  ARROW("arrow", "line", closed = false),
  DOUBLE_ARROW("double-arrow", "line", closed = false),
  AXIS_QUADRANT_1("axis-quadrant-1", "line", closed = false),
  AXIS_QUADRANTS("axis-quadrants", "line", closed = false),
  WAVE("wave", "line", closed = false),
  ZIGZAG("zigzag", "line", closed = false),

  RECT("rect", "rect", closed = true),
  SQUARE("square", "rect", closed = true),
  ROUNDED_RECT("rounded-rect", "rect", closed = true),
  DASHED_RECT("dashed-rect", "rect", closed = true, strokeStyle = ShapeStrokeStyle.DASHED),
  TRIANGLE("triangle", "rect", closed = true),
  RIGHT_TRIANGLE("right-triangle", "rect", closed = true),
  DIAMOND("diamond", "rect", closed = true),
  PENTAGON("pentagon", "rect", closed = true),
  HEXAGON("hexagon", "rect", closed = true),
  STAR_5("star-5", "rect", closed = true),
  STAR_4("star-4", "rect", closed = true),
  PARALLELOGRAM("parallelogram", "rect", closed = true),
  TRAPEZOID("trapezoid", "rect", closed = true),
  CROSS("cross", "rect", closed = false),

  ELLIPSE("ellipse", "ellipse", closed = true),
  CIRCLE("circle", "ellipse", closed = true),
}

enum class ShapeStrokeStyle { SOLID, DASHED }

/** 几何平面点（与压感无关，区别于 [PenPoint]）。 */
data class ShapePoint(val x: Float, val y: Float)

/** 由轮廓点集导出的包围盒。 */
data class ShapeBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
  val width get() = right - left
  val height get() = bottom - top

  fun contains(point: ShapePoint, tolerance: Float = 0f): Boolean =
    point.x >= left - tolerance && point.x <= right + tolerance &&
      point.y >= top - tolerance && point.y <= bottom + tolerance
}

object ShapeCatalog {
  /** 对话框展示顺序（线族 → 面族 → 椭圆族）。 */
  val kinds: List<ShapeCatalogKind> = ShapeCatalogKind.entries.toList()

  private val byIdMap: Map<String, ShapeCatalogKind> = kinds.associateBy(ShapeCatalogKind::id)

  /** 未知 id 返回 null，由调用方决定按基类型降级。 */
  fun byId(id: String): ShapeCatalogKind? = byIdMap[id]

  private const val CURVE_SEGMENTS = 48
  private const val WAVE_SEGMENTS = 48
  private const val WAVE_AMPLITUDE_RATIO = 0.08f
  private const val WAVE_PERIODS = 2.5f
  private const val ARROW_HEAD_RATIO = 0.15f
  private const val ARROW_HEAD_MIN = 8f
  private const val ARROW_HEAD_ANGLE = 0.45f
  private const val ROUNDED_CORNER_RATIO = 0.2f
  private const val PARALLELOGRAM_OFFSET_RATIO = 0.25f
  private const val TRAPEZOID_TOP_RATIO = 0.5f

  /**
   * 由两点生成轮廓折线。确定性纯函数：同输入恒同输出。
   *
   * - 线族（baseType=line）：保留 first→second 方向；两点重合时退化为重复点轮廓。
   * - 面族（baseType=rect/ellipse）：按对角两点取 min/max 包围盒；square/circle 在
   *   包围盒内取中心内接（min 边长），与拖拽方向无关。
   * - 闭合形状：outline 首尾点相同。
   */
  fun outline(
    kind: ShapeCatalogKind,
    first: ShapePoint,
    second: ShapePoint,
  ): List<ShapePoint> = when (kind) {
    ShapeCatalogKind.LINE -> lineOutline(first, second)
    ShapeCatalogKind.DASHED_LINE -> lineOutline(first, second)
    ShapeCatalogKind.ARROW -> arrowOutline(first, second, headAtEnd = true, headAtStart = false)
    ShapeCatalogKind.DOUBLE_ARROW -> arrowOutline(first, second, headAtEnd = true, headAtStart = true)
    ShapeCatalogKind.AXIS_QUADRANT_1 -> firstQuadrantAxisOutline(first, second)
    ShapeCatalogKind.AXIS_QUADRANTS -> fourQuadrantAxisOutline(first, second)
    ShapeCatalogKind.WAVE -> waveOutline(first, second, rounded = true)
    ShapeCatalogKind.ZIGZAG -> waveOutline(first, second, rounded = false)
    ShapeCatalogKind.RECT -> rectOutline(left(first, second), top(first, second), right(first, second), bottom(first, second))
    ShapeCatalogKind.DASHED_RECT -> rectOutline(left(first, second), top(first, second), right(first, second), bottom(first, second))
    ShapeCatalogKind.SQUARE -> centeredSquareOutline(first, second)
    ShapeCatalogKind.ROUNDED_RECT -> roundedRectOutline(first, second)
    ShapeCatalogKind.TRIANGLE -> triangleOutline(first, second)
    ShapeCatalogKind.RIGHT_TRIANGLE -> rightTriangleOutline(first, second)
    ShapeCatalogKind.DIAMOND -> diamondOutline(first, second)
    ShapeCatalogKind.PENTAGON -> regularPolygonOutline(first, second, sides = 5, rotation = -PI / 2.0)
    ShapeCatalogKind.HEXAGON -> regularPolygonOutline(first, second, sides = 6, rotation = -PI / 2.0)
    ShapeCatalogKind.STAR_5 -> starOutline(first, second, points = 5)
    ShapeCatalogKind.STAR_4 -> starOutline(first, second, points = 4)
    ShapeCatalogKind.PARALLELOGRAM -> parallelogramOutline(first, second)
    ShapeCatalogKind.TRAPEZOID -> trapezoidOutline(first, second)
    ShapeCatalogKind.CROSS -> crossOutline(first, second)
    ShapeCatalogKind.ELLIPSE -> ellipseOutline(first, second)
    ShapeCatalogKind.CIRCLE -> circleOutline(first, second)
  }

  /** 轮廓点集包围盒（含箭头翼等超出拖拽框的部分）。 */
  fun bounds(kind: ShapeCatalogKind, first: ShapePoint, second: ShapePoint): ShapeBounds {
    val points = outline(kind, first, second)
    val left = points.minOf(ShapePoint::x)
    val top = points.minOf(ShapePoint::y)
    val right = points.maxOf(ShapePoint::x)
    val bottom = points.maxOf(ShapePoint::y)
    return ShapeBounds(left, top, right, bottom)
  }

  /**
   * Stroke hit-test：点到轮廓任一线段距离 ≤ tolerance 视为命中。
   * 面族不填充——只测描边（与桌面形状均为描边渲染一致）。
   */
  fun hitTest(
    kind: ShapeCatalogKind,
    first: ShapePoint,
    second: ShapePoint,
    point: ShapePoint,
    tolerance: Float = 6f,
  ): Boolean {
    val points = outline(kind, first, second)
    if (points.isEmpty()) return false
    if (points.size == 1) return distance(point, points[0]) <= tolerance
    var previous = points[0]
    for (index in 1 until points.size) {
      val current = points[index]
      if (distanceToSegment(point, previous, current) <= tolerance) return true
      previous = current
    }
    return false
  }

  // ---- 线族 ----

  private fun lineOutline(first: ShapePoint, second: ShapePoint): List<ShapePoint> =
    listOf(first, second)

  private fun arrowOutline(
    first: ShapePoint,
    second: ShapePoint,
    headAtEnd: Boolean,
    headAtStart: Boolean,
  ): List<ShapePoint> {
    val outline = mutableListOf<ShapePoint>()
    if (headAtStart) {
      outline += headWings(second, first)
    }
    outline += first
    outline += second
    if (headAtEnd) {
      outline += headWings(first, second)
    }
    return outline
  }

  /** 返回箭头两翼（杆端到两翼的连线段，追加进轮廓以参与 hit-test）。 */
  private fun headWings(shaftFrom: ShapePoint, tip: ShapePoint): List<ShapePoint> {
    val length = distance(shaftFrom, tip)
    if (length <= 0f) return emptyList()
    val headLength = max(ARROW_HEAD_MIN, length * ARROW_HEAD_RATIO)
    val direction = atan2(tip.y - shaftFrom.y, tip.x - shaftFrom.x)
    val wings = listOf(direction - ARROW_HEAD_ANGLE, direction + ARROW_HEAD_ANGLE).map { angle ->
      ShapePoint(
        x = tip.x - headLength * cos(angle),
        y = tip.y - headLength * sin(angle),
      )
    }
    return listOf(tip, wings[0], tip, wings[1])
  }

  /** 第一象限坐标轴：first 为原点，x 轴指向 second 的 x 分量、y 轴指向 second 的 y 分量，双轴带箭头。 */
  private fun firstQuadrantAxisOutline(first: ShapePoint, second: ShapePoint): List<ShapePoint> {
    if (first == second) return listOf(first, second)
    val outline = mutableListOf<ShapePoint>()
    outline += headWings(ShapePoint(second.x, first.y), first)
    outline += first
    outline += ShapePoint(second.x, first.y)
    outline += headWings(ShapePoint(first.x, second.y), first)
    outline += first
    outline += ShapePoint(first.x, second.y)
    return outline
  }

  /** 四象限坐标轴：包围盒中心十字，四端均带箭头。 */
  private fun fourQuadrantAxisOutline(first: ShapePoint, second: ShapePoint): List<ShapePoint> {
    val left = left(first, second)
    val top = top(first, second)
    val right = right(first, second)
    val bottom = bottom(first, second)
    if (left == right && top == bottom) return listOf(first, second)
    val centerX = (left + right) / 2f
    val centerY = (top + bottom) / 2f
    val center = ShapePoint(centerX, centerY)
    val outline = mutableListOf<ShapePoint>()
    outline += headWings(ShapePoint(right, centerY), center)
    outline += center
    outline += ShapePoint(right, centerY)
    outline += headWings(ShapePoint(left, centerY), center)
    outline += center
    outline += ShapePoint(left, centerY)
    outline += headWings(ShapePoint(centerX, top), center)
    outline += center
    outline += ShapePoint(centerX, top)
    outline += headWings(ShapePoint(centerX, bottom), center)
    outline += center
    outline += ShapePoint(centerX, bottom)
    return outline
  }

  /**
   * 波浪（rounded=true 正弦）/ 锯齿（rounded=false 三角波）：沿 first→second 展波，
   * 振幅为两点距离的 [WAVE_AMPLITUDE_RATIO]。首尾点恒落在锚点上（D4 两点参数化）。
   */
  private fun waveOutline(first: ShapePoint, second: ShapePoint, rounded: Boolean): List<ShapePoint> {
    if (first == second) return listOf(first, second)
    val length = distance(first, second)
    val amplitude = length * WAVE_AMPLITUDE_RATIO
    val normalX = -(second.y - first.y) / length
    val normalY = (second.x - first.x) / length
    val outline = mutableListOf<ShapePoint>()
    for (step in 0..WAVE_SEGMENTS) {
      val t = step.toDouble() / WAVE_SEGMENTS
      val phase = t * WAVE_PERIODS * 2.0 * PI
      // 三角波由正弦符号截断近似，保持顶点数一致便于共享轮廓参数。
      val offset = if (rounded) sin(phase).toFloat() else sign(sin(phase)).toFloat() * 0.6f
      outline += ShapePoint(
        x = first.x + (second.x - first.x) * t.toFloat() + normalX * amplitude * offset,
        y = first.y + (second.y - first.y) * t.toFloat() + normalY * amplitude * offset,
      )
    }
    outline[0] = first
    outline[outline.lastIndex] = second
    return outline
  }

  // ---- 面族（rect 基类型）----

  private fun rectOutline(left: Float, top: Float, right: Float, bottom: Float): List<ShapePoint> = listOf(
    ShapePoint(left, top),
    ShapePoint(right, top),
    ShapePoint(right, bottom),
    ShapePoint(left, bottom),
    ShapePoint(left, top),
  )

  private fun centeredSquareOutline(first: ShapePoint, second: ShapePoint): List<ShapePoint> {
    val side = min(abs(right(first, second) - left(first, second)), abs(bottom(first, second) - top(first, second)))
    val centerX = (left(first, second) + right(first, second)) / 2f
    val centerY = (top(first, second) + bottom(first, second)) / 2f
    val half = side / 2f
    return rectOutline(centerX - half, centerY - half, centerX + half, centerY + half)
  }

  private fun roundedRectOutline(first: ShapePoint, second: ShapePoint): List<ShapePoint> {
    val left = left(first, second)
    val top = top(first, second)
    val right = right(first, second)
    val bottom = bottom(first, second)
    val radius = min(abs(right - left), abs(bottom - top)) * ROUNDED_CORNER_RATIO / 2f
    val outline = mutableListOf<ShapePoint>()
    outline += ShapePoint(left + radius, top)
    outline += ShapePoint(right - radius, top)
    outline += ShapePoint(right, top + radius)
    outline += ShapePoint(right, bottom - radius)
    outline += ShapePoint(right - radius, bottom)
    outline += ShapePoint(left + radius, bottom)
    outline += ShapePoint(left, bottom - radius)
    outline += ShapePoint(left, top + radius)
    outline += ShapePoint(left, top)
    outline += outline.first()
    return outline
  }

  private fun triangleOutline(first: ShapePoint, second: ShapePoint): List<ShapePoint> {
    val left = left(first, second)
    val top = top(first, second)
    val right = right(first, second)
    val bottom = bottom(first, second)
    val centerX = (left + right) / 2f
    return listOf(
      ShapePoint(centerX, top),
      ShapePoint(right, bottom),
      ShapePoint(left, bottom),
      ShapePoint(centerX, top),
    )
  }

  private fun rightTriangleOutline(first: ShapePoint, second: ShapePoint): List<ShapePoint> {
    val left = left(first, second)
    val top = top(first, second)
    val right = right(first, second)
    val bottom = bottom(first, second)
    return listOf(
      ShapePoint(left, bottom),
      ShapePoint(right, bottom),
      ShapePoint(left, top),
      ShapePoint(left, bottom),
    )
  }

  private fun diamondOutline(first: ShapePoint, second: ShapePoint): List<ShapePoint> {
    val left = left(first, second)
    val top = top(first, second)
    val right = right(first, second)
    val bottom = bottom(first, second)
    val centerX = (left + right) / 2f
    val centerY = (top + bottom) / 2f
    return listOf(
      ShapePoint(centerX, top),
      ShapePoint(right, centerY),
      ShapePoint(centerX, bottom),
      ShapePoint(left, centerY),
      ShapePoint(centerX, top),
    )
  }

  /** 正 N 边形内接于包围盒椭圆，rotation 为首顶点相位（-PI/2 顶点朝上）。 */
  private fun regularPolygonOutline(
    first: ShapePoint,
    second: ShapePoint,
    sides: Int,
    rotation: Double,
  ): List<ShapePoint> {
    val left = left(first, second)
    val top = top(first, second)
    val right = right(first, second)
    val bottom = bottom(first, second)
    val centerX = (left + right) / 2f
    val centerY = (top + bottom) / 2f
    val radiusX = abs(right - left) / 2f
    val radiusY = abs(bottom - top) / 2f
    val outline = mutableListOf<ShapePoint>()
    for (index in 0..sides) {
      val angle = rotation + index.toDouble() / sides * 2.0 * PI
      outline += ShapePoint(
        x = centerX + radiusX * cos(angle).toFloat(),
        y = centerY + radiusY * sin(angle).toFloat(),
      )
    }
    return outline
  }

  /** N 角星：外接包围盒椭圆，内半径取外半径的 0.5 倍。 */
  private fun starOutline(first: ShapePoint, second: ShapePoint, points: Int): List<ShapePoint> {
    val left = left(first, second)
    val top = top(first, second)
    val right = right(first, second)
    val bottom = bottom(first, second)
    val centerX = (left + right) / 2f
    val centerY = (top + bottom) / 2f
    val radiusX = abs(right - left) / 2f
    val radiusY = abs(bottom - top) / 2f
    val innerRadiusRatio = 0.5f
    val outline = mutableListOf<ShapePoint>()
    val total = points * 2
    for (index in 0..total) {
      val angle = -PI / 2.0 + index.toDouble() / total * 2.0 * PI
      val ratio = if (index % 2 == 0) 1f else innerRadiusRatio
      outline += ShapePoint(
        x = centerX + radiusX * ratio * cos(angle).toFloat(),
        y = centerY + radiusY * ratio * sin(angle).toFloat(),
      )
    }
    return outline
  }

  private fun parallelogramOutline(first: ShapePoint, second: ShapePoint): List<ShapePoint> {
    val left = left(first, second)
    val top = top(first, second)
    val right = right(first, second)
    val bottom = bottom(first, second)
    val offset = abs(right - left) * PARALLELOGRAM_OFFSET_RATIO
    return listOf(
      ShapePoint(left + offset, top),
      ShapePoint(right, top),
      ShapePoint(right - offset, bottom),
      ShapePoint(left, bottom),
      ShapePoint(left + offset, top),
    )
  }

  private fun trapezoidOutline(first: ShapePoint, second: ShapePoint): List<ShapePoint> {
    val left = left(first, second)
    val top = top(first, second)
    val right = right(first, second)
    val bottom = bottom(first, second)
    val topWidth = abs(right - left) * TRAPEZOID_TOP_RATIO
    val topInset = (abs(right - left) - topWidth) / 2f
    return listOf(
      ShapePoint(left + topInset, top),
      ShapePoint(right - topInset, top),
      ShapePoint(right, bottom),
      ShapePoint(left, bottom),
      ShapePoint(left + topInset, top),
    )
  }

  private fun crossOutline(first: ShapePoint, second: ShapePoint): List<ShapePoint> {
    val left = left(first, second)
    val top = top(first, second)
    val right = right(first, second)
    val bottom = bottom(first, second)
    val centerX = (left + right) / 2f
    val centerY = (top + bottom) / 2f
    return listOf(
      ShapePoint(left, centerY),
      ShapePoint(right, centerY),
      ShapePoint(centerX, top),
      ShapePoint(centerX, bottom),
    )
  }

  // ---- 椭圆族 ----

  private fun ellipseOutline(first: ShapePoint, second: ShapePoint): List<ShapePoint> {
    val left = left(first, second)
    val top = top(first, second)
    val right = right(first, second)
    val bottom = bottom(first, second)
    val centerX = (left + right) / 2f
    val centerY = (top + bottom) / 2f
    val radiusX = abs(right - left) / 2f
    val radiusY = abs(bottom - top) / 2f
    val outline = mutableListOf<ShapePoint>()
    for (index in 0..CURVE_SEGMENTS) {
      val angle = index.toDouble() / CURVE_SEGMENTS * 2.0 * PI
      outline += ShapePoint(
        x = centerX + radiusX * cos(angle).toFloat(),
        y = centerY + radiusY * sin(angle).toFloat(),
      )
    }
    return outline
  }

  private fun circleOutline(first: ShapePoint, second: ShapePoint): List<ShapePoint> {
    val side = min(abs(right(first, second) - left(first, second)), abs(bottom(first, second) - top(first, second)))
    val centerX = (left(first, second) + right(first, second)) / 2f
    val centerY = (top(first, second) + bottom(first, second)) / 2f
    val radius = side / 2f
    val outline = mutableListOf<ShapePoint>()
    for (index in 0..CURVE_SEGMENTS) {
      val angle = index.toDouble() / CURVE_SEGMENTS * 2.0 * PI
      outline += ShapePoint(
        x = centerX + radius * cos(angle).toFloat(),
        y = centerY + radius * sin(angle).toFloat(),
      )
    }
    return outline
  }

  // ---- 基础几何 ----

  private fun left(first: ShapePoint, second: ShapePoint) = min(first.x, second.x)

  private fun top(first: ShapePoint, second: ShapePoint) = min(first.y, second.y)

  private fun right(first: ShapePoint, second: ShapePoint) = max(first.x, second.x)

  private fun bottom(first: ShapePoint, second: ShapePoint) = max(first.y, second.y)

  private fun distance(first: ShapePoint, second: ShapePoint): Float =
    hypot(first.x - second.x, first.y - second.y)

  private fun distanceToSegment(point: ShapePoint, start: ShapePoint, end: ShapePoint): Float {
    val segmentX = end.x - start.x
    val segmentY = end.y - start.y
    val lengthSquared = segmentX * segmentX + segmentY * segmentY
    if (lengthSquared == 0f) return distance(point, start)
    val t = ((point.x - start.x) * segmentX + (point.y - start.y) * segmentY) / lengthSquared
    val clamped = t.coerceIn(0f, 1f)
    val projectedX = start.x + clamped * segmentX
    val projectedY = start.y + clamped * segmentY
    return distance(point, ShapePoint(projectedX, projectedY))
  }
}

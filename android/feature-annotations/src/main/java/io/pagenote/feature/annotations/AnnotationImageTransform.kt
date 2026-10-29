package io.pagenote.feature.annotations

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 图片/贴纸项的拖拽与缩放数据层（移植批次 5）。对应 touchnotes 的
 * `ShapeMatrix{offsetX, offsetY, scaleX, scaleY, angle}` + `MoveAndZoomAction`
 * 拖拽/缩放手势语义，投影到 annotations.json 的 image 两点 bbox（D4）+
 * D7 扩展字段（`angle` / `flipX` / `flipY`）上：
 *
 * - 平移与等比缩放直接改写 canonical `points`（桌面渲染端无感）；
 * - 旋转与镜像只改扩展字段，桌面按未旋转 bbox 渲染并原样保留字段——与
 *   D5 §6 的降级渲染边界同构，渲染接线归画布重做批次。
 *
 * 纯数据代数：无 Android 依赖、无副作用；item 级读写经 [applyToItem] /
 * [fromItem]，未涉及的字段（含未知 extension 字段）原样保留（D2）。
 */
data class AnnotationImageTransform(
  val bounds: StickerAnnotation.Bounds,
  val angleDegrees: Double = 0.0,
  val flipX: Boolean = false,
  val flipY: Boolean = false,
) {
  init {
    require(bounds.width() > 0.0 && bounds.height() > 0.0) { "degenerate bounds: $bounds" }
  }

  /** 平移（拖拽）：两点整体加 delta。 */
  fun moved(dx: Double, dy: Double): AnnotationImageTransform {
    require(dx.isFinite() && dy.isFinite()) { "bad drag delta: $dx, $dy" }
    return copy(
      bounds = bounds.copy(
        left = bounds.left + dx,
        top = bounds.top + dy,
        right = bounds.right + dx,
        bottom = bounds.bottom + dy,
      ),
    )
  }

  /**
   * 等比缩放（捏合）：绕 [anchor]（默认中心）按 `factor > 0` 缩放；结果任一边
   * 小于 [StickerAnnotation.MIN_EDGE] 时返回 null（拒绝产生退化 bbox）。
   */
  fun scaled(factor: Double, anchor: ScaleAnchor = ScaleAnchor.CENTER): AnnotationImageTransform? {
    require(factor.isFinite() && factor > 0.0) { "bad scale factor: $factor" }
    val (px, py) = when (anchor) {
      ScaleAnchor.CENTER -> bounds.centerX() to bounds.centerY()
      ScaleAnchor.FIRST_CORNER -> bounds.left to bounds.top
    }
    fun scaleAxis(value: Double): Double = px + (value - px) * factor
    fun scaleOrdinate(value: Double): Double = py + (value - py) * factor
    val left = scaleAxis(bounds.left)
    val top = scaleOrdinate(bounds.top)
    val right = scaleAxis(bounds.right)
    val bottom = scaleOrdinate(bounds.bottom)
    val next = StickerAnnotation.Bounds(
      minOf(left, right),
      minOf(top, bottom),
      maxOf(left, right),
      maxOf(top, bottom),
    )
    if (listOf(next.left, next.top, next.right, next.bottom).any { !it.isFinite() || abs(it) > StickerAnnotation.MAX_COORD_ABS }) {
      return null
    }
    if (next.width() < StickerAnnotation.MIN_EDGE || next.height() < StickerAnnotation.MIN_EDGE) return null
    return copy(bounds = next)
  }

  /** 旋转（绕 bbox 中心）：角度累加并规范到 (-180, 180]。 */
  fun rotated(deltaDegrees: Double): AnnotationImageTransform {
    require(deltaDegrees.isFinite()) { "bad rotation delta: $deltaDegrees" }
    return copy(angleDegrees = StickerAnnotation.normalizeAngle(angleDegrees + deltaDegrees))
  }

  /** 镜像：翻转轴。 */
  fun flipped(axis: FlipAxis): AnnotationImageTransform = when (axis) {
    FlipAxis.X -> copy(flipX = !flipX)
    FlipAxis.Y -> copy(flipY = !flipY)
  }

  /**
   * 命中测试：点是否落在（考虑 `angle` 旋转与 flip 后的）贴纸矩形内，容差
   * [tolerance]（文档单位）。旋转语义：矩形绕中心旋转 `angleDegrees`。
   */
  fun hitTest(x: Double, y: Double, tolerance: Double = 0.0): Boolean {
    if (!x.isFinite() || !y.isFinite() || tolerance < 0.0) return false
    val radians = Math.toRadians(-angleDegrees)
    val cos = cos(radians)
    val sin = sin(radians)
    val dx = x - bounds.centerX()
    val dy = y - bounds.centerY()
    val localX = dx * cos - dy * sin
    val localY = dx * sin + dy * cos
    val halfWidth = bounds.width() / 2.0 + tolerance
    val halfHeight = bounds.height() / 2.0 + tolerance
    return localX in -halfWidth..halfWidth && localY in -halfHeight..halfHeight
  }

  /** 序列化为 item 的 extension 字段（默认值省略；points 由 [applyToItem] 写 canonical）。 */
  fun extensionFields(): Map<String, Any?> = buildMap {
    if (angleDegrees != 0.0) put(StickerAnnotation.ANGLE_FIELD, angleDegrees)
    if (flipX) put(StickerAnnotation.FLIP_X_FIELD, true)
    if (flipY) put(StickerAnnotation.FLIP_Y_FIELD, true)
  }

  /**
   * 把本变换写回一个 image item：改写 canonical `points`（归一化两点）与
   * angle/flipX/flipY 扩展字段（回正姿态即移除对应字段），其余字段
   * （url/color/width/sticker/latex 及一切未知字段）原样保留（D2）。
   * 返回新对象，不修改入参。
   */
  fun applyToItem(item: JsonObject): JsonObject {
    val rebuilt = item.toMutableMap()
    listOf(
      StickerAnnotation.ANGLE_FIELD,
      StickerAnnotation.FLIP_X_FIELD,
      StickerAnnotation.FLIP_Y_FIELD,
    ).forEach(rebuilt::remove)
    extensionFields().forEach { (key, value) ->
      rebuilt[key] = when (value) {
        is Boolean -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
      }
    }
    rebuilt["points"] = JsonArray(
      listOf(
        JsonArray(listOf(JsonPrimitive(bounds.left), JsonPrimitive(bounds.top))),
        JsonArray(listOf(JsonPrimitive(bounds.right), JsonPrimitive(bounds.bottom))),
      ),
    )
    return JsonObject(rebuilt)
  }

  enum class ScaleAnchor { CENTER, FIRST_CORNER }

  enum class FlipAxis { X, Y }

  companion object {
    /**
     * 从 image item 读出变换。容错语义与 [StickerAnnotation.fromItem] 一致：
     * 非 image 项或几何非法返回 null；越界角度/非布尔 flip 降级默认。
     */
    fun fromItem(item: JsonObject): AnnotationImageTransform? {
      val sticker = StickerAnnotation.fromItem(item) ?: return null
      return AnnotationImageTransform(
        bounds = StickerAnnotation.bounds(sticker.first, sticker.second),
        angleDegrees = sticker.angleDegrees,
        flipX = sticker.flipX,
        flipY = sticker.flipY,
      )
    }
  }
}

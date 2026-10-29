package io.pagenote.feature.annotations

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 贴纸对象落盘 schema（移植批次 5，schema 决策 D7，canvas-schema-decision.md）。
 *
 * 贴纸在 annotations.json 中**不新增 `type` 枚举**（延续 D1）：落盘为桌面 9 类型中的
 * `image` 项，位图沿用项目附件通道（`url: /attachments/<uuid36>.(png|jpe?g|gif|webp)`，
 * 由 ProjectImageImporter 写入项目 `assets/` 目录），几何沿用 D4 的两点 bbox 参数化
 * （对齐桌面 `shape()` 的 image 分支，最小边 10）。
 *
 * 贴纸身份与摆放姿态走 D2 透传通道的 item extension 字段（桌面校验器只查已知字段，
 * 这些键原样落盘不被拒绝）：
 *
 * - `sticker`（字符串，可选）：贴纸来源标识——贴纸库内的相对路径（如 `"cat.png"` 或
 *   `"pack1/cat.png"`）。缺省表示普通图片批注（非贴纸）。
 * - `angle`（数值，度，可选）：绕 bbox 中心的旋转角，规范域 `(-180, 180]`；缺省 0。
 * - `flipX` / `flipY`（布尔，可选）：水平 / 垂直镜像；缺省 false。
 * - `latex`（字符串数组，可选）+ `latexColor`（字符串 `#rrggbb`，可选）：公式素材的
 *   源数据（对齐 touchnotes `Image.mLatexTexts`/`latexColor`——位图附件是渲染产物，
 *   latex 字段保留可重渲染性）。见 [FormulaAnnotation]。
 *
 * GIF 贴纸不引入新字段：`url` 的 `.gif` 后缀即动效标记（[isAnimatedUrl]）。
 *
 * 读侧容错（对齐 D5 §3 / D6 §4）：未知 `sticker` 来源原样保留；越界 `angle`、非布尔
 * `flipX/flipY`、非法 `latex` 一律降级为默认值，绝不拒绝 item——桌面写入的历史数据
 * 永不被 Android 判非法。写侧严格：[create] 校验失败返回 null，Android 永不写非法值。
 */
data class StickerAnnotation(
  /** 附件文件名（uuid36 + 扩展名）；`url` 由 [urlForAssetName] 派生。 */
  val assetName: String,
  /** 两点 bbox（任意对角顺序；[toItem] 输出前经 [bounds] 归一化为左上/右下）。 */
  val first: Pair<Double, Double>,
  val second: Pair<Double, Double>,
  /** Canonical `color`（`#RRGGBB`）。 */
  val color: String,
  /** Canonical `width`（0.1..100）。 */
  val width: Double,
  /** `sticker` 扩展字段；null 表示普通图片批注（不写该字段）。 */
  val stickerSource: String? = null,
  /** `angle` 扩展字段（度，规范域 (-180, 180]）；0 不落盘。 */
  val angleDegrees: Double = 0.0,
  val flipX: Boolean = false,
  val flipY: Boolean = false,
) {
  fun toItem(): JsonObject {
    val normalized = bounds(first, second)
    val item = buildJsonObject {
      put("type", "image")
      put("color", color.lowercase())
      put("width", width)
      put("url", urlForAssetName(assetName))
      put(
        "points",
        JsonArray(
          listOf(
            JsonArray(listOf(JsonPrimitive(normalized.left), JsonPrimitive(normalized.top))),
            JsonArray(listOf(JsonPrimitive(normalized.right), JsonPrimitive(normalized.bottom))),
          ),
        ),
      )
      stickerSource?.let { put(STICKER_FIELD, it) }
      if (angleDegrees != 0.0) put(ANGLE_FIELD, angleDegrees)
      if (flipX) put(FLIP_X_FIELD, true)
      if (flipY) put(FLIP_Y_FIELD, true)
    }
    return item
  }

  companion object {
    const val STICKER_FIELD = "sticker"
    const val ANGLE_FIELD = "angle"
    const val FLIP_X_FIELD = "flipX"
    const val FLIP_Y_FIELD = "flipY"

    /** D4 image 分支：两点 bbox 最小边 10（桌面 `shape()` 的渲染下限）。 */
    const val MIN_EDGE = 10.0
    const val MAX_COORD_ABS = 1_000_000.0
    const val MAX_STICKER_SOURCE_LENGTH = 200

    private val COLOR = Regex("^#[a-fA-F0-9]{6}$")
    private val ASSET_NAME =
      Regex("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}\\.(png|jpg|jpeg|gif|webp)$")

    fun isValidAssetName(assetName: String): Boolean = ASSET_NAME.matches(assetName)
    fun isValidColor(color: String): Boolean = COLOR.matches(color)

    /** GIF 派生：`url` 后缀即动效标记，无独立字段。 */
    fun isAnimatedUrl(url: String): Boolean = url.substringBeforeLast('?').endsWith(".gif", ignoreCase = true)

    fun urlForAssetName(assetName: String): String = "/attachments/$assetName"

    /** 规范角度域 (-180, 180]（写侧与 [AnnotationImageTransform] 共用）。 */
    fun normalizeAngle(degrees: Double): Double {
      require(degrees.isFinite()) { "bad angle: $degrees" }
      var value = degrees % 360.0
      if (value <= -180.0) value += 360.0
      if (value > 180.0) value -= 360.0
      // 消除 -0.0。
      return value + 0.0
    }

    /** 归一化对角两点为左上/右下 bbox（D4：绘制取 min/max）。 */
    fun bounds(
      first: Pair<Double, Double>,
      second: Pair<Double, Double>,
    ): Bounds {
      val left = minOf(first.first, second.first)
      val top = minOf(first.second, second.second)
      val right = maxOf(first.first, second.first)
      val bottom = maxOf(first.second, second.second)
      return Bounds(left, top, right, bottom)
    }

    /**
     * 摆放几何：按位图像素纵横比与目标宽（文档单位）推导两点 bbox，中心对齐
     * `(centerX, centerY)`。宽或高不足 [MIN_EDGE] 时返回 null。
     */
    fun placementBounds(
      pixelWidth: Int,
      pixelHeight: Int,
      centerX: Double,
      centerY: Double,
      targetWidth: Double,
    ): Pair<Pair<Double, Double>, Pair<Double, Double>>? {
      if (pixelWidth <= 0 || pixelHeight <= 0 ||
        !centerX.isFinite() || !centerY.isFinite() ||
        !targetWidth.isFinite() || targetWidth < MIN_EDGE
      ) {
        return null
      }
      val width = targetWidth
      val height = targetWidth * pixelHeight / pixelWidth
      if (!height.isFinite() || height < MIN_EDGE) return null
      val left = centerX - width / 2.0
      val top = centerY - height / 2.0
      val right = centerX + width / 2.0
      val bottom = centerY + height / 2.0
      if (listOf(left, top, right, bottom).any { kotlin.math.abs(it) > MAX_COORD_ABS }) return null
      return (left to top) to (right to bottom)
    }

    /**
     * 写侧严格校验（draft 模式：失败返回 null，Android 永不写非法贴纸项）。
     */
    fun create(
      assetName: String,
      first: Pair<Double, Double>,
      second: Pair<Double, Double>,
      color: String,
      width: Double,
      stickerSource: String? = null,
      angleDegrees: Double = 0.0,
      flipX: Boolean = false,
      flipY: Boolean = false,
    ): StickerAnnotation? {
      if (!isValidAssetName(assetName) || !isValidColor(color)) return null
      if (!width.isFinite() || width !in 0.1..100.0) return null
      if (stickerSource != null &&
        (stickerSource.length > MAX_STICKER_SOURCE_LENGTH || stickerSource.isBlank() ||
          stickerSource.startsWith("/") || stickerSource.contains(".."))
      ) {
        return null
      }
      if (!angleDegrees.isFinite() || kotlin.math.abs(angleDegrees) > 360.0) return null
      val coordinates = listOf(first.first, first.second, second.first, second.second)
      if (coordinates.any { !it.isFinite() || kotlin.math.abs(it) > MAX_COORD_ABS }) return null
      val bbox = bounds(first, second)
      if (bbox.width() < MIN_EDGE || bbox.height() < MIN_EDGE) return null
      return StickerAnnotation(
        assetName = assetName,
        first = first,
        second = second,
        color = color.lowercase(),
        width = width,
        stickerSource = stickerSource,
        angleDegrees = normalizeAngle(angleDegrees),
        flipX = flipX,
        flipY = flipY,
      )
    }

    /**
     * 读侧容错解析：非 `image` 项或 canonical 字段非法（url/points/color/width）返回
     * null；扩展字段越界一律降级默认（angle→0、flip→false、sticker 非法→null），
     * 不拒绝 item。points 不是恰好 2 点返回 null（D4 image 两点参数化）。
     */
    fun fromItem(item: JsonObject): StickerAnnotation? {
      if (item.stringOrNull("type") != "image") return null
      val url = item.stringOrNull("url") ?: return null
      val assetName = url.removePrefix("/attachments/").takeIf(::isValidAssetName) ?: return null
      val color = item.stringOrNull("color")?.takeIf(StickerAnnotation::isValidColor) ?: return null
      val width = item["width"]?.jsonPrimitive?.doubleOrNull
      if (width == null || !width.isFinite() || width !in 0.1..100.0) return null
      val points = item["points"] as? JsonArray ?: return null
      if (points.size != 2) return null
      val first = points.pointAt(0) ?: return null
      val second = points.pointAt(1) ?: return null
      val bbox = bounds(first, second)
      if (bbox.width() <= 0.0 || bbox.height() <= 0.0) return null

      val sticker = item.stringOrNull(STICKER_FIELD)
        ?.takeIf { it.isNotBlank() && it.length <= MAX_STICKER_SOURCE_LENGTH && !it.startsWith("/") && !it.contains("..") }
      val angle = item[ANGLE_FIELD]?.jsonPrimitive?.doubleOrNull
        ?.takeIf { it.isFinite() && kotlin.math.abs(it) <= 360.0 }
        ?.let(::normalizeAngle) ?: 0.0
      val flipX = item.booleanOrNull(FLIP_X_FIELD) ?: false
      val flipY = item.booleanOrNull(FLIP_Y_FIELD) ?: false
      return StickerAnnotation(
        assetName = assetName,
        first = first,
        second = second,
        color = color.lowercase(),
        width = width,
        stickerSource = sticker,
        angleDegrees = angle,
        flipX = flipX,
        flipY = flipY,
      )
    }

    private fun JsonArray.pointAt(index: Int): Pair<Double, Double>? {
      val pair = this[index] as? JsonArray ?: return null
      if (pair.size != 2) return null
      val x = pair[0].jsonPrimitive.doubleOrNull ?: return null
      val y = pair[1].jsonPrimitive.doubleOrNull ?: return null
      if (!x.isFinite() || !y.isFinite()) return null
      return x to y
    }

    private fun JsonObject.stringOrNull(key: String): String? =
      this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

    private fun JsonObject.booleanOrNull(key: String): Boolean? =
      this[key]?.let { element ->
        runCatching { element.jsonPrimitive }.getOrNull()?.takeUnless { it.isString }?.booleanOrNull
      }
  }

  /** 归一化 bbox（left/top ≤ right/bottom）。 */
  data class Bounds(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    fun width(): Double = right - left
    fun height(): Double = bottom - top
    fun centerX(): Double = (left + right) / 2.0
    fun centerY(): Double = (top + bottom) / 2.0
  }
}

package io.pagenote.android

import io.pagenote.feature.annotations.CanvasMaterialCodec
import io.pagenote.feature.annotations.CanvasMaterialEntry
import io.pagenote.feature.annotations.CanvasMaterialKind
import io.pagenote.feature.annotations.FormulaAnnotation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 公式识别产物 → 画布对象/素材库（移植批次 6）。
 *
 * 画布公式沿用批次 5 的 D7 schema：`type:"image"` 项 + 附件位图，LaTeX 源
 * 走 `latex`/`latexColor` 扩展字段（[FormulaAnnotation.mergeIntoItem]），
 * 桌面端按普通图片渲染并透传扩展字段。素材库条目走 `materials.json`
 * （[CanvasMaterialCodec]，kind=FORMULA）。
 */
internal object FormulaMaterialInsertion {
  private val ASSET_NAME = Regex(
    "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}\\.(png|jpe?g|gif|webp)$",
  )
  private const val DISPLAY_WIDTH_UNITS = 350.0
  private const val MAX_COORDINATE = 1_000_000.0

  /**
   * 构建公式 image 项。锚点为选区左上角（文档单位）；显示宽度固定
   * [DISPLAY_WIDTH_UNITS]（与贴纸/图片插入一致），高度按位图纵横比推导。
   * LaTeX 非法（空/超长）返回 null。
   */
  fun createItem(
    assetName: String,
    pixelWidth: Int,
    pixelHeight: Int,
    anchorX: Double,
    anchorY: Double,
    latex: String,
    color: String,
    width: Double,
    displayWidthUnits: Double = DISPLAY_WIDTH_UNITS,
  ): JsonObject? {
    if (!ASSET_NAME.matches(assetName)) return null
    if (pixelWidth <= 0 || pixelHeight <= 0) return null
    if (!anchorX.isFinite() || !anchorY.isFinite() ||
      anchorX < -MAX_COORDINATE || anchorY < -MAX_COORDINATE
    ) {
      return null
    }
    val formula = FormulaAnnotation.create(listOf(latex), color) ?: return null
    val displayWidth = displayWidthUnits.coerceIn(1.0, 1000.0)
    val height = displayWidth * pixelHeight / pixelWidth
    val bottomY = anchorY + height
    if (!height.isFinite() || height <= 0.0 || !bottomY.isFinite() || bottomY > MAX_COORDINATE) {
      return null
    }
    val base = buildJsonObject {
      put("type", JsonPrimitive("image"))
      put("color", JsonPrimitive(color))
      put("width", JsonPrimitive(width))
      put("url", JsonPrimitive("/attachments/$assetName"))
      put(
        "points",
        JsonArray(
          listOf(
            JsonArray(listOf(JsonPrimitive(anchorX), JsonPrimitive(anchorY))),
            JsonArray(listOf(JsonPrimitive(anchorX + displayWidth), JsonPrimitive(bottomY))),
          ),
        ),
      )
    }
    return FormulaAnnotation.mergeIntoItem(base, formula)
  }

  /** 素材库条目：名称取 LaTeX 前 40 码点；items 为单 image 项。 */
  fun materialEntry(
    item: JsonObject,
    createdAtMillis: Long,
    namePrefix: String,
  ): CanvasMaterialEntry? {
    val latex = FormulaAnnotation.fromItem(item)?.latexLines?.firstOrNull() ?: return null
    val name = buildString {
      append(namePrefix)
      append(' ')
      val trimmed = latex.trim()
      val end = trimmed.codePointCount(0, trimmed.length).coerceAtMost(40)
      append(trimmed.substring(0, trimmed.offsetByCodePoints(0, end)))
    }
    return runCatching {
      CanvasMaterialCodec.newEntry(
        name = name,
        kind = CanvasMaterialKind.FORMULA,
        createdAtMillis = createdAtMillis,
        items = JsonArray(listOf(item)),
      )
    }.getOrNull()
  }
}

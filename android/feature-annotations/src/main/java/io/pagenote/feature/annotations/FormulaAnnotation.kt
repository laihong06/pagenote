package io.pagenote.feature.annotations

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * 公式素材的源数据 schema（移植批次 5，决策 D7）。对齐 touchnotes
 * `FormulaMaterial extends Image`：公式在画布上是一张渲染位图（普通 image 项 +
 * 附件），`latex` 扩展字段保留可重渲染的 LaTeX 源（`Image.mLatexTexts`），
 * `latexColor` 保留位图之外的颜色语义（`Image.latexColor`，`#RRGGBB` 字符串化）。
 *
 * - `latex`（字符串数组，1..[MAX_LINES] 行，每行 1..[MAX_LINE_LENGTH] 字符）：
 *   多行公式按行渲染、垂直堆叠（渲染布局见 app 侧 FormulaMaterialLayout）。
 * - `latexColor`（字符串 `#rrggbb`，可选）：缺省沿用 canonical `color`。
 *
 * 读侧容错（D7）：非法行（空/超长/非字符串）被丢弃，全部非法则视为无公式源；
 * 非法颜色降级为 canonical `color`。读侧永不因 latex 字段拒绝 item。
 */
data class FormulaAnnotation(
  val latexLines: List<String>,
  /** 渲染颜色（`#RRGGBB`）；始终有值——缺省取 item 的 canonical color。 */
  val latexColor: String,
) {
  companion object {
    const val LATEX_FIELD = "latex"
    const val LATEX_COLOR_FIELD = "latexColor"
    const val MAX_LINES = 8
    const val MAX_LINE_LENGTH = 2000

    private val COLOR = Regex("^#[a-fA-F0-9]{6}$")

    /** 写侧严格：行数/行长/颜色非法返回 null。 */
    fun create(latexLines: List<String>, latexColor: String?): FormulaAnnotation? {
      val lines = latexLines.mapNotNull { line -> line.takeIf { it.isNotBlank() && it.length <= MAX_LINE_LENGTH } }
      if (lines.isEmpty() || lines.size > MAX_LINES) return null
      if (latexColor != null && !COLOR.matches(latexColor)) return null
      return FormulaAnnotation(
        latexLines = lines,
        latexColor = (latexColor ?: "#000000").lowercase(),
      )
    }

    /**
     * 读侧容错：从 image item 提取公式源。无 `latex` 字段或全部行非法返回 null
     * （普通图片/贴纸项）；`latexColor` 非法时降级为 canonical `color`。
     */
    fun fromItem(item: JsonObject): FormulaAnnotation? {
      if (item["type"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() } != "image") return null
      val rawLines = runCatching { item[LATEX_FIELD]?.jsonArray }.getOrNull() ?: return null
      val lines = rawLines.mapNotNull { element ->
        runCatching { element.jsonPrimitive.content }.getOrNull()
          ?.takeIf { it.isNotBlank() && it.length <= MAX_LINE_LENGTH }
      }
      if (lines.isEmpty() || lines.size > MAX_LINES) return null
      val canonicalColor = item["color"]
        ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.takeIf { COLOR.matches(it) }
      val declared = item[LATEX_COLOR_FIELD]
        ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.takeIf { COLOR.matches(it) }
      return FormulaAnnotation(
        latexLines = lines,
        latexColor = (declared ?: canonicalColor ?: "#000000").lowercase(),
      )
    }

    /** 把公式扩展字段合并进 item（保留其余字段；数组走 D2 透传通道）。 */
    fun mergeIntoItem(item: JsonObject, formula: FormulaAnnotation?): JsonObject {
      val rebuilt = item.toMutableMap()
      rebuilt.remove(LATEX_FIELD)
      rebuilt.remove(LATEX_COLOR_FIELD)
      if (formula != null) {
        rebuilt[LATEX_FIELD] = buildJsonArray { formula.latexLines.forEach { add(JsonPrimitive(it)) } }
        rebuilt[LATEX_COLOR_FIELD] = JsonPrimitive(formula.latexColor)
      }
      return JsonObject(rebuilt)
    }
  }
}

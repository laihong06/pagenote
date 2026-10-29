package io.pagenote.feature.annotations

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 公式素材扩展字段（latex / latexColor，D7）测试。 */
class FormulaAnnotationTest {
  private fun latexArray(vararg lines: String): kotlinx.serialization.json.JsonArray =
    kotlinx.serialization.json.JsonArray(lines.map { kotlinx.serialization.json.JsonPrimitive(it) })

  private fun imageItem(
    latex: kotlinx.serialization.json.JsonArray? = null,
    latexColor: String? = null,
    color: String = "#185abd",
  ) =
    buildJsonObject {
      put("type", "image")
      put("color", color)
      put("width", 1.0)
      put("url", "/attachments/0123abcd-0000-4000-8000-000000000000.png")
      put("points", buildJsonArray {
        add(buildJsonArray { add(0.0); add(0.0) })
        add(buildJsonArray { add(60.0); add(40.0) })
      })
      latex?.let { put("latex", it) }
      latexColor?.let { put("latexColor", it) }
    }

  @Test
  fun createValidatesLinesAndColor() {
    val formula = FormulaAnnotation.create(listOf("E=mc^2", "\\int_0^1 x dx"), "#FF0000")!!
    assertEquals(listOf("E=mc^2", "\\int_0^1 x dx"), formula.latexLines)
    assertEquals("#ff0000", formula.latexColor)

    assertNull(FormulaAnnotation.create(emptyList(), null))
    assertNull(FormulaAnnotation.create(listOf("  "), null))
    assertNull(FormulaAnnotation.create(List(9) { "x" }, null))
    assertNull(FormulaAnnotation.create(listOf("x".repeat(2001)), null))
    assertNull(FormulaAnnotation.create(listOf("x"), "#12345"))
  }

  @Test
  fun fromItemReadsLatexArrayAndColorFallback() {
    val parsed = FormulaAnnotation.fromItem(
      imageItem(latex = latexArray("E=mc^2", "a+b"), latexColor = "#00ff00"),
    )!!
    assertEquals(listOf("E=mc^2", "a+b"), parsed.latexLines)
    assertEquals("#00ff00", parsed.latexColor)

    // latexColor 非法 → 降级 canonical color。
    val fallback = FormulaAnnotation.fromItem(imageItem(latex = latexArray("x"), latexColor = "green"))!!
    assertEquals("#185abd", fallback.latexColor)

    // 无 latex 字段 → 非公式项。
    assertNull(FormulaAnnotation.fromItem(imageItem()))
    // 非 image 项 → null。
    assertNull(
      FormulaAnnotation.fromItem(
        buildJsonObject {
          put("type", "text")
          put("color", "#000000")
          put("width", 1.0)
          put("text", "x")
        },
      ),
    )
  }

  @Test
  fun fromItemDropsInvalidLines() {
    // 一行合法、一行空 → 保留合法行。
    val parsed = FormulaAnnotation.fromItem(imageItem(latex = latexArray("x", "   ")))!!
    assertEquals(listOf("x"), parsed.latexLines)
    // 全部非法 → 视为无公式源。
    assertNull(FormulaAnnotation.fromItem(imageItem(latex = latexArray("", "  "))))
  }

  @Test
  fun mergeIntoItemWritesAndClearsFields() {
    val base = imageItem()
    val formula = FormulaAnnotation.create(listOf("E=mc^2"), "#123456")!!
    val merged = FormulaAnnotation.mergeIntoItem(base, formula)
    assertTrue(merged.containsKey("latex"))
    assertEquals("#123456", (merged[FormulaAnnotation.LATEX_COLOR_FIELD] as JsonPrimitive).content)
    // canonical 保留。
    assertEquals("image", (merged["type"] as JsonPrimitive).content)

    val cleared = FormulaAnnotation.mergeIntoItem(merged, null)
    assertFalse(cleared.containsKey("latex"))
    assertFalse(cleared.containsKey("latexColor"))
    // 数组值经 JSON 往返保真（D2）。
    val roundTrip = FormulaAnnotation.fromItem(merged)!!
    assertEquals(listOf("E=mc^2"), roundTrip.latexLines)
    assertEquals("#123456", roundTrip.latexColor)
  }

  @Test
  fun mergedItemStillPassesAnnotationValidation() {
    val formula = FormulaAnnotation.create(listOf("\\frac{a}{b}"), "#000000")!!
    val merged = FormulaAnnotation.mergeIntoItem(imageItem(), formula)
    AnnotationValidation.validateItems(JsonArray(listOf(merged)))
  }
}

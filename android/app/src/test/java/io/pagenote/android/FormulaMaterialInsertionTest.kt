package io.pagenote.android

import io.pagenote.feature.annotations.CanvasMaterialKind
import io.pagenote.feature.annotations.FormulaAnnotation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormulaMaterialInsertionTest {
  private val asset = "0123abcd-2345-6789-abcd-ef0123456789.png"

  @Test
  fun createItemBuildsImageItemWithLatexExtensionFields() {
    val item = FormulaMaterialInsertion.createItem(
      assetName = asset,
      pixelWidth = 400,
      pixelHeight = 100,
      anchorX = 80.0,
      anchorY = 120.0,
      latex = "E=mc^2",
      color = "#123456",
      width = 2.0,
    )
    assertNotNull(item)
    assertEquals("image", item!!["type"]!!.jsonPrimitive.content)
    assertEquals("/attachments/$asset", item["url"]!!.jsonPrimitive.content)
    val points = item["points"]!!.jsonArray
    val topLeft = points[0].jsonArray
    val bottomRight = points[1].jsonArray
    assertEquals(80.0, topLeft[0].jsonPrimitive.content.toDouble(), 1e-9)
    assertEquals(120.0, topLeft[1].jsonPrimitive.content.toDouble(), 1e-9)
    // 显示宽 350，高度按位图纵横比 400x100 → 87.5。
    assertEquals(430.0, bottomRight[0].jsonPrimitive.content.toDouble(), 1e-9)
    assertEquals(207.5, bottomRight[1].jsonPrimitive.content.toDouble(), 1e-9)
    // D7 公式扩展字段随 item 落盘。
    assertEquals(listOf("E=mc^2"), item[FormulaAnnotation.LATEX_FIELD]!!.jsonArray.map { it.jsonPrimitive.content })
    assertEquals("#123456", item[FormulaAnnotation.LATEX_COLOR_FIELD]!!.jsonPrimitive.content)
  }

  @Test
  fun createItemRejectsInvalidGeometryOrLatex() {
    assertNull(
      FormulaMaterialInsertion.createItem("not-an-asset.png", 10, 10, 0.0, 0.0, "x", "#123456", 2.0),
    )
    assertNull(
      FormulaMaterialInsertion.createItem(asset, 0, 10, 0.0, 0.0, "x", "#123456", 2.0),
    )
    assertNull(
      FormulaMaterialInsertion.createItem(asset, 10, 10, 0.0, 0.0, "  ", "#123456", 2.0),
    )
  }

  @Test
  fun materialEntryWrapsTheItemAsAFormulaMaterial() {
    val item = FormulaMaterialInsertion.createItem(
      assetName = asset,
      pixelWidth = 100,
      pixelHeight = 40,
      anchorX = 0.0,
      anchorY = 0.0,
      latex = "\\frac{a}{b}+c",
      color = "#000000",
      width = 2.0,
    )!!
    val entry = FormulaMaterialInsertion.materialEntry(item, createdAtMillis = 123L, namePrefix = "公式")
    assertNotNull(entry)
    assertEquals(CanvasMaterialKind.FORMULA, entry!!.kind)
    assertEquals(123L, entry.createdAtMillis)
    assertTrue(entry.name.startsWith("公式 \\frac{a}{b}"))
    assertEquals(1, entry.items.size)
    assertEquals(item, entry.items[0])
  }

  @Test
  fun materialEntryReturnsNullForNonFormulaItem() {
    val plain = kotlinx.serialization.json.buildJsonObject {
      put("type", kotlinx.serialization.json.JsonPrimitive("image"))
      put("url", kotlinx.serialization.json.JsonPrimitive("/attachments/$asset"))
    }
    assertNull(FormulaMaterialInsertion.materialEntry(plain, 0L, "公式"))
  }
}

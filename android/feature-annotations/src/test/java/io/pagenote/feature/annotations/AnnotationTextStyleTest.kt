package io.pagenote.feature.annotations

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port batch 4: text style schema (font / font size / alignment / color). */
class AnnotationTextStyleTest {
  private val json = Json

  private fun page(vararg raw: String): JsonArray = JsonArray(raw.map { json.parseToJsonElement(it).jsonObject })

  @Test
  fun derivedFontSizeMatchesTheDesktopDerivation() {
    // Desktop src/annotations.js:178 — Math.max(20, item.width * 5).
    assertEquals(20.0, AnnotationTextStyle.derivedFontSize(0.1), 0.0)
    assertEquals(20.0, AnnotationTextStyle.derivedFontSize(3.0), 0.0)
    assertEquals(25.0, AnnotationTextStyle.derivedFontSize(5.0), 0.0)
    assertEquals(500.0, AnnotationTextStyle.derivedFontSize(100.0), 0.0)
    assertEquals(20.0, AnnotationTextStyle.effectiveFontSize(3.0, null), 0.0)
    assertEquals(64.0, AnnotationTextStyle.effectiveFontSize(3.0, 64.0), 0.0)
  }

  @Test
  fun createValidatesEveryField() {
    val style = AnnotationTextStyle.create(
      color = "#DC3545",
      fontId = "serif",
      fontSize = 42.0,
      align = AnnotationTextAlign.CENTER,
    )
    assertEquals("#dc3545", style.color)
    assertEquals("serif", style.fontId)
    assertEquals(42.0, style.fontSize!!, 0.0)
    assertEquals(AnnotationTextAlign.CENTER, style.align)

    assertFalse(runCatching { AnnotationTextStyle.create("red") }.isSuccess)
    assertFalse(runCatching { AnnotationTextStyle.create("#123456", fontId = "comic-sans") }.isSuccess)
    assertFalse(runCatching { AnnotationTextStyle.create("#123456", fontSize = 19.9) }.isSuccess)
    assertFalse(runCatching { AnnotationTextStyle.create("#123456", fontSize = 500.1) }.isSuccess)
    assertFalse(runCatching { AnnotationTextStyle.create("#123456", fontSize = Double.NaN) }.isSuccess)
    // Null font/size stay legal (absent fields).
    assertTrue(runCatching { AnnotationTextStyle.create("#123456") }.isSuccess)
  }

  @Test
  fun forNewItemWritesTheTextToolDefaultSchema() {
    val style = AnnotationTextStyle.forNewItem("#185abd", penWidth = 6.0)
    assertEquals(TextFontCatalog.DEFAULT_FONT_ID, style.fontId)
    assertEquals(30.0, style.fontSize!!, 0.0)
    assertEquals(AnnotationTextAlign.LEFT, style.align)
    assertEquals("#185abd", style.color)
    assertEquals(
      mapOf("font" to "default", "fontSize" to 30.0, "align" to "left"),
      style.extensionFields(),
    )
  }

  @Test
  fun extensionFieldsOmitAbsentValues() {
    val style = AnnotationTextStyle.create("#123456", fontId = null, fontSize = null)
    assertEquals(mapOf<String, Any?>("align" to "left"), style.extensionFields())
  }

  @Test
  fun fromItemReadsTolerantlyAndDegradesUnknownValues() {
    val styled = json.parseToJsonElement(
      """{"type":"text","color":"#123456","width":6.0,"points":[[10,20]],""" +
        """"text":"hi","font":"mono","fontSize":40.0,"align":"right"}""",
    ).let { it as JsonObject }
    val style = AnnotationTextStyle.fromItem(styled)!!
    assertEquals("mono", style.fontId)
    assertEquals(40.0, style.fontSize!!, 0.0)
    assertEquals(AnnotationTextAlign.RIGHT, style.align)

    // Unknown font / bad size / unknown align degrade; size falls back to derived.
    val tolerant = json.parseToJsonElement(
      """{"type":"text","color":"#123456","width":10.0,"points":[[10,20]],""" +
        """"text":"hi","font":"zapfino","fontSize":9999.0,"align":"justify"}""",
    ).let { it as JsonObject }
    val degraded = AnnotationTextStyle.fromItem(tolerant)!!
    assertNull(degraded.fontId)
    assertEquals(50.0, degraded.fontSize!!, 0.0)
    assertEquals(AnnotationTextAlign.LEFT, degraded.align)

    // Legacy desktop item without any extension fields.
    val legacy = json.parseToJsonElement(
      """{"type":"text","color":"#123456","width":2.0,"points":[[10,20]],"text":"hi"}""",
    ).let { it as JsonObject }
    val legacyStyle = AnnotationTextStyle.fromItem(legacy)!!
    assertNull(legacyStyle.fontId)
    assertEquals(20.0, legacyStyle.fontSize!!, 0.0)
    assertEquals(AnnotationTextAlign.LEFT, legacyStyle.align)
  }

  @Test
  fun fromItemRejectsNonTextAndInvalidItems() {
    assertNull(
      AnnotationTextStyle.fromItem(
        json.parseToJsonElement(
          """{"type":"rect","color":"#123456","width":2.0,"points":[[10,20],[30,40]]}""",
        ).let { it as JsonObject },
      ),
    )
    assertNull(
      AnnotationTextStyle.fromItem(
        json.parseToJsonElement("""{"type":"text","color":"red","width":2.0,"points":[[10,20]],"text":"hi"}""").let { it as JsonObject },
      ),
    )
  }

  @Test
  fun updateTextStyleRewritesOnlyChangedFieldsAndPreservesEverythingElse() {
    val page = page(
      """{"type":"pen","color":"#111111","width":2.0,"points":[[1,1],[2,2]]}""",
      """{"type":"text","color":"#123456","width":6.0,"points":[[10,20]],"text":"hi",""" +
        """"font":"serif","fontSize":30.0,"align":"left","pressures":[1.0],"custom":"keep"}""",
    )

    val updated = AnnotationTextStyleEditor.updateTextStyle(
      page = page,
      index = 1,
      color = "#ABCDEF",
      fontId = "mono",
      fontSize = 42.0,
      align = AnnotationTextAlign.CENTER,
    )!!

    val item = updated[1].let { it as JsonObject }
    assertEquals("#abcdef", item["color"]!!.jsonPrimitive.content)
    assertEquals("mono", item["font"]!!.jsonPrimitive.content)
    assertEquals(42.0, item["fontSize"]!!.jsonPrimitive.content.toDouble(), 0.0)
    assertEquals("center", item["align"]!!.jsonPrimitive.content)
    // Unrelated fields survive, including passthrough extensions.
    assertEquals("keep", item["custom"]!!.jsonPrimitive.content)
    assertEquals(1, (item["pressures"] as JsonArray).size)
    // The sibling item is untouched.
    assertEquals(page[0], updated[0])
  }

  @Test
  fun updateTextStyleIsANoopForEffectiveEquivalents() {
    // No explicit fields: requesting the defaults (default font, derived size,
    // left align, same color) must not write any extension field.
    val page = page(
      """{"type":"text","color":"#123456","width":4.0,"points":[[10,20]],"text":"hi"}""",
    )
    val updated = AnnotationTextStyleEditor.updateTextStyle(
      page = page,
      index = 0,
      color = "#123456",
      fontId = TextFontCatalog.DEFAULT_FONT_ID,
      fontSize = 20.0,
      align = AnnotationTextAlign.LEFT,
    )
    assertTrue(updated === page)
  }

  @Test
  fun updateTextStyleRejectsInvalidTargetsAndValues() {
    val textPage = page(
      """{"type":"text","color":"#123456","width":4.0,"points":[[10,20]],"text":"hi"}""",
      """{"type":"rect","color":"#123456","width":4.0,"points":[[10,20],[30,40]]}""",
    )
    // Out of bounds / non-text targets.
    assertNull(AnnotationTextStyleEditor.updateTextStyle(textPage, 9, color = "#ffffff"))
    assertNull(AnnotationTextStyleEditor.updateTextStyle(textPage, 1, color = "#ffffff"))
    // Invalid values.
    assertNull(AnnotationTextStyleEditor.updateTextStyle(textPage, 0, color = "white"))
    assertNull(AnnotationTextStyleEditor.updateTextStyle(textPage, 0, fontId = "unknown"))
    assertNull(AnnotationTextStyleEditor.updateTextStyle(textPage, 0, fontSize = 1000.0))
    // Null changes keep the page.
    assertTrue(AnnotationTextStyleEditor.updateTextStyle(textPage, 0) === textPage)
  }

  @Test
  fun updateTextStyleDowngradesExplicitSizeOnlyWhenDifferent() {
    // Explicit size equal to the requested value is a no-op even though the
    // field exists; a different value rewrites it.
    val page = page(
      """{"type":"text","color":"#123456","width":6.0,"points":[[10,20]],"text":"hi","fontSize":30.0}""",
    )
    assertTrue(AnnotationTextStyleEditor.updateTextStyle(page, 0, fontSize = 30.0) === page)
    val updated = AnnotationTextStyleEditor.updateTextStyle(page, 0, fontSize = 33.0)!!
    assertEquals(33.0, (updated[0] as JsonObject)["fontSize"]!!.jsonPrimitive.content.toDouble(), 0.0)
  }
}

package io.pagenote.android

import io.pagenote.feature.annotations.AnnotationTextStyle
import io.pagenote.feature.annotations.AnnotationValidation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationTextDraftTest {
  @Test
  fun buildsTextItem() {
    val item = AnnotationTextDraft.toItem(10.0, 20.0, "#FF0000", 2.0, "hello")

    assertEquals("text", item["type"])
    assertEquals("#FF0000", item["color"])
    assertEquals(2.0, item["width"])
    assertEquals(listOf(listOf(10.0, 20.0)), item["points"])
    assertEquals("hello", item["text"])
  }

  @Test
  fun defaultCallKeepsTheLegacyItemWithoutStyleFields() {
    val item = AnnotationTextDraft.toItem(10.0, 20.0, "#FF0000", 2.0, "hello")

    assertFalse(item.containsKey("font"))
    assertFalse(item.containsKey("fontSize"))
    assertFalse(item.containsKey("align"))
  }

  @Test
  fun styleWritesTheFontFontSizeAlignExtensionFields() {
    val style = AnnotationTextStyle.forNewItem("#185abd", 6.0)
    val item = AnnotationTextDraft.toItem(10.0, 20.0, "#185abd", 6.0, "hello", style)

    assertEquals("default", item["font"])
    assertEquals(30.0, item["fontSize"])
    assertEquals("left", item["align"])
    // Canonical fields are unchanged by the style.
    assertEquals("text", item["type"])
    assertEquals("#185abd", item["color"])
    assertEquals(6.0, item["width"])
    assertEquals(listOf(listOf(10.0, 20.0)), item["points"])
    assertEquals("hello", item["text"])
  }

  @Test
  fun styleFieldOrderKeepsCanonicalFieldsFirst() {
    val style = AnnotationTextStyle.forNewItem("#185abd", 6.0)
    val item = AnnotationTextDraft.toItem(10.0, 20.0, "#185abd", 6.0, "hello", style)

    assertEquals(
      listOf("type", "color", "width", "points", "text", "font", "fontSize", "align"),
      item.keys.toList(),
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsBlankText() {
    AnnotationTextDraft.toItem(10.0, 20.0, "#FF0000", 2.0, "  ")
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsBadColor() {
    AnnotationTextDraft.toItem(10.0, 20.0, "red", 2.0, "hi")
  }

  @Test
  fun truncatesLongText() {
    val item = AnnotationTextDraft.toItem(0.0, 0.0, "#000000", 1.0, "x".repeat(12000))

    assertEquals(10000, (item["text"] as String).length)
  }

  @Test
  fun styledItemsPassStoreLevelValidation() {
    val style = AnnotationTextStyle.forNewItem("#185abd", 6.0)
    val item = AnnotationTextDraft.toItem(10.0, 20.0, "#185abd", 6.0, "hello", style)

    // The extension fields ride along through annotations.json validation
    // exactly like the passthrough extensions of pen items.
    val element = JsonObject(
      item.mapValues { (_, value) ->
        when (value) {
          is String -> JsonPrimitive(value)
          is Double -> JsonPrimitive(value)
          is List<*> -> JsonArray(value.map { point ->
            JsonArray((point as List<*>).map { axis -> JsonPrimitive(axis as Double) })
          })
          else -> throw IllegalArgumentException("unexpected value: $value")
        }
      },
    )
    assertTrue(
      runCatching {
        AnnotationValidation.validateItems(JsonArray(listOf(element)))
      }.isSuccess,
    )
  }
}

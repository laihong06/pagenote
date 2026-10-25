package io.pagenote.android

import io.pagenote.feature.pen.PenActionRegistry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationOcrConvertActionTest {
  private fun pageOf(vararg texts: String): JsonArray = JsonArray(
    texts.map { text ->
      buildJsonObject {
        put("type", "text")
        put("text", text)
      }
    },
  )

  @Test
  fun registryExposesTheConvertAction() {
    // 构造动作以触发 companion 的注册（const id 会被内联，不触发类初始化）。
    val action = AnnotationOcrConvertAction.create(
      "p", 1, pageOf("a"), pageOf("a", "b"),
    ) {}
    assertTrue(PenActionRegistry.isRegistered(action!!.actionId))
    assertEquals("pdf.ocrConvert", AnnotationOcrConvertAction.ID)
  }

  @Test
  fun createReturnsNullForNoOpConversion() {
    val page = pageOf("a")
    assertNull(
      AnnotationOcrConvertAction.create("p", 1, page, page) {},
    )
  }

  @Test
  fun doUndoRedoReplayPageSnapshots() {
    val before = pageOf("ink1", "ink2")
    val after = pageOf("transcribed")
    val applied = mutableListOf<JsonArray>()
    val action = AnnotationOcrConvertAction.create("project-1", 3, before, after) { page ->
      applied.add(page)
    }

    action!!.doAction()
    action.undo()
    action.redo()

    assertEquals(listOf(after, before, after), applied)
    assertEquals("pdf:project-1:3", action.scope)
    assertEquals("ink OCR convert", action.label)
  }

  @Test
  fun consecutiveConversionsNeverCoalesce() {
    val before = pageOf("ink")
    val after = pageOf("text")
    val first = AnnotationOcrConvertAction.create("p", 1, before, after) {}
    val second = AnnotationOcrConvertAction.create("p", 1, after, before) {}
    assertNull(first!!.merge(second!!))
  }
}

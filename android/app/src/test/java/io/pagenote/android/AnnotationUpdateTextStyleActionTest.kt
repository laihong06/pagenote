package io.pagenote.android

import io.pagenote.feature.annotations.AnnotationTextAlign
import io.pagenote.feature.pen.PenActionRegistry
import io.pagenote.feature.pen.PenActionStack
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

/** Port batch 4: UpdateTextStyle as a unified-stack undo action. */
class AnnotationUpdateTextStyleActionTest {
  private val json = Json

  private fun page(vararg raw: String): JsonArray =
    JsonArray(raw.map { json.parseToJsonElement(it).jsonObject })

  private fun textItem(color: String = "#123456", font: String? = null, fontSize: Double? = null): String {
    val extras = buildString {
      font?.let { append(""""font":"$it",""") }
      fontSize?.let { append(""""fontSize":$it,""") }
    }
    return """{"type":"text","color":"$color","width":6.0,"points":[[10,20]],"text":"hi",$extras"custom":"keep"}"""
  }

  @Test
  fun executesUpdatesAndUndoesThroughTheUnifiedStack() {
    val before = page(textItem())
    val applied = mutableListOf<JsonArray>()
    val stack = PenActionStack()

    val action = AnnotationUpdateTextStyleAction.create(
      projectId = "project-1",
      page = 3,
      items = before,
      index = 0,
      color = "#abcdef",
      fontId = "serif",
      fontSize = 42.0,
      align = AnnotationTextAlign.CENTER,
      apply = { applied.add(it) },
    )!!
    assertTrue(PenActionRegistry.isRegistered(action.actionId))
    assertEquals("pdf.annotationTextStyle", action.actionId)
    assertEquals("pdf:project-1:3", action.scope)

    stack.execute(action)
    assertEquals(1, applied.size)

    val updatedItem = applied.last()[0] as JsonObject
    assertEquals("#abcdef", updatedItem["color"]!!.jsonPrimitive.content)
    assertEquals("serif", updatedItem["font"]!!.jsonPrimitive.content)
    assertEquals("keep", updatedItem["custom"]!!.jsonPrimitive.content)

    assertTrue(stack.undo("pdf:project-1:3"))
    assertEquals(before, applied.last())
    assertTrue(stack.redo("pdf:project-1:3"))
    assertEquals(3, applied.size)
  }

  @Test
  fun createWiresApplyToPersistence() {
    val before = page(textItem())
    val applied = mutableListOf<JsonArray>()
    val action = AnnotationUpdateTextStyleAction.create(
      projectId = "p",
      page = 1,
      items = before,
      index = 0,
      fontSize = 64.0,
      apply = { applied.add(it) },
    )!!

    action.doAction()
    assertEquals(1, applied.size)
    action.undo()
    assertEquals(before, applied.last())
    action.redo()
    val item = applied.last()[0] as JsonObject
    assertEquals(64.0, item["fontSize"]!!.jsonPrimitive.content.toDouble(), 0.0)
  }

  @Test
  fun consecutiveStyleEditsCoalesceIntoOneUndoStep() {
    val initial = page(textItem())
    val applied = mutableListOf<JsonArray>()
    val stack = PenActionStack()

    val first = AnnotationUpdateTextStyleAction.create(
      "p", 2, initial, 0, fontSize = 40.0,
    ) { applied.add(it) }!!
    stack.execute(first)
    val afterFirst = applied.last()

    val second = AnnotationUpdateTextStyleAction.create(
      "p", 2, afterFirst, 0, fontSize = 50.0,
    ) { applied.add(it) }!!
    stack.execute(second)
    val afterSecond = applied.last()

    // One undo restores the FIRST before, skipping the intermediate state.
    assertTrue(stack.undo("pdf:p:2"))
    assertEquals(initial, applied.last())
    assertTrue(stack.redo("pdf:p:2"))
    assertEquals(afterSecond, applied.last())
    // The merged pair stays one undo step: undoing again returns straight to
    // the initial page and then the undo scope is exhausted.
    assertTrue(stack.undo("pdf:p:2"))
    assertEquals(initial, applied.last())
    assertFalse(stack.canUndo("pdf:p:2"))
    // Only the merged action is redoable — the intermediate edit state never
    // appears as its own undo/redo step.
    assertTrue(stack.canRedo("pdf:p:2"))
  }

  @Test
  fun styleEditsOnDifferentScopesNeverMerge() {
    val pageOne = page(textItem())
    val pageTwo = page(textItem())
    val appliedOne = mutableListOf<JsonArray>()
    val appliedTwo = mutableListOf<JsonArray>()
    val stack = PenActionStack()

    stack.execute(
      AnnotationUpdateTextStyleAction.create("p", 1, pageOne, 0, fontSize = 40.0) { appliedOne.add(it) }!!,
    )
    stack.execute(
      AnnotationUpdateTextStyleAction.create("p", 2, pageTwo, 0, fontSize = 40.0) { appliedTwo.add(it) }!!,
    )

    assertTrue(stack.undo("pdf:p:1"))
    assertEquals(pageOne, appliedOne.last())
    assertTrue(stack.canUndo("pdf:p:2"))
    assertFalse(stack.canRedo("pdf:p:2"))
  }

  @Test
  fun builderRejectsInvalidTargetsAndNoOpEdits() {
    val rectOnly = page("""{"type":"rect","color":"#123456","width":4.0,"points":[[10,20],[30,40]]}""")
    assertNull(AnnotationUpdateTextStyleAction.create("p", 1, rectOnly, 0, fontSize = 40.0) {})
    assertNull(AnnotationUpdateTextStyleAction.create("p", 1, rectOnly, 9, fontSize = 40.0) {})
    assertNull(
      AnnotationUpdateTextStyleAction.create("p", 1, page(textItem()), 0, fontId = "unknown") {},
    )
    // Effective no-op (same values) records nothing.
    assertNull(
      AnnotationUpdateTextStyleAction.create("p", 1, page(textItem()), 0, color = "#123456") {},
    )
  }
}

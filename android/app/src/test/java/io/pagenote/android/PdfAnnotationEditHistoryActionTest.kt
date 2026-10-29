package io.pagenote.android

import io.pagenote.feature.pen.PenActionRegistry
import io.pagenote.feature.pen.PenActionStack
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port batch 1: PdfAnnotationEditHistory snapshots as unified PenActions. */
class PdfAnnotationEditHistoryActionTest {
  private val json = Json

  private fun items(raw: String): JsonArray = json.parseToJsonElement(raw).jsonArray

  @Test
  fun adaptsSnapshotRecordingToTheUnifiedStack() {
    val before = items("""[{"type":"line","color":"#ff0000","width":2.0,"points":[[1,1],[9,9]]}]""")
    val after = items("[]")
    val applied = mutableListOf<JsonArray>()
    val stack = PenActionStack()

    val action = PdfAnnotationEditHistoryAction("project-1", 3, before, after) { applied.add(it) }
    assertTrue(PenActionRegistry.isRegistered(action.actionId))
    stack.execute(action)

    assertEquals(listOf(after), applied)
    assertTrue(stack.undo("pdf:project-1:3"))
    assertEquals(listOf(after, before), applied)
    assertTrue(stack.redo("pdf:project-1:3"))
    assertEquals(listOf(after, before, after), applied)
    // Redo re-enables undo and consumes the redo entry.
    assertTrue(stack.canUndo("pdf:project-1:3"))
    assertFalse(stack.canRedo("pdf:project-1:3"))
  }

  @Test
  fun snapshotActionsNeverMergeAndStayScoped() {
    val appliedA = mutableListOf<JsonArray>()
    val appliedB = mutableListOf<JsonArray>()
    val stack = PenActionStack()
    val empty = items("[]")
    val one = items("""[{"type":"rect","color":"#ff0000","width":2.0,"points":[[1,1],[9,9]]}]""")

    stack.execute(PdfAnnotationEditHistoryAction("p", 1, empty, one) { appliedA.add(it) })
    stack.execute(PdfAnnotationEditHistoryAction("p", 1, one, empty) { appliedA.add(it) })
    stack.execute(PdfAnnotationEditHistoryAction("p", 2, empty, one) { appliedB.add(it) })

    // Two same-scope snapshot edits stay separate undo steps (never merged),
    // and undoing scope pdf:p:1 leaves page 2 untouched.
    assertTrue(stack.undo("pdf:p:1"))
    assertEquals(listOf(one, empty, one), appliedA)
    assertEquals(listOf(one), appliedB)
    assertTrue(stack.undo("pdf:p:1"))
    assertEquals(listOf(one, empty, one, empty), appliedA)
    assertFalse(stack.undo("pdf:p:1"))
    assertTrue(stack.canUndo("pdf:p:2"))
  }
}

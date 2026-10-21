package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditHistoryTest {
  @Test
  fun startsWithEmptyUndoAndRedoStacks() {
    val history = MarkdownEditHistory("initial")

    assertFalse(history.canUndo)
    assertFalse(history.canRedo)
    assertNull(history.undo("initial"))
    assertNull(history.redo("initial"))
  }

  @Test
  fun undoAndRedoRoundTripTheCurrentMarkdown() {
    val history = MarkdownEditHistory("a")
    history.record(before = "a", after = "ab", timestampMillis = 10)

    assertTrue(history.canUndo)
    assertFalse(history.canRedo)
    assertEquals("a", history.undo("ab"))
    assertFalse(history.canUndo)
    assertTrue(history.canRedo)
    assertEquals("ab", history.redo("a"))
    assertTrue(history.canUndo)
    assertFalse(history.canRedo)
  }

  @Test
  fun editsAtExactlyFiveHundredMillisecondsShareOneUndoStep() {
    val history = MarkdownEditHistory("")
    history.record(before = "", after = "a", timestampMillis = 1_000)
    history.record(before = "a", after = "ab", timestampMillis = 1_500)

    assertEquals("", history.undo("ab"))
  }

  @Test
  fun editsAfterFiveHundredMillisecondsCreateSeparateUndoSteps() {
    val history = MarkdownEditHistory("")
    history.record(before = "", after = "a", timestampMillis = 1_000)
    history.record(before = "a", after = "ab", timestampMillis = 1_501)

    assertEquals("a", history.undo("ab"))
    assertEquals("", history.undo("a"))
  }

  @Test
  fun newEditAfterUndoClearsRedoAndStartsASeparateStep() {
    val history = MarkdownEditHistory("")
    history.record(before = "", after = "a", timestampMillis = 1_000)
    history.record(before = "a", after = "ab", timestampMillis = 2_000)
    assertEquals("a", history.undo("ab"))

    history.record(before = "a", after = "ac", timestampMillis = 2_001)

    assertFalse(history.canRedo)
    assertEquals("a", history.undo("ac"))
    assertEquals("ac", history.redo("a"))
  }

  @Test
  fun retainsAtMostEightyUndoSnapshots() {
    val history = MarkdownEditHistory("0")
    var current = "0"
    repeat(81) { index ->
      val updated = "$current x"
      history.record(before = current, after = updated, timestampMillis = index * 501L)
      current = updated
    }

    repeat(80) { current = requireNotNull(history.undo(current)) }
    assertEquals("0 x", current)
    assertNull(history.undo(current))
  }

  @Test
  fun resetClearsBothStacksAndSetsTheNewCurrentText() {
    val history = MarkdownEditHistory("a")
    history.record(before = "a", after = "ab", timestampMillis = 1_000)
    assertEquals("a", history.undo("ab"))
    assertTrue(history.canRedo)

    history.reset("external")

    assertFalse(history.canUndo)
    assertFalse(history.canRedo)
    assertNull(history.undo("external"))
  }

  @Test
  fun staleCurrentTextClearsHistoryAndRejectsUndo() {
    val history = MarkdownEditHistory("a")
    history.record(before = "a", after = "ab", timestampMillis = 1_000)

    assertNull(history.undo("external"))
    assertFalse(history.canUndo)
    assertFalse(history.canRedo)
  }
}

package io.pagenote.android

import java.util.ArrayDeque

class MarkdownEditHistory(initialMarkdown: String) {
  private val undo = ArrayDeque<String>()
  private val redo = ArrayDeque<String>()
  private var currentMarkdown = initialMarkdown
  private var lastEditAt: Long? = null

  val canUndo: Boolean
    get() = undo.isNotEmpty()

  val canRedo: Boolean
    get() = redo.isNotEmpty()

  fun record(before: String, after: String, timestampMillis: Long) {
    if (before == after) return
    if (before != currentMarkdown) reset(before)

    val previousEditAt = lastEditAt
    if (undo.isEmpty() || previousEditAt == null || timestampMillis < previousEditAt ||
      timestampMillis - previousEditAt > GROUP_WINDOW_MILLIS
    ) {
      pushUndo(before)
    }
    redo.clear()
    currentMarkdown = after
    lastEditAt = timestampMillis
  }

  fun undo(current: String): String? {
    if (current != currentMarkdown) {
      reset(current)
      return null
    }
    val previous = undo.pollLast() ?: return null
    redo.addLast(currentMarkdown)
    currentMarkdown = previous
    lastEditAt = null
    return previous
  }

  fun redo(current: String): String? {
    if (current != currentMarkdown) {
      reset(current)
      return null
    }
    val next = redo.pollLast() ?: return null
    pushUndo(currentMarkdown)
    currentMarkdown = next
    lastEditAt = null
    return next
  }

  fun reset(markdown: String) {
    undo.clear()
    redo.clear()
    currentMarkdown = markdown
    lastEditAt = null
  }

  private fun pushUndo(markdown: String) {
    if (undo.size == MAX_UNDO_STEPS) undo.removeFirst()
    undo.addLast(markdown)
  }

  private companion object {
    const val GROUP_WINDOW_MILLIS = 500L
    const val MAX_UNDO_STEPS = 80
  }
}

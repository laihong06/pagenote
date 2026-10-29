package io.pagenote.feature.pen

/**
 * Paper background kinds for the note/canvas surface (port batch 3 data layer).
 *
 * The serialized `key` strings are the cross-batch contract: once released they
 * must never be renamed or removed, because `src/note-settings.json` and the
 * reader settings both persist them. Unknown keys decode to `null` on read so
 * older builds keep opening projects written by newer ones (the caller falls
 * back to its default background instead of failing).
 *
 * Rendering is intentionally out of scope here; the canvas redo batch consumes
 * this enum for drawing. This type only owns identity and persistence keys.
 */
enum class NoteBackground(val key: String) {
  PLAIN("plain"),
  FOUR_LINE("four-line"),
  GRID("grid"),
  DOT("dot"),
  RESTRICTED_MARGIN("restricted-margin");

  companion object {
    /** All kinds in stable declaration order; catalog must only grow. */
    val ALL: List<NoteBackground> = entries.toList()

    /** Tolerant decode: unknown or blank keys yield `null` (caller falls back to a default). */
    fun fromKey(key: String?): NoteBackground? = ALL.firstOrNull { it.key == key }
  }
}

/**
 * Resolve the effective background for a project: a project-level override wins
 * over the app-level default; when the project never chose one the default
 * applies. `null` from [NoteBackground.fromKey] (unknown persisted key) is
 * treated the same as "not set".
 */
fun resolveNoteBackground(projectOverride: NoteBackground?, default: NoteBackground): NoteBackground =
  projectOverride ?: default

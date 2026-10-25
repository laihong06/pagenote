package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteBackgroundTest {
  @Test
  fun catalogCoversThePortedBackgroundKinds() {
    assertEquals(
      listOf("plain", "four-line", "grid", "dot", "restricted-margin"),
      NoteBackground.ALL.map(NoteBackground::key),
    )
  }

  @Test
  fun keysRoundTripThroughFromKey() {
    NoteBackground.ALL.forEach { background ->
      assertEquals(background, NoteBackground.fromKey(background.key))
    }
  }

  @Test
  fun unknownKeyDecodesToNull() {
    assertNull(NoteBackground.fromKey("dotted-grid"))
    assertNull(NoteBackground.fromKey(""))
    assertNull(NoteBackground.fromKey(null))
  }

  @Test
  fun keySpellingIsStableForTheWireFormat() {
    // Guard the two hyphenated keys against accidental renames; they are the
    // persisted contract shared with project files and reader settings.
    assertEquals("four-line", NoteBackground.FOUR_LINE.key)
    assertEquals("restricted-margin", NoteBackground.RESTRICTED_MARGIN.key)
  }

  @Test
  fun projectOverrideWinsOverDefault() {
    assertEquals(
      NoteBackground.GRID,
      resolveNoteBackground(projectOverride = NoteBackground.GRID, default = NoteBackground.PLAIN),
    )
  }

  @Test
  fun missingOverrideFallsBackToDefault() {
    assertEquals(
      NoteBackground.FOUR_LINE,
      resolveNoteBackground(projectOverride = null, default = NoteBackground.FOUR_LINE),
    )
  }

  @Test
  fun unknownPersistedOverrideBehavesLikeUnset() {
    assertEquals(
      NoteBackground.DOT,
      resolveNoteBackground(
        projectOverride = NoteBackground.fromKey("future-kind"),
        default = NoteBackground.DOT,
      ),
    )
  }
}

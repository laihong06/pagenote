package io.pagenote.feature.notes

import io.pagenote.core.project.PageNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSCompatibilityTest {
  @Test
  fun unknownExecutableFencesRemainSourceAndSurvivePageEditing() {
    val source = "<style>p{color:red}</style>\n<canvas></canvas>\n<script>const label = `汉字 \$value`; // not executed\n</script>"
    for (engine in listOf("mds", "mds-html", "mds-svg", "mds-canvas", "mds-three", "mds-chart")) {
      val note = "Before\n\n```$engine\n$source\n```\n\nAfter"
      val parsed = NoteRichContent.extract(note)
      assertEquals(engine, parsed.codeBlocks.single().lang)
      assertTrue(parsed.codeBlocks.single().source.contains(source))
      val original = PageNotes.join(listOf(note, "Other page"))
      val result = PageNoteEditor.replacePage(original, 2, "Edited other page", PageNotes.revision("Other page"))
      assertEquals(note, PageNotes.split(result.markdown)[0])
      val updated = note.replace("Before", "Updated prose")
      val replaced = PageNoteEditor.replacePage(result.markdown, 1, updated, PageNotes.revision(note))
      assertEquals(updated, PageNotes.split(replaced.markdown)[0])
      assertEquals("Edited other page", PageNotes.split(replaced.markdown)[1])
    }
  }
}

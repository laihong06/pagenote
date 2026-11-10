package io.pagenote.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSStateEditorTest {
  private val original = MarkdownSState.parse(
    """{"version":1,"id":"target","kind":"genetic-lab","genes":[1,0,1,0,1,0,1,0,1,0,1,0]}""",
  )
  private val changed = original.copy(genes = listOf(0, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0))

  @Test
  fun replacesOnlyMatchingBlockAcrossCrLfAndKeepsOtherMarkdownAndPages() {
    val firstPage = "Before 😀\r\n```markdowns\r\n${original.encode()}\r\n```\r\nAfter\r\n"
    val markdown = "$firstPage\r\n<!-- page -->\r\nOther page"

    val updated = MarkdownSStateEditor.replace(markdown, "target", original, changed)

    assertTrue(updated.startsWith("Before 😀\r\n```markdowns\r\n"))
    assertTrue(updated.endsWith("\r\n```\r\nAfter\r\n\r\n<!-- page -->\r\nOther page"))
    assertEquals(1, MarkdownSBlockParser.parse(updated).size)
    assertEquals(changed.genes, MarkdownSBlockParser.parse(updated).single().parseResult.state!!.genes)
  }

  @Test
  fun rejectsMissingDuplicateAndStaleStateWithoutReturningModifiedMarkdown() {
    val one = "```markdowns\n${original.encode()}\n```"
    val cases = listOf(
      "Before only",
      "$one\n\n$one",
      one,
    )
    cases.forEachIndexed { index, markdown ->
      try {
        val expected = if (index == 2) original.copy(genes = List(12) { 0 }) else original
        MarkdownSStateEditor.replace(markdown, "target", expected, changed)
        throw AssertionError("Expected replace to reject case $index")
      } catch (_: MarkdownSBlockNotUniqueException) {
      } catch (error: MarkdownSStateConflictException) {
        assertEquals(2, index)
      }
    }
  }
}

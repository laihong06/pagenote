package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTaskInputTest {
  @Test
  fun buildsSinglePageInputWithRevision() {
    val input = AgentTaskInput.build(
      documentId = "doc-1",
      page = 2,
      totalPages = 5,
      instruction = "补个例子",
      noteMarkdown = "# n2",
      noteRevision = "rev-2",
      sourceText = "source-2",
      neighbors = listOf("prev" to 1, "next" to 3),
    )

    assertEquals("doc-1", input.task.documentId)
    assertEquals(2, input.task.page)
    assertEquals("补个例子", input.task.instruction)
    assertEquals("rev-2", input.task.expectedRevision)
    assertEquals(8, input.task.maxSteps)
    assertTrue(input.messages.any { it["role"] == "user" && it["content"].orEmpty().contains("source-2") })
  }

  @Test
  fun includesOcrWarningsInPageSourceContext() {
    val input = AgentTaskInput.build(
      documentId = "doc-1",
      page = 1,
      totalPages = 1,
      instruction = "解释本页",
      noteMarkdown = "",
      noteRevision = "rev-1",
      sourceText = "recognized source",
      sourceWarnings = listOf("OCR confidence is low; verify against the PDF."),
    )

    val userMessage = input.messages.single { it["role"] == "user" }["content"].orEmpty()
    assertTrue(userMessage.contains("OCR confidence is low; verify against the PDF."))
  }

  @Test
  fun rejectsWholeChapterAssembly() {
    try {
      AgentTaskInput.build(
        documentId = "doc-1",
        page = 2,
        totalPages = 3,
        instruction = "x",
        noteMarkdown = "n",
        noteRevision = "r",
        sourceText = "s",
        neighbors = listOf("a" to 1, "b" to 3),
      )
      throw AssertionError("expected whole-chapter refusal")
    } catch (_: IllegalArgumentException) {
    }
  }
}

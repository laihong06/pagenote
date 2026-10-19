package io.pagenote.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRequestTest {
  private val task = AgentTask(
    documentId = "doc",
    page = 3,
    instruction = "explain",
    expectedRevision = "rev-task",
    maxSteps = 8,
  )

  @Test
  fun singlePageCarriesNeighborsAndRevision() {
    val messages = PageRequest.build(
      task = task,
      current = PageRequest.PageMaterial(page = 3, text = "full page 3", revision = "rev-3"),
      neighbors = listOf(
        PageRequest.PageMaterial(page = 2, text = "summary 2"),
        PageRequest.PageMaterial(page = 4, text = "summary 4"),
      ),
    )
    assertEquals(2, messages.size)
    assertEquals("system", messages[0]["role"])
    val user = messages[1]["content"].orEmpty()
    assertTrue(user.contains("full page 3"))
    assertTrue(user.contains("[neighbor page 2] summary 2"))
    assertTrue(user.contains("[neighbor page 4] summary 4"))
    assertTrue(user.contains("expectedRevision: rev-3"))
    assertTrue(user.contains("taskExpectedRevision: rev-task"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun wholeChapterAssemblyRefused() {
    PageRequest.build(
      task = task,
      current = PageRequest.PageMaterial(page = 3, text = "p3"),
      neighbors = listOf(
        PageRequest.PageMaterial(page = 1, text = "p1"),
        PageRequest.PageMaterial(page = 2, text = "p2"),
      ),
      totalPages = 3,
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun tooManyNeighborsRejected() {
    PageRequest.build(
      task = task,
      current = PageRequest.PageMaterial(page = 3, text = "p3"),
      neighbors = listOf(
        PageRequest.PageMaterial(page = 1, text = "p1"),
        PageRequest.PageMaterial(page = 2, text = "p2"),
        PageRequest.PageMaterial(page = 4, text = "p4"),
      ),
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun currentPageMismatchRejected() {
    PageRequest.build(
      task = task,
      current = PageRequest.PageMaterial(page = 5, text = "p5"),
    )
  }

  @Test
  fun singlePageDocumentAllowsPageScopedRequest() {
    val messages = PageRequest.build(
      task = task.copy(page = 1),
      current = PageRequest.PageMaterial(page = 1, text = "only page"),
      totalPages = 1,
    )

    assertTrue(messages.last()["content"].orEmpty().contains("only page"))
  }
}

package io.pagenote.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTutorTest {
  private val endpoint = ModelEndpoint(
    baseUrl = "https://model.invalid/v1",
    apiKey = "unit-test-only",
    model = "unit-test-model",
  )

  @Test
  fun requestContainsOnlyCurrentAndAdjacentPageMaterial() = runBlocking {
    val chat = RecordingChatClient("A clear explanation.")
    val request = request().copy(
      sourceText = "current PDF source",
      noteMarkdown = "current note",
      neighbors = listOf(
        PageRequest.PageMaterial(page = 2, text = "previous PDF source"),
        PageRequest.PageMaterial(page = 4, text = "next PDF source"),
      ),
    )

    val result = PageTutor(chat).explain(endpoint, request)

    assertEquals(1, chat.calls)
    assertEquals(3, result.page)
    assertEquals("note-revision-3", result.noteRevision)
    val userMessage = chat.messages.single { it["role"] == "user" }["content"].orEmpty()
    assertTrue(userMessage.contains("current PDF source"))
    assertTrue(userMessage.contains("current note"))
    assertTrue(userMessage.contains("previous PDF source"))
    assertTrue(userMessage.contains("next PDF source"))
    assertTrue(userMessage.contains("expectedRevision: note-revision-3"))
    assertFalse(userMessage.contains("unrequested page"))
  }

  @Test
  fun focusRequestUsesOnlySelectedRegionAndTargetPageNote() = runBlocking {
    val chat = RecordingChatClient("A focused explanation.")
    val request = request().copy(
      sourceText = "full-page OCR must stay out of focus mode",
      noteMarkdown = "target page note",
      neighbors = listOf(
        PageRequest.PageMaterial(page = 2, text = "previous page must stay out"),
        PageRequest.PageMaterial(page = 4, text = "next page must stay out"),
      ),
      focus = PageTutorFocus(
        label = "Selected equation",
        text = "region OCR excerpt",
        warnings = listOf("OCR confidence is low; verify the original region."),
      ),
    )

    val result = PageTutor(chat).explain(endpoint, request)

    val userMessage = chat.messages.single { it["role"] == "user" }["content"].orEmpty()
    assertTrue(userMessage.contains("Selected-region OCR"))
    assertTrue(userMessage.contains("untrusted"))
    assertTrue(userMessage.contains("Selected equation"))
    assertTrue(userMessage.contains("region OCR excerpt"))
    assertTrue(userMessage.contains("target page note"))
    assertTrue(userMessage.contains("expectedRevision: note-revision-3"))
    assertFalse(userMessage.contains("full-page OCR must stay out"))
    assertFalse(userMessage.contains("previous page must stay out"))
    assertFalse(userMessage.contains("next page must stay out"))
    assertEquals(listOf("OCR confidence is low; verify the original region."), result.warnings)
  }

  @Test
  fun rejectsBlankFocusBeforeCallingClient() = runBlocking {
    val chat = RecordingChatClient("unused")

    assertThrows(IllegalArgumentException::class.java) {
      runBlocking {
        PageTutor(chat).explain(
          endpoint,
          request().copy(focus = PageTutorFocus(label = "Selected region", text = " \n ")),
        )
      }
    }
    assertEquals(0, chat.calls)
  }

  @Test
  fun capsFocusTextAtTwentyThousandCharactersWithExplicitWarning() = runBlocking {
    val chat = RecordingChatClient("Explanation")
    val request = request().copy(
      focus = PageTutorFocus(
        label = "Selected region",
        text = "r".repeat(PageTutor.MAX_PAGE_TEXT_CHARS) + "FOCUS_TAIL",
      ),
    )

    val result = PageTutor(chat).explain(endpoint, request)
    val userMessage = chat.messages.single { it["role"] == "user" }["content"].orEmpty()

    assertTrue(userMessage.contains("r".repeat(PageTutor.MAX_PAGE_TEXT_CHARS)))
    assertFalse(userMessage.contains("FOCUS_TAIL"))
    assertTrue(result.warnings.contains("Selected region OCR text was truncated to 20000 characters."))
  }

  @Test
  fun capsEachPdfPageAtTwentyThousandCharactersWithWarnings() = runBlocking {
    val chat = RecordingChatClient("Explanation")
    val request = request().copy(
      sourceText = "c".repeat(PageTutor.MAX_PAGE_TEXT_CHARS) + "CURRENT_TAIL",
      neighbors = listOf(
        PageRequest.PageMaterial(
          page = 2,
          text = "p".repeat(PageTutor.MAX_PAGE_TEXT_CHARS) + "PREVIOUS_TAIL",
        ),
      ),
    )

    val result = PageTutor(chat).explain(endpoint, request)
    val userMessage = chat.messages.single { it["role"] == "user" }["content"].orEmpty()

    assertFalse(userMessage.contains("CURRENT_TAIL"))
    assertFalse(userMessage.contains("PREVIOUS_TAIL"))
    assertTrue(result.warnings.any { it.contains("20000") && it.contains("3") })
    assertTrue(result.warnings.any { it.contains("20000") && it.contains("2") })
  }

  @Test
  fun avoidsSendingEveryPageOfShortDocuments() = runBlocking {
    val chat = RecordingChatClient("Explanation")
    val request = request().copy(
      page = 2,
      totalPages = 3,
      neighbors = listOf(
        PageRequest.PageMaterial(page = 1, text = "previous"),
        PageRequest.PageMaterial(page = 3, text = "next"),
      ),
    )

    PageTutor(chat).explain(endpoint, request)

    val userMessage = chat.messages.single { it["role"] == "user" }["content"].orEmpty()
    assertTrue(userMessage.contains("[neighbor page 1] previous"))
    assertFalse(userMessage.contains("[neighbor page 3] next"))
  }

  @Test
  fun doesNotWarnAboutTruncationOnNeighborDroppedFromRequest() = runBlocking {
    val result = PageTutor(RecordingChatClient("Explanation")).explain(
      endpoint,
      request().copy(
        page = 2,
        totalPages = 3,
        neighbors = listOf(
          PageRequest.PageMaterial(page = 1, text = "p".repeat(PageTutor.MAX_PAGE_TEXT_CHARS + 1)),
          PageRequest.PageMaterial(page = 3, text = "n".repeat(PageTutor.MAX_PAGE_TEXT_CHARS + 1)),
        ),
      ),
    )

    assertTrue(result.warnings.any { it.contains("Page 1 PDF text") })
    assertFalse(result.warnings.any { it.contains("Page 3 PDF text") })
  }

  @Test
  fun returnsOrdinaryMarkdownWithoutAgentProtocolParsing() = runBlocking {
    val markdown = "### Explanation\n\nDONE\n\nTOOL write_note page=99"
    val result = PageTutor(RecordingChatClient(markdown)).explain(endpoint, request())

    assertEquals(markdown, result.markdown)
  }

  @Test
  fun rejectsBlankSourceBeforeCallingClient() = runBlocking {
    val chat = RecordingChatClient("unused")

    assertThrows(IllegalArgumentException::class.java) {
      runBlocking {
        PageTutor(chat).explain(endpoint, request().copy(sourceText = " \n "))
      }
    }
    assertEquals(0, chat.calls)
  }

  @Test
  fun rejectsBlankResponse() {
    runBlocking {
      val chat = RecordingChatClient(" \n ")

      assertThrows(IllegalStateException::class.java) {
        runBlocking { PageTutor(chat).explain(endpoint, request()) }
      }
    }
  }

  @Test
  fun rejectsNonAdjacentNeighborPagesBeforeCallingClient() = runBlocking {
    val chat = RecordingChatClient("unused")

    assertThrows(IllegalArgumentException::class.java) {
      runBlocking {
        PageTutor(chat).explain(
          endpoint,
          request().copy(neighbors = listOf(PageRequest.PageMaterial(page = 1, text = "distant"))),
        )
      }
    }
    assertEquals(0, chat.calls)
  }

  @Test
  fun retainsWarningsFromOcrSourcePreparation() = runBlocking {
    val result = PageTutor(RecordingChatClient("Explanation")).explain(
      endpoint,
      request().copy(sourceWarnings = listOf("OCR confidence is low; verify the original page.")),
    )

    assertEquals(listOf("OCR confidence is low; verify the original page."), result.warnings)
  }

  private fun request() = PageTutorRequest(
    documentId = "document-1",
    page = 3,
    totalPages = 8,
    instruction = "Explain this page clearly.",
    sourceText = "page source",
    noteMarkdown = "existing note",
    noteRevision = "note-revision-3",
  )

  private class RecordingChatClient(private val response: String) : ChatClient {
    var calls = 0
    var messages: List<Map<String, String>> = emptyList()

    override suspend fun complete(
      endpoint: ModelEndpoint,
      messages: List<Map<String, String>>,
    ): String {
      calls += 1
      this.messages = messages
      return response
    }
  }
}

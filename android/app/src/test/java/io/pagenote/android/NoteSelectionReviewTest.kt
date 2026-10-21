package io.pagenote.android

import io.pagenote.core.project.PageNotes
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSelectionReviewTest {
  @Test
  fun captureRetainsExactQuoteAndUtf16Anchor() {
    val context = "before\nselected text\nafter"

    val draft = NoteSelectionReviewDraft.capture(page = 3, context = context, start = 7, end = 20)

    assertEquals("selected text", draft.quote)
    assertEquals(context, draft.context)
    assertEquals(3, draft.page)
    assertEquals(PageNotes.revision(context), draft.revision)
    assertEquals("markdown-source-utf16", draft.anchor["coordinateSystem"]?.jsonPrimitive?.content)
    assertEquals(7, draft.anchor["start"]?.jsonPrimitive?.content?.toInt())
    assertEquals(20, draft.anchor["end"]?.jsonPrimitive?.content?.toInt())
    assertEquals(draft.quote, context.substring(7, 20))
  }

  @Test
  fun captureNormalizesReversedDirectionalSelectionEndpoints() {
    val context = "alpha selected omega"

    val draft = NoteSelectionReviewDraft.capture(page = 1, context = context, start = 14, end = 6)

    assertEquals("selected", draft.quote)
    assertEquals(6, draft.anchor["start"]?.jsonPrimitive?.content?.toInt())
    assertEquals(14, draft.anchor["end"]?.jsonPrimitive?.content?.toInt())
    assertEquals(draft.quote, context.substring(6, 14))
  }

  @Test
  fun captureRejectsInvalidPagesAndRanges() {
    assertThrows(IllegalArgumentException::class.java) {
      NoteSelectionReviewDraft.capture(page = 0, context = "abc", start = 0, end = 1)
    }
    assertThrows(IllegalArgumentException::class.java) {
      NoteSelectionReviewDraft.capture(page = -1, context = "abc", start = 0, end = 1)
    }
    assertThrows(IllegalArgumentException::class.java) {
      NoteSelectionReviewDraft.capture(page = 1, context = "abc", start = 1, end = 1)
    }
    assertThrows(IllegalArgumentException::class.java) {
      NoteSelectionReviewDraft.capture(page = 1, context = "abc", start = -1, end = 1)
    }
    assertThrows(IllegalArgumentException::class.java) {
      NoteSelectionReviewDraft.capture(page = 1, context = "abc", start = 1, end = 4)
    }
    assertThrows(IllegalArgumentException::class.java) {
      NoteSelectionReviewDraft.capture(page = 1, context = "abc", start = 4, end = 1)
    }
  }

  @Test
  fun captureRejectsWhitespaceOnlyQuotes() {
    assertThrows(IllegalArgumentException::class.java) {
      NoteSelectionReviewDraft.capture(page = 1, context = "x \n\t y", start = 1, end = 4)
    }
  }

  @Test
  fun captureUsesUtf16OffsetsForSupplementaryCharacters() {
    val context = "a😀b"

    val draft = NoteSelectionReviewDraft.capture(page = 2, context = context, start = 1, end = 3)

    assertEquals("😀", draft.quote)
    assertEquals(4, context.length)
    assertEquals(1, draft.anchor["start"]?.jsonPrimitive?.content?.toInt())
    assertEquals(3, draft.anchor["end"]?.jsonPrimitive?.content?.toInt())
    assertEquals(draft.quote, context.substring(1, 3))
  }

  @Test
  fun captureEnforcesQuoteAndContextLimitsAtUtf16Boundaries() {
    val maxQuote = "q".repeat(100_000)
    val quoteDraft = NoteSelectionReviewDraft.capture(
      page = 1,
      context = maxQuote,
      start = 0,
      end = maxQuote.length,
    )
    assertEquals(100_000, quoteDraft.quote.length)
    assertThrows(IllegalArgumentException::class.java) {
      NoteSelectionReviewDraft.capture(page = 1, context = "q".repeat(100_001), start = 0, end = 100_001)
    }

    val maxContext = "x" + "a".repeat(999_999)
    val contextDraft = NoteSelectionReviewDraft.capture(page = 1, context = maxContext, start = 0, end = 1)
    assertEquals(1_000_000, contextDraft.context.length)
    assertThrows(IllegalArgumentException::class.java) {
      NoteSelectionReviewDraft.capture(page = 1, context = "x".repeat(1_000_001), start = 0, end = 1)
    }
  }

  @Test
  fun captureRejectsOversizedSelectionBeforeMaterializingText() {
    val context = MaterializationForbiddenText(100_001)

    val error = assertThrows(IllegalArgumentException::class.java) {
      NoteSelectionReviewDraft.capture(page = 1, context = context, start = 0, end = context.length)
    }

    assertTrue(error.message.orEmpty().contains("selection is too large"))
  }

  @Test
  fun requireCurrentPageRejectsAChangedContextRevision() {
    val context = "captured page"
    val draft = NoteSelectionReviewDraft.capture(page = 1, context = context, start = 0, end = 8)

    draft.requireCurrentPage(context)

    assertThrows(IllegalArgumentException::class.java) {
      draft.requireCurrentPage("externally edited page")
    }
  }
}

private class MaterializationForbiddenText(override val length: Int) : CharSequence {
  override fun get(index: Int): Char = error("oversized selection must be rejected before reading")

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
    error("oversized selection must be rejected before slicing")

  override fun toString(): String = error("oversized selection must be rejected before materializing")
}

package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MarkdownImageInsertionTest {
  @Test
  fun createsProjectRelativeReferencesForSupportedImageExtensions() {
    listOf("png", "jpg", "jpeg", "gif", "webp").forEach { extension ->
      val name = "01234567-89ab-cdef-0123-456789abcdef.$extension"
      assertEquals(
        "![page scan](assets/$name)",
        MarkdownImageInsertion.createReference("page scan", name),
      )
    }
  }

  @Test
  fun rejectsPathsAndNonUuidAssetNames() {
    listOf(
      "../01234567-89ab-cdef-0123-456789abcdef.png",
      "assets/01234567-89ab-cdef-0123-456789abcdef.png",
      "01234567-89ab-cdef-0123-456789abcdef.svg",
      "not-a-uuid.png",
    ).forEach { name ->
      assertThrows(IllegalArgumentException::class.java) {
        MarkdownImageInsertion.createReference("image", name)
      }
    }
  }

  @Test
  fun escapesMarkdownAltTextAndRemovesControlCharacters() {
    val reference = MarkdownImageInsertion.createReference(
      "[diagram]\\draft\nnext\u0000",
      "01234567-89ab-cdef-0123-456789abcdef.png",
    )

    assertEquals(
      "![\\[diagram\\]\\\\draft next](assets/01234567-89ab-cdef-0123-456789abcdef.png)",
      reference,
    )
  }

  @Test
  fun insertsAtCaretAndReturnsCursorAfterReference() {
    val markdown = "before after"
    val reference = "![scan](assets/01234567-89ab-cdef-0123-456789abcdef.png)"
    val edit = MarkdownImageInsertion.createEdit(markdown, 7, 7, reference)

    assertEquals(7, edit.replaceStart)
    assertEquals(7, edit.replaceEnd)
    assertEquals("\n$reference\n", edit.replacement)
    assertEquals(7 + edit.replacement.length, edit.cursor)
    assertEquals("before \n$reference\nafter", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
  }

  @Test
  fun replacesOnlySelectedRangeAndKeepsSupplementaryCharacters() {
    val markdown = "a😀 before selected after"
    val start = markdown.indexOf("selected")
    val end = start + "selected".length
    val reference = "![scan](assets/01234567-89ab-cdef-0123-456789abcdef.png)"
    val edit = MarkdownImageInsertion.createEdit(markdown, start, end, reference)

    assertEquals("a😀 before \n$reference\n after", markdown.replaceRange(edit.replaceStart, edit.replaceEnd, edit.replacement))
    assertEquals(start + edit.replacement.length, edit.cursor)
  }

  @Test
  fun clampsAndNormalizesSelectionBounds() {
    val reference = "![scan](assets/01234567-89ab-cdef-0123-456789abcdef.png)"
    val edit = MarkdownImageInsertion.createEdit("abc", 9, -4, reference)

    assertEquals(0, edit.replaceStart)
    assertEquals(3, edit.replaceEnd)
    assertEquals("\n$reference\n", edit.replacement)
    assertEquals(edit.replacement.length, edit.cursor)
  }
}

package io.pagenote.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteRangeTest {
  @Test
  fun slice_middle_page() {
    val slice = NoteRange.slice("0123456789", 4, 3)
    assertEquals("456", slice.text)
    assertEquals(4, slice.start)
    assertEquals(7, slice.end)
    assertEquals(10, slice.total)
    assertTrue(slice.hasMore)
  }

  @Test
  fun slice_past_end_is_empty() {
    val slice = NoteRange.slice("abc", 9, 8)
    assertEquals("", slice.text)
    assertFalse(slice.hasMore)
  }

  @Test
  fun patch_replaces_range() {
    assertEquals("aXYZef", NoteRange.patch("abcdef", 1, "XYZ", 4))
  }
}

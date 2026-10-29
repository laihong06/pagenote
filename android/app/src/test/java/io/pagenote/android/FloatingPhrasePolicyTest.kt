package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 悬浮窗常用语策略（移植差距清单 #24）。 */
class FloatingPhrasePolicyTest {

  @Test
  fun normalizeTrimsAndRejectsBlank() {
    assertEquals("hello", FloatingPhrasePolicy.normalize("  hello  \n"))
    assertNull(FloatingPhrasePolicy.normalize("   "))
    assertNull(FloatingPhrasePolicy.normalize(""))
  }

  @Test
  fun normalizeTruncatesToMaxChars() {
    val long = "a".repeat(FloatingPhrasePolicy.MAX_CHARS + 10)
    assertEquals(FloatingPhrasePolicy.MAX_CHARS, FloatingPhrasePolicy.normalize(long)!!.length)
  }

  @Test
  fun addPutsNewPhraseOnTop() {
    val result = FloatingPhrasePolicy.add(listOf("b", "a"), "c")
    assertEquals(listOf("c", "b", "a"), result)
  }

  @Test
  fun addDeduplicatesByMovingExistingToTop() {
    val result = FloatingPhrasePolicy.add(listOf("b", "a", "c"), "a")
    assertEquals(listOf("a", "b", "c"), result)
  }

  @Test
  fun addRejectsBlankByReturningInput() {
    val existing = listOf("a")
    assertEquals(existing, FloatingPhrasePolicy.add(existing, "  "))
  }

  @Test
  fun addEvictsOldestBeyondCapacity() {
    val full = (1..FloatingPhrasePolicy.MAX_PHRASES).map { "p$it" }
    val result = FloatingPhrasePolicy.add(full, "new")
    assertEquals(FloatingPhrasePolicy.MAX_PHRASES, result.size)
    assertEquals("new", result.first())
    // 溢出发生在列表末尾：最旧的 p50 被挤出，p1 仍在。
    assertEquals("p49", result.last())
    assertEquals(false, result.contains("p50"))
  }

  @Test
  fun removeDeletesGivenIndexAndIgnoresOutOfRange() {
    assertEquals(listOf("a", "c"), FloatingPhrasePolicy.remove(listOf("a", "b", "c"), 1))
    assertEquals(listOf("a", "b", "c"), FloatingPhrasePolicy.remove(listOf("a", "b", "c"), 9))
    assertEquals(listOf("a", "b", "c"), FloatingPhrasePolicy.remove(listOf("a", "b", "c"), -1))
  }

  @Test
  fun previewFlattensWhitespaceAndTruncates() {
    assertEquals("hello world", FloatingPhrasePolicy.preview("hello\n  world"))
    val preview = FloatingPhrasePolicy.preview("x".repeat(100), maxChars = 10)
    assertEquals(11, preview.length)
    assertTrue(preview.endsWith("…"))
  }
}

package io.pagenote.feature.ocr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexTokenizerTest {
  @Test
  fun padsReservedPrefixWhenDictionaryIsShort() {
    val tokenizer = LatexTokenizer.fromTokens(listOf("\\frac"))
    assertEquals(listOf("<pad>", "<bos>", "<eos>", "\\frac"), tokenizer.tokens)
    assertEquals(4, tokenizer.vocabularySize)
  }

  @Test
  fun decodeStopsAtEosAndTrims() {
    val tokenizer = LatexTokenizer.fromTokens(listOf("a", "+", "b", "c"))
    val ids = intArrayOf(tokenizer.bosId, 3, 4, 5, tokenizer.eosId, 6)
    assertEquals("a+b", tokenizer.decode(ids))
  }

  @Test
  fun decodeMarksUnknownIds() {
    val tokenizer = LatexTokenizer.fromTokens(listOf("a"))
    assertEquals("a\u25A1", tokenizer.decode(intArrayOf(tokenizer.bosId, 3, 99)))
  }

  @Test
  fun encodeRoundTripsLongestMatchFirst() {
    val tokenizer = LatexTokenizer.fromTokens(
      listOf("\\frac", "{", "}", "x", "\\alpha", "^"),
    )
    val text = "\\frac{x}^{\\alpha}"
    val encoded = tokenizer.encode(text)
    // 最长匹配优先：token 数少于字符数，且逐 token 拼接应还原原文。
    assertTrue(encoded.size < text.length)
    val joined = encoded.joinToString("") { id ->
      if (id >= 0) tokenizer.tokenAt(id)!! else "\u25A1"
    }
    assertEquals(text, joined)
    assertTrue(encoded.none { it == LatexTokenizer.UNKNOWN_ID })
  }

  @Test
  fun fromJsonTextAcceptsPlainArrayAndWrappedObject() {
    val arrayForm = LatexTokenizer.fromJsonText("""["<pad>","<bos>","<eos>","\\frac","x"]""")
    assertEquals(5, arrayForm.vocabularySize)
    assertEquals("\\frac", arrayForm.tokenAt(3))

    val wrapped = LatexTokenizer.fromJsonText("""{"version":1,"tokens":["<pad>","<bos>","<eos>","y"]}""")
    assertEquals("y", wrapped.tokenAt(3))
  }

  @Test
  fun fromJsonTextRejectsNonDictionaryPayload() {
    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      LatexTokenizer.fromJsonText("""{"version":1}""")
    }
    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      LatexTokenizer.fromJsonText("")
    }
  }

  @Test
  fun fromJsonTextUnescapesUnicodeTokens() {
    val tokenizer = LatexTokenizer.fromJsonText("[\"<pad>\",\"<bos>\",\"<eos>\",\"\\u03c0\"]")
    assertEquals("π", tokenizer.tokenAt(3))
    assertArrayEquals(intArrayOf(3), tokenizer.encode("π"))
  }
}

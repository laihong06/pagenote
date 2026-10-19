package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Test

class MermaidSourceCodecTest {
  @Test
  fun roundTripsQuotesUnicodeAndMarkupWithoutExecutableUriCharacters() {
    val source = "graph TD\nA[\"引号 <script>alert(1)</script>\"] --> B[\"https://example.test\"]"

    val uri = MermaidSourceCodec.encode(source)

    assertEquals(source, MermaidSourceCodec.decode(uri))
    assertEquals("pagenote-mermaid", uri.substringBefore(':'))
    assertEquals(-1, uri.indexOf('('))
    assertEquals(-1, uri.indexOf(')'))
  }

  @Test
  fun emptySourceRoundTrips() {
    assertEquals("", MermaidSourceCodec.decode(MermaidSourceCodec.encode("")))
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsMalformedPrivateImageUri() {
    MermaidSourceCodec.decode("pagenote-mermaid:not_base64!")
  }
}

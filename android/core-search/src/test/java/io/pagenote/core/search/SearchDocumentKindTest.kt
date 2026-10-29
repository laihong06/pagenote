package io.pagenote.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchDocumentKindTest {
  @Test
  fun keysAreStableForPersistence() {
    assertEquals("source", SearchDocumentKind.SOURCE.key)
    assertEquals("note", SearchDocumentKind.NOTE.key)
    assertEquals("ocr", SearchDocumentKind.OCR.key)
    assertEquals("transcript", SearchDocumentKind.TRANSCRIPT.key)
  }

  @Test
  fun fromKeyRoundTripsKnownKeys() {
    SearchDocumentKind.entries.forEach { kind ->
      assertEquals(kind, SearchDocumentKind.fromKey(kind.key))
    }
  }

  @Test
  fun unknownKeysDegradeToNull() {
    assertNull(SearchDocumentKind.fromKey("mystery"))
    assertNull(SearchDocumentKind.fromKey(""))
  }
}

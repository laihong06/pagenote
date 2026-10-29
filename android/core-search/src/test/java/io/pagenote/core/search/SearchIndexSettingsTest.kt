package io.pagenote.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexSettingsTest {
  @Test
  fun defaultsEnableEveryKind() {
    assertEquals(
      setOf(
        SearchDocumentKind.SOURCE,
        SearchDocumentKind.NOTE,
        SearchDocumentKind.OCR,
        SearchDocumentKind.TRANSCRIPT,
      ),
      SearchIndexSettings().kinds(),
    )
  }

  @Test
  fun kindsFollowTheEnabledFlags() {
    val settings = SearchIndexSettings(
      indexSource = false,
      indexNotes = true,
      indexOcr = false,
      indexTranscripts = true,
    )
    assertEquals(setOf(SearchDocumentKind.NOTE, SearchDocumentKind.TRANSCRIPT), settings.kinds())
  }

  @Test
  fun sanitizeFallsBackToAllOnWhenNothingIsIndexed() {
    val sanitized = SearchIndexSettings.sanitize(
      SearchIndexSettings(indexSource = false, indexNotes = false, indexOcr = false, indexTranscripts = false),
    )
    assertEquals(SearchIndexSettings(), sanitized)
  }

  @Test
  fun sanitizeKeepsNonEmptyScopes() {
    val settings = SearchIndexSettings(indexNotes = true, indexSource = false, indexOcr = false, indexTranscripts = false)
    assertEquals(settings, SearchIndexSettings.sanitize(settings))
  }

  @Test(expected = IllegalArgumentException::class)
  fun validateRejectsEmptyScope() {
    SearchIndexSettings.validate(
      SearchIndexSettings(indexSource = false, indexNotes = false, indexOcr = false, indexTranscripts = false),
    )
  }

  @Test
  fun defaultScopePassesValidation() {
    SearchIndexSettings.validate(SearchIndexSettings())
    assertTrue(true)
  }
}

package io.pagenote.android

import io.pagenote.core.search.SearchDocumentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 批次 7：ReaderSettings 的搜索索引范围 sanitize / validate / kinds 联动。 */
class ReaderSearchSettingsTest {
  @Test
  fun defaultsIndexEverything() {
    val settings = ReaderSettings()
    assertEquals(
      SearchDocumentKind.entries.toSet(),
      settings.searchIndexSettings().kinds(),
    )
    ReaderSettingsPolicy.validate(settings)
  }

  @Test
  fun kindsFollowTheFlags() {
    val settings = ReaderSettings(
      searchIndexSource = false,
      searchIndexNotes = true,
      searchIndexOcr = false,
      searchIndexTranscripts = true,
    )
    assertEquals(
      setOf(SearchDocumentKind.NOTE, SearchDocumentKind.TRANSCRIPT),
      settings.searchIndexSettings().kinds(),
    )
    ReaderSettingsPolicy.validate(settings)
  }

  @Test
  fun sanitizeFallsBackToAllOnWhenEverythingIsOff() {
    val sanitized = ReaderSettingsPolicy.sanitize(
      ReaderSettings(
        searchIndexSource = false,
        searchIndexNotes = false,
        searchIndexOcr = false,
        searchIndexTranscripts = false,
      ),
    )
    assertTrue(sanitized.searchIndexSource)
    assertTrue(sanitized.searchIndexNotes)
    assertTrue(sanitized.searchIndexOcr)
    assertTrue(sanitized.searchIndexTranscripts)
    ReaderSettingsPolicy.validate(sanitized)
  }

  @Test(expected = IllegalArgumentException::class)
  fun validateRejectsEverythingOffBeforeSanitize() {
    ReaderSettingsPolicy.validate(
      ReaderSettings(
        searchIndexSource = false,
        searchIndexNotes = false,
        searchIndexOcr = false,
        searchIndexTranscripts = false,
      ),
    )
  }
}

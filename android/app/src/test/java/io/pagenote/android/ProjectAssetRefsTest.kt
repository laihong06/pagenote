package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectAssetRefsTest {
  @Test
  fun scansRefsAndReportsMissing() {
    val text = "# t\n\n![a](assets/123e4567-e89b-12d3-a456-426614174000.png)\n\n![b](assets/123e4567-e89b-12d3-a456-426614174001.png)\n"

    val refs = ProjectAssetRefs.scan(text)

    assertEquals(
      listOf("123e4567-e89b-12d3-a456-426614174000.png", "123e4567-e89b-12d3-a456-426614174001.png"),
      refs,
    )
    val missing = ProjectAssetRefs.missing(refs, exists = { it != "123e4567-e89b-12d3-a456-426614174001.png" })
    assertEquals(listOf("123e4567-e89b-12d3-a456-426614174001.png"), missing)
  }

  @Test
  fun emptyWhenNoRefs() {
    assertTrue(ProjectAssetRefs.scan("# plain\n").isEmpty())
  }

  @Test
  fun ignoresAttachmentScheme() {
    assertTrue(ProjectAssetRefs.scan("![a](/attachments/x.png)").isEmpty())
  }
}

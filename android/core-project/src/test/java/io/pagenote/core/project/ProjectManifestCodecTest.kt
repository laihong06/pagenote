package io.pagenote.core.project

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectManifestCodecTest {
  @Test
  fun decodesDesktopManifestAndIgnoresUnknownFields() {
    val manifest = ProjectManifestCodec.decode(
      """{"format":"page-notes-project","version":1,"id":"${"a".repeat(64)}","name":"Reader sample","created":"2026-10-17T00:00:00.000Z","left":"src/original.pdf","right":"src/right.md","leftName":"Original.pdf","futureField":true}""",
    )

    assertEquals("page-notes-project", manifest.format)
    assertEquals("Reader sample", manifest.name)
    assertEquals("src/original.pdf", manifest.left)
    assertEquals("src/right.md", manifest.right)
    assertEquals("Original.pdf", manifest.leftName)
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsNonObjectJson() {
    ProjectManifestCodec.decode("[]")
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsMissingRequiredFields() {
    ProjectManifestCodec.decode("""{"format":"pagenote-project","version":1}""")
  }
}

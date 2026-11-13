package io.pagenote.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderProjectIdentityTest {
  @Test
  fun savedReaderResultOnlyAppliesToTheSameProjectAndDirectory() {
    assertTrue(
      readerProjectIdentityMatches(
        expectedProjectId = "project-a",
        expectedDirectoryUri = "content://tree/one",
        currentProjectId = "project-a",
        currentDirectoryUri = "content://tree/one",
      ),
    )
    assertFalse(
      readerProjectIdentityMatches(
        expectedProjectId = "project-a",
        expectedDirectoryUri = "content://tree/one",
        currentProjectId = "project-b",
        currentDirectoryUri = "content://tree/two",
      ),
    )
    assertFalse(
      readerProjectIdentityMatches(
        expectedProjectId = "project-a",
        expectedDirectoryUri = "content://tree/one",
        currentProjectId = "project-a",
        currentDirectoryUri = "content://tree/two",
      ),
    )
    assertFalse(
      readerProjectIdentityMatches(
        expectedProjectId = "project-a",
        expectedDirectoryUri = "content://tree/one",
        currentProjectId = null,
        currentDirectoryUri = null,
      ),
    )
  }
}

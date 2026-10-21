package io.pagenote.core.project

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProjectStoreTest {
  @Test
  fun rejectsUnsupportedManifestVersion() {
    val manifest = ProjectManifest(
      format = "pagenote-project",
      version = 2,
      id = "a".repeat(64),
      name = "sample",
      left = "src/original.pdf",
      right = "src/right.md",
    )
    try {
      ProjectValidation.validate(manifest)
      fail("expected unsupported manifest version rejection")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test
  fun rejectsProjectLinksThatEscapeSrc() {
    val manifest = ProjectManifest(
      format = "pagenote-project",
      version = 1,
      id = "a".repeat(64),
      name = "sample",
      left = "src/../../private.pdf",
      right = "src/right.md",
    )
    try {
      ProjectValidation.validate(manifest)
      fail("expected project link traversal rejection")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test
  fun saveNoteConflictsOnStaleRevision() {
    val dir = Files.createTempDirectory("pagenote-android").toFile()
    val right = java.io.File(dir, "right.md")
    right.writeText("# p1\n\n<!-- pagebreak -->\n\n# p2\n")
    val rev = PageNotes.revision("# p1")
    ProjectStore.saveNote(right, 1, "# p1 edited", rev)
    assertEquals("# p1 edited", ProjectStore.readNotes(right)[1])
    try {
      ProjectStore.saveNote(right, 1, "# stale", rev)
      fail("expected conflict")
    } catch (e: IllegalStateException) {
      // expected
    }
  }

  @Test
  fun newProjectManifestMatchesDesktopContract() {
    val manifest = ProjectStore.newManifest("demo")

    ProjectValidation.validate(manifest)
    assertEquals("pagenote-project", manifest.format)
    assertEquals(1, manifest.version)
    assertEquals(64, manifest.id.length)
    assertTrue(manifest.id.all { it in '0'..'9' || it in 'a'..'f' })
    assertEquals("demo", manifest.name)
    assertEquals("src/left.md", manifest.left)
    assertEquals("src/right.md", manifest.right)
  }

  @Test
  fun rejectsBadProjectName() {
    listOf("", "a/b", "a:b", "a*").forEach { name ->
      try {
        ProjectStore.newManifest(name)
        fail("expected bad name: $name")
      } catch (_: IllegalArgumentException) {
      }
    }
  }
}

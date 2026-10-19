package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class NewProjectManifestPolicyTest {
  @Test
  fun keepsMarkdownSourceWhenNoPdfWasSelected() {
    val manifest = NewProjectManifestPolicy.create("Study notes")

    assertEquals("src/left.md", manifest.left)
    assertEquals("src/right.md", manifest.right)
    assertEquals(null, manifest.leftName)
  }

  @Test
  fun mapsImportedPdfToProjectOwnedSourceAndPreservesDisplayName() {
    val manifest = NewProjectManifestPolicy.create("Blank worksheet", "whiteboard.pdf")

    assertEquals("src/document.pdf", manifest.left)
    assertEquals("src/right.md", manifest.right)
    assertEquals("whiteboard.pdf", manifest.leftName)
  }

  @Test
  fun supportsAndNormalizesPdfAndMarkdownExtensions() {
    assertEquals("pdf", NewProjectManifestPolicy.sourceExtension("SCAN.PDF"))
    assertEquals("md", NewProjectManifestPolicy.sourceExtension("notes.Md"))
    assertEquals("markdown", NewProjectManifestPolicy.sourceExtension("lesson.MARKDOWN"))
    assertNull(NewProjectManifestPolicy.sourceExtension("notes.txt"))
  }

  @Test
  fun suggestsSafeProjectNameFromSourceDisplayName() {
    assertEquals("Exam_ Notes", NewProjectManifestPolicy.suggestedProjectName("Exam: Notes.MARKDOWN"))
    assertEquals("Imported document", NewProjectManifestPolicy.suggestedProjectName(".pdf"))
  }

  @Test
  fun createsMarkdownManifestWithOriginalSourceName() {
    val manifest = NewProjectManifestPolicy.create("Exam notes", "Exam notes.markdown")

    assertEquals("src/document.markdown", manifest.left)
    assertEquals("src/right.md", manifest.right)
    assertEquals("Exam notes.markdown", manifest.leftName)
  }

  @Test
  fun rejectsUnsupportedSourceExtension() {
    assertThrows(IllegalArgumentException::class.java) {
      NewProjectManifestPolicy.create("Text import", "source.txt")
    }
  }
}

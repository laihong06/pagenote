package io.pagenote.android

import org.junit.Assert.assertEquals
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
}

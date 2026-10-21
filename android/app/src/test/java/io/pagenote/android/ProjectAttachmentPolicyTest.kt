package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectAttachmentPolicyTest {
  @Test
  fun acceptsOnlyDesktopAttachmentTypesAndUuidAssetNames() {
    listOf("pdf", "png", "jpg", "jpeg", "gif", "webp", "mp3", "mp4", "md", "markdown", "txt", "docx", "pptx")
      .forEach { extension ->
        assertEquals(extension, ProjectAttachmentPolicy.extensionForDisplayName("lecture.$extension"))
      }
    assertEquals(null, ProjectAttachmentPolicy.extensionForDisplayName("script.svg"))
    assertEquals(true, ProjectAttachmentPolicy.isSafeAssetName("01234567-89ab-cdef-0123-456789abcdef.png"))
    assertEquals(false, ProjectAttachmentPolicy.isSafeAssetName("../01234567-89ab-cdef-0123-456789abcdef.png"))
    assertEquals(false, ProjectAttachmentPolicy.isSafeAssetName("01234567-89ab-cdef-0123-456789abcdef.svg"))
  }

  @Test
  fun createsDesktopCompatibleImageReferenceOnlyForSafeImageAssets() {
    assertEquals(
      "![\\[diagram\\]](assets/01234567-89ab-cdef-0123-456789abcdef.png)",
      ProjectAttachmentPolicy.createImageReference(
        "[diagram]",
        "01234567-89ab-cdef-0123-456789abcdef.png",
      ),
    )
    assertThrows(IllegalArgumentException::class.java) {
      ProjectAttachmentPolicy.createImageReference("document", "01234567-89ab-cdef-0123-456789abcdef.pdf")
    }
  }

  @Test
  fun blocksDeletionForMarkdownAndPdfAnnotationReferences() {
    val image = "01234567-89ab-cdef-0123-456789abcdef.png"
    val note = "![scan](assets/$image)"
    val annotations = """{"document":{"2":[{"type":"image","url":"/attachments/$image"}]}}"""

    assertEquals(
      ProjectAttachmentPolicy.DeleteEligibility.REFERENCED,
      ProjectAttachmentPolicy.deletionEligibility(image, note, "{}"),
    )
    assertEquals(
      ProjectAttachmentPolicy.DeleteEligibility.REFERENCED,
      ProjectAttachmentPolicy.deletionEligibility(image, "", annotations),
    )
    assertEquals(
      ProjectAttachmentPolicy.DeleteEligibility.ALLOWED,
      ProjectAttachmentPolicy.deletionEligibility(image, "plain note", "{}"),
    )
  }

  @Test
  fun failsClosedWhenAnnotationReferencesCannotBeRead() {
    assertEquals(
      ProjectAttachmentPolicy.DeleteEligibility.UNKNOWN_REFERENCES,
      ProjectAttachmentPolicy.deletionEligibility(
        "01234567-89ab-cdef-0123-456789abcdef.png",
        "plain note",
        "{broken json",
      ),
    )
  }
}

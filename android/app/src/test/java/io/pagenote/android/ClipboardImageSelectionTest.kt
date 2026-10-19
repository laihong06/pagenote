package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardImageSelectionTest {
  @Test
  fun selectsFirstSupportedContentImageAndSkipsOtherClipboardItems() {
    val firstSupported = ClipboardImageCandidate("CONTENT://media/first", " IMAGE/PNG ; name=first.png ")
    val secondSupported = ClipboardImageCandidate("content://media/second", "image/webp")

    assertEquals(
      firstSupported,
      ClipboardImageSelection.firstSupported(
        listOf(
          ClipboardImageCandidate("content://media/text", "text/plain"),
          ClipboardImageCandidate("file:///sdcard/image.png", "image/png"),
          ClipboardImageCandidate("content:opaque-image", "image/png"),
          ClipboardImageCandidate("content:///missing-authority.png", "image/png"),
          ClipboardImageCandidate(null, "image/jpeg"),
          firstSupported,
          secondSupported,
        ),
      ),
    )
  }

  @Test
  fun derivesSafeFallbackNamesFromSupportedMimeTypesOnly() {
    assertEquals("clipboard-image.png", ClipboardImageSelection.fallbackDisplayName("image/png"))
    assertEquals("clipboard-image.jpg", ClipboardImageSelection.fallbackDisplayName(" IMAGE/JPEG; name=scan.jpg "))
    assertEquals("clipboard-image.jpg", ClipboardImageSelection.fallbackDisplayName("image/jpg"))
    assertEquals("clipboard-image.gif", ClipboardImageSelection.fallbackDisplayName("image/gif"))
    assertEquals("clipboard-image.webp", ClipboardImageSelection.fallbackDisplayName("image/webp"))
    assertNull(ClipboardImageSelection.fallbackDisplayName("image/svg+xml"))
  }
}

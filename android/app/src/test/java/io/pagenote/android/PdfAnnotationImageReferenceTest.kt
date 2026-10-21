package io.pagenote.android

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAnnotationImageReferenceTest {
  @Test
  fun acceptsOnlyDesktopAttachmentUrlsForSingleUuidRasterAssets() {
    val uuid = "01234567-89ab-cdef-0123-456789abcdef"
    listOf("png", "jpg", "jpeg", "gif", "webp").forEach { extension ->
      assertEquals(
        "$uuid.$extension",
        PdfAnnotationImageReference.assetName("/attachments/$uuid.$extension"),
      )
    }
  }

  @Test
  fun rejectsRemoteAbsoluteTraversalMalformedAndUnsupportedImageReferences() {
    listOf(
      "https://example.test/attachments/01234567-89ab-cdef-0123-456789abcdef.png",
      "http://example.test/image.png",
      "assets/01234567-89ab-cdef-0123-456789abcdef.png",
      "/assets/01234567-89ab-cdef-0123-456789abcdef.png",
      "file:///tmp/image.png",
      "/attachments/../image.png",
      "/attachments\\01234567-89ab-cdef-0123-456789abcdef.png",
      "/attachments/01234567-89ab-cdef-0123-456789abcdef.png/../other.png",
      "/attachments/not-a-uuid.png",
      "/attachments/01234567-89ab-cdef-0123-456789abcdef.svg",
      "/attachments/01234567-89ab-cdef-0123-456789abcdef.png?download=1",
      "/attachments/01234567-89ab-cdef-0123-456789ABCDEF.png",
    ).forEach { raw ->
      assertNull("must reject $raw", PdfAnnotationImageReference.assetName(raw))
    }
  }

  @Test
  fun overlayParserCarriesOnlyValidatedImageAssetNamesAndPositiveBounds() {
    val uuid = "01234567-89ab-cdef-0123-456789abcdef"
    val validPage = Json.parseToJsonElement(
      """[{"type":"image","color":"#185abd","width":3,"points":[[80,90],[430,265]],"url":"/attachments/$uuid.png"}]""",
    )
    val parsed = PdfAnnotationOverlayItem.fromPage(validPage).single()
    assertEquals(uuid + ".png", parsed.imageAssetName)
    assertEquals(listOf(80.0 to 90.0, 430.0 to 265.0), parsed.points)

    val remotePage = Json.parseToJsonElement(
      """[{"type":"image","color":"#185abd","width":3,"points":[[80,90],[430,265]],"url":"https://example.test/image.png"}]""",
    )
    val reversedPage = Json.parseToJsonElement(
      """[{"type":"image","color":"#185abd","width":3,"points":[[430,265],[80,90]],"url":"/attachments/$uuid.png"}]""",
    )
    assertTrue(PdfAnnotationOverlayItem.fromPage(remotePage).isEmpty())
    assertTrue(PdfAnnotationOverlayItem.fromPage(reversedPage).isEmpty())
  }
}

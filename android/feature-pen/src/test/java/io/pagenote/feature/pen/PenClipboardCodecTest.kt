package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PenClipboardCodecTest {
  private val source = PenStroke(
    type = "pen",
    color = "#185abd",
    width = 2.5,
    points = listOf(PenPoint(120f, 240f, 0.35f), PenPoint(180f, 300f, 0.8f)),
    extraFields = mapOf(
      "custom" to "kept",
      "layoutAnchor" to mapOf("blockId" to "desktop-only"),
    ),
  )

  @Test
  fun encodeUsesDesktopMarkerAndAnnotationsItemShape() {
    val payload = PenClipboardCodec.encode(source)

    assertTrue(payload.startsWith("PAGE_NOTES_ANNOTATION:\n"))
    assertTrue(payload.contains("\"type\":\"pen\""))
    assertTrue(payload.contains("\"pressures\":["))
  }

  @Test
  fun decodeTranslatesEveryPointAndPreservesPressureAndUnknownFields() {
    val decoded = PenClipboardCodec.decode(
      PenClipboardCodec.encode(source),
      destination = PenPoint(80f, 90f, 1f),
    )

    assertEquals(
      listOf(PenPoint(80f, 90f, 0.35f), PenPoint(140f, 150f, 0.8f)),
      decoded?.points,
    )
    assertEquals("kept", decoded?.extraFields?.get("custom"))
    assertTrue(decoded?.extraFields?.containsKey("layoutAnchor") == false)
  }

  @Test
  fun roundTripsPenAndHighlighterTypes() {
    listOf("pen", "highlight").forEach { type ->
      val item = source.copy(type = type)
      assertEquals(item.copy(extraFields = mapOf("custom" to "kept")), PenClipboardCodec.decode(
        PenClipboardCodec.encode(item),
        destination = item.points.first(),
      ))
    }
  }

  @Test
  fun acceptsDesktopPayloadWithoutPressureMetadata() {
    val payload = """PAGE_NOTES_ANNOTATION:
      {"type":"pen","color":"#185abd","width":2,"points":[[20,30],[40,60]]}
    """.trimIndent()

    val decoded = PenClipboardCodec.decode(payload, PenPoint(80f, 90f, 1f))

    assertEquals(listOf(PenPoint(80f, 90f, 1f), PenPoint(100f, 120f, 1f)), decoded?.points)
  }

  @Test
  fun rejectsUnmarkedMalformedUnsupportedAndOversizedPayloads() {
    assertNull(PenClipboardCodec.decode("plain text", PenPoint(80f, 90f, 1f)))
    assertNull(PenClipboardCodec.decode("PAGE_NOTES_ANNOTATION:\n{", PenPoint(80f, 90f, 1f)))
    val unsupported = "PAGE_NOTES_ANNOTATION:\n{\"type\":\"text\"}"
    assertNull(PenClipboardCodec.decode(unsupported, PenPoint(80f, 90f, 1f)))
    val vector = "PAGE_NOTES_ANNOTATION:\n{\"type\":\"line\"}"
    assertNull(PenClipboardCodec.decode(vector, PenPoint(80f, 90f, 1f)))
    assertNull(
      PenClipboardCodec.decode(
        "PAGE_NOTES_ANNOTATION:\n" + " ".repeat(PenClipboardCodec.MAX_PAYLOAD_CHARS),
        PenPoint(80f, 90f, 1f),
      ),
    )
  }

  @Test
  fun rejectsInvalidFieldsAndCoordinatesBeforeReturningAnItem() {
    val invalidColor = PenClipboardCodec.encode(source).replace("#185abd", "red")
    val invalidCoordinate = PenClipboardCodec.encode(source).replace("120.0", "2000000.0")

    assertNull(PenClipboardCodec.decode(invalidColor, PenPoint(80f, 90f, 1f)))
    assertNull(PenClipboardCodec.decode(invalidCoordinate, PenPoint(80f, 90f, 1f)))
  }

  @Test
  fun crossPageDecodeMatchesPlainDecodeInsideDestinationBounds() {
    val payload = PenClipboardCodec.encode(source)

    // 120+60=180 <= 1000 wide, 240+60=300 <= 1414 tall destination page.
    val decoded = PenClipboardCodec.decodeForPage(
      payload,
      destination = PenPoint(80f, 90f, 1f),
      destinationPageHeightUnits = 1414.2f,
    )

    assertEquals(
      PenClipboardCodec.decode(payload, PenPoint(80f, 90f, 1f)),
      decoded,
    )
  }

  @Test
  fun crossPageDecodeRejectsStrokesFallingOffAShorterDestinationPage() {
    // Translated tail lands at y=250 on a destination page only 200 tall.
    assertNull(
      PenClipboardCodec.decodeForPage(
        PenClipboardCodec.encode(source),
        destination = PenPoint(80f, 190f, 1f),
        destinationPageHeightUnits = 200f,
      ),
    )
    // Same page, but shifted so the whole stroke fits: accepted.
    val shifted = source.copy(points = listOf(PenPoint(120f, 40f, 0.35f), PenPoint(180f, 100f, 0.8f)))
    assertEquals(
      listOf(PenPoint(80f, 90f, 0.35f), PenPoint(140f, 150f, 0.8f)),
      PenClipboardCodec.decodeForPage(
        PenClipboardCodec.encode(shifted),
        destination = PenPoint(80f, 90f, 1f),
        destinationPageHeightUnits = 200f,
      )?.points,
    )
  }

  @Test
  fun crossPageDecodeCanClampIntoTheDestinationPageAndRevalidates() {
    val clamped = PenClipboardCodec.decodeForPage(
      PenClipboardCodec.encode(source),
      destination = PenPoint(990f, 190f, 1f),
      destinationPageHeightUnits = 200f,
      clampToPage = true,
    )

    // First point lands on the destination; the tail (translated to
    // 1050,250) clamps inside the 1000x200 page and passes validation.
    assertEquals(PenPoint(990f, 190f, 0.35f), clamped?.points?.first())
    assertEquals(PenPoint(1000f, 200f, 0.8f), clamped?.points?.last())
  }

  @Test
  fun crossPageDecodeRejectsBadHeightsAndBadPayloads() {
    val payload = PenClipboardCodec.encode(source)
    assertNull(
      PenClipboardCodec.decodeForPage(payload, PenPoint(80f, 90f, 1f), destinationPageHeightUnits = 0f),
    )
    assertNull(
      PenClipboardCodec.decodeForPage(payload, PenPoint(80f, 90f, 1f), destinationPageHeightUnits = -10f),
    )
    assertNull(
      PenClipboardCodec.decodeForPage(payload, PenPoint(80f, 90f, 1f), destinationPageHeightUnits = Float.NaN),
    )
    assertNull(
      PenClipboardCodec.decodeForPage("plain text", PenPoint(80f, 90f, 1f), destinationPageHeightUnits = 1000f),
    )
  }
}

package io.pagenote.android

import io.pagenote.feature.pen.PenPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAnnotationAreaEraserTest {
  @Test
  fun sweptEraserSplitsNativeRectangleAndPreservesOtherAnnotations() {
    val original = Json.parseToJsonElement(
      """[{"type":"rect","color":"#185abd","width":5,"points":[[220,250],[780,750]]},""" +
        """{"type":"text","color":"#185abd","width":5,"points":[[900,900]],"text":"keep"}]""",
    ) as JsonArray

    val erased = PdfAnnotationAreaEraser.erasePage(
      original,
      from = PenPoint(500f, 150f, 1f),
      to = PenPoint(500f, 850f, 1f),
      radius = 10f,
    )

    val split = erased.dropLast(1).map { it as JsonObject }
    assertTrue(split.size >= 2)
    assertTrue(split.all { it["type"]?.jsonPrimitive?.content == "line" })
    assertEquals(original.last(), erased.last())
    val cutTopEdge = split.none { line ->
      val coordinates = line["points"] as JsonArray
      val first = coordinates[0] as JsonArray
      val second = coordinates[1] as JsonArray
      first[1].jsonPrimitive.content.toFloat() == 250f &&
        second[1].jsonPrimitive.content.toFloat() == 250f &&
        first[0].jsonPrimitive.content.toFloat() < 500f && second[0].jsonPrimitive.content.toFloat() > 500f
    }
    assertTrue(cutTopEdge)
  }

  @Test
  fun erasingAwayFromNativeRectangleLeavesOriginalPageUntouched() {
    val original = Json.parseToJsonElement(
      """[{"type":"rect","color":"#185abd","width":5,"points":[[220,250],[780,750]]}]""",
    ) as JsonArray

    val erased = PdfAnnotationAreaEraser.erasePage(
      original,
      from = PenPoint(900f, 900f, 1f),
      to = PenPoint(950f, 950f, 1f),
      radius = 10f,
    )

    assertSame(original, erased)
  }

  @Test
  fun sweptEraserHandlesAllNativeVectorShapeKinds() {
    val original = Json.parseToJsonElement(
      """[{"type":"line","color":"#185abd","width":5,"points":[[220,500],[780,500]]},""" +
        """{"type":"arrow","color":"#185abd","width":5,"points":[[220,500],[780,500]]},""" +
        """{"type":"rect","color":"#185abd","width":5,"points":[[220,250],[780,750]]},""" +
        """{"type":"ellipse","color":"#185abd","width":5,"points":[[220,250],[780,750]]}]""",
    ) as JsonArray

    val erased = PdfAnnotationAreaEraser.erasePage(
      original,
      from = PenPoint(500f, 100f, 1f),
      to = PenPoint(500f, 900f, 1f),
      radius = 10f,
    )

    assertTrue(erased.size > original.size)
    assertTrue(erased.all { (it as JsonObject)["type"]?.jsonPrimitive?.content == "line" })
  }
}

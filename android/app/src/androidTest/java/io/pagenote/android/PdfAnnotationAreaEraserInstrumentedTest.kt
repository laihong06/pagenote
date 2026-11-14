package io.pagenote.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenStroke
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfAnnotationAreaEraserInstrumentedTest {
  @Test
  fun sweptEraserKeepsOneContinuousReferencePenMaterialPerShapeFragment() {
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

    val fragments = erased.previewFragments()
    assertTrue(fragments.size >= 2)
    assertTrue(fragments.all { it["type"]?.jsonPrimitive?.content == "pen" })
    assertTrue(fragments.all { it["pagenotePolyline"]?.jsonPrimitive?.content == "true" })
    assertTrue(fragments.all { (it["points"] as JsonArray).size >= 2 })
    assertEquals(original.last(), erased.last())
    assertTrue(fragments.none { fragment ->
      val points = (fragment["points"] as JsonArray).map { point ->
        val pair = point as JsonArray
        pair[0].jsonPrimitive.content.toFloat() to pair[1].jsonPrimitive.content.toFloat()
      }
      points.zipWithNext().any { (a, b) ->
        a.second == 250f && b.second == 250f && a.first < 500f && b.first > 500f
      }
    })
  }

  @Test
  fun referenceVectorPathIsSampledBeforePenSplitAndFragmentsStayContinuous() {
    val page = Json.parseToJsonElement(
      """[{"type":"line","color":"#185abd","width":5,"points":[[220,500],[780,500]]}]""",
    ) as JsonArray

    val erased = PdfAnnotationAreaEraser.erasePage(
      page,
      from = PenPoint(500f, 400f, 1f),
      to = PenPoint(500f, 600f, 1f),
      radius = 10f,
    )

    val fragments = erased.previewFragments()
    assertEquals(2, fragments.size)
    assertTrue(fragments.all { it["type"]?.jsonPrimitive?.content == "pen" })
    assertTrue(fragments.all { it["pagenotePolyline"]?.jsonPrimitive?.content == "true" })
    assertTrue(fragments.all { (it["width"] as JsonPrimitive).content.toFloat() == 4f })
    assertTrue(fragments.all { (it["points"] as JsonArray).size > 100 })
  }

  @Test
  fun committedFragmentsJoinCurrentInkAndDropTransientPreviewField() {
    val page = Json.parseToJsonElement(
      """[{"type":"rect","color":"#185abd","width":5,"points":[[220,250],[780,750]]}]""",
    ) as JsonArray
    val preview = PdfAnnotationAreaEraser.erasePage(
      page,
      PenPoint(500f, 150f, 1f),
      PenPoint(500f, 850f, 1f),
      10f,
    )
    val ink = PenStroke("pen", "#ff0000", 3.0, listOf(PenPoint(20f, 30f, 1f), PenPoint(40f, 50f, .8f)))

    val committed = PdfAnnotationAreaEraser.preparePageForCommit(preview, listOf(ink))
    val fragments = committed.filterIsInstance<JsonObject>().filter {
      it["pagenotePolyline"]?.jsonPrimitive?.content == "true"
    }

    assertTrue(fragments.size >= 2)
    assertTrue(fragments.all { it["type"]?.jsonPrimitive?.content == "pen" })
    assertTrue(fragments.none { it.containsKey(PdfAnnotationAreaEraser.PREVIEW_FIELD) })
    assertTrue(committed.any { (it as? JsonObject)?.get("color")?.jsonPrimitive?.content == "#ff0000" })
    assertFalse(committed.any { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "rect" })
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
  fun referenceAreaEraserSplitsEveryNativeVectorShapeKind() {
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

    val fragments = erased.previewFragments()
    assertTrue(erased.size > original.size)
    assertTrue(fragments.isNotEmpty())
    assertTrue(fragments.all { it["type"]?.jsonPrimitive?.content == "pen" })
  }

  private fun JsonArray.previewFragments(): List<JsonObject> = filterIsInstance<JsonObject>()
    .filter { it[PdfAnnotationAreaEraser.PREVIEW_FIELD]?.jsonPrimitive?.content == "true" }
}

package io.pagenote.android

import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenRect
import io.pagenote.feature.pen.PenStroke
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InkOcrPipelineTest {
  private val aspect = 1.414 // A4 portrait pageHeightPx/pageWidthPx

  private fun penStroke(vararg points: Pair<Float, Float>, type: String = "pen") = PenStroke(
    type = type,
    color = "#123456",
    width = 2.0,
    points = points.map { PenPoint(it.first, it.second, 0.8f) },
  )

  private fun pageOf(vararg items: JsonObject): JsonArray = JsonArray(items.toList())

  private fun point(x: Double, y: Double): JsonArray =
    JsonArray(listOf(JsonPrimitive(x), JsonPrimitive(y)))

  private fun penItem(vararg points: Pair<Double, Double>, type: String = "pen"): JsonObject =
    buildJsonObject {
      put("type", type)
      put("color", "#123456")
      put("width", 2.0)
      put("points", JsonArray(points.map { pair -> point(pair.first, pair.second) }))
      put("pressures", JsonArray(points.map { JsonPrimitive(1.0) }))
    }

  private fun rectItem(): JsonObject = buildJsonObject {
    put("type", "rect")
    put("color", "#123456")
    put("width", 1.0)
    put("points", JsonArray(listOf(point(0.0, 0.0), point(5.0, 5.0))))
  }

  private val textItem: JsonObject = buildJsonObject {
    put("type", "text")
    put("text", "hi")
  }

  // --- 几何 ------------------------------------------------------------

  @Test
  fun documentRectScalesNormalizedYByAspect() {
    val rect = InkOcrSelection.documentRect(NormalizedOcrRegion(100, 200, 300, 400), aspect)
    assertEquals(PenRect(100.0, 200.0 * 1.414, 300.0, 400.0 * 1.414), rect)
  }

  @Test
  fun documentRectNormalizesReversedCorners() {
    val rect = InkOcrSelection.documentRect(NormalizedOcrRegion(300, 400, 100, 200), aspect)
    assertEquals(100.0, rect.left, 1e-9)
    assertEquals(200.0 * 1.414, rect.top, 1e-9)
  }

  @Test
  fun selectStrokeIndexesKeepsOnlyInkInsideOrCrossingTheRegion() {
    val region = NormalizedOcrRegion(100, 100, 500, 300)
    // 选区文档矩形：x 100..500，y 141..424（归一化 y×1.414）。
    val strokes = listOf(
      penStroke(150f to 150f, 200f to 160f),                     // 0 inside
      penStroke(900f to 900f, 950f to 950f),                     // 1 far outside
      penStroke(0f to 300f, 600f to 300f),                       // 2 crossing horizontally
      penStroke(400f to 200f, 450f to 210f, type = "highlight"), // 3 highlight inside
      penStroke(50f to 50f, 60f to 60f),                         // 4 outside
    )
    assertEquals(listOf(0, 2, 3), InkOcrSelection.selectStrokeIndexes(strokes, region, aspect))
  }

  @Test
  fun selectItemIndexesIgnoresNonInkItems() {
    val region = NormalizedOcrRegion(0, 0, 1000, 1000)
    val page = pageOf(
      penItem(10.0 to 10.0, 20.0 to 20.0),
      rectItem(),
      penItem(2000.0 to 2000.0, 2100.0 to 2100.0),
    )
    assertEquals(listOf(0), InkOcrSelection.selectItemIndexes(page, region, aspect))
  }

  // --- 指纹 ------------------------------------------------------------

  @Test
  fun canonicalKeyIgnoresNumberFormattingAndKeyOrder() {
    val fromPenModel = mapOf(
      "type" to "pen",
      "color" to "#123456",
      "width" to 2.0,
      "points" to listOf(listOf(10.0, 20.0)),
      "pressures" to listOf(1.0),
    )
    val fromDisk = mapOf(
      "points" to listOf(listOf(10.0, 20.0)),
      "pressures" to listOf(1.0),
      "width" to 2.0,
      "color" to "#123456",
      "type" to "pen",
    )
    assertEquals(canonicalItemKey(fromPenModel), canonicalItemKey(fromDisk))
    // 3 vs 3.0 的整型化差异不应改变指纹。
    assertEquals(
      canonicalItemKey(mapOf("width" to 3.0)),
      canonicalItemKey(mapOf("width" to 3)),
    )
  }

  @Test
  fun canonicalKeyDistinguishesDifferentContent() {
    val a = mapOf("type" to "pen", "points" to listOf(listOf(1.0, 2.0)))
    val b = mapOf("type" to "pen", "points" to listOf(listOf(1.0, 3.0)))
    assertFalse(canonicalItemKey(a) == canonicalItemKey(b))
  }

  // --- 页变换 ----------------------------------------------------------

  @Test
  fun convertPageRemovesFingerprintedInkAndAppendsProduct() {
    val inkKept = penItem(900.0 to 900.0, 950.0 to 950.0)
    val inkConverted = penItem(10.0 to 10.0, 20.0 to 20.0)
    val page = pageOf(inkConverted, inkKept)
    val fingerprint = canonicalItemKey(annotationPageToRawList(JsonArray(listOf(inkConverted))).first())

    val (after, removed) = InkOcrConversion.convertPage(page, setOf(fingerprint), textItem)

    assertEquals(1, removed)
    assertEquals(2, after.size)
    assertEquals(inkKept, after[0])
    assertEquals(textItem, after[1])
  }

  @Test
  fun convertPageNeverRemovesNonInkItemsEvenWithCollidingFingerprint() {
    val page = pageOf(rectItem())
    val fingerprint = canonicalItemKey(annotationPageToRawList(page).first())
    val (after, removed) = InkOcrConversion.convertPage(page, setOf(fingerprint), textItem)
    assertEquals(0, removed)
    assertEquals(rectItem(), after[0])
    assertEquals(textItem, after[1])
  }

  @Test
  fun convertPageWithEmptyFingerprintsOnlyAppends() {
    val page = pageOf(penItem(1.0 to 1.0, 2.0 to 2.0))
    val (after, removed) = InkOcrConversion.convertPage(page, emptySet(), textItem)
    assertEquals(0, removed)
    assertEquals(2, after.size)
  }

  @Test
  fun annotationPageToRawListNormalizesIntegralDoublesForFingerprinting() {
    // 磁盘侧可能写 2（org.json 整型化）或 2.0（kotlinx）；两侧 raw 化后指纹必须一致。
    val decimal = penItem(10.0 to 20.0)
    val integral = buildJsonObject {
      put("type", "pen")
      put("color", "#123456")
      put("width", JsonPrimitive(2))
      put(
        "points",
        JsonArray(listOf(JsonArray(listOf(JsonPrimitive(10), JsonPrimitive(20))))),
      )
      put("pressures", JsonArray(listOf(JsonPrimitive(1))))
    }
    val rawDecimal = annotationPageToRawList(JsonArray(listOf(decimal))).single()
    val rawIntegral = annotationPageToRawList(JsonArray(listOf(integral))).single()
    // raw 形态：points 为嵌套 List，数值为 Double。
    assertEquals(listOf(listOf(10.0, 20.0)), rawDecimal["points"])
    assertEquals(canonicalItemKey(rawDecimal), canonicalItemKey(rawIntegral))
  }

  // --- 文本项 ----------------------------------------------------------

  @Test
  fun buildTextItemAnchorsAtRegionWithClampedDerivedFontSize() {
    val item = InkOcrConversion.buildTextItem(
      region = NormalizedOcrRegion(100, 200, 500, 250),
      aspect = 1.414,
      text = "  手写内容  \n",
      color = "#123456",
      width = 2.0,
    )
    assertNotNull(item)
    item!!
    assertEquals("text", item["type"]!!.jsonPrimitive.content)
    val points = item["points"] as JsonArray
    val anchor = points[0] as JsonArray
    assertEquals(100.0, anchor[0].jsonPrimitive.doubleOrNull!!, 1e-9)
    assertEquals(200.0 * 1.414, anchor[1].jsonPrimitive.doubleOrNull!!, 1e-9)
    assertEquals("手写内容", item["text"]!!.jsonPrimitive.content)
    val fontSize = item["fontSize"]!!.jsonPrimitive.doubleOrNull!!
    assertTrue("fontSize in schema range: $fontSize", fontSize in 20.0..500.0)
    assertEquals("left", item["align"]!!.jsonPrimitive.content)
  }

  @Test
  fun buildTextItemRejectsBlankText() {
    assertNull(
      InkOcrConversion.buildTextItem(
        NormalizedOcrRegion(0, 0, 100, 100), 1.0, "  \n ", "#123456", 2.0,
      ),
    )
  }
}

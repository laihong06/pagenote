package io.pagenote.feature.annotations

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 拖拽/缩放数据（D7）测试：平移/缩放/旋转/镜像、命中测试、item 读写保留未知字段。 */
class AnnotationImageTransformTest {
  private fun item(angle: Double? = null, flipX: Boolean? = null, flipY: Boolean? = null): JsonObject =
    buildJsonObject {
      put("type", "image")
      put("color", "#185abd")
      put("width", 1.0)
      put("url", "/attachments/0123abcd-0000-4000-8000-000000000000.png")
      put("points", kotlinx.serialization.json.buildJsonArray {
        add(kotlinx.serialization.json.buildJsonArray { add(100.0); add(100.0) })
        add(kotlinx.serialization.json.buildJsonArray { add(300.0); add(200.0) })
      })
      put("sticker", "pack1/cat.png")
      put("layoutAnchor", "keep-me")
      angle?.let { put("angle", it) }
      flipX?.let { put("flipX", it) }
      flipY?.let { put("flipY", it) }
    }

  @Test
  fun fromItemReadsPoseAndBounds() {
    val parsed = AnnotationImageTransform.fromItem(item(angle = 90.0, flipX = true))
    assertNotNull(parsed)
    assertEquals(200.0, parsed!!.bounds.width(), 0.0)
    assertEquals(100.0, parsed.bounds.height(), 0.0)
    assertEquals(90.0, parsed.angleDegrees, 0.0)
    assertTrue(parsed.flipX)
    assertFalse(parsed.flipY)
  }

  @Test
  fun fromItemRejectsNonImageItems() {
    val textItem = buildJsonObject {
      put("type", "text")
      put("color", "#000000")
      put("width", 1.0)
      put("points", kotlinx.serialization.json.buildJsonArray {
        add(kotlinx.serialization.json.buildJsonArray { add(0.0); add(0.0) })
      })
      put("text", "hi")
    }
    assertNull(AnnotationImageTransform.fromItem(textItem))
  }

  @Test
  fun moveTranslatesBothCorners() {
    val parsed = AnnotationImageTransform.fromItem(item())!!
    val moved = parsed.moved(50.0, -25.0)
    assertEquals(150.0, moved.bounds.left, 0.0)
    assertEquals(75.0, moved.bounds.top, 0.0)
    assertEquals(350.0, moved.bounds.right, 0.0)
    assertEquals(175.0, moved.bounds.bottom, 0.0)
  }

  @Test
  fun scaleAroundCenterKeepsCenterAndClampsDegenerate() {
    val parsed = AnnotationImageTransform.fromItem(item())!!
    val scaled = parsed.scaled(1.5)!!
    assertEquals(200.0, scaled.bounds.centerX(), 1e-9)
    assertEquals(150.0, scaled.bounds.centerY(), 1e-9)
    assertEquals(300.0, scaled.bounds.width(), 0.0)
    assertEquals(150.0, scaled.bounds.height(), 0.0)

    // 缩到最小边以下 → 拒绝。
    assertNull(parsed.scaled(0.02))
    // 非法因子 → 抛 IllegalArgumentException。
    var threw = false
    try {
      parsed.scaled(-1.0)
    } catch (_: IllegalArgumentException) {
      threw = true
    }
    assertTrue(threw)
  }

  @Test
  fun scaleAroundFirstCornerPinsCorner() {
    val parsed = AnnotationImageTransform.fromItem(item())!!
    val scaled = parsed.scaled(2.0, AnnotationImageTransform.ScaleAnchor.FIRST_CORNER)!!
    assertEquals(100.0, scaled.bounds.left, 0.0)
    assertEquals(100.0, scaled.bounds.top, 0.0)
    assertEquals(500.0, scaled.bounds.right, 0.0)
    assertEquals(300.0, scaled.bounds.bottom, 0.0)
  }

  @Test
  fun rotateAccumulatesAndNormalizes() {
    val parsed = AnnotationImageTransform.fromItem(item())!!
    assertEquals(90.0, parsed.rotated(90.0).angleDegrees, 0.0)
    assertEquals(180.0, parsed.rotated(180.0).angleDegrees, 0.0)
    // 规范域 (-180, 180]：190 → -170。
    assertEquals(-170.0, parsed.rotated(190.0).angleDegrees, 0.0)
    // 连续旋转：190 → -170，再 -20 → 规范回 170。
    assertEquals(170.0, parsed.rotated(190.0).rotated(-20.0).angleDegrees, 0.0)
  }

  @Test
  fun flipTogglesFlags() {
    val parsed = AnnotationImageTransform.fromItem(item())!!
    val flipped = parsed.flipped(AnnotationImageTransform.FlipAxis.X).flipped(AnnotationImageTransform.FlipAxis.Y)
    assertTrue(flipped.flipX)
    assertTrue(flipped.flipY)
    assertFalse(flipped.flipped(AnnotationImageTransform.FlipAxis.X).flipX)
  }

  @Test
  fun hitTestAccountsForRotation() {
    val parsed = AnnotationImageTransform.fromItem(item())!!
    // 未旋转：中心命中，远处未命中。
    assertTrue(parsed.hitTest(200.0, 150.0))
    assertFalse(parsed.hitTest(1000.0, 1000.0))

    // 旋转 90°后：矩形变为 100 宽 × 200 高，x∈[150,250]、y∈[50,250]。
    val rotated = parsed.rotated(90.0)
    // 中心仍在。
    assertTrue(rotated.hitTest(200.0, 150.0))
    // 贴右缘（x=240 ≤ 250）命中；(260, 150) 在旋转矩形外。
    assertTrue(rotated.hitTest(240.0, 150.0))
    assertFalse(rotated.hitTest(260.0, 150.0))
    // (200, 60) 在旋转矩形内但原矩形外（y=60 < 100）。
    assertFalse(parsed.hitTest(200.0, 60.0))
    assertTrue(rotated.hitTest(200.0, 60.0))
    // 容差放宽后命中（260 - 250 = 10 ≤ 20）。
    assertTrue(rotated.hitTest(260.0, 150.0, tolerance = 20.0))
  }

  @Test
  fun applyToItemRewritesPoseAndPreservesUnknownFields() {
    val parsed = AnnotationImageTransform.fromItem(item(angle = 30.0))!!
    val moved = parsed.moved(10.0, 10.0).rotated(60.0).flipped(AnnotationImageTransform.FlipAxis.Y)

    val updated = moved.applyToItem(item(angle = 30.0))
    assertEquals(90.0, (updated[StickerAnnotation.ANGLE_FIELD] as JsonPrimitive).content.toDouble(), 0.0)
    assertEquals(true, (updated[StickerAnnotation.FLIP_Y_FIELD] as JsonPrimitive).content == "true")
    assertFalse(updated.containsKey(StickerAnnotation.FLIP_X_FIELD))
    // canonical / 来源 / 未知字段保留。
    assertEquals("image", (updated["type"] as JsonPrimitive).content)
    assertEquals("pack1/cat.png", (updated[StickerAnnotation.STICKER_FIELD] as JsonPrimitive).content)
    assertEquals("keep-me", (updated["layoutAnchor"] as JsonPrimitive).content)
    // 平移 +10/+10 已写入 points。
    val points = updated["points"].toString()
    assertTrue(points.contains("110.0"))
  }

  @Test
  fun applyToItemClearsPoseFieldsWhenReset() {
    val parsed = AnnotationImageTransform.fromItem(item(angle = 30.0, flipX = true))!!
    val reset = AnnotationImageTransform(
      bounds = parsed.bounds,
      angleDegrees = 0.0,
      flipX = false,
      flipY = false,
    )
    val updated = reset.applyToItem(item(angle = 30.0, flipX = true))
    assertFalse(updated.containsKey("angle"))
    assertFalse(updated.containsKey("flipX"))
    assertFalse(updated.containsKey("flipY"))
    // 几何照写（往返核对）。
    val reparsedReset = AnnotationImageTransform.fromItem(updated)!!
    assertEquals(parsed.bounds.left, reparsedReset.bounds.left, 1e-9)
    assertEquals(0.0, reparsedReset.angleDegrees, 0.0)
  }

  @Test
  fun applyToItemRoundTripsThroughFromItem() {
    val parsed = AnnotationImageTransform.fromItem(item())!!
    val moved = parsed.moved(7.5, -3.25).scaled(1.25)!!.rotated(-337.0).flipped(AnnotationImageTransform.FlipAxis.X)
    val updated = moved.applyToItem(item())
    val reparsed = AnnotationImageTransform.fromItem(updated)!!
    assertEquals(moved.bounds.left, reparsed.bounds.left, 1e-9)
    assertEquals(moved.bounds.top, reparsed.bounds.top, 1e-9)
    assertEquals(moved.angleDegrees, reparsed.angleDegrees, 0.0)
    assertEquals(moved.flipX, reparsed.flipX)
    assertEquals(moved.flipY, reparsed.flipY)
  }
}

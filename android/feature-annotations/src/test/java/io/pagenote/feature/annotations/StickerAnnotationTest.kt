package io.pagenote.feature.annotations

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 贴纸对象落盘 schema（D7）测试：canonical image 项 + 扩展字段透传、
 * 写侧严格 / 读侧容错、GIF 派生、摆放几何。
 */
class StickerAnnotationTest {
  private val asset = "0123abcd-0000-4000-8000-000000000000.png"
  private val gifAsset = "0123abcd-0000-4000-8000-000000000009.gif"

  @Test
  fun createWritesCanonicalImageItemWithTwoPointBbox() {
    val sticker = StickerAnnotation.create(
      assetName = asset,
      first = 80.0 to 100.0,
      second = 430.0 to 350.0,
      color = "#185ABD",
      width = 2.0,
      stickerSource = "pack1/cat.png",
    )!!

    val item = sticker.toItem()
    assertEquals("image", item.str("type"))
    assertEquals("#185abd", item.str("color"))
    assertEquals(2.0, item.num("width"), 0.0)
    assertEquals("/attachments/$asset", item.str("url"))
    assertEquals(
      listOf(listOf(80.0, 100.0), listOf(430.0, 350.0)),
      (item["points"] as JsonArray).map { point ->
        (point as JsonArray).map { (it as JsonPrimitive).content.toDouble() }
      },
    )
    assertEquals("pack1/cat.png", item.str("sticker"))
  }

  @Test
  fun defaultPoseWritesNoExtensionFields() {
    val sticker = StickerAnnotation.create(
      assetName = asset,
      first = 0.0 to 0.0,
      second = 50.0 to 50.0,
      color = "#000000",
      width = 1.0,
    )!!

    val item = sticker.toItem()
    assertFalse(item.containsKey(StickerAnnotation.STICKER_FIELD))
    assertFalse(item.containsKey(StickerAnnotation.ANGLE_FIELD))
    assertFalse(item.containsKey(StickerAnnotation.FLIP_X_FIELD))
    assertFalse(item.containsKey(StickerAnnotation.FLIP_Y_FIELD))
  }

  @Test
  fun poseFieldsRideAlongAndBboxIsNormalized() {
    val sticker = StickerAnnotation.create(
      assetName = asset,
      first = 430.0 to 350.0,
      second = 80.0 to 100.0,
      color = "#000000",
      width = 1.0,
      angleDegrees = 30.0,
      flipX = true,
    )!!

    val item = sticker.toItem()
    assertEquals(30.0, item.num("angle"), 0.0)
    assertEquals(true, item.bool(StickerAnnotation.FLIP_X_FIELD))
    assertFalse(item.containsKey(StickerAnnotation.FLIP_Y_FIELD))
    // 对角两点归一化为左上/右下。
    assertEquals(
      listOf(80.0, 100.0),
      ((item["points"] as JsonArray)[0] as JsonArray).map { (it as JsonPrimitive).content.toDouble() },
    )
  }

  @Test
  fun angleNormalizesIntoMinus180To180() {
    assertEquals(0.0, StickerAnnotation.normalizeAngle(360.0), 0.0)
    assertEquals(0.0, StickerAnnotation.normalizeAngle(-360.0), 0.0)
    assertEquals(-170.0, StickerAnnotation.normalizeAngle(190.0), 0.0)
    assertEquals(180.0, StickerAnnotation.normalizeAngle(180.0), 0.0)
    assertEquals(90.0, StickerAnnotation.normalizeAngle(450.0), 0.0)
  }

  @Test
  fun createRejectsInvalidInputs() {
    // 坏资产名 / 坏颜色 / 坏宽度。
    assertNull(
      StickerAnnotation.create("cat.png", 0.0 to 0.0, 40.0 to 40.0, "#000000", 1.0),
    )
    assertNull(
      StickerAnnotation.create(asset, 0.0 to 0.0, 40.0 to 40.0, "#12345", 1.0),
    )
    assertNull(
      StickerAnnotation.create(asset, 0.0 to 0.0, 40.0 to 40.0, "#000000", 0.0),
    )
    // 零/小于最小边 10 的 bbox。
    assertNull(
      StickerAnnotation.create(asset, 0.0 to 0.0, 9.9 to 50.0, "#000000", 1.0),
    )
    assertNull(
      StickerAnnotation.create(asset, 10.0 to 10.0, 10.0 to 10.0, "#000000", 1.0),
    )
    // 非法来源（绝对路径 / 穿越）与越界角度。
    assertNull(
      StickerAnnotation.create(asset, 0.0 to 0.0, 40.0 to 40.0, "#000000", 1.0, stickerSource = "/abs"),
    )
    assertNull(
      StickerAnnotation.create(asset, 0.0 to 0.0, 40.0 to 40.0, "#000000", 1.0, stickerSource = "../up"),
    )
    assertNull(
      StickerAnnotation.create(asset, 0.0 to 0.0, 40.0 to 40.0, "#000000", 1.0, angleDegrees = 361.0),
    )
    // 坐标越界。
    assertNull(
      StickerAnnotation.create(asset, 0.0 to 0.0, 1_000_001.0 to 50.0, "#000000", 1.0),
    )
  }

  @Test
  fun fromItemRoundTripsAndKeepsUnknownFieldsUntouched() {
    val item = buildJsonObject {
      put("type", "image")
      put("color", "#000000")
      put("width", 1.5)
      put("url", "/attachments/$asset")
      put("points", buildJsonArray {
        add(buildJsonArray { add(10.0); add(20.0) })
        add(buildJsonArray { add(200.0); add(260.0) })
      })
      put("sticker", "pack1/cat.png")
      put("angle", -45.0)
      put("flipY", true)
      put("layoutAnchor", "keep-me")
    }

    val sticker = StickerAnnotation.fromItem(item)!!
    assertEquals(asset, sticker.assetName)
    assertEquals("pack1/cat.png", sticker.stickerSource)
    assertEquals(-45.0, sticker.angleDegrees, 0.0)
    assertFalse(sticker.flipX)
    assertTrue(sticker.flipY)

    // 序列化往返：未知字段由调用方（页面合并器）保留，本 schema 只管 canonical+扩展。
    val out = sticker.toItem()
    assertEquals("pack1/cat.png", out.str("sticker"))
    assertEquals(-45.0, out.num("angle"), 0.0)
    assertEquals(true, out.bool(StickerAnnotation.FLIP_Y_FIELD))
  }

  @Test
  fun fromItemDegradesInvalidExtensionValuesInsteadOfFailing() {
    val item = buildJsonObject {
      put("type", "image")
      put("color", "#000000")
      put("width", 1.0)
      put("url", "/attachments/$asset")
      put("points", buildJsonArray {
        add(buildJsonArray { add(0.0); add(0.0) })
        add(buildJsonArray { add(50.0); add(50.0) })
      })
      put("angle", 720.0) // 越界：降级 0
      put("flipX", "yes") // 非布尔：降级 false
      put("sticker", "") // 空串：视为无来源
    }

    val sticker = StickerAnnotation.fromItem(item)!!
    assertEquals(0.0, sticker.angleDegrees, 0.0)
    assertFalse(sticker.flipX)
    assertNull(sticker.stickerSource)
  }

  @Test
  fun fromItemRejectsNonImageOrMalformedCanonicalItems() {
    // 非 image 项。
    assertNull(
      StickerAnnotation.fromItem(
        buildJsonObject {
          put("type", "text")
          put("color", "#000000")
          put("width", 1.0)
          put("text", "hi")
        },
      ),
    )
    // 非 2 点。
    assertNull(
      StickerAnnotation.fromItem(
        buildJsonObject {
          put("type", "image")
          put("color", "#000000")
          put("width", 1.0)
          put("url", "/attachments/$asset")
          put("points", buildJsonArray { add(buildJsonArray { add(0.0); add(0.0) }) })
        },
      ),
    )
    // url 不指向合法附件名。
    assertNull(
      StickerAnnotation.fromItem(
        buildJsonObject {
          put("type", "image")
          put("color", "#000000")
          put("width", 1.0)
          put("url", "https://example.com/cat.png")
          put("points", buildJsonArray {
            add(buildJsonArray { add(0.0); add(0.0) })
            add(buildJsonArray { add(50.0); add(50.0) })
          })
        },
      ),
    )
  }

  @Test
  fun isAnimatedIsDerivedFromUrlSuffix() {
    assertTrue(StickerAnnotation.isAnimatedUrl("/attachments/$gifAsset"))
    assertFalse(StickerAnnotation.isAnimatedUrl("/attachments/$asset"))
    val gifSticker = StickerAnnotation.create(
      assetName = gifAsset,
      first = 0.0 to 0.0,
      second = 100.0 to 100.0,
      color = "#000000",
      width = 1.0,
    )!!
    assertTrue(StickerAnnotation.isAnimatedUrl(StickerAnnotation.urlForAssetName(gifSticker.assetName)))
  }

  @Test
  fun placementBoundsKeepsAspectRatioAndCenter() {
    val (first, second) = StickerAnnotation.placementBounds(
      pixelWidth = 200,
      pixelHeight = 100,
      centerX = 500.0,
      centerY = 400.0,
      targetWidth = 350.0,
    )!!

    val bounds = StickerAnnotation.bounds(first, second)
    assertEquals(350.0, bounds.width(), 0.0)
    assertEquals(175.0, bounds.height(), 0.0)
    assertEquals(500.0, bounds.centerX(), 0.0)
    assertEquals(400.0, bounds.centerY(), 0.0)
  }

  @Test
  fun placementBoundsRejectsDegenerateGeometry() {
    // 目标宽过小 → 高不足最小边。
    assertNull(
      StickerAnnotation.placementBounds(2000, 100, 500.0, 400.0, 10.0),
    )
    assertNull(StickerAnnotation.placementBounds(0, 100, 500.0, 400.0, 350.0))
    assertNull(StickerAnnotation.placementBounds(100, 100, Double.NaN, 400.0, 350.0))
  }

  @Test
  fun itemPassesAnnotationValidationMirror() {
    // D7 落盘项必须能通过桌面校验镜像（AnnotationValidation）。
    val sticker = StickerAnnotation.create(
      assetName = asset,
      first = 0.0 to 0.0,
      second = 350.0 to 200.0,
      color = "#000000",
      width = 1.0,
      stickerSource = "cat.png",
      angleDegrees = 15.0,
    )!!
    AnnotationValidation.validateItems(JsonArray(listOf(sticker.toItem())))
  }
}

private fun JsonObject.str(key: String): String = (this[key] as JsonPrimitive).content
private fun JsonObject.num(key: String): Double = (this[key] as JsonPrimitive).content.toDouble()
private fun JsonObject.bool(key: String): Boolean = (this[key] as JsonPrimitive).content == "true"

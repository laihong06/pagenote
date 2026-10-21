package io.pagenote.android

import io.pagenote.feature.pen.PenStyle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object PdfImageAnnotationDraft {
  private const val VISIBLE_TOP_INSET_DP = 80.0
  private const val MINIMUM_TOP_Y = 90.0
  private const val IMAGE_WIDTH = 350.0
  private const val MAX_COORDINATE = 1_000_000.0
  private val assetNamePattern = Regex(
    "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}\\.(png|jpg|jpeg|gif|webp)$",
  )

  fun visibleTopY(
    bounds: PdfViewportBounds,
    transform: PdfViewportTransform,
    density: Float,
  ): Double? {
    val values = listOf(
      bounds.viewportWidthPx,
      bounds.viewportHeightPx,
      bounds.pageWidthPx,
      bounds.pageHeightPx,
      transform.scale,
      transform.translationX,
      transform.translationY,
      density,
    )
    if (values.any { !it.isFinite() } ||
      bounds.viewportWidthPx <= 0f || bounds.viewportHeightPx <= 0f ||
      bounds.pageWidthPx <= 0f || bounds.pageHeightPx <= 0f ||
      transform.scale <= 0f || density <= 0f
    ) {
      return null
    }

    val screenY = VISIBLE_TOP_INSET_DP * density
    val pageY = (screenY - bounds.viewportHeightPx / 2.0 - transform.translationY) /
      transform.scale + bounds.pageHeightPx / 2.0
    val annotationY = pageY / bounds.pageWidthPx * 1000.0
    if (!annotationY.isFinite()) return null
    return maxOf(MINIMUM_TOP_Y, annotationY).takeIf { it <= MAX_COORDINATE }
  }

  fun create(
    assetName: String,
    pixelWidth: Int,
    pixelHeight: Int,
    visibleTopY: Double,
    style: PenStyle,
  ): JsonObject? {
    if (!assetNamePattern.matches(assetName) ||
      pixelWidth <= 0 || pixelHeight <= 0 ||
      pixelWidth.toLong() * pixelHeight > ProjectImageImporter.MAX_IMAGE_PIXELS ||
      !visibleTopY.isFinite() || visibleTopY < MINIMUM_TOP_Y || visibleTopY > MAX_COORDINATE
    ) {
      return null
    }

    val imageHeight = IMAGE_WIDTH * pixelHeight / pixelWidth
    val bottomY = visibleTopY + imageHeight
    if (!imageHeight.isFinite() || imageHeight <= 0.0 || !bottomY.isFinite() || bottomY > MAX_COORDINATE) {
      return null
    }

    return buildJsonObject {
      put("type", "image")
      put("color", style.color)
      put("width", style.width)
      put("url", "/attachments/$assetName")
      put("points", buildJsonArray {
        add(buildJsonArray {
          add(80.0)
          add(visibleTopY)
        })
        add(buildJsonArray {
          add(80.0 + IMAGE_WIDTH)
          add(bottomY)
        })
      })
    }
  }
}

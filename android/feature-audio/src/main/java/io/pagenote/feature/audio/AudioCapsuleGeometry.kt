package io.pagenote.feature.audio

/**
 * 音频胶囊几何（移植批次 8）：width-1000 文档空间里的显示框与命中判定。
 * 纯 JVM，供画布 overlay 绘制与点击路由共用同一套矩形。
 */
object AudioCapsuleGeometry {
  /** 胶囊显示框（左上角锚点 + 固定尺寸；y 无上界裁剪，超出部分随页滚动）。 */
  fun rect(anchorX: Double, anchorY: Double): DoubleArray = doubleArrayOf(
    anchorX,
    anchorY,
    anchorX + AudioCapsulePolicy.CAPSULE_WIDTH,
    anchorY + AudioCapsulePolicy.CAPSULE_HEIGHT,
  )

  fun rect(capsule: AudioCapsule): DoubleArray = rect(capsule.anchorX, capsule.anchorY)

  /**
   * 命中判定：点（width-1000 文档单位）落在哪个胶囊的显示框内（含
   * [AudioCapsulePolicy.HIT_TOLERANCE] 容差）。多个命中时返回创建时间最晚
   * 的胶囊（后放的在上层）。
   */
  fun hitTest(capsules: List<AudioCapsule>, x: Double, y: Double): AudioCapsule? {
    if (!x.isFinite() || !y.isFinite()) return null
    val tolerance = AudioCapsulePolicy.HIT_TOLERANCE
    return capsules
      .filter { capsule ->
        val rect = rect(capsule)
        x >= rect[0] - tolerance && x <= rect[2] + tolerance &&
          y >= rect[1] - tolerance && y <= rect[3] + tolerance
      }
      .maxByOrNull(AudioCapsule::createdAtMillis)
  }

  /**
   * 文档坐标 → overlay 像素坐标（overlay 画布以页宽 = 1000 文档单位线性
   * 映射，与 PdfAnnotationOverlay 的 pageWidthScale 同一契约）。
   */
  fun toPixels(anchorX: Double, anchorY: Double, pageWidthScale: Double): Pair<Double, Double> =
    (anchorX * pageWidthScale) to (anchorY * pageWidthScale)
}

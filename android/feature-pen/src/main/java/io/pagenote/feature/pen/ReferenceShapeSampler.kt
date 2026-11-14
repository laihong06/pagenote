package io.pagenote.feature.pen

/** Normal-mode touch sampling from TouchEventManager; coordinates are screen pixels. */
internal class ReferenceShapeSampler {
  private var lastX = 0f
  private var lastY = 0f
  // The reference retains this counter between gestures on one manager.
  private var stationaryCount = 0
  private val samples = mutableListOf<Pair<Float, Float>>()

  fun begin(x: Float, y: Float) {
    lastX = x
    lastY = y
    samples.clear()
  }

  /** Returns true exactly when the reference app adds a point and resets its timer. */
  fun move(x: Float, y: Float, squaredDistanceThreshold: Float): Boolean {
    val dx = x - lastX
    val dy = y - lastY
    var added = false
    if (dx * dx + dy * dy > squaredDistanceThreshold) {
      if (stationaryCount > 10) {
        stationaryCount = 8
      } else {
        samples.add(x to y)
        stationaryCount = 0
        added = true
      }
    } else {
      stationaryCount++
    }
    if (stationaryCount <= 1) {
      lastX = x
      lastY = y
    }
    return added
  }

  fun points(): List<Pair<Float, Float>> = samples.toList()
}

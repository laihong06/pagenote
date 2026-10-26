package io.pagenote.android

import kotlin.math.roundToInt

/**
 * 护眼/夜览滤镜纯策略（移植差距清单 #24，设置项 ⚙️）：应用内容之上叠加
 * 一层暖琥珀半透明色以降低蓝光/亮度对比。强度 → 颜色的映射在这里决定，
 * 设置存储（[ReaderSettings.nightFilterEnabled]/[ReaderSettings.nightFilterStrength]）
 * 与阅读界面 overlay 只消费结果。
 */
object NightFilterPolicy {
  const val MIN_STRENGTH = 0
  const val MAX_STRENGTH = 100

  /** 默认强度（适中偏暖）。 */
  const val DEFAULT_STRENGTH = 45

  /** 滤镜基色：暖琥珀（滤蓝光观感），RGB 分量固定，只随强度变化 alpha。 */
  const val BASE_RGB = 0xB26B00

  /** 最大叠加不透明度（强度 100），保持内容可读。 */
  const val MAX_ALPHA = 0.35f

  fun validateStrength(value: Int) {
    require(value in MIN_STRENGTH..MAX_STRENGTH) { "bad night filter strength" }
  }

  /** 越界强度回落默认（DataStore 加载路径）。 */
  fun sanitizeStrength(value: Int): Int =
    value.takeIf { it in MIN_STRENGTH..MAX_STRENGTH } ?: DEFAULT_STRENGTH

  /**
   * 强度 → 叠加色 ARGB：alpha 从 0（关闭/0）线性到 [MAX_ALPHA]（100）。
   * 强度为 0 返回全透明（绘制层可直接跳过）。
   */
  fun overlayArgb(strength: Int): Int {
    val clamped = sanitizeStrength(strength)
    val alpha = (MAX_ALPHA * clamped / MAX_STRENGTH).coerceIn(0f, MAX_ALPHA)
    val alphaByte = (alpha * 255f).roundToInt().coerceIn(0, 255)
    return (alphaByte shl 24) or BASE_RGB
  }
}

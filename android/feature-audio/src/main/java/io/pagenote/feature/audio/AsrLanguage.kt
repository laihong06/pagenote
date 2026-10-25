package io.pagenote.feature.audio

/**
 * ASR 识别语言设置（移植批次 8，镜像批次 6 的 [io.pagenote.feature.ocr.OcrLanguage]）：
 * 设置保存"想要的语言"，[resolve] 在录制/转写时解析为目录条目可承载的语言。
 * "system" 跟随系统 locale：zh 前缀 → 中文，否则英文。
 */
object AsrLanguage {
  const val SYSTEM = "system"
  const val CHINESE = "zh"
  const val ENGLISH = "en"

  /** 设置层接受的语言 id（含 system）。 */
  val SETTING_KEYS = setOf(SYSTEM, CHINESE, ENGLISH)

  fun isValidSettingKey(key: String): Boolean = SETTING_KEYS.contains(key)

  /**
   * 设置值 → 引擎语言：system 按 locale 解析；未知设置值按 system 处理
   * （回落不失败）。
   */
  fun resolve(settingKey: String, systemLanguageTag: String): String = when (
    settingKey.takeIf(SETTING_KEYS::contains) ?: SYSTEM
  ) {
    CHINESE -> CHINESE
    ENGLISH -> ENGLISH
    else -> if (systemLanguageTag.lowercase().startsWith("zh")) CHINESE else ENGLISH
  }
}

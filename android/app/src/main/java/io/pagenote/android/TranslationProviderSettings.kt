package io.pagenote.android

/**
 * 可选翻译服务商设置（移植差距清单 #23）。
 *
 * 直连服务商是可选路径：不配置时翻译动作仍走 agent 模型端点（零新增 Key）。
 * 服务商 Key 与提示词一样并入 Agent 设置界面，密钥经 Android Keystore 加密存储
 * （[TranslationProviderSettingsStore]），不落明文。
 */
enum class TranslationProvider(val id: String) {
  NONE("none"),
  BAIDU("baidu"),
  YOUDAO("youdao"),
  ;

  companion object {
    fun fromId(value: String): TranslationProvider =
      entries.firstOrNull { it.id == value.trim().lowercase() } ?: NONE
  }
}

data class TranslationProviderDraft(
  val provider: TranslationProvider = TranslationProvider.NONE,
  val appId: String = "",
)

data class TranslationProviderView(
  val provider: TranslationProvider,
  val appId: String,
  val hasSecretKey: Boolean,
)

sealed interface TranslationKeyChange {
  data object Keep : TranslationKeyChange
  data class Replace(val value: String) : TranslationKeyChange
  data object Clear : TranslationKeyChange
}

object TranslationProviderPolicy {
  const val MAX_APP_ID_LENGTH = 256
  const val MAX_SECRET_KEY_LENGTH = 4_096

  fun validate(draft: TranslationProviderDraft) {
    val appId = draft.appId.trim()
    require(appId.length <= MAX_APP_ID_LENGTH) { "bad translation app id" }
    require(appId.none(Char::isISOControl)) { "bad translation app id" }
  }

  fun validateSecretKey(value: String) {
    require(value.isNotBlank() && value.length <= MAX_SECRET_KEY_LENGTH && value.none(Char::isISOControl)) {
      "bad translation secret key"
    }
  }
}

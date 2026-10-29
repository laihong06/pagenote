package io.pagenote.android

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

private const val TRANSLATION_SETTINGS_FILE_NAME = "translation-provider-settings.enc"

fun translationProviderSettingsStore(context: Context): TranslationProviderSettingsStore =
  TranslationProviderSettingsStore(
    file = File(context.noBackupFilesDir, TRANSLATION_SETTINGS_FILE_NAME),
    cipher = AndroidKeystoreAgentSecretCipher(
      alias = "io.pagenote.android.translation-provider-settings.v1",
    ),
  )

/**
 * 翻译服务商设置存储（移植差距清单 #23）：与服务商协议无关的加密本地存储，
 * 结构对照 [AgentModelSettingsStore]（原子写 + 假密文零回退），密钥不明文落盘。
 */
class TranslationProviderSettingsStore(
  private val file: File,
  private val cipher: AgentSecretCipher,
) {
  @Synchronized
  fun loadForEditing(): TranslationProviderView {
    val configuration = readConfiguration()
    return TranslationProviderView(
      provider = configuration.draft.provider,
      appId = configuration.draft.appId,
      hasSecretKey = configuration.secretKey.isNotEmpty(),
    )
  }

  /** 直连是否可用：选了服务商且密钥已保存。翻译动作据此决定走直连或 agent 端点。 */
  @Synchronized
  fun resolveClient(transport: TranslationTransport): TranslationClient? {
    val configuration = readConfiguration()
    if (configuration.draft.provider == TranslationProvider.NONE) return null
    if (configuration.secretKey.isBlank()) return null
    TranslationProviderPolicy.validate(configuration.draft)
    return translationClientFor(
      provider = configuration.draft.provider,
      appId = configuration.draft.appId,
      secretKey = configuration.secretKey,
      transport = transport,
    )
  }

  @Synchronized
  fun save(draft: TranslationProviderDraft, keyChange: TranslationKeyChange) {
    require(draft.provider != TranslationProvider.NONE || draft.appId.isBlank()) {
      "app id requires a provider"
    }
    TranslationProviderPolicy.validate(draft)
    val normalizedDraft = draft.copy(appId = draft.appId.trim())
    val secretKey = when (keyChange) {
      TranslationKeyChange.Keep -> readConfiguration().secretKey
      is TranslationKeyChange.Replace ->
        keyChange.value.also(TranslationProviderPolicy::validateSecretKey).trim()
      TranslationKeyChange.Clear -> ""
    }
    if (normalizedDraft.provider == TranslationProvider.NONE && keyChange != TranslationKeyChange.Clear) {
      // 切回“不使用直连”时把密钥一并清空，避免遗留无用凭据。
      return writeConfiguration(StoredConfiguration(normalizedDraft, ""))
    }
    writeConfiguration(StoredConfiguration(normalizedDraft, secretKey))
  }

  private fun readConfiguration(): StoredConfiguration {
    if (!file.exists()) return StoredConfiguration(TranslationProviderDraft(), "")
    val plaintext = cipher.decrypt(file.readBytes())
    return try {
      DataInputStream(ByteArrayInputStream(plaintext)).use { input ->
        require(input.readInt() == FORMAT_VERSION) { "unsupported translation settings format" }
        val draft = TranslationProviderDraft(
          provider = TranslationProvider.fromId(input.readUTF()),
          appId = input.readUTF(),
        )
        val secretKey = input.readUTF()
        require(input.available() == 0) { "invalid translation settings payload" }
        TranslationProviderPolicy.validate(draft)
        if (secretKey.isNotEmpty()) TranslationProviderPolicy.validateSecretKey(secretKey)
        StoredConfiguration(draft, secretKey)
      }
    } finally {
      plaintext.fill(0)
    }
  }

  private fun writeConfiguration(configuration: StoredConfiguration) {
    val plaintext = ByteArrayOutputStream().use { bytes ->
      DataOutputStream(bytes).use { output ->
        output.writeInt(FORMAT_VERSION)
        output.writeUTF(configuration.draft.provider.id)
        output.writeUTF(configuration.draft.appId)
        output.writeUTF(configuration.secretKey)
      }
      bytes.toByteArray()
    }
    val encrypted = try {
      cipher.encrypt(plaintext)
    } finally {
      plaintext.fill(0)
    }
    val parent = file.absoluteFile.parentFile ?: error("translation settings need a parent directory")
    check(parent.exists() || parent.mkdirs()) { "unable to create translation settings directory" }
    val temporaryFile = File.createTempFile(".translation-settings-", ".tmp", parent)
    try {
      FileOutputStream(temporaryFile).use { output ->
        output.write(encrypted)
        output.fd.sync()
      }
      Files.move(temporaryFile.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    } finally {
      temporaryFile.delete()
      encrypted.fill(0)
    }
  }

  private data class StoredConfiguration(val draft: TranslationProviderDraft, val secretKey: String)

  private companion object {
    const val FORMAT_VERSION = 1
  }
}

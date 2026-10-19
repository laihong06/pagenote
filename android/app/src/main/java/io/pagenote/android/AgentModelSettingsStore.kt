package io.pagenote.android

import android.content.Context
import android.content.pm.ApplicationInfo
import io.pagenote.agent.ModelConfig
import io.pagenote.agent.ModelEndpoint
import io.pagenote.agent.MissingModelConfigException
import io.pagenote.agent.OpenAiCompatibleDefaults
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

private const val SETTINGS_FILE_NAME = "agent-model-settings.enc"

fun agentModelSettingsStore(context: Context): AgentModelSettingsStore = AgentModelSettingsStore(
  file = File(context.noBackupFilesDir, SETTINGS_FILE_NAME),
  cipher = AndroidKeystoreAgentSecretCipher(),
  isDebugBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
)

class AgentModelSettingsStore(
  private val file: File,
  private val cipher: AgentSecretCipher,
  private val isDebugBuild: Boolean,
  private val defaultDraft: AgentModelSettingsDraft = AgentModelSettingsDraft(
    baseUrl = if (isDebugBuild) OpenAiCompatibleDefaults.LOCAL_RELAY_BASE_URL else "",
  ),
) {
  @Synchronized
  fun loadForEditing(): AgentModelSettingsView {
    val configuration = readConfiguration()
    return AgentModelSettingsView(
      baseUrl = configuration.draft.baseUrl,
      model = configuration.draft.model,
      hasApiKey = configuration.apiKey.isNotEmpty(),
    )
  }

  @Synchronized
  fun resolveEndpoint(): ModelEndpoint {
    val configuration = readConfiguration()
    if (configuration.apiKey.isBlank()) throw MissingModelConfigException()
    AgentModelSettingsPolicy.validate(configuration.draft, isDebugBuild)
    return ModelConfig.resolve(
      ModelConfig.Input(
        baseUrl = configuration.draft.baseUrl,
        apiKey = configuration.apiKey,
        model = configuration.draft.model,
      ),
    )
  }

  @Synchronized
  fun save(draft: AgentModelSettingsDraft, keyChange: AgentKeyChange) {
    AgentModelSettingsPolicy.validate(draft, isDebugBuild)
    val normalizedDraft = draft.copy(baseUrl = draft.baseUrl.trim(), model = draft.model.trim())
    val previousKey = if (keyChange == AgentKeyChange.Keep) readConfiguration().apiKey else ""
    val apiKey = when (keyChange) {
      AgentKeyChange.Keep -> previousKey
      is AgentKeyChange.Replace -> keyChange.value.also(AgentModelSettingsPolicy::validateApiKey).trim()
      AgentKeyChange.Clear -> ""
    }
    writeConfiguration(StoredConfiguration(normalizedDraft, apiKey))
  }

  private fun readConfiguration(): StoredConfiguration {
    if (!file.exists()) return StoredConfiguration(defaultDraft, "")
    val plaintext = cipher.decrypt(file.readBytes())
    return try {
      DataInputStream(ByteArrayInputStream(plaintext)).use { input ->
        require(input.readInt() == FORMAT_VERSION) { "unsupported model settings format" }
        val draft = AgentModelSettingsDraft(input.readUTF(), input.readUTF())
        val apiKey = input.readUTF()
        require(input.available() == 0) { "invalid model settings payload" }
        AgentModelSettingsPolicy.validateStored(draft)
        AgentModelSettingsPolicy.validateApiKeyIfPresent(apiKey)
        StoredConfiguration(draft, apiKey)
      }
    } finally {
      plaintext.fill(0)
    }
  }

  private fun writeConfiguration(configuration: StoredConfiguration) {
    val plaintext = ByteArrayOutputStream().use { bytes ->
      DataOutputStream(bytes).use { output ->
        output.writeInt(FORMAT_VERSION)
        output.writeUTF(configuration.draft.baseUrl)
        output.writeUTF(configuration.draft.model)
        output.writeUTF(configuration.apiKey)
      }
      bytes.toByteArray()
    }
    val encrypted = try {
      cipher.encrypt(plaintext)
    } finally {
      plaintext.fill(0)
    }
    val parent = file.absoluteFile.parentFile ?: error("model settings need a parent directory")
    check(parent.exists() || parent.mkdirs()) { "unable to create model settings directory" }
    val temporaryFile = File.createTempFile(".agent-settings-", ".tmp", parent)
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

  private data class StoredConfiguration(val draft: AgentModelSettingsDraft, val apiKey: String)

  private companion object {
    const val FORMAT_VERSION = 1
  }
}

private fun AgentModelSettingsPolicy.validateApiKeyIfPresent(value: String) {
  if (value.isNotEmpty()) validateApiKey(value)
}

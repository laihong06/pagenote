package io.pagenote.android

import io.pagenote.agent.OpenAiCompatibleDefaults
import java.net.URI

data class AgentModelSettingsDraft(
  val baseUrl: String = OpenAiCompatibleDefaults.LOCAL_RELAY_BASE_URL,
  val model: String = OpenAiCompatibleDefaults.LOCAL_RELAY_MODEL,
)

data class AgentModelSettingsView(
  val baseUrl: String,
  val model: String,
  val hasApiKey: Boolean,
)

sealed interface AgentKeyChange {
  data object Keep : AgentKeyChange
  data class Replace(val value: String) : AgentKeyChange
  data object Clear : AgentKeyChange
}

interface AgentSecretCipher {
  fun encrypt(plaintext: ByteArray): ByteArray
  fun decrypt(ciphertext: ByteArray): ByteArray
}

object AgentModelSettingsPolicy {
  const val MAX_BASE_URL_LENGTH = 2_048
  const val MAX_MODEL_LENGTH = 256
  const val MAX_API_KEY_LENGTH = 4_096
  private val DEBUG_HTTP_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

  fun validate(draft: AgentModelSettingsDraft, isDebugBuild: Boolean) {
    val uri = validateStored(draft)
    val scheme = uri.scheme.lowercase()
    require(
      scheme == "https" || (isDebugBuild && scheme == "http" && uri.host.lowercase() in DEBUG_HTTP_HOSTS),
    ) { "debug HTTP is limited to local development hosts" }
  }

  fun validateStored(draft: AgentModelSettingsDraft): URI {
    val baseUrl = draft.baseUrl.trim()
    require(baseUrl.isNotEmpty() && baseUrl.length <= MAX_BASE_URL_LENGTH) { "bad base URL" }
    require(draft.model.trim().isNotEmpty() && draft.model.trim().length <= MAX_MODEL_LENGTH) { "bad model" }

    val uri = try {
      URI(baseUrl)
    } catch (error: Exception) {
      throw IllegalArgumentException("bad base URL", error)
    }
    val scheme = uri.scheme?.lowercase()
    require(uri.isAbsolute && uri.host?.isNotBlank() == true) { "base URL must have a host" }
    require(scheme == "https" || scheme == "http") { "base URL must use HTTP or HTTPS" }
    require(uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
      "base URL cannot contain credentials, query, or fragment"
    }
    require(uri.port == -1 || uri.port in 1..65_535) { "bad base URL port" }
    return uri
  }

  fun validateApiKey(value: String) {
    require(value.isNotBlank() && value.length <= MAX_API_KEY_LENGTH && value.none(Char::isISOControl)) {
      "bad API key"
    }
  }
}

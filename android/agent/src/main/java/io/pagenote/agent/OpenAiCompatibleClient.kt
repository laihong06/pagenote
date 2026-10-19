package io.pagenote.agent

data class ModelEndpoint(
  val baseUrl: String,
  val apiKey: String,
  val model: String,
)

object OpenAiCompatibleDefaults {
  const val LOCAL_RELAY_BASE_URL = "http://127.0.0.1:3457/v1"
  const val LOCAL_RELAY_MODEL = "cline-free/mimo-v2.6-flash"
}

interface ChatClient {
  suspend fun complete(endpoint: ModelEndpoint, messages: List<Map<String, String>>): String
}

package io.pagenote.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MissingModelConfigException : IllegalStateException("missing model config")

class OkHttpChatClient private constructor(
  private val client: OkHttpClient,
) : ChatClient {
  constructor() : this(
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(120, TimeUnit.SECONDS)
      .build(),
  )

  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun complete(endpoint: ModelEndpoint, messages: List<Map<String, String>>): String =
    withContext(Dispatchers.IO) {
      require(endpoint.baseUrl.isNotBlank()) { throw MissingModelConfigException() }
      require(endpoint.model.isNotBlank()) { throw MissingModelConfigException() }
      require(endpoint.apiKey.isNotBlank()) { throw MissingModelConfigException() }
      val url = endpoint.baseUrl.trimEnd('/') + "/chat/completions"
      val payload = buildString {
        append("{\"model\":")
        append(Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(endpoint.model)))
        append(",\"messages\":[")
        messages.forEachIndexed { index, message ->
          if (index > 0) append(",")
          append("{\"role\":")
          append(Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(message["role"].orEmpty())))
          append(",\"content\":")
          append(Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(message["content"].orEmpty())))
          append("}")
        }
        append("],\"max_tokens\":2048}")
      }
      val request = Request.Builder()
        .url(url)
        .addHeader("Content-Type", "application/json")
        .addHeader("Authorization", "Bearer ${endpoint.apiKey}")
        .post(payload.toRequestBody("application/json".toMediaType()))
        .build()
      client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IllegalStateException("model http ${response.code}")
        val root = json.parseToJsonElement(body).jsonObject
        root["choices"]?.jsonArray?.firstOrNull()
          ?.jsonObject?.get("message")?.jsonObject
          ?.get("content")?.jsonPrimitive?.content
          ?: throw IllegalStateException("empty model response")
      }
    }
}

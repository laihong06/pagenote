package io.pagenote.android

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** 翻译服务商 HTTP 传输（OkHttp 直连；超时与 agent 客户端同量级）。 */
class OkHttpTranslationTransport(
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build(),
) : TranslationTransport {
  override suspend fun post(url: String, fields: Map<String, String>): String =
    withContext(Dispatchers.IO) {
      val body = FormBody.Builder().apply {
        fields.forEach { (name, value) -> add(name, value) }
      }.build()
      val request = Request.Builder()
        .url(url)
        .post(body)
        .build()
      try {
        client.newCall(request).execute().use { response ->
          val text = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            throw TranslationException("translation http ${response.code}")
          }
          text
        }
      } catch (error: TranslationException) {
        throw error
      } catch (error: Exception) {
        throw TranslationException(error.message ?: "translation transport failed", error)
      }
    }
}

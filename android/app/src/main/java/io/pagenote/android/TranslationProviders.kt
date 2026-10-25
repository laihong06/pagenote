package io.pagenote.android

import java.security.MessageDigest

/**
 * 翻译服务商直连协议（移植差距清单 #23）。
 *
 * 只实现签名、字段组装与响应解析这些纯逻辑；HTTP 由可注入的
 * [TranslationTransport] 承载，JVM 单测无需真实网络。
 * 语义对照：
 * - 百度通用翻译：`fanyi-api.baidu.com/api/trans/vip/translate`，
 *   sign = MD5(appid + q + salt + 密钥) 的小写十六进制；
 * - 有道智云文本翻译：`openapi.youdao.com/api` v3 签名，
 *   sign = SHA-256(appKey + input + salt + curtime + 密钥)，input 按官方规则截断。
 */
fun interface TranslationTransport {
  /** 以 form-urlencoded POST 请求 `url`，返回响应体文本；非 2xx 抛 [TranslationException]。 */
  suspend fun post(url: String, fields: Map<String, String>): String
}

class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class TranslationOutcome(val text: String)

interface TranslationClient {
  /** 返回翻译后的文本；服务商报错时抛 [TranslationException]。 */
  suspend fun translate(text: String, targetLanguage: String): TranslationOutcome
}

object BaiduTranslation {
  const val ENDPOINT = "https://fanyi-api.baidu.com/api/trans/vip/translate"

  /** 百度：目标语言直接使用小写代码（zh/en 与 Android 侧约定一致）。 */
  fun languageCode(target: String): String = target.trim().lowercase()

  /** 签名算法独立暴露，便于用固定向量单测。 */
  fun sign(appId: String, query: String, salt: String, secretKey: String): String =
    md5Hex(appId + query + salt + secretKey)

  fun requestFields(
    appId: String,
    secretKey: String,
    query: String,
    from: String,
    to: String,
    salt: String,
  ): Map<String, String> = linkedMapOf(
    "q" to query,
    "from" to from,
    "to" to to,
    "appid" to appId,
    "salt" to salt,
    "sign" to sign(appId, query, salt, secretKey),
  )

  class Client(
    private val transport: TranslationTransport,
    private val appId: String,
    private val secretKey: String,
    private val salt: () -> String = { System.nanoTime().toString(16) },
  ) : TranslationClient {
    override suspend fun translate(text: String, targetLanguage: String): TranslationOutcome {
      if (text.isBlank()) throw TranslationException("empty translation input")
      val fields = requestFields(
        appId = appId,
        secretKey = secretKey,
        query = text,
        from = "auto",
        to = languageCode(targetLanguage),
        salt = salt(),
      )
      val body = transport.post(ENDPOINT, fields)
      return TranslationOutcome(parse(body))
    }
  }

  /** 成功：`trans_result[].dst` 按行拼接；错误码非空抛 [TranslationException]。 */
  fun parse(body: String): String {
    val error = JsonStrings.string(body, "error_code")
    if (error != null) {
      val message = JsonStrings.string(body, "error_msg")
      throw TranslationException("baidu translate error $error${message?.let { ": $it" }.orEmpty()}")
    }
    val destinations = JsonStrings.arrayStrings(body, "trans_result", "dst")
    if (destinations.isEmpty()) throw TranslationException("baidu translate returned no result")
    return destinations.joinToString("\n")
  }
}

object YoudaoTranslation {
  const val ENDPOINT = "https://openapi.youdao.com/api"

  /** 有道：简体/繁体使用官方代码。 */
  fun languageCode(target: String): String = when (target.trim().lowercase()) {
    "zh", "zh-cn", "zh-hans" -> "zh-CHS"
    "zh-tw", "zh-hant" -> "zh-CHT"
    else -> target.trim().lowercase()
  }

  /** v3 签名；input 按官方规则：长度 > 20 时取前 10 + 长度 + 后 10。 */
  fun sign(
    appKey: String,
    input: String,
    salt: String,
    curtime: String,
    secretKey: String,
  ): String {
    val truncatedInput = when {
      input.length <= 20 -> input
      else -> input.substring(0, 10) + input.length + input.substring(input.length - 10)
    }
    return sha256Hex(appKey + truncatedInput + salt + curtime + secretKey)
  }

  fun requestFields(
    appKey: String,
    secretKey: String,
    query: String,
    from: String,
    to: String,
    salt: String,
    curtime: String,
  ): Map<String, String> = linkedMapOf(
    "q" to query,
    "from" to from,
    "to" to to,
    "appKey" to appKey,
    "salt" to salt,
    "sign" to sign(appKey, query, salt, curtime, secretKey),
    "signType" to "v3",
    "curtime" to curtime,
  )

  class Client(
    private val transport: TranslationTransport,
    private val appKey: String,
    private val secretKey: String,
    private val salt: () -> String = { System.nanoTime().toString(16) },
    private val curtime: () -> String = { (System.currentTimeMillis() / 1000).toString() },
  ) : TranslationClient {
    override suspend fun translate(text: String, targetLanguage: String): TranslationOutcome {
      if (text.isBlank()) throw TranslationException("empty translation input")
      val fields = requestFields(
        appKey = appKey,
        secretKey = secretKey,
        query = text,
        from = "auto",
        to = languageCode(targetLanguage),
        salt = salt(),
        curtime = curtime(),
      )
      val body = transport.post(ENDPOINT, fields)
      return TranslationOutcome(parse(body))
    }
  }

  /** 成功：`translation[]` 按行拼接；`errorCode` 非 "0" 抛 [TranslationException]。 */
  fun parse(body: String): String {
    val errorCode = JsonStrings.string(body, "errorCode") ?: "unknown"
    if (errorCode != "0") throw TranslationException("youdao translate error $errorCode")
    val translations = JsonStrings.arrayStrings(body, "translation", null)
    if (translations.isEmpty()) throw TranslationException("youdao translate returned no result")
    return translations.joinToString("\n")
  }
}

/** 按需手写的最小 JSON 字符串读取（无本地 JSON 库依赖的纯 JVM 解析）。 */
internal object JsonStrings {
  /** 读取顶层字符串字段；缺失或类型不符返回 null。 */
  fun string(body: String, field: String): String? {
    val keyIndex = findField(body, field) ?: return null
    return readStringAfter(body, keyIndex)
  }

  /** 读取 `arrayField` 数组：元素为对象时取每个对象的 `itemField`，元素为裸字符串时直接收集。 */
  fun arrayStrings(body: String, arrayField: String, itemField: String?): List<String> {
    val arrayStart = findArrayValueStart(body, arrayField) ?: return emptyList()
    val results = mutableListOf<String>()
    var index = arrayStart
    while (index < body.length) {
      when {
        body[index] == ']' -> return results
        body[index] == '{' -> {
          val objEnd = findObjectEnd(body, index) ?: return results
          val obj = body.substring(index + 1, objEnd)
          if (itemField != null) {
            val fieldIndex = findField(obj, itemField)
            if (fieldIndex != null) {
              readStringAfter(obj, fieldIndex)?.let { value -> results.add(value) }
            }
          }
          index = objEnd + 1
        }
        body[index] == '"' -> {
          val closing = findClosingQuote(body, index) ?: return results
          if (itemField == null) {
            results.add(unescape(body.substring(index + 1, closing)))
          }
          index = closing + 1
        }
        else -> index++
      }
    }
    return results
  }

  /** 跳过字符串字面量后返回对象结束 '}' 的下标；嵌套对象按深度计数。 */
  private fun findObjectEnd(body: String, openIndex: Int): Int? {
    var depth = 0
    var index = openIndex
    while (index < body.length) {
      when (val ch = body[index]) {
        '"' -> {
          index = (findClosingQuote(body, index) ?: return null) + 1
          continue
        }
        '{' -> depth++
        '}' -> {
          depth--
          if (depth == 0) return index
        }
        else -> {}
      }
      index++
    }
    return null
  }

  private fun findField(body: String, field: String): Int? {
    var from = 0
    while (true) {
      val keyIndex = body.indexOf("\"$field\"", from)
      if (keyIndex < 0) return null
      var cursor = keyIndex + field.length + 2
      while (cursor < body.length && body[cursor].isWhitespace()) cursor++
      if (cursor < body.length && body[cursor] == ':') {
        return cursor + 1
      }
      from = keyIndex + 1
    }
  }

  private fun findArrayValueStart(body: String, field: String): Int? {
    val afterColon = findField(body, field) ?: return null
    var cursor = afterColon
    while (cursor < body.length && body[cursor].isWhitespace()) cursor++
    if (cursor >= body.length || body[cursor] != '[') return null
    return cursor + 1
  }

  private fun readStringAfter(body: String, from: Int): String? {
    var cursor = from
    while (cursor < body.length && body[cursor].isWhitespace()) cursor++
    if (cursor >= body.length || body[cursor] != '"') return null
    val closing = findClosingQuote(body, cursor) ?: return null
    return unescape(body.substring(cursor + 1, closing))
  }

  private fun findClosingQuote(body: String, openIndex: Int): Int? {
    var index = openIndex + 1
    while (index < body.length) {
      val ch = body[index]
      if (ch == '\\') {
        index += 2
        continue
      }
      if (ch == '"') return index
      index++
    }
    return null
  }

  private fun unescape(token: String): String {
    if ('\\' !in token) return token
    val output = StringBuilder(token.length)
    var index = 0
    while (index < token.length) {
      val ch = token[index]
      if (ch != '\\') {
        output.append(ch)
        index++
        continue
      }
      index++
      if (index >= token.length) break
      when (val next = token[index]) {
        'n' -> output.append('\n')
        't' -> output.append('\t')
        'r' -> output.append('\r')
        'u' -> {
          if (index + 4 < token.length) {
            output.append(token.substring(index + 1, index + 5).toInt(16).toChar())
            index += 4
          }
        }
        else -> output.append(next)
      }
      index++
    }
    return output.toString()
  }
}

internal fun md5Hex(value: String): String =
  digestHex("MD5", value.toByteArray(Charsets.UTF_8))

internal fun sha256Hex(value: String): String =
  digestHex("SHA-256", value.toByteArray(Charsets.UTF_8))

private fun digestHex(algorithm: String, bytes: ByteArray): String =
  MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }

/** 服务商选择：配置了直连时返回对应客户端，否则返回 null（走 agent 端点）。 */
fun translationClientFor(
  provider: TranslationProvider,
  appId: String,
  secretKey: String,
  transport: TranslationTransport,
): TranslationClient? = when (provider) {
  TranslationProvider.NONE -> null
  TranslationProvider.BAIDU -> BaiduTranslation.Client(transport, appId, secretKey)
  TranslationProvider.YOUDAO -> YoudaoTranslation.Client(transport, appKey = appId, secretKey = secretKey)
}

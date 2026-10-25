package io.pagenote.android

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TranslationProvidersTest {

  @Test
  fun baiduSignMatchesKnownVector() {
    // 固定向量：MD5("appid" + "query" + "salt" + "secret")
    assertEquals(
      md5Hex("appid" + "query" + "salt" + "secret"),
      BaiduTranslation.sign("appid", "query", "salt", "secret"),
    )
    // MD5("hello")= 5d41402abc4b2a76b9719d911017c592（公开向量）。
    assertEquals("5d41402abc4b2a76b9719d911017c592", md5Hex("hello"))
  }

  @Test
  fun youdaoSignTruncatesLongInputAndHashesV3() {
    val short = "0123456789"
    assertEquals(
      sha256Hex("appKey" + short + "salt" + "1000" + "secret"),
      YoudaoTranslation.sign("appKey", short, "salt", "1000", "secret"),
    )
    val long = "a".repeat(30)
    val expectedInput = "aaaaaaaaaa" + 30 + "aaaaaaaaaa"
    assertEquals(
      sha256Hex("appKey" + expectedInput + "salt" + "1000" + "secret"),
      YoudaoTranslation.sign("appKey", long, "salt", "1000", "secret"),
    )
    // SHA-256("abc") 公开向量。
    assertEquals(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
      sha256Hex("abc"),
    )
  }

  @Test
  fun baiduRequestFieldsCarrySignAndLanguages() {
    val fields = BaiduTranslation.requestFields(
      appId = "appid", secretKey = "secret", query = "hello", from = "auto", to = "zh", salt = "s1",
    )
    assertEquals("hello", fields["q"])
    assertEquals("zh", fields["to"])
    assertEquals("appid", fields["appid"])
    assertEquals(BaiduTranslation.sign("appid", "hello", "s1", "secret"), fields["sign"])
    assertEquals("auto", fields["from"])
  }

  @Test
  fun youdaoRequestFieldsCarryV3Sign() {
    val fields = YoudaoTranslation.requestFields(
      appKey = "appKey", secretKey = "secret", query = "hi", from = "auto", to = "zh-CHS", salt = "s2", curtime = "1700000000",
    )
    assertEquals("hi", fields["q"])
    assertEquals("zh-CHS", fields["to"])
    assertEquals("v3", fields["signType"])
    assertEquals("1700000000", fields["curtime"])
    assertEquals(YoudaoTranslation.sign("appKey", "hi", "s2", "1700000000", "secret"), fields["sign"])
  }

  @Test
  fun parsesBaiduSuccessAndError() {
    val body = """{"from":"en","to":"zh","trans_result":[{"src":"hello","dst":"你好"},{"src":"world","dst":"世界"}]}"""
    assertEquals("你好\n世界", BaiduTranslation.parse(body))
    val error = """{"error_code":"54001","error_msg":"Invalid Sign"}"""
    val thrown = assertThrows(TranslationException::class.java) { BaiduTranslation.parse(error) }
    assertTrue(thrown.message!!.contains("54001"))
    assertTrue(thrown.message!!.contains("Invalid Sign"))
    assertThrows(TranslationException::class.java) { BaiduTranslation.parse("{}") }
  }

  @Test
  fun parsesYoudaoSuccessAndError() {
    // 有道真实响应总带 errorCode；缺失时按失败处理（unknown）。
    assertEquals(
      "你好\n世界",
      YoudaoTranslation.parse("""{"errorCode":"0","translation":["你好","世界"]}"""),
    )
    val thrown = assertThrows(TranslationException::class.java) {
      YoudaoTranslation.parse("""{"errorCode":"108"}""")
    }
    assertTrue(thrown.message!!.contains("108"))
    assertThrows(TranslationException::class.java) {
      YoudaoTranslation.parse("""{"translation":["缺 errorCode"]}""")
    }
  }

  @Test
  fun clientUsesTransportAndReportsFailures() = runBlocking {
    var capturedUrl: String? = null
    var capturedFields: Map<String, String>? = null
    val transport = TranslationTransport { url, fields ->
      capturedUrl = url
      capturedFields = fields
      """{"trans_result":[{"src":"hello","dst":"你好"}]}"""
    }
    val outcome = BaiduTranslation.Client(transport, "appid", "secret", salt = { "fixed" })
      .translate("hello", "zh")
    assertEquals("你好", outcome.text)
    assertEquals(BaiduTranslation.ENDPOINT, capturedUrl)
    assertEquals(BaiduTranslation.sign("appid", "hello", "fixed", "secret"), capturedFields!!["sign"])

    val failing = TranslationTransport { _, _ -> throw TranslationException("network down") }
    val thrown = assertThrows(TranslationException::class.java) {
      runBlocking { BaiduTranslation.Client(failing, "appid", "secret").translate("hello", "zh") }
    }
    assertTrue(thrown.message!!.contains("network down"))
  }

  @Test
  fun youdaoLanguageCodeDistinguishesSimplifiedAndTraditional() {
    assertEquals("zh-CHS", YoudaoTranslation.languageCode("zh"))
    assertEquals("zh-CHT", YoudaoTranslation.languageCode("zh-TW"))
    assertEquals("en", YoudaoTranslation.languageCode("EN"))
  }

  @Test
  fun factoryReturnsClientOnlyForConfiguredProvider() {
    assertNull(
      translationClientFor(TranslationProvider.NONE, "id", "secret", TranslationTransport { _, _ -> "" }),
    )
    val baidu = translationClientFor(TranslationProvider.BAIDU, "id", "secret", TranslationTransport { _, _ -> "" })
    assertTrue(baidu is BaiduTranslation.Client)
    val youdao = translationClientFor(TranslationProvider.YOUDAO, "id", "secret", TranslationTransport { _, _ -> "" })
    assertTrue(youdao is YoudaoTranslation.Client)
  }
}

/** 与 AgentModelSettingsTest.TestSecretCipher 同构的测试假密文（XOR 掩码，首字节哨兵）。 */
private class FakeTranslationCipher : AgentSecretCipher {
  override fun encrypt(plaintext: ByteArray): ByteArray =
    byteArrayOf(0x5a) + plaintext.map { (it.toInt() xor 0x55).toByte() }

  override fun decrypt(ciphertext: ByteArray): ByteArray {
    require(ciphertext.firstOrNull() == 0x5a.toByte())
    return ciphertext.drop(1).map { (it.toInt() xor 0x55).toByte() }.toByteArray()
  }
}

class TranslationProviderSettingsTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private fun store(file: File) = TranslationProviderSettingsStore(file, cipher = FakeTranslationCipher())

  @Test
  fun newStoreIsUnconfiguredAndResolvesNoClient() {
    val store = store(File(temporaryFolder.root, "t.enc"))
    assertEquals(
      TranslationProviderView(TranslationProvider.NONE, "", hasSecretKey = false),
      store.loadForEditing(),
    )
    assertNull(store.resolveClient(TranslationTransport { _, _ -> "" }))
  }

  @Test
  fun saveRoundTripsProviderAndSecretWithoutPlaintextOnDisk() {
    val file = File(temporaryFolder.root, "baidu.enc")
    val store = store(file)
    val draft = TranslationProviderDraft(TranslationProvider.BAIDU, "2024 appId")

    store.save(draft, TranslationKeyChange.Replace("unit-secret"))

    val view = store.loadForEditing()
    assertEquals(TranslationProvider.BAIDU, view.provider)
    assertEquals("2024 appId", view.appId)
    assertTrue(view.hasSecretKey)
    assertFalse(file.readBytes().decodeToString().contains("unit-secret"))
    val client = store.resolveClient(TranslationTransport { _, _ -> "" })
    assertTrue(client is BaiduTranslation.Client)

    // Keep：重存不带新 Key 保留旧密钥。
    store.save(draft.copy(appId = "next"), TranslationKeyChange.Keep)
    assertTrue(store.loadForEditing().hasSecretKey)

    // Clear：清掉密钥后 resolveClient 返回 null。
    store.save(draft, TranslationKeyChange.Clear)
    assertTrue(store.loadForEditing().hasSecretKey.not())
    assertNull(store.resolveClient(TranslationTransport { _, _ -> "" }))
  }

  @Test
  fun switchingBackToNoneClearsCredentials() {
    val store = store(File(temporaryFolder.root, "youdao.enc"))
    store.save(TranslationProviderDraft(TranslationProvider.YOUDAO, "app"), TranslationKeyChange.Replace("k"))
    assertTrue(store.loadForEditing().hasSecretKey)

    store.save(TranslationProviderDraft(TranslationProvider.NONE, ""), TranslationKeyChange.Keep)

    val view = store.loadForEditing()
    assertEquals(TranslationProvider.NONE, view.provider)
    assertFalse(view.hasSecretKey)
  }

  @Test
  fun rejectsInvalidDraftsAndKeys() {
    val store = store(File(temporaryFolder.root, "invalid.enc"))
    assertThrows(IllegalArgumentException::class.java) {
      store.save(TranslationProviderDraft(TranslationProvider.BAIDU, "id" + "\u0000"), TranslationKeyChange.Replace("k"))
    }
    assertThrows(IllegalArgumentException::class.java) {
      store.save(TranslationProviderDraft(TranslationProvider.BAIDU, "id"), TranslationKeyChange.Replace(" "))
    }
    assertThrows(IllegalArgumentException::class.java) {
      store.save(TranslationProviderDraft(TranslationProvider.NONE, "orphan-id"), TranslationKeyChange.Keep)
    }
  }
}

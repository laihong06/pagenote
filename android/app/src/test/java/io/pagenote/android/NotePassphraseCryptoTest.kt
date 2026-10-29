package io.pagenote.android

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePassphraseCryptoTest {
  private val hasher = Pbkdf2NotePassphraseHasher(
    iterations = 1_000,
    saltSource = { byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16) },
  )

  @Test
  fun hashVerifiesTheSamePassphrase() {
    val hash = hasher.hash("笔记口令-2026".toCharArray())
    assertTrue(hasher.verify("笔记口令-2026".toCharArray(), hash))
  }

  @Test
  fun hashRejectsADifferentPassphrase() {
    val hash = hasher.hash("correct horse".toCharArray())
    assertFalse(hasher.verify("correct horse ".toCharArray(), hash))
    assertFalse(hasher.verify("".toCharArray(), hash))
  }

  @Test
  fun randomizedSaltsProduceDifferentHashesForTheSamePassphrase() {
    val randomHasher = Pbkdf2NotePassphraseHasher(iterations = 1_000)
    val first = randomHasher.hash("same".toCharArray())
    val second = randomHasher.hash("same".toCharArray())
    assertNotEquals(first.saltBase64, second.saltBase64)
    assertNotEquals(first.hashBase64, second.hashBase64)
    assertTrue(randomHasher.verify("same".toCharArray(), first))
    assertTrue(randomHasher.verify("same".toCharArray(), second))
  }

  @Test
  fun hashJsonRoundTripsThroughTheStoredShape() {
    val hash = hasher.hash("round trip".toCharArray())
    val restored = NotePassphraseHash.fromJson(hash.toJson())
    assertEquals(hash, restored)
    assertTrue(hasher.verify("round trip".toCharArray(), restored))
  }

  @Test
  fun storedHashRejectsUnknownAlgorithmsAndBadFields() {
    val hash = hasher.hash("x".toCharArray() + CharArray(3))

    assertThrows(IllegalArgumentException::class.java) {
      NotePassphraseHash.fromJson(
        kotlinx.serialization.json.Json.parseToJsonElement(
          """{"algorithm":"MD5","iterations":1000,"salt":"AQ==","hash":"AQ=="}""",
        ).let { it as kotlinx.serialization.json.JsonObject },
      )
    }
    assertThrows(IllegalArgumentException::class.java) {
      NotePassphraseHash.fromJson(hash.toJson().let { root ->
        kotlinx.serialization.json.JsonObject(
          (root.toMap() - "iterations") + ("iterations" to kotlinx.serialization.json.JsonPrimitive("many")),
        )
      })
    }
    assertThrows(IllegalArgumentException::class.java) {
      NotePassphraseHash.fromJson(hash.toJson().let { root ->
        kotlinx.serialization.json.JsonObject(
          (root.toMap() - "salt") + ("salt" to kotlinx.serialization.json.JsonPrimitive("not base64!")),
        )
      })
    }
  }

  @Test
  fun verifyReturnsFalseForCorruptedStoredHashInsteadOfThrowing() {
    val hash = hasher.hash("pass".toCharArray())
    val corrupted = hash.copy(hashBase64 = "!!!not-base64!!!")
    assertFalse(hasher.verify("pass".toCharArray(), corrupted))
    val wrongAlgorithm = hash.copy(algorithm = "PBKDF2WithHmacSHA512")
    assertFalse(hasher.verify("pass".toCharArray(), wrongAlgorithm))
    val outOfRangeIterations = hash.copy(iterations = NotePassphraseHash.MAX_ITERATIONS + 1)
    assertFalse(hasher.verify("pass".toCharArray(), outOfRangeIterations))
  }

  @Test
  fun encryptDecryptRoundTripsUnicodeContent() {
    val plain = "逐页笔记导出内容：公式 \$x^2\$ 与 emoji 📘。"
    val container = NoteExportCipher.encrypt(plain, "导出口令".toCharArray())
    assertEquals(plain, NoteExportCipher.decrypt(container, "导出口令".toCharArray()))
  }

  @Test
  fun decryptRejectsWrongPassphrase() {
    val container = NoteExportCipher.encrypt("secret notes", "right".toCharArray())
    assertThrows(Exception::class.java) {
      NoteExportCipher.decrypt(container, "wrong".toCharArray())
    }
  }

  @Test
  fun decryptRejectsTamperedPayloadAndForeignInput() {
    val container = NoteExportCipher.encrypt("secret notes", "right".toCharArray())
    val root = kotlinx.serialization.json.Json.parseToJsonElement(container)
      .let { it as kotlinx.serialization.json.JsonObject }
    val tampered = kotlinx.serialization.json.JsonObject(
      root.toMap() + ("payload" to kotlinx.serialization.json.JsonPrimitive(
        Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)),
      )),
    ).toString()
    assertThrows(Exception::class.java) {
      NoteExportCipher.decrypt(tampered, "right".toCharArray())
    }
    assertThrows(IllegalArgumentException::class.java) {
      NoteExportCipher.decrypt("{\"version\":1}", "right".toCharArray())
    }
    assertThrows(IllegalArgumentException::class.java) {
      NoteExportCipher.decrypt("not json at all", "right".toCharArray())
    }
  }

  @Test
  fun containerCarriesTheContractFormatAndFreshRandomMaterial() {
    val first = NoteExportCipher.encrypt("a", "p".toCharArray())
    val second = NoteExportCipher.encrypt("a", "p".toCharArray())
    val firstRoot = kotlinx.serialization.json.Json.parseToJsonElement(first)
      .let { it as kotlinx.serialization.json.JsonObject }
    assertEquals(
      NoteExportCipher.FORMAT,
      (firstRoot["format"] as kotlinx.serialization.json.JsonPrimitive).content,
    )
    assertEquals(
      NoteExportCipher.VERSION,
      (firstRoot["version"] as kotlinx.serialization.json.JsonPrimitive).content.toInt(),
    )
    assertNotEquals(
      (firstRoot["iv"] as kotlinx.serialization.json.JsonPrimitive).content,
      ((kotlinx.serialization.json.Json.parseToJsonElement(second) as kotlinx.serialization.json.JsonObject)["iv"]
        as kotlinx.serialization.json.JsonPrimitive).content,
    )
  }

  @Test
  fun exportedCipherContainerIsPrettyPrintedJsonWithKdfParameters() {
    val container = NoteExportCipher.encrypt("content", "p".toCharArray())
    val root = kotlinx.serialization.json.Json.parseToJsonElement(container)
      .let { it as kotlinx.serialization.json.JsonObject }
    val kdf = root["kdf"] as kotlinx.serialization.json.JsonObject
    assertEquals(
      NotePassphraseHash.ALGORITHM,
      (kdf["algorithm"] as kotlinx.serialization.json.JsonPrimitive).content,
    )
    assertEquals(
      Pbkdf2NotePassphraseHasher.DEFAULT_ITERATIONS,
      (kdf["iterations"] as kotlinx.serialization.json.JsonPrimitive).content.toInt(),
    )
    Base64.getDecoder().decode((kdf["salt"] as kotlinx.serialization.json.JsonPrimitive).content)
    Base64.getDecoder().decode((root["iv"] as kotlinx.serialization.json.JsonPrimitive).content)
    Base64.getDecoder().decode((root["payload"] as kotlinx.serialization.json.JsonPrimitive).content)
  }
}

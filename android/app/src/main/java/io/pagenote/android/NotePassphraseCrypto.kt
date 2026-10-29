package io.pagenote.android

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Stored passphrase hash for a project (port batch 3 note-password layer).
 *
 * The hash travels inside the project (`src/note-settings.json`), so it must be
 * verifiable on any device without AndroidKeystore: a device-bound key would
 * break the "project carries its own password" contract. We therefore follow
 * the *architectural* precedent of [AgentSecretCipher]/[AndroidKeystoreAgentSecretCipher]
 * — a small injectable interface plus a default implementation — but the
 * implementation itself is plain PBKDF2WithHmacSHA256, which is pure JVM and
 * unit-testable without a device.
 */
data class NotePassphraseHash(
  val algorithm: String,
  val iterations: Int,
  val saltBase64: String,
  val hashBase64: String,
) {
  fun toJson(): JsonObject = JsonObject(
    linkedMapOf<String, JsonElement>(
      "algorithm" to JsonPrimitive(algorithm),
      "iterations" to JsonPrimitive(iterations),
      "salt" to JsonPrimitive(saltBase64),
      "hash" to JsonPrimitive(hashBase64),
    ),
  )

  companion object {
    const val ALGORITHM = "PBKDF2WithHmacSHA256"
    const val MIN_ITERATIONS = 1
    const val MAX_ITERATIONS = 10_000_000

    fun fromJson(root: JsonObject): NotePassphraseHash {
      val algorithm = root.stringField("algorithm")
      val iterations = root.intField("iterations")
      val salt = root.stringField("salt")
      val hash = root.stringField("hash")
      require(algorithm == ALGORITHM) { "unsupported passphrase algorithm: $algorithm" }
      require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "bad passphrase iteration count" }
      Base64.getDecoder().decode(salt)
      Base64.getDecoder().decode(hash)
      return NotePassphraseHash(algorithm, iterations, salt, hash)
    }

    private fun JsonObject.stringField(key: String): String {
      val element = this[key] as? JsonPrimitive
        ?: throw IllegalArgumentException("passphrase field must be a string: $key")
      return element.content
    }

    private fun JsonObject.intField(key: String): Int {
      val element = this[key] as? JsonPrimitive
        ?: throw IllegalArgumentException("passphrase field must be an integer: $key")
      return element.int
    }
  }
}

/** Injectable hashing boundary (AndroidKeystore-cipher precedent: interface + default impl + test doubles). */
interface NotePassphraseHasher {
  fun hash(passphrase: CharArray): NotePassphraseHash

  fun verify(passphrase: CharArray, stored: NotePassphraseHash): Boolean
}

/** Pure-JVM PBKDF2 hasher; iterations and salt source are injectable for tests. */
class Pbkdf2NotePassphraseHasher(
  private val iterations: Int = DEFAULT_ITERATIONS,
  private val saltSource: () -> ByteArray = { SecureRandom().generateSeed(SALT_LENGTH_BYTES) },
) : NotePassphraseHasher {
  override fun hash(passphrase: CharArray): NotePassphraseHash {
    val salt = saltSource()
    val derived = derive(passphrase, salt, iterations)
    return try {
      NotePassphraseHash(
        algorithm = NotePassphraseHash.ALGORITHM,
        iterations = iterations,
        saltBase64 = Base64.getEncoder().encodeToString(salt),
        hashBase64 = Base64.getEncoder().encodeToString(derived),
      )
    } finally {
      salt.fill(0)
      derived.fill(0)
    }
  }

  override fun verify(passphrase: CharArray, stored: NotePassphraseHash): Boolean {
    if (stored.algorithm != NotePassphraseHash.ALGORITHM) return false
    val salt = try {
      Base64.getDecoder().decode(stored.saltBase64)
    } catch (_: IllegalArgumentException) {
      return false
    }
    val expected = try {
      Base64.getDecoder().decode(stored.hashBase64)
    } catch (_: IllegalArgumentException) {
      return false
    }
    val derived = try {
      derive(passphrase, salt, stored.iterations)
    } catch (_: IllegalArgumentException) {
      // Out-of-range persisted iteration counts cannot be replayed; treat as no-match.
      return false
    }
    return try {
      MessageDigest.isEqual(derived, expected)
    } finally {
      derived.fill(0)
    }
  }

  private fun derive(passphrase: CharArray, salt: ByteArray, iterationCount: Int): ByteArray {
    require(
      iterationCount in NotePassphraseHash.MIN_ITERATIONS..NotePassphraseHash.MAX_ITERATIONS,
    ) { "bad passphrase iteration count" }
    val spec = PBEKeySpec(passphrase, salt, iterationCount, KEY_LENGTH_BITS)
    val factory = SecretKeyFactory.getInstance(NotePassphraseHash.ALGORITHM)
    return try {
      factory.generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  companion object {
    const val DEFAULT_ITERATIONS = 120_000
    const val SALT_LENGTH_BYTES = 16
    const val KEY_LENGTH_BITS = 256
  }
}

/**
 * Encrypts note exports with the project passphrase (port batch 3).
 *
 * Output is a self-contained JSON container so the existing export pipeline
 * (pending-export snapshot, SAF destination picker, plain text write) works
 * unchanged: the container string replaces the export content and any device
 * holding the passphrase can decrypt it with [decrypt]. AES-GCM provides
 * confidentiality and tamper detection; the key is derived per export with a
 * fresh random salt (never reused from the stored project hash).
 */
object NoteExportCipher {
  const val FORMAT = "pagenote-encrypted-note-export"
  const val VERSION = 1
  const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
  const val IV_LENGTH_BYTES = 12
  const val GCM_TAG_LENGTH_BITS = 128
  private val json = Json { prettyPrint = true }

  fun encrypt(plainText: String, passphrase: CharArray): String {
    val salt = SecureRandom().generateSeed(Pbkdf2NotePassphraseHasher.SALT_LENGTH_BYTES)
    val keyBytes = deriveKeyBytes(passphrase, salt)
    val cipher = try {
      Cipher.getInstance(CIPHER_TRANSFORMATION).also { it.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES")) }
    } finally {
      keyBytes.fill(0)
    }
    val iv = cipher.iv
    require(iv.size == IV_LENGTH_BYTES) { "unexpected AES-GCM IV length" }
    val body = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    return try {
      val container = JsonObject(
        linkedMapOf<String, JsonElement>(
          "format" to JsonPrimitive(FORMAT),
          "version" to JsonPrimitive(VERSION),
          "kdf" to JsonObject(
            linkedMapOf<String, JsonElement>(
              "algorithm" to JsonPrimitive(NotePassphraseHash.ALGORITHM),
              "iterations" to JsonPrimitive(Pbkdf2NotePassphraseHasher.DEFAULT_ITERATIONS),
              "salt" to JsonPrimitive(Base64.getEncoder().encodeToString(salt)),
            ),
          ),
          "iv" to JsonPrimitive(Base64.getEncoder().encodeToString(iv)),
          "payload" to JsonPrimitive(Base64.getEncoder().encodeToString(body)),
        ),
      )
      json.encodeToString(JsonObject.serializer(), container)
    } finally {
      iv.fill(0)
      body.fill(0)
      salt.fill(0)
    }
  }

  fun decrypt(containerText: String, passphrase: CharArray): String {
    val root = try {
      Json.parseToJsonElement(containerText).jsonObject
    } catch (_: Exception) {
      throw IllegalArgumentException("not an encrypted pagenote export")
    }
    if ((root["format"] as? JsonPrimitive)?.content != FORMAT) {
      throw IllegalArgumentException("not an encrypted pagenote export")
    }
    when (val version = (root["version"] as? JsonPrimitive)?.longOrNull) {
      VERSION.toLong() -> Unit
      else -> throw IllegalArgumentException("unsupported encrypted export version: $version")
    }
    val kdf = root["kdf"]?.jsonObject
      ?: throw IllegalArgumentException("encrypted export is missing kdf parameters")
    val algorithm = kdf.stringField("algorithm")
    require(algorithm == NotePassphraseHash.ALGORITHM) { "unsupported kdf algorithm: $algorithm" }
    val iterations = kdf.stringField("iterations").toIntOrNull()
      ?: throw IllegalArgumentException("encrypted export has a bad iteration count")
    require(
      iterations in NotePassphraseHash.MIN_ITERATIONS..NotePassphraseHash.MAX_ITERATIONS,
    ) {
      "encrypted export has a bad iteration count"
    }
    val salt = decodeBase64(kdf.stringField("salt"), "salt")
    val iv = decodeBase64(root.stringField("iv"), "iv")
    val payload = decodeBase64(root.stringField("payload"), "payload")
    require(iv.size == IV_LENGTH_BYTES) { "encrypted export has a bad IV length" }
    val keyBytes = deriveKeyBytes(passphrase, salt, iterations)
    val plain = try {
      Cipher.getInstance(CIPHER_TRANSFORMATION).run {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        doFinal(payload)
      }
    } finally {
      keyBytes.fill(0)
    }
    return try {
      String(plain, Charsets.UTF_8)
    } finally {
      plain.fill(0)
    }
  }

  private fun deriveKeyBytes(
    passphrase: CharArray,
    salt: ByteArray,
    iterationCount: Int = Pbkdf2NotePassphraseHasher.DEFAULT_ITERATIONS,
  ): ByteArray {
    val spec = PBEKeySpec(passphrase, salt, iterationCount, Pbkdf2NotePassphraseHasher.KEY_LENGTH_BITS)
    val factory = SecretKeyFactory.getInstance(NotePassphraseHash.ALGORITHM)
    return try {
      factory.generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  private fun decodeBase64(value: String, field: String): ByteArray = try {
    Base64.getDecoder().decode(value)
  } catch (_: IllegalArgumentException) {
    throw IllegalArgumentException("encrypted export has a bad base64 $field")
  }

  private fun JsonObject.stringField(key: String): String {
    val element = this[key] as? JsonPrimitive
      ?: throw IllegalArgumentException("encrypted export field must be a string: $key")
    return element.content
  }
}

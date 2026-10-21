package io.pagenote.android

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreAgentSecretCipher(
  private val alias: String = DEFAULT_ALIAS,
) : AgentSecretCipher {
  override fun encrypt(plaintext: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey())
    val iv = cipher.iv
    require(iv.size == IV_LENGTH) { "unexpected AES-GCM IV length" }
    val body = cipher.doFinal(plaintext)
    return try {
      ByteArray(1 + iv.size + body.size).also { output ->
        output[0] = CIPHERTEXT_VERSION
        iv.copyInto(output, destinationOffset = 1)
        body.copyInto(output, destinationOffset = 1 + iv.size)
      }
    } finally {
      iv.fill(0)
      body.fill(0)
    }
  }

  override fun decrypt(ciphertext: ByteArray): ByteArray {
    require(
      ciphertext.size > 1 + IV_LENGTH + GCM_TAG_LENGTH_BYTES && ciphertext[0] == CIPHERTEXT_VERSION,
    ) { "invalid encrypted model settings" }
    val iv = ciphertext.copyOfRange(1, 1 + IV_LENGTH)
    val body = ciphertext.copyOfRange(1 + IV_LENGTH, ciphertext.size)
    return try {
      Cipher.getInstance(TRANSFORMATION).run {
        init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        doFinal(body)
      }
    } finally {
      iv.fill(0)
      body.fill(0)
    }
  }

  private fun secretKey(): SecretKey = synchronized(KEYSTORE_LOCK) {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(
      KeyProperties.KEY_ALGORITHM_AES,
      ANDROID_KEYSTORE,
    ).run {
      init(
        KeyGenParameterSpec.Builder(
          alias,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setKeySize(256)
          .setRandomizedEncryptionRequired(true)
          .build(),
      )
      generateKey()
    }
  }

  private companion object {
    const val DEFAULT_ALIAS = "io.pagenote.android.agent-model-settings.v1"
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val CIPHERTEXT_VERSION: Byte = 1
    const val IV_LENGTH = 12
    const val GCM_TAG_LENGTH_BITS = 128
    const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8
    val KEYSTORE_LOCK = Any()
  }
}

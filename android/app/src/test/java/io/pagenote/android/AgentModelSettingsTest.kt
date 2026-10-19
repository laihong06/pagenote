package io.pagenote.android

import io.pagenote.agent.MissingModelConfigException
import io.pagenote.agent.OpenAiCompatibleDefaults
import java.io.File
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AgentModelSettingsTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun newStoreUsesLocalRelayDefaultsWithoutKey() {
    val store = newStore()

    assertEquals(
      AgentModelSettingsView(
        baseUrl = OpenAiCompatibleDefaults.LOCAL_RELAY_BASE_URL,
        model = OpenAiCompatibleDefaults.LOCAL_RELAY_MODEL,
        hasApiKey = false,
      ),
      store.loadForEditing(),
    )
    expectMissingKey { store.resolveEndpoint() }
  }

  @Test
  fun newReleaseStoreDoesNotSuggestAnHttpEndpoint() {
    val store = AgentModelSettingsStore(
      file = File(temporaryFolder.root, "release-agent-config.enc"),
      cipher = TestSecretCipher(),
      isDebugBuild = false,
    )

    assertEquals(
      AgentModelSettingsView(
        baseUrl = "",
        model = OpenAiCompatibleDefaults.LOCAL_RELAY_MODEL,
        hasApiKey = false,
      ),
      store.loadForEditing(),
    )
    expectMissingKey { store.resolveEndpoint() }
  }

  @Test
  fun savedEndpointAndReplacementKeyRoundTripWithoutPlaintextOnDisk() {
    val file = File(temporaryFolder.root, "agent-config.enc")
    val store = newStore(file)
    val draft = AgentModelSettingsDraft("https://models.example/v1", "study-model")
    val key = "unit-test-secret-value"

    store.save(draft, AgentKeyChange.Replace(key))

    assertEquals(AgentModelSettingsView(draft.baseUrl, draft.model, true), store.loadForEditing())
    assertEquals(draft.baseUrl, store.resolveEndpoint().baseUrl)
    assertEquals(draft.model, store.resolveEndpoint().model)
    assertEquals(key, store.resolveEndpoint().apiKey)
    assertFalse(file.readBytes().containsBytes(key.toByteArray(Charsets.UTF_8)))
  }

  @Test
  fun blankKeyEditKeepsPreviouslySavedKey() {
    val store = newStore()
    val draft = AgentModelSettingsDraft("https://models.example/v1", "study-model")
    store.save(draft, AgentKeyChange.Replace("first-secret"))

    store.save(draft.copy(model = "second-model"), AgentKeyChange.Keep)

    assertEquals("first-secret", store.resolveEndpoint().apiKey)
    assertEquals("second-model", store.loadForEditing().model)
  }

  @Test
  fun clearRemovesSavedKeyButKeepsEndpoint() {
    val store = newStore()
    val draft = AgentModelSettingsDraft("https://models.example/v1", "study-model")
    store.save(draft, AgentKeyChange.Replace("saved-secret"))

    store.save(draft, AgentKeyChange.Clear)

    assertEquals(AgentModelSettingsView(draft.baseUrl, draft.model, false), store.loadForEditing())
    expectMissingKey { store.resolveEndpoint() }
  }

  @Test
  fun invalidSaveLeavesPreviousEncryptedConfigurationUnchanged() {
    val file = File(temporaryFolder.root, "agent-config.enc")
    val store = AgentModelSettingsStore(file, TestSecretCipher(), isDebugBuild = false)
    val previous = AgentModelSettingsDraft("https://models.example/v1", "study-model")
    store.save(previous, AgentKeyChange.Replace("saved-secret"))
    val previousBytes = file.readBytes()

    expectIllegalArgument {
      store.save(AgentModelSettingsDraft("http://models.example/v1", "new-model"), AgentKeyChange.Clear)
    }

    assertTrue(previousBytes.contentEquals(file.readBytes()))
    assertEquals(AgentModelSettingsView(previous.baseUrl, previous.model, true), store.loadForEditing())
    assertEquals("saved-secret", store.resolveEndpoint().apiKey)
  }

  @Test
  fun releasePolicyRejectsHttpButDebugPolicyAcceptsIt() {
    val draft = AgentModelSettingsDraft("http://127.0.0.1:3457/v1", "local-model")

    AgentModelSettingsPolicy.validate(draft, isDebugBuild = true)
    expectIllegalArgument { AgentModelSettingsPolicy.validate(draft, isDebugBuild = false) }
  }

  @Test
  fun debugPolicyRejectsHttpForRemoteHosts() {
    val draft = AgentModelSettingsDraft("http://models.example/v1", "local-model")

    expectIllegalArgument { AgentModelSettingsPolicy.validate(draft, isDebugBuild = true) }
  }

  @Test
  fun rejectsUrlsWithoutSupportedAbsoluteHttpAuthority() {
    listOf(
      "not a url",
      "file:///tmp/model",
      "https:///missing-host",
      "https://user:pass@models.example/v1",
      "https://models.example/v1?token=secret",
      "https://models.example/v1#fragment",
    ).forEach { invalidUrl ->
      expectIllegalArgument {
        AgentModelSettingsPolicy.validate(AgentModelSettingsDraft(invalidUrl, "study-model"), isDebugBuild = false)
      }
    }
  }

  @Test
  fun replacementKeyCannotBeBlank() {
    val store = newStore()

    expectIllegalArgument {
      store.save(AgentModelSettingsDraft("https://models.example/v1", "study-model"), AgentKeyChange.Replace("  "))
    }

    assertFalse(store.loadForEditing().hasApiKey)
  }

  @Test
  fun replacementKeyCannotContainControlCharacters() {
    val store = newStore()

    expectIllegalArgument {
      store.save(
        AgentModelSettingsDraft("https://models.example/v1", "study-model"),
        AgentKeyChange.Replace("bad\nkey"),
      )
    }

    assertFalse(store.loadForEditing().hasApiKey)
  }

  private fun newStore(file: File = File(temporaryFolder.root, "agent-config.enc")) =
    AgentModelSettingsStore(file = file, cipher = TestSecretCipher(), isDebugBuild = true)

  private fun expectMissingKey(block: () -> Unit) {
    try {
      block()
      throw AssertionError("Expected MissingModelConfigException")
    } catch (_: MissingModelConfigException) {
    }
  }

  private fun expectIllegalArgument(block: () -> Unit) {
    try {
      block()
      throw AssertionError("Expected IllegalArgumentException")
    } catch (_: IllegalArgumentException) {
    }
  }
}

private class TestSecretCipher : AgentSecretCipher {
  override fun encrypt(plaintext: ByteArray): ByteArray = byteArrayOf(0x5a) + plaintext.map { (it.toInt() xor 0x55).toByte() }

  override fun decrypt(ciphertext: ByteArray): ByteArray {
    require(ciphertext.firstOrNull() == 0x5a.toByte())
    return ciphertext.drop(1).map { (it.toInt() xor 0x55).toByte() }.toByteArray()
  }
}

private fun ByteArray.containsBytes(needle: ByteArray): Boolean {
  if (needle.isEmpty() || needle.size > size) return false
  return (0..size - needle.size).any { start ->
    needle.indices.all { offset -> this[start + offset] == needle[offset] }
  }
}

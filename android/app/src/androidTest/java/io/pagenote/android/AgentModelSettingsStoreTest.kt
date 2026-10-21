package io.pagenote.android

import android.security.NetworkSecurityPolicy
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class AgentModelSettingsStoreTest {
  @Test
  fun debugManifestPermitsCleartextOnlyForLocalDevelopmentHosts() {
    val policy = NetworkSecurityPolicy.getInstance()

    assertEquals(true, policy.isCleartextTrafficPermitted("localhost"))
    assertEquals(true, policy.isCleartextTrafficPermitted("127.0.0.1"))
    assertEquals(true, policy.isCleartextTrafficPermitted("10.0.2.2"))
    assertFalse(policy.isCleartextTrafficPermitted("models.example"))
  }

  @Test
  fun keystoreEncryptsSavedKeyAndCanResolveItAfterReopening() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val keyAlias = "io.pagenote.android.agent-model-test.${UUID.randomUUID()}"
    val file = File(context.noBackupFilesDir, "agent-model-test-${UUID.randomUUID()}.enc")
    val store = AgentModelSettingsStore(file, AndroidKeystoreAgentSecretCipher(keyAlias), isDebugBuild = true)
    val key = "keystore-instrumentation-secret"

    try {
      val draft = AgentModelSettingsDraft("http://127.0.0.1:3457/v1", "test-model")
      store.save(draft, AgentKeyChange.Replace(key))
      val persistedBytes = file.readBytes()
      val reopened = AgentModelSettingsStore(file, AndroidKeystoreAgentSecretCipher(keyAlias), isDebugBuild = true)

      assertFalse(String(persistedBytes, Charsets.ISO_8859_1).contains(key))
      assertEquals(AgentModelSettingsView(draft.baseUrl, draft.model, true), reopened.loadForEditing())
      assertEquals(key, reopened.resolveEndpoint().apiKey)
    } finally {
      file.delete()
      KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
        deleteEntry(keyAlias)
      }
    }
  }
}

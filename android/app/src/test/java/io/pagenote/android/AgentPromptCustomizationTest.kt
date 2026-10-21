package io.pagenote.android

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AgentPromptCustomizationTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun editableCatalogMatchesDesktopModuleIdsAndOrder() {
    assertEquals(
      listOf("system", "learning", "page-tutor", "output", "tag-edit", "learner"),
      AgentPromptCatalog.modules.map { it.id },
    )
  }

  @Test
  fun readsBundledDefaultAndIsolatesSavedOverridesAcrossStoreReopen() = runBlocking {
    val file = File(temporaryFolder.root, "prompts.preferences_pb")
    val firstScope = newScope()
    try {
      val first = newStore(file, firstScope)
      val default = first.loadForEditing("learning")
      assertEquals(defaults.getValue("learning"), default.text)
      assertEquals(defaults.getValue("learning"), default.defaultText)
      assertFalse(default.isCustomized)

      first.save("learning", "custom learning")
      first.save("output", "custom output")
    } finally {
      firstScope.cancel()
    }

    val secondScope = newScope()
    try {
      val second = newStore(file, secondScope)
      assertEquals("custom learning", second.read("learning"))
      assertEquals("custom output", second.read("output"))
      assertEquals(defaults.getValue("tag-edit"), second.read("tag-edit"))
    } finally {
      secondScope.cancel()
    }
  }

  @Test
  fun rejectsUnknownBlankAndOversizedValuesWithoutReplacingLastValidOverride() = runBlocking {
    val scope = newScope()
    try {
      val store = newStore(File(temporaryFolder.root, "invalid.preferences_pb"), scope)
      store.save("system", "last valid")
      listOf(" \n ", "x".repeat(AgentPromptCatalog.MAX_PROMPT_LENGTH + 1)).forEach { invalid ->
        assertRejected { store.save("system", invalid) }
        assertEquals("last valid", store.read("system"))
      }
      assertRejected { store.save("unknown", "text") }
      assertRejected { store.read("unknown") }
      assertEquals("last valid", store.read("system"))
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun resetRemovesOnlyTheSelectedOverride() = runBlocking {
    val scope = newScope()
    try {
      val store = newStore(File(temporaryFolder.root, "reset.preferences_pb"), scope)
      store.save("system", "custom system")
      store.save("learner", "custom learner")

      store.reset("system")

      assertEquals(defaults.getValue("system"), store.read("system"))
      assertEquals("custom learner", store.read("learner"))
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun requestRoutingLoadsOnlyModulesRelevantToTheTask() {
    assertEquals(
      listOf("system", "learning", "page-tutor", "output"),
      AgentPromptRouting.forPageTutor(),
    )
    assertEquals(
      listOf("system", "tag-edit", "output"),
      AgentPromptRouting.forAgentTask("按 AI Tag 修改本页批注"),
    )
    assertEquals(
      listOf("system", "learning", "page-tutor", "output"),
      AgentPromptRouting.forAgentTask("请解释这一页的推导过程"),
    )
    assertEquals(
      listOf("system", "learning", "page-tutor", "output"),
      AgentPromptRouting.forAgentTask("EXPLAIN each stage"),
    )
    assertEquals(
      listOf("system", "output"),
      AgentPromptRouting.forAgentTask("只修正这一行的格式"),
    )
    assertFalse("learner stays opt-in", "learner" in AgentPromptRouting.forAgentTask("请解释这页内容"))
  }

  @Test
  fun appendsConfiguredModulesAfterTheImmutableExistingSafetyPrefix() {
    val source = listOf(
      mapOf("role" to "system", "content" to "LOCKED DOCUMENT SAFETY"),
      mapOf("role" to "user", "content" to "explain page"),
    )
    val configured = AgentPromptComposition.append(
      source,
      listOf(AgentPromptText("system", "custom system"), AgentPromptText("output", "custom output")),
    )
    val system = configured.first { it["role"] == "system" }["content"].orEmpty()

    assertTrue(system.startsWith("LOCKED DOCUMENT SAFETY"))
    assertTrue(system.contains("[system]\ncustom system"))
    assertTrue(system.contains("[output]\ncustom output"))
    assertEquals("explain page", configured.single { it["role"] == "user" }["content"])
  }

  private fun newStore(file: File, scope: CoroutineScope): AgentPromptSettingsStore =
    AgentPromptSettingsStore(newDataStore(file, scope), defaults::getValue)

  private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private fun newDataStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

  private inline fun assertRejected(block: () -> Unit) {
    var rejected = false
    try {
      block()
    } catch (_: IllegalArgumentException) {
      rejected = true
    }
    assertTrue(rejected)
  }

  private companion object {
    val defaults = mapOf(
      "system" to "system default",
      "learning" to "learning default",
      "page-tutor" to "page tutor default",
      "output" to "output default",
      "tag-edit" to "tag edit default",
      "learner" to "learner default",
    )
  }
}

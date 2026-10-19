package io.pagenote.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class AgentPromptSettingsDialogTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val file = File(
    InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
    "agent-prompts-${UUID.randomUUID()}.preferences_pb",
  )
  private val store by lazy {
    AgentPromptSettingsStore(
      PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
      bundledDefault = { id -> defaults.getValue(id) },
    )
  }

  @After
  fun cleanUp() {
    scope.cancel()
    file.delete()
  }

  @Test
  fun switchingModulesKeepsDraftAndSaveResetAreScopedToSelection() {
    composeRule.setContent { AgentPromptSettingsDialog(store = store, onDismiss = {}) }
    composeRule.onNodeWithTag("agent_prompt_text").assertIsDisplayed()
      .performTextReplacement("custom system draft")

    selectModule("learning")
    composeRule.onNodeWithTag("agent_prompt_text").performTextReplacement("custom learning draft")
    selectModule("system")
    composeRule.onNodeWithTag("agent_prompt_text").assertTextContains("custom system draft")
    composeRule.onNodeWithTag("agent_prompt_save").performClick()
    composeRule.onNodeWithTag("agent_prompt_status").assertTextEquals(
      InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.agent_prompt_saved),
    )

    runBlocking {
      assertEquals("custom system draft", store.read("system"))
      assertEquals(defaults.getValue("learning"), store.read("learning"))
    }

    composeRule.onNodeWithTag("agent_prompt_reset").performClick()
    composeRule.waitUntil(5_000) { runBlocking { store.read("system") == defaults.getValue("system") } }
    composeRule.onNodeWithTag("agent_prompt_status").assertTextEquals(
      InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.agent_prompt_default_restored),
    )
    runBlocking { assertEquals(defaults.getValue("system"), store.read("system")) }
  }

  @Test
  fun blankPromptShowsValidationErrorAndDoesNotPersist() {
    composeRule.setContent { AgentPromptSettingsDialog(store = store, onDismiss = {}) }
    composeRule.onNodeWithTag("agent_prompt_text").performTextReplacement("   ")
    composeRule.onNodeWithTag("agent_prompt_save").performClick()
    composeRule.onNodeWithTag("agent_prompt_error").assertIsDisplayed()

    runBlocking { assertFalse(store.loadForEditing("system").isCustomized) }
  }

  @Test
  fun everyBundledPromptAssetIsReadableAndNonBlank() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val assetStore = AgentPromptSettingsStore(
      PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
      bundledDefault = { id ->
        val module = AgentPromptCatalog.module(id)
        context.assets.open("agent-prompts/${module.assetName}").bufferedReader(Charsets.UTF_8).use { it.readText() }
      },
    )

    runBlocking {
      AgentPromptCatalog.modules.forEach { module ->
        assertFalse(module.id, assetStore.read(module.id).isBlank())
      }
    }
  }

  private fun selectModule(id: String) {
    composeRule.onNodeWithTag("agent_prompt_module_picker").performClick()
    composeRule.onNodeWithTag("agent_prompt_option_$id").performClick()
  }

  private companion object {
    val defaults = mapOf(
      "system" to "SYSTEM DEFAULT",
      "learning" to "LEARNING DEFAULT",
      "page-tutor" to "PAGE TUTOR DEFAULT",
      "output" to "OUTPUT DEFAULT",
      "tag-edit" to "TAG EDIT DEFAULT",
      "learner" to "LEARNER DEFAULT",
    )
  }
}

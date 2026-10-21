package io.pagenote.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.agentPromptOverridesDataStore by preferencesDataStore(name = "agent_prompt_overrides")

internal fun agentPromptSettingsStore(context: Context): AgentPromptSettingsStore = AgentPromptSettingsStore(
  dataStore = context.agentPromptOverridesDataStore,
  bundledDefault = { id ->
    val module = AgentPromptCatalog.module(id)
    context.assets.open("agent-prompts/${module.assetName}").bufferedReader(Charsets.UTF_8).use { it.readText() }
  },
)

internal data class AgentPromptModule(
  val id: String,
  val titleResource: Int,
  val assetName: String,
)

internal data class AgentPromptText(val id: String, val text: String)

internal data class AgentPromptSettingsView(
  val id: String,
  val text: String,
  val defaultText: String,
  val isCustomized: Boolean,
)

internal object AgentPromptCatalog {
  const val MAX_PROMPT_LENGTH = 32_768
  val modules = listOf(
    AgentPromptModule("system", R.string.agent_prompt_module_system, "system.txt"),
    AgentPromptModule("learning", R.string.agent_prompt_module_learning, "learning.txt"),
    AgentPromptModule("page-tutor", R.string.agent_prompt_module_page_tutor, "page-tutor.txt"),
    AgentPromptModule("output", R.string.agent_prompt_module_output, "output.txt"),
    AgentPromptModule("tag-edit", R.string.agent_prompt_module_tag_edit, "tag-edit.txt"),
    AgentPromptModule("learner", R.string.agent_prompt_module_learner, "learner.txt"),
  )
  private val byId = modules.associateBy(AgentPromptModule::id)

  fun module(id: String): AgentPromptModule = byId[id] ?: throw IllegalArgumentException("unknown prompt module: $id")

  fun validate(id: String, text: String) {
    module(id)
    require(text.isNotBlank()) { "prompt cannot be blank" }
    require(text.length <= MAX_PROMPT_LENGTH) { "prompt exceeds $MAX_PROMPT_LENGTH characters" }
  }
}

internal class AgentPromptSettingsStore(
  private val dataStore: DataStore<Preferences>,
  private val bundledDefault: (String) -> String,
) {
  suspend fun loadForEditing(id: String): AgentPromptSettingsView {
    AgentPromptCatalog.module(id)
    val preferences = dataStore.data.first()
    val default = bundledDefault(id)
    val override = preferences[promptKey(id)]?.takeIf { isValidStored(id, it) }
    return AgentPromptSettingsView(
      id = id,
      text = override ?: default,
      defaultText = default,
      isCustomized = override != null,
    )
  }

  suspend fun read(id: String): String = loadForEditing(id).text

  suspend fun readModules(ids: List<String>): List<AgentPromptText> {
    require(ids.distinct().size == ids.size) { "duplicate prompt module" }
    ids.forEach(AgentPromptCatalog::module)
    val preferences = dataStore.data.first()
    return ids.map { id ->
      val override = preferences[promptKey(id)]?.takeIf { isValidStored(id, it) }
      AgentPromptText(id, override ?: bundledDefault(id))
    }
  }

  suspend fun save(id: String, text: String) {
    AgentPromptCatalog.validate(id, text)
    dataStore.edit { preferences -> preferences[promptKey(id)] = text }
  }

  suspend fun reset(id: String) {
    AgentPromptCatalog.module(id)
    dataStore.edit { preferences -> preferences.remove(promptKey(id)) }
  }

  private fun isValidStored(id: String, text: String): Boolean =
    text.isNotBlank() && text.length <= AgentPromptCatalog.MAX_PROMPT_LENGTH &&
      runCatching { AgentPromptCatalog.module(id) }.isSuccess

  private fun promptKey(id: String) = stringPreferencesKey("prompt_$id")
}

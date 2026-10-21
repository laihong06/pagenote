package io.pagenote.android

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.net.URI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val Context.recentProjectsDataStore by preferencesDataStore(name = "recent_projects")
private val RECENT_PROJECTS_KEY = stringPreferencesKey("recent_projects")

internal fun recentProjectsStore(context: Context): RecentProjectsStore =
  RecentProjectsStore(context.recentProjectsDataStore)

internal data class RecentProjectEntry(
  val accessTreeUri: String,
  val rootDocumentId: String,
  val projectId: String,
  val name: String,
  val lastOpenedAtMillis: Long,
) {
  fun toProjectOpenTarget() = ProjectOpenTarget(
    accessTreeUri = Uri.parse(accessTreeUri),
    rootDocumentId = rootDocumentId,
    needsPersistablePermission = false,
  )
}

internal object RecentProjectsPolicy {
  const val MAX_ENTRIES = 12

  fun upsert(existing: List<RecentProjectEntry>, candidate: RecentProjectEntry): List<RecentProjectEntry> {
    require(isValid(candidate)) { "Invalid recent project entry" }
    val identity = candidate.identity()
    return (listOf(candidate) + existing.filter { isValid(it) && it.identity() != identity })
      .take(MAX_ENTRIES)
  }

  fun remove(existing: List<RecentProjectEntry>, target: RecentProjectEntry): List<RecentProjectEntry> =
    existing.filter { isValid(it) && it.identity() != target.identity() }.take(MAX_ENTRIES)

  fun normalize(entries: Iterable<RecentProjectEntry>): List<RecentProjectEntry> {
    val seen = mutableSetOf<Pair<String, String>>()
    return entries.filter { entry ->
      isValid(entry) && seen.add(entry.identity())
    }.take(MAX_ENTRIES)
  }

  fun isValid(entry: RecentProjectEntry): Boolean {
    if (entry.accessTreeUri.length > 4096 || entry.rootDocumentId.isBlank() || entry.rootDocumentId.length > 4096) {
      return false
    }
    if (entry.projectId.isBlank() || entry.projectId.length > 256 || entry.name.isBlank() || entry.name.length > 512) {
      return false
    }
    if (entry.lastOpenedAtMillis < 0) return false
    return runCatching {
      val uri = URI(entry.accessTreeUri)
      uri.scheme.equals("content", ignoreCase = true) && !uri.rawAuthority.isNullOrBlank()
    }.getOrDefault(false)
  }

  private fun RecentProjectEntry.identity() = accessTreeUri to rootDocumentId
}

internal class RecentProjectsStore(private val dataStore: DataStore<Preferences>) {
  val recentProjects: Flow<List<RecentProjectEntry>> = dataStore.data.map { preferences ->
    RecentProjectsCodec.decode(preferences[RECENT_PROJECTS_KEY].orEmpty())
  }

  suspend fun record(entry: RecentProjectEntry) {
    require(RecentProjectsPolicy.isValid(entry)) { "Invalid recent project entry" }
    dataStore.edit { preferences ->
      val existing = RecentProjectsCodec.decode(preferences[RECENT_PROJECTS_KEY].orEmpty())
      preferences[RECENT_PROJECTS_KEY] = RecentProjectsCodec.encode(
        RecentProjectsPolicy.upsert(existing, entry),
      )
    }
  }

  suspend fun remove(entry: RecentProjectEntry) {
    dataStore.edit { preferences ->
      val existing = RecentProjectsCodec.decode(preferences[RECENT_PROJECTS_KEY].orEmpty())
      preferences[RECENT_PROJECTS_KEY] = RecentProjectsCodec.encode(
        RecentProjectsPolicy.remove(existing, entry),
      )
    }
  }
}

private object RecentProjectsCodec {
  private const val MAX_STORED_CHARS = 256 * 1024
  private val json = Json

  fun encode(entries: List<RecentProjectEntry>): String = buildJsonArray {
    RecentProjectsPolicy.normalize(entries).forEach { entry ->
      add(
        buildJsonObject {
          put("accessTreeUri", JsonPrimitive(entry.accessTreeUri))
          put("rootDocumentId", JsonPrimitive(entry.rootDocumentId))
          put("projectId", JsonPrimitive(entry.projectId))
          put("name", JsonPrimitive(entry.name))
          put("lastOpenedAtMillis", JsonPrimitive(entry.lastOpenedAtMillis))
        },
      )
    }
  }.toString()

  fun decode(value: String): List<RecentProjectEntry> {
    if (value.isBlank() || value.length > MAX_STORED_CHARS) return emptyList()
    val records = runCatching { json.parseToJsonElement(value).jsonArray }.getOrNull() ?: return emptyList()
    return RecentProjectsPolicy.normalize(records.mapNotNull(::decodeEntry))
  }

  private fun decodeEntry(element: JsonElement): RecentProjectEntry? = runCatching {
    val record: JsonObject = element.jsonObject
    RecentProjectEntry(
      accessTreeUri = record.requiredString("accessTreeUri"),
      rootDocumentId = record.requiredString("rootDocumentId"),
      projectId = record.requiredString("projectId"),
      name = record.requiredString("name"),
      lastOpenedAtMillis = record["lastOpenedAtMillis"]?.jsonPrimitive?.longOrNull
        ?: return null,
    )
  }.getOrNull()
}

private fun JsonObject.requiredString(key: String): String =
  this[key]?.jsonPrimitive?.takeIf { it.isString }?.contentOrNull ?: error("Missing $key")

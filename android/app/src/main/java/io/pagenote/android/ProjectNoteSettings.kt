package io.pagenote.android

import io.pagenote.feature.pen.NoteBackground
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Project-level note settings persisted at `src/note-settings.json`
 * (port batch 3 data layer).
 *
 * The file is Android-owned project-local data: the desktop project pipeline
 * neither reads nor rewrites it (like `annotations.json` it simply stays in the
 * project src directory). Both fields are optional — an absent file or absent
 * field means "not chosen", and the reader falls back to the app-level default
 * paper background. Unknown background keys decode as "not chosen" so newer
 * desktop/Android files keep opening on this build.
 *
 * `passphraseHash` carries the note-password challenge: when present, opening
 * the project requires verifying the passphrase, and note exports are
 * encrypted with it (see [NoteExportCipher]).
 */
data class ProjectNoteSettings(
  val background: NoteBackground? = null,
  val passphraseHash: NotePassphraseHash? = null,
) {
  val hasPassphrase: Boolean get() = passphraseHash != null

  companion object {
    val DEFAULT = ProjectNoteSettings()
  }
}

object ProjectNoteSettingsCodec {
  const val VERSION = 1

  private val json = Json { prettyPrint = true }

  fun decode(text: String): ProjectNoteSettings {
    val root = try {
      Json.parseToJsonElement(text).jsonObject
    } catch (_: Exception) {
      throw IllegalArgumentException("note settings must be a JSON object")
    }
    val version = (root["version"] as? JsonPrimitive)?.intOrNull
    if (version != VERSION) {
      throw IllegalArgumentException("unsupported note settings version: $version")
    }
    val backgroundKey = (root["background"] as? JsonPrimitive)?.takeIf {
      it !is kotlinx.serialization.json.JsonNull
    }?.content
    // Unknown background keys degrade to "not chosen" instead of failing the open.
    val background = NoteBackground.fromKey(backgroundKey)
    val passphraseHash = (root["passphrase"] as? JsonObject)?.let(NotePassphraseHash::fromJson)
    return ProjectNoteSettings(
      background = background,
      passphraseHash = passphraseHash,
    )
  }

  fun encode(settings: ProjectNoteSettings): String {
    val fields = linkedMapOf<String, JsonElement>(
      "version" to JsonPrimitive(VERSION),
    )
    settings.background?.let { background -> fields["background"] = JsonPrimitive(background.key) }
    settings.passphraseHash?.let { hash -> fields["passphrase"] = hash.toJson() }
    return json.encodeToString(JsonObject.serializer(), JsonObject(fields))
  }
}

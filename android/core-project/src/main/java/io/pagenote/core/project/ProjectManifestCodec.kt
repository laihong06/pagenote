package io.pagenote.core.project

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

object ProjectManifestCodec {
  fun decode(text: String): ProjectManifest {
    val root = try {
      Json.parseToJsonElement(text) as? JsonObject
        ?: throw IllegalArgumentException("project manifest must be an object")
    } catch (error: IllegalArgumentException) {
      throw error
    } catch (error: Exception) {
      throw IllegalArgumentException("invalid project manifest JSON", error)
    }

    val manifest = ProjectManifest(
      format = root.requiredString("format"),
      version = root.requiredInt("version"),
      id = root.requiredString("id"),
      name = root.requiredString("name"),
      created = root.optionalString("created"),
      modified = root.optionalString("modified"),
      left = root.requiredString("left"),
      right = root.requiredString("right"),
      leftName = root.optionalString("leftName"),
      preview = root.optionalString("preview"),
    )
    ProjectValidation.validate(manifest)
    return manifest
  }

  private fun JsonObject.requiredString(key: String): String =
    optionalString(key) ?: throw IllegalArgumentException("missing project field: $key")

  private fun JsonObject.optionalString(key: String): String? {
    val element = this[key] ?: return null
    if (element === JsonNull) return null
    val primitive = element as? JsonPrimitive
      ?: throw IllegalArgumentException("project field must be a string: $key")
    require(primitive.isString) { "project field must be a string: $key" }
    return primitive.content
  }

  private fun JsonObject.requiredInt(key: String): Int {
    val element: JsonElement = this[key]
      ?: throw IllegalArgumentException("missing project field: $key")
    val primitive = element as? JsonPrimitive
      ?: throw IllegalArgumentException("project field must be an integer: $key")
    require(!primitive.isString) { "project field must be an integer: $key" }
    return primitive.intOrNull
      ?: throw IllegalArgumentException("project field must be an integer: $key")
  }
}

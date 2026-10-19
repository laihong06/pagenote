package io.pagenote.core.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

object PreparedManifestCodec {
  fun encode(manifest: PreparedManifest): String {
    PreparedManifestStore.validate(manifest)
    val value = buildJsonObject {
      put("version", JsonPrimitive(manifest.version))
      put("documentId", JsonPrimitive(manifest.documentId))
      put("page", JsonPrimitive(manifest.page))
      put("sourceRevision", JsonPrimitive(manifest.sourceRevision))
      put("kind", JsonPrimitive(manifest.kind))
      put("originalPreserved", JsonPrimitive(manifest.originalPreserved))
      put("region", buildJsonObject {
        put("x", JsonPrimitive(manifest.region.x)); put("y", JsonPrimitive(manifest.region.y))
        put("width", JsonPrimitive(manifest.region.width)); put("height", JsonPrimitive(manifest.region.height))
      })
      put("cacheKey", JsonPrimitive(manifest.cacheKey))
      put("width", JsonPrimitive(manifest.width)); put("height", JsonPrimitive(manifest.height))
      put("scale", JsonPrimitive(manifest.scale))
      put("nativeCharacters", JsonPrimitive(manifest.nativeCharacters))
      put("ocrCharacters", JsonPrimitive(manifest.ocrCharacters))
      put("textCharacters", JsonPrimitive(manifest.textCharacters))
      put("blockCount", JsonPrimitive(manifest.blockCount)); put("tableCount", JsonPrimitive(manifest.tableCount))
      put("ocr", manifest.ocr)
      put("warnings", JsonArray(manifest.warnings.map(::JsonPrimitive)))
      put("visualReviewRequired", JsonPrimitive(manifest.visualReviewRequired))
      put("files", JsonArray(manifest.files.map(::JsonPrimitive)))
      put("visuals", manifest.visuals)
    }
    return Json.encodeToString(JsonElement.serializer(), value)
  }

  fun decode(json: String): PreparedManifest {
    val root = parseObject(json)
    val region = root.requiredObject("region")
    val manifest = PreparedManifest(
      version = root.requiredInt("version"), documentId = root.requiredString("documentId"),
      page = root.requiredInt("page"), sourceRevision = root.requiredString("sourceRevision"),
      kind = root.requiredString("kind"), originalPreserved = root.optionalBoolean("originalPreserved") ?: true,
      region = PageRegion(region.requiredDouble("x"), region.requiredDouble("y"),
        region.requiredDouble("width"), region.requiredDouble("height")),
      cacheKey = root.requiredString("cacheKey"), width = root.requiredInt("width"),
      height = root.requiredInt("height"), scale = root.requiredDouble("scale"),
      nativeCharacters = root.optionalInt("nativeCharacters") ?: 0,
      ocrCharacters = root.optionalInt("ocrCharacters") ?: 0,
      textCharacters = root.optionalInt("textCharacters") ?: 0,
      blockCount = root.optionalInt("blockCount") ?: 0,
      tableCount = root.optionalInt("tableCount") ?: 0,
      ocr = root.requiredObject("ocr"),
      warnings = root.requiredArray("warnings").map { it.requiredString("warning") },
      visualReviewRequired = root.requiredBoolean("visualReviewRequired"),
      files = root.requiredArray("files").map { it.requiredString("file") },
      visuals = root.requiredArray("visuals"),
    )
    PreparedManifestStore.validate(manifest)
    return manifest
  }

  private fun parseObject(text: String): JsonObject = try {
    Json.parseToJsonElement(text) as? JsonObject ?: throw IllegalArgumentException("manifest must be an object")
  } catch (error: IllegalArgumentException) {
    throw error
  } catch (error: Exception) {
    throw IllegalArgumentException("invalid manifest JSON", error)
  }

  private fun JsonObject.requiredObject(key: String) =
    this[key] as? JsonObject ?: throw IllegalArgumentException("missing or invalid $key")
  private fun JsonObject.requiredArray(key: String) =
    this[key] as? JsonArray ?: throw IllegalArgumentException("missing or invalid $key")
  private fun JsonObject.requiredString(key: String): String {
    val value = this[key] as? JsonPrimitive ?: throw IllegalArgumentException("missing or invalid $key")
    require(value.isString) { "invalid $key" }
    return value.content
  }
  private fun JsonObject.requiredInt(key: String): Int {
    val value = this[key] as? JsonPrimitive ?: throw IllegalArgumentException("missing or invalid $key")
    require(!value.isString) { "invalid $key" }
    return value.intOrNull ?: throw IllegalArgumentException("invalid $key")
  }
  private fun JsonObject.requiredDouble(key: String): Double {
    val value = this[key] as? JsonPrimitive ?: throw IllegalArgumentException("missing or invalid $key")
    require(!value.isString) { "invalid $key" }
    return value.doubleOrNull?.takeIf(Double::isFinite) ?: throw IllegalArgumentException("invalid $key")
  }
  private fun JsonObject.requiredBoolean(key: String): Boolean {
    val value = this[key] as? JsonPrimitive ?: throw IllegalArgumentException("missing or invalid $key")
    return value.booleanOrNull ?: throw IllegalArgumentException("invalid $key")
  }
  private fun JsonObject.optionalInt(key: String): Int? = if (containsKey(key)) requiredInt(key) else null
  private fun JsonObject.optionalBoolean(key: String): Boolean? = if (containsKey(key)) requiredBoolean(key) else null
  private fun JsonElement.requiredString(label: String): String {
    val value = this as? JsonPrimitive ?: throw IllegalArgumentException("invalid $label")
    require(value.isString) { "invalid $label" }
    return value.content
  }
}

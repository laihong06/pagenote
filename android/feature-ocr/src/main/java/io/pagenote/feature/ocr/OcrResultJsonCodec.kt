package io.pagenote.feature.ocr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull

object OcrResultJsonCodec {
  fun encode(result: OcrResult): String {
    OcrResultPolicy.validate(result)
    val value = buildJsonObject {
      put("engine", JsonPrimitive(result.engine)); put("status", JsonPrimitive(result.status))
      put("imagePreserved", JsonPrimitive(result.imagePreserved))
      put("warnings", JsonArray(result.warnings.map(::JsonPrimitive)))
      put("blocks", JsonArray(result.blocks.map { block -> buildJsonObject {
        put("text", JsonPrimitive(block.text)); put("confidence", JsonPrimitive(block.confidence))
        put("x", JsonPrimitive(block.x)); put("y", JsonPrimitive(block.y))
        put("width", JsonPrimitive(block.width)); put("height", JsonPrimitive(block.height))
      } }))
    }
    return Json.encodeToString(JsonElement.serializer(), value)
  }

  fun decode(json: String): OcrResult {
    val root = try {
      Json.parseToJsonElement(json) as? JsonObject ?: throw IllegalArgumentException("OCR result must be an object")
    } catch (error: IllegalArgumentException) {
      throw error
    } catch (error: Exception) {
      throw IllegalArgumentException("invalid OCR result JSON", error)
    }
    val result = OcrResult(
      engine = root.requiredString("engine"), status = root.requiredString("status"),
      imagePreserved = root.requiredBoolean("imagePreserved"),
      warnings = root.requiredArray("warnings").map { it.requiredString("warning") },
      blocks = root.requiredArray("blocks").map { element ->
        val block = element as? JsonObject ?: throw IllegalArgumentException("invalid OCR block")
        OcrBlock(block.requiredString("text"), block.requiredDouble("confidence"),
          block.requiredDouble("x"), block.requiredDouble("y"),
          block.requiredDouble("width"), block.requiredDouble("height"))
      },
    )
    OcrResultPolicy.validate(result)
    return result
  }

  private fun JsonObject.requiredArray(key: String) =
    this[key] as? JsonArray ?: throw IllegalArgumentException("missing or invalid $key")
  private fun JsonObject.requiredString(key: String): String {
    val value = this[key] as? JsonPrimitive ?: throw IllegalArgumentException("missing or invalid $key")
    require(value.isString) { "invalid $key" }
    return value.content
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
  private fun JsonElement.requiredString(label: String): String {
    val value = this as? JsonPrimitive ?: throw IllegalArgumentException("invalid $label")
    require(value.isString) { "invalid $label" }
    return value.content
  }
}

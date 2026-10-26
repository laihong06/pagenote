package io.pagenote.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

internal data class BookReadingPosition(
  val projectId: String,
  val page: Int,
  val scale: Float,
  val x: Float,
  val y: Float,
  val pen: String,
) {
  fun encode(): String = buildJsonObject {
    put("version", 1)
    put("projectId", projectId); put("page", page); put("scale", scale)
    put("x", x); put("y", y); put("pen", pen)
  }.toString()

  companion object {
    fun decode(text: String): BookReadingPosition {
      try {
        val json = Json.parseToJsonElement(text).jsonObject
        require(json["version"]?.jsonPrimitive?.int == 1) { "Unknown reading position version" }
        return BookReadingPosition(
          json.getValue("projectId").jsonPrimitive.content, json.getValue("page").jsonPrimitive.int,
          json.getValue("scale").jsonPrimitive.float, json.getValue("x").jsonPrimitive.float,
          json.getValue("y").jsonPrimitive.float, json.getValue("pen").jsonPrimitive.content,
        ).also {
          require(it.projectId.isNotBlank() && it.page >= 0 && it.scale.isFinite() && it.scale > 0 &&
            it.x.isFinite() && it.y.isFinite()) { "Invalid book reading position" }
        }
      } catch (failure: Exception) {
        throw IllegalArgumentException("Invalid book reading position", failure)
      }
    }
  }
}

@Composable
internal fun PersistBookReadingPosition(session: ReaderSessionViewModel, shelf: BookshelfController) {
  LaunchedEffect(session.project.value, session.pageIndex.intValue, session.viewportBookmark.value, session.penTool.value) {
    val checkpoint = session.saveCheckpoint() ?: return@LaunchedEffect
    delay(600)
    try { shelf.store.saveCheckpoint(checkpoint) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) { shelf.error = failure.message }
  }
}

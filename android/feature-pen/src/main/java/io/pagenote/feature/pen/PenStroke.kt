package io.pagenote.feature.pen

/** Single pressure point. x/y share the annotations.json page coordinate space. */
data class PenPoint(val x: Float, val y: Float, val pressure: Float)

/** Ink surface, mirroring the annotations.json top-level keys. */
enum class PenSide(val key: String) {
  DOCUMENT("document"),
  NOTE("note");

  companion object {
    fun fromKey(key: String): PenSide? = PenSide.values().firstOrNull { it.key == key }
  }
}

data class PenStroke(
  val type: String,
  val color: String,
  val width: Double,
  val points: List<PenPoint>,
  val text: String? = null,
  val html: String? = null,
  val url: String? = null,
  val extraFields: Map<String, Any?> = emptyMap(),
) {
  /** Serializes to one annotations.json item; pressure rides along in "pressures". */
  fun toAnnotationsItem(): Map<String, Any?> {
    val item = linkedMapOf<String, Any?>(
      "type" to type,
      "color" to color,
      "width" to width,
      "points" to points.map { listOf(it.x.toDouble(), it.y.toDouble()) },
      "pressures" to points.map { it.pressure.toDouble() },
    )
    if (text != null) item["text"] = text
    if (html != null) item["html"] = html
    if (url != null) item["url"] = url
    extraFields.forEach { (key, value) ->
      if (key !in CANONICAL_ANNOTATION_FIELDS) item[key] = value
    }
    return item
  }

  companion object {
    internal val CANONICAL_ANNOTATION_FIELDS = setOf(
      "type", "color", "width", "points", "pressures", "text", "html", "url",
    )
  }
}

/** Validation aligned with the desktop annotations.json rules. */
object PenValidation {
  val STROKE_TYPES = setOf(
    "pen", "highlight", "line", "arrow", "rect", "ellipse", "text", "image", "html",
  )
  const val MIN_WIDTH = 0.1
  const val MAX_WIDTH = 100.0
  const val MAX_POINTS = 30000
  const val MAX_ITEMS_PER_PAGE = 2000
  const val MAX_TEXT_LENGTH = 10000
  const val MAX_HTML_LENGTH = 100000
  const val MAX_COORD_ABS = 1_000_000f
  const val DEFAULT_PRESSURE = 1.0f
  const val UNDO_DEPTH = 80

  private val COLOR = Regex("^#[a-fA-F0-9]{6}$")
  private val IMAGE_URL =
    Regex("^/attachments/[a-f0-9-]{36}\\.(png|jpe?g|gif|webp)$", RegexOption.IGNORE_CASE)

  fun validate(stroke: PenStroke, page: Int) {
    require(page >= 1) { "bad page: $page" }
    require(STROKE_TYPES.contains(stroke.type)) { "bad stroke type: ${stroke.type}" }
    require(COLOR.matches(stroke.color)) { "bad color: ${stroke.color}" }
    require(stroke.width.isFinite() && stroke.width >= MIN_WIDTH && stroke.width <= MAX_WIDTH) {
      "bad width: ${stroke.width}"
    }
    require(stroke.points.isNotEmpty() && stroke.points.size <= MAX_POINTS) {
      "bad points size: ${stroke.points.size}"
    }
    stroke.points.forEach { point ->
      require(point.x.isFinite() && point.y.isFinite() && point.pressure.isFinite()) {
        "bad point: $point"
      }
      require(kotlin.math.abs(point.x) <= MAX_COORD_ABS && kotlin.math.abs(point.y) <= MAX_COORD_ABS) {
        "point out of range: $point"
      }
      require(point.pressure in 0.0f..1.0f) { "bad pressure: ${point.pressure}" }
    }
    if (stroke.type == "text") {
      require(stroke.text != null && stroke.text.length <= MAX_TEXT_LENGTH) { "bad text item" }
    }
    if (stroke.type == "html") {
      require(stroke.html != null && stroke.html.length <= MAX_HTML_LENGTH) { "bad html item" }
    }
    if (stroke.type == "image") {
      require(stroke.url != null && IMAGE_URL.matches(stroke.url)) { "bad image url: ${stroke.url}" }
    }
  }

  fun validateItemCount(count: Int, side: PenSide, page: Int) {
    require(count <= MAX_ITEMS_PER_PAGE) {
      "too many items on ${side.key} page $page: $count"
    }
  }
}

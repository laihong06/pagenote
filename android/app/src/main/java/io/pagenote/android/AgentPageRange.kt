package io.pagenote.android

object AgentPageRange {
  fun parse(text: String, totalPages: Int): List<Int> {
    require(totalPages >= 1) { "bad total pages" }
    val cleaned = text.trim()
    require(cleaned.isNotEmpty()) { "page range must not be blank" }
    val pages = LinkedHashSet<Int>()
    cleaned.split(",").forEach { token ->
      val part = token.trim()
      require(part.isNotEmpty()) { "bad page range: $text" }
      if ("-" in part) {
        val bounds = part.split("-")
        require(bounds.size == 2) { "bad page range: $text" }
        val start = bounds[0].trim().toIntOrNull() ?: throw IllegalArgumentException("bad page range: $text")
        val end = bounds[1].trim().toIntOrNull() ?: throw IllegalArgumentException("bad page range: $text")
        require(start in 1..totalPages && end in 1..totalPages && start <= end) { "page out of range: $text" }
        (start..end).forEach { pages.add(it) }
      } else {
        val page = part.toIntOrNull() ?: throw IllegalArgumentException("bad page range: $text")
        require(page in 1..totalPages) { "page out of range: $text" }
        pages.add(page)
      }
    }
    require(pages.isNotEmpty()) { "page range must not be blank" }
    return pages.toList()
  }
}

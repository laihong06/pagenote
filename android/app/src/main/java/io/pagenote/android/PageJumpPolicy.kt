package io.pagenote.android

object PageJumpPolicy {
  fun parseTarget(input: String, totalPages: Int): Int? {
    if (totalPages < 1) return null
    val page = input.trim().toIntOrNull() ?: return null
    return (page - 1).takeIf { it in 0 until totalPages }
  }
}

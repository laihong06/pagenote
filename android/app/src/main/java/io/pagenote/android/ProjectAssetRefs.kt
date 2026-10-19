package io.pagenote.android

object ProjectAssetRefs {
  private val REF = Regex("(?<!/)assets/([a-f0-9-]+\\.[a-z0-9]+)", RegexOption.IGNORE_CASE)

  fun scan(markdown: String): List<String> =
    REF.findAll(markdown).map { it.groupValues[1] }.toList()

  fun missing(refs: List<String>, exists: (String) -> Boolean): List<String> =
    refs.filterNot(exists).distinct()
}

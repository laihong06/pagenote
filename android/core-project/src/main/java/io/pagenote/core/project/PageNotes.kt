package io.pagenote.core.project

import java.security.MessageDigest

object PageNotes {
  const val SEPARATOR = "<!-- pagebreak -->"

  fun split(text: String): List<String> {
    if (text.isEmpty()) return listOf("")
    val pattern = Regex("(?:\\r?\\n){2}<!--[ \\t]*pagebreak[ \\t]*-->(?:\\r?\\n){2}|^[ \\t]*<!--[ \\t]*pagebreak[ \\t]*-->[ \\t]*\$", RegexOption.MULTILINE)
    return text.split(pattern)
  }

  fun join(pages: List<String>): String {
    return pages.joinToString("\n\n<!-- pagebreak -->\n\n")
  }

  fun revision(markdown: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(markdown.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
  }
}

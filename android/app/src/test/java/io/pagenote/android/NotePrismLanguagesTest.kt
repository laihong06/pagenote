package io.pagenote.android

import io.noties.prism4j.Prism4j
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotePrismLanguagesTest {
  @Test
  fun languageAliasesNormalizeOnlyWhenTheTargetGrammarIsBundled() {
    val prism = Prism4j(GrammarLocatorDef())
    val bundledAliases = mapOf(
      "python3" to "python",
      "py3" to "python",
      "c++" to "cpp",
      "c#" to "csharp",
      "javascriptreact" to "javascript",
    )
    val unsupportedAliases = listOf("typescriptreact", "shellscript", "console", "ps1")

    bundledAliases.forEach { (source, target) ->
      val rewritten = NotePreviewRewrite.forPreview("```$source\ncode\n```")
      val actualLanguage = rewritten.substringAfter("```").substringBefore('\n')
      assertEquals(target, actualLanguage)
      assertNotNull("Missing bundled grammar for $actualLanguage", prism.grammar(actualLanguage))
    }
    unsupportedAliases.forEach { source ->
      val rewritten = NotePreviewRewrite.forPreview("```$source\ncode\n```")
      val actualLanguage = rewritten.substringAfter("```").substringBefore('\n')
      assertEquals("Unsupported $source should stay an explicit plain-text language", source, actualLanguage)
      assertNull(prism.grammar(actualLanguage))
    }
  }

  @Test
  fun bundledPythonGrammarTokenizesAndUnknownLanguagesStayUnsupported() {
    val prism = Prism4j(GrammarLocatorDef())
    val grammar = prism.grammar("python")

    assertNotNull(grammar)
    assertFalse(prism.tokenize("print('hello')", grammar!!).isEmpty())
    assertNull(prism.grammar("unknown-language"))
  }
}

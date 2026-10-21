package io.pagenote.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteRichContentTest {
  @Test
  fun extractsAllFourKinds() {
    val markdown = "# t\n\nInline \$a+b\$ and block:\n\n\$\$x^2\$\$\n\n```python3\nprint(1)\n```\n\n```mermaid\ngraph A-->B\n```\n\n![p](/attachments/123e4567-e89b-12d3-a456-426614174000.png)\n"

    val content = NoteRichContent.extract(markdown)

    assertEquals(2, content.formulas.size)
    assertEquals(1, content.codeBlocks.size)
    assertEquals("python", content.codeBlocks[0].lang)
    assertEquals(1, content.diagrams.size)
    assertEquals(MermaidState.READY, content.diagrams[0].state)
    assertEquals(1, content.images.size)
    assertEquals(ImageKind.ATTACHMENT, content.images[0].kind)
    assertTrue(content.errors.isEmpty())
  }

  @Test
  fun unclosedFenceIsErrorAndPlainText() {
    val content = NoteRichContent.extract("```java\nunclosed\n")

    assertEquals(1, content.errors.size)
    assertEquals(1, content.codeBlocks.size)
    assertEquals(false, content.codeBlocks[0].closed)
    assertEquals(listOf(NoteDiagnostic.UNCLOSED_CODE_FENCE), content.diagnostics)
  }

  @Test
  fun unterminatedFormulaHasStableDiagnostic() {
    val content = NoteRichContent.extract("formula: \$privateSource")

    assertEquals(listOf(NoteDiagnostic.UNTERMINATED_FORMULA), content.diagnostics)
    assertTrue(content.warnings.isNotEmpty())
    assertTrue(content.diagnostics.toString().contains("privateSource").not())
  }

  @Test
  fun multilineAndMultiTickCodeSpansDoNotDiagnoseFormulaMarkers() {
    val markdown = "``first line\n\$not closed\n` still code\nlast``\n\n`another\n\$\$also code`"

    val content = NoteRichContent.extract(markdown)

    assertTrue(content.diagnostics.isEmpty())
    assertTrue(content.warnings.isEmpty())
  }

  @Test
  fun formulaCloserInsideCodeSpanDoesNotCloseFormula() {
    val content = NoteRichContent.extract("\$outside `\$inside`")

    assertEquals(listOf(NoteDiagnostic.UNTERMINATED_FORMULA), content.diagnostics)
  }

  @Test
  fun repeatedFormulaWarningsAreDeduplicatedByDiagnosticType() {
    val content = NoteRichContent.extract("first \$ and second \$\$")

    assertEquals(listOf(NoteDiagnostic.UNTERMINATED_FORMULA), content.diagnostics)
  }

  @Test
  fun missingAndRemoteImagesWarn() {
    val content = NoteRichContent.extract(
      "![a](/attachments/123e4567-e89b-12d3-a456-426614174000.png)\n\n![b](https://x/y.png)\n",
    )

    assertEquals(ImageKind.ATTACHMENT, content.images[0].kind)
    assertEquals(ImageKind.REMOTE, content.images[1].kind)
    assertTrue(content.warnings.any { it.contains("https://x/y.png") })
  }

  @Test
  fun emptyMermaidWarns() {
    val content = NoteRichContent.extract("```mermaid\n\n```\n")

    assertEquals(MermaidState.EMPTY, content.diagrams.single().state)
    assertTrue(content.warnings.isNotEmpty())
  }

  @Test
  fun mathFenceCountsAsFormula() {
    val content = NoteRichContent.extract("```math\nx^2\n```\n")

    assertEquals(1, content.formulas.size)
    assertTrue(content.codeBlocks.isEmpty())
  }
}

package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePreviewRewriteTest {
  @Test
  fun mermaidFenceRewritesToPrivateImageUriWithoutExposingSource() {
    val markdown = "# t\n\n```mermaid\ngraph A-->B\n```\n\nAfter\n"

    val rewritten = NotePreviewRewrite.forPreview(markdown)

    assertFalse(rewritten.contains("```mermaid"))
    assertFalse(rewritten.contains("graph A-->B"))
    val uri = Regex("!\\[Mermaid diagram]\\((pagenote-mermaid:[^)]*)\\)")
      .find(rewritten)?.groupValues?.get(1)
    assertEquals("graph A-->B", MermaidSourceCodec.decode(uri ?: error("missing diagram URI")))
    assertTrue(rewritten.startsWith("# t\n\n"))
    assertTrue(rewritten.endsWith("\nAfter\n"))
  }

  @Test
  fun adjacentAndEmptyMermaidFencesRemainDistinct() {
    val rewritten = NotePreviewRewrite.forPreview("~~~mermaid\ngraph A-->B\n~~~\n\n```mermaid\n```\n")
    val uris = Regex("!\\[Mermaid diagram]\\((pagenote-mermaid:[^)]*)\\)")
      .findAll(rewritten).map { it.groupValues[1] }.toList()

    assertEquals(listOf("graph A-->B", ""), uris.map(MermaidSourceCodec::decode))
  }

  @Test
  fun mermaidLookingFenceInsideAnotherCodeBlockIsNotRewritten() {
    val markdown = "```text\n```mermaid\ngraph A-->B\n```\n```\n"

    assertEquals(markdown, NotePreviewRewrite.forPreview(markdown))
  }

  @Test
  fun oversizedMermaidSourceStaysVisibleAsSourceInsteadOfBeingDropped() {
    val source = "x".repeat(MermaidSourceCodec.MAX_SOURCE_CHARS + 1)
    val markdown = "```mermaid\n$source\n```"

    val rewritten = NotePreviewRewrite.forPreview(markdown)

    assertEquals(markdown, rewritten)
  }

  @Test
  fun mathFenceRewritesToNativeLatexBlock() {
    val rewritten = NotePreviewRewrite.forPreview("```math\nx^2\n```\n")

    assertFalse(rewritten.contains("```math"))
    assertTrue(rewritten.contains("$$\nx^2\n$$"))
  }

  @Test
  fun supportedCodeLanguageAliasesAreNormalizedForOfflineHighlighting() {
    val rewritten = NotePreviewRewrite.forPreview("```python3\nprint(1)\n```\n")

    assertTrue(rewritten.startsWith("```python\n"))
  }

  @Test
  fun inlineSingleDollarMathUsesMarkwonLatexDelimiters() {
    val rewritten = NotePreviewRewrite.forPreview("Value \$x^2\$ stays inline.")

    assertTrue(rewritten.contains("Value \$\$x^2\$\$ stays inline."))
  }

  @Test
  fun richFencesNestedInsideCodeAreNotRewritten() {
    val markdown = "```text\n```mermaid\ngraph A-->B\n```\n```\n"

    val rewritten = NotePreviewRewrite.forPreview(markdown)

    assertEquals(markdown, rewritten)
  }

  @Test
  fun dollarSignsInCodeAndEscapedTextAreNotRewritten() {
    val markdown = "`\$x\$` and \\$5 stay literal; \$y\$ is math."

    val rewritten = NotePreviewRewrite.forPreview(markdown)

    assertTrue(rewritten.contains("`\$x\$`"))
    assertTrue(rewritten.contains("\\$5"))
    assertTrue(rewritten.contains("\$\$y\$\$"))
  }

  @Test
  fun dollarSignsInsideMultilineCodeSpanRemainLiteral() {
    val markdown = "before `code\n\$not math\$\nstill code` and \$y\$"

    val rewritten = NotePreviewRewrite.forPreview(markdown)

    assertTrue(rewritten.contains("`code\n\$not math\$\nstill code`"))
    assertTrue(rewritten.endsWith("and \$\$y\$\$"))
  }

  @Test
  fun attachmentImageRewritesToLocalScheme() {
    val rewritten = NotePreviewRewrite.forPreview(
      "![p](/attachments/123e4567-e89b-12d3-a456-426614174000.png)",
      assetBase = "pagenote-asset",
    )

    assertTrue(rewritten.contains("pagenote-asset://123e4567-e89b-12d3-a456-426614174000.png"))
    assertFalse(rewritten.contains("/attachments/"))
  }

  @Test
  fun projectAssetImageRewritesToLocalScheme() {
    val rewritten = NotePreviewRewrite.forPreview(
      "![p](assets/123e4567-e89b-12d3-a456-426614174000.png)",
      assetBase = "pagenote-asset",
    )

    assertTrue(rewritten.contains("pagenote-asset://123e4567-e89b-12d3-a456-426614174000.png"))
    assertFalse(rewritten.contains("assets/123e4567-e89b-12d3-a456-426614174000.png"))
  }

}

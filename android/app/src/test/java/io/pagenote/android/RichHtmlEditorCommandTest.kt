package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port batch 4: rich editor style commands and pure editor policies. */
class RichHtmlEditorCommandTest {
  @Test
  fun everyCommandMapsToAWhitelistedExecCommandName() {
    val whitelist = setOf(
      "bold", "italic", "underline", "strikeThrough",
      "justifyLeft", "justifyCenter", "justifyRight",
      "insertUnorderedList", "insertOrderedList",
      "formatBlock", "foreColor", "fontName", "removeFormat",
    )
    val commands = listOf(
      RichHtmlEditorCommand.Bold,
      RichHtmlEditorCommand.Italic,
      RichHtmlEditorCommand.Underline,
      RichHtmlEditorCommand.StrikeThrough,
      RichHtmlEditorCommand.JustifyLeft,
      RichHtmlEditorCommand.JustifyCenter,
      RichHtmlEditorCommand.JustifyRight,
      RichHtmlEditorCommand.BulletList,
      RichHtmlEditorCommand.OrderedList,
      RichHtmlEditorCommand.RemoveFormat,
      RichHtmlEditorCommand.FormatBlock(RichHtmlEditorCommand.Block.HEADING_1),
      RichHtmlEditorCommand.ForeColor("#185abd"),
      RichHtmlEditorCommand.FontName("serif"),
    )
    assertEquals(whitelist, commands.map(RichHtmlEditorCommand::name).toSet())
    // Valueless commands dispatch null.
    assertNull(RichHtmlEditorCommand.Bold.value)
  }

  @Test
  fun formatBlockAcceptsOnlyTheClosedBlockSet() {
    RichHtmlEditorCommand.Block.values().forEach { block ->
      assertEquals(block, RichHtmlEditorCommand.FormatBlock.fromTag(block.tag)?.block)
    }
    assertNull(RichHtmlEditorCommand.FormatBlock.fromTag("script"))
    assertNull(RichHtmlEditorCommand.FormatBlock.fromTag("h6"))
    assertNull(RichHtmlEditorCommand.FormatBlock.fromTag(""))
  }

  @Test
  fun foreColorAcceptsOnlyRRGGBB() {
    assertEquals("#185abd", RichHtmlEditorCommand.ForeColor.create("#185ABD")?.color)
    assertNull(RichHtmlEditorCommand.ForeColor.create("185abd"))
    assertNull(RichHtmlEditorCommand.ForeColor.create("red"))
    assertNull(RichHtmlEditorCommand.ForeColor.create("#12345"))
    assertNull(RichHtmlEditorCommand.ForeColor.create("#1234567"))
  }

  @Test
  fun fontNameResolvesCatalogIdsToCssStacksOnly() {
    val serif = RichHtmlEditorCommand.FontName.create("serif")!!
    assertEquals("serif", serif.fontId)
    assertEquals("serif", serif.value)
    val mono = RichHtmlEditorCommand.FontName.create("mono")!!
    assertEquals("monospace", mono.value)
    // Unknown ids never become commands, so no arbitrary font-family string
    // can reach the editor.
    assertNull(RichHtmlEditorCommand.FontName.create("Comic Sans MS, cursive"))
    assertNull(RichHtmlEditorCommand.FontName.create(""))
  }

  @Test
  fun editorScriptQuotesArgumentsSafely() {
    val command = RichHtmlEditorCommand.ForeColor.create("#185abd")!!
    val script = command.toEditorScript("7")
    assertEquals(
      """window.commandPagenoteRichEdit("foreColor","#185abd","7");""",
      script,
    )
    // A hostile request id stays one quoted argument — its quotes are escaped,
    // so the call cannot be terminated early.
    val hostile = RichHtmlEditorCommand.Bold.toEditorScript("1\"); alert(1)")
    assertEquals(
      """window.commandPagenoteRichEdit("bold",null,"1\"); alert(1)");""",
      hostile,
    )
  }

  @Test
  fun hasVisibleContentIgnoresTagsEntitiesAndWhitespace() {
    assertFalse(RichHtmlEditorPolicy.hasVisibleContent(""))
    assertFalse(RichHtmlEditorPolicy.hasVisibleContent("<p><br></p>"))
    assertFalse(RichHtmlEditorPolicy.hasVisibleContent(" <p>&nbsp;</p>\n"))
    assertTrue(RichHtmlEditorPolicy.hasVisibleContent("<p>hi</p>"))
    assertTrue(RichHtmlEditorPolicy.hasVisibleContent("<h2><b>R</b>ich</h2>"))
    assertTrue(RichHtmlEditorPolicy.hasVisibleContent("<p>1</p>"))
  }
}

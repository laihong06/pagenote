package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownLatexBlockInsertionTest {
  @Test
  fun wrapsRecognizedLatexInTheDesktopBlockTemplate() {
    val edit = MarkdownLatexBlockInsertion.createEdit(
      markdown = "beforeafter",
      selectionStart = 6,
      selectionEnd = 6,
      latex = " \\int_0^1 x\\,dx ",
    )!!
    // 选区被替换为块公式；正文是裁剪后的 LaTeX；模板与桌面完全一致。
    assertEquals(6, edit.replaceStart)
    assertEquals(6, edit.replaceEnd)
    assertEquals("\n\n$$\n\\int_0^1 x\\,dx\n$$\n\n", edit.replacement)
    assertEquals(6 + "\n\n$$\n".length, edit.selectionStart)
    assertEquals(edit.selectionStart + "\\int_0^1 x\\,dx".length, edit.selectionEnd)
  }

  @Test
  fun replacesSelectedTextWithTheRecognizedFormula() {
    val markdown = "a[E=mc^2]b"
    val edit = MarkdownLatexBlockInsertion.createEdit(
      markdown = markdown,
      selectionStart = 1,
      selectionEnd = 9,
      latex = "a^2+b^2=c^2",
    )!!
    assertEquals(
      "a\n\n$$\na^2+b^2=c^2\n$$\n\nb",
      markdown.substring(0, edit.replaceStart) + edit.replacement + markdown.substring(edit.replaceEnd),
    )
  }

  @Test
  fun matchesMarkdownFormulaInsertionTemplateExactly() {
    val sameEdit = MarkdownFormulaInsertion.createEdit("x", 1, 1)
    val latexEdit = MarkdownLatexBlockInsertion.createEdit("x", 1, 1, sameEdit.replacement.substring(5, sameEdit.replacement.length - 5))
    assertEquals(sameEdit.replacement, latexEdit!!.replacement)
  }

  @Test
  fun rejectsBlankLatexAndClampsBrokenSelection() {
    assertNull(MarkdownLatexBlockInsertion.createEdit("x", 0, 1, "   "))
    // 反向/越界选区按 UTF-16 单位夹取。
    val edit = MarkdownLatexBlockInsertion.createEdit("hello", 9, -2, "y")!!
    assertEquals(0, edit.replaceStart)
    assertEquals(5, edit.replaceEnd)
    // 含 emoji 的正文按码元偏移正确选中。
    val emoji = "😀公式"
    val emojiEdit = MarkdownLatexBlockInsertion.createEdit(emoji, 0, emoji.length, "z")!!
    assertEquals(emoji.length, emojiEdit.replaceEnd)
  }
}

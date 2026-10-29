package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 公式素材多行布局测试（touchnotes FormulaMaterial 布局语义复刻）。 */
class FormulaMaterialLayoutTest {
  @Test
  fun singleLineGetsPaddingOnBothAxes() {
    val block = FormulaMaterialLayout.layout(listOf(120 to 60))!!
    assertEquals(120 + 2 * FormulaMaterialLayout.HORIZONTAL_PADDING, block.width)
    assertEquals(60 + 2 * FormulaMaterialLayout.VERTICAL_PADDING, block.height)
    val line = block.lines.single()
    assertEquals(
      (FormulaMaterialLayout.HORIZONTAL_PADDING).toFloat(),
      line.x.toFloat(),
      0f,
    )
    assertEquals(FormulaMaterialLayout.VERTICAL_PADDING, line.y)
    assertEquals(60, line.height)
  }

  @Test
  fun tallLineIsNormalizedToMaxFormulaHeight() {
    val block = FormulaMaterialLayout.layout(listOf(200 to 400))!!
    val line = block.lines.single()
    assertEquals(FormulaMaterialLayout.MAX_FORMULA_HEIGHT, line.height)
    assertEquals(50, line.width) // 200 * 100/400
  }

  @Test
  fun multiLineStacksWithSpacingAndCentersHorizontally() {
    val block = FormulaMaterialLayout.layout(listOf(100 to 40, 300 to 60))!!
    val expectedHeight = 40 + 60 + FormulaMaterialLayout.VERTICAL_SPACING +
      2 * FormulaMaterialLayout.VERTICAL_PADDING
    assertEquals(300 + 2 * FormulaMaterialLayout.HORIZONTAL_PADDING, block.width)
    assertEquals(expectedHeight, block.height)
    assertEquals(FormulaMaterialLayout.VERTICAL_PADDING, block.lines[0].y)
    assertEquals(
      FormulaMaterialLayout.VERTICAL_PADDING + 40 + FormulaMaterialLayout.VERTICAL_SPACING,
      block.lines[1].y,
    )
    // 行水平居中。
    assertEquals((block.width - 100) / 2, block.lines[0].x)
    assertEquals((block.width - 300) / 2, block.lines[1].x)
  }

  @Test
  fun targetHeightScalesWholeBlock() {
    // 画布高 = 40 + 60 + spacing20 + padding2×40 = 200；目标 50 → 0.25。
    val block = FormulaMaterialLayout.layout(listOf(100 to 40, 300 to 60), targetHeight = 50)!!
    assertEquals(50, block.height)
    assertEquals(0.25f, block.lines[0].overallScale, 1e-6f)
    assertEquals(10, block.lines[0].height) // 40 * 0.25
    assertEquals(15, block.lines[1].height) // 60 * 0.25
    assertEquals(10, block.lines[0].y) // VERTICAL_PADDING * 0.25
    // 行间距同步缩放：第二行 y = 10 + 10 + 20*0.25。
    assertEquals(25, block.lines[1].y)
  }

  @Test
  fun rejectsEmptyOversizedOrNonPositiveInput() {
    assertNull(FormulaMaterialLayout.layout(emptyList()))
    assertNull(FormulaMaterialLayout.layout(List(9) { 10 to 10 }))
    assertNull(FormulaMaterialLayout.layout(listOf(0 to 10)))
    assertNull(FormulaMaterialLayout.layout(listOf(10 to -5)))
  }
}

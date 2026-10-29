package io.pagenote.android

import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteMergePlannerTest {
  private val overwrite =
    NoteMergeStrategy(NoteMergePageOrdering.PAGE_ALIGNED, NoteMergeConflictResolution.OVERWRITE_WITH_IMPORTED)
  private val keep =
    NoteMergeStrategy(NoteMergePageOrdering.PAGE_ALIGNED, NoteMergeConflictResolution.KEEP_CURRENT)
  private val combine =
    NoteMergeStrategy(NoteMergePageOrdering.PAGE_ALIGNED, NoteMergeConflictResolution.COMBINE_CURRENT_FIRST)

  @Test
  fun `aligned overwrite reproduces the desktop whole-page import`() {
    val current = PageNotes.join(listOf("第一页旧稿", "邻页保留", "第三页旧稿"))
    val result = NoteMergePlanner.merge(
      currentNotes = current,
      importedPages = mapOf(1 to "第一页新稿", 3 to ""),
      strategy = overwrite,
    )
    assertEquals(PageNotes.join(listOf("第一页新稿", "邻页保留", "")), result.mergedMarkdown)
    assertEquals(listOf(1, 3), result.overwrittenPages)
    // 分类只覆盖导入涉及的页；未在备份中的第 2 页不在任何列表里。
    assertTrue(result.unchangedPages.isEmpty())
    assertTrue(result.changedPageCount == 2)
  }

  @Test
  fun `keep current fills only blank pages and never touches conflicts`() {
    val current = PageNotes.join(listOf("第一页旧稿", "", "第三页旧稿"))
    val result = NoteMergePlanner.merge(
      currentNotes = current,
      importedPages = mapOf(1 to "第一页新稿", 2 to "第二页新稿", 3 to "第三页新稿"),
      strategy = keep,
    )
    assertEquals(PageNotes.join(listOf("第一页旧稿", "第二页新稿", "第三页旧稿")), result.mergedMarkdown)
    assertEquals(listOf(2), result.filledEmptyPages)
    assertEquals(listOf(1, 3), result.keptCurrentPages)
  }

  @Test
  fun `combine keeps current content first and appends imported below`() {
    val current = PageNotes.join(listOf("当前要点", "第二页"))
    val result = NoteMergePlanner.merge(
      currentNotes = current,
      importedPages = mapOf(1 to "导入补充", 2 to "导入覆盖"),
      strategy = combine,
    )
    assertEquals(
      PageNotes.join(listOf("当前要点\n\n导入补充", "第二页\n\n导入覆盖")),
      result.mergedMarkdown,
    )
    assertEquals(listOf(1, 2), result.combinedPages)
    assertTrue(result.overwrittenPages.isEmpty())
  }

  @Test
  fun `identical imported content counts as unchanged in every resolution`() {
    val current = PageNotes.join(listOf("同文"))
    NoteMergePlanner.merge(current, mapOf(1 to "同文"), overwrite).let {
      assertEquals(listOf(1), it.unchangedPages)
      assertTrue(it.changedPageCount == 0)
    }
    NoteMergePlanner.merge(current, mapOf(1 to "同文"), keep).let {
      assertEquals(listOf(1), it.unchangedPages)
    }
    NoteMergePlanner.merge(current, mapOf(1 to "同文"), combine).let {
      assertEquals(listOf(1), it.unchangedPages)
      assertEquals("同文", NotePage.pageAt(it.mergedMarkdown, 1))
    }
  }

  @Test
  fun `aligned import can extend the notes document past the current page count`() {
    val result = NoteMergePlanner.merge(
      currentNotes = PageNotes.join(listOf("第一页")),
      importedPages = mapOf(1 to "第一页新稿", 3 to "第三页新稿"),
      strategy = overwrite,
    )
    assertEquals(PageNotes.join(listOf("第一页新稿", "", "第三页新稿")), result.mergedMarkdown)
    assertEquals(listOf(3), result.filledEmptyPages)
  }

  @Test
  fun `append ordering renumbers imported pages after the current notes`() {
    val current = PageNotes.join(listOf("第一页", "第二页"))
    val result = NoteMergePlanner.merge(
      currentNotes = current,
      importedPages = mapOf(7 to "追加乙", 2 to "追加甲"),
      strategy = NoteMergeStrategy(NoteMergePageOrdering.APPEND_AFTER_LAST),
    )
    assertEquals(PageNotes.join(listOf("第一页", "第二页", "追加甲", "追加乙")), result.mergedMarkdown)
    assertEquals(listOf(3, 4), result.appendedPages)
    // 追加模式下原页内容不动。
    assertEquals("第一页", NotePage.pageAt(result.mergedMarkdown, 1))
    assertEquals("第二页", NotePage.pageAt(result.mergedMarkdown, 2))
  }

  @Test
  fun `append ordering skips empty imported pages`() {
    val result = NoteMergePlanner.merge(
      currentNotes = "",
      importedPages = mapOf(1 to "", 2 to "仅有内容"),
      strategy = NoteMergeStrategy(NoteMergePageOrdering.APPEND_AFTER_LAST),
    )
    assertEquals(PageNotes.join(listOf("", "仅有内容")), result.mergedMarkdown)
    assertEquals(listOf(2), result.appendedPages)
  }

  @Test
  fun `preview reports conflicts and deduplicated asset references`() {
    val current = PageNotes.join(listOf("旧内容 ![图](assets/aaa.png)", "第二页"))
    val imported = mapOf(
      1 to "新内容 ![图](assets/aaa.png) ![图](assets/bbb.png)",
      2 to "![图](assets/bbb.png) ![图](assets/bbb.png)",
    )
    val preview = NoteMergePlanner.preview(current, imported)
    assertEquals(2, preview.importedPageCount)
    assertEquals(listOf(1, 2), preview.conflictPages)
    // 第 1 页引用 2 个资产、第 2 页引用 bbb 两次 → 引用总数 4，去重后 2；
    // aaa.png 已在当前笔记引用，新增引用只有 bbb.png。
    assertEquals(4, preview.importedAssetRefCount)
    assertEquals(listOf("aaa.png", "bbb.png"), preview.uniqueImportedAssetRefs)
    assertEquals(listOf("bbb.png"), preview.newAssetRefs)
    assertEquals(2, preview.currentNotesPageCount)
  }

  @Test
  fun `preview finds no conflicts when imported pages are new or identical`() {
    val current = PageNotes.join(listOf("同文", ""))
    val preview = NoteMergePlanner.preview(current, mapOf(1 to "同文", 2 to "新页", 9 to "远页"))
    assertTrue(preview.conflictPages.isEmpty())
    assertEquals(3, preview.importedPageCount)
  }
}

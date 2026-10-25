package io.pagenote.android

import io.pagenote.core.project.PageNotes

/** 页序策略：导入页按页码对齐覆盖，或作为新页追加到当前笔记末尾。 */
internal enum class NoteMergePageOrdering {
  PAGE_ALIGNED,
  APPEND_AFTER_LAST,
}

/** 冲突策略：目标页同时存在不同内容时的处理方式。 */
internal enum class NoteMergeConflictResolution {
  OVERWRITE_WITH_IMPORTED,
  KEEP_CURRENT,
  COMBINE_CURRENT_FIRST,
}

internal data class NoteMergeStrategy(
  val ordering: NoteMergePageOrdering = NoteMergePageOrdering.PAGE_ALIGNED,
  val conflict: NoteMergeConflictResolution = NoteMergeConflictResolution.OVERWRITE_WITH_IMPORTED,
)

/** 合并前的对话框摘要：页数、冲突页与资产引用去重结果。 */
internal data class NoteMergePreview(
  val importedPageCount: Int,
  val conflictPages: List<Int>,
  val importedAssetRefCount: Int,
  val uniqueImportedAssetRefs: List<String>,
  val newAssetRefs: List<String>,
  val currentNotesPageCount: Int,
)

/** 合并执行结果；列表均为升序页码。 */
internal data class NoteMergeResult(
  val mergedMarkdown: String,
  val overwrittenPages: List<Int>,
  val combinedPages: List<Int>,
  val keptCurrentPages: List<Int>,
  val filledEmptyPages: List<Int>,
  val appendedPages: List<Int>,
  val unchangedPages: List<Int>,
) {
  val changedPageCount: Int
    get() = overwrittenPages.size + combinedPages.size + filledEmptyPages.size + appendedPages.size
}

/**
 * 笔记合并策略（移植差距清单 #15 的 Android 侧增强）。
 *
 * 桌面 JSON 导入是"按页码整页覆盖"；这里在导入合法 JSON 备份后提供
 * 页序（对齐/追加）、冲突（覆盖/保留当前/合并内容）与资产引用去重：
 * 同名 `assets/<uuid>.<ext>` 引用跨页去重，并指出当前笔记未引用过的新资产，
 * 供写入后按项目 assets 目录检查缺失。字节资产不随 JSON 备份携带，
 * 合并只处理 Markdown 引用，不复制或改写资产文件。
 */
internal object NoteMergePlanner {

  fun preview(currentNotes: String, importedPages: Map<Int, String>): NoteMergePreview {
    val currentPages = PageNotes.split(currentNotes)
    val currentRefs = currentPages.flatMap(ProjectAssetRefs::scan).toSet()
    val ordered = importedPages.keys.sorted()
    val importedRefs = ordered.flatMap { page -> ProjectAssetRefs.scan(importedPages.getValue(page)) }
    val uniqueRefs = importedRefs.distinct()
    val conflicts = ordered.filter { page ->
      val current = currentPages.getOrElse(page - 1) { "" }
      val imported = importedPages.getValue(page)
      current.isNotEmpty() && imported.isNotEmpty() && current != imported
    }
    return NoteMergePreview(
      importedPageCount = importedPages.size,
      conflictPages = conflicts,
      importedAssetRefCount = importedRefs.size,
      uniqueImportedAssetRefs = uniqueRefs,
      newAssetRefs = uniqueRefs.filterNot { it in currentRefs },
      currentNotesPageCount = currentPages.size,
    )
  }

  fun merge(
    currentNotes: String,
    importedPages: Map<Int, String>,
    strategy: NoteMergeStrategy,
  ): NoteMergeResult = when (strategy.ordering) {
    NoteMergePageOrdering.PAGE_ALIGNED -> mergeAligned(currentNotes, importedPages, strategy.conflict)
    NoteMergePageOrdering.APPEND_AFTER_LAST -> mergeAppend(currentNotes, importedPages)
  }

  private fun mergeAligned(
    currentNotes: String,
    importedPages: Map<Int, String>,
    conflict: NoteMergeConflictResolution,
  ): NoteMergeResult {
    val pages = PageNotes.split(currentNotes).toMutableList()
    val overwritten = mutableListOf<Int>()
    val combined = mutableListOf<Int>()
    val kept = mutableListOf<Int>()
    val filled = mutableListOf<Int>()
    val unchanged = mutableListOf<Int>()
    importedPages.keys.sorted().forEach { page ->
      require(page >= 1) { "note pages are one-based" }
      while (pages.size < page) pages += ""
      val current = pages[page - 1]
      val imported = importedPages.getValue(page)
      val resolved = when {
        current == imported -> {
          unchanged += page
          current
        }
        current.isEmpty() -> {
          filled += page
          imported
        }
        imported.isEmpty() -> when (conflict) {
          // 空导入内容视为"清空该页"的覆盖意图。
          NoteMergeConflictResolution.KEEP_CURRENT -> {
            kept += page
            current
          }
          else -> {
            overwritten += page
            imported
          }
        }
        else -> when (conflict) {
          NoteMergeConflictResolution.OVERWRITE_WITH_IMPORTED -> {
            overwritten += page
            imported
          }
          NoteMergeConflictResolution.KEEP_CURRENT -> {
            kept += page
            current
          }
          NoteMergeConflictResolution.COMBINE_CURRENT_FIRST -> {
            combined += page
            combine(current, imported)
          }
        }
      }
      pages[page - 1] = resolved
    }
    return NoteMergeResult(
      mergedMarkdown = PageNotes.join(pages),
      overwrittenPages = overwritten,
      combinedPages = combined,
      keptCurrentPages = kept,
      filledEmptyPages = filled,
      appendedPages = emptyList(),
      unchangedPages = unchanged,
    )
  }

  private fun mergeAppend(currentNotes: String, importedPages: Map<Int, String>): NoteMergeResult {
    val pages = PageNotes.split(currentNotes).toMutableList()
    val appended = mutableListOf<Int>()
    // 追加模式按导入页码升序接到当前笔记末尾；空页跳过，不产生空白新页。
    importedPages.keys.sorted().forEach { importedPage ->
      val content = importedPages.getValue(importedPage)
      if (content.isEmpty()) return@forEach
      pages += content
      appended += pages.size
    }
    return NoteMergeResult(
      mergedMarkdown = PageNotes.join(pages),
      overwrittenPages = emptyList(),
      combinedPages = emptyList(),
      keptCurrentPages = emptyList(),
      filledEmptyPages = emptyList(),
      appendedPages = appended,
      unchangedPages = emptyList(),
    )
  }

  private fun combine(current: String, imported: String): String = when {
    current.isEmpty() -> imported
    imported.isEmpty() -> current
    else -> "$current\n\n$imported"
  }
}

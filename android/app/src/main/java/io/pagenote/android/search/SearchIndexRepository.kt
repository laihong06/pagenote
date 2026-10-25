package io.pagenote.android.search

import io.pagenote.core.search.SearchDoc
import io.pagenote.core.search.SearchDocKey
import io.pagenote.core.search.SearchDocumentKind
import io.pagenote.core.search.SearchHit
import io.pagenote.core.search.SearchIndexStore
import io.pagenote.core.search.SearchQueryPlan
import io.pagenote.core.search.SearchQueryPolicy
import io.pagenote.core.search.SearchRect
import io.pagenote.core.search.SnippetBuilder

/** Room 仓储：core-search [SearchIndexStore] 的实现 + 检索查询。 */
class SearchIndexRepository(private val dao: SearchDocDao) : SearchIndexStore {

  override suspend fun revisionOf(key: SearchDocKey): String? =
    dao.revisionOf(key.projectId, key.page, key.kind.key)

  override suspend fun put(doc: SearchDoc) = dao.upsert(doc.toEntity())

  override suspend fun removePage(projectId: String, page: Int, kinds: Set<SearchDocumentKind>) {
    if (kinds.isNotEmpty()) dao.deletePage(projectId, page, kinds.map { it.key })
  }

  override suspend fun removeProject(projectId: String) = dao.deleteProject(projectId)

  /** 范围收窄：删除项目下指定类别的全部索引条目。 */
  suspend fun removeKinds(projectId: String, kinds: Set<SearchDocumentKind>) {
    if (kinds.isNotEmpty()) dao.deleteKindsForProject(projectId, kinds.map { it.key })
  }

  /** 按查询计划路由 FTS MATCH 或 LIKE 扫描，产出带摘要的结果列表。 */
  suspend fun search(
    projectId: String,
    query: String,
    kinds: Set<SearchDocumentKind>,
    limit: Int = DEFAULT_LIMIT,
  ): List<SearchHit> {
    require(limit > 0) { "search limit must be positive" }
    val kindKeys = kinds.map { it.key }
    if (kindKeys.isEmpty()) return emptyList()
    val entities = when (val plan = SearchQueryPolicy.plan(query)) {
      is SearchQueryPlan.Empty -> return emptyList()
      is SearchQueryPlan.FullText -> dao.searchFullText(projectId, kindKeys, plan.match, limit)
      is SearchQueryPlan.Contains -> dao.searchContains(projectId, kindKeys, plan.pattern, limit)
    }
    return entities.map { it.toHit(query) }
  }

  suspend fun countForProject(projectId: String): Int = dao.countForProject(projectId)

  private fun SearchDoc.toEntity(): SearchDocEntity {
    val now = System.currentTimeMillis()
    return SearchDocEntity(
      projectId = projectId,
      page = page,
      kind = kind.key,
      content = content,
      revision = revision,
      hasRect = rect != null,
      rectLeft = rect?.left ?: 0,
      rectTop = rect?.top ?: 0,
      rectRight = rect?.right ?: 0,
      rectBottom = rect?.bottom ?: 0,
      updatedAt = now,
    )
  }

  private fun SearchDocEntity.toHit(query: String): SearchHit = SearchHit(
    projectId = projectId,
    page = page,
    kind = SearchDocumentKind.fromKey(kind) ?: SearchDocumentKind.SOURCE,
    snippet = SnippetBuilder.build(content, query),
    rect = if (hasRect) SearchRect(rectLeft, rectTop, rectRight, rectBottom) else null,
  )

  companion object {
    const val DEFAULT_LIMIT = 50
  }
}

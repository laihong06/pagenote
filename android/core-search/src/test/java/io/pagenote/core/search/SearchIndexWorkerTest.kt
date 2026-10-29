package io.pagenote.core.search

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexWorkerTest {
  /** 内存假件：记录 revision 与写入序列，模拟 Room 仓储的增量语义。 */
  private class FakeStore : SearchIndexStore {
    val revisions = mutableMapOf<SearchDocKey, String>()
    val writes = mutableListOf<SearchDoc>()
    val removedPages = mutableListOf<Triple<String, Int, Set<SearchDocumentKind>>>()
    val removedProjects = mutableListOf<String>()

    override suspend fun revisionOf(key: SearchDocKey): String? = revisions[key]

    override suspend fun put(doc: SearchDoc) {
      writes += doc
      revisions[doc.key] = doc.revision
    }

    override suspend fun removePage(projectId: String, page: Int, kinds: Set<SearchDocumentKind>) {
      removedPages += Triple(projectId, page, kinds)
      revisions.keys.removeAll { key ->
        key.projectId == projectId && key.page == page && key.kind in kinds
      }
    }

    override suspend fun removeProject(projectId: String) {
      removedProjects += projectId
      revisions.keys.removeAll { it.projectId == projectId }
    }
  }

  private fun doc(page: Int, revision: String, kind: SearchDocumentKind = SearchDocumentKind.NOTE) =
    SearchDoc("p1", page, kind, "content $page", revision)

  @Test
  fun unchangedRevisionIsSkipped() = runBlocking {
    val store = FakeStore()
    SearchIndexWorker.apply(listOf(SearchIndexRequest.Upsert(doc(1, "r1"))), store)
    SearchIndexWorker.apply(listOf(SearchIndexRequest.Upsert(doc(1, "r1"))), store)
    assertEquals(1, store.writes.size)
  }

  @Test
  fun changedRevisionIsRewritten() = runBlocking {
    val store = FakeStore()
    SearchIndexWorker.apply(listOf(SearchIndexRequest.Upsert(doc(1, "r1"))), store)
    SearchIndexWorker.apply(listOf(SearchIndexRequest.Upsert(doc(1, "r2"))), store)
    assertEquals(listOf("r1", "r2"), store.writes.map { it.revision })
    assertEquals("r2", store.revisionOf(SearchDocKey("p1", 1, SearchDocumentKind.NOTE)))
  }

  @Test
  fun removePageRemovesOnlyRequestedKinds() = runBlocking {
    val store = FakeStore()
    SearchIndexWorker.apply(
      listOf(
        SearchIndexRequest.Upsert(doc(1, "r1", SearchDocumentKind.NOTE)),
        SearchIndexRequest.Upsert(doc(1, "r1", SearchDocumentKind.OCR)),
      ),
      store,
    )
    SearchIndexWorker.apply(
      listOf(SearchIndexRequest.RemovePage("p1", 1, setOf(SearchDocumentKind.OCR))),
      store,
    )
    assertNull(store.revisionOf(SearchDocKey("p1", 1, SearchDocumentKind.OCR)))
    assertEquals("r1", store.revisionOf(SearchDocKey("p1", 1, SearchDocumentKind.NOTE)))
    assertEquals(listOf(Triple("p1", 1, setOf(SearchDocumentKind.OCR))), store.removedPages)
  }

  @Test
  fun removeProjectClearsEverythingForThatProject() = runBlocking {
    val store = FakeStore()
    SearchIndexWorker.apply(
      listOf(
        SearchIndexRequest.Upsert(doc(1, "r1")),
        SearchIndexRequest.Upsert(doc(2, "r1").copy(projectId = "p2")),
      ),
      store,
    )
    SearchIndexWorker.apply(listOf(SearchIndexRequest.RemoveProject("p1")), store)
    assertNull(store.revisionOf(SearchDocKey("p1", 1, SearchDocumentKind.NOTE)))
    assertEquals("r1", store.revisionOf(SearchDocKey("p2", 2, SearchDocumentKind.NOTE)))
    assertEquals(listOf("p1"), store.removedProjects)
  }

  @Test
  fun emptyBatchIsANoOp() = runBlocking {
    val store = FakeStore()
    SearchIndexWorker.apply(emptyList(), store)
    assertTrue(store.writes.isEmpty())
  }
}

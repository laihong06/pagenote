package io.pagenote.android

import kotlinx.coroutines.runBlocking
import io.pagenote.android.search.ProjectSearchIndexer
import io.pagenote.core.search.SearchDoc
import io.pagenote.core.search.SearchDocKey
import io.pagenote.core.search.SearchDocumentKind
import io.pagenote.core.search.SearchIndexSettings
import io.pagenote.core.search.SearchIndexStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSearchIndexerTest {
  private class FakeStore : SearchIndexStore {
    val revisions = mutableMapOf<SearchDocKey, String>()
    val writes = mutableListOf<SearchDoc>()

    override suspend fun revisionOf(key: SearchDocKey): String? = revisions[key]
    override suspend fun put(doc: SearchDoc) {
      writes += doc
      revisions[doc.key] = doc.revision
    }
    override suspend fun removePage(projectId: String, page: Int, kinds: Set<SearchDocumentKind>) {
      revisions.keys.removeAll { it.projectId == projectId && it.page == page && it.kind in kinds }
    }
    override suspend fun removeProject(projectId: String) {
      revisions.keys.removeAll { it.projectId == projectId }
    }
  }

  private fun doc(page: Int, kind: SearchDocumentKind, revision: String = "r1") =
    SearchDoc("p1", page, kind, "text $page", revision)

  @Test
  fun submitsOnlyKindsInsideScope() = runBlocking {
    val store = FakeStore()
    val indexer = ProjectSearchIndexer(
      store,
      SearchIndexSettings(indexSource = false, indexNotes = true, indexOcr = true, indexTranscripts = true),
    )
    indexer.submit(doc(1, SearchDocumentKind.SOURCE))
    indexer.submit(doc(1, SearchDocumentKind.NOTE))
    indexer.drainNow()
    assertEquals(listOf(SearchDocumentKind.NOTE), store.writes.map { it.kind })
    indexer.close()
  }

  @Test
  fun conflationAndRevisionGatingApplyThroughDrain() = runBlocking {
    val store = FakeStore()
    val indexer = ProjectSearchIndexer(store)
    indexer.submit(doc(1, SearchDocumentKind.NOTE, "r1"))
    indexer.submit(doc(1, SearchDocumentKind.NOTE, "r2"))
    indexer.submit(doc(2, SearchDocumentKind.OCR, "o1"))
    indexer.drainNow()
    assertEquals(2, store.writes.size)
    assertEquals("r2", store.revisions[SearchDocKey("p1", 1, SearchDocumentKind.NOTE)])

    // 同 revision 再提交：增量闸门跳过。
    indexer.submit(doc(1, SearchDocumentKind.NOTE, "r2"))
    indexer.drainNow()
    assertEquals(2, store.writes.size)
    indexer.close()
  }

  @Test
  fun removePageDropsPendingUpsertsForThatPage() = runBlocking {
    val store = FakeStore()
    val indexer = ProjectSearchIndexer(store)
    indexer.submit(doc(1, SearchDocumentKind.NOTE))
    indexer.removePage("p1", 1, SearchDocumentKind.ALL)
    indexer.drainNow()
    assertTrue(store.writes.isEmpty())
    assertNull(store.revisions[SearchDocKey("p1", 1, SearchDocumentKind.NOTE)])
    indexer.close()
  }

  @Test
  fun settingsSnapshotIsReadableForSearchRouting() {
    val indexer = ProjectSearchIndexer(FakeStore())
    assertEquals(SearchDocumentKind.entries.toSet(), indexer.settings.kinds())
    indexer.settings = SearchIndexSettings(indexOcr = false)
    assertEquals(
      SearchDocumentKind.entries.toSet() - SearchDocumentKind.OCR,
      indexer.settings.kinds(),
    )
    indexer.close()
  }
}

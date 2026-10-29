package io.pagenote.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexQueueTest {
  private fun doc(page: Int, kind: SearchDocumentKind = SearchDocumentKind.NOTE, revision: String = "r1") =
    SearchDoc(projectId = "p1", page = page, kind = kind, content = "text $page", revision = revision)

  @Test
  fun drainReturnsAndClearsPendingRequests() {
    val queue = SearchIndexQueue()
    queue.offer(SearchIndexRequest.Upsert(doc(1)))
    queue.offer(SearchIndexRequest.Upsert(doc(2)))
    assertEquals(2, queue.drain().size)
    assertEquals(0, queue.size)
    assertEquals(0, queue.drain().size)
  }

  @Test
  fun upsertsToTheSameKeyKeepOnlyTheLatest() {
    val queue = SearchIndexQueue()
    queue.offer(SearchIndexRequest.Upsert(doc(1, revision = "r1")))
    queue.offer(SearchIndexRequest.Upsert(doc(1, revision = "r2")))
    val drained = queue.drain()
    assertEquals(1, drained.size)
    assertEquals("r2", (drained.single() as SearchIndexRequest.Upsert).doc.revision)
  }

  @Test
  fun samePageDifferentKindsAreNotConflated() {
    val queue = SearchIndexQueue()
    queue.offer(SearchIndexRequest.Upsert(doc(1, SearchDocumentKind.NOTE)))
    queue.offer(SearchIndexRequest.Upsert(doc(1, SearchDocumentKind.OCR)))
    assertEquals(2, queue.drain().size)
  }

  @Test
  fun removePageDropsPendingUpsertsOfThatPageAndKind() {
    val queue = SearchIndexQueue()
    queue.offer(SearchIndexRequest.Upsert(doc(1, SearchDocumentKind.OCR)))
    queue.offer(SearchIndexRequest.Upsert(doc(1, SearchDocumentKind.NOTE)))
    queue.offer(SearchIndexRequest.RemovePage("p1", 1, setOf(SearchDocumentKind.OCR)))
    val drained = queue.drain()
    assertEquals(2, drained.size)
    assertTrue(drained[0] is SearchIndexRequest.Upsert)
    assertEquals(SearchDocumentKind.NOTE, (drained[0] as SearchIndexRequest.Upsert).doc.kind)
    assertEquals(SearchIndexRequest.RemovePage("p1", 1, setOf(SearchDocumentKind.OCR)), drained[1])
  }

  @Test
  fun upsertAfterRemovePageStillSurvives() {
    val queue = SearchIndexQueue()
    queue.offer(SearchIndexRequest.RemovePage("p1", 1, setOf(SearchDocumentKind.NOTE)))
    queue.offer(SearchIndexRequest.Upsert(doc(1)))
    val drained = queue.drain()
    assertEquals(2, drained.size)
    assertTrue(drained[0] is SearchIndexRequest.RemovePage)
    assertTrue(drained[1] is SearchIndexRequest.Upsert)
  }

  @Test
  fun removeProjectClearsEveryPendingRequestForThatProject() {
    val queue = SearchIndexQueue()
    queue.offer(SearchIndexRequest.Upsert(doc(1)))
    queue.offer(SearchIndexRequest.Upsert(doc(2, kind = SearchDocumentKind.OCR)))
    queue.offer(SearchIndexRequest.RemovePage("p1", 3, SearchDocumentKind.ALL))
    queue.offer(SearchIndexRequest.Upsert(doc(2).copy(projectId = "p2")))
    queue.offer(SearchIndexRequest.RemoveProject("p1"))
    val drained = queue.drain()
    assertEquals(2, drained.size)
    assertEquals("p2", (drained[0] as SearchIndexRequest.Upsert).doc.projectId)
    assertEquals(SearchIndexRequest.RemoveProject("p1"), drained[1])
  }

  @Test
  fun preservesSubmissionOrderAcrossKinds() {
    val queue = SearchIndexQueue()
    queue.offer(SearchIndexRequest.Upsert(doc(1)))
    queue.offer(SearchIndexRequest.RemovePage("p1", 2, SearchDocumentKind.ALL))
    queue.offer(SearchIndexRequest.Upsert(doc(3)))
    val drained = queue.drain()
    assertTrue(drained[0] is SearchIndexRequest.Upsert)
    assertTrue(drained[1] is SearchIndexRequest.RemovePage)
    assertTrue(drained[2] is SearchIndexRequest.Upsert)
  }
}

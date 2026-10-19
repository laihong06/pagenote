package io.pagenote.android

import androidx.lifecycle.SavedStateHandle
import io.pagenote.feature.pen.PenCanvasTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSessionCheckpointTest {
  @Test
  fun savedStateRestoresProjectPageViewportAndPenTool() {
    val original = ReaderSessionCheckpoint(
      accessTreeUri = "content://provider/tree/project",
      rootDocumentId = "root-id",
      projectId = "project-id",
      pageIndex = 8,
      viewportBookmark = PdfViewportBookmark(2.1f, 0.2f, 0.7f),
      penToolName = PenCanvasTool.HIGHLIGHTER.name,
    )
    val handle = SavedStateHandle()

    original.saveTo(handle)

    assertEquals(original, ReaderSessionCheckpoint.restoreFrom(handle))
    assertEquals(PenCanvasTool.HIGHLIGHTER, requireNotNull(ReaderSessionCheckpoint.restoreFrom(handle)).resolvedPenTool())
  }

  @Test
  fun malformedViewportIsReplacedWithDefaultAndPageIsClamped() {
    val handle = SavedStateHandle(
      mapOf(
        "reader.accessTreeUri" to "content://provider/tree/project",
        "reader.rootDocumentId" to "root-id",
        "reader.projectId" to "project-id",
        "reader.pageIndex" to -4,
        "reader.viewportScale" to Float.NaN,
        "reader.viewportCenterX" to Float.POSITIVE_INFINITY,
      ),
    )

    val restored = requireNotNull(ReaderSessionCheckpoint.restoreFrom(handle))

    assertEquals(0, restored.pageIndex)
    assertEquals(PdfViewportBookmark.DEFAULT, restored.viewportBookmark)
    assertEquals(PenCanvasTool.PEN, restored.resolvedPenTool())
  }

  @Test
  fun incompleteCheckpointIsIgnored() {
    assertNull(ReaderSessionCheckpoint.restoreFrom(SavedStateHandle()))
  }
}

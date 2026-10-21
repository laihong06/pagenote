package io.pagenote.android

import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectOpenTargetTest {
  private val authority = "com.android.externalstorage.documents"
  private val parentId = "primary:pagenote"
  private val parentTree = DocumentsContract.buildTreeDocumentUri(authority, parentId)

  @Test
  fun selectedTreeUsesItsRootAndNeedsPersistablePermission() {
    val target = ProjectOpenTarget.fromSelectedTree(parentTree)

    assertEquals(parentTree, target.accessTreeUri)
    assertEquals(parentId, target.rootDocumentId)
    assertTrue(target.needsPersistablePermission)
  }

  @Test
  fun createdProjectReusesParentGrantAndUsesChildDocumentIdAsRoot() {
    val projectId = "$parentId/AndroidTest"
    val projectDocument = DocumentsContract.buildDocumentUriUsingTree(parentTree, projectId)

    val target = ProjectOpenTarget.fromCreatedProject(parentTree, projectDocument)

    assertEquals(parentTree, target.accessTreeUri)
    assertEquals(projectId, target.rootDocumentId)
    assertFalse(target.needsPersistablePermission)
  }
}

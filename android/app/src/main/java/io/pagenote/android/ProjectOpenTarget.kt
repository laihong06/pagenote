package io.pagenote.android

import android.net.Uri
import android.provider.DocumentsContract

data class ProjectOpenTarget(
  val accessTreeUri: Uri,
  val rootDocumentId: String,
  val needsPersistablePermission: Boolean,
) {
  companion object {
    fun fromSelectedTree(treeUri: Uri) = ProjectOpenTarget(
      accessTreeUri = treeUri,
      rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri),
      needsPersistablePermission = true,
    )

    fun fromCreatedProject(parentTreeUri: Uri, projectDocumentUri: Uri) = ProjectOpenTarget(
      accessTreeUri = parentTreeUri,
      rootDocumentId = DocumentsContract.getDocumentId(projectDocumentUri),
      needsPersistablePermission = false,
    )
  }
}

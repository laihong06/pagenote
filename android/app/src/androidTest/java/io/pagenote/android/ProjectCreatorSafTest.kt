package io.pagenote.android

import android.webkit.MimeTypeMap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectCreatorSafTest {
  @Test
  fun manifestMimeHasNoProviderFilenameExtension() {
    val extension = MimeTypeMap.getSingleton()
      .getExtensionFromMimeType(ProjectCreator.PROJECT_MANIFEST_MIME_TYPE)

    assertEquals("", extension.orEmpty())
  }
}

package io.pagenote.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Spanned
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.Markwon
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteAssetSafIntegrationTest {
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val resolver get() = context.contentResolver

  @Test
  fun attachmentImageResolvesThroughProjectDocumentTreeAndContentResolver() {
    resetProvider(NoteAssetSafTestProvider.FAILURE_NONE)
    val (renderer, image) = renderAttachment()

    loadAndAwaitImage(renderer, image)

    assertTrue("provider image stream must finish decoding", image.hasResult())
    val bitmap = (image.result as? BitmapDrawable)?.bitmap
    assertTrue("successful SAF lookup must produce a decoded bitmap", bitmap != null)
    assertEquals(Color.MAGENTA, requireNotNull(bitmap).getPixel(0, 0))
    assertProviderStats(expectedQueries = 2, expectedOpens = 1)
  }

  @Test
  fun childQueryAuthorizationFailureUsesSafePlaceholder() {
    resetProvider(NoteAssetSafTestProvider.FAILURE_QUERY)
    val (renderer, image) = renderAttachment()

    loadAndAwaitImage(renderer, image)

    assertTrue("query denial must become a completed image state", image.hasResult())
    assertSafeMissingImage(image.result)
    assertProviderStats(expectedQueries = 2, expectedOpens = 0)
  }

  @Test
  fun documentOpenAuthorizationFailureUsesSafePlaceholder() {
    resetProvider(NoteAssetSafTestProvider.FAILURE_OPEN)
    val (renderer, image) = renderAttachment()

    loadAndAwaitImage(renderer, image)

    assertTrue("open denial must become a completed image state", image.hasResult())
    assertSafeMissingImage(image.result)
    assertProviderStats(expectedQueries = 2, expectedOpens = 1)
  }

  private fun renderAttachment(): Pair<Markwon, AsyncDrawable> {
    val treeUri = DocumentsContract.buildTreeDocumentUri(
      NoteAssetSafTestProvider.AUTHORITY,
      NoteAssetSafTestProvider.TREE_DOCUMENT_ID,
    )
    val sourceDirectoryUri = DocumentsContract.buildDocumentUriUsingTree(
      treeUri,
      NoteAssetSafTestProvider.SOURCE_DOCUMENT_ID,
    )
    val renderer = NoteMarkdownRenderer.createPreview(context, sourceDirectoryUri, Color.BLACK)
    val markdown = NotePreviewRewrite.forPreview(
      "![fixture](/attachments/${NoteAssetSafTestProvider.IMAGE_NAME})",
    )
    val rendered = renderer.render(renderer.parse(markdown)) as Spanned
    val image = rendered.getSpans(0, rendered.length, AsyncDrawableSpan::class.java).single().drawable
    return renderer to image
  }

  private fun resetProvider(failureMode: String) {
    resolver.call(
      NoteAssetSafTestProvider.AUTHORITY,
      NoteAssetSafTestProvider.METHOD_RESET,
      null,
      Bundle().apply { putString(NoteAssetSafTestProvider.EXTRA_FAILURE_MODE, failureMode) },
    )
  }

  private fun assertProviderStats(expectedQueries: Int, expectedOpens: Int) {
    val stats = requireNotNull(
      resolver.call(NoteAssetSafTestProvider.AUTHORITY, NoteAssetSafTestProvider.METHOD_STATS, null, null),
    )
    assertEquals(expectedQueries, stats.getInt(NoteAssetSafTestProvider.EXTRA_QUERY_COUNT))
    assertEquals(expectedOpens, stats.getInt(NoteAssetSafTestProvider.EXTRA_OPEN_COUNT))
  }

  private fun assertSafeMissingImage(drawable: Drawable) {
    val expected = context.getString(
      R.string.note_rich_image_missing,
      NoteAssetSafTestProvider.IMAGE_NAME,
    )
    val placeholder = assertVisiblePlaceholder(drawable, expected)
    assertFalse(placeholder.message.contains(NoteAssetSafTestProvider.PRIVATE_PROVIDER_DETAIL))
    assertFalse(placeholder.message.contains(NoteAssetSafTestProvider.AUTHORITY))
    assertFalse(placeholder.message.contains("/src/assets/"))
  }

  private fun assertVisiblePlaceholder(drawable: Drawable, expectedMessage: String): NoteImagePlaceholderDrawable {
    val placeholder = drawable as? NoteImagePlaceholderDrawable
    assertTrue("provider failures must show the existing warning placeholder", placeholder != null)
    val warning = requireNotNull(placeholder)
    assertEquals(expectedMessage, warning.message)
    assertTrue(warning.intrinsicWidth > 0)
    assertTrue(warning.intrinsicHeight > 0)
    val bitmap = Bitmap.createBitmap(warning.intrinsicWidth, warning.intrinsicHeight, Bitmap.Config.ARGB_8888)
    warning.setBounds(0, 0, bitmap.width, bitmap.height)
    warning.draw(Canvas(bitmap))
    val background = Color.rgb(255, 246, 220)
    val border = Color.rgb(225, 188, 111)
    var textPixels = 0
    for (y in 0 until bitmap.height) {
      for (x in 0 until bitmap.width) {
        val color = bitmap.getPixel(x, y)
        if (color != Color.TRANSPARENT && color != background && color != border) textPixels++
      }
    }
    assertTrue("warning text must draw visible pixels", textPixels > 4)
    return warning
  }

  private fun loadAndAwaitImage(renderer: Markwon, image: AsyncDrawable) {
    image.setCallback2(object : Drawable.Callback {
      override fun invalidateDrawable(who: Drawable) = Unit
      override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = Unit
      override fun unscheduleDrawable(who: Drawable, what: Runnable) = Unit
    })
    renderer.configuration().asyncDrawableLoader().load(image)
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val deadline = android.os.SystemClock.uptimeMillis() + 2_000
    while (!image.hasResult() && android.os.SystemClock.uptimeMillis() < deadline) {
      instrumentation.waitForIdleSync()
      Thread.sleep(10)
    }
  }
}

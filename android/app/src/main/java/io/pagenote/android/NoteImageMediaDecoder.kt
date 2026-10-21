package io.pagenote.android

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import io.noties.markwon.image.MediaDecoder
import java.io.InputStream

internal class NoteImageMediaDecoder(
  private val context: Context,
  private val textColor: Int,
) : MediaDecoder() {
  override fun decode(contentType: String?, inputStream: InputStream): Drawable {
    val bitmap = BitmapFactory.decodeStream(inputStream)
      ?: return NoteImagePlaceholderDrawable(
        context,
        context.getString(
          R.string.note_rich_image_decode_failed,
          contentType.orEmpty().take(MAX_SOURCE_LENGTH),
        ),
        textColor,
      )
    return BitmapDrawable(context.resources, bitmap)
  }

  override fun supportedTypes(): Collection<String> = emptyList()

  private companion object {
    const val MAX_SOURCE_LENGTH = 192
  }
}

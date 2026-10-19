package io.pagenote.android

import android.content.Context
import android.net.Uri
import io.noties.markwon.image.ImageItem
import io.noties.markwon.image.SchemeHandler
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

internal class MermaidImageSchemeHandler(
  private val context: Context,
  private val coordinator: MermaidRenderCoordinator,
  private val textColor: Int,
) : SchemeHandler() {
  override fun supportedSchemes(): Collection<String> = listOf(MermaidSourceCodec.SCHEME)

  override fun handle(raw: String, uri: Uri): ImageItem {
    if (uri.scheme != MermaidSourceCodec.SCHEME) return failedImage(raw.take(MAX_SOURCE_LABEL_LENGTH))
    val source = runCatching { MermaidSourceCodec.decode(raw) }.getOrNull()
      ?: return failedImage(raw.take(MAX_SOURCE_LABEL_LENGTH))
    if (source.isEmpty()) {
      return ImageItem.withResult(
        NoteImagePlaceholderDrawable(context, context.getString(R.string.note_rich_mermaid_empty), textColor),
      )
    }

    return when (val rendered = coordinator.render(source)) {
      is MermaidRenderCoordinator.Result.Ready -> ImageItem.withDecodingNeeded(
        MermaidSvgMediaDecoder.CONTENT_TYPE,
        ByteArrayInputStream(rendered.svg.toByteArray(StandardCharsets.UTF_8)),
      )
      MermaidRenderCoordinator.Result.Empty -> ImageItem.withResult(
        NoteImagePlaceholderDrawable(context, context.getString(R.string.note_rich_mermaid_empty), textColor),
      )
      MermaidRenderCoordinator.Result.Failed -> failedImage(source)
    }
  }

  private fun failedImage(source: String) = ImageItem.withResult(
    NoteImagePlaceholderDrawable(
      context,
      context.getString(R.string.note_rich_mermaid_error, sourcePreview(source)),
      textColor,
    ),
  )

  private fun sourcePreview(source: String): String =
    if (source.length <= MAX_SOURCE_LABEL_LENGTH) source else source.take(MAX_SOURCE_LABEL_LENGTH - 3) + "..."

  private companion object {
    const val MAX_SOURCE_LABEL_LENGTH = 180
  }
}

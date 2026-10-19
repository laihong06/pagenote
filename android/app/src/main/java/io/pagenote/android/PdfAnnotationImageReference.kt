package io.pagenote.android

internal object PdfAnnotationImageReference {
  private val assetPathPattern = Regex(
    "^/attachments/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(png|jpe?g|gif|webp))$",
  )

  fun assetName(raw: String): String? = assetPathPattern.matchEntire(raw)?.groupValues?.get(1)
}

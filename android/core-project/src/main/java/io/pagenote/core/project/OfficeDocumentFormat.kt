package io.pagenote.core.project

/**
 * Office 源格式门控（task 36 本地引擎切片）。
 *
 * 只有 [OfficePdfEngine.supportedFormats] 里出现过的格式才允许进入本地转换/编辑；
 * 在引擎接入之前该集合为空，项目 opener 对这些扩展名的拒绝行为保持不变。
 */
enum class OfficeDocumentFormat(
  val extension: String,
  val mimeType: String,
  val isTextDocument: Boolean,
) {
  DOC("doc", "application/msword", true),
  DOCX(
    "docx",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    true,
  ),
  PPT("ppt", "application/vnd.ms-powerpoint", false),
  PPTX(
    "pptx",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    false,
  ),
  ;

  val displayName: String get() = extension.uppercase()

  companion object {
    fun fromExtension(extension: String?): OfficeDocumentFormat? {
      val normalized = extension?.trim()?.lowercase()?.substringAfterLast('.') ?: return null
      return entries.firstOrNull { it.extension == normalized }
    }
  }
}

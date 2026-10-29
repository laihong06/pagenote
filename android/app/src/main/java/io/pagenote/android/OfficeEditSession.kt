package io.pagenote.android

import io.pagenote.core.project.OfficeDocumentFormat
import io.pagenote.core.project.OfficeSourceRevision
import java.io.File

/**
 * 本地 Office 编辑会话（task 36 编辑/Save & Return 切片）。
 *
 * 引擎把源转换为 PDF 预览成功后建立会话：工作副本落在 app 自有目录，
 * 原始源字节以打开时 revision 为准。编辑器写回工作副本后置 [dirty]，
 * Save & Return 用 [OfficeWriteBack] 决策并执行 revision 校验写回。
 * LibreOfficeKit 接入前没有引擎，会话不会被创建；本类只承载已成立的契约。
 */
internal class OfficeEditSession(
  val projectId: String,
  val format: OfficeDocumentFormat,
  val openingRevision: OfficeSourceRevision,
  val workingCopyFile: File,
  val sourceName: String,
) {
  @Volatile
  var dirty: Boolean = false

  /** 当前工作副本 revision；工作副本缺失/不可读视为打开时 revision。 */
  fun currentWorkingRevision(): OfficeSourceRevision =
    runCatching {
      workingCopyFile.inputStream().use(OfficeSourceRevision::from)
    }.getOrDefault(openingRevision)
}

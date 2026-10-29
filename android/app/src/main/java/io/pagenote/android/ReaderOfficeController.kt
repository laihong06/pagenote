package io.pagenote.android

import android.content.Context
import android.provider.DocumentsContract
import io.pagenote.core.project.OfficeDocumentFormat
import io.pagenote.core.project.OfficePdfConversionResult
import io.pagenote.core.project.OfficeSaveReadiness
import io.pagenote.core.project.OfficeSourceRevision
import io.pagenote.core.project.OfficeWriteBack
import io.pagenote.core.project.OfficeWriteBackPlan
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Office 本地引擎切片的阅读器动作（task 36）：office_edit / save_return 的实现体。
 *
 * 从 ReaderHome 抽出以控制组合方法大小。桌面宿主运行时（COM/AppleScript）不移植；
 * 引擎缺失/格式未放开时如实报告，不伪造成功、不触碰源文件。
 */
internal class ReaderOfficeController(
  private val context: Context,
  private val scope: CoroutineScope,
  private val host: Host,
) {
  interface Host {
    fun currentProject(): OpenedProject?
    fun noteWritable(): Boolean
    fun setStatus(status: String?)
    fun officeSession(): OfficeEditSession?
    fun setOfficeSession(session: OfficeEditSession?)
    fun setOfficeConverting(value: Boolean)
    fun setOfficeSaving(value: Boolean)
    fun menuBusyNow(): Boolean
  }

  fun sourceFormat(): OfficeDocumentFormat? =
    host.currentProject()?.let { OfficeDocumentFormat.fromExtension(it.sourceExtension) }

  fun sessionForCurrentProject(): OfficeEditSession? =
    host.officeSession()?.takeIf { it.projectId == host.currentProject()?.manifest?.id }

  /** office_edit：对当前 Office 源运行本地转换并建立编辑会话。 */
  fun beginOfficeEditSession() {
    val activeProject = host.currentProject() ?: return
    val format = OfficeDocumentFormat.fromExtension(activeProject.sourceExtension) ?: run {
      host.setStatus(context.getString(R.string.reader_menu_office_source_required))
      return
    }
    val engine = LocalOfficePdfEngineRegistry.engine ?: run {
      host.setStatus(context.getString(R.string.reader_menu_office_engine_unavailable))
      return
    }
    if (host.menuBusyNow()) return
    host.setOfficeConverting(true)
    scope.launch {
      try {
        val service = OfficePdfConversionService(
          File(context.cacheDir, "office-preview-${activeProject.manifest.id}"),
        )
        val revision = withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(activeProject.sourceUri)
            ?.use(OfficeSourceRevision::from)
            ?: throw IllegalStateException("无法读取 Office 源文件")
        }
        val outcome = withContext(Dispatchers.IO) {
          service.convertAndCache(
            currentRevision = revision,
            format = format,
            engine = engine,
            openSource = {
              context.contentResolver.openInputStream(activeProject.sourceUri)
                ?: throw IllegalStateException("无法读取 Office 源文件")
            },
          )
        }
        when (outcome) {
          is OfficePdfConversionService.Outcome.PreviewReady -> {
            val session = sessionForCurrentProject() ?: OfficeEditSession(
              projectId = activeProject.manifest.id,
              format = format,
              openingRevision = revision,
              workingCopyFile = File(service.workingDirectory, "working-copy.${format.extension}"),
              sourceName = activeProject.sourceName,
            )
            session.workingCopyFile.parentFile?.mkdirs()
            if (!session.workingCopyFile.isFile) {
              withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(activeProject.sourceUri)
                  ?.use { input -> session.workingCopyFile.outputStream().use(input::copyTo) }
                  ?: throw IllegalStateException("无法建立 Office 工作副本")
              }
            }
            session.dirty = false
            host.setOfficeSession(session)
            host.setStatus(context.getString(R.string.office_conversion_ready, outcome.file.name))
          }
          is OfficePdfConversionService.Outcome.Unavailable -> when (val result = outcome.result) {
            is OfficePdfConversionResult.UnsupportedFormat ->
              host.setStatus(
                context.getString(R.string.office_conversion_unsupported, result.format.displayName),
              )
            is OfficePdfConversionResult.EngineUnavailable ->
              host.setStatus(context.getString(R.string.reader_menu_office_engine_unavailable))
            is OfficePdfConversionResult.Failed ->
              host.setStatus(context.getString(R.string.office_conversion_failed, result.reason))
            // Ready 不会出现在 Unavailable 结果里；此处仅为穷尽性。
            else -> Unit
          }
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        host.setStatus(
          context.getString(
            R.string.office_conversion_failed,
            error.message ?: error.javaClass.simpleName,
          ),
        )
      } finally {
        host.setOfficeConverting(false)
      }
    }
  }

  /** save_return：revision 校验写回原 Office 文件，先落恢复副本，失败保留源。 */
  fun runOfficeSaveReturn() {
    val activeProject = host.currentProject() ?: return
    val format = OfficeDocumentFormat.fromExtension(activeProject.sourceExtension) ?: run {
      host.setStatus(context.getString(R.string.reader_menu_office_source_required))
      return
    }
    val session = sessionForCurrentProject() ?: run {
      host.setStatus(context.getString(R.string.reader_menu_save_return_no_session))
      return
    }
    if (!session.dirty) {
      host.setStatus(context.getString(R.string.reader_menu_save_return_no_changes))
      return
    }
    if (host.menuBusyNow()) return
    host.setOfficeSaving(true)
    scope.launch {
      try {
        val (currentSourceRevision, originalBytes) = withContext(Dispatchers.IO) {
          val bytes = context.contentResolver.openInputStream(activeProject.sourceUri)
            ?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取 Office 源文件")
          OfficeSourceRevision.from(bytes.inputStream()) to bytes
        }
        val workingBytes = withContext(Dispatchers.IO) { session.workingCopyFile.readBytes() }
        val destinationWritable = host.noteWritable()
        val plan = OfficeWriteBack.plan(
          openingRevision = session.openingRevision,
          currentRevision = currentSourceRevision,
          editorDirty = true,
          workingCopyBytes = workingBytes,
          destinationWritable = destinationWritable,
          recoveryCopyReady = destinationWritable,
          recoveryNameBase = session.sourceName,
        )
        when (plan) {
          is OfficeWriteBackPlan.Blocked -> host.setStatus(
            context.getString(
              when (plan.readiness) {
                OfficeSaveReadiness.SOURCE_CONFLICT -> R.string.reader_menu_save_return_source_conflict
                OfficeSaveReadiness.DESTINATION_UNAVAILABLE -> R.string.reader_menu_read_only
                else -> R.string.reader_menu_save_return_no_changes
              },
            ),
          )
          is OfficeWriteBackPlan.Write -> {
            writeBackWithRecovery(activeProject, plan, originalBytes)
            session.dirty = false
            host.setStatus(context.getString(R.string.reader_menu_save_return_success))
          }
          OfficeWriteBackPlan.NoChanges ->
            host.setStatus(context.getString(R.string.reader_menu_save_return_no_changes))
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        host.setStatus(
          context.getString(
            R.string.reader_menu_save_return_failed,
            error.message ?: error.javaClass.simpleName,
          ),
        )
      } finally {
        host.setOfficeSaving(false)
      }
    }
  }

  /** 先写恢复副本（原始源字节），revision 复核后写回；写回失败尝试还原。 */
  private suspend fun writeBackWithRecovery(
    activeProject: OpenedProject,
    plan: OfficeWriteBackPlan.Write,
    originalBytes: ByteArray,
  ) {
    withContext(Dispatchers.IO) {
      val srcDir = activeProject.sourceDirectoryUri
      // plan.recoveryCopyName 即完整恢复副本名（<源名>.recovery.<扩展名>）。
      val recoveryUri = DocumentsContract.createDocument(
        context.contentResolver,
        srcDir,
        "application/octet-stream",
        plan.recoveryCopyName,
      ) ?: throw IllegalStateException("无法创建恢复副本")
      val recoveryWritten = runCatching {
        context.contentResolver.openOutputStream(recoveryUri, "w")?.use { output ->
          output.write(originalBytes)
        } != null
      }.getOrDefault(false)
      if (!recoveryWritten) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, recoveryUri) }
        throw IllegalStateException("恢复副本写入失败，已中止保存")
      }
      val fresh = context.contentResolver.openInputStream(activeProject.sourceUri)
        ?.use(OfficeSourceRevision::from)
      if (fresh != null && fresh.sha256 != sha256Of(originalBytes)) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, recoveryUri) }
        throw IllegalStateException("源文件在保存前被修改")
      }
      val written = runCatching {
        context.contentResolver.openOutputStream(activeProject.sourceUri, "w")?.use { output ->
          output.write(plan.sourceBytes)
        } != null
      }.getOrDefault(false)
      if (!written) {
        runCatching {
          context.contentResolver.openOutputStream(activeProject.sourceUri, "w")
            ?.use { it.write(originalBytes) }
        }
        throw IllegalStateException("Office 源写回失败，已尝试还原原始内容")
      }
    }
  }

  private fun sha256Of(bytes: ByteArray): String =
    java.security.MessageDigest.getInstance("SHA-256")
      .digest(bytes)
      .joinToString("") { "%02x".format(it) }
}

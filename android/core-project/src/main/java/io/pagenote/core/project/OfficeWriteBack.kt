package io.pagenote.core.project

/**
 * Save & Return 写回计划（task 36 编辑/Save & Return 切片的纯核部分）。
 *
 * 复用 [OfficeSavePolicy] 的就绪判定，把判定结果展开成可执行动作：
 * 写回工作副本字节 + 先落一份恢复副本，或明确阻断并保留最后有效源。
 */
sealed interface OfficeWriteBackPlan {
  /** 编辑器没有未保存修改：不触碰源与目标。 */
  data object NoChanges : OfficeWriteBackPlan

  /** 阻断：readiness 给出本地化前的原因（SOURCE_CONFLICT / DESTINATION_UNAVAILABLE / RECOVERY_REQUIRED）。 */
  data class Blocked(val readiness: OfficeSaveReadiness) : OfficeWriteBackPlan

  /** 就绪：先写 recoveryCopyName 恢复副本，再以 revision 校验写回 sourceBytes。 */
  data class Write(val sourceBytes: ByteArray, val recoveryCopyName: String) : OfficeWriteBackPlan {
    override fun equals(other: Any?): Boolean = other is Write &&
      other.sourceBytes.contentEquals(sourceBytes) &&
      other.recoveryCopyName == recoveryCopyName

    override fun hashCode(): Int = sourceBytes.contentHashCode() * 31 + recoveryCopyName.hashCode()
  }
}

object OfficeWriteBack {
  const val RECOVERY_MARKER = ".recovery"

  /**
   * @param workingCopyBytes 工作副本的完整字节（保存目标格式与源一致）；未保存修改时可为 null。
   * @param recoveryNameBase 源文件显示名（含扩展名），用于生成恢复副本名。
   */
  fun plan(
    openingRevision: OfficeSourceRevision,
    currentRevision: OfficeSourceRevision,
    editorDirty: Boolean,
    workingCopyBytes: ByteArray?,
    destinationWritable: Boolean,
    recoveryCopyReady: Boolean,
    recoveryNameBase: String,
  ): OfficeWriteBackPlan {
    val readiness = OfficeSavePolicy.evaluate(
      openingRevision = openingRevision,
      currentRevision = currentRevision,
      editorDirty = editorDirty,
      destinationWritable = destinationWritable,
      recoveryCopyReady = recoveryCopyReady,
    )
    return when (readiness) {
      OfficeSaveReadiness.NO_CHANGES -> OfficeWriteBackPlan.NoChanges
      OfficeSaveReadiness.READY -> {
        val bytes = requireNotNull(workingCopyBytes) { "READY plan requires working copy bytes" }
        OfficeWriteBackPlan.Write(
          sourceBytes = bytes,
          recoveryCopyName = recoveryCopyName(recoveryNameBase),
        )
      }
      else -> OfficeWriteBackPlan.Blocked(readiness)
    }
  }

  /** 恢复副本名：`lecture.docx` → `lecture.recovery.docx`；无扩展名时追加 `.recovery`。 */
  fun recoveryCopyName(sourceName: String): String {
    val base = sourceName.trim()
    require(base.isNotEmpty()) { "recovery copy needs a source name" }
    val dot = base.lastIndexOf('.')
    return if (dot > 0) {
      base.substring(0, dot) + RECOVERY_MARKER + base.substring(dot)
    } else {
      base + RECOVERY_MARKER
    }
  }
}

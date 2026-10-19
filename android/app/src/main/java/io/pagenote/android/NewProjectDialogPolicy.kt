package io.pagenote.android

internal object NewProjectDialogPolicy {
  fun canDismiss(isCreating: Boolean, isChoosingDirectory: Boolean, isChoosingPdf: Boolean): Boolean =
    !isCreating && !isChoosingDirectory && !isChoosingPdf
}

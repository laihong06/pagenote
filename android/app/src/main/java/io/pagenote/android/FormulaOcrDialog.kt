package io.pagenote.android

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.pagenote.feature.ocr.LatexOcrResult

/**
 * 公式识别预览修正对话框（移植批次 6）：选区墨迹 → LaTeX 候选 → 预览修正
 * → 插入笔记块公式 / 插入画布公式素材（可选存入素材库）。
 *
 * - 候选（束搜 top-N）以 chips 呈现，点选载入编辑框；也可以直接手改；
 * - 预览用离线 JLatexMath（FormulaMaterialRenderer，与公式素材/笔记渲染
 *   同源），渲染失败显示明确占位不阻塞修正；
 * - 模型不可用时（候选空 + 告警）仍可手动输入 LaTeX 走完整插入链路。
 */
@Composable
internal fun FormulaOcrDialog(
  result: LatexOcrResult,
  onInsertNote: (latex: String) -> Unit,
  onInsertCanvas: (latex: String, removeInk: Boolean, saveToLibrary: Boolean) -> Unit,
  canInsertCanvas: Boolean,
  onDismiss: () -> Unit,
) {
  var latex by remember(result) {
    mutableStateOf(result.bestLatex().orEmpty())
  }
  var removeInk by remember { mutableStateOf(true) }
  var saveToLibrary by remember { mutableStateOf(false) }
  var preview by remember { mutableStateOf<Bitmap?>(null) }
  var previewDirty by remember { mutableStateOf(false) }
  val density = LocalDensity.current
  val renderer = remember { FormulaMaterialRenderer() }

  LaunchedEffect(latex) {
    previewDirty = latex.isNotBlank()
    // 不主动 recycle 旧位图：Image 组合可能仍在同一帧绘制它；预览位图很小，
    // 交给 GC 回收。
    preview = null
    if (latex.isBlank()) return@LaunchedEffect
    val textSize = with(density) { 20.dp.toPx() }
    preview = renderer.render(listOf(latex.trim()), textSize, android.graphics.Color.DKGRAY)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.formula_ocr_dialog_title)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = stringResource(R.string.ocr_status, result.status, result.engine),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        result.warnings.forEach { warning ->
          Text(
            modifier = Modifier.testTag("formula_ocr_warning"),
            text = warning,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
          )
        }
        if (result.candidates.isNotEmpty()) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            result.candidates.take(3).forEachIndexed { index, candidate ->
              FilterChip(
                selected = latex == candidate.latex,
                onClick = { latex = candidate.latex },
                label = {
                  Text(
                    "${(candidate.confidence * 100).toInt()}%",
                    fontFamily = FontFamily.Monospace,
                  )
                },
                modifier = Modifier.testTag("formula_ocr_candidate_$index"),
              )
            }
          }
        }
        OutlinedTextField(
          value = latex,
          onValueChange = { latex = it },
          modifier = Modifier.fillMaxWidth().testTag("formula_ocr_latex"),
          label = { Text(stringResource(R.string.formula_ocr_latex_label)) },
          singleLine = false,
          minLines = 2,
          maxLines = 4,
        )
        val bitmap = preview
        if (bitmap != null && !bitmap.isRecycled) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.formula_ocr_preview),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("formula_ocr_preview"),
            contentScale = ContentScale.FillWidth,
          )
        } else if (previewDirty) {
          Text(
            modifier = Modifier.testTag("formula_ocr_preview_failed"),
            text = stringResource(R.string.formula_ocr_preview_failed),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
          )
        }
        if (canInsertCanvas) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Switch(
              checked = removeInk,
              onCheckedChange = { removeInk = it },
              modifier = Modifier.testTag("formula_ocr_remove_ink"),
            )
            Text(stringResource(R.string.ink_ocr_remove_ink), style = MaterialTheme.typography.bodyMedium)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Switch(
              checked = saveToLibrary,
              onCheckedChange = { saveToLibrary = it },
              modifier = Modifier.testTag("formula_ocr_save_material"),
            )
            Text(stringResource(R.string.formula_ocr_save_material), style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
          modifier = Modifier.testTag("formula_ocr_insert_note"),
          enabled = latex.isNotBlank(),
          onClick = { onInsertNote(latex.trim()) },
        ) { Text(stringResource(R.string.formula_ocr_insert_note)) }
        Button(
          modifier = Modifier.testTag("formula_ocr_insert_canvas"),
          enabled = latex.isNotBlank() && canInsertCanvas,
          onClick = { onInsertCanvas(latex.trim(), removeInk, saveToLibrary) },
        ) { Text(stringResource(R.string.formula_ocr_insert_canvas)) }
      }
    },
    dismissButton = {
      TextButton(modifier = Modifier.testTag("formula_ocr_cancel"), onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

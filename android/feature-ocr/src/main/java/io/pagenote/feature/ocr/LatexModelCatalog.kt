package io.pagenote.feature.ocr

/**
 * 公式识别（墨迹 → LaTeX）开源模型选型目录（移植批次 6）。
 *
 * 选型结论（对应闭源 native 公式引擎的开源替代）：
 *
 * | 候选 | 许可 | 形态 | 结论 |
 * | --- | --- | --- | --- |
 * | Mathpix | 闭源商业 API | 云服务 | 排除：隐私边界（离线 OCR 契约不允许网络调用） |
 * | pix2tex（Image-to-Markup, Lukas Blecher） | MIT | ViT 编码器 + 自回归 transformer 解码器 | **选定**：纯视觉→LaTeX、可导出 ONNX、词典小、可束搜出多候选 |
 * | texify | Apache-2.0 | GGUF/llama 系本地栈 | 备选：运行时栈与现有 ONNX Runtime 不一致，留作后续 |
 *
 * pix2tex 的部署形态：官方发布 PyTorch 权重；部署前导出为 encoder/decoder
 * 两个 ONNX 图（encoder: 图像 patch 序列 → memory；decoder: memory + token
 * 前缀 → 下一步 logits），词典导出为 tokens.json（字符串数组，下标即 id，
 * 前三个保留 `<pad>`/`<bos>`/`<eos>`，与 pix2tex 词典前缀一致）。
 *
 * **本批次仓库未捆绑模型二进制**（导出产物体积大且无法在本环境验证其数
 * 值正确性）；[LatexOcrEngine.create] 在资产缺失时抛出带路径的
 * [OfflineOcrException]，调用方据此把"模型未捆绑"作为可恢复状态呈现，
 * 手动输入/修正链路（预览修正对话框）在无模型时仍然可用。
 */
data class LatexModelEntry(
  /** 稳定模型 id（设置持久化用）。 */
  val id: String,
  /** 编码器 ONNX 资产路径（相对 assets）。 */
  val encoderPath: String,
  /** 解码器 ONNX 资产路径（相对 assets）。 */
  val decoderPath: String,
  /** 词典 JSON 资产路径（相对 assets；字符串数组，下标即 token id）。 */
  val tokensPath: String,
  /** 模型许可证（展示与分发核对用）。 */
  val license: String,
  /** 上游来源说明。 */
  val source: String,
)

object LatexModelCatalog {
  const val DEFAULT_ID = "pix2tex-vit"

  /** 选定条目：pix2tex ViT + transformer decoder 的 ONNX 导出。 */
  val PIX2TEX_VIT = LatexModelEntry(
    id = DEFAULT_ID,
    encoderPath = "models/formula/pix2tex-vit/encoder.onnx",
    decoderPath = "models/formula/pix2tex-vit/decoder.onnx",
    tokensPath = "models/formula/pix2tex-vit/tokens.json",
    license = "MIT",
    source = "pix2tex (Image-to-Markup Transformer, Lukas Blecher) checkpoint exported to ONNX",
  )

  val DEFAULT: LatexModelEntry = PIX2TEX_VIT

  val ENTRIES: List<LatexModelEntry> = listOf(PIX2TEX_VIT)

  fun byId(id: String): LatexModelEntry? = ENTRIES.firstOrNull { it.id == id }

  fun isValidId(id: String): Boolean = ENTRIES.any { it.id == id }
}

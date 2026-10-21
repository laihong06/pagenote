package io.pagenote.android

internal data class MarkdownPreviewCaptureFixture(
  val name: String,
  val markdown: String,
  val expectedText: String,
)

internal object MarkdownPreviewCaptureFixtures {
  private val dollar = '$'

  val richLight = MarkdownPreviewCaptureFixture(
    name = "rich-light",
    markdown = """
      # 模式识别：模型评估

      准确率不能单独说明模型表现。行内公式 ${dollar}x^2 + y^2${dollar} 与后面的表格、图表和代码应在窄笔记栏中自然换行。

      | 指标 | 计算方式 | 解释 |
      | --- | --- | --- |
      | 灵敏度 | TP / (TP + FN) | 找出真实阳性 |
      | 特异度 | TN / (TN + FP) | 排除真实阴性 |

      ${dollar}${dollar}
      \mathrm{Accuracy}=\frac{\mathrm{TP}+\mathrm{TN}}{\mathrm{TP}+\mathrm{TN}+\mathrm{FP}+\mathrm{FN}}
      ${dollar}${dollar}

      ```kotlin
      fun predict(features: FloatArray): Int = model(features)
      ```

      ```mermaid
      flowchart LR
        A[输入特征] --> B[分类模型]
        B --> C[预测类别]
        B --> D[置信度]
      ```

      ![missing project image](/attachments/deadbeef.png)
      ![blocked remote image](https://example.invalid/model.png?token=must-not-render)
    """.trimIndent(),
    expectedText = "准确率不能单独说明模型表现",
  )
}

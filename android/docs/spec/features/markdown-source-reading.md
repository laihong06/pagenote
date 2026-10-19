# Markdown 源文档阅读

## Scope

Task 4.3 切片：左源为 md/markdown 的项目，左窗格显示源 Markdown 的分页阅读（复用 `PageNotes.split` 分页 + Markwon 预览），右窗格笔记不变。PDF 路径不动；本切片暂不处理 Office，完整 Office 目标见 `desktop-ui-and-office-parity.md`。

## Behavior

- 打开时不再 `require(pdf)`：md 源跳过 `PdfDocumentSession`，读左源文本做分页；`renderer == null + markdownSource != null` 即 Markdown 阅读态。
- 左窗格：Markdown 源分页显示（Markwon 预览 + 富块预处理复用），页码与右窗格笔记页对齐；无 PDF 笔迹/框选/OCR/缩放入口（工具栏对应按钮禁用）。
- 页数 = max(源页数, 笔记页数)，空页显示空态；超长页沿用 `NotePage.preview` 截断。
- 笔记、历史、批注、review、handoff、Agent 讲解（OCR 回退记 unavailable）全部沿用页号，不动。

## Acceptance (EARS)

- When 左源为 Markdown 时，阅读器 shall 分页显示源文并与笔记页对齐。
- When 源页超笔记页时，shall 空页占位可编辑保存。
- When Markdown 阅读态时，PDF 专属入口 shall 禁用而不报错。

## Verification

- JVM：源分页/空页/超长用例；仪器编译：Markdown 左窗格。
- `cd android && ./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --offline` 通过；真机待 task 12。

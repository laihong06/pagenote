# Android PDF Text 工具

## 范围

接通 Handwriting > Text 命令，沿用桌面点击 PDF 放置文字批注的交互。此命令与现有通用 `Annotate` 模式分开：Text 模式只创建文字，通用模式仍允许在文字和区域之间选择。

复用现有 `AnnotationTextInputDialog`、`AnnotationTextDraft`、`ProjectAnnotationStore` 和 PDF overlay；不新增项目字段或附件，不改变 pen/highlighter 的输入和保存格式。

## 行为

- 仅当当前项目包含可写 PDF、页面视图可交互且没有冲突操作时启用 Text 命令；项目、PDF、只读或 busy 条件不满足时禁用并提供现有本地化原因。
- 选择 Text 后进入 tap-to-place 模式并暂停自由笔迹/视口手势；点击 PDF 页面映射为桌面宽度坐标，打开纯文字输入框。
- 纯文字模式不提供区域类型切换。取消后不写数据并留在 Text 放置模式；提交后退出模式。
- 空白文本不能提交；有效文本使用当前笔颜色和宽度，按既有 schema 写为单点 `type=text` 条目，文本最多 10000 个 UTF-16 code units。
- 保存沿用页面 annotation 数组的读-合并-写、SAF 回滚和恢复副本提示；其他批注、其他页和未知字段保持不变，成功后刷新当前页 overlay。
- 通用 Annotate 的文字/区域模式保持原行为。

## 验收（EARS）

- When 用户在可写 PDF 项目选择 Handwriting > Text 时，Android shall 进入仅文字的点选放置模式并阻止笔迹输入。
- When 用户在页面上点击时，Android shall 将该点转换为项目规定的页面宽度坐标并打开纯文字输入框。
- When 用户取消或文本为空时，Android shall 不写 annotations 数据、不创建 overlay 项，并允许继续放置或取消 Text 模式。
- When 用户提交非空文字时，Android shall 以当前颜色/宽度创建兼容的 `type=text` 条目，只追加到当前 PDF 页并在写入成功后刷新 overlay。
- When 项目不可写、不是 PDF 或 Reader 忙碌时，Android shall 禁用 Text 命令且不更改页面或 annotations。
- When 用户使用通用 Annotate 命令时，Android shall 保留现有文字/区域类型选择行为。

## 验证范围

- JVM：复用 `AnnotationTextDraftTest` 与现有 annotation store 验证，不扩展设备矩阵。
- 唯一 Android 14/API 34 平板 AVD：一项 MainActivity 闭环，使用测试生成的本地 PDF，验证命令门控、放置点映射、纯文字模式、取消/空白、提交、SAF 持久化及 overlay。
- 构建：完整 `:app:testDebugUnitTest`、该聚焦仪器用例和 `:app:assembleDebug`；不打开外部 app。
- 小米平板 5 Pro 真机验收仍归父 SPEC task 12。

# Android Markdown 下划线格式

## 范围

接通可写页笔记 Format > Underline，复用桌面源码模式生成的 HTML `<u>文字</u>`。继续以 Markdown 源文为项目权威数据，预览由现有 Markwon renderer 展示原生 `UnderlineSpan`。

本切片只允许 Markwon HTML renderer 的下划线 handler（`u`/`ins`），不启用其他默认 HTML tag handlers，不引入 WebView、网络或项目格式字段。

## 行为

- 仅当当前页可写 Markdown 编辑器存在且应用不忙时启用；无项目、只读、预览或编辑锁定时禁用。
- 有选区时将所选文本包裹为 `<u>文本</u>`，保留其他源文并继续选中原文。
- 光标折叠时插入 `<u>本地化占位词</u>` 并选中占位词。
- 使用 Android `EditText` UTF-16 选区偏移；通过现有原生 watcher 更新草稿，不自动保存。
- 保存复用当前页 revision 检查，不修改其他页、附件或项目结构。
- 编辑器与预览均将 `<u>` 显示为原生下划线 span；Markwon editor 需显式注册仅负责 UnderlineSpan 的 `EditHandler`，因为其默认行为只标记 Markdown 标点。
- 其他 HTML tag 不启用其默认渲染行为；原始标签保留在可编辑源文中。

## 验收（EARS）

- When 用户在可写页笔记中选中文本并选择 Underline 时，Android shall 仅以 `<u>` 和 `</u>` 包裹选区并保留其他源文。
- When 用户没有选区时选择 Underline，Android shall 在光标处插入成对 HTML tag 与现有本地化占位文本，并选中占位文本。
- When 编辑器或预览渲染下划线 Markdown 时，Android shall 对所选内容应用原生 `UnderlineSpan`，且保留可编辑源标记与已存在的 Markdown 格式。
- When 项目不可写、编辑器处于预览/忙碌状态或没有活动项目时，Android shall 禁用 Underline 命令且不得更改草稿。
- When 用户保存下划线修改时，Android shall 按当前页 revision 保护流程保存，且相邻页内容不变。

## 验证范围

- JVM：桌面 `<u>...</u>` 分隔符、选区保留、折叠光标本地化占位及 UTF-16 边界。
- Android 14/API 34 平板 AVD：一项 MainActivity 流程验证选区格式化、嵌套粗体保留、空选区占位、保存/相邻页隔离、编辑器与预览的 `UnderlineSpan`，并确认其他 HTML handlers 未启用；不启动外部 UI。
- 构建：受影响 app JVM 测试、完整 `:app:testDebugUnitTest`、单项 API 34 平板 AVD 测试、`:app:assembleDebug`。
- 不运行其他 API、OEM、设备、页面大小或性能矩阵；小米平板 5 Pro 实机验收仍归父 SPEC task 12。

## 技术依据

- Markwon 4.6.2 `HtmlPlugin` 支持关闭默认 handlers 并单独注册 handler：[`HtmlPlugin.java`](https://github.com/noties/Markwon/blob/v4.6.2/markwon-html/src/main/java/io/noties/markwon/html/HtmlPlugin.java)。
- Markwon 4.6.2 `UnderlineHandler` 以 Android 原生 `UnderlineSpan` 绘制 `<u>` 与 `<ins>`：[`UnderlineHandler.java`](https://github.com/noties/Markwon/blob/v4.6.2/markwon-html/src/main/java/io/noties/markwon/html/tag/UnderlineHandler.java)。
- Markwon editor 默认只标记 Markdown 标点；将 renderer spans 映射到输入态需单独注册 `EditHandler`：[`MarkwonEditor.java`](https://github.com/noties/Markwon/blob/v4.6.2/markwon-editor/src/main/java/io/noties/markwon/editor/MarkwonEditor.java)、[`EditHandler.java`](https://github.com/noties/Markwon/blob/v4.6.2/markwon-editor/src/main/java/io/noties/markwon/editor/EditHandler.java)。

# Android Markdown 任务列表插入

## 范围

接通 Android 段落菜单的 Task List 命令，沿用桌面 `setupMarkdownTools` 源码模式回退路径，逐行添加 GFM 未完成任务前缀 `- [ ] `。现有 Markwon TaskList renderer 负责预览。

## 行为

- 命令仅在当前项目存在可写 Markdown 编辑器且应用不忙时启用；无项目、只读、预览或忙碌时禁用且不改笔记。
- 按现有 `MarkdownParagraphFormatting.prefixLines` 规则，把选区扩展到行首/行尾或文档末尾，对范围内每行添加 `- [ ] `；空行也会添加前缀，范围外文本不变。
- 反向选区规范化，越界范围夹取；偏移使用 EditText/Kotlin 字符串 UTF-16 单位。
- 完成后光标折叠至变换后范围末尾；原生 editor watcher 更新当前页草稿，不自动保存，保存继续使用 revision 检查。
- 预览显示未完成复选框；点按复选框不改变源码或项目状态，状态变更继续通过编辑 Markdown 标记完成。

## 非目标

- 不实现富文本待办对象、复选框点击写回、完成状态切换或自动重排。
- 不启用无 Markdown 表示的字号命令。

## 验收（EARS）

- When 用户在可写页笔记中执行 Task List 时，Android shall 对选区覆盖的每行添加桌面源码回退一致的 `- [ ] ` 前缀，包括空行。
- When 选区跨多行、反向、越界或结束在下一行起点时，Android shall 遵守既有段落范围规则，保留范围外文本和 UTF-16 光标语义。
- When 命令发生在无项目、只读、预览或忙碌状态时，Android shall 禁用命令且保持草稿不变。
- When 用户预览任务列表时，Android shall 使用现有 GFM `TaskListSpan` 显示复选框；点击复选框不写回。
- When 用户保存当前页时，Android shall 只保存该页的 Markdown，并保留相邻页。

## 验证范围

- JVM：验证任务列表固定前缀、多行/空行、反向与越界范围由现有段落 helper 正确处理。
- Android 14/API 34 平板 AVD：扩展现有聚焦段落命令仪器用例，验证任务列表菜单门控、草稿、保存、相邻页及 GFM `TaskListSpan`。
- 构建：受影响 app JVM 测试、单项 Android 14/API 34 平板 AVD 仪器测试、`:app:assembleDebug`。
- 不运行其它 API、OEM、设备、页面大小或性能矩阵；小米平板 5 Pro 真机综合验收仍归父 SPEC task 12。

# Android Markdown 表格插入

## 范围

为 Android 可写页笔记接通 Insert 菜单的 Table 命令。Android 使用 Markdown 源码编辑器，严格沿用桌面源码模式固定的双列表格模板；预览复用现有 Markwon GFM 表格渲染。

## 行为

- 命令仅在当前项目有可写 Markdown 编辑器且应用不忙时启用；无项目、只读、预览、不可编辑或忙碌时不修改笔记。
- 插入内容固定为 `\n\n| 列一 | 列二 |\n| --- | --- |\n| 内容 | 内容 |\n\n`。
- 与桌面源码模式一致，命令丢弃当前选区内容并用表格模板替换；折叠选区处直接插入模板。光标置于模板末尾，不选中单元格文本。
- 选区反向时规范化，越界时夹取到 Markdown UTF-16 边界。替换只影响选区，模板前后其它笔记保持不变。
- 通过原生 `Editable.replace` 更新编辑器；现有 watcher 更新当前页草稿。命令不自动保存、不改其它页，保存仍走现有 revision 检查。
- 预览使用当前离线 GFM renderer，显示为表格而非原始管道分隔源码。

## 非目标

- 不提供行列数选择、对齐选项、单元格导航或表格专用编辑控件。
- 不启用 Insert 菜单中的链接或粘贴 Markdown 命令。

## 验收（EARS）

- When 用户在可写笔记中执行 Table，Android shall 用桌面源码模式双列表格模板替换当前选区并把光标置于模板末尾。
- When 用户在折叠选区处执行 Table，Android shall 原位插入固定模板，不选择单元格文本。
- When 选区反向或越界时，Android shall 规范化/夹取 UTF-16 范围，并保留选区之外的笔记文本。
- When 命令发生在预览、只读、无项目或忙碌状态时，Android shall 保持 Markdown 不变并禁用菜单命令。
- When 用户保存并预览当前笔记时，Android shall 仅保存当前页，并使用 GFM 表格 span 呈现表头和内容。

## 验证范围

- JVM：覆盖模板精确内容、选区替换/折叠光标、反向/越界范围以及周围文本保持。
- Android 14/API 34 平板 AVD：一项聚焦 Reader 仪器流程，验证菜单门控、选区替换、光标、草稿、页级保存和 GFM TableSpan 预览；不启动外部 UI。
- 构建：新增 helper 聚焦 JVM 测试、该单项 API 34 AVD 仪器测试、`:app:assembleDebug`。
- 不运行其它 API、OEM、设备、页面大小或性能矩阵；AVD 仅为平台代理，小米平板 5 Pro 真机验收仍归父 SPEC task 12。

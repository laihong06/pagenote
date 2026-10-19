# Android Markdown 编辑撤销与重做

## 范围

接通 Android Edit 菜单中的 Undo note edit 与 Redo note edit。操作当前可写页 Markdown 源文本，复用原生 EditText watcher 更新草稿；不改变项目格式或现有笔迹撤销/重做栈。

## 行为

- 命令仅在当前页可写 Markdown 编辑器存在、应用不忙且对应历史栈非空时启用。
- 键入、粘贴、剪切和格式/插入命令等所有经 `Editable` 产生的文本变化均进入同一页级历史；相邻文本变化间隔 `<= 500ms` 时合并为同一 undo 步，间隔 `> 500ms` 时开始新步骤，最多保留 80 个 undo 快照。
- Undo 恢复前一份完整 Markdown 快照并将当前内容压入 redo；Redo 对称恢复。新编辑清空 redo。
- 历史仅保存在当前 Reader 页面会话的内存中；项目/页切换、外部内容重新载入或编辑器重建时清空；显式保存不清空历史。
- 恢复通过原生 `Editable` 更新，watcher 仍更新当前页草稿；不自动保存。恢复后光标折叠到文末。
- 只读、预览、无项目、忙碌、无可撤销/重做内容时命令禁用，且不得改变草稿。

## 非目标

- 不持久化撤销栈到项目文件或跨进程；笔迹 Undo/Redo 保持独立。
- 本切片只接通菜单命令，不新增硬件键盘快捷键、撤销历史 UI 或跨页批量历史。

## 验收（EARS）

- When 用户在可写页笔记中输入或使用格式/插入命令修改文本时，Android shall 为该页记录可撤销快照，并按 500ms 合并窗口与 80 步上限裁剪。
- When 用户选择 Undo 时，Android shall 恢复上一份当前页 Markdown，将被撤销内容放入 redo，并通过原生 watcher 更新草稿。
- When 用户选择 Redo 时，Android shall 恢复最近一次被撤销内容；之后的新编辑 shall 清空 redo。
- When 项目/页切换、编辑器重载或重建时，Android shall 清空对应历史；保存当前页不清空历史。
- When 用户保存修改后的笔记时，Android shall 保持现有 revision 检查并只写当前页。
- When 用户处于只读、预览、无项目或忙碌状态，或对应历史为空时，Android shall 禁用命令且不改草稿。

## 验证范围

- JVM：确定性测试覆盖初始状态、undo/redo 往返、500ms 分组边界、redo 清空、80 步上限、外部重置和文本失配防护。
- Android 14/API 34 平板 AVD：一个聚焦 Reader 流程验证初始禁用、编辑、undo/redo、页级保存/相邻页隔离、预览/只读门控；不启动外部 UI。
- 构建：聚焦历史 helper 与完整 `:app:testDebugUnitTest`、单项 API 34 平板 AVD 仪器测试、`:app:assembleDebug`。
- 不运行其它 API、OEM、设备、页面大小或性能矩阵；小米平板 5 Pro 真机验收仍归父 SPEC task 12。

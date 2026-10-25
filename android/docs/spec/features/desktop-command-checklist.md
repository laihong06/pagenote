# 桌面命令清单（Android 14）

> 范围：对照桌面 `index.html` 命令区，逐项列出 Android `ReaderMenuCommand.id` 与行为；终态移除 `pendingCommand()` 占位项。只覆盖 Android 14/API 34 功能一致性，不扩展 API 矩阵。

## 命令对照

| 桌面入口 | Android 命令 id | Android 行为 |
| --- | --- | --- |
| 打开项目 | `open_project` | 已接线：SAF 打开项目 |
| 打开文档 | `open_document` | 已接线：导入 PDF/Markdown 并新建项目 |
| 新建项目 | `new_project` | 已接线：选择项目目录，可选导入 PDF/Markdown 原文 |
| 最近文档 | `recent_documents` | 已接线：最近项目列表 |
| 插入附件 | `attachments` | 已接线：项目附件管理 |
| 导入笔记 | `import_notes` | 已接线：当前页/整份导入；JSON 备份走合并对话框（页序对齐/追加、冲突覆盖/保留/合并、资产引用去重摘要） |
| 导出笔记 | `export_notes` | 已接线：当前页/整份导出 |
| （Android 附加）合并导出 PDF | `export_merged_pdf` | 已接线（移植差距清单 #15 的 Android 侧导出增强）：按页码范围把源 PDF 页拼接导出，可把每页笔记转为纯文本 PDF 页附加在对应源页后（convert note）；仅 PDF 项目可用。桌面无此命令，属 Android 端新增 |
| 历史版本 | `note_history` | 已接线：页级历史恢复 |
| 项目笔记设置 | `note_settings` | 已接线：项目级纸张背景与笔记口令设置 |
| AI 批注（讲解/任务/批注/审阅/交接） | `pdf_text_selection`、`page_tutor`、`agent_task`、`annotations`、`reviews`、`handoff` | 已接线：端侧 Agent 与 OpenAI 兼容接入 |
| 编辑：撤销/重做/剪切/复制/全选 | `note_undo`、`note_redo`、`cut`、`copy`、`select_all` | 已接线：页级 Markdown 历史与剪贴板 |
| 段落：字号/标题/列表/引用 | `font_size`、`heading`、`unordered_list`、`ordered_list`、`task_list`、`quote` | 已接线：Markdown 段落格式化 |
| 格式：粗体/斜体/删除线/下划线 | `bold`、`italic`、`strikethrough`、`underline` | 已接线：Markdown 内联格式化 |
| 插入：代码/公式/表格/链接/图片/粘贴 | `code`、`formula`、`table`、`link`、`image`、`paste_image`、`paste_markdown` | 已接线：Markdown 插入命令 |
| 阅读：上一页/下一页/OCR/缩放/适合窗口 | `previous_page`、`next_page`、`ocr`、`region_ocr`、`zoom_out`、`zoom_in`、`fit_width`、`fit_page` | 已接线：页跳转、OCR 与视口命令 |
| 手写批注：选择/笔/荧光笔/橡皮/形状/颜色/宽度/撤销/复制/粘贴/图片/删除 | `select_tool`、`pen`、`highlighter`、`eraser`、`line`、`arrow`、`shape`、`ink_text`、`ink_rich_text`、`ink_ocr`、`formula_ocr`、`ink_color`、`ink_width`、`annotate_mode`、`pen_undo`、`pen_redo`、`copy_ink`、`paste_ink`、`insert_ink_image`、`clear_page`、`delete_ink` | 已接线：笔迹与原生批注命令（`ink_rich_text` 为移植批次4新增的画布富文本编辑窗入口；`ink_ocr`/`formula_ocr` 为批次6新增的手写转文字与公式识别入口） |
| Word 编辑：本地打开/编辑 | `office_edit` | 已接线（task 36 本地引擎切片）：`OfficePdfEngine` 边界 + `OfficePdfConversionService` 本地转换/预览缓存；仅当当前项目源为 Office 格式且引擎已注册时可进入。LibreOfficeKit 接入前命令禁用并显示本地原因（源非 Office / 引擎未接入），不再指向桌面会话；`.doc/.docx/.ppt/.pptx` 项目源 opener 拒绝行为保持不变 |
| Word 编辑：选中文字 → AI 批注 | `selection_to_annotation` | 已接线：笔记选区审阅入口 |
| Word 编辑：保存并返回 | `save_return` | 已接线（task 36 本地引擎切片）：`OfficeWriteBack` 修订校验写回 + 恢复副本事务；仅在当前项目存在脏的本地 Office 编辑会话且策略 READY 时可用，否则禁用并显示本地原因 |
| 设置/模型设置 | `settings_action`、`model_settings` | 已接线：阅读设置与模型设置 |
| 使用说明：关于/快捷键/隐私/诊断 | `about`、`shortcuts`、`privacy_license`、`diagnostics` | 已接线：本地帮助命令 |

## 验收（EARS）

- When 桌面命令清单中任一已接线命令被触发，Android shall 执行同名原生行为，不得保留占位禁用项。
- When 命令属于 `office_edit` 或 `save_return`，Android shall 走 task 36 本地引擎切片的真实路径（本地转换/写回事务），并在引擎未接入或无编辑会话时显示准确的本地原因；不再声称需要桌面会话。
- When 清单更新时，Android shall 同步更新本文件与 `tasks.md`，不得只改代码不改文档。

## 验证

- JVM/静态检查：命令 id 与 `MainActivity.menuSections` 一致；`pendingCommand()` 占位已全部清除（零待办命令）。
- `:app:assembleDebug` 通过；`git diff --check` 通过。
- API 34 平板 AVD 只做受影响流程回归，不扩展矩阵。

## 关联

- 父任务：`../tasks.md` task 35。
- 导航契约：`adaptive-menu-navigation.md`。
- 总体对齐：`desktop-ui-and-office-parity.md`。

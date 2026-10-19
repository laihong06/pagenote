# 桌面命令清单（Android 14）

> 范围：对照桌面 `index.html` 命令区，逐项列出 Android `ReaderMenuCommand.id` 与行为；终态移除 `pendingCommand()` 占位项。只覆盖 Android 14/API 34 功能一致性，不扩展 API 矩阵。

## 命令对照

| 桌面入口 | Android 命令 id | Android 行为 |
| --- | --- | --- |
| 打开文档 | `open_project` | 已接线：SAF 打开项目 |
| 新建项目 | `new_project` | 已接线：选择项目目录，可选导入 PDF 原文 |
| 最近文档 | `recent_documents` | 已接线：最近项目列表 |
| 插入附件 | `attachments` | 已接线：项目附件管理 |
| 导入笔记 | `import_notes` | 已接线：当前页/整份导入 |
| 导出笔记 | `export_notes` | 已接线：当前页/整份导出 |
| 历史版本 | `note_history` | 已接线：页级历史恢复 |
| AI 批注（讲解/任务/批注/审阅/交接） | `pdf_text_selection`、`page_tutor`、`agent_task`、`annotations`、`reviews`、`handoff` | 已接线：端侧 Agent 与 OpenAI 兼容接入 |
| 编辑：撤销/重做/剪切/复制/全选 | `note_undo`、`note_redo`、`cut`、`copy`、`select_all` | 已接线：页级 Markdown 历史与剪贴板 |
| 段落：字号/标题/列表/引用 | `font_size`、`heading`、`unordered_list`、`ordered_list`、`task_list`、`quote` | 已接线：Markdown 段落格式化 |
| 格式：粗体/斜体/删除线/下划线 | `bold`、`italic`、`strikethrough`、`underline` | 已接线：Markdown 内联格式化 |
| 插入：代码/公式/表格/链接/图片/粘贴 | `code`、`formula`、`table`、`link`、`image`、`paste_image`、`paste_markdown` | 已接线：Markdown 插入命令 |
| 阅读：上一页/下一页/OCR/缩放/适合窗口 | `previous_page`、`next_page`、`ocr`、`region_ocr`、`zoom_out`、`zoom_in`、`fit_width`、`fit_page` | 已接线：页跳转、OCR 与视口命令 |
| 手写批注：选择/笔/荧光笔/橡皮/形状/颜色/宽度/撤销/复制/粘贴/图片/删除 | `select_tool`、`pen`、`highlighter`、`eraser`、`line`、`arrow`、`shape`、`ink_text`、`ink_color`、`ink_width`、`annotate_mode`、`pen_undo`、`pen_redo`、`copy_ink`、`paste_ink`、`insert_ink_image`、`clear_page`、`delete_ink` | 已接线：笔迹与原生批注命令 |
| Word 编辑：本地打开/编辑 | `office_edit` | 未接线：`pendingCommand()` 占位，待 task 36 本地 Office 引擎 |
| Word 编辑：选中文字 → AI 批注 | `selection_to_annotation` | 已接线：笔记选区审阅入口 |
| Word 编辑：保存并返回 | `save_return` | 未接线：`pendingCommand()` 占位，待 task 36 保存事务 |
| 设置/模型设置 | `settings_action`、`model_settings` | 已接线：阅读设置与模型设置 |
| 使用说明：关于/快捷键/隐私/诊断 | `about`、`shortcuts`、`privacy_license`、`diagnostics` | 已接线：本地帮助命令 |

## 验收（EARS）

- When 桌面命令清单中任一已接线命令被触发，Android shall 执行同名原生行为，不得保留占位禁用项。
- When 命令属于 `office_edit` 或 `save_return`，Android shall 在 task 36 落地前保持明确的未实现占位，并在 task 35 中如实记录缺口。
- When 清单更新时，Android shall 同步更新本文件与 `tasks.md` task 35，不得只改代码不改文档。

## 验证

- JVM/静态检查：命令 id 与 `MainActivity.menuSections` 一致；`pendingCommand()` 仅保留 `office_edit` 与 `save_return`。
- `:app:assembleDebug` 通过；`git diff --check` 通过。
- API 34 平板 AVD 只做受影响流程回归，不扩展矩阵。

## 关联

- 父任务：`../tasks.md` task 35。
- 导航契约：`adaptive-menu-navigation.md`。
- 总体对齐：`desktop-ui-and-office-parity.md`。

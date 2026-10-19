# 桌面命令 → Android 命令缺口审计

> 范围：本文只核对桌面 `index.html` 命令区到 Android `ReaderMenuCommand.id` 的接线，不审计已接线命令的完整行为语义。语义差异另见 `desktop-semantic-parity.md`。输入包括 `desktop-command-checklist.md`、`tasks.md` task 35/36/37 和 `MainActivity.kt` 的 `menuSections` / `pendingCommand()`。
> 结论先行：在**命令 ID 接线层面**，唯一占位命令是 `office_edit` / `save_return`（归 task 36），其余命令均有 handler。此结论不等同于桌面工作流语义无差异，也不声称完整移植完成。

## 1. 缺口总览

| Android 命令 id | 桌面对应入口 | 状态 | 归属 |
| --- | --- | --- | --- |
| `office_edit` | 桌面 Word 编辑会话入口（`documentEditButton` / `wordTools` 编辑区，桌面侧依赖本机 Microsoft Word，见 `index.html` 帮助文本） | 未接线：`pendingCommand()` 占位，禁用态 | task 36（本地 Office 引擎与桌面格式编辑） |
| `save_return` | 桌面 `wordDone`（保存并返回阅读） | 未接线：`pendingCommand()` 占位，禁用态 | task 36（编辑与 Save & Return 切片、保存事务） |

`menuSections` 中除上述两项外无其他 `pendingCommand()`；与 `desktop-command-checklist.md` 的“`pendingCommand()` 仅保留 `office_edit` 与 `save_return`”一致。

## 2. 逐项对照（桌面 → Android）

### 文件组（`index.html` 文件菜单：文档 + 笔记区）

| 桌面入口 | Android 命令 id | 状态 |
| --- | --- | --- |
| `openButton` / `welcomeOpen`（打开文档） | `open_project` | 已接线：SAF 打开项目 |
| 新建项目对话框（`newConfirm`/`newName`，`src/main.js`） | `new_project` | 已接线：`menuSections` FILE 组内 `actionCommand`（注：checklist 对照表未单列此行，代码侧已接线） |
| `library`（最近文档下拉） | `recent_documents` | 已接线：最近项目列表 |
| `attachment`（插入附件） | `attachments` | 已接线：项目附件管理 |
| `importNote`（导入笔记） | `import_notes` | 已接线：当前页/整份导入 |
| `exportNote`（导出笔记） | `export_notes` | 已接线：当前页/整份导出 |
| `historyButton`（历史版本） | `note_history` | 已接线：页级历史恢复 |

### AI 批注（`aiReviewButton` 及选区批注流）

| 桌面入口 | Android 命令 id | 状态 |
| --- | --- | --- |
| AI 批注（讲解/任务/批注/审阅/交接） | `pdf_text_selection`、`page_tutor`、`agent_task`、`annotations`、`reviews`、`handoff` | 已接线：端侧 Agent 与 OpenAI 兼容接入 |
| `wordSelectionReview`（选中文字 → AI 批注） | `selection_to_annotation` | 已接线：笔记选区审阅入口（Word 编辑分组内唯一已接线项） |

### 编辑组（`data-md`：undo/redo/cut/copy/selectAll）

| 桌面入口 | Android 命令 id | 状态 |
| --- | --- | --- |
| 撤销 / 重做 / 剪切 / 复制 / 全选 | `note_undo`、`note_redo`、`cut`、`copy`、`select_all` | 已接线：页级 Markdown 历史与剪贴板 |

### 段落组（`noteFontTool` / `headingTool` / `data-md` bullet/ordered/task/quote）

| 桌面入口 | Android 命令 id | 状态 |
| --- | --- | --- |
| 字号 / 标题 / 列表 / 编号 / 待办 / 引用 | `font_size`、`heading`、`unordered_list`、`ordered_list`、`task_list`、`quote` | 已接线：Markdown 段落格式化 |

### 格式组（`data-md` bold/italic/strike/underline）

| 桌面入口 | Android 命令 id | 状态 |
| --- | --- | --- |
| 加粗 / 斜体 / 删除线 / 下划线 | `bold`、`italic`、`strikethrough`、`underline` | 已接线：Markdown 内联格式化 |

### 插入组（`data-md` code/math/table/link + `mdImage` + `mdPaste`）

| 桌面入口 | Android 命令 id | 状态 |
| --- | --- | --- |
| 代码 / 公式(`math`→`formula`) / 表格 / 链接 / 图片(`mdImage`→`image`) / 格式粘贴(`mdPaste`→`paste_markdown`) | `code`、`formula`、`table`、`link`、`image`、`paste_markdown`，另有 `paste_image`（剪贴板图片粘贴） | 已接线：Markdown 插入命令 |

### 阅读组（`prev` / `pageNumber` / `next` / `zoomOut` / `zoomIn` / `fit`）

| 桌面入口 | Android 命令 id | 状态 |
| --- | --- | --- |
| 上一页 / 下一页 | `previous_page`、`next_page` | 已接线：页跳转命令 |
| `pageNumber` 页码输入 | （无独立菜单命令 id；对应 `page-jump.md` 跳转流程） | 已接线行为归页跳转 SPEC，不计为命令缺口 |
| OCR / 区域 OCR | `ocr`、`region_ocr` | 已接线：OCR 与区域 OCR |
| 缩小 / 放大 / 适合窗口（桌面单个 `fit`） | `zoom_out`、`zoom_in`、`fit_width`、`fit_page` | 已接线：视口命令（桌面 `fit` 在 Android 拆为适合宽度/整页两项） |

### 手写批注组（`inkTools`：`data-ink-tool` + 颜色/宽度 + 撤销/复制/粘贴/图片/删除）

| 桌面入口 | Android 命令 id | 状态 |
| --- | --- | --- |
| 选择 / 画笔 / 荧光笔 / 橡皮 | `select_tool`、`pen`、`highlighter`、`eraser` | 已接线 |
| 直线 / 箭头 / 矩形+椭圆（桌面 `rect`/`ellipse` 合并） / 文字 | `line`、`arrow`、`shape`、`ink_text` | 已接线（`shape` 覆盖矩形与椭圆） |
| `inkColor` / `inkWidth` | `ink_color`、`ink_width` | 已接线 |
| 批注模式 / 撤销 / 重做 | `annotate_mode`、`pen_undo`、`pen_redo` | 已接线 |
| 复制 / 粘贴 / 图片 / 删除（含桌面 `inkDelete`；清页为 `clear_page`） | `copy_ink`、`paste_ink`、`insert_ink_image`、`delete_ink`、`clear_page` | 已接线 |

### Word 编辑组（`wordTools`）

| 桌面入口 | Android 命令 id | 状态 |
| --- | --- | --- |
| Word 编辑会话入口（桌面依赖本机 Word） | `office_edit` | **缺口**：`pendingCommand()`，待 task 36 本地 Office 引擎 |
| `wordSelectionReview`（选中文字 → AI 批注） | `selection_to_annotation` | 已接线 |
| `wordDone`（保存并返回阅读） | `save_return` | **缺口**：`pendingCommand()`，待 task 36 保存事务 |

### 设置与使用说明（`settingsButton` / `aiSettingsButton` / `helpButton` / `help` 对话框）

| 桌面入口 | Android 命令 id | 状态 |
| --- | --- | --- |
| 设置（含外观、Markdown 主题、语言、换页动画、双栏比例） | `settings_action` | 已接线：阅读设置 |
| AI 设置 | `model_settings` | 已接线：模型设置 |
| 使用说明（关于 / 快捷键 / 隐私与许可证 / 诊断） | `about`、`shortcuts`、`privacy_license`、`diagnostics` | 已接线：本地帮助命令 |

### 导航类返回入口（非命令 id）

桌面 `noteReturn` / `inkReturn` / `inkDone` / `wordReturn`（返回双栏/结束批注）无独立命令 `id`，对应 Android 自适应导航的抽屉/侧栏返回与关闭行为，归 `adaptive-menu-navigation.md` 契约覆盖，不计为命令缺口。

## 3. 非目标（不计为缺口）

- 桌面宿主运行时：Windows COM、macOS AppleScript、桌面 MCP transport（`desktop-ui-and-office-parity.md` §1 明确不跨平台搬运，由端内 Agent 提供学习工作流）。
- 电子表格、账号体系、云协作、远程文档转换服务（同 §1，明确不在本 SPEC 范围内）。
- PDF HTML 批注渲染/剪贴板差异另由 task 38 跟踪（task 35 原文），不属于本命令对照的缺口。

## 4. 命令 ID 审计结论

- 桌面 `index.html` 命令到 Android 命令 `id` 的**唯一未接线占位是 `office_edit` / `save_return`**，两者均为 `menuSections` WORD_EDITING 组内的 `pendingCommand()`，归 task 36。
- 其余桌面命令均有对应已接线命令 `id`（含 `new_project`、`paste_image`、`fit_width`/`fit_page` 拆分、`shape` 合并等一对多/多对一映射，见 §2）。
- 本命令审计不证明已接线 handler 与桌面行为完全一致；其余已发现的工作流语义差异见 `desktop-semantic-parity.md`。
- **不声称完整移植完成**：终态结论与占位清除归 task 37；task 36 落地前 `office_edit` / `save_return` 保持明确未实现占位（与 `desktop-command-checklist.md` EARS 一致）。

## 5. 关联

- 总体对齐：`desktop-ui-and-office-parity.md`
- 工作流语义差异：`desktop-semantic-parity.md`
- 命令清单：`desktop-command-checklist.md`
- 任务：`../tasks.md` task 35（清单与一致性）、task 36（Office 引擎与保存事务）、task 37（收尾审计）、task 38（PDF 富 HTML 批注差异）
- 导航契约：`adaptive-menu-navigation.md`
- 代码（只读）：`android/app/src/main/java/io/pagenote/android/MainActivity.kt`（`menuSections`、`pendingCommand`）
- 桌面参照：仓库根目录 `index.html`、`src/main.js`

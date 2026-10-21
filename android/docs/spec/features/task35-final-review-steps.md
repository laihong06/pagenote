# Task 35 最终复核可执行步骤（未关闭 task 35）

> 状态声明：本文是 `android/docs/spec/tasks.md` task 35 的最终复核步骤文档，**不关闭 task 35，不关闭 task 36，不修改任何实现代码、测试代码与既有 SPEC 结论**。终态结论归 task 37，所有 gap 关闭前不得宣称完整移植。
> 只读审计输入（均未改）：`tasks.md` task 35（含已勾选的清单 SPEC / JVM 校验子项）；`features/desktop-command-checklist.md`（命令对照表 + EARS + 验证节）；`features/desktop-parity-gap-audit.md`（§1–§5，唯一缺口结论）；`final-parity-closeout-plan.md` 第 2.2 节（`pendingCommand()` 清零口径）与第 5 节（执行顺序：task 36 关闭 → task 35 最终复核 → task 37 终态审计）。
> 当前基线快照（只读，不代表完成）：task 35 未关闭；task 36 未关闭；`pendingCommand()` 当前仅保留 `office_edit` 与 `save_return`；task 38 已关闭（PDF 富 HTML 差异不计入命令缺口，仅引用）。

## 0. 范围与执行约束

- 只覆盖 task 35：桌面命令清单与 Android 14+ 功能一致性最终复核。不扩展 API 矩阵，不碰桌面 Mac/Windows 打包与 `release/`，不改 Kotlin/Java/XML。
- 测试范围沿用 `tasks.md` 头部约束与收尾计划门定义：只运行受影响的定向 JVM 测试、`:app:assembleDebug`（及任务原文要求的测试源码编译）与必要时的唯一 API 34 平板 AVD 聚焦回归；`git diff --check` 通过。
- 行号仅供定位，以 `id` 字符串为准（`menuSections` 定义约 3848 行起；`pendingCommand` 定义约 3527 行；`office_edit` 占位约 4263 行；`save_return` 占位约 4306 行）。

## 1. 前置条件（必须全部满足，否则不得进入第 2 节）

1. task 36 已关闭：`office-engine-next-slice.md` 先行切片及后续三切片全部完成（引擎可复现构建验证、项目源与 PDF 预览、编辑与 Save & Return、可靠文字选区），只有引擎可实际打开并渲染的格式才放开扩展名。关闭证据中可查全屏 Office 编辑后 pagenote 返回/保存流程（退出后回到原项目、页码和双栏状态）。
2. `office_edit` / `save_return` 已有真实可执行行为上线：清零不得通过删除菜单项实现（按 `desktop-ui-and-office-parity.md` 第 2 节，命令不得因移动端简化而隐藏）。
3. 确认 task 35 仍为未关闭态：`tasks.md` 中为 `- [ ] 35.`，且第三段“最终对齐仍待所有 gap 实现后复核”尚未被改写为完成结论。
4. 准备好三方文件定位：
   - 清单文档：`android/docs/spec/features/desktop-command-checklist.md`
   - 代码：`android/app/src/main/java/io/pagenote/android/MainActivity.kt`（`menuSections`、`pendingCommand`）
   - 测试：`android/app/src/test/java/io/pagenote/android/DesktopCommandChecklistTest.kt`、`android/app/src/test/java/io/pagenote/android/DesktopParityPlaceholdersTest.kt`

## 2. 三方一致核对（清单文档 / MainActivity menuSections / DesktopCommandChecklistTest）

目标：复核执行者亲手确认三方对同一命令集合的描述完全一致，重点是 `pendingCommand()` 从“仅保留两项”变为“零占位”。

### 步骤 2.1 — 列出代码侧占位集合

- 在 `MainActivity.kt` 中搜索 `pendingCommand(` 调用，提取其第一个字符串参数集合，记为 `pendingCalls`。
- 未关闭前期望：`pendingCalls == {"office_edit", "save_return"}`（分别位于 WORD_EDITING 组内，`selection_to_annotation` 上下两侧）。
- 终态期望（task 36 落地后）：`pendingCalls == {}`（空集）。若非空，停止复核，回 task 36。
- 同步确认 `pendingCommand` 函数定义本身（`enabled = false` + `disabledReason` + 空 `onSelect`）是否还被除上述两项外的任何命令使用。

### 步骤 2.2 — 列出代码侧已接线命令集合

- 在 `MainActivity.kt` 中提取 `"([a-z_0-9]+)",\s*R\.string` 匹配到的命令 `id` 集合，记为 `wired`。
- 核对 `wired` 包含 `DesktopCommandChecklistTest.wiredCommands` 的全部 68 项（`open_project`、`new_project`、`recent_documents`、`attachments`、`import_notes`、`export_notes`、`note_history`、`pdf_text_selection`、`page_tutor`、`agent_task`、`annotations`、`reviews`、`handoff`、`note_undo`、`note_redo`、`cut`、`copy`、`select_all`、`font_size`、`heading`、`unordered_list`、`ordered_list`、`task_list`、`quote`、`bold`、`italic`、`strikethrough`、`underline`、`code`、`formula`、`table`、`link`、`image`、`paste_image`、`paste_markdown`、`previous_page`、`next_page`、`ocr`、`region_ocr`、`zoom_out`、`zoom_in`、`fit_width`、`fit_page`、`select_tool`、`pen`、`highlighter`、`eraser`、`line`、`arrow`、`shape`、`ink_text`、`ink_color`、`ink_width`、`annotate_mode`、`pen_undo`、`pen_redo`、`copy_ink`、`paste_ink`、`insert_ink_image`、`clear_page`、`delete_ink`、`selection_to_annotation`、`settings_action`、`model_settings`、`about`、`shortcuts`、`privacy_license`、`diagnostics`）。
- 终态还须确认：`office_edit` 与 `save_return` 已进入 `wired` 且为真实 `actionCommand`（可执行行为），不再是 `pendingCommand`。
- 保持已知一对多/多对一映射一致，不得丢项：桌面单个 `fit` 对应 `fit_width`/`fit_page` 两项；桌面 `rect`/`ellipse` 合并为 `shape`；`new_project` 仅在代码 FILE 组内（checklist 对照表未单列）；`paste_image` 为剪贴板图片粘贴增项；页码输入归 `page-jump.md`，导航返回归 `adaptive-menu-navigation.md`，均不计为命令缺口（见 gap audit §2 末与导航小节）。

### 步骤 2.3 — 核对清单文档侧

- 打开 `desktop-command-checklist.md` 命令对照表：终态 `office_edit` / `save_return` 两行必须已从“未接线：`pendingCommand()` 占位，待 task 36…”改写为已接线行为描述；其余行保持已接线描述不变。
- 打开该文件 EARS 节：终态“`pendingCommand()` 仅保留 `office_edit` 与 `save_return`”必须已更新为“零占位”口径；“task 36 落地前保持占位”句保留为历史条件，不得删除后让人误以为从无占位。
- 打开该文件验证节：JVM/静态检查条目必须已从“仅保留两项”更新为“零占位”断言；API 34 平板 AVD 条目保持“只做受影响流程回归，不扩展矩阵”。
- 打开该文件关联节：父任务仍指向 `../tasks.md` task 35，不得提前指向 task 37 结论。

### 步骤 2.4 — 核对测试侧（只读核对，改测试本身见第 3 节）

- 打开 `DesktopCommandChecklistTest.kt`：`checklistMatchesWiredMenuCommands` 必须已能表达终态（`pendingCalls` 期望为空集，`wiredCommands` 已含 `office_edit`/`save_return`，见第 3 节具体更新要求）；`checklistDocumentsOnlyTrackedPlaceholders` 对 checklist 文档关键词的断言必须与第 2.3 节改写后的文档一致。
- 打开 `DesktopParityPlaceholdersTest.kt`：`pendingPlaceholdersAreOfficeEditAndSaveReturnOnly` 的“仅两项”断言必须已更新为“零占位”断言（见第 3 节），不得保留旧断言与新代码并存。
- 若三方任一方仍是旧口径（文档写零占位但测试仍断言两项，或代码已清零但文档仍写占位），停止复核，先拉齐三方再重进本节。

## 3. `pendingCommand()` 清零后的测试更新要求

> 本节是复核关闭者必须落实的更新清单。改测试代码本身属于 task 35 关闭动作，不属于本文（本文不改代码）。

1. `DesktopCommandChecklistTest.kt`：
   - `pendingCommands` 从 `setOf("office_edit", "save_return")` 改为空集（或等价零占位表达）。
   - `checklistMatchesWiredMenuCommands` 中 `assertEquals(setOf("office_edit", "save_return"), pendingCalls)` 改为对空集的断言；`pendingCommands.intersect(wired)` 的旧断言同步改为终态口径（两项已转入 `wired`，交集口径不再适用）。
   - `wiredCommands` 必须新增 `office_edit` 与 `save_return`（作为已接线命令），总数 68 → 70。
   - `checklistDocumentsOnlyTrackedPlaceholders` 仍保留对 checklist 文档的关键词覆盖，但断言内容必须与改写后的 checklist 文档一致（不再要求文档保留“占位”表述）。
2. `DesktopParityPlaceholdersTest.kt`：
   - `pendingPlaceholdersAreOfficeEditAndSaveReturnOnly` 的 `assertEquals(setOf("office_edit", "save_return"), pendingCalls)` 改为零占位断言；测试名若保留须名实相符，建议随关闭改名或在记录模板中注明新口径。
3. `ReaderMenuModelTest` 与 `:app:testDebugUnitTest` 受影响部分：随 task 35 关闭重跑定向用例，不得用全量历史记录代替本轮结果（收尾计划门 A）。
4. 文档同步（与代码/测试同一轮落地，不得只改代码不改文档）：
   - `desktop-command-checklist.md`（对照表 + EARS + 验证节，见 2.3）。
   - `tasks.md` task 35：勾选关闭框，并在条目内如实记录清零证据（测试名、AVD 回归、构建证据），不得提前写 task 37 终态结论。
5. 构建门：`:app:assembleDebug`（及任务原文要求的测试源码编译）通过 + `git diff --check` 通过（收尾计划门 B）。task 37 汇总时需引用本轮四项证据中的 task 35 部分，不得用一次构建代替四项证据。

## 4. 中英文禁用原因回归（API 34 平板 AVD，唯一常规仪器设备）

字符串位置（只读定位，以 `name` 为准）：

- 英文：`android/app/src/main/res/values/strings.xml`：`reader_menu_unavailable_reason`（通用未接入）、`reader_menu_office_desktop_only`（`office_edit`）、`reader_menu_save_return_desktop_session`（`save_return`）。
- 简体中文：`android/app/src/main/res/values-zh-rCN/strings.xml` 同名三项。
- 文案对照：
  - `office_edit`：英文 `Complex Office editing must be done in the desktop app.` / 中文 `复杂 Office 文档编辑请回桌面端处理。`
  - `save_return`：英文 `This command requires an active desktop Office editing session.` / 中文 `此命令需要可返回的桌面 Office 编辑会话。`
  - 通用占位默认：英文 `This command has not been implemented in the app yet.` / 中文 `此命令尚未接入应用。`

### 步骤 4.1 — 清零前预检（若复核时占位仍在，仅用于确认 task 36 尚未落地）

- 同一台 API 34 平板 AVD，先后切换系统语言为英文与简体中文。
- 打开 WORD_EDITING 分组：`office_edit` 与 `save_return` 均呈禁用态，点击无效果，所示禁用原因分别等于上述对应文案（英文模式显示英文，中文模式显示中文）。
- 不得将禁用归因于 Android 版本；中英文任一对不上即停止复核，记为 task 35 文案回归失败。

### 步骤 4.2 — 清零后终态回归（task 35 关闭的必备 AVD 证据）

- 同一台 API 34 平板 AVD，英文与简体中文各跑一遍受影响流程（不扩展手机/OEM/API 35/16 KB 矩阵）：
  1. `office_edit` 与 `save_return` 不再呈禁用态，不再显示第 4 节三项占位文案中的任何一项；两项均为可点击、可执行行为。
  2. 其余命令的禁用原因口径不变且中英文正确（如需项目/PDF/可写/选区等条件命令，沿用 task 31 口径延续：通用未接入用中性原因，附件/复杂 Office/Save and Return 显示各自移动端范围或桌面会话原因）。
  3. 全屏 Office 编辑往返：进入编辑后保留 pagenote 返回/保存流程，退出后回到原项目、页码和双栏状态（该行为主证据归 task 36 关闭记录，task 35 记录中引用即可）。
- 通过标准：英文 1/1、简体中文 1/1（对应 task 35 原文“只验证受影响的用户流程与 API 34 AVD”与收尾计划门 C 的“受影响流程回归（英文/简体中文禁用原因）”）。
- 离线要求（门 C 延续）：飞行模式下 Office 阅读/编辑、Markdown/Mermaid 预览、离线 OCR 均不得访问远程 renderer 或 Office server。

## 5. 任务关闭记录模板（task 35 关闭者在 `tasks.md` 或关闭 PR 中逐项填写）

```markdown
### task 35 最终复核关闭记录（未关闭前不得宣称完成）
- task 36 状态：已关闭（关闭记录链接：<link>；四切片证据齐全，扩展名仅放开引擎可渲染格式）。
- pendingCommand() 清零证据：
  - `pendingCalls` grep 结果：<粘贴空集输出>（`pendingCommand(` 仅剩定义本身，无调用；或列出零调用）。
  - `office_edit` / `save_return` 已转 `actionCommand`（文件：MainActivity.kt，WORD_EDITING 组，`selection_to_annotation` 上下两侧）。
- 三方一致核对：
  - 清单文档：desktop-command-checklist.md 对照表/EARS/验证节已更新（commit：<sha>）。
  - 代码：MainActivity.kt menuSections 已接线 70 项（含 office_edit/save_return），fit 拆分/shape 合并/new_project/paste_image 映射一致。
  - 测试：DesktopCommandChecklistTest（零占位断言，wired 70 项）/ DesktopParityPlaceholdersTest（零占位断言）已更新。
- 定向测试门（门 A）：DesktopCommandChecklistTest、ReaderMenuModelTest、:app:testDebugUnitTest 受影响部分全过（粘贴本轮结果，不得用历史代替）。
- 构建门（门 B）：:app:assembleDebug 通过；git diff --check 通过。
- AVD 回归门（门 C，API 34 平板唯一设备）：英文 1/1 通过；简体中文 1/1 通过；占位文案已消除，其余禁用原因中英文正确；离线要求通过。
- 文档同步：desktop-command-checklist.md 与 tasks.md task 35 同一轮更新（commit：<sha>）。
- 不宣称声明：本记录仅关闭 task 35；完整移植/全量对齐结论归 task 37，task 12/30/36 全部关闭前不宣称。
- 关闭者/日期：<name> / <yyyy-mm-dd>
```

## 6. 不宣称与非目标

- 本文不关闭 task 35/36/37 中的任何一项；复核执行者关闭 task 35 后仍不得宣称完整移植、桌面功能全量对齐完成、Office 可用——终态结论只能写在 task 12/30/35/36 全部关闭之后的 task 37 审计中。
- 合成回归通过不得外推；AVD 通过不得外推为真机已验证；候选研究（Collabora/LibreOfficeKit 文档阅读与构建验证）不得当作交付（收尾计划第 4 节延续）。
- PDF HTML 批注渲染/剪贴板差异归 task 38（已关闭），task 35 复核只引用结论，不重验。
- 宿主运行时（Windows COM、macOS AppleScript、桌面 MCP transport）、电子表格、账号体系、云协作、远程文档转换为明确非目标，不计为缺口，不得在 task 35 复核中补实现。
- 如复核发现新的 UI 缺口，应新建独立 SPEC/任务，不得在 task 35 内直接认领完成。

## 7. 关联（只读）

- 任务：`../tasks.md` task 35（本体）、task 36（Office 引擎与保存事务）、task 37（收尾审计）、task 38（已关闭，仅引用）。
- 命令清单：`desktop-command-checklist.md`；缺口审计：`desktop-parity-gap-audit.md`；收尾计划：`../final-parity-closeout-plan.md`（2.2/5）。
- 总体对齐：`desktop-ui-and-office-parity.md`（第 2 节命令口径、第 5 节 EARS、第 6 节验证矩阵）。
- 代码（只读）：`MainActivity.kt`（`menuSections`、`pendingCommand`）；测试（只读）：`DesktopCommandChecklistTest.kt`、`DesktopParityPlaceholdersTest.kt`。
- 字符串（只读）：`android/app/src/main/res/values/strings.xml`、`android/app/src/main/res/values-zh-rCN/strings.xml`。
- 桌面参照（只读）：仓库根目录 `index.html`、`src/main.js`。

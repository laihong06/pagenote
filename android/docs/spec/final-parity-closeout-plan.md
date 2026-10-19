# Task 37 收尾审计可执行计划（未关闭任何任务）

> 状态声明：本文是 `android/docs/spec/tasks.md` task 37 的收敛计划文档，**不关闭 task 37 本体，不关闭 task 12/30/35/36，不修改任何实现代码与既有 SPEC 结论**。
> 只读审计输入（均未改）：`tasks.md` task 37；`features/desktop-ui-and-office-parity.md` 第 5/6/7 节；`features/desktop-parity-gap-audit.md`；`features/desktop-command-checklist.md`；`features/task12-remaining-acceptance.md`；`features/long-markdown-coldstart-gap.md`；`features/office-engine-next-slice.md`。
> task 37 原文目标：对照 `desktop-ui-and-office-parity.md` 的菜单表、桌面功能入口与文档格式，逐项确认 Android 可执行行为，清除未实现占位和仅展示 UI；每个缺口落到独立 SPEC/任务，并在终态所有任务关闭前不宣称完整移植。

## 1. 当前基线（只读快照，不代表完成）

- task 12：未关闭。剩余验收口径以 `features/task12-remaining-acceptance.md` 为准。
- task 30：未关闭。唯一剩余子项以 `features/long-markdown-coldstart-gap.md` 为准。
- task 35：未关闭。`pendingCommand()` 当前仅保留 `office_edit` 与 `save_return`，终态要求清零。
- task 36：未关闭。`office_edit` / `save_return` 为唯一命令缺口，占位保持到 task 36 落地。
- task 38：已关闭，PDF HTML 批注差异不计入 task 37 的命令缺口，仅作相邻证据引用。
- 命令缺口审计结论（只读引用 `features/desktop-parity-gap-audit.md`）：桌面 `index.html` 命令到 Android 命令 id 的唯一缺口是 `office_edit` / `save_return`，归 task 36。

## 2. Task 37 关闭条件（必须全部满足，缺一不可）

### 2.1 task 12/30/35/36 全部关闭

task 37 不得先于以下四项关闭：

- task 12：`task12-remaining-acceptance.md` 第 1/2/3 条全部消除：API 34 平板 AVD 批注/笔迹保存重载闭环通过且已知 `waitUntil(10s)` flake 已按 SPEC 单独调查闭环；冷启动第 31 页 ANR 按 task 30 口径处理完毕；代表性功能闭环完成且 Office 待实现声明不再适用（即 task 36 已关闭）。侧键橡皮擦按原文口径记为未验证硬件行为，不扩展常规矩阵。
- task 30：`long-markdown-coldstart-gap.md` 的唯一剩余子项完成：在 API 34 平板 AVD 上，对隔离的 69 页验收副本执行应用进程冷启动、恢复/跳转第 31 页、编辑和保存，确认无系统 ANR。三项合成回归（`NoteLongMarkdownEditorTest` 三用例）不得外推为冷进程已验证；小米实机 ANR 须有明确记录，不得含糊。
- task 35：`pendingCommand()` 清零（见 2.2）；`desktop-command-checklist.md` 与 `tasks.md` task 35 同步更新；`DesktopCommandChecklistTest` 校验通过；且最终对齐复核发生在所有 gap 实现之后。
- task 36：`office-engine-next-slice.md` 先行切片及后续三切片全部完成：引擎可复现构建验证、项目源与 PDF 预览、编辑与 Save and Return、可靠文字选区。只有引擎可实际打开并渲染的格式才放开扩展名；关闭前 `office_edit` / `save_return` 保持禁用占位。

### 2.2 `pendingCommand()` 清零

- 当前允许状态（未关闭前）：`pendingCommand()` 仅保留 `office_edit` 与 `save_return`，与 `desktop-command-checklist.md` EARS 一致。
- task 37 关闭态：`menuSections` 中无任何 `pendingCommand()`；`DesktopCommandChecklistTest` 中“仅保留两项”的断言必须已更新为“零占位”断言；桌面 `fit` 拆分与 `shape` 合并等映射保持文档与代码一致。
- 清零不得通过删除菜单项实现：按 parity 第 2 节，命令不得因移动端简化而隐藏，必须是真实可执行行为上线。

### 2.3 占位 UI 清除

- 无未接线但可点击无效果的命令；无仅展示 UI、无后端行为的页面或按钮。
- 禁用态命令必须显示准确禁用原因（task 31 口径延续）：通用未接入命令用中性原因；附件、复杂 Office 编辑、Save and Return 显示各自移动端范围或桌面会话原因；任何未实现命令不得归因于 Android 版本。
- 进入全屏 Office 编辑后保留 pagenote 返回/保存流程，退出后回到原项目、页码和双栏状态；该行为在 task 36 关闭证据中可查。

### 2.4 每个缺口有独立 SPEC/任务

- `office_edit` / `save_return` 命令缺口归 task 36；SPEC 为 `features/office-save-policy.md`、office-editor-plan 四切片、`features/office-engine-next-slice.md`。
- 长 Markdown 冷启动冷进程验证归 task 30；SPEC 为 `features/large-markdown-page-layout.md`、`features/long-markdown-coldstart-gap.md`。
- API 34 批注/笔迹保存重载与已知 flake 归 task 12；SPEC 为 `features/task12-remaining-acceptance.md` 第 1 条；flake 单独调查，不得以不阻塞为由直接关闭 task 12。
- 命令清单最终复核归 task 35；SPEC 为 `features/desktop-command-checklist.md`、`features/desktop-parity-gap-audit.md`。
- 宿主运行时、电子表格、账号、云协作、远程转换为明确非目标，不计为缺口，不得在 task 37 中补实现。
- PDF HTML 批注差异归 task 38（已关闭），task 37 只引用结论，不重验。

## 3. 证据门（每项前置任务单独过门，task 37 只做汇总核对）

测试范围沿用 `tasks.md` 头部约束：只运行受影响的定向测试、Debug 构建和必要时唯一 API 34 平板 AVD；不扩展手机/OEM/API 35/16 KB 矩阵。API 35/16 KB 历史结果仅作审计参考，不是重跑门槛。

### 门 A：JVM 回归（受影响模块定向）

- task 35：`DesktopCommandChecklistTest`、`ReaderMenuModelTest` 及 `:app:testDebugUnitTest` 受影响部分。
- task 36：`OfficeSavePolicyTest` 及后续切片新增的聚焦 JVM 用例。
- task 30：长 Markdown 布局/编辑/保存隔离性的聚焦 JVM 用例。
- task 12：批注/笔迹 store、mapper/draft 及 flake 对应的聚焦 JVM 用例。
- 通过标准：受影响用例全过；不得用全量历史记录代替本轮受影响用例结果。

### 门 B：`assembleDebug`

- 各前置任务落地时分别通过 `:app:assembleDebug`（及任务原文要求的测试 APK 编译）。
- task 37 汇总时核对每项关闭记录中的构建证据，不得用一次构建代替四项证据。
- 另需 `git diff --check` 通过。

### 门 C：API 34 AVD 聚焦回归（唯一常规仪器设备）

- task 12：API 34 平板 AVD 批注/笔迹保存重载闭环；已知超时 flake 的单独调查记录。
- task 30：隔离 69 页副本的应用进程冷启动、恢复/跳转第 31 页、编辑、保存，无系统 ANR；正文完整可编辑，保存回读后只有当前页测试标记变化。
- task 35：受影响流程回归（英文/简体中文禁用原因），不扩展 API 矩阵。
- task 36：单台 API 34 平板 AVD 闭环覆盖导入、阅读、编辑、保存、关闭、重开；并按 parity 第 6 节验证适用于已实现切片的失败分支。
- 离线要求：飞行模式下 Office 阅读/编辑、Markdown/Mermaid 预览、离线 OCR 均不得访问远程 renderer 或 Office server。

## 4. 不宣称完成声明（task 37 关闭前必须逐条满足）

- 在 task 12/30/35/36 全部关闭前，不得宣称完整移植、桌面功能全量对齐完成、Office 可用。
- 合成回归通过不得外推：`NoteLongMarkdownEditorTest` 三项合成用例未覆盖冷进程、未使用原用户 PDF，不得写成冷启动已验证。
- AVD 通过不得外推为真机已验证：小米 Pad 5 Pro 实体笔压力/误触/延迟、侧键映射、MIUI 差异、真实系统 picker/持久授权按原文口径单独记录，不得用 AVD 结论覆盖。
- 候选研究不得当作交付：Collabora/LibreOfficeKit 文档阅读与构建验证在引擎切片完成前不得写成 Office 编辑已可用；无法渲染的扩展名不得提前放开。
- task 37 的终态审计结论只能写在四项前置任务全部关闭之后，本文不写该结论。

## 5. 执行顺序（建议，主线程排期参考）

1. task 36 先行切片（引擎可复现构建验证），此期间 `office_edit` / `save_return` 保持占位。
2. task 30 剩余冷进程子项（隔离副本，API 34 AVD），同步消除 task 12 第 2 条的 ANR 引用。
3. task 12 批注/笔迹闭环与 flake 单独调查。
4. task 36 剩余三切片，关闭 task 36。
5. task 35 最终复核（所有 gap 实现后）：`pendingCommand()` 清零与清单/代码/测试三方一致，关闭 task 35。
6. task 37 终态审计：按 parity 第 5 节 EARS 与第 6 节验证矩阵逐项勾选，清除占位 UI，确认每个缺口均有独立归属，方可关闭 task 37。

## 6. 非目标（本文明确不做）

- 不关闭 task 12/30/35/36/37 中的任何一项。
- 不写实现代码，不新增或修改测试，不跑构建（含 JVM、`assembleDebug`、AVD）。
- 不碰桌面 Mac/Windows 打包与 `release/`，不改 Kotlin/Java/XML。
- 不做 commit 与 push；后续提交由主线程负责。
- 如终审发现新的 UI 缺口，应新建独立 SPEC/任务，不得在本文件内直接认领完成。

## 7. 关联（只读）

- 任务：`android/docs/spec/tasks.md` task 12/30/35/36/37（task 38 已关闭，仅引用）。
- 总体对齐：`features/desktop-ui-and-office-parity.md`（第 5 节 EARS、第 6 节验证矩阵、第 7 节关联）。
- 缺口审计：`features/desktop-parity-gap-audit.md`；命令清单：`features/desktop-command-checklist.md`。
- task 12 口径：`features/task12-remaining-acceptance.md`。
- task 30 口径：`features/long-markdown-coldstart-gap.md`、`features/large-markdown-page-layout.md`。
- task 36 口径：`features/office-engine-next-slice.md`、`features/office-save-policy.md`、`android/docs/spec/office-editor-plan.md`。
- 桌面参照：仓库根目录 `index.html`、`src/main.js`。

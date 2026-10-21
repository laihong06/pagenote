# Office 项目源与 PDF 预览切片（task 36 第二切片定义）

**Status:** 第二切片可执行定义；task 36 仍为未完成（open），本文不声称 task 36 已完成，不写实现代码。

## 目标

把 `../../office-editor-plan.md` “Implementation Slices” 第 2 项（Project source and preview）写成可执行定义：Office 源保留原格式、PDF 预览为可丢弃派生缓存；仅当引擎可实际渲染时才放开对应扩展名；任何失败保留源文件。

本切片是 `features/office-engine-next-slice.md`（第一切片：引擎可复现构建验证）的后继，不替代它，不提前做编辑与 Save & Return、可靠文字选区。

## 输入（只读审计，不改）

- `../../office-editor-plan.md`：四切片划分与 EARS 验收；本文件只定义第 2 项。
- `../tasks.md` task 36：离线、进程内、不调外部 Office app、不调远程服务；先验证上游引擎，不先放开无法渲染的扩展名。
- `features/office-save-policy.md`：纯 project-core 保存就绪策略（`OfficeSavePolicyTest` 6/6），SAF 与 editor 集成仍 open；本切片不改变该策略状态。
- `features/office-engine-next-slice.md`：第一切片定义；本切片以前一切片通过为前置条件。

## 前置条件

- 第一切片已通过：arm64 引擎产物可复现构建，pagenote 自有 Activity/View 已在单台 API 34 平板 AVD 上离线完成 load → render → edit → release。
- 在第一切片通过前，本切片不得进入可执行阶段；`.doc/.docx/.ppt/.pptx` 仍被 opener 拒绝。

## 非目标（本切片明确不做）

- 不做编辑与 Save & Return 切片、可靠文字选区切片。
- 不接 editor UI 编辑写回、不改 `office_edit` / `save_return` 的 `pendingCommand()` 可用状态（仍指向 task 36）。
- 不写 Kotlin/Java/XML 或任何实现代码；本文件只是可执行定义。
- 不声称 task 36 已完成，不关闭缺口审计中的任何 gap。

## 可执行定义

1. **源格式保留**：项目源以原始 `.doc/.docx/.ppt/.pptx` 字节为准，保留原扩展名与完整源字节；PDF 预览永远不是可编辑源。
2. **源接入方式**：从 SAF 源复制到 app 自有工作目录；对完整源字节计算 SHA-256 并记录字节数，同时记录打开时的源 revision（provider 时间戳不作为权威）。
3. **派生预览**：用已验证的本地引擎在离线状态下导出本地 PDF 预览；预览仅供现有 PDF 阅读器/笔记对齐消费。
4. **缓存键**：可丢弃预览缓存键必须包含源 SHA-256、源格式、引擎修订、导出设置/版本；任一分量变化即视为不同缓存项，不得复用旧预览冒充新源。
5. **失效规则**：源字节变化、引擎修订变化或导出设置变化时，旧预览失效并可直接丢弃重建；不得把失效预览当作源。
6. **扩展名门控**：只有引擎已证明可实际打开并渲染的格式，才可放开对应扩展名的项目源入口；无法渲染的扩展名不得提前放开，opener 拒绝行为保持不变。
7. **阅读器对接**：预览页接入既有 PDF 阅读器/笔记对齐；页数变化时保留越界页笔记数据，不丢弃用户批注/笔迹/AI review 文件。
8. **失败分支**：引擎导出失败、SAF 读取失败、权限丢失或工作目录写入失败时，保留最后有效源文件，不生成冒充成功的预览，不报告导入成功。

## 扩展名门控

- 本切片通过前已放开的格式集合为空；每放开一种扩展名，必须有该格式在 API 34 平板 AVD 上的离线 open → preview fixture 记录。
- 未放开的扩展名仍被 opener 拒绝；`office_edit` / `save_return` 保持禁用占位并指向 task 36。

## EARS 验收（本切片）

- When 用户导入受支持的 Office 源，the app shall 保留原扩展名与完整源字节，并记录源 SHA-256 revision。
- When 源、引擎或导出设置任一变化，the app shall 使旧 PDF 预览失效并按新缓存键重建，不得复用过期预览。
- When 用户打开 PDF 预览，the app shall 仅将其作为可丢弃派生缓存消费，不得将其作为可编辑源。
- When 仅引擎可实际渲染的扩展名被请求，the app shall 才放开对应项目源入口；否则拒绝并保持源不变。
- If 预览导出或源接入失败，the app shall 保留最后有效源文件，不声称导入或预览成功，并明确标识失败原因。

## 验证记录要求

- 每种放开格式的 fixture（`.doc/.docx/.ppt/.pptx` 按门控逐个记录）、源 SHA-256、引擎修订、导出设置/版本、预览缓存键、API 34 平板 AVD 离线 open → preview 结果。
- 失败分支记录：导出失败/SAF 失败/权限丢失时源保留证据。
- 仅要求单台 API 34 平板 AVD；不建跨 API/跨设备矩阵（沿用 task 36 约束）。
- 后续切片（Save & Return、选区）各有独立验收，不在本文件提前认领。

## 关联

- 实施总计划：`../../office-editor-plan.md`
- 任务：`../tasks.md` task 36（open）、task 37（收尾审计归属）
- 前一切片：`features/office-engine-next-slice.md`
- 保存策略：`features/office-save-policy.md`
- 缺口审计：`features/desktop-parity-gap-audit.md`
- 总体对齐：`features/desktop-ui-and-office-parity.md` §4 / §7

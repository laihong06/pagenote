# Agent 任务执行 UI 闭环

## Scope

Task 10 切片三：把 `AgentRunner` + `ProjectAgentTools.handlers` 接到阅读器 UI，提供单页任务执行入口：读当前页上下文、跑工具循环、展示实际完成/修改范围。本片只做单页闭环，多页串行沿用 `SerialPages` 语义后续再接 UI。

## Behavior

- 入口：阅读器工具栏新增 Agent 任务按钮，打开任务对话框（指令输入 + 执行 + 结果展示）。
- 执行前保存脏笔记：有未保存修改先 save，失败则中止任务（沿用 page tutor 语义）。
- 工具接线：用 `ProjectAgentTools.handlers(resolver, project, renderer, currentPage)` 构 `LocalToolDispatcher`，当前页传入 1-based 页号；`renderer == null` 即 Markdown 源态，read 工具按既有回退语义返回 unavailable。
- 初始 messages：复用 `PageRequest.build` 组单页材料（当前页笔记 + revision + 原生/Markdown 源文及最多前后相邻页）。PDF 原生文字不可用或为空时，须在请求模型前用离线 OCR 准备当前页和相邻页；OCR 仅发生在上下文准备阶段，不在 Agent 工具循环内触发。上下文准备整体受 OCR 超时限制，OCR 置信度/警告须随源文传入模型上下文。
- ONNX `session.run()` 为同步原生调用，协程超时不能强制中断已开始的推理；超时取消会阻止后续模型请求，并在当前原生调用返回时完成取消与资源释放。
- 步数：`maxSteps` 默认 8（`AgentLoop` 允许 1..16），UI 不暴露配置。
- 结果：`Done` 展示 pages/updated（只报真实完成/修改页）；`Failed` 展示 reason（限流明确提示停止页）；工具失败（ok=false，含 revision 冲突）不中断循环，由 `AgentRunner` 回传模型继续。
- 缺模型配置：抛 `MissingModelConfigException` 时打开模型设置，不发请求。
- 写工具落盘后刷新当前页 UI：若 updated 含当前页，重读笔记并更新 draft/baseRevision。
- 任务执行中禁用重复执行与翻页写入入口（最小集：执行中禁用执行按钮与保存按钮）。

## Acceptance (EARS)

- When 用户输入指令并执行时，shall 用当前页上下文跑工具循环并展示真实完成/修改页。
- When 任务涉及写工具且 revision 冲突时，shall 不覆盖并在结果中报告失败原因。
- When 模型限流或超步时，shall 停止并报告实际完成范围，不伪称完成。
- When 缺模型配置时，shall 打开模型设置且不发请求。
- When 当前页被任务修改时，shall 刷新笔记 UI 到最新 revision。
- When Android 14 上 PDF 原生文字不可用时，shall 在请求模型前对当前页和可用相邻页执行离线 OCR；若 OCR 失败或超时，shall 停止本次任务并显示明确错误，不发送空源文。
- When 源文由 OCR 得到且包含警告时，shall 将警告一并包含在模型上下文中，并明确该内容需要对照原 PDF 核验。

## Verification

- JVM：`PageTutorSourceLoaderTest` 9 项、`AgentTaskInputTest` 3 项、`IoOwnedResourceTest` 1 项通过，覆盖 API 34 原生 PDF 文字不可用时当前页/相邻页 OCR 回退、OCR 警告传递、既有 Page Tutor 原生文字优先行为及取消后的资源释放。
- API 34 平板 AVD：`AgentTaskDialogTest` 6/6 通过；arm64 Debug APK 安装并启动。
- `:app:compileDebugAndroidTestKotlin`、`:app:assembleDebug`、`:app:assembleDebugAndroidTest` 通过。真实模型服务和小米真机 OCR 闭环仍待 task 12 验收。

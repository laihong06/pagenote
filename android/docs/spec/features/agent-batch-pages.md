# Agent 批量页任务 UI

## Scope

Task 10 切片四：在单页任务闭环上加批量页执行 UI：用户输入页范围（如 `2-4`），按 `SerialPages` 语义逐页串行跑单页任务，汇总真实完成/修改/未完成页。本片不做后台批量、不并发，限流即停。

## Behavior

- 入口：复用 Agent 任务对话框，新增页范围输入（默认当前页，如 `3`；支持 `2-4` 与 `1,3,5` 混写，空格忽略）。
- 解析 `AgentPageRange.parse(text, totalPages)` 纯函数：1-based、去重、保序、越界/空/格式错抛 `IllegalArgumentException`（UI 转本地化错误，不发请求）。
- 执行：逐页复用单页任务链路（存脏笔记→组上下文→`AgentRunner` + 真实 handlers→落盘刷新），每页独立 `maxSteps=8`；页间不共享历史 messages。
- 报告：`SerialPages.Report` 直显（completed/updated/incomplete + 限流标记）；限流停后剩余页记未完成，不继续。
- 写后刷新：updated 含当前页则重读笔记刷新 draft/baseRevision；批量页若含多页修改，只刷新当前页 UI，其它页靠翻页时 `LaunchedEffect` 重载。
- 执行中禁用范围输入与重复执行；缺模型配置打开模型设置，不发请求。

## Acceptance (EARS)

- When 用户输入合法页范围并执行时，shall 逐页串行跑并报告真实完成/修改/未完成页。
- When 页范围非法时，shall 本地报错且不发模型请求。
- When 某页限流时，shall 停止后续页并标记剩余为未完成。
- When 普通页失败时，shall 继续下一页，不中断整批。

## Verification

- JVM：范围解析（单页/区间/混写/去重/越界/空）+ `SerialPages` 既有用例。
- 仪器编译：范围输入框与结果 tags 可达。
- `cd android && ./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --offline` 通过；真机与真模型待 task 12。

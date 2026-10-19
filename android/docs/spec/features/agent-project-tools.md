# Agent 项目工具接入（只读先行）

## Scope

Task 10 切片一：把 `AgentRunner` 的 `LocalToolDispatcher` 接到真实项目数据，先只读工具（read_page/read_review/read_handoff），写工具（write_note/patch_note 等）下片再做。本片无 UI，只做工具接线 + 测试。

## Tool Contract

- 新增 `ProjectAgentTools(resolver, project: OpenedProject, renderer: PdfDocumentSession?)`，`handlers(): Map<String, suspend (ToolRequest) -> ToolResult>`。
- read_page：args `page`（1-based，缺省当前页），返回该页笔记 + 原生文字（经 `extractText`，不可用则标 unavailable，不跑 OCR）；revision 附带 `PageNotes.revision`。
- read_review：args `status?`（open/applied/dismissed 全支持，不传则全列），经 `ProjectReviewStore.list`，返回 id/number/side/kind/page/status/instruction 摘要。
- read_handoff：args `page`，经 `ProjectHandoffStore.view`（noteRev 取该页当前 revision，srcRev 取 `renderer.sourceRevision` 或空），返回 summary + stale。
- 所有工具只读不写；缺项目/缺页/异常返回 `ok=false + detail`，不抛；revision 冲突语义下片写工具再做。

## Acceptance (EARS)

- When Agent 调用 read_page 时，shall 返回该页笔记、原生文字与 revision。
- When 调用 read_review 时，shall 返回过滤后的记录摘要。
- When 调用 read_handoff 时，shall 返回交接摘要与 stale 标记。
- When 参数非法或数据缺失时，shall 返回 ok=false 而不抛错中断循环。

## Verification

- JVM（agent 模块 + app File 等价）：三工具正常/缺参/缺数据用例。
- `AgentRunner` 接 handlers 跑一轮 TOOL→DONE，断言 touchedPage。
- `cd android && ./gradlew :agent:testDebugUnitTest :app:testDebugUnitTest --offline` 通过。

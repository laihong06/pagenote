# Agent 工具集范围（已实现 7 个）

## Scope

锁定端侧 Agent 首版实际接线的 7 个本地工具，避免 `LOCAL_TOOLS` 声明与实现脱节。`read_state/prepare_page/attach_image/navigate` 四个名额保留但本版不分发，调用即报 unknown tool。

## Tool Contract

- 已接线：`read_page/read_review/read_handoff/write_note/patch_note/write_review_status/write_handoff`（见 `agent-project-tools.md`、`agent-write-tools.md`）。
- 未接线：`read_state/prepare_page/attach_image/navigate` 在 `LocalToolDispatcher` 无 handler，分发即 `ok=false + unknown tool`，不中断循环。
- `ToolSchemas.SCHEMAS` 与 `AgentLoop.LOCAL_TOOLS` 保持同 key；参数名以 `ProjectAgentTools` 实现为准（`write_review_status: id/status`，`write_handoff: page/summary/expectedRevision/sourceRevision`）。
- `LocalToolDispatcher` 在调用 handler 前执行 `ToolSchemas.check`；缺必填参数、非法页码或缺少写入 revision 时直接拒绝，不进入项目操作。

## Acceptance (EARS)

- When 模型调用 7 个已接线工具时，shall 按各自契约执行并返回 ok/touched/updated。
- When 模型调用 4 个未接线工具时，shall 返回 unknown tool 并继续循环，不伪称执行。
- When 未知工具携带页码参数时，shall 不将该参数计入实际触达/完成页。
- When 模型的已接线工具参数不满足 schema 时，shall 拒绝请求且不调用 handler。

## Verification

- JVM：`ToolSchemasTest` 覆盖已接线参数与未知工具拒绝；`AgentRunnerTest` 覆盖无效参数在 handler 前被拒绝，以及 unknown tool 错误反馈后继续调用可用工具且不计入其页码。
- `cd android && ./gradlew :agent:testDebugUnitTest --offline` 通过。

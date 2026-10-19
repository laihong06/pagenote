# Agent 提示词与交接接线关系

## Scope

明确 `PromptIndex` / `HandoffCache`（agent 纯内存）与 app 落盘归属，避免重复实现。两缓存都不在任务循环内自动调用，由调用方按需使用。

## Wiring Contract

- `PromptIndex`：只做场景路由与按 revision 分批读判定；模块正文不内置，由调用方按需传入。`AgentTaskInput` 组单页材料时不拉全量提示词，只带当前页与相邻源页（沿用 page-tutor 语义）。
- `HandoffCache`：只做内存 stale 判定；落盘读写归 `ProjectHandoffStore`（`ai-handoffs.json`），`read_handoff/write_handoff` 工具已接该 store，不经此缓存。
- 任务循环内不自动读提示词、不自动读写交接；需要时由 UI 显式触发对应工具。

## Acceptance (EARS)

- When 组装单页任务时，shall 只带当前页与相邻源页，不全量加载提示词。
- When 读写交接时，shall 经 `ProjectHandoffStore` 落盘，`HandoffCache` 只做内存视图判定。

## Verification

- 既有 `PromptIndexTest` / `HandoffCacheTest` 覆盖判定语义；app 侧不新增落盘路径。
- `cd android && ./gradlew :agent:testDebugUnitTest --offline` 通过。

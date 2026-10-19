# Agent 写工具接入（revision 保护）

## Scope

Task 10 切片二：`write_note` / `patch_note` / `write_review_status` / `write_handoff` 四个写工具，全部带 revision 保护。本片无 UI，只做接线 + 测试。

## Tool Contract

- write_note：args `page` + `markdown` + `expectedRevision`（必填），经 `PageNoteEditor.replacePage` + `ProjectNoteStore.write` 落盘；冲突（`PageNoteConflictException` / `NoteDocumentConflictException`）返回 ok=false + 原因，不抛。
- patch_note：args `page` + `start` + `end?` + `replacement` + `expectedRevision`，经 `NoteRange.patch` 对页内切片打补丁后走 write_note 同链路；越界返回 ok=false。
- write_review_status：args `id` + `status`（applied 需 `noteRewritten=true`），经 `ProjectReviewStore.transition`；非法流转返回 ok=false。
- write_handoff：args `page` + `summary` + `expectedRevision` + `sourceRevision`，经 `ProjectHandoffStore.write`（currentNoteRevision 取该页 live revision）；冲突返回 ok=false。
- 成功返回 ok=true + touchedPage/updatedPage；`updatedPage` 仅写工具成功时设，`AgentRunner` 据此汇总实际修改范围。

## Acceptance (EARS)

- When 写工具 revision 匹配时，shall 落盘并报告 updatedPage。
- When revision 冲突时，shall 返回 ok=false 且不覆盖。
- When 参数非法时，shall 返回 ok=false 而不抛错中断循环。

## Verification

- JVM：四工具成功/冲突/非法参数用例 + `AgentRunner` TOOL(write)→DONE 汇总 updated。
- `cd android && ./gradlew :agent:testDebugUnitTest :app:testDebugUnitTest --offline` 通过。

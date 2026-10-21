# AI Review 与交接 UI

## Scope

Task 6 最后两片：整体 AI review 创建/列表、handoff 交接读写 UI（含 stale 处理）。笔记选区 review 的独立契约见 `note-selection-ai-review.md`。数据层 File 语义已有（`ReviewStore`/`HandoffStore`），本片补 SAF 薄适配 + UI。

## Review Interaction

- 工具栏 `Review` 按钮打开本页 review 列表弹窗：列出 document/note 两侧本页记录的 quote、instruction 与状态，可新建整体要求（instruction 输入 + side 选择，kind=overall）。
- 用户可 dismiss 或删除 review。`applied` 只能由 Agent 在目标页笔记成功发生实际修改后流转，不能由 UI 手动确认；同轮修改/校验详见 `note-selection-ai-review.md`。
- 整体 review 的 quote/context 为空；selection review 必须保留选中文字、上下文、revision 和 anchor，详见 `note-selection-ai-review.md`。

## Review Data Contract

- 新增 `ProjectReviewStore(resolver, sourceDirectoryUri, reviewsUri)`：`list(page?)`、`create(input)`、`transition(id, to)`、`delete(id)`，语义与 File 版一致，SAF 读写 `src/ai-reviews.json`。
- 缺文件读空列表；写时建文件；transition 非法流转抛 IllegalStateException。

## Handoff Interaction

- 笔记头 `History` 旁加 `Handoff` 按钮：弹窗显示本页交接（summary + stale 标记），可新建/覆盖（summary 输入≤4000，二次确认），stale 时黄色警告并要求先 reload。
- stale 判定：`HandoffStore.view` 对当前 noteRevision（编辑器草稿 revision）与 sourceRevision（`PdfDocumentSession.sourceRevision` 或空）。

## Handoff Data Contract

- 新增 `ProjectHandoffStore(resolver, sourceDirectoryUri, handoffsUri)`：`view(page, noteRev, srcRev)`、`write(page, summary, expectedRevision, sourceRevision, currentNoteRevision)`，语义与 File 版一致，SAF 读写 `src/ai-handoffs.json`。
- `expectedRevision` 冲突抛 IllegalStateException，不覆盖。

## Acceptance (EARS)

- When 新建 review 时，shall 落盘 open 记录并列出。
- When 用户 dismiss 或删除 review 时，shall 不修改笔记正文。
- When Agent 将 review 流转为 applied 时，shall 要求同轮 Agent 已成功修改该 review 页的笔记。
- When 读 handoff 且 revision 漂移时，shall 标 stale 并要求 reload 后再写。
- When 写 handoff 用过期 revision 时，shall 拒绝覆盖。

## Verification

- JVM：SAF store 的 File 等价路径测试 + 仪器测试覆盖 review 详情写入/读取与状态授权。
- `cd android && ./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --offline` 通过；真机待 task 12。

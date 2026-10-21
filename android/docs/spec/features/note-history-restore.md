# 按页历史与恢复 UI

## Scope

把 `NoteHistory` 的快照/列表/恢复能力接到阅读器笔记面板：保存成功后自动快照当前页，笔记头新增历史入口，弹窗内可浏览版本、预览内容、显式恢复。只针对 SAF 项目的按页 Markdown 笔记，不碰桌面 `notes.json` 内部形态。

## Interaction

- 笔记头在 Reload/Save 旁新增 `History` 按钮；仅当项目可写且历史目录可列时可用，打开按页版本弹窗。
- 弹窗列出该页版本（时间倒序，显示时间与大小），选中即在弹窗内预览该版本 Markdown；空历史显示空态文案。
- 预览下方有 `Restore` 按钮，点击后二次确认（当前草稿会被覆盖，覆盖前先对当前内容快照一次），确认后用 `NoteHistory.restore` 校验 `expectedRevision` 再回填编辑器。
- 恢复成功关闭弹窗并提示已恢复；`expectedRevision` 冲突时不覆盖，提示重新加载后重试；只读项目不显示 History 按钮。

## Data Contract

- 历史目录为项目 `src/history/`（经 SAF 换算到 app 可写缓存镜像则先落盘再回写，首版只做 File 版：历史目录取项目根 `File`，SAF 只读项目禁用历史）。
- 版本文件名沿用 `NoteHistory.fileName`（`<13位毫秒>-<uuid>-page<页>.md`）；列表用 `NoteHistory.entries`；恢复用 `NoteHistory.restore(historyDir, version, page, expectedRevision, current)`。
- 每次 `saveCurrentNote` 成功后，对保存后的页内容 `NoteHistory.snapshot` 一次；去重规则与桌面一致：与最新快照内容相同则不重复快照。
- `expectedRevision` 取保存成功后该页的 `PageNotes.revision`；恢复时 current 取编辑器当前草稿。

## Acceptance (EARS)

- When 保存当前页成功时，阅读器 shall 对该页内容做一次历史快照（与最新快照相同则跳过）。
- When 打开历史弹窗时，阅读器 shall 列出该页全部版本并可预览选中版本内容。
- When 确认恢复某版本时，阅读器 shall 先快照当前草稿再用 `expectedRevision` 校验恢复，冲突时不覆盖。
- When 项目只读时，阅读器 shall 不显示历史入口。

## Verification

- JVM：`NoteHistory` 快照去重、过期 revision 拒绝恢复两个新用例。
- Compose 仪器测试（编译即可，真机待 task 12）：历史按钮打开弹窗、选中版本预览、确认恢复回填。
- `cd android && ./gradlew testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug --offline` 通过。

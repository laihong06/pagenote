# 共享契约回归：assets 与 expectedRevision

## Scope

补齐 Task 1 最后一块共享回归：桌面 `exportAssets/importAssets` 的资源往返语义，以及 `expectedRevision` 冲突拒绝语义，在 `android/shared-tests` 用夹具和断言 pin 住。只做契约回归，不接 UI，不改 SAF 写路径。

## Assets Contract

- 导出方向：内部文本中的 `/attachments/<name>` 逐个去重后拷贝到项目 `src/assets/<name>`，缺源文件跳过不报错，文本统一改写为 `assets/<name>`。
- 导入方向：项目文本中的 `assets/<name>` 逐个去重后拷回内部附件区，缺文件跳过不报错，文本统一改写为 `/attachments/<name>`。
- 文件名约束：`[a-f0-9-]+\.[a-z0-9]+`，大小写不敏感；路径必须经 `ProjectValidation.resolveLinkSegments` 落在 `src/` 内，越界拒绝。
- 往返不变性：`import(export(x))` 与原文附件引用集合一致；缺文件场景只影响拷贝，不改变改写规则。

## Revision Contract

- revision 为整页笔记 UTF-8 的 SHA-256 hex，共 64 位小写；空页即空字符串的哈希。
- `saveNote` 必须带 `expectedRevision`；与当前页 revision 不一致时拒绝覆盖并抛 `IllegalStateException`，旧内容保持不动。
- `expectedRevision == null` 表示调用方已自行承担并发风险，仅用于明确允许覆盖的调用点；共享回归必须覆盖“带旧 revision 写冲突页”用例。
- `restore` 同理：`expectedRevision` 与 current 不一致时拒绝恢复。

## Fixtures

- `asset-note.md`：含两个 `/attachments/<uuid>.png` 引用，其中一个故意无源文件，验证跳过语义。
- `asset-project/`：`src/assets/` 下放一个真实附件文件，用于导入方向。
- `revision-conflict.md`：两页笔记，夹具记录第 1 页旧 revision；测试先正常写一次，再用旧 revision 写，断言第二次抛冲突且内容仍是第一次的结果。

## Acceptance (EARS)

- When 导出含缺失附件的笔记时，共享回归 shall 跳过缺失拷贝但仍改写全部引用。
- When 导入含缺失资源的项目文本时，共享回归 shall 跳过缺失拷贝但仍改写全部引用。
- When 用过期 `expectedRevision` 调用 `saveNote` 时，共享回归 shall 抛 `IllegalStateException` 且不改变已存页内容。
- When 往返 `export` 再 `import` 时，共享回归 shall 恢复原附件引用集合。

## Verification

- `ContractRegressionTest` 新增 `assets_export_skips_missing_but_rewrites`、`assets_import_skips_missing_but_rewrites`、`save_note_rejects_stale_expected_revision` 三个用例。
- `cd android && ./gradlew :shared-tests:testDebugUnitTest --offline` 通过。

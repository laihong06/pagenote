# 批注与笔迹 SAF 写入恢复

## Scope

保护现有 `src/annotations.json` 在 SAF Provider 覆盖写入中途失败时的已有内容。覆盖通用 PDF 批注 `writePage`/`deletePage` 与手写笔迹 `writePage`；不改变 JSON 格式、页合并语义或 revision schema。

## Behavior

- 写已有文件前读取并保留原始 UTF-8 文本，完成页合并后使用共享 `RecoverableDocumentWrite`：先创建同目录 JSON 恢复副本，再重读并比较原文，变化时中止写入。
- 更新成功后清理恢复副本；Provider 清理失败时记录恢复副本名称，UI 提示保存成功但恢复副本仍保留。
- 覆盖写入失败时尝试将原文写回。回滚成功则报告写入失败且原文已恢复；回滚失败则保留恢复副本并在错误中报告其名称。不得清空笔迹/批注内存草稿或伪报成功。
- Provider 明确不支持创建同目录副本时沿用共享写入器的 best-effort 行为；若能力查询或创建副本本身报错，则在目标文件写入前失败。
- 首次创建 `annotations.json` 失败时清理新建的半成品文件；若 Provider 拒绝清理，错误必须指出文件可能仍残留。
- 非目标页、另一侧及未知 JSON 字段在任一成功写入或回滚后均保持原值。

## Acceptance (EARS)

- When Provider 在覆盖后截断并报错且回滚成功时，the Android app shall 恢复原 `annotations.json` 并保留本次内存编辑供用户重试。
- When Provider 支持创建同目录恢复副本且覆盖与回滚都失败时，the Android app shall 保留并报告包含完整原 JSON 的恢复副本名称；Provider 明确不支持创建时按 best-effort 回滚，并明确报告没有恢复副本可用。
- When 批注/笔迹页写成功但恢复副本无法清理时，the Android app shall 报告已保存及残留副本名称。
- When Provider 在副本准备期间发现目标文件被外部修改时，the Android app shall 不覆盖新内容，并报告 revision 冲突。

## Verification

- 聚焦 SAF 仪器测试：对通用批注和笔迹至少各验证一次“部分写入失败后回滚恢复”；另验证回滚失败时副本保留并报告名称，以及成功写入/清理副本的正常路径。
- 运行相关 `RecoverableDocumentWriteTest`、新增 SAF 仪器测试，并构建 Debug APK；仅使用 Android 14/API 34 平板 AVD，不跑其他设备矩阵。

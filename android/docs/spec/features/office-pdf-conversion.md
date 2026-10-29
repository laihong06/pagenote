# Android 本地 Office→PDF 转换与命令接线（task 36 本地引擎切片）

**Status:** 已实施（分支 `codex/port-canvas-notes-features`，移植差距清单 #15 + task 36）。
本切片交付 Office→PDF 转换机制与 `office_edit`/`save_return` 实命令接线；
LibreOfficeKit 引擎本体仍未集成，task 36 保持 open。

## 范围

- Office→PDF 转换并入 task 36：引擎边界、转换编排、可丢弃预览缓存、Save & Return 写回事务全部为可执行代码，宿主注册一个引擎实现即可启用。
- `office_edit` / `save_return` 从 `pendingCommand()` 占位改为实命令（`pendingCommand()` 已删除，零占位）；桌面宿主运行时（COM/AppleScript）不移植。
- 扩展名门控保持关闭：`LocalOfficePdfEngineRegistry.engine == null` 时任何 Office 格式都不得转换、不得放开项目源入口；`ProjectDocumentOpener` 对 `.doc/.docx/.ppt/.pptx` 的拒绝行为不变（沿用 `office-preview-slice.md` 的门控要求）。

## 契约

- 引擎边界 `OfficePdfEngine`（core-project）：`engineId`/`engineRevision`/`exportSettingsVersion`/`supportedFormats` + `convertToPdf(InputStream)`。`supportedFormats` 只能列出引擎已证明可离线渲染的格式。
- 转换结果 `OfficePdfConversionResult`：`Ready(pdf, cacheKey)` / `UnsupportedFormat` / `EngineUnavailable` / `Failed(reason)`。引擎为 null 一律 `EngineUnavailable`，不伪造预览；取消异常原样上抛。
- 预览缓存键 `OfficePreviewCacheKey` = 源 SHA-256 + 源格式 + 引擎 id/修订 + 导出设置版本；任一分量变化即失效重建（`OfficePreviewCachePolicy.isUsable`）。缓存文件名由全键分量 SHA-256 派生。
- `OfficePdfConversionService`（app）：流式哈希源 revision → 命中缓存或转换 → 原子写预览 + JSON 键侧车 → 清理过期预览。失败不写文件并丢弃失效旧预览；源字节只读。
- Save & Return：`OfficeWriteBack.plan` 复用 `OfficeSavePolicy` 判定，READY 时产出 `Write(源格式字节, 恢复副本名)`；执行顺序为 ①写恢复副本（原始源字节）②revision 复核 ③写回源文件，失败尝试还原并明确报告，绝不提前声称成功。恢复副本名 `<源名>.recovery.<扩展名>`。
- 菜单接线（MainActivity + `ReaderOfficeController`）：
  - `office_edit`：仅当当前项目源为 Office 格式且引擎已注册且非 busy 时可用；onSelect 运行本地转换并建立 `OfficeEditSession`（工作副本落 app 自有目录）。不可用时禁用并显示本地原因（源非 Office / 引擎未接入）。
  - `save_return`：仅当存在当前项目的脏编辑会话且写回策略可执行时可用；onSelect 走恢复副本 + revision 校验写回，成功后清除脏标记。无会话/无修改/冲突/只读各有本地化原因。

## 验证

- JVM：`OfficePdfConversionTest`（门控/未证明格式/成功+缓存键/引擎失败/缓存键失效分量/格式识别）、`OfficeWriteBackTest`（NO_CHANGES/冲突/只读/缺恢复副本/READY/恢复副本命名）、`OfficePdfConversionServiceTest`（无引擎不落盘/复用/源变化与引擎变化失效/失败不留预览/未证明格式零 IO/`cachedPreview` 键匹配）全部通过。
- `:app:assembleDebug` 与 AndroidTest 源码编译通过；`DesktopCommandChecklistTest`、`DesktopParityPlaceholdersTest` 更新为断言零 `pendingCommand()` 调用。
- 未覆盖：SAF/Uri 读写薄层（`ReaderOfficeController` 的 provider 交互）、LibreOfficeKit 引擎本体（第一切片仍未完成）、API 34 AVD 端到端 Office 导入/编辑/保存闭环（待引擎集成后按 task 36 验收）。

## 关联

- 任务：`../tasks.md` task 36（open）、task 35（命令清单）。
- 前置切片定义：`office-engine-next-slice.md`、`office-preview-slice.md`、`office-save-policy.md`、`../../office-editor-plan.md`。
- 命令清单：`desktop-command-checklist.md`。

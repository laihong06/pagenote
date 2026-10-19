# 项目创建（SAF）

## Scope

补 Task 3 最后一块：安卓端经 SAF 目录选择器创建新 pagenote 项目（`project.pagenote` + `src/left.md` + `src/right.md` + `src/assets/`），创建后直接打开。与桌面 `POST /api/projects` 同 manifest 语义；assets 导入导出与 SAF 原子写已有实现，本片只做创建与打开。可选 PDF 原文导入属于后续独立切片，见 `project-create-optional-pdf.md`。

## Interaction

- 阅读器运行时无项目状态（`project == null`）显示 `Open project` 旁新增 `New project` 按钮；点击弹创建弹窗（名称输入 + 目录选择器）。
- 目录选择经 `ActivityResultContracts.OpenDocumentTree` 拿持久权限；名称校验与桌面一致（非空，不含 `<>:"/\|?*`）。
- SAF 选择器返回（选择目录或取消）后，创建弹窗保持打开，已输入的名称及已有目录选择不丢失。
- 创建中禁用按钮并显示进度；重名/无权限/创建失败显示可恢复错误，不闪退；成功后直接打开新项目（复用现有 open 流程）。

## Data Contract

- 经 `DocumentsContract.createDocument` 在所选树下建 `<name>/` → `src/` → `left.md`（`# <name>\n`）、`right.md`（空）、`assets/` 目录、`project.pagenote`（manifest JSON，字段与 `ProjectStore.create` 一致：format/version/id/name/created/left/right）。
- manifest 用不映射到文件扩展名的项目 MIME，确保 SAF Provider 保留精确的 `project.pagenote` 文件名。
- SAF 授权只属于用户选择器返回的父 tree URI。创建结果必须同时保留该父 tree URI 作为访问根，并以新项目目录的 document ID 作为项目根；不得把 `createDocument` 返回的 document URI 当成可再次持久授权的 tree URI，也不得为合成的子 tree URI申请持久权限。
- 打开器始终用父 tree URI 构造受授权范围内的子 document URI，同时按传入的项目根 document ID 查找 `project.pagenote`、原文、笔记与资源目录。普通“打开项目”仍以 tree URI 自身的 tree document ID 为项目根。
- id 为随机 UUID 的 SHA-256 hex；created 为 ISO-8601；left/right 固定 `src/left.md`/`src/right.md`。
- 同名目录存在时报错（桌面 `fs.mkdir` EEXIST 语义），不覆盖；任一步失败后尝试清理已建的空目录残留，失败则报告残留名。

## Acceptance (EARS)

- When 输入合法名称并选定目录时，阅读器 shall 建出完整项目结构并直接打开。
- When 新项目创建成功时，shall 沿用 SAF 选择器授予的父 tree 权限，并以新目录 document ID 打开，不对创建返回的 document URI 请求持久权限。
- When SAF 目录选择器返回时，shall 保持创建弹窗，并保留当前名称与已选目录状态。
- When 名称非法或目录同名已存在时，shall 拒绝并提示，不创建半截项目。
- When 创建中途失败时，shall 报告错误并尽力清理残留。

## Verification

- JVM/仪器：名称校验、manifest 字段、SAF 创建顺序、目录选择期间的弹窗关闭策略，以及创建目标保留父 tree URI/项目根 document ID。设备真机验证 Provider 保留 `project.pagenote` 文件名，并能从创建目标直接打开。
- 2026-10-17 Xiaomi 23127PN0CC（4 KB）：在独立的 `CodexAndroidDebug` 目录创建并直接打开 `CodexSafEndToEnd20261017`，文件清单与名称正确，选择器返回后弹窗和输入状态保留。`ProjectOpenTargetTest` 两项在设备通过；`ProjectCreatorSafTest` 仪器包安装被 MIUI 拒绝（`INSTALL_FAILED_USER_RESTRICTED`），其 MIME 行为由同一 Provider 的端到端创建验证覆盖。
- 2026-10-19 `Pixel_Tablet_API34` AVD：`ProjectCreatorSafTest` 安装并执行通过（1/1），验证 manifest MIME 不会映射出文件扩展名。该结果验证 Android API 行为，不消除 Xiaomi MIUI 对仪器包安装的设备限制，也不替代目标设备验收。
- `cd android && ./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --offline` 通过；其余设备矩阵待 task 12。

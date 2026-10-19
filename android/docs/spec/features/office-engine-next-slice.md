# Office 引擎下一可执行切片（task 36 收敛）

**Status:** 下一切片定义；task 36 仍为未完成（open），本文不声称 task 36 已完成。

## 目标

把 `../tasks.md` task 36 收敛为唯一先行可执行切片：**引擎可复现构建验证**，即 `../../office-editor-plan.md` “Implementation Slices” 第 1 项（Engine build and embedding proof）。先验证上游 LibreOfficeKit Android 原生引擎是否可在 pagenote 进程内离线运行，再谈扩展名放开、预览、编辑与 Save & Return。

## 输入（只读审计，不改）

- `features/office-save-policy.md`：纯 project-core 保存就绪策略已落（`OfficeSavePolicyTest` 6/6），SAF 与 editor 集成仍 open。
- `../../office-editor-plan.md`：四切片划分与 EARS 验收；本切片只做第 1 项。
- `../tasks.md` task 36：离线、进程内、不调外部 Office app、不调远程服务；先验证上游引擎，不先放开无法渲染的扩展名。
- `features/desktop-parity-gap-audit.md`：`office_edit` / `save_return` 为唯一命令缺口，占位保持到 task 36 落地。
- `features/desktop-ui-and-office-parity.md` §4 / §7：引擎边界、候选与官方构建引用（Collabora Android build guide、LibreOfficeKit API）。

## 非目标（本切片明确不做）

- 不放开 `.doc/.docx/.ppt/.pptx` 项目源入口；当前 opener 拒绝 Office 扩展名的行为保持不变。
- 不做项目源与 PDF 预览切片、编辑与 Save & Return 切片、可靠文字选区切片。
- 不接 SAF 写事务、不接 editor UI、不改 `office_edit` / `save_return` 的 `pendingCommand()` 占位状态。
- 不写实现代码；本文件只是可执行定义。
- 不声称 task 36 已完成，不关闭缺口审计中的任何 gap。

## 可执行步骤

1. **定 upstream 修订**：pin LibreOffice core 与 Collabora Android（online monorepo）兼容修订，记录提交哈希与取数日期。
2. **建 Linux 可复现 builder**：按官方 Android 构建说明建立 Linux 原生构建环境（容器/工具链固定版本）；macOS 宿主只做编排，不替代 Linux 构建。记录 Dockerfile/镜像摘要、构建命令与环境变量。
3. **构建 arm64 产物**：从源码构建 arm64 Android core（`liblo-native-code.so`）与 Android editor 组件；记录 NDK 版本、ABI、构建 flags、产物哈希、包体增量。
4. **进程内加载证明**：用 pagenote 自有 Activity/View 在单台 API 34 平板 AVD 上离线完成 load → render → edit → release 全路径，不启动已安装的 Collabora app，不访问网络。记录启动行为、API 34 运行结果、所需上游补丁。
5. **许可证记录**：随构建记录上游 license notices（含 LibreOffice/Collabora 相关 MPL/LGPL/GPL 义务），说明分发影响；无源码/构建配方的不透明生成库不得签入 app。
6. **失败分支**：若候选无法嵌入，按 parity §4.2 继续评估本地开源引擎或可维护适配，不得回退为外部 app / 云转换服务，不得以候选研究当作交付。

## 扩展名门控

- 只有引擎可实际打开并渲染的格式才可进入后续“项目源与 PDF 预览”切片；无法渲染的扩展名不得提前放开。
- 本切片通过前，`.doc/.docx/.ppt/.pptx` 仍被 opener 拒绝；`office_edit` / `save_return` 保持禁用占位并指向 task 36。

## EARS 验收（本切片）

- When 构建机按记录的修订与容器重跑构建，the build shall 生成哈希一致的 arm64 引擎产物。
- When pagenote 自有 Activity/View 在 API 34 平板 AVD 离线加载引擎，the app shall 完成渲染、接受编辑并释放引擎，全程无网络访问。
- When 引擎验证未通过，the app shall 保持 Office 扩展名关闭与占位禁用，不声称任何编辑能力可用。
- When 本切片完成，the repo shall 含有可复现的构建配方、产物哈希、许可证记录与 API 34 验证记录，且 task 36 仍标记未完成。

## 验证记录要求

- 上游修订、Linux builder 定义、NDK/ABI/flags、产物哈希、包体增量、启动行为、API 34 AVD 结果、上游补丁、license notices。
- 仅要求单台 API 34 平板 AVD 闭环；不建跨 API/跨设备矩阵（沿用 task 36 约束）。
- 后续切片（预览、Save & Return、选区）各有独立验收，不在本文件提前认领。

## 关联

- 实施总计划：`../../office-editor-plan.md`
- 任务：`../tasks.md` task 36（open）、task 37（收尾审计归属）
- 保存策略：`features/office-save-policy.md`
- 缺口审计：`features/desktop-parity-gap-audit.md`
- 总体对齐：`features/desktop-ui-and-office-parity.md` §4 / §7

# 长 Markdown ANR 仪器操作（task 30 冷启动验收步骤）

本文保留 task 30 冷启动验收的可重复步骤、合成 fixture 保留参数和已完成的 API 34 平板 AVD 验收记录。

> task 30 已依据下方 2026-10-20 API 34 AVD 冷启动、真实 IME 跳页、编辑保存与再次重开证据关闭。小米 Pad 5 Pro 真机未在本次验收，仍是设备环境差异。

## 目标

- 在 `Pixel_Tablet_API34`（Android 14 平板 AVD，唯一验收环境）上执行：
  应用进程冷启动 → 恢复/跳转第 31 页 → 编辑 → 保存，确认无系统 ANR。
- 对象：隔离的 69 页验收副本（第 31 页约 57 KB Markdown，含约 39.6 KB 连续 token）。
- 通过标准：无系统 ANR 对话框，正文完整可编辑，保存回读后只有当前页的测试标记变化。

## 非目标

- 不把 AVD 通过外推为小米 Pad 5 Pro 真机已验证。
- 不把合成 fixture 结果外推为原用户 PDF 的专项复测。
- 不使用原用户 PDF，不改动桌面原件，不新增 OEM / API / 设备矩阵，不要求实体设备（无真机矩阵）。
- fixture 保留参数默认关闭，常规测试仍清理 SAF grant 与最近项目记录。

## 已完成验收记录

- 日期：2026-10-20；环境：`Pixel_Tablet_API34`，Android 14/API 34，平板分辨率 2560x1600；包：`io.pagenote.android.debug`。
- fixture：测试 Provider 生成的隔离 69 页合成项目，第 31 页 Markdown 57,337 字符，含 40,000 个连续 `A`；未读取或修改用户 PDF。
- 两次 `am force-stop` 后均以 `LaunchState:COLD` 启动，Activity 启动时间分别为 661 ms、750 ms。Recent 打开后页码从第 1 页开始，按 SPEC 用真实页码对话框手动跳转第 31 页。
- 页码输入时确认系统 IME 显示（`mInputShown=true`）；点 Go 后 IME 收起（`false`），应用进程存活，长页在 ANR 阈值内完成布局，无 ANR 对话框或新输入超时日志。
- 真实编辑器追加并保存 `COLDSTART_PROBE_20261020`。Provider 回读确认项目仍为 69 页、第 31 页标记位于末尾，且第 30/32 页分别仍为 `Page 30 note` / `Page 32 note`；再次冷启动并跳页后界面仍显示该标记和完整长正文。
- `NoteLongMarkdownEditorTest` 五项 API 34 instrumentation 回归通过，覆盖长 token 编辑/布局、光标映射、系统剪贴板复制/剪切/粘贴与分享、真实页跳转和 SAF 恢复保存。

## 前置条件

- AVD：`Pixel_Tablet_API34` 单一环境，API 34，Android 14 平板形态。
- 应用包：待验收的 Android debug 包（以实际安装包名为准，下文以 `<appId>` 占位，执行时替换）。
- 验收副本来源：隔离的 69 页项目副本，已通过 SAF 可访问位置准备好；桌面原件与原用户 PDF 只读，不触碰。
- 主机具备 `adb`，且同一时间只保留一个 `Pixel_Tablet_API34` 实例在线（`adb devices` 确认）。
- AVD 时间/动画：建议关闭窗口动画以减少计时噪声，但不得改变冷启动定义。

## 步骤 0：SAF 副本准备（只做一次，失败则停止）

1. 在主机侧确认隔离副本目录结构完整：69 页 PDF 本体、第 31 页 Markdown 存在且大小量级符合预期（约 57 KB）。
2. 将隔离副本推送到 AVD 可经 SAF 访问的位置，或经应用内“最近项目”可识别的 SAF tree（不得覆盖既有测试 Provider 数据）。
3. 在应用内打开一次该副本，确认第 31 页可显示；随后用系统返回/主页将应用置于后台。
4. 记录副本的 SAF tree URI / root document 标识与第 31 页 checkpoint（如有），供步骤 3 恢复判定使用。
5. 若副本打不开或第 31 页正文缺失：停止，不进入计时步骤，按“副本准备失败”记录。

## 步骤 1：进程 kill（制造冷进程起点）

1. 确认应用当前进程存在：
   `adb shell pidof <appId>`，记录 PID_KILL_BEFORE。
2. 执行进程 kill，模拟系统进程死亡（任选其一并记录所用方式）：
   `adb shell am force-stop <appId>`。
3. 验证进程已消失：
   `adb shell pidof <appId>` 应无输出；若仍有输出，等待 5 秒后重试一次，仍失败则停止并记录。
4. 不执行清除数据（clear data），不卸载应用；只杀进程，保留“最近项目”与 SAF 授权。

## 步骤 2：冷启动（计时 T_cold_start）

1. 记录主机时间 `T0`（冷启动发起时刻）。
2. 从 Launcher 点击应用图标冷启动，或执行：
   `adb shell am start -n <appId>/.MainActivity`（Activity 名以实际 manifest 为准，执行时替换并记录）。
3. 观察应用进入“最近项目”或上次会话入口；记录首个可交互画面出现时刻 `T1`。
4. 计时：`T_cold_start = T1 - T0`，仅作记录，不设独立通过门槛；若在此阶段出现系统 ANR 对话框，直接判为不通过并保留截图/logcat。
5. 记录冷启动后新进程 PID：
   `adb shell pidof <appId>`，记为 PID_COLD_AFTER，须与 PID_KILL_BEFORE 不同，否则不视为冷进程，需重做步骤 1。

## 步骤 3：恢复/跳转第 31 页（计时 T_restore，ANR 判定点）

1. 从“最近项目”选择隔离的 69 页验收副本进入阅读器（优先走真实“最近项目”恢复路径；若不支持，则经 SAF 打开同一副本并记录所用路径）。
2. 记录恢复发起时刻 `R0`（点击副本时刻）。
3. 等待阅读器恢复第 31 页 checkpoint；若自动恢复页不是第 31 页，则手动跳转到第 31 页，并记录“自动恢复页码”与“手动跳转”事实。手动跳转须实际显示并收起页码输入软键盘；仅 Compose `performTextInput` 不覆盖 IME/insets 路径。
4. 记录第 31 页首轮布局完成且可交互时刻 `R1`（编辑器可接受输入、光标可定位）。
5. 计时：`T_restore = R1 - R0`，期望低于 5 秒（系统输入分发 ANR 阈值参照）。
6. ANR 判定（任一成立即为不通过）：
   - 出现系统 ANR 对话框（“应用无响应”/“Close app”/“Wait”）。
   - `R1 - R0 >= 5 秒` 且期间输入无响应。
   - logcat 出现 `Input dispatching timed out` 且归属 `<appId>` 进程。
7. 若通过，继续步骤 4；若不通过，保留 ANR 截图、`adb shell dumpsys activity` 相关片段与 logcat，停止后按“冷启动恢复 ANR”记录。

## 步骤 4：编辑（计时 T_edit_layout，内容一致性检查）

1. 在第 31 页编辑器末尾（或与既有合成回归一致的锚点）追加一行测试标记，例如 `<!-- coldstart-probe -->`，不得改动其他行。
2. 记录编辑发起时刻 `E0` 与编辑后布局稳定可继续输入时刻 `E1`；`T_edit_layout = E1 - E0` 仅作记录，期望远低于 5 秒。
3. 目检：全文仍完整显示，长连续 token 行未被截断；撤销/重做可用；选区可定位。
4. 若编辑期间出现系统 ANR 对话框，直接判为不通过并保留证据。

## 步骤 5：保存与回读（隔离性判定）

1. 执行按页保存，记录保存发起时刻 `S0` 与保存完成提示/文件时间更新时刻 `S1`。
2. 保存后回读第 31 页文件，确认测试标记存在且其余字符逐字符一致（与步骤 4 前快照 diff）。
3. 检查第 30 页与第 32 页文件未变（hash 或 diff 确认）。
4. 重新 kill 并冷启动一次（重复步骤 1–3 的 kill/恢复），确认第 31 页测试标记在重开后仍存在且无 ANR；此为“保存持久性”确认。
5. 验收副本测试标记的清理方式需单独记录；不得将其写回桌面原件或原用户 PDF。

## 计时与 ANR 判定汇总

- 必记录四段计时：`T_cold_start`、`T_restore`、`T_edit_layout`、`T_save`（`S1 - S0`）。
- 硬判定只有两处：
  - 恢复/跳转段 `T_restore < 5 秒` 且全程无系统 ANR 对话框、无 `Input dispatching timed out` 归属本进程。
  - 编辑/保存段无系统 ANR 对话框，且回读满足“只有当前页测试标记变化”。
- 其他计时（冷启动、编辑布局、保存）只记录数值，不单独作为通过/不通过门槛，但须写入验收记录以便与 `large-markdown-page-layout.md` 证据节对比。
- 一次早期计时曾从挂载测试项目开始、混入项目/Activity 初始化噪声；本操作要求计时起点为“已发起恢复/跳转”而非“挂载项目”，避免复现该噪声。

## 通过记录模板（每次执行填写一份）

- 日期 / 执行人：
- AVD：`Pixel_Tablet_API34`（确认单实例）。
- 应用包名与版本 / 构建来源：
- 隔离副本标识（SAF tree URI 脱敏摘要、69 页确认、第 31 页大小）：
- kill 方式 / PID_KILL_BEFORE / PID_COLD_AFTER：
- 冷启动入口（图标点击 / am start 命令原文）：
- 恢复路径（最近项目自动恢复 / 手动跳转第 31 页）：
- 四段计时：`T_cold_start` / `T_restore` / `T_edit_layout` / `T_save`：
- ANR 判定：有/无对话框，有/无 `Input dispatching timed out`：
- 第 30/32 页未变确认：是/否（附 hash/diff 摘要）：
- 小米差异记录：仍为未复现的环境差异，是/否：
- 结论：通过 / 不通过（不写“task 30 已关闭”）。

## 关联（只读）

- 任务：`android/docs/spec/tasks.md` task 30（已关闭；本文件保留复现步骤和验收记录）。
- 契约：`android/docs/spec/features/large-markdown-page-layout.md`（行为、EARS、证据与假设）。
- 缺口说明：`android/docs/spec/features/long-markdown-coldstart-gap.md`（三项合成回归通过但均未覆盖冷进程）。
- 回归（只读引用，未修改）：`android/app/src/androidTest/java/io/pagenote/android/NoteLongMarkdownEditorTest.kt`。

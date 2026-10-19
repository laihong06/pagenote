# Android 自适应菜单导航实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Android 顶部平铺的桌面类命令迁入按窗口宽度自适应的抽屉/侧栏，保留现有阅读和编辑状态。

**Architecture:** 新增独立的菜单模型与 Compose 导航组件，ReaderHome 负责把已有业务回调映射为分组命令。Compact 窗口使用 Modal drawer，宽窗口使用可收起的侧栏；不把项目/页面状态移入导航组件。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、现有 Android instrumentation / JVM 测试。

**Spec:** `android/docs/spec/features/adaptive-menu-navigation.md`

## Global Constraints

- Android `minSdk` 保持 34。
- 菜单断点以窗口可用宽度 600dp 判定，窄于 600dp 用抽屉，否则用常驻侧栏。
- 阅读双栏仍以工作区可用宽度 840dp 判定。
- 用户选定的桌面菜单分组及顺序不可隐藏或改名；未接线命令必须说明禁用原因。
- 每次 push 必须包含根 `CHANGELOG.md` 更新；分支仍是 `codex/android-port`，目标 beta。

## Review Focus

- 599/600dp 窗口断点：策略单测覆盖两侧。
- 旋转或窗口 resize：仪器测试验证菜单状态重建不丢页面/草稿。
- 禁用命令：仪器测试验证可访问的禁用原因。
- 小屏长分组列表：仪器测试滚动到末尾及返回。
- 系统栏 inset：平板 AVD 截图/交互验证菜单与阅读区不互相覆盖。

---

### Task 1: 菜单策略与分组模型

**Files:**
- Create: `android/app/src/main/java/io/pagenote/android/ReaderMenuModel.kt`
- Test: `android/app/src/test/java/io/pagenote/android/ReaderMenuModelTest.kt`

**Interfaces:**
- Produces: `ReaderMenuLayout.forWidth(widthDp: Int): ReaderMenuLayout`，其中 `COMPACT_DRAWER` 对应 `< 600dp`，`PERMANENT_SIDEBAR` 对应 `>= 600dp`。

- [x] 写断点测试并运行，确认 599dp 和 600dp 用例因接口缺失失败。
- [x] 实现最小纯 Kotlin 策略与桌面分组 key/顺序。
- [x] 运行 `./gradlew :app:testDebugUnitTest --tests io.pagenote.android.ReaderMenuModelTest`。
- [x] 提交 `feat(android): define adaptive menu policy`。

### Task 2: Compose 抽屉和可收起侧栏

**Files:**
- Create: `android/app/src/main/java/io/pagenote/android/ReaderAdaptiveMenu.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Test: `android/app/src/androidTest/java/io/pagenote/android/ReaderAdaptiveMenuTest.kt`

**Interfaces:**
- Consumes: `ReaderMenuLayout`, `ReaderMenuGroup`, and immutable `ReaderMenuCommand` values from Task 1.
- Produces: `ReaderAdaptiveMenu(layout, groups, expanded, onExpandedChange, content)`; menu component does not own reader state.

- [x] 写 Compose 仪器测试，验证 compact 抽屉入口/关闭和 expanded 侧栏分组入口；先运行确认缺少组件而失败。
- [x] 构建菜单容器、无障碍标签、禁用原因及可折叠侧栏。
- [x] Pixel Tablet API 35 AVD 检查 tablet 布局；完整仪器测试覆盖抽屉/侧栏交互，手机尺寸整机视觉检查留待后续。
- [x] 与 Task 3 合并提交：UI 容器和 ReaderHome 命令映射共享同一宿主改动，合并后作为一个可验证功能切片提交。

### Task 3: 接线现有 ReaderHome 命令

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`
- Test: `android/app/src/androidTest/java/io/pagenote/android/MainActivityLaunchTest.kt`

- [x] 写阅读器测试，验证现有命令和禁用原因在桌面分组内可访问。
- [x] 把现有顶部命令迁入菜单，顶栏保留文档身份、窄屏菜单按钮和当前页状态。
- [x] 接上窄屏抽屉与宽屏侧栏，命令复用现有 handler；未移植命令显示禁用原因。
- [x] 运行 JVM、完整 Android 仪器、四 ABI 构建及 APK 16 KB 检查。
- [x] 更新任务勾选与 CHANGELOG，提交 `feat(android): move reader commands into menu groups`。

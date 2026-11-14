# Android 界面改造运行截图（本地实验）

2026-11-09，Android Emulator `root34`，Android 14 / API 34、1080×2400、420 dpi、arm64-v8a、SwiftShader。调试包 `io.pagenote.android.debug` 运行在基线 `b9ba3e2b8d07e08d675ffacc54436d7d03133661` 的未提交工作树，没有独立 runtime commit。`NoteUiEvidenceTest` 使用合成 PDF/项目并在应用实际窗口中采集。

- `bookshelf-local.png`：已绑定的书架；`bookshelf-create-local.png`：右下角新建菜单。
- `reader-local.png`：阅读器及展开的笔栏；`pen-style-local.png`：长按笔打开样式；`pen-collapsed-local.png`：收起笔栏。
- `pen-box-local.png`：笔型和颜色快捷面板；`canvas-focus-local.png`：隐藏笔记区域后的画布专注模式。平板对应文件以 `tablet-` 开头。这两个状态为本次新增，没有对应的基线截图。
- `settings-local.png`：设置及分类导航。
- `bookshelf-phone-before-local.png`：改造前已有工作树的手机书架截图；与本次截图的项目数据并非完全相同，不能作为严格的同状态像素比较。
- `bookshelf-phone-unbound-local.png`：前一轮书架未绑定状态，采集时尚未加入宽屏侧栏与本轮浮动入口。

截图证明所示模拟器运行状态，不代表真机、横屏、深色主题或维护者视觉审核。参考应用手册属于视觉参考，并非 PageNote 运行截图。

`tablet-*-local.png` 来自同一 `root34` AVD 的临时屏幕覆盖 2560×1600、240 dpi（宽度大于 840 dp），完成后已恢复 AVD 原尺寸和密度；不是独立真机或不同 Android 版本。`tablet-unbound-local.png` 为未绑定书架，`tablet-bookshelf-local.png` 为测试项目书架，`tablet-bookshelf-create-local.png` 为新建菜单，`tablet-reader-local.png`、`tablet-pen-style-local.png`、`tablet-pen-collapsed-local.png` 展示阅读器和笔盒状态，`tablet-settings-local.png` 展示设置。执行 `BookshelfTabletLayoutTest`、`NoteUiEvidenceTest` 和 `ReaderSettingsMarkdownThemeTest` 的平板组合运行成功。笔栏越界在上述新版截图前已修复；旧版 `tablet-bookshelf-local.png` 已替换，不用于声称改造前截图。

2026-11-09 后续更新：`bookshelf-navigation-local.png` 为手机导航抽屉；平板侧栏支持收起和展开，`ReaderPenRailInteractionTest` 使用像素采样验证笔身不越出工具栏。手机组合回归执行 27 项，0 失败；平板尺寸覆盖的书架、设置与截图用例单独运行通过。截图均为未提交工作树，并非发布包或视觉审核结论。

## 独立基线与前后对照

`phone-before-{bookshelf,reader,pen-style,settings}.png` 和 `tablet-before-{bookshelf,reader,pen-style,settings}.png` 来自独立检出 `b9ba3e2b8d07e08d675ffacc54436d7d03133661` 的本地 debug 构建，唯一产品构建配置变更是将 debug applicationId 后缀改为 `.baseline`，以免覆盖开发包；另加入本目录的 `BaselineUiEvidenceTest.kt.txt` 截图测试。源码主界面未改动。两组分别采用上述手机和平板模拟器尺寸，与改造后截图使用相同的合成 PDF、笔记正文、项目名称和系统；按屏幕状态对照，书架卡片排序与菜单触发方式因改造而变化。每套测试均在真实运行窗口采集，测试操作使用 Compose 语义和模拟输入；不作为参考应用截图或真机证据。

同一项目对应关系：`phone-before-bookshelf.png` / `bookshelf-local.png`，`phone-before-reader.png` / `reader-local.png`，`phone-before-pen-style.png` / `pen-style-local.png`，`phone-before-settings.png` / `settings-local.png`；平板相应文件以 `tablet-before-` / `tablet-` 开头。截图中的时间、系统栏、动画和滚动位置会有差异，不作为像素级比对数据。

2026-11-10：`tablet-settings-local.png` 更新为设置右侧面板，仍使用 2560×1600 / 240 dpi 覆盖尺寸；`tablet-before-settings.png` 为相同合成项目在基线版本的居中弹窗。此对照展示布局变化，不代表参考应用的像素级一致。

2026-11-10 最终更新：手机与平板的改造后截图均使用上述尺寸重新采集，包含新增笔盒和画布专注模式，以及手机导航抽屉。运行版本为同一基线上的未提交工作树；手机修复后的相关组合 16/16、平板组合 23/23，截图不代表真机或视觉审核。

`launcher-icon-local.png` 是 2026-11-10 在同一 Android 14 手机尺寸模拟器安装本地 debug APK 后采集的应用列表，展示新自适应启动图标；构建仍来自上述未提交工作树，未采集图标替换前的同状态截图，也不代表真机或桌面端验证。

## 阅读器菜单调整（2026-11-10）

`phone-reader-menu-before.png` / `phone-reader-menu-after.png` 和 `tablet-reader-menu-before.png` / `tablet-reader-menu-after.png` 分别是同一合成项目在菜单改造前后的 PageNote 实际运行截图；手机与平板尺寸、系统和基线提交同上，均为同一基线上的不同未提交工作树状态，没有独立 runtime commit。`phone-reader-menu-file-after.png`、`phone-reader-menu-settings-after.png` 与对应的 `tablet-` 文件显示新页签。自动化通过界面操作展开和切换；截图不等同真机或 TalkBack 验证。

参考 App `com.newskyer.draw` 7.0.3 的真实运行菜单另在相同平板尺寸模拟器观察并留在本地 `/tmp/ref-menu.png` 等临时文件，用于研究，不作为 PageNote 运行截图或像素一致的证据。

# Android 原生 Markdown S（实验性）

## 实现阶段与范围

- 里程碑：M4 的 Android 专项原型与基础交互验收；不是 Markdown S 总体 M4 交付，也不代表已发布。
- 平台：Android；仅覆盖 `version: 1`、`kind: genetic-lab` 的声明式 `markdowns` 围栏。
- Android 以原生 View/Canvas 重现桌面遗传实验的八章结构、配色、纸张背景、章节导航、36 个个体、交叉、选择、变异、40 代图表和基因状态。
- 组件改动更新当前页 Markdown 草稿；当前整合版沿用阅读器自动／手动保存队列写入原文件。本轮没有重跑组件完整读写验收，下面的早期手动保存结果保留其原始范围。
- 2026-11-14：源码已进入整合提交 `a315827`，仍为未公开发行的 debug 实验实现。本轮只复验阅读器整合路径，没有重新执行渲染专项用例；下方 11 月 7–8 日截图的“未提交工作区”是当时真实来源，不将它们追认为本次运行截图。

## 八章运行截图（2026-11-07 Canvas 原型）

以下截图由 AVD `root34` 上的真实 Android instrumentation runtime 生成。每张截屏都保留当前完整滚动页面，目标章节通过章节导航滚动到视口；图片不是设计稿。

1. [编码与变异：12 位可点击染色体](images/markdown-s/native-eight-chapters/01-chromosome.png)
2. [种群与个体：36 个带适应度的个体](images/markdown-s/native-eight-chapters/02-population.png)
3. [适应度地形：基因解码曲线与位置滑块](images/markdown-s/native-eight-chapters/03-fitness-landscape.png)
4. [选择轮盘：按适应度分配扇区并选择个体](images/markdown-s/native-eight-chapters/04-selection-wheel.png)
5. [单点交叉：两组子代和 1–11 切点](images/markdown-s/native-eight-chapters/05-crossover.png)
6. [变异概率：发生至少一次变化的概率反馈](images/markdown-s/native-eight-chapters/06-mutation.png)
7. [演化记录：选择 0–40 代并显示最佳适应度](images/markdown-s/native-eight-chapters/07-generation-chart.png)
8. [染色体旋转示意：拖动、点选位点、暂停与继续](images/markdown-s/native-eight-chapters/08-chromosome-3d.png)

此外，[NotePanel 交互前](images/markdown-s/roundtrip/markdowns-evidence/note-panel-before.png)与[交互后](images/markdown-s/roundtrip/markdowns-evidence/note-panel-after-interaction.png)截图覆盖 Android 笔记面板接线和草稿状态。

## 用户可操作范围

- 点击或拖刷染色体位点、选择种群个体、拖动或用左右方向键操作轮盘，或调整位置、交叉切点、变异率、代数；状态会写入对应围栏。NotePanel 改状态后保留当前阅读滚动位置。
- 第 40 代最佳适应度与桌面样本一致，为 99.97；遗传算法使用相同的 36 个体、12 位基因、40 代及固定 seed。
- 原有 Markdown 仍通过 Markwon 显示。未知／无效声明块降级显示源码，陈旧或冲突状态拒绝写入。
- `mds-html`、`html`、`js` 等执行围栏仍显示源码，不执行 HTML/JavaScript，不创建 WebView 或 iframe。

## 与桌面端的差距

- 2026-11-08 本地实验原型已将第 8 章接入 SceneView 2.3.0 / Filament 原生 3D；初始化抛出异常时使用原有 Canvas 示意并提示。其余章节保留 Android 原生 View/Canvas。Android 使用同一 `genetic-lab` 围栏的基因、旋转角和暂停状态；这不代表支持执行任意 Three.js 代码。
- [Filament 模拟器运行截图](images/markdown-s/filament-chromosome-3d.png)展示拖动及暂停后的场景；此前 [Canvas 视图](images/markdown-s/native-eight-chapters/08-chromosome-3d.png) 为对照。两张均来自 AVD root34、Android 14、1080×2400、420 dpi、arm64-v8a、SwiftShader；基线 `b9ba3e2b8d07e08d675ffacc54436d7d03133661`，均为本地未提交工作区，没有 runtime commit。新截图 SHA-256：`dec74d3aa4aadbf70e9d7d3ba1c672c474cfd8f15978405d1be33e9f5fc76037`。
- [NotePanel 真实运行截图](images/markdown-s/filament-note-panel-3d.png)在相同模拟器和本地代码下捕获拖动后、保存前的实际笔记预览，SHA-256：`34b2ada5c921a2981f59303cccbd44bd77dfd51e4ec5948a8a57190a1750d762`。
- [主阅读器裁剪前](images/markdown-s/filament-main-before-clip.png)与[裁剪后](images/markdown-s/filament-main-activity-3d.png)截图来自同一模拟器的 MainActivity 项目重新打开流程，本地基线相同、未提交；SHA-256 分别为 `0a0a1fbd56fde39b8b1fc59d91a60426fc2a87aee8ebd861f94a2aa6b156b42d` 和 `0260f59ddb5282b0fdc4fc63d5825a67bb56a8ac4df3d0ad4abe3a12b06fbe8c`。裁剪前笔记滚动绘制越过文档区域，修复后通过截图区域比较。
- [进程停止并重开后的场景](images/markdown-s/filament-after-process-restart.png)和[系统输入横向拖动后](images/markdown-s/filament-after-real-drag.png)截图来自同一模拟器上的调试包与测试项目；SHA-256 分别为 `6408989b6642eb358725bfadfbbaf6770ca07f8d2cdc6cc51a82b597ddad6f56` 和 `894ba57e8b8d4f81d8ce724a0673b788b81f30864a89ac9bd4cd60d02bf54aec`。后一张的保存按钮显示有待保存修改。
- Filament 场景与桌面 Three.js 的具体光照、材质和交互仍有差异；未做真实设备 GPU、完整关闭重启应用、深色主题、TalkBack 与维护者视觉审查，不能视为全平台或发布验收。
- 第一章支持逐位点按和横向拖刷；Android 原生 View 在密度和触控边界上的手感仍与桌面 SVG 不同。轮盘支持拖动与左右方向键，不包含桌面浏览器完整的焦点/键盘语义。
- Android 字体、屏幕窄宽、原生滑块和系统状态栏与桌面不同。八章内容和核心数据契约接近，不能宣称像素级或完整交互等同。
- 深色主题、横屏/平板、系统字号放大、TalkBack 全流程、旋转/进程恢复和真实设备 GPU 行为未验证。

## 验收状态

已执行：

- `MarkdownSStateTest`、`MarkdownSBlockParserTest`、`MarkdownSStateEditorTest`、`MarkdownSGeneticAlgorithmTest` 的 notes 单元测试通过。
- `MarkdownSNoteRendererTest` 的三项 Android instrumentation 在 AVD `root34`（Android 14 / API 34、1080×2400、420 dpi、arm64-v8a、SwiftShader）通过：八章交互及截图（含基因拖刷与轮盘左右键）；NotePanel 组件改动进入草稿并在章节内继续交互时保留滚动位置；项目笔记文件保存后由 `ProjectDocumentOpener` 重新打开并读取，第二页保持不变，重建预览后组件仍可交互。
- `:app:compileDebugKotlin`、`:app:compileDebugAndroidTestKotlin` 均通过。

仍未覆盖：

- 没有关闭 Android 项目界面或杀进程后重新打开应用；项目 Markdown 文件被重新读取已通过，但完整 App 关闭/重启流程尚未通过验收。
- 未覆盖普通旧笔记的完整公式、附件、Mermaid 回归，撤销/重做、历史恢复、跨项目切换、非法围栏 UI、源码编辑、主题和辅助功能全套验收。
- 因此官方验收用例 E01、F01–F06、E02–E07 不整体标记通过；本轮仅证明所列代码路径和代表样本。

## 运行证据来源

- 环境：Android Emulator AVD `root34`，Android 14 / API 34，1080×2400，420 dpi，arm64-v8a；SwiftShader。
- 构建：Android `debug` instrumentation，包 `io.pagenote.android.debug`，版本名 `1.0.0-beta.6-dev.1`。
- 源码基线：`android/beta` commit `b9ba3e2b8d07e08d675ffacc54436d7d03133661`；运行时为该基线的本地未提交工作区，没有对应 runtime commit。
- 单元测试：`ANDROID_HOME=/Users/broccoli/Library/Android/sdk ./gradlew :feature-notes:testDebugUnitTest`。
- 构建验证：`ANDROID_HOME=/Users/broccoli/Library/Android/sdk ./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`。
- 仪器测试：`ANDROID_HOME=/Users/broccoli/Library/Android/sdk ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.pagenote.android.MarkdownSNoteRendererTest`，结果 3/3 通过。
- 2026-11-08 更新：同一模拟器执行 `:feature-notes:testDebugUnitTest` 和 `MarkdownSNoteRendererTest,MarkdownSMainActivity3dTest` 两个 instrumentation 类，13/13 通过。覆盖强制初始化异常时的二维降级、场景复用及材质更新、不可见和后台时暂停旋转、屏幕不被强制常亮、3D 位点触摸及围栏更新、项目保存、Activity 重建、返回书架并重新打开、恢复场景角度、文档区域裁剪，以及通过系统输入在场景内横向旋转与纵向滚动。主界面的预览、保存和书架按钮在自动化测试中通过 Compose 语义操作触发。
- 独立进程重启验证：在测试模拟器保留 debug 包与测试项目，先保存包含角度 `-5.5048699378967285` 的笔记，执行 `am force-stop` 并确认目标进程退出，再启动 MainActivity、通过系统输入从书架打开同一项目。UI 源码与项目文件均显示该角度，预览可重新渲染；再用系统输入拖动并保存，项目文件角度变为 `-8.152860641479492`，前后正文及第二页仍在。此为模拟器的代表路径，真实设备 GPU、触控、主题、辅助功能和维护者视觉审查仍未验证。
- Android 仪器测试实际写入的文件由 `ProjectNoteStore` 保存，再通过 `ProjectDocumentOpener` 打开项目目录并读取；并未关闭/重启完整 Activity 或应用。

八张章节截图 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| `01-chromosome.png` | `625c6fadfdcddf93f05ad3c7d6504d155b67d020a0286984e15f587f39483bba` |
| `02-population.png` | `97d153024a7c9e076188d6b8e1d6e3088ab6c0200ec02b4319659916d26e84ba` |
| `03-fitness-landscape.png` | `f95dacd60bfb7590890504cc17938f903eb39e86ff025a4a454aa7189524747a` |
| `04-selection-wheel.png` | `3d801f65d0bd357f0c87f4cc08f33fa414a9ede4c86eeba2570ffb2b7f04fd28` |
| `05-crossover.png` | `c8ea505561b491f8262c16ec7fc8aa436929eed95bd636f9734669474dff58d1` |
| `06-mutation.png` | `af4726b31a4248fdff1150c96f5807f0f802e21cc38e944847a972c0af2df5ef` |
| `07-generation-chart.png` | `0e7a8d155dc9c8b335adcf51370a89e97ec543602f9ff3db6e9dc8dacbb44d83` |
| `08-chromosome-3d.png` | `1a3f950d11622ab6a880e3c5adb78fffc60e1156c4bdebec59cb5161be4b25b0` |

NotePanel 保存路径截图 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| `note-panel-before.png` | `8c59010d7bc73d0fbbaf9027e4fd4de553749b1828afb5bd5637b8264d5997a3` |
| `note-panel-after-interaction.png` | `5b424511a74d6c7879baccfab2357f18f99a347c1cfff034a2e7bdc997ea1e28` |

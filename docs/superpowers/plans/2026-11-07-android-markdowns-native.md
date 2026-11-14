# Android 原生 Markdown S 组件 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 笔记阅读流中原生渲染当前 `markdowns` v1 遗传算法实验，并将交互状态安全写回当前 Markdown 草稿，经既有保存流程持久化。

**Architecture:** 在 `feature-notes` 增加可测试的声明式数据校验和精确围栏替换；Android app 层提供生命周期受控的原生实验视图，并接到现有 Markwon 阅读与页面保存链路。普通 Markdown 继续由 Markwon 渲染，旧 `mds-html` 等可执行围栏继续显示源码，不运行 WebView/iframe。

**Tech Stack:** Kotlin、Android View/Canvas、Jetpack Compose `AndroidView`、Markwon、现有 `PageNoteEditor` 和项目笔记保存接口。

**Spec:** `docs/superpowers/specs/2026-11-07-android-markdowns-native-design.md`; shared format `docs/markdown-s/NATIVE-MARKDOWN.zh-CN.md`.

## Global Constraints

- 只识别 `markdowns` 与 `mds-native` 声明式数据围栏；不得在 Android 执行任意 HTML/JavaScript。
- 格式为 version 1、kind `genetic-lab`，唯一非空 ID（最长 80 字符）及 12 个二进制基因。
- 状态字段遵守桌面 `src/markdown-native-contract.js` 的边界；单块 UTF-8 源码上限 200 KiB。
- 组件交互只修改唯一匹配围栏；找不到、重复 ID、冲突或源码过期时拒绝写入。
- 保留围栏外文本、换行风格、其它页内容和现有 Markdown 阅读/保存行为。
- 每个实际支持的模块分别提供 Android 真机运行截图、交互结果和 provenance；未验证能力不得宣传支持。
- 不新增或运行测试，除非用户另行要求；实现阶段可按仓库既有验证规范补测试与记录，但需先遵守本任务权限约束。

## Review Focus

- LF/CRLF、围栏标记长度及 Markdown 中相似代码片段：测试替换精确保留围栏外文本。
- 重复/缺失组件 ID、JSON 无效、未知类型/版本：测试显示源码和诊断且不写入。
- 外部编辑或旧回调晚到：测试基于页面 revision 与组件原状态拒绝陈旧更新。
- 长按/快速重复操作、旋转和离开页面：测试视图状态不重复更新，资源和监听器能释放。
- 低内存或设备不支持 WebGL：测试明确降级、Markdown 状态仍保留；不可用静态图片冒充运行场景。

---

## 文件与责任

- `android/feature-notes/src/main/java/io/pagenote/feature/notes/MarkdownSState.kt`：数据模型、JSON 解析、范围校验、UTF-8 大小校验。
- `android/feature-notes/src/main/java/io/pagenote/feature/notes/MarkdownSBlockParser.kt`：识别支持围栏并给出原 Markdown UTF-16 start/end/source ranges 与诊断。
- `android/feature-notes/src/main/java/io/pagenote/feature/notes/MarkdownSStateEditor.kt`：按 ID/期望状态做唯一匹配和局部围栏替换，保留换行及周围源码。
- `android/feature-notes/src/test/java/io/pagenote/feature/notes/MarkdownSStateTest.kt`、`MarkdownSBlockParserTest.kt`、`MarkdownSStateEditorTest.kt`：数据、解析、编辑契约。
- `android/app/src/main/java/io/pagenote/android/MarkdownSGeneticLabView.kt`：单个实验的原生 view、SVG/Canvas 图形和触控/键盘状态交互；不得依赖 WebView。
- `android/app/src/main/java/io/pagenote/android/MarkdownSNoteRenderer.kt`：Markwon 文本段与受支持原生块的阅读流组合、诊断降级和 View 生命周期。
- `android/app/src/main/java/io/pagenote/android/MainActivity.kt`：在 `MarkdownSourcePane`/当前预览保存上下文中接入渲染器及状态更新回调，不改变普通 Markdown 入口语义。
- `android/app/src/main/res/values/strings.xml`：组件错误、未知能力与保存失败文案。
- `android/app/src/androidTest/java/io/pagenote/android/MarkdownSNoteRendererTest.kt`（若当前 Android UI 测试约定适配）：布局、真实触摸/键盘交互、生命周期/视图截图。
- `docs/android/guide/markdown-s.md`、`docs/android/guide/images/markdown-s/`、`docs/android/CHANGELOG.md`：仅在实际实现和证据完成后更新平台说明与变更记录。

## Tasks

### Task 1: 共享格式模型与解析

**Interfaces:**
- Produces `MarkdownSState.parse(source: String): MarkdownSParseResult`。
- `MarkdownSState` 字段：`version: Int`, `id: String`, `kind: String`, `genes: List<Int>`, `position: Double?`, `generation: Int?`, `paused: Boolean?`, `angle: Double?`, `cut: Int?`, `selectedIndividual: Int?`, `seed: Long?`, `mutationRate: Double?`, `wheelAngle: Double?`；保留未识别 JSON 字段，序列化更新不得删除它们。
- Produces `MarkdownSBlockParser.parse(markdown: String): List<MarkdownSBlock>`，block 提供 engine、source、fenceStart、sourceStart、sourceEnd、fenceEnd、parseResult，偏移以 Kotlin UTF-16 字符索引计。

- [ ] 添加解析测试：桌面示例状态通过；12 基因长度/值、ID、字段范围、UTF-8 200 KiB 边界、畸形 JSON 均符合共享契约。
- [ ] 添加围栏测试：三个反引号/波浪围栏、较长闭合围栏、围栏内反引号、CRLF、未闭合围栏以及围栏外相似文本只产生预期 block/ranges。
- [ ] 实现上述解析器于 `feature-notes`，只解析 `markdowns` 与 `mds-native`，未知语言仍由现有代码块处理。
- [ ] 运行 `./gradlew :feature-notes:testDebugUnitTest --tests '*MarkdownSStateTest' --tests '*MarkdownSBlockParserTest'`；预期新增用例通过且既有 notes 单元测试通过。

### Task 2: 冲突安全的围栏状态替换

**Interfaces:**
- Produces `MarkdownSStateEditor.replace(markdown: String, id: String, expected: MarkdownSState, next: MarkdownSState): String`。
- Errors: `MarkdownSBlockNotUniqueException`, `MarkdownSStateConflictException`, `MarkdownSInvalidStateException`。
- 只允许更新同一 ID 唯一围栏；比较当前解析态和 expected；保留原围栏 marker、缩进、围栏外内容及原始换行风格。

- [ ] 添加替换测试：LF/CRLF、多页笔记、emoji/中文、围栏前后空白和同文多个不同组件，仅目标源码变化。
- [ ] 添加拒绝写入测试：缺 ID、重复 ID、expected 过期、无效 next、修改期间组件被删除时均不改变 Markdown。
- [ ] 实现 `MarkdownSStateEditor.replace`，通过 Task 1 的解析 ranges 定位；禁止以 `String.replace` 全局替换源码。
- [ ] 运行 `./gradlew :feature-notes:testDebugUnitTest --tests '*MarkdownSStateEditorTest'`；预期目标替换与拒绝用例通过。

### Task 3: 原生实验组件模型与绘制

**Interfaces:**
- `MarkdownSGeneticLabView(context: Context, onStateChange: (expected: MarkdownSState, next: MarkdownSState) -> Unit)`。
- `bind(state: MarkdownSState, colors: MarkdownSColors, enabled: Boolean)`；`dispose()` 清理回调、动画、观察器和绘图资源。
- 第一阶段组件范围：基因 SVG 式 12 位交互条、选择/交叉/变异参数和代数图表使用原生 Android View/Canvas；不得直接复刻任意网页脚本。

- [ ] 添加状态转移单元测试：点击/键盘基因翻转、选择个体、切点 1..11、变异率 0..0.3、代数 0..40、暂停与角度边界生成合法 next state。
- [ ] 实现可访问控件：有名称的按钮/滑块、TalkBack 可读状态、触摸目标与横竖屏布局；遵守减少动态效果偏好。
- [ ] 实现原生绘制，所有状态均从 bind 输入导出；不要把 Canvas 像素或 drawable 缓存当持久数据。
- [ ] 实现 `dispose()` 并验证 view detach/attach 不重复注册监听、动画或回调。
- [ ] 在 Android emulator/实体设备启动运行：实际操作基因、参数、代数和暂停，观察状态变化；保存每个支持模块独立截图及 provenance。

### Task 4: 接入 Markdown 阅读流与同文件保存

**Interfaces:**
- `MarkdownSNoteRenderer` 接收 `Context`, 原始当前页 Markdown, 现有 `Markwon`, `MarkdownSColors` 与 `onMarkdownChanged(updatedMarkdown: String)`；提供渲染 View 和 `dispose()`。
- 宿主回调更新现有 `noteDraft` 并沿用 `saveCurrentNote`/`PageNoteEditor.replacePage` 落盘；页面 revision 变化时拒绝陈旧回调并提示刷新。Android 当前不自动保存 Markdown 草稿，不添加新的自动保存路径。

- [ ] 阅读 `MarkdownSourcePane`、`MermaidPreviewHost` 与笔记自动保存调用链，确认选定接入点能保留块级原始源码和 Markwon 正文行为。
- [ ] 实现阅读流组合：普通文本/公式/表格/代码/Mermaid继续 Markwon；有效 `markdowns` block 插入实验 view；无效/未知声明数据展示原文及错误提示。
- [ ] 组件操作调用 state editor 更新当前页 Markdown，只向宿主发完整新 Markdown；宿主使用现有页面 revision 与 autosave 流程，不创建另一个文件。
- [ ] 切页、换项目、源文档切换、View 销毁时 dispose；验证旧回调不会修改其它页面。
- [ ] 更新 Android UI 验收：混排普通段落、公式、代码、Mermaid、原生模块，操作组件并等待保存后从源笔记读取状态。

### Task 5: 降级、回归、平台证据与文档

- [ ] Android 继续把六种旧执行围栏显示为源码；确认没有新增 WebView/iframe/JavaScript 执行路径。
- [ ] 在项目副本中打开、操作、保存、关闭并重开；确认状态恢复、围栏外内容与其它页不变，未知/损坏块仍完整可读。
- [ ] 覆盖旧 Markdown 主流程：普通文本、公式、代码、表格、附件图片、Mermaid、编辑和多页保存；记录设备/OS/软件版本/源码 SHA 与未执行项。
- [ ] 对每个实际渲染模块单独采集 Android 真机截图和可观察交互记录；Three/WebGL 若未实现或未验证，明确显示 unsupported，不报告支持。
- [ ] 更新 `docs/android/guide/markdown-s.md`、相邻截图 provenance、Android README/feature matrix（若有公开能力声明）和 `docs/android/CHANGELOG.md`；解释还未支持的任意 HTML 执行。
- [ ] 运行 Android 对应构建及测试命令，记录确切结果，不将截图测试替代功能测试，也不将 CI 当作实机验收。

## Plan Self-Review

- Spec coverage: 同文件状态持久化由 Task 2/4 覆盖；解析/降级和兼容旧执行围栏由 Task 1/2/5 覆盖；原生组件及资源生命周期由 Task 3/4 覆盖；跨页、冲突、回归和 Android 证据由 Task 4/5 覆盖。
- Open integration choice: `MarkdownSourcePane` 当前在 AndroidView 中用单个 Markwon TextView 排版；Task 4 第一步必须确认最小块级插入策略，不能为了接组件重写普通 Markdown 渲染器。若 Markwon 现有 API 无法安全支持原生交互块，应先拆成独立可滚动 block 流并保持现有视觉/保存回归，作为 Task 4 内部方案，不扩展为 HTML 执行。
- Review focus is exercised by Tasks 1, 2, 3, 4 and 5; remaining device-specific GPU performance is reported as unverified unless actually measured.

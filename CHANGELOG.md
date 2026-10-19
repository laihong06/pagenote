# Changelog / 更新日志

## Unreleased / 尚未发布

- Add a native Android 14+ app for page-linked PDF/Markdown reading, offline OCR, pen annotations, OpenAI-compatible page tutoring, and rich HTML PDF annotations. The separate ARM64 beta build uses `io.pagenote.android.beta`; Office editing remains deferred. JVM tests, Android test compilation, focused API 34 tablet AVD renderer/reader tests (2/2), APK startup, and 16 KB ELF/ZIP alignment checks pass. / 新增 Android 14+ 原生应用，支持逐页 PDF/Markdown 阅读、离线 OCR、手写笔批注、OpenAI 兼容逐页讲解及富 HTML PDF 批注。ARM64 测试包使用独立 `io.pagenote.android.beta`；Office 编辑仍暂缓。JVM 测试、Android 测试源码编译、API 34 平板 AVD renderer/阅读器定向测试（2/2）、APK 启动及 16 KB ELF/ZIP 对齐检查通过。
- Keep very long unbroken Markdown tokens responsive in the Android editor using edit-buffer-only layout boundaries; preserve source offsets, copy/cut/paste/share, drafts and saved project text. Verify API 34 tablet AVD cold start, IME page jump, save and reopen. / Android 编辑器为超长连续 Markdown token 增加仅存在于编辑缓冲区的排版边界，保持原文偏移、复制/剪切/粘贴/分享、草稿与项目保存内容不变；已在 API 34 平板模拟器验证冷启动、输入法跳页、保存和重开。
- List Android `new_project` explicitly in the desktop-command SPEC and make its JVM check verify every wired and deferred command is documented. / 在安卓桌面命令 SPEC 中明确列出 `new_project`，并让 JVM 校验覆盖全部已接线及暂缓命令，防止文档漏项。
- Strengthen Android API 34 regression coverage for real-PDF swipe navigation, zoomed stylus coordinate mapping, extended-canvas bounds, and page-prefetch cancellation on page changes. / 加强 Android API 34 回归覆盖：真实 PDF 双向滑动翻页、缩放手写坐标映射、页外书写边界，以及预解析队列随翻页取消旧窗口。

- Update the Android reader with vertical PDF swipes, zoom-aware stylus writing and optional extended canvas, configurable upcoming-page preparation, and dwell-based shape recognition; record remaining acceptance gaps in the Android SPEC. / 安卓阅读器新增 PDF 上下滑动翻页、缩放下手写与可选页外画布、可配置后续页面预解析及停留识别形状，并在 Android SPEC 中记录剩余验收缺口。

- State the author’s non-commercial-use requirement prominently in both READMEs, and disclose the conflict with the unchanged MIT license. Documentation only; published packages unchanged. / 中英文 README 标注禁止商业使用，并注明与尚未调整的 MIT 许可证存在冲突；不更改已发布安装包。

- Look up draft releases through the authenticated release list: GitHub's tag endpoint can return 404 until publication. / 修正草稿发布查询：公开标签接口可能返回 404，改为在授权可见的发布列表中匹配。

- Allow a versioned beta upload request on the beta branch, without depending on a workflow being present on main; the upload still requires an exact successful validation/tag match. / Beta 分支可通过记录构建编号和标签触发直传，无需把测试流程混入 main。

- Add a manual cloud-to-draft Mac upload workflow that verifies the successful CI commit, exact beta tag, draft status and package checksums. It never publishes a release or replaces published files. / 增加云端直传草稿发布流程，核对 CI 提交、Beta 标签和校验值，不自动公开发布、不替换已发布文件。

## 1.1.0-beta.4 — 2026-10-17

- Keep the Windows executable-name override off macOS: electron-builder uses it for the entire app bundle name. Mac beta now builds as pagenote Beta.app with a matching binary path, alongside stable pagenote.app. / Windows 的可执行文件名覆盖不再影响 Mac；Mac 测试版正确生成独立的 pagenote Beta.app，避免与正式应用重名。

- Dismiss the pen popover before test pan/ink drag actions: on compact displays it can cover the source pane and legitimately receive those events. Preserve the actual native-pan and ink-position assertions. / 小屏幕交互测试先收起覆盖文档的画笔面板，再验证平移和手写拖动；保留实际效果断言。

- Adapt the reading zoom test to compact displays, where the optional Fit label is intentionally hidden; keep all focal-point and page-transition assertions. / 阅读缩放测试兼容小屏幕隐藏“适合窗口”文字按钮的布局，保留全部缩放与翻页断言。

- Record the failed Mac Paper/ink drag assertion as a non-blocking beta diagnostic instead of claiming a pass; retain the failing assertions and screenshots. Keep web prompt-control tests on the web fallback while separately testing native settings. / 如实记录 Mac 纸张主题手写拖动断言异常，保留测试；区分网页控件与原生设置验证。

- Explicitly install the Electron runtime before native/UI tests on clean CI, where npm lifecycle scripts are disabled by repository policy. / 干净 CI 按仓库策略禁用 npm 生命周期脚本，测试前显式准备 Electron 运行库。

- Initialize the temporary test directory on clean macOS CI checkouts; the Swift helper and application compile successfully there. / 修复干净 CI 检出时测试临时目录未创建的问题；已确认 Swift 原生组件与应用可编译。

- Integrate obroccolio PR #6 into beta: native Mac settings, system/light/dark themes, Paper reading layout and first-run welcome. Windows retains the web settings window. / 合入 Mac 主题与原生设置，Windows 使用网页设置窗口。
- Bring native prompt editing up to the current routing architecture and forward expected revisions to prevent overwriting concurrent AI preference updates. / 补齐入口、生成策略、用户画像模块及提示词保存版本保护。
- Keep Mermaid measurement outside the window grid and clean it after success/failure; cover cold rendering, repeated navigation and invalid diagrams. / 修复 Mermaid 测量节点顶起底栏的问题并验证翻页与异常图表。
- Publish matching Windows x64 and Mac Apple Silicon arm64 beta packages; isolate beta identity/data from stable. Intel Mac builds are not included because the native helper is arm64. / 分发同版本 Windows 与 Apple Silicon 测试包，正式版不变；不提供夹带 arm64 组件的 Intel 包。
- Add a dedicated macOS CI build/native/UI/reading/package validation workflow; retain the existing exhaustive workflow and documented MCP limitations. / 增加针对 Mac 改动的分级构建验证，保留既有完整测试与已知限制。

- Redraw the bilingual beta README workflow using aligned generation/revision columns, shared guidance and a learner-feedback loop, following the founder's reference diagram. Documentation only; release binaries unchanged. / 按参考图重排中英文路由图，保留生成、修改、材料准备与用户画像回路；仅更新文档，不改安装包。

- Clarify beta project opening: use File → Open project; remove inherited stable association/registration instructions. Documentation only; beta.3 binaries unchanged. / 修正 beta 项目打开说明，不引导注册正式版文件关联；安装包不变。

## 1.1.0-beta.3 — 2026-10-17

- Separate beta READMEs/downloads from main: publish Windows beta installer, ZIP, source and checksums as a prerelease; keep stable v1.0 untouched. / beta 文档仅提供对应测试包，正式版保持独立；Mac 当前测试包未提供。
- Install as pagenote Beta with a distinct app ID, shortcuts and local data directory; do not migrate stable/legacy data or claim project associations. Explicitly opened project files remain shared. / 分离安装和本地数据，不接管正式版关联；主动打开的项目文件仍需自行复制隔离。
- Include the beta.1 layered workflows and beta.2 routing diagrams/Windows gestures in this beta distribution. / 本次测试包包含此前分层提示词、路由图及触控板改进。

### 1.1.0-beta.2（仅 beta，尚未公开发布）

- Add bilingual SVG prompt/data-routing diagrams to beta READMEs. / 为 beta 中英文 README 增加分层路由图。
- Adapt the gesture engine from obroccolio PR #6 (77d30dd) for Windows: proportional pinch, native two-axis pan, anchored pane zoom, retained PDF/text layers, refreshed bitmap detail and zoom-aware Tag positions. / 沿用贡献者手势逻辑适配 Windows，支持连续捏合、双向平移、锚点缩放与批注定位。
- Observe outer viewport size and reserve scrollbar gutters so Windows scrollbar appearance does not rebuild pages during pinch. / 修正 Windows 非覆盖滚动条引起的尺寸变化与文字层重建。


### 1.1.0-beta.1（仅 beta 分支，尚未公开发布安装包）

- Synchronize the English MCP tool count with the beta catalog (24 tools). / 同步英文 MCP 文档的测试版工具数量为 24。

- Support direct revision feedback receipts as profile evidence after content changes are saved; retain the unresolved full-MCP navigation timeout in KNOWN_ISSUES.md. / 支持直接修改意见的偏好来源凭据；如实保留完整 MCP 回归翻页超时的未通过记录。

- Soft current-page edge highlight; layered, editable entry/generation/revision/teaching/profile prompts with host-Agent scheduling autonomy. / 当前页柔和边缘高亮，分层可编辑提示词，尊重原厂 Agent 的会话与任务调度。
- Add prepare_task for local cached source/OCR/visual data, neighbor pages, note revisions and selected feedback; include continuations and live tool routing. / 新增按场景材料准备与工具目录，减少重复预处理。
- Persist evidence-backed candidate preferences from applied Tags; support editable/resettable profile and revision-conflict protection. / 从已完成批注提炼带来源的候选偏好，可编辑、清空或恢复默认，并发修改有版本保护。
- Add end-to-end workflow, cache, continuation, preference evidence and live-entry regression coverage. / 增加路由、缓存、续读、偏好与入口实时更新验证。

- Establish `beta` for new features and routine fixes; reserve `main` for founder-approved stable promotion after real-use testing. / 建立 beta 测试分支，新功能与常规修复先测试，实测并经发起人批准后再通过 PR 晋级 main 正式分支。

- Surface Mac Office bridge limits, PDF screenshot permissions and recoverable working copies in both READMEs; distinguish Windows file-association scripts from Finder usage. / 将 Mac 配套文档中的 Office 限制、截图权限与恢复副本说明提到主 README，区分 Windows 注册脚本与 Mac 项目打开方式。

- Expand both READMEs with verified contributor-hosted Mac DMG/ZIP links for Apple Silicon and Intel, installation/Office permissions, platform status and contributor credits. Documentation only; published binaries remain unchanged. / 完善中英文 README，加入贡献者提供的 Mac 双架构安装包、安装与 Office 权限说明及贡献致谢，明确其测试版和独立构建身份；不替换已发布程序。

## 1.0.0 — 2026-10-17

- Attribute acceptance to LTH0001's Windows tests (PR #4) plus the founder's supplementary local tests, with final release approval by the founder. / 发布依据为 LTH0001 测试加发起人本机补测，由发起人综合结果放行。

- Freeze Windows v1.0 under the founder-approved early-stage release baseline; macOS remains experimental (1.0.0-beta.1 builds). / 依据发起人确认的早期分级质量门槛冻结 Windows v1.0 正式版，Mac 继续作为测试版。
- Integrate PR #3 and #4, preserve both test suites, and retain PR #2 regression coverage without replacing the equivalent existing formula fix. / 合入 Mac 适配与候选版验收，保留双方测试和公式修复回归。
- Record non-blocking defects and environment gaps in KNOWN_ISSUES.md; include DMG checksums. / 非阻断问题及未验证范围统一归档，补齐 DMG 校验值。

### Candidate preparation history / 候选版准备记录

- Freeze the beta.5 feature scope on `release/2.0`; this candidate is for acceptance testing and is not a stable release. / 在 `release/2.0` 冻结 beta.5 功能范围，本候选版只用于验收，不代表正式放行。
- Restore the existing version-history dialog through the native File menu after the old toolbar was hidden. / 修复旧工具栏隐藏后版本历史无可见入口的问题，可从原生“文件”菜单打开并恢复历史笔记。
- Fix the English-Windows prompt-settings test timeout by checking save responses instead of Chinese status text; wait for asynchronous Tag cards and update continuous-reading tests to the current editor. / 修复英文 Windows 下提示词设置测试误等中文文案的超时，等待异步 Tag 卡片更新，并使连续阅读用例适配当前编辑方式。
- Add an independent-terminal acceptance record and retain failed-run evidence separately from candidate retests. / 补充独立终端验收记录，保留原始失败与候选版复测证据。
- Make the continuous-reading test use a desktop wide enough for its footer controls, and keep the soak test in note-edit mode between saved edits. These are test-harness corrections only. / 连续阅读测试固定足够宽的桌面，长时间测试避免重复切换笔记编辑状态；本次仅修正测试脚本。
- Wait for the Electron window before resizing it in the continuous-reading harness. / 连续阅读测试等待 Electron 窗口创建后再调整大小，消除冷启动竞态。
- Type soak-test edits into a normal paragraph so revisiting a page retains its expected heading. / 长时间测试在普通正文段落输入，避免测试文字改变用于定位的标题。
- Explicitly reopen the test document before checking all 60 saved pages after restart; add a short packaged smoke run for this harness, separate from one-hour stability acceptance. / 长测重启后显式打开测试文档并核对 60 页数量；CI 增加打包版脚本短跑，明确不替代一小时稳定性验收。

- Adapt the beta.5 release tooling for macOS: route desktop packaging through a cache-aware wrapper, add version-consistency checks to both release jobs, and keep Mac menus/packaging coverage. / 适配 beta.5 发布流程：打包走缓存脚本、两条发布流水线接入版本检查，保留 Mac 菜单与打包覆盖。

## 2.0.0-beta.5 — 2026-10-16

- Use a project-local packaging cache by default to avoid cross-device rename failures in redirected Windows profiles. / 打包默认使用项目内缓存，避免 Windows 用户目录被重定向时发生跨盘重命名失败。

- Publish beta.5 installer, complete Windows ZIP, source archive and checksums together; align download links and enforce package/lock/tag/changelog consistency. / 统一发布 beta.5 安装包、完整 Windows ZIP、源码和校验值，同步下载入口，并增加版本、锁文件、标签与更新日志一致性检查。

- Update the native-menu test to match the current page-tutor wording after the upstream prompt rewrite. / 菜单测试已跟随上游逐页精讲新文案更新。

- Restore native macOS Finder/Dock/window/menu behavior, including save-before-hide, explicit quit, Command editing shortcuts and Backspace annotation deletion. / 恢复 macOS Finder、Dock、窗口与原生菜单操作，包括关闭前保存、退出、Command 快捷键和 Backspace 删除批注。
- Connect macOS Word/PowerPoint conversion, project refresh and revision-checked Word edits; render Word screenshots from temporary PDFs without replacing committed previews. / 接入 macOS Word/PPT 转换、项目刷新和版本校验的 Word 编辑；Word 截图使用临时 PDF，不覆盖已保存预览。
- Build universal MCP Node runtimes for Apple Silicon and Intel, and retain OCR native dependencies and licenses in both Mac and Windows packaging. / 为 Apple Silicon 与 Intel 构建通用 MCP 运行时，Mac 与 Windows 软件包均保留 OCR 原生依赖和许可。
- Add a versioned prompt index with task routes for initial teaching, AI Tag edits and ordinary editing; customize individual modules in AI settings. Recommend resource-capped parallel page requests using adjacent source pages, without mandatory handoff waits. Add local Markdown/math/Mermaid/image preflight before saving. / 新增统一提示词索引及初始讲解、AI Tag 修改等任务路由；AI 设置可分别编辑模块，按版本增量读取。逐页采用受资源限制的并发建议与相邻源页上下文，不再等待上一页交接；新增保存前本地格式和附件预检。

- Render multiline display formulas next to prose even when their delimiter lines have no surrounding blank paragraphs. / 修复独立公式虽已分行、但与正文之间缺少空行时仍显示为反斜杠文本的问题。

- Preserve consecutive empty note pages when opening partially generated projects, and reuse the source fingerprint after project cache refresh. / 修复讲解逐页生成时连续空白笔记页被误解析成分页符的问题；修复项目源文件缓存刷新后重复重开的性能问题。

- Bundle offline Chinese/English PP-OCRv4 on CPU; prepare per-page text, coordinates/confidence, page/region PNGs and native Markdown tables/Mermaid assets for AI. Cache prepared data in portable projects; preserve source channels and report uncertain/failed OCR instead of claiming lossless extraction. / 内置离线中英文 OCR，按页准备文字、坐标和置信度、页面与区域图片、Markdown 表格及 Mermaid 图像；结果随项目缓存，保留原文与原图，明确识别局限和失败状态。
- Update image dependencies and bundle PaddleOCR model licensing notices. / 更新图像依赖，随包提供 OCR 模型许可说明。

- Add selective page reads, compact MCP state/results, revision-based cache reuse and portable per-page knowledge handoffs. / 支持原文、笔记及局部段落按需读取，精简 MCP 状态与返回，复用未变化内容，并保存可迁移的页间知识交接。
- Record the project rule that every push must include a matching changelog entry. / 将每次推送必须同步更新日志写入项目协作规则。

- Publish AI notes after each saved page without navigating or resetting the reader's scroll position. / AI 讲解逐页保存后静默更新，保留用户阅读页码和滚动位置。

- Slim the tool rail from 72 to 60 CSS pixels and fit its buttons and popovers. / 左侧工具栏从 72 收窄到 60 像素，同步调整按钮和弹出菜单。

- Align the side tool rail with the workspace below the native menu, removing the duplicated top spacer. / 移除左侧工具栏多余的顶部偏移，使其与原生菜单下的工作区对齐。

- Dismiss selection-tool options on outside clicks or Escape while keeping text selection as the default. / 点击文档、笔记或按 Escape 收起选择工具菜单，默认使用文字选择。

- Handle adjacent multiline dollar display-math delimiters (issue #1). / 修复跨行公式分隔符紧贴正文时显示 LaTeX 源码的问题。
- Add editable local default AI instructions and route the tutor skill only to relevant tasks, across subjects and document types. / 新增可保存、恢复的默认 AI 系统提示词；精讲技能按任务启用，适配不同学科和材料。

- Bundle a page tutor SKILL.md, expose it through MCP resource/prompt discovery and add a copy-prompt menu action. / 内置逐页精讲技能，通过 MCP 资源与提示模板提供，并支持从菜单复制提示词。

- Recognize copied double-escaped TeX delimiters and consistently escaped commands without changing code blocks or normal matrix row breaks. / 兼容复制后的双重转义公式，保留代码块和正常矩阵换行；修复公式显示反斜杠源码的问题。

- Bundle common code-language grammars, normalize aliases/case and preserve plain code when highlighting fails. / 本地打包常用代码语言，统一大小写和别名，高亮失败时保留原文。
- Add a per-page model-request policy with learner context and knowledge handoffs to MCP initialization and documentation. / MCP 初始化指令和文档明确每页单独请求、充分使用上下文及页间知识交接。

- Queue project-open requests during startup; report errors instead of silently dropping them. / 启动期间保留项目打开请求，失败时显示具体错误。
- Show the existing window on file activation and prevent source-refresh races from reopening the previous project. / 文件唤起时显示已有窗口，防止源文档刷新冲突将界面切回旧项目。
- Include current-user file-association setup for ZIP distributions. / 为 ZIP 解压版提供当前用户的项目文件关联脚本。
- Rewrite the bilingual introduction around the motivation for page-by-page AI-assisted learning. / 用更自然的中英文介绍逐页 AI 辅助学习的初心。

## 2.0.0-beta.4 — 2026-10-15

- Regenerate the dependency lockfile from the official npm registry, replacing unavailable tarball versions exposed by the first clean GitHub build. / 按 npm 官方源重新生成依赖锁，修正首次 GitHub 干净构建发现的不可下载版本。
- Retain beta.3 functionality and Windows file-operation fixes. / 保留 beta.3 的功能与 Windows 文件操作修复。
- Normalize source line endings on Windows and prepare Electron's lazily downloaded runtime before source tests. / 固定 Windows 检出的源码换行格式，并在源码测试前准备 Electron 延迟下载的运行时。

## 2.0.0-beta.3 — 2026-10-15

First public beta / 首次公开测试版。

- Read source documents beside page-linked Markdown notes. Render LaTeX, Mermaid, code highlighting, tables and pasted images. / 双栏阅读与逐页 Markdown 笔记，支持公式、图表、代码高亮、表格和图片。
- Chinese/English interface, portable projects, annotation tools, note history, and an authenticated MCP bridge. / 中英文界面、可迁移项目、批注工具、笔记历史与带凭据校验的 MCP 接口。
- Use the system default application for Word/PDF/PPT edits. / Word、PDF、PPT 调用系统默认应用编辑。
- Fix EXDEV data migration and file replacement, encrypted cross-volume file copies, and project sync without a request body. / 修复 EXDEV 数据迁移、文件替换、加密文件跨盘复制和无请求体项目同步问题。

Unsigned Windows x64 prerelease. Signing, vendor review and clean-system acceptance remain pending; see the release-readiness report. / Windows x64 未签名测试版，签名、厂商复核与独立系统验收仍待完成，详见发布准备报告。

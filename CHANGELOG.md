# Changelog / 更新日志

## Unreleased / 尚未发布

- Migrate HarmonyOS code off deprecated APIs: router/promptAction/getContext moved to `getUIContext()` equivalents, `ImagePacker.packing` → `packToData`, `picker.PhotoViewPicker` → `photoAccessHelper.PhotoViewPicker`, and `Circle().fill` (API 26-only) replaced with a rounded Stack to keep API 20 compatibility. A clean rebuild reports zero deprecation warnings; emulator smoke (bookshelf, reader, photo picker, back navigation, Yuketang panel) passes. / 鸿蒙代码迁离废弃 API：router/promptAction/getContext 改走 getUIContext()，packing 改 packToData，相册选择器改 photoAccessHelper，Circle.fill 改圆角 Stack 保持 API 20 兼容；全量重编废弃警告清零，模拟器冒烟通过。

- Advance the HarmonyOS line to 0.2.0-dev.1 (versionCode 2)

- Advance the HarmonyOS line to 0.2.0-dev.1 (versionCode 2) after the feature batches since 0.1.0-dev.1, and record the platform's tracked limits in KNOWN_ISSUES.md (no PDF Kit on the emulator image, unverified portrait branch, picker media-index limits, unverified live-class capture and real AI calls, unsigned debug builds only). No published downloads. / 鸿蒙线推进到 0.2.0-dev.1（versionCode 2），并在 KNOWN_ISSUES 记录平台已知边界（模拟器无 PDF Kit、竖屏未上机、选择器媒体库索引限制、实时采集与 AI 真实调用未验收、仅有未签名 debug 包）。


- Make the HarmonyOS reader orientation-adaptive: left/right panes in landscape switch to top/bottom stacking in portrait or narrow windows (tracked via onAreaChange; the transfer sheet is bound to whichever branch container is mounted). Landscape regression (all sheets) verified on the tablet emulator; the portrait branch is compile-verified but not device-verified because emulator rotation commands are no-ops on this image and the foldable emulator's debug channel never came up. / 鸿蒙阅读页横竖屏自适应：横屏左右分栏、竖屏上下堆叠；横屏回归（各弹层）实测通过，竖屏分支编译验证（模拟器旋转无效、折叠屏调试通道未连通，未能上机）。

- Add note export/import on HarmonyOS (Android note-export/import parity): 更多 sheet in the reader offers full JSON v1 backup export (version/document/pages/notes field-compatible with desktop), current-page Markdown export via the system save picker (dirty draft saved first), and import that merges JSON backups page-wise (valid pages override, empty string clears, missing pages untouched) or inserts Markdown/TXT at the cursor. Export content verified on device; merge semantics verified offline; the import picker only lists media-indexed files so the on-device import loop did not complete. / 鸿蒙端新增笔记导出/导入：JSON v1 全量备份（字段与桌面一致）、当前页 Markdown、备份按页合并或文本插入光标；导出内容设备实测，合并语义离线校验，导入选择器受媒体库索引限制。

- Add image insertion into notes on HarmonyOS: the 插图 toolbar button opens the system photo picker, copies the chosen image into project `src/assets/`, and inserts a Markdown reference at the cursor. The picker flow is verified on emulator (empty gallery on emulator — only picker launch verified); renders through the existing preview image path. / 鸿蒙端新增插图：系统相册选择器选图复制进项目 src/assets 并在光标处插入引用；选择器链路模拟器已验（空图库只验到弹出），渲染走既有图片预览路径。

- Add Mermaid diagram rendering on HarmonyOS: ```mermaid fenced blocks render through a Web component with vendored local mermaid 11 (rawfile, offline), content-height sized via result polling since runJavaScript does not await promises. Verified on emulator with a flowchart screenshot (`emulator-18-mermaid.jpeg`). / 鸿蒙端新增 Mermaid 渲染：```mermaid 围栏块经 Web 组件用本地 vendor 的 mermaid 11 离线渲染，按内容定高（runJavaScript 不等 Promise，改为轮询结果）；模拟器实测流程图渲染。

- Add note-selection AI actions on HarmonyOS (rewrite / translate / summarize): capture the note TextArea selection, run the configured OpenAI-compatible endpoint, and insert the confirmed result back by replacing or appending after the selection. Sheet UI verified on emulator; a real model call still needs a configured reachable endpoint. / 鸿蒙端新增笔记选区 AI 动作：改写/翻译/总结，确认后替换或追加插回；面板流程模拟器已验证，真实模型调用待可用端点。

- Add flashcards on HarmonyOS (Android flashcard module parity): create cards from note selection or the paragraph at cursor (UTF-16 anchors + page revision; manual cards allowed when the page has no usable content), SM-2 simplified four-grade review scheduling with the same constants as Android, due queue, and card library management; stored per project in `src/flashcards.json`. Verified on emulator: card creation, persistence, due-review, rating side effects (reps/interval/dueAt) and library counts (screenshots `emulator-15/16`). / 鸿蒙端新增闪卡：选区/段落制卡（锚点+页 revision，页面无内容时允许手工卡）、SM-2 四档复习排程（常数与 Android 一致）、到期队列与卡片库，存项目 src/flashcards.json；模拟器实测制卡/复习/评分/卡片库全链路。

- Expand HarmonyOS annotations to the shared contract's drawable types: pen, highlighter (semi-transparent), line/rect/ellipse drag shapes with live preview, and tap-to-place text annotations (inline input row — a bottom sheet proved unusable because the soft keyboard dismisses it on this system), plus three stroke widths. Verified on emulator: rect/highlight/text committed to `annotations.json` in the contract format (screenshots `emulator-13/14`). / 鸿蒙批注扩展到契约图形类型：笔、荧光、直线/方框/椭圆拖拽预览、点击落文字批注（软键盘会顶掉底部弹层，输入改为工具栏内嵌行），三档粗细；模拟器实测写入 annotations.json 契约格式。

- Add the night-reading warm filter on HarmonyOS: a toolbar toggle overlays the reading area with a non-interactive warm mask. Verified on the tablet emulator (screenshot `emulator-12-night-filter.jpeg`). / 鸿蒙端新增夜览滤镜：阅读页工具栏开关，阅读区覆盖不拦截触摸的暖色遮罩，模拟器实测。

- Add inline `$…$` formula rendering on HarmonyOS: math spans mix into text via a wrapping Flex row of inline MathView instances sized by rendered content (Web CSS px are component vp — no px2vp scaling; zero-size initial width prevented Web content creation). Verified on the tablet emulator with block and inline KaTeX rendering side by side (screenshot `emulator-11-math-inline.jpeg`); missing note images degrade to their alt placeholder. / 鸿蒙端新增行内 $…$ 公式渲染：公式 span 经 Flex 换行混排，行内 MathView 按内容定宽高；模拟器实测块级与行内 KaTeX 渲染，缺失图片按占位降级。

- Fix Yuketang imported-courseware display on HarmonyOS after a real user login and import attempt: devices without PDF Kit now fall back to showing the original slide images from `yuketang-source.json` with correct page counts and page flipping (previously an unreadable text notice); the PDF builder now writes the JPEG's intrinsic pixel size into the image dictionary (mismatched dimensions produced horizontally tiled pages in the first real import); `Image` now receives `file://` URIs (plain sandbox paths were misrouted to rawfile lookup, which also broke Markdown image attachments); empty SHA-256 input no longer trips cryptoFramework. Verified with a real account: course/lesson lists and a full 134-page history import whose generated PDF renders page-by-page correctly under PyMuPDF; a server-deleted slide (404/416) correctly aborts and rolls back the import. / 修复雨课堂导入课件的显示问题：无 PDF Kit 设备改用来源图片降级显示并可翻页；PDF 生成器图像字典写入真实像素尺寸（修复首测导入的横向平铺乱图）；Image 改用 file:// URI（顺带修复笔记图片附件加载）；空 SHA-256 输入不再报错。真实账号实测：课程/课堂列表与 134 页完整导入，生成 PDF 经 PyMuPDF 逐页渲染校验正常；服务器已删页面（404/416）正确失败回滚。

- Add Yuketang live-class capture on HarmonyOS (R4): checkin + WebSocket timeline with cursor replay and gap marking, exponential-backoff reconnect, sequential pending-page queue with explicit retry, first page creates the project and later pages append via a full rebuild from source images with RainPageOrder-compatible grouping (grouped by classroom+presentation, sorted by source index), notes and annotations remapped by the old-to-new page permutation, duplicate pages detected by content hash. PDF structure verified by an offline byte-level check (xref offsets, page count, EOF); live capture against a real class still needs a signed-in account. / 鸿蒙端新增雨课堂实时课堂采集：checkin + WebSocket 时间线游标回放、断线指数退避重连、待写队列显式重试、首页建项目后续页追加（按来源图重建 PDF、课件分组排序与笔记/批注页码重映射、内容哈希去重）；PDF 结构经离线字节级校验，真实课堂采集仍需登录账号验收。

- Port the Yuketang (rain classroom) history-import line to HarmonyOS, aligned with the Android module contract: in-app official QR login over WebSocket (ticket URL domain allowlist, session kept only in app preferences), course and history-lesson lists, classroom-report slide download with visibility and completeness rules, and project creation as image-based PDF (≤960×720 pages, JPEG q88 re-encode via a new minimal PDF builder since HarmonyOS has no PDF creation API) plus paged blank notes and `yuketang-source.json` source manifest; failed imports roll back the whole project directory. On the emulator the login WebSocket, ticket fetch, QR image download and rendering are verified end-to-end with a screenshot; WeChat scan confirmation, real course lists and imports require a user account and remain unverified, as does live-class capture (not implemented yet). / 鸿蒙端接入雨课堂历史导入线：应用内官方二维码登录（WebSocket、域名校验、凭据只存本机）、课程/历史课堂列表、课后报告课件按可见性下载、图片型 PDF 项目生成（新增最小 PDF 生成器，因鸿蒙无 PDF 创建 API）与来源清单，失败整体回滚；模拟器实测登录链路至二维码渲染并留存截图，扫码确认与真实导入需用户账号未验证，实时课堂采集未实现。

- Verify the HarmonyOS app on a MatePad Pro 13 tablet emulator (HarmonyOS 6.1.1, API 24) and fix three runtime defects found there: ReaderPage no longer crashes on devices missing PDF Kit native libraries (PDF pane is now dynamically imported behind a `canIUse` check with a graceful fallback, so Markdown projects always open); all `bindSheet` panels failed because multiple sheets were bound to one node (each sheet is now bound to a different node, restoring search/history/AI sheets); handwriting annotations never worked because runtime-updated props were plain fields instead of `@Prop` (`penColor`/`drawingEnabled` fixed, strokes verified on screen). Bookshelf, project creation, Markdown reading, note save/dirty state, history list, cross-page search with jump, ink strokes and the import picker were exercised end-to-end with screenshots under `docs/harmonyos/images/`. PDF rendering itself remains unverifiable on this emulator image (no PDF Kit native library) and the picker only shows media-indexed files, so import-select-open and real AI calls are still pending. / 鸿蒙版在平板模拟器上完成首轮运行验证并修复三个运行时缺陷：缺 PDF Kit 原生库的设备打开阅读页崩溃（改为 canIUse 检测+动态 import 降级）；同一节点多 bindSheet 全部失效（拆分挂载节点）；批注参数未用 @Prop 导致永不生效。书架、新建、阅读、笔记保存、历史、搜索跳页、笔迹、导入选择器均已实测并留存截图；模拟器镜像无 PDF Kit，PdfView 真实渲染、导入全链路与 AI 真实调用仍待验证。

- Enable the locally installed CodeGraph for this repository: project index built (95 files, 1,791 symbols) and its MCP server added alongside `deveco-mcp` in the gitignored project `.mcp.json`; index directory excluded as local tooling. No runtime code changes. / 为本仓库启用本机 CodeGraph：已建索引并把其 MCP 与 deveco-mcp 并列写入不入库的 `.mcp.json`，索引目录按本机工具排除，不改动运行代码。

- Wire the locally installed DevEco CLI into the HarmonyOS line: 53 official `hmos-*` skills and the `deveco-mcp` syntax-check server are installed per-machine for this project; their directories and MCP config are gitignored as local tooling, and the project-local `harmonyos-dev` skill documents the setup and division of roles. No runtime code changes. / 鸿蒙线接入本机 DevEco CLI：53 个官方技能与语法检查 MCP 按本机安装，相关目录已加入 gitignore 作为本机工具，不入库；项目局部技能记录接入方式与分工，不改动运行代码。

- Extend the HarmonyOS line on `harmonyos/beta`: handwriting annotations over the source document (four colors, undo, per-page clear, `src/annotations.json` with the desktop-compatible 1000-scale coordinate contract), cross-page full-text note search with jump-to-page (Markdown projects include the source text), image attachment rendering from project `src/assets` sandbox paths, and offline KaTeX formula rendering ($$ / ```math blocks) via a Web component with vendored rawfile assets. Compile verified; emulator/device runs still pending. / 鸿蒙线新增：源文档手写批注（四色/撤销/清页，1000 比例坐标契约）、跨页笔记全文搜索并跳页（MD 项目连同原文）、`src/assets` 图片附件渲染、本地 KaTeX 公式渲染（Web 组件 + rawfile，不依赖网络）；编译通过，模拟器/真机验证仍待做。

- Start the HarmonyOS (ArkTS/ArkUI) platform line on `harmonyos/beta`: native tablet-first app under `harmonyos/` with bookshelf, document import via system picker, PDF reading through PDF Kit, page-linked Markdown notes with the shared `<!-- pagebreak -->` format, revision-checked saves, note history snapshots, basic Markdown preview, and an OpenAI-compatible per-page AI explainer (key stored in app preferences, never in project dirs). Core layer matches the shared project contract field by field. Built with DevEco Studio 26 (API 26 SDK); unsigned debug HAP verified to compile; emulator/device runs and screenshots not yet performed. No published downloads. / 启动鸿蒙平台线：原生平板应用，含书架、系统文件选择器导入、PDF Kit 阅读、共享格式逐页 Markdown 笔记、revision 校验保存、历史快照、基础 Markdown 预览与按页 AI 讲解；核心层与共享契约逐条对齐；编译验证通过但未做模拟器/真机验证，无公开下载。

- Add a project-local HarmonyOS development skill at `.kimi/skills/harmonyos-dev/` capturing the verified local toolchain paths, offline build command and shared format contract. / 新增项目局部鸿蒙开发技能，记录本机工具链、离线构建命令与共享格式契约。

- Reorganize the default project README into platform navigation and provide a separate Windows stable guide; preserve prior mixed documentation as history. Documentation only: stable source behavior, versions and published binaries are unchanged. / 默认首页改为系统导航，Windows正式版使用独立操作说明与历史截图；旧混合文档归档，不晋级测试代码、不修改版本与制品。

- Adopt PolyForm Noncommercial 1.0.0 for newly licensed founder-original material; preserve historical MIT/contributor permissions and third-party notices, update bilingual scope and package metadata, and include both scope and legacy notices in future distributions. Published binaries and tags are unchanged. / 新增原创内容改用非商业许可，保留历史 MIT、贡献者和依赖授权，未来分发携带许可范围与旧声明，不替换历史包。

- State the author’s non-commercial-use requirement prominently in both READMEs, and disclose the conflict with the unchanged MIT license. Documentation only; published packages unchanged. / 中英文 README 标注禁止商业使用，并注明与尚未调整的 MIT 许可证存在冲突；不更改已发布安装包。

### 1.0.1 Windows patch candidate

- Backport only the Mermaid measurement-container fix from obroccolio PR #6: temporary SVG measurements no longer enter the window grid or move page/word-count footers while navigating. Always clean measurement nodes on success and failure. / 仅回移 Mermaid 测量节点修复，防止翻页时页码与字数底栏上跳，正常及异常路径均清理节点。
- Add frame-by-frame cold/warm/repeated/scroll/error diagram navigation regression coverage. Beta themes, native settings and welcome UI are excluded. / 增加逐帧翻页回归；不包含 Beta 主题、设置与欢迎页。
- Source patch version is 1.0.1; existing public v1.0.0 binaries and downloads remain unchanged until a separate matching package release. / 修复源码版本为 1.0.1，已发布 1.0.0 安装包保持不变。

- Separate stable README/download entries from beta; main offers Windows v1.0.0 only, with a link to the beta branch. Documentation only; stable binaries and application code unchanged. / 正式版文档仅提供对应正式包，实验性下载移至测试渠道；不更改正式程序或发布产物。

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

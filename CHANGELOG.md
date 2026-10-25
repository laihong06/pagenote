# Changelog / 更新日志

## Unreleased / 尚未发布

- Lay the Android canvas port batch 4 text tool and rich text editor layer: a text font catalog in feature-annotations (six built-in entries mapping stable ids to Android system font families and matching WebView CSS stacks — no binary font assets bundled, mirroring the desktop's system-font approach) plus a text style schema for `type:"text"` annotations carrying font/fontSize/align through the D2 extension-field passthrough channel with read-side degradation of unknown values and write-side validation (schema decision D6 in canvas-schema-decision.md); new text items now persist the default style (default font, width-derived size, left alignment) while legacy items keep rendering with the desktop derivation. The canvas overlay renders and hit-tests text with the effective font, size and alignment. Style edits are exposed as an UpdateTextStyle undo action on the unified pen action stack (registered `pdf.annotationTextStyle`, consecutive same-scope edits coalesce) while the reader keeps its existing snapshot undo path. A rich text editing window (offline contenteditable WebView with the same asset-origin security posture as the HTML renderer) sanitizes content with DOMPurify in both directions (sanitize-in on load, sanitize-out on every read), keeps style actions and typing in an 80-deep internal undo stack, and confirms into the canvas as a `type: "html"` annotation — creating a new object or updating the selected one in place, persisted through the regular undoable page pipeline. Verified with :feature-annotations unit tests (38/38, incl. 14 new), :app unit tests (399/399, incl. 19 new/extended cases), :feature-pen regression (104/104), and androidTest source compilation; WebView interactive editing was not exercised on a device/AVD in this batch. / 安卓移植批次4文本工具与富文本编辑层：feature-annotations 新增文本字体目录（6 个内置条目，稳定 id 映射 Android 系统字体族与 WebView CSS 栈——不捆绑字体二进制，与桌面系统字体做法一致）及 `type:"text"` 文本样式 schema（字体/字号/对齐经 D2 扩展字段透传，读侧未知值降级、写侧严格校验，决策 D6 见 canvas-schema-decision.md）；新建文本项显式落默认样式（默认字体、按笔宽派生字号、左对齐），旧数据仍按桌面派生式渲染。画布 overlay 按生效字体/字号/对齐渲染并命中测试。样式编辑以 UpdateTextStyle 撤销动作接入统一笔迹动作栈（注册 `pdf.annotationTextStyle`，连续同域编辑合并），阅读器既有快照撤销路径不变。新增富文本编辑窗（离线 contenteditable WebView，与 HTML 渲染器同源同安全姿态）：DOMPurify 双向净化（载入净化进 DOM、每次读出再净化），样式动作与输入共用 80 深度内部撤销栈，确认后以 `type: "html"` 批注落画布——新建对象或原位更新选中项，走常规可撤销页持久化。已通过 :feature-annotations 单测（38/38，含 14 个新增）、:app 单测（399/399，含 19 个新增/扩展用例）、:feature-pen 回归（104/104）及 androidTest 源码编译；本批次未在真机/AVD 上验证 WebView 交互编辑。
- Lay the Android canvas port batch 3 data/settings layer: a note paper background catalog (plain / four-line / grid / dot / restricted-margin) in feature-pen with tolerant unknown-key decoding, project-level persistence in `src/note-settings.json` (SAF recoverable write, project override over the app default), and a new "default paper background" reader setting persisted via DataStore with a bilingual picker. Add project-level note passwords: a PBKDF2WithHmacSHA256 passphrase hash stored inside the project (injectable hasher following the AgentSecretCipher precedent — deliberately not AndroidKeystore-bound so the project stays portable), an open-time verification dialog that gates every project load path before any reader state changes, and note exports encrypted as a self-contained AES-256-GCM JSON container (`*.encrypted.json`) with a fresh per-export salt whenever a passphrase is set. Background rendering stays with the canvas redo batch; unencrypted export behavior is unchanged. Verified with :feature-pen unit tests (104/104, incl. 7 new NoteBackground cases), :app unit tests (379/379, incl. 28 new background/passphrase/export cases), and androidTest source compilation; no public release published. / 安卓移植批次3数据/设置层：feature-pen 新增笔记纸张背景目录（空白/四线格/方格/点阵/横线留边），未知 key 容错降级；项目级持久化落 `src/note-settings.json`（SAF 恢复写入，项目覆盖优先于全局默认）；阅读设置新增"默认纸张背景"项（DataStore 持久化 + 双语选择器）。新增项目级笔记密码：口令哈希采用 PBKDF2WithHmacSHA256 存于项目文件内（按 AgentSecretCipher 先例做可注入接口——刻意不用 AndroidKeystore 绑定，保证项目可迁移），打开项目时在任何阅读状态切换前先弹口令校验对话框，设置口令后笔记导出整体加密为自包含 AES-256-GCM JSON 容器（`*.encrypted.json`，每次导出新随机 salt）。背景渲染归画布重做批次；未设口令的导出行为不变。已通过 :feature-pen 单测（104/104，含 7 个新增背景用例）、:app 单测（379/379，含 28 个新增背景/口令/导出用例）及 androidTest 源码编译验证；未发布公开版本。

- Lay the Android canvas port batch 2 shape geometry layer: a pure-JVM ShapeCatalog in feature-pen with 24 two-point-parameterized shape kinds (lines, dashed lines, single/double arrows, first-quadrant and four-quadrant coordinate axes, sine wave, zigzag, rectangle family, polygons, stars, cross, ellipse/circle), each generating deterministic outline polylines, bounds and stroke hit-test, covered by a new 16-case unit suite. Per the new schema decision D5 (canvas-schema-decision.md), catalog shapes persist as the desktop base type (line/rect/ellipse) plus an extension field `shape` carrying the kind id — no new `type` enum, desktop ignores the unknown field and keeps rendering the base shape. The reader shape picker dialog now offers all 24 shapes with bilingual labels; canvas gestures are untouched and the drag pipeline forwards the tool as before, while the overlay still renders catalog shapes as their base type until a later batch. Verified with :feature-pen unit tests (97/97, incl. 16 new) and :app unit tests (350/350, incl. 3 new vector-draft cases); no public release published. / 安卓移植批次2形状几何层：feature-pen 新增纯 JVM ShapeCatalog，24 种两点参数化形状（直线、虚线、单/双箭头、第一象限与四象限坐标轴、正弦波浪、锯齿、矩形族、多边形、星形、十字、椭圆/正圆），逐种提供确定性轮廓折线、包围盒与描边 hit-test，并新增 16 个单测。按新增 schema 决策 D5（canvas-schema-decision.md），目录形状以桌面基类型（line/rect/ellipse）加扩展字段 `shape`（kind id）落盘——不新增 `type` 枚举，桌面忽略未知字段并按基类型渲染。阅读器形状选择对话框扩展为全部 24 种形状（双语标签）；画布手势未改动，拖拽管线照旧转发工具，目标形状的 overlay 渲染留待后续批次。已通过 :feature-pen 单测（97/97，含 16 个新增）与 :app 单测（350/350，含 3 个新增矢量草稿用例）验证；未发布公开版本。

- Lay the Android canvas port batch 1 data layer: a unified pen action abstraction (do/undo/redo/merge) with a registry and scope-keyed action stack that adapts PenPageStore mutations and the native PDF annotation snapshot history to one Action contract; a pure-JVM store-level rect eraser (eraseInRect) for pen strokes and annotations.json items that batch-deletes intersecting geometry; page-rotation support in the canvas page transform (rotation around the page center with round-trip-consistent mapping); and cross-page clipboard paste that reuses the decode translation and re-validates against the destination page bounds. Data layer only — reader UI wiring and PDF rotation plumbing are deferred to later batches; existing built-in undo/redo behavior is unchanged. Verified with :feature-pen unit tests (81/81), :feature-annotations unit tests (24/24) and the new app-module adapter test (2/2); no public release published. / 安卓移植批次1数据层：统一笔迹动作抽象（do/undo/redo/merge）与动作注册表、按作用域隔离的动作栈，将 PenPageStore 变更与原生 PDF 批注快照历史适配为同一 Action 契约；新增纯 JVM 的 store 级矩形橡皮 eraseInRect，批量删除相交笔画与 annotations.json 批注项；画布页面 transform 支持绕页面中心旋转且正逆映射一致；剪贴板跨页粘贴复用解码平移并按目标页边界二次校验。仅数据层——阅读器 UI 接线与 PDF 旋转贯通留待后续批次，既有内置撤销/重做行为不变。已通过 :feature-pen 单测（81/81）、:feature-annotations 单测（24/24）与新增 app 适配器测试（2/2）验证；未发布公开版本。

- Desktop development advances to 1.1.0-beta.5: new projects link src/source.* and src/notes.md from project.pagenote; standalone imports and opened legacy standalone entries become portable projects while preserving managed history, page notes, assets and Tags. Existing manifests retain their linked filenames. Serialize source replacement with note persistence and reject aliasing note/source paths. Public Beta 4, Android beta.5 and the Windows frozen candidate remain unchanged. / 桌面开发版统一项目目录与 src 文件，补齐直接导入和旧独立文档转换；保留分页、附件、Tag、历史和旧项目链接，不更改已发布安装包及冻结候选。

- Adopt PolyForm Noncommercial 1.0.0 for newly licensed founder-original material; preserve historical MIT/contributor permissions and third-party notices, update bilingual scope and package metadata, and include both scope and legacy notices in future distributions. Published binaries and tags are unchanged. / 新增原创内容改用非商业许可，保留历史 MIT、贡献者和依赖授权，未来分发携带许可范围与旧声明，不替换历史包。

- Add bilingual beta README downloads for the published Android 1.0.0-beta.5 prerelease, with Android 14+/ARM64, contributor signing and incomplete Office/parity scope; desktop downloads remain Beta 4. Publication workflow passed, and local APK SHA256 plus six native-library 16 KB ELF/ZIP checks passed. / Beta 首页新增安卓测试版下载与限制说明；发布流程及本地 APK 校验通过，桌面下载保持不变。

- Compare frozen Android runtime sources against the integration commit while allowing the new release-notes file; adding release documentation changes the directory tree hash without changing application code. / 安卓发布校验排除新增发布说明文件，仍严格比对冻结应用代码，修正首次流水线的目录树校验失败。

- Accept PR #10 into beta only and freeze Android 1.0.0-beta.5; add verified contributor-APK publication with source provenance, signature/metadata/checksum checks and explicit platform limitations. Windows release candidate and main remain unchanged. / 接收安卓 PR 并固定安卓测试版，记录来源、签名与验收边界；不改变 Windows 冻结候选及正式分支。

- Align the Android beta build metadata with 1.0.0-beta.5 (versionCode 5); this source PR does not publish a release. / 将 Android 测试版构建元数据对齐到 1.0.0-beta.5（versionCode 5）；本源码 PR 不会发布版本。
- Give the outside-page ink setting switch a localized accessibility name and add API 34 Compose coverage for its default-off, toggle, save, and restored states. Verified both settings instrumentation tests, AndroidTest source compilation, and Debug APK assembly; no ink behavior changed. / 为页外书写设置开关关联本地化无障碍名称，并增加 Android 14 Compose 回归，覆盖默认关闭、切换、保存和状态回显。设置仪器测试 2/2、AndroidTest 源码编译和 Debug APK 构建通过；未改变手写行为。
- Complete the reader-interaction acceptance audit on the API 34 tablet AVD and add an Activity regression for failed native-annotation writes falling back to the original pressure-bearing freehand stroke. Existing Activity coverage proves shape persistence and undo/redo. Verified navigation 5/5, pen View 9/9, settings 2/2, shape history 1/1, fallback 1/1, focused JVM tests, AndroidTest compilation, and Debug APK assembly; no public release was published. / 完成 Android 14 平板模拟器上的阅读器交互验收，并增加原生批注写入失败后回退为原始含压感自由笔迹的 Activity 回归；已有 Activity 回归覆盖识形落盘与撤销/重做。翻页 5/5、笔迹 View 9/9、设置 2/2、识形历史 1/1、失败回退 1/1、定向 JVM、AndroidTest 编译和 Debug APK 构建通过；未发布公开版本。
- Define a reader-interaction acceptance contract for vertical PDF swipes, upper-right page controls, zoomed/outside-page stylus ink, local forward-page preparation, and dwell shape recognition. Add task 50 to revalidate all six interactions and add coverage for failed native-shape writes falling back to freehand; existing Activity coverage already proves shape persistence and undo/redo. Documentation only; no tests rerun in that commit. / 明确 PDF 竖向滑动、右上角页码导航、缩放及页外手写、后续页本地预解析和停留识形的统一验收契约；新增 task 50 复核六项交互，并补充原生形状写入失败时回退自由笔迹的覆盖；已有 Activity 测试已证明识形落盘及撤销/重做。本提交仅更新文档，未重跑测试。
- Add Android standalone PDF/Markdown import from the File menu and no-project welcome view. Suggest a safe project name, copy Markdown bytes unchanged into the standard v1 project, preserve PDF validation and keep the active reader until successful creation. Verify persisted source grants across Activity recreation and release on clear, dismiss, or success; verify rollback when the copied PDF fails validation. Focused JVM tests, API 34 tablet AVD ProjectCreateOptionalPdfActivityTest (10/10), NewProjectDialogTest (3/3), and a local Debug APK build pass; no public APK or release was published. / Android 文件菜单与空白页新增 PDF/Markdown 独立导入入口，自动建议安全项目名；Markdown 按原始字节写入标准 v1 项目，PDF 保持校验，创建成功前不切换当前阅读项目。验证持久化来源授权可跨 Activity 重建，并在移除、取消或创建成功后释放；复制后的 PDF 校验失败会回滚新建项目。定向 JVM 测试、API 34 平板 AVD 的 ProjectCreateOptionalPdfActivityTest（10/10）、NewProjectDialogTest（3/3）及本地 Debug APK 构建通过；未发布公开 APK 或版本。
- Allow color and width controls to edit the selected freehand stroke or native PDF annotation while preserving the other style property, geometry and unknown fields; keep page-scoped undo/redo and the chosen style for future drawing. Verified with Android unit tests, API 34 tablet AVD annotation tests (2/2), and a local Debug APK build; no public APK or release was published. / 颜色与宽度控件现可修改选中的自由笔迹或 PDF 原生批注，同时保留未修改的样式属性、几何和未知字段；支持按页撤销/重做，并保留后续绘制样式。Android 单测、API 34 平板 AVD 批注测试（2/2）和本地 Debug APK 构建通过；未发布公开 APK 或版本。
- Harden selected annotation style edits against concurrent page writes and annotation-list deletions: patch only the still-matching selected item, preserve unrelated latest fields, and clear or shift selection indexes so styling cannot retarget a neighboring item. Equivalent colors are no-ops. Verified with Android unit tests, API 34 tablet AVD annotation tests (2/2), and a local Debug APK build; no public APK or release was published. / 加固选中批注样式修改，避免与并发页面写入或批注列表删除冲突：仅更新仍匹配的目标项、保留其他最新字段，并在删除后清除或调整选择索引，避免样式误改相邻项目；等价颜色视为无操作。Android 单测、API 34 平板 AVD 批注测试（2/2）和本地 Debug APK 构建通过；未发布公开 APK 或版本。
- Add a desktop workflow semantic-parity audit beyond command wiring, tracking six verified Android gaps and correcting the selected-annotation style contract. / 新增超越命令接线层的桌面工作流语义差异审计，追踪六项已核实的安卓缺口，并修正“已选批注样式”契约。
- Pin the candidate Collabora Android engine revision and record the Linux builder, NDK and 16 KB alignment constraints without claiming build or integration success. / 固定 Collabora Android 引擎候选版本并记录 Linux 构建器、NDK 与 16 KB 对齐约束；不将其表述为构建或集成成功。
- Add a native Android 14+ app for page-linked PDF/Markdown reading, offline OCR, pen annotations, OpenAI-compatible page tutoring, and rich HTML PDF annotations. The separate ARM64 beta build uses `io.pagenote.android.beta`; Office editing remains deferred. JVM tests, Android test compilation, focused API 34 tablet AVD renderer/reader tests (2/2), APK startup, and 16 KB ELF/ZIP alignment checks pass. / 新增 Android 14+ 原生应用，支持逐页 PDF/Markdown 阅读、离线 OCR、手写笔批注、OpenAI 兼容逐页讲解及富 HTML PDF 批注。ARM64 测试包使用独立 `io.pagenote.android.beta`；Office 编辑仍暂缓。JVM 测试、Android 测试源码编译、API 34 平板 AVD renderer/阅读器定向测试（2/2）、APK 启动及 16 KB ELF/ZIP 对齐检查通过。
- Keep very long unbroken Markdown tokens responsive in the Android editor using edit-buffer-only layout boundaries; preserve source offsets, copy/cut/paste/share, drafts and saved project text. Verify API 34 tablet AVD cold start, IME page jump, save and reopen. / Android 编辑器为超长连续 Markdown token 增加仅存在于编辑缓冲区的排版边界，保持原文偏移、复制/剪切/粘贴/分享、草稿与项目保存内容不变；已在 API 34 平板模拟器验证冷启动、输入法跳页、保存和重开。
- Add Android Ctrl/Cmd+B, I, and K formatting shortcuts that reuse note-toolbar transforms only while the writable Markdown editor is focused; preserve selection and undo/redo and leave dialogs, read-only notes, and other panes untouched. Verified on the API 34 tablet AVD. / 新增 Android Ctrl/Cmd+B、I、K 笔记格式快捷键，仅在可写 Markdown 编辑器获得焦点时复用工具栏变换；保留选区与撤销/重做，并避开对话框、只读笔记及其他面板。已在 API 34 平板模拟器验证。
- Add an independent Android Classic / Paper Markdown reading theme with persistent settings, warm serif content palettes for light and dark appearance, and matching source, note, code, and Mermaid styling. Theme changes leave Markdown files and PDF pixels untouched. Verified with focused API 34 tablet tests and the 16 KB-aligned debug APK. / 新增独立的 Android Markdown「经典 / 纸页」阅读主题及持久化设置，为浅色与深色外观提供暖纸衬线配色，并同步应用于 Markdown 原文、笔记、代码与 Mermaid；切换主题不改写 Markdown 文件或 PDF 像素。已通过 API 34 平板定向测试及 16 KB 对齐 Debug APK 验证。
- Add editable Android-local overrides for the six desktop AI prompt modules, with per-module save/reset, validation, task-aware loading, and the immutable document-safety prefix. Verified on the API 34 tablet AVD. / 新增与桌面六个 AI 提示词模块对应的 Android 本地编辑与覆盖，支持逐模块保存/恢复、输入校验和按任务加载，并始终保留不可移除的文档安全前缀；已在 API 34 平板模拟器验证。
- Specify Android tasks 46–49 before implementation: safe rich-HTML clipboard conversion, editable desktop-compatible AI prompt modules, an independent Markdown theme, and focused editor shortcuts. / 在实现前补齐安卓任务 46–49 的 SPEC：安全富 HTML 剪贴板转换、兼容桌面模块的可编辑 AI 提示词、独立 Markdown 主题与编辑器快捷键。
- Convert explicitly pasted clipboard HTML to Markdown locally, preserving common formatting while dropping active content and unsafe links; retain plain-text fallback and page-scoped note saving. / 将用户主动粘贴的剪贴板 HTML 在本地转换为 Markdown，保留常用格式并移除活动内容和不安全链接；保留纯文本回退与按页保存。
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

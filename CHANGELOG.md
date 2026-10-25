# Changelog / 更新日志

## Unreleased / 尚未发布

- Rebuild the Android pen canvas rendering pipeline: committed ink renders once into a view-pixel raster blitted each frame, freehand ink renders through midpoint quadratic smoothing, and a display-only predictor extrapolates the live tail from the last nine samples with turn, speed, pressure, and refresh-rate damping. Stored annotations, the public canvas API, and desktop behavior are unchanged; verified with new JVM/instrumentation suites plus all existing pixel regressions on the API 34 tablet AVD. Local debug build only, no public release. / 重构安卓手写渲染管线：已提交笔迹渲染进逐帧位图缓存的视图像素层，自由笔迹改为中点二次贝塞尔平滑，新增仅用于显示的落笔预测（九点窗口，按转角、速度、压感和刷新率调节）。存储的批注数据、画布公开接口与桌面行为均不变；新增 JVM/仪器测试并通过全部既有像素回归（API 34 平板 AVD）。仅本地 debug 构建，不发布公开版本。
- Fix Android highlighter self-overdraw: composite each gesture as one translucent layer so retracing and self-intersections cannot increase its opacity; keep live and reloaded ink consistent, pressure widths intact and separate strokes independent. Verified 14 native pen checks and 50 pen JVM tests on the API 34 tablet workflow; local debug APK only, no public release. / 修复安卓荧光笔单次下笔反复叠色：整笔作为一层半透明涂层合成，往返与交叉不再加深；书写预览与重新载入一致，保留压感粗细和不同笔画的独立叠加。笔迹模块 14 项原生检查和 50 项 JVM 测试通过；仅本地 debug 包，不发布公开版本。
- Port the reader reference to Android with a 44dp pictographic pen rail, constant-width ballpoint and transient laser pointer, existing color/width/vector tools, and a localized right-side lazy PDF/Markdown page-preview picker. Page selection keeps draft-save/conflict protection; PDF menus collapse to a top trigger. Seven focused API 34 checks and 67 related JVM tests pass; local debug build only, no published downloads change. / 安卓落地 44dp 象形笔盒、恒定粗细圆珠笔和不落盘的临时激光笔，复用颜色/粗细/矢量工具；页码入口改为跟随应用语言的右侧 PDF/Markdown 页面预览，跳页保留草稿保存与冲突保护，PDF 菜单默认收起到顶部按钮。API 34 定向检查 7/7、相关 JVM 67 项通过；仅本地 debug 构建，不修改公开下载。
- Refine the local HTML reader prototype from the supplied references: pictographic pen tools on a left rail with color/width/shape previews, plus a right-hand thumbnail page picker opened from the page counter and dismissed by selection, outside click or Escape. Production clients remain unchanged. / 根据参考图调整本地 HTML 阅读器样板：左侧象形笔工具与颜色/粗细/形状预览；点击页码打开右侧缩略图选页栏，选页、点击栏外或 Esc 可收起。不修改正式客户端。
- Add a standalone offline HTML UI reference in `docs/ui-reference/` for screenshot-driven design iteration: bookshelf, reader, project forms, Rain Classroom, settings and shared tokens, with local reference-image comparison. Prototype data only; no production UI, accounts, project files or public downloads change. / 新增可直接打开的离线 HTML 界面样板，涵盖书架、阅读器、新建项目、雨课堂、设置和基础设计规范，支持本地截图并排/叠加对照。仅使用样例数据，不修改正式界面、账号、项目文件或公开下载。
- Keep Android Rain Classroom captures in first-seen PPT sections and original slide-index order, inserting late pages in place and following the received page without duplicating replays. Move page notes, ink/annotations, AI references and local note history with their pages; roll back related files on write failure. Verified the ended `test` course (two PPTs, 78 pages) and focused API 34 regressions. Local debug build only; desktop behavior and public downloads are unchanged. / 安卓雨课堂按 PPT 首次出现顺序分节、按原始页索引排列；迟到页原位插入，重复页不增页并跟随到对应页面。笔记、手写/批注、AI 页引用和本地笔记历史随页移动，写入失败回滚关联文件。已验证下课的 `test` 课程（2 份 PPT、78 页）及 API 34 定向回归；仅更新本地 debug 包，不改桌面行为或公开下载。
- Align the feature PR with the upstream beta integration line as 1.1.0-beta.5-dev.1, preserving its isolated beta identity and data directory; the earlier 1.0.1-dev.6/dev.7 versions below describe local-only builds. No published version or download is replaced. / 功能 PR 对齐上游 beta 集成线，开发版本为 1.1.0-beta.5-dev.1，保留独立的 Beta 身份与数据目录；下文 1.0.1-dev.6/dev.7 仅为此前本地构建记录，不替换已发布版本或下载。
- Prepare the cross-platform bookshelf and Rain Classroom follow-up for upstream PR review, with fresh packaged Mac arm64, Android 14 tablet bookshelf and targeted JVM/protocol checks. Source-only feature submission; no main-branch promotion or public binary release. / 将跨端书架与雨课堂后续修复整理提交上游 PR；复验打包 Mac arm64、Android 14 平板书架以及定向 JVM/协议回归。本次仅提交功能源码，不合并主分支或发布公开安装包。
- Add the Android-style bound-folder bookshelf to the shared Windows/Mac UI: automatic project discovery, PDF/Note covers, portable cover customization, search and list views, independent imports and save-protected return navigation. Local development advances to 1.0.1-dev.7; no public download is replaced. / Windows/Mac 共用界面增加与安卓一致的绑定目录书架：自动发现项目、PDF/Note 封面、跨端封面自定义、搜索和列表、独立导入及保存后返回书架。本地开发版本进入 1.0.1-dev.7，不替换公开下载。
- Serialize desktop Rain Classroom slide appends with note autosaves, preventing an older slide transaction from overwriting newly saved portable notes; wait for pending writes before reopening projects. / 桌面雨课堂追加页面与笔记自动保存共用写入队列，避免旧页面事务覆盖新保存的项目笔记；重新打开项目之前等待待写入任务。

- Label Android's default paper notebook cover “Note” rather than printing “草纸本”; retain the cover colors and the separate project title. / 安卓默认纸本封面改用“Note”字样，不再印“草纸本”；保留封面配色，实际书名仍单独显示。

- Align shared Windows/macOS Rain Classroom history and live capture with the verified Android flow: load visible student-report slides with real per-deck identities, retain rotated bearer sessions, backfill the complete initial timeline including stable page IDs, and offer new-project capture without a placeholder PDF. Local development advances to 1.0.1-dev.6; no public binaries or download links are replaced. / Windows/Mac 共用雨课堂实现对齐 Android 已验证流程：历史导入读取学生报告可见页并保留真实课件身份，安全保存轮换的 Bearer 会话，首次补采完整时间线并支持稳定页面 ID；新增无需占位 PDF 的“新建项目并采集”入口。本地开发版本进入 1.0.1-dev.6，不替换公开安装包或下载链接。

- Replace Android's startup screen with a bound-folder bookshelf: discover standard projects recursively, show PDF page/paper notebook covers, customize display titles/images/PDF cover pages/colors, and support search, sort and grid/list views. New notebooks, document imports and Rain Classroom projects default to that folder; returning to the shelf saves notes and preserves per-book reading positions. Verified the existing three-project library and focused Android 14 tablet checks; local debug build only, no public release or desktop package changes. / 安卓首页改为绑定目录的书架，自动递归发现标准项目，支持 PDF 页面与草纸本封面、自定义书名/图片/PDF 封面页/纸本配色，以及搜索、排序和网格/列表。普通新建、文档导入和雨课堂项目默认保存到书架目录；返回书架前保存笔记，并分别记住各本书的阅读位置。已验证原有三个项目与 Android 14 平板定向流程；仅更新本地 debug 包，不发布新版本或修改桌面安装包。

- Fix Android mid-lesson capture dropping earlier slides: replay the full initial classroom timeline, resolve `sid` page IDs as well as `si` indices, retain failed backfill pages for retry and preserve reconnect cursors. Verified 18 real backfilled pages with matching PDF, notes and source records on the Android 14 tablet AVD. Local debug build only; public downloads unchanged. / 修复安卓中途上课采集遗漏前面课件的问题：首次连接补采完整时间线，兼容 `sid` 页面 ID 与 `si` 页序，保留失败补采队列和断线游标。已在 Android 14 平板模拟器验证真实补采 18 页，PDF、分页笔记和来源记录一致。仅更新本地 debug 包，不更改公开下载。

- Let Android Rain Classroom create a new capture project without an existing PDF. Collect its name/folder and attendance consent, create the PDF from the first real slide, then append and deduplicate later slides while preserving notes. Ongoing history reports (`LESSON_ON`) now offer the live workflow instead of a raw error. Verified live first-slide creation and the actual reader entry on the API 34 tablet AVD; no subsequent live slide arrived during the observation window. Local debug build only. / 安卓雨课堂支持不预先打开 PDF，直接设置项目名称、保存目录并确认考勤提示后开始采集；首张真实课件建立项目，后续页面追加去重并保留笔记。历史导入遇到正在上课的 `LESSON_ON` 时改为引导实时采集。已在 Android 14 平板模拟器验证真实课堂首张建项目与实际菜单入口；观察期间没有新的真实翻页。本次仅更新本地 debug 包。

- Fix Android Rain Classroom history imports confusing lesson IDs with presentation IDs; read visible student-report slides in source order and preserve real per-deck identities. Verified a real-account 82-page import on the API 34 tablet AVD, with matching PDF, notes and source records. Also accept current-class responses without a status field and retain rotated bearer credentials securely. Local debug builds only; public releases are unchanged. / 修复安卓雨课堂历史导入混用课堂 ID 与课件 ID 导致的 401，改为读取课后报告中的可见页并按源页序导入，保留各份课件的真实标识。已在 Android 14 平板模拟器使用真实账号验证 82 页导入，PDF、分页笔记和来源记录一致；兼容缺少状态字段的当前课堂响应，并安全保存轮换的认证令牌。仅更新本地 debug 构建，不更改公开发布版本。

- Let desktop Rain Classroom imports choose their destination folder through the native directory picker; Android continues to use SAF. / 桌面雨课堂导入现在可通过系统目录选择器指定目标文件夹；Android 继续使用 SAF。

- Preserve course, classroom, and lesson identity on every Rain Classroom source page so duplicate detection remains correct when a project is reused across classes; migrate legacy manifests on append. / 雨课堂来源清单为每一页保留课程、课堂和课次身份，修正项目跨课堂复用时的重复判定，并在追加时迁移旧清单。

- Add an Electron UI regression for the native File → Rain Classroom entry, confirming it opens the in-app panel, shows the signed-out state and keeps live capture disabled until attendance consent. No Rain account or classroom is used. / 新增 Electron UI 回归，验证原生“文件 → 雨课堂导入与实时采集”会打开应用内面板、显示未登录状态，并在确认签到提示前禁用实时采集；不使用真实账号或课堂。

- Record fresh Rain Classroom packaging and runtime boundaries: Node regressions and Android API 34 tablet AVD launch/QR display pass; no account confirmation, history import, or real live lesson was performed. Windows x64 packaging completes with a missing `mcp-dist/mcp-node.exe` warning; the rebuilt Mac arm64 bundle awaits cold-start because the host is locked. No public release. / 更新雨课堂最新打包与运行边界：Node 回归及 Android 14 平板 AVD 启动、应用内二维码显示通过；未确认真实账号、导入历史课件或进入真实课堂。Windows x64 打包完成但提示缺少 `mcp-dist/mcp-node.exe`；Mac arm64 已重打包，主机锁屏，仍待冷启动。未发布公开版本。
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
- Implement Rain Classroom in-app QR login, course/history import and consent-gated per-slide live capture for desktop and native Android. Add a separate current-class selector backed by the `status=1` classroom list, require every provided course/classroom ID to agree before capture, and explain unmatched classes in the UI. Sessions use Electron safeStorage or Android Keystore; captures append local slides/notes and a versioned source manifest. The shared sanitized fixtures and fake-WebSocket recovery/retry tests pass; real Rain Classroom login/live-class behavior and Windows runtime remain unverified. The Windows x64 directory package builds and contains Rain modules, but packaging warns that `mcp-dist/mcp-node.exe` is missing. No public release is published. / 实现桌面端与原生 Android 的雨课堂应用内扫码登录、课程/历史课件导入和需明确确认的逐页实时采集；新增独立的正在进行课堂列表，并要求课堂与课程已提供的标识全部匹配才允许采集，未匹配时会显示原因。会话分别使用 Electron safeStorage 与 Android Keystore，课件页追加到本地项目并记录版本化来源清单。共享脱敏样例及 fake-WebSocket 补采/重试测试通过；真实账号登录/课堂采集及 Windows 运行尚未验证。Windows x64 目录包已构建并包含 Rain 模块，但打包时提示缺少 `mcp-dist/mcp-node.exe`。未发布公开版本。
- Replay missed Rain Classroom slides from the reconnect timeline using a per-session cursor, retain failed slides in a retryable queue even after stopping, and block a new lesson or logout until pending slides are written. Warn when the server no longer includes the previous position. / 雨课堂断线重连后按会话游标回放并补采时间线中的新页；停止后仍保留失败页面供重试，页面写入完成前禁止切换课堂或退出账号。若服务端时间线已不含上次位置，则明确提示期间可能缺页。
- Document runtime and test-only license notices for the native Rain Classroom module. / 补充原生雨课堂模块运行依赖与测试依赖的第三方许可证记录。
- Display the Rain Classroom WebSocket ticket as its bounded, host-validated QR image in both desktop and Android apps instead of incorrectly encoding the image URL as a new QR code; normalize upstream `image/jpg` to `image/jpeg` and add a sanitized protocol regression fixture. The Mac arm64 app shows the in-app QR; account login and live capture remain unverified. / 修复桌面和 Android 将雨课堂 WebSocket 图片票据错误地再次编码为二维码的问题；校验允许的主机并限制大小，在应用内显示且将上游 `image/jpg` 归一化为 `image/jpeg`，新增脱敏协议回归样例。Mac arm64 应用已显示应用内二维码，真实账号登录和实时采集尚未验证。
- Record the initial Mac feasibility probe that imported an 82-page Rain Classroom deck through an existing authenticated browser session and verified offline OCR/cache in the rebuilt arm64 app. That early probe is superseded by the in-app integration above; no public release is published. / 记录早期 Mac 可行性试验：通过已登录网页会话导入 82 页雨课堂课件，并在重新打包的 arm64 应用验证离线 OCR 与缓存；该早期试验现由上方应用内接入实现推进，不发布公开版本。
- Add a versioned Rain Classroom source-manifest schema and sanitized cross-platform protocol fixtures; preserve source slide index zero and avoid storing credentials or signed URLs in project files. / 新增版本化雨课堂来源清单 schema 和脱敏跨端协议样例；保留来源页索引 0，不将登录凭据或签名资源 URL 写入项目文件。
- Record the three-platform validation boundary: Windows x64 packaging includes Rain Classroom but is not runtime-tested and reports a missing MCP Node binary; the arm64 Mac app is rebuilt but awaits cold-start validation; Android API 34 JVM tests and debug packaging pass. Fixture/fake-WebSocket results do not verify a real Rain Classroom account or live class. No release published. / 记录三端验收边界：Windows x64 包含雨课堂功能但尚未实机运行，打包时提示 MCP Node 二进制缺失；arm64 Mac 已重打包但仍待冷启动；Android 14 JVM 测试与 Debug 包构建通过。脱敏样例和 fake-WebSocket 测试不能证明真实雨课堂账号或课堂联通，未发布版本。
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

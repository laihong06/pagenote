# pagenote Android 端任务拆解

> 执行顺序：先契约与骨架，再阅读与笔记，再标注与笔迹，再 OCR 与准备管线，再 Agent 与模型，最后集成与验收。
> 每完成一个任务更新复选框，保持变更可回链到 `requirements.md` 验收。
> 桌面菜单分组、自适应侧栏和全量用户功能移植已确认；Android 14/API 34 是最低功能基线，API 34 及以上不得按系统版本区分可用功能。
> 测试按用户要求收敛：只运行受影响的定向测试、Debug 构建和必要时唯一 API 34 平板 AVD；AVD 可替代常规设备验收。真实 MIUI/压感/侧键差异单独记录，不扩展设备与系统版本矩阵。

## Implementation Plan

- [x] 1. 跨端契约与测试夹具（`ProjectAssets` 导出/导入往返与缺文件跳过、`saveNote` 过期 revision 冲突拒绝已由共享回归 pin 住，契约见 `features/shared-contract-assets-revision.md`）
  - [x] 在 `android/docs/spec/contracts/` 落 `project.schema.json`、`annotations.schema.json`、`ai-reviews.schema.json`、`prepared-manifest.schema.json` 与分页示例。
  - [x] 在 `android/shared-tests/` 覆盖分页、空页、标注和准备 manifest 的核心桌面语义。
  - [x] 补充 assets 与 `expectedRevision` 冲突的共享回归夹具和断言。
  - _Requirement: 项目与数据兼容，构建与测试_

- [x] 2. 安卓工程骨架与构建隔离
  - 新建 Gradle 工程与 `app/core-project/core-reader/feature-notes/feature-annotations/feature-pen/feature-ocr/agent/shared-tests` 模块。
  - 接本地 SDK 与 Gradle Wrapper，`assembleDebug` 按 ABI 可出可安装产物。
  - 最低运行版本 Android 14/API 34；所有 Android 模块的 `minSdk` 与需求规格一致。
  - 桌面构建忽略 `android/`，安卓构建只认 `android/`；验证互不干扰。
  - _Requirement: 构建与测试，约束与非功能_

- [x] 3. 项目管理与分页笔记（SAF 打开/创建、分页读写与 revision 冲突、按页历史、assets 直读均已落地，真机待 task 12）
  - [x] 解码并校验现有项目清单，通过 SAF 打开现有 PDF 项目。
  - [x] 实现 `splitPageNotes` 同语义分页与合并、空页占位，以及整页 SHA-256 revision 和 `expectedRevision` 冲突拒绝。
  - [x] 接入按页历史快照/列表/恢复工作流（保存成功自动快照去重，历史弹窗预览与二次确认恢复，`expectedRevision` 冲突不覆盖；契约见 `features/note-history-restore.md`）。
  - [x] 接入 SAF 项目创建（名称校验、目录选择、完整结构落盘、失败清理、创建后直接打开；权限 URI、manifest 文件名和 picker 返回状态在 task 12 补充回归修复；契约见 `features/project-create.md`）。
  - [x] 接入资源引用直读（SAF 项目即源目录：打开时扫描 `assets/` 引用并提示缺失，保存时直写引用不搬运；契约见 `features/project-assets-saf.md`）。
  - _Requirement: 项目与数据兼容，阅读与笔记_

- [x] 4. 阅读器与双栏自适应（SAF 入口、PdfRenderer 翻页、原生文字/视口/缓存、窄屏堆叠/宽屏并排、Markdown 源阅读均已落地，真机待 task 12）
  - [x] 4.1 SAF 打开项目目录、解码并校验 `project.pagenote`、使用 `PdfRenderer` 翻页（`ProjectDocumentOpener` + `PdfDocumentSession` + 上下页已接通）。
  - [x] 4.2 原生文字/文字块抽取、整页与区域 PNG、缩放平移及缓存接入（API 34 使用本地 PDFBox 字形坐标回退、API 35 使用 `PdfRenderer` 文本接口；API 34+ 均报告可用，`PdfPagePolicy` 整页渲染、区域图、缩放平移与 `ProjectPreparedCache` 已接通）。
  - [x] 4.3 手机上下页、平板左右对照、Markdown 源文档阅读（Markdown 源按 `PageNotes.split` 分页、页数取 max、对齐笔记页，PDF 专属入口禁用；契约见 `features/markdown-source-reading.md`）。
  - PDF 按页渲染、原生文字与文字块抽取、整页 PNG。
  - 手机竖屏上下页，平板横屏左右对照；缩放、平移、适应宽度、重置缩放。
  - Markdown 源文档阅读（已落地，JVM 单测 + 仪器编译 + 238 任务全量构建与 16 KB 检查通过，真机待 task 12）。
  - _Requirement: 阅读与笔记，布局与适配_

- [x] 5. 笔记渲染与轻量编辑（Markwon 编辑/预览、GFM 表格/删除线/任务列表、公式/代码 span 与图片离线占位已实现；Mermaid 离线 SVG、语法诊断、异步布局和像素重叠回归由 API 34/35 AVD 验证；SAF `ContentResolver` 查询/打开成功及失败回退由 API 34/35 AVD 集成测试验证。原项目页面 1/35/69 现场抽查、真实系统持久授权与真机交互仍归 task 12）
  - [x] 富块透出与附件图已落地：Mermaid 源码单次出现并保留状态，附件图经 `pagenote-asset://` + Markwon image 插件加载本地 `src/assets/`，未知语言与失败态不丢源码；契约见 `features/note-rich-render.md`。
  - [x] 5.1 API 34/35 AVD 已验证 LaTeX drawable span、Prism 高亮 span、页笔记/Markdown 源预览中的本地化诊断，以及有效公式真实像素绘制和无效 TeX 经 renderer 错误回退；JVM 覆盖多行/多反引号代码跨度和公式闭合符屏蔽。实施单见 `formula-preview-rendering-plan.md`。
  - [x] 5.2 图片严格离线：已覆盖 Markwon 默认 http/https handler；API 34/35 AVD 已验证缺图、损坏内联图片警告、解码期抛 I/O 的流仍显示 warning、HTTP/HTTPS 不连接，以及真实目标 `ContentResolver` 对 `DocumentsContract` 形状 URI 的查询/打开成功和查询拒绝/打开拒绝时的安全占位。此测试不证明系统 picker 或真实持久 URI grant；所有外链阻断独立于 Agent 联网权限。实施单见 `saf-image-contentresolver-plan.md`。
  - [x] 5.3 Mermaid 围栏由 APK 内固定 Mermaid/DOMPurify renderer 离线绘制；隐藏 WebView 仅加载白名单 APK 资源，SVG 转原生 Drawable；空/错误/超长源码、并发、JSON 安全传递与无网络由 Pixel Tablet API 35 AVD 验证。全量 JVM、56 项 AVD 仪器测试、四 ABI 构建和 16 KB ELF/ZIP 检查通过；真机交互待 task 12。
  - [x] 5.4 Mermaid 异步 SVG 重叠已由平板宽度 `NotePanel` 像素回归复现并修复：SVG 绘制裁剪到 drawable 边界，异步尺寸完成后重新测量正文。API 34/35 focused regression 与两版各 66 项 app instrumentation、app/notes JVM tests、`assembleDebug`、四 ABI 16 KB 检查通过；原项目 pages 1/35/69 现场抽查仍归 task 12。实施记录见 `mermaid-layout-regression-plan.md`。
  - [x] 5.5 新增单设备 Markdown 预览截图工具：在 Android 14/API 34 平板 AVD 复用真实 `NotePanel`，用一组富内容覆盖中英混排、表格、公式、代码、Mermaid 与离线图片占位。截图发现 SVG 在 Markwon 平移画布坐标下不可见，已改为宽度受限、最多 4,194,304 像素且单边不超过 16,384 像素的 `BitmapDrawable`。仅保留一个 focused instrumentation 测试和一张浅色截图；脚本先构建、再用显式 ADB serial 只安装到所选 arm64 API 34 AVD。目标小米实机验收仍待进行。详见 `features/markdown-visual-capture.md` 与 `markdown-visual-capture-plan.md`。
  - [x] 超长页截断呈现已落地：预览复用 `NotePage.preview` 8000 字符阈值 + 字数提示，编辑仍为全文；契约见 `features/note-overlong-preview.md`。
  - [x] 5.6 补齐删除线和 GFM 任务列表的 Android Markwon 预览渲染，并确认源码编辑保留标记；不实现点按勾选或写回。渲染器仪器类 12/12 在唯一 API 34 平板 AVD 通过，notes/app JVM 测试及 Debug 应用/测试 APK 构建通过。契约见 `features/markdown-gfm-basics.md`。
  - [x] 5.7 接通可写笔记编辑器中的粗体、斜体、删除线菜单命令；保持桌面 Markdown 标记、UTF-16 选区、自动草稿恢复和 revision 保存语义。5 个格式化 JVM 用例、单项 API 34 平板 AVD 保存/预览闭环和 Debug APK 构建通过；小米平板 5 Pro 实机验收仍待 task 12。契约见 `features/markdown-inline-formatting.md`，实施见 `markdown-inline-formatting-plan.md`。
  - [x] 5.8 接通可写页笔记的项目图片插入：通过 Android 文档选择器导入到 `src/assets/`，限制 200 MiB/2400 万像素并验证 MIME 与真实解码，插入相对 Markdown 引用；取消/SAF 写入失败不改草稿，保存重开后仍可离线预览。6 个聚焦 JVM 用例、单项 Android 14/API 34 平板 AVD 导入/回滚/预览/保存仪器流程及 Debug APK 构建通过；小米平板 5 Pro 真机验收仍待 task 12。剪贴板图片粘贴已由 task 5.13 完成。契约见 `features/markdown-image-insertion.md`，实施见 `markdown-image-insertion-plan.md`。
  - [x] 5.9 接通标题 1–3、无序/有序列表和引用源码前缀格式化；字号继续禁用。按桌面源码编辑模式回退语义处理多行选区、保留 UTF-16 光标并只更新当前页草稿。8 个聚焦 JVM 用例、单项 Android 14/API 34 平板 AVD 标题/列表/引用/保存/预览仪器流程 1/1 及 Debug APK 构建通过；任务列表前缀见 task 23。小米平板 5 Pro 真机验收仍待 task 12。契约见 `features/markdown-paragraph-formatting.md`，实施见 `markdown-paragraph-formatting-plan.md`。
  - [x] 5.10 接通 Insert > Code block：复用桌面源码模式的 `python` fenced block，保留正文并选中围栏内部；折叠选区插入本地化 `Code`/`代码` 占位词，更新草稿但不自动保存。5 个聚焦 JVM 用例、单项 Android 14/API 34 平板 AVD 阅读/保存/预览仪器流程 1/1 及 Debug APK 构建通过；小米平板 5 Pro 真机验收仍待 task 12。契约见 `features/markdown-code-block-insertion.md`，实施见 `markdown-code-block-insertion-plan.md`。
  - [x] 5.11 接通 Insert > Formula：精确复用桌面源码模式 `$$` 块公式，保留所选 TeX 并选中正文；折叠选区插入并选中 `E=mc^2`，草稿仅更新当前页。5 个聚焦 JVM 用例、唯一 API 34 平板 AVD 编辑/保存/真实 LaTeX 预览仪器流程 1/1 及 `:app:assembleDebug` 通过；AVD 仅作代理，小米平板 5 Pro 实机验收仍待 task 12。契约见 `features/markdown-formula-insertion.md`，实施见 `markdown-formula-insertion-plan.md`。
  - [x] 5.12 接通 Insert > Table：沿用桌面源码模式双列表格模板，以模板替换选区并把光标置于末尾；草稿只更新当前页，通过 GFM renderer 预览。4 个聚焦 JVM 用例、唯一 API 34 平板 AVD 编辑/保存/TableRowSpan 预览流程 1/1 及 `:app:assembleDebug` 通过；AVD 仅作代理，小米平板 5 Pro 实机验收仍待 task 12。契约见 `features/markdown-table-insertion.md`，实施见 `markdown-table-insertion-plan.md`。
  - [x] 5.13 接通 Insert > Paste image：仅在用户显式点击时读取剪贴板，复用现有大小/像素限制和 SAF 回滚，缺少显示名时按 MIME 安全回退；当前页草稿插入相对引用，保存后可离线重开。2 个聚焦 JVM 用例、单项 Android 14/API 34 平板 AVD 无图拒绝/无名 URI 导入/预览/保存流程 1/1 及 Debug APK 构建通过；AVD 仅作代理，小米平板 5 Pro 验收仍待 task 12。契约见 `features/markdown-clipboard-image-paste.md`，实施见 `markdown-clipboard-image-paste-plan.md`。
  - _Requirement: 阅读与笔记_

- [x] 6. 标注与 AI 标注工作台（数据模型、SAF 边界、文字/区域批注、overall review、handoff、selection review 与同轮 Agent 应用授权已落地；验收限制见相关契约）
  - [x] 实现 annotation、AI review、handoff 数据模型、校验和本地 store 单测。
  - [x] 通用标注 SAF 数据边界已落地（`ProjectAnnotationStore` 全类型读写/删页，`AnnotationSafBoundary` 纯函数，与笔迹写共存；契约见 `features/annotation-saf-boundary.md`）。
  - [x] 文字批注创建/列表/删除 UI 已落地（批注模式点选落点、文本输入、全量页写回、非笔迹列表与删除；契约见 `features/annotation-text-ui.md`）。
  - [x] 6.6 Handwriting > Text 专用点选批注模式（桌面兼容 `type=text`；区别于通用文字/区域 Annotate 模式）；空项目和 Markdown 阅读态禁用，有可写 PDF 时启用。API 34 平板 AVD 单项闭环 1/1、app JVM 测试及 `:app:assembleDebug` 通过，覆盖取消不落盘、纯文字 dialog、宽度 1000 坐标、当前页 SAF 保存与 overlay 刷新。小米平板 5 Pro 真机/手写笔仍待 task 12。契约见 `features/pdf-text-annotation-tool.md`，计划见 `pdf-text-annotation-tool-plan.md`。
  - [x] 6.7 Handwriting > Insert Image：复用 SAF 项目资源导入，在 PDF 当前页创建桌面兼容 `/attachments/<uuid>` `type=image` 批注，离线映射到项目 `src/assets/` 并绘制 overlay；7 个聚焦 JVM 用例、API 34 平板 AVD 的 Activity 3/3 与 overlay 2/2、`compileDebugAndroidTestKotlin` 和 `assembleDebug` 通过。AVD 仅作代理，小米平板 5 Pro/手写笔真机验收仍待 task 12。契约见 `features/pdf-image-annotation.md`，计划见 `pdf-image-annotation-plan.md`。
  - [x] 区域批注创建/列表/删除 UI 已落地（批注模式 rect 对角落盘、零面积拒绝、空文本允许、列表可删；契约见 `features/annotation-region-ui.md`）。
  - [x] 6.1 PDF `rect`/`text` 批注按桌面宽度坐标叠加绘制，随页面缩放/平移并在创建/删除后刷新；兼容项目打开后新建的 `annotations.json`。两个聚焦 JVM 测试类、API 34 平板 AVD 两项仪器测试、Debug 应用与测试 APK 构建通过；仅目标 AVD 验证，实机仍待 task 12。契约见 `features/pdf-annotation-overlay.md`。
  - [x] 6.2 PDF `line`/`arrow`/`ellipse` 按桌面坐标绘制，箭头几何、反向椭圆边界、单点回退及异常坐标有覆盖；两个聚焦 JVM 测试类、API 34 平板 AVD 三项 overlay 仪器测试、Debug 应用与测试 APK 构建通过。实机仍待 task 12；契约见 `features/pdf-vector-annotation-overlay.md`。
  - [x] 6.3 PDF `line`/`arrow`/`rect`/`ellipse` 拖拽创建，包含视口逆变换、实时预览、SAF 合并写入、失败清理预览和列表删除；mapper/draft 聚焦 JVM 测试通过，单项 Compose 拖拽与像素测试在唯一 API 34 平板 AVD 通过，Debug 应用/测试 APK 构建通过。小米平板 5 Pro 实机验收仍待 task 12；契约见 `features/pdf-vector-annotation-authoring.md`。
  - [x] AI review 列表与 overall 创建、dismiss、删除已落地；`applied` 受同轮实际改写授权，见 `features/review-handoff-ui.md`。
  - [x] 当前页 Markdown 编辑区可创建带 quote/context/revision/UTF-16 anchor 的 selection review，Agent 可读且仅在同轮成功修改目标页后应用。最终审查硬化已落地：超限选区显示本地化原因并在扫描/物化文本前快速拒绝；畸形 UTF-8 在写入前拒绝；Agent review JSON/详情/列表元数据均有限额；SAF 在支持时恢复部分写入，成功写入后清理失败会报告保留副本名，首次初始化清理双重失败会提示不完整文件残留风险。新鲜 app/Agent JVM 测试 175/175、56/56；四个聚焦仪器测试类在 API 34 4 KB、API 35 4 KB 与 API 35 16 KB AVD 各通过 33/33；Android 测试源码编译和 debug APK 构建通过，独立审查无遗留 findings。实现已提交并推送至个人 fork 的 `codex/android-port`（`cff73e4`）。完整 Activity 打开项目和 dirty-save 失败交互未端到端测试，PDF/Office 文字选择未实现，实体设备/手写笔未测试。SAF 仅应用进程内串行化，不提供跨进程 CAS；未保存草稿进程回收恢复已在 task 15 单独实现并测试。契约与结果见 `features/note-selection-ai-review.md` 及实施计划 `note-selection-ai-review-plan.md`。
  - [x] 6.4 既有 `annotations.json` 的批注写入/删页接入可恢复 SAF 写入器；覆盖中断时回滚原文，双重失败保留并报告恢复副本，成功写入但清理失败时在状态栏报告副本名。API 34 定向仪器回归 5/5、app JVM 测试及 Debug 构建通过；契约见 `features/annotation-saf-write-recovery.md`。
  - [x] 6.5 Android 14 searchable PDF 文字拖选与 AI review 已落地；PDFBox-Android 仅在进入选区时抽取文字/字形几何，每 PDF session 缓存最近三页，渲染继续使用 PdfRenderer。核心映射与 Agent 授权 JVM 定向测试通过；唯一 API 34 平板 AVD 的一条真实阅读器闭环 1/1，覆盖脏笔记保存、非默认缩放/平移锚点、90°旋转/裁剪页、进程内并发笔记写入串行化及 stale revision 拒绝；Debug APK 构建通过。桌面 `ai-workspace` 验证 quote 不匹配时以 width-1000 shape 回退高亮。验证仅限相关测试，不增加设备/API 矩阵；小米平板 5 Pro/MIUI/实体笔验收随 task 12 完成。外部 SAF 写入方不受应用进程锁保护。契约与最小测试面见 `features/pdf-text-selection-ai-review.md`，实施单见 `pdf-text-selection-ai-review-plan.md`。
  - [x] 交接读写 UI 与 stale 处理已落地（本页交接预览、stale 警告、过期 revision 拒绝覆盖；契约见 `features/review-handoff-ui.md`）。
  - _Requirement: 标注_

- [x] 7. 手写笔迹（原生 View、压感、分页编辑、可恢复 SAF 保存、撤销/重做/清除均已落地，真机基础书写/侧键验收待 task 12）
  - [x] 原生 View 笔/荧光笔/橡皮、stylus 侧键与硬件橡皮输入、撤销、重做、按页清除。
  - [x] 笔迹按页存取并保留 annotations.json 中的其他批注、页面和未知字段。
  - [x] 7.1 手写菜单颜色/宽度已接入：支持 8 个色板、有效 `#RRGGBB` 自定义色与桌面四档宽度，应用于新建笔/荧光笔及 PDF 线条、形状、文字/区域批注。聚焦 style/vector/text/region JVM 类通过；API 34 平板 AVD 上单项 Compose 测试通过；Debug 应用/测试 APK 构建通过。小米 Pad 5 Pro 实体笔体验仍待 task 12；契约见 `features/pen-style-controls.md`。
  - [x] 主动笔/被动笔兼容与误触抑制逻辑已落地（平台 palm rejection + 单指针锁定 + ACTION_CANCEL 丢弃）；边缘、断触、延迟真机验证待 task 12。
  - [x] 原生 View 仪器测试覆盖合成笔迹坐标/压力、取消/禁用输入和侧键 generic-motion 橡皮擦；3/3 在 API 34 4 KB、API 35 4 KB 与 API 35 16 KB AVD 各通过。该结果只验证应用事件逻辑，实体手写笔的压力、误触和延迟仍待 task 12。
  - [x] 7.2 保留被修改页面中仍存在的笔迹对象未知扩展字段；删除/清空笔迹时随对象一并删除，不允许扩展字段覆盖标准笔迹字段。回归验证含嵌套字段读写、删除及标准字段优先；app 聚焦 JVM 测试、`feature-pen` 全套 JVM 测试和 Debug APK 构建通过。契约见 `features/pen-project-integration.md`，实施见 `pen-extension-field-preservation-plan.md`。
  - [x] 7.3 项目打开后才创建 `src/annotations.json` 时，已存在的 `ProjectPenStore` 仍须发现并复用该文件；API 34 平板 AVD SAF 回归 3/3、相邻写入恢复测试 5/5 通过，并在 69 页项目独立副本上通过批注→笔迹→进程重启重读，目录仅保留单个 `annotations.json`。小米 Pad 5 Pro 实体笔验收仍待 task 12；契约见 `features/pen-late-annotations-resolution.md`，实施见 `pen-late-annotations-resolution-plan.md`。
  - _Requirement: 笔迹，布局与适配_

- [x] 8. 页面准备管线（原生文字/整页渲染/区域 PNG/`MarkdownTables`/`RegionOcrProcessor.refresh` 缓存刷新均已接通；此项不生成 Mermaid PNG 缓存，笔记预览实际绘图仍归 task 5）
  - [x] 原生文字/文字块抽取、整页 `PdfRenderer` 渲染、缓存 key、manifest 与 1000x1000 区域契约。
  - [x] 接通区域 PNG 生成（复用 `toPixelRect` + `createBitmap`，floor/ceil + 裁剪）、Markdown 表格抽取（`MarkdownTables`，headers/rows/source，供 Agent 上下文；区域 OCR 缓存仍为 pdf 专用）、项目缓存 `refresh` 重做（`RegionOcrProcessor.recognize(refresh=true)` 跳读强写）；契约见 `features/page-prepare-pipeline.md`。准备缓存不另存 Mermaid PNG；阅读预览绘图仍按 task 5 验收。
  - _Requirement: 页面准备与 OCR_

- [x] 9. 离线 OCR（内置 ONNX 模型、失败/低置信度警告及离线行为已接通；自动触发包含原生文本不可用/为空、图多字少和疑似乱码回退。历史 AVD 模型验证见 `features/offline-ocr.md`；当前仅目标小米平板 Android 14 验收有效）
  - [x] 原生 OCR 引擎在 API 34、API 35 4 KB 和 16 KB ARM64 AVD 各通过 7 项 instrumentation；中英文识别、区域坐标、原图所有权和失败语义均有覆盖。
  - [x] 中英文整页与区域识别、坐标、置信度、警告、原图保留。
  - [x] 触发规则（`OcrTrigger`：不可用/空/图多字少/疑似乱码时自动补 OCR，Agent 循环内不触发，page tutor 统一接入）、超时处理、失败明确标记、严格离线可用。
  - [x] 区域重识别 API 与全页坐标映射，契约见 `features/ocr-region-recognition.md`。
  - [x] 阅读器区域选择、离线识别与 SAF 项目缓存；JVM 回归、仪器测试编译、四 ABI APK 构建及 16 KB 对齐检查通过，真机手写笔/启动验收待完成，契约见 `features/reader-ocr-selection.md`，实施拆分见 `reader-ocr-selection-plan.md`。
  - _Requirement: 页面准备与 OCR_

- [x] 9.1 区域 OCR 直接发起 Page Tutor（复用已识别的选区文本、置信度和警告；不重复整页 OCR；不自动改写笔记，详见 `features/ocr-region-page-tutor.md`）
  - [x] 有文本的成功/部分结果才显示入口；失败与空结果隐藏入口。
  - [x] 区域请求仅携带已识别文本与目标页笔记，不触发整页或邻页 OCR；Agent 测试确认 OCR 警告保留且全页/邻页材料不发送。
  - [x] 结果保留预览和显式追加，使用目标页 revision 写入保护；入口、上下文、预览展示与启动烟测在 API 34 平板 AVD 定向通过。
  - _Requirement: 页面准备与 OCR、Agent 与模型_

- [x] 10. 端侧 Agent 与 OpenAI 兼容接入（客户端、任务循环、读写工具、单页/批量任务 UI 均已落地，真模型与真机待 task 12）
  - OpenAI 兼容客户端，支持 BaseURL/Key/模型名配置，默认可配本地中转与指定模型。
  - [x] Android 模型设置 UI、Keystore 加密 Key 存储与本地中转可达性说明，契约见 `features/openai-model-settings.md`，实施拆分见 `openai-model-settings-plan.md`；debug/release 网络策略和本地构建验证通过，设置导航及 Keystore 仪器测试已在 API 35 AVD 执行通过。
  - [x] 单页讲解预览和显式追加，按页 OCR 回退、相邻页上下文、20,000 字符截断、旧提示词预览禁用及 SAF 写入恢复/revision 冲突保护；SAF 父目录/能力查询异常或备份准备失败时 fail closed，明确不支持备份才使用回滚兜底，清理失败报告残留名称；契约见 `features/page-tutor-preview-and-append.md`，单测、APK 构建及 16 KB 检查通过，Compose 预览交互仪器用例已在 API 35 AVD 通过。
  - [x] 只读项目工具已接入（read_page/read_review/read_handoff：笔记+原生文字+revision/记录摘要/stale，异常转 ok=false 不中断循环；契约见 `features/agent-project-tools.md`）。
  - [x] 写工具已接入（write_note/patch_note/write_review_status/write_handoff：revision 必填，冲突转 ok=false 不覆盖并报告 updatedPage；契约见 `features/agent-write-tools.md`）。
  - [x] 单页任务 UI 闭环已接入（工具栏 Agent 任务入口、当前页上下文组装、工具循环、Done/Failed 范围报告、缺配置开模型设置、当前页被改刷新；契约见 `features/agent-task-run.md`）。
  - [x] Android 14 PDF Agent 上下文准备复用离线 OCR，覆盖当前页和相邻页并向模型传递 OCR 警告；13 项聚焦 JVM 测试、API 34 平板 AVD 的 6 项 Agent 对话框仪器测试、仪器源码编译及 Debug APK 构建通过。真实模型服务和小米真机 OCR 闭环待 task 12，契约见 `features/agent-task-run.md`。
  - [x] 10.2 Page Tutor 在 Android 14 优先使用 PDFBox 可搜索文字，只有页面无可用文字或稀疏文字叠加图像时才触发离线 OCR；混合页保留两种文字来源并提示核对。相关 JVM 测试、唯一 API 34 AVD 聚焦流程（可搜索页+混合页，1/1）和 Debug 构建通过；不扩展其他设备/API 测试矩阵。契约见 `features/page-tutor-preview-and-append.md`。
  - [x] 批量页任务 UI 已接入（页范围解析、逐页串行、限流即停、真实完成/修改/未完成报告；契约见 `features/agent-batch-pages.md`）。
  - [x] 10.1 未知工具拒绝原因回传模型并允许在步数预算内恢复；未知调用的页码不计入触达/完成范围。AgentRunner 聚焦恢复回归、完整 agent JVM 套件和 Debug APK 构建通过；契约见 `features/agent-toolset-scope.md`。
  - 任务循环、本地工具收敛集、多步上限、revision 冲突处理、实际完成范围报告。
  - 提示词索引与按 revision 分批读，逐页独立请求加相邻源页。
  - 单页串行汇总只报告真实完成/修改的请求页；限流停止，普通失败继续。
  - 密钥不落项目目录，未配置明确提示。
  - _Requirement: Agent 与模型_

- [x] 11. 设置、语言、主题（DataStore 持久化、设置面板、运行时主题/语言/比例/动画均已落地，真机待 task 12）
  - 主题、语言、双栏比例、动画开关持久化。
  - 语言键语义复用桌面 `locales/catalog.json`。
  - [x] 原生设置面板与运行时生效，契约见 `features/reader-settings-ui.md`。
  - _Requirement: 布局与适配_

- [x] 12. Android 14 平板功能验收（产品目标为小米平板 5 Pro / 骁龙 870；单一 API 34 平板 AVD 可替代常规功能验收。此前 API 35/16 KB/其他 AVD 结果为历史证据，不是当前门槛）
  - [x] 小米平板 5 Pro / Android 14 实机：从应用打开 SAF 目录选择器并取消，返回应用且空项目状态不变；通过 SAF 打开隔离的 69 页副本，在第 31 页修改、保存、重载。重读副本确认唯一变化是第 31 页的验收标记，第 30/32 页不变，桌面原件只读未改。
  - [x] API 34 平板 AVD 批注/笔迹闭环：`PdfAnnotationSelectionClipboardActivityTest` 2/2、`InkSelectionClipboardActivityTest` 1/1；覆盖原生形状、笔迹保存/回读、复制/粘贴、移动/缩放、删除及撤销/重做。先前选择超时的根因是测试在原生画布切换 Select 前发出触摸，且用宽度比例构造了落在 PDF 页外的 y 坐标（映射 y=-77.33）；测试现等待工具同步，并以 PDF 页坐标经 `PenCanvasGeometry` 投影。出问题用例连续两次通过。用户实测主动笔基础书写与压感正常；侧键橡皮擦未单独测试，明确为非阻塞未验证硬件行为。
  - [x] 第 16 页图像页离线 OCR：包内 `pp-ocrv6-tiny-onnx` 返回 `ok` 和 13 个结果块；该页仅有 20 个原生文本字符并含大幅嵌入图像。Android 私有模型配置文件不存在，未配置 API Key，因此按条件未请求 AI 页讲解。
  - [x] PDF 线条批注和一条笔迹写入隔离项目；冷重开后 `annotations.json` 与重开前 SHA-256 完全一致。用户在 Xiaomi Pad 5 Pro 实测主动式手写笔书写及压感整体正常；此次自动化持久化动作使用 ADB 触控模拟，不代表自动化测过压感。
  - [ ] 手写笔侧键橡皮擦未单独测试；不影响用户已报告的基础书写与压感正常，也不作为常规功能验收阻塞。
  - [x] 旧版冷启动重开项目后跳转第 31 页曾出现系统输入超时；按 task 30 在 API 34 AVD 完成 69 页合成项目冷启动、真实 IME 跳页、编辑保存和再次重开，未再出现 ANR。小米实机未重测当前修复版，见 `features/large-markdown-page-layout.md`。
  - [x] API 34 平板 AVD 安装 Debug 包并完成 PDF 批注、笔迹、分页笔记保存/回读、Recent 重开和 OCR 代表性闭环。附件和使用说明命令已分别在 task 32/33 接通。Office 本机编辑按 Android 移植范围回桌面端处理，不属于本验收门槛；任何未实现命令不得归因于 Android 版本。
  - 只确认上述闭环，不抽查多页或多种文档，不测压力曲线、延迟、边缘性能、电量、长时间稳定性，也不要求无端点配置时验证云请求。
  - 日常只运行受影响模块的直接相关契约/JVM 测试与 Debug 构建；UI/平台改动才在唯一 API 34 平板 AVD 上跑聚焦仪器测试。无需每次重跑完整套件；Markdown 预览使用 task 5.5 的单项测试和一张截图。
  - 不运行手机、其他 OEM、API 35 或 16 KB 设备矩阵。16 KB 页大小及公开商店合规检查仅在目标 SDK/商店政策触发时作为独立发布门禁。
  - _Requirement: 构建与测试，全部 EARS_

<details>
<summary>历史验证记录（已完成；仅供审计，不是当前重跑门槛）</summary>

- [x] 保留本地化 Compose 下的 Activity Result owner；应用单测、instrumentation 编译与 APK 编译通过；最终 arm64 APK 真机重装后冷启动返回 `Status: ok`、338 ms，未见应用 fatal 崩溃日志。
- [x] 切换到项目自建 OpenCV 4.10.0 AAR，并通过全部 ABI APK 的 ELF/ZIP 16 KB 检查；原 4.14.0 `RDVL`/SIGILL 已解决。
- [x] 当前连接设备页大小为 4096 bytes；APK 可安装并启动，但不作为 16 KB 运行时验证。
- [x] 已安装官方 Android 15 `google_apis_playstore_ps16k` ARM64 镜像并建立 `Pixel_Tablet_16KB`；`getconf PAGESIZE=16384`，应用启动正常。初始 71 项套件记录属于早期验收；最新 107 项跨 AVD 矩阵见下方记录。
- [x] 修复 OCR 测试 runner 曾静默运行 0 tests 的问题，并以项目自建 OpenCV 4.10.0 AAR 解决旧版 `RDVL`/SIGILL；API 34、API 35 4 KB/16 KB 的模型识别测试均通过。API 35 16 KB AVD 的严格断网测试由 `scripts/test_offline_ocr_avd.sh` 验证。
- [x] 既有 v1 `.pagenote` 项目的隔离 SAF URI 往返测试：生成 69 页 PDF 按第 1/35/69 页正确渲染，修改第 35 页后重开并确认邻页笔记未变；过期 revision 不覆盖新内容。两项仪器用例在 API 34、API 35 4 KB 和 API 35 16 KB AVD 全部通过，详见 `features/existing-project-saf-roundtrip.md`。
- [x] 用户已完成讲解的 69 页课件项目通过真实 DocumentsUI 选取与持久 SAF tree 授权，在 API 35 4 KB 和 16 KB 平板 AVD 均打开并报告 `1 / 69`；4 KB AVD 的第 31 页 PDF、Markdown、LaTeX 和项目相对图片已做实际预览检查。原桌面文件只读，导入测试副本未提交进仓库，详见 `features/existing-project-saf-roundtrip.md` 与 `features/project-assets-saf.md`。
- [x] 用户 69 页项目第 31 页真实 SAF 笔记保存/应用内重载在 API 35 4 KB 与 16 KB 平板 AVD 通过；第 30/32 页未变，桌面基线未改，模拟器副本已恢复，详见 `features/page-notes.md`。
- [x] 最新完整 JVM 回归 370/370 通过，`assembleDebug` 成功；四 ABI debug APK 的 ELF LOAD 与 ZIP alignment 16 KB 检查通过，arm64 OpenCV 不含 RDVL。该构建验证不关闭本任务中的手动设备验收。
- [x] 最新 `:app:connectedDebugAndroidTest` 完整仪器套件 107/107 通过：Android 14 AVD (4 KB)、Android 15 Pixel Tablet AVD (4 KB)、Android 15 AVD (16 KB) 均为 0 failures / 0 skipped。覆盖 Activity 启动与设置导航、Compose 对话框、Markdown renderer、项目/批注/Agent/OCR 选择组件、自适应菜单及后续 Activity 闭环回归；SAF fixture 不等同于系统 picker/真实持久授权，也不覆盖实体手写笔、真模型服务或全部手动交互验收。设备页大小由 `getconf PAGESIZE` 核实；本轮未修改 USB/安装安全设置。
- [x] `:feature-pen:connectedDebugAndroidTest` 原生 View 笔迹测试 3/3 在 API 34 4 KB、API 35 4 KB、API 35 16 KB AVD 全部通过；包括 generic-motion 侧键橡皮擦。该项不替代实体笔硬件验收。
- [x] 原生 `PenCanvasView` 仪器测试 3/3 在 API 34 4 KB、API 35 4 KB 与 API 35 16 KB AVD 各通过，覆盖合成压感笔迹、取消/禁用输入及两个平台侧键映射经 `dispatchGenericMotionEvent` 触发橡皮擦。仍需实体手写笔验证硬件压力、掌拒、延迟及具体按键映射。
- [x] 选区 AI 审阅的 `MainActivity` 成功保存与 revision 冲突中止闭环（真实菜单 handler + 原生编辑器 + SAF fixture），新增 2 项仪器测试在 API 34 4 KB、API 35 4 KB、API 35 16 KB 平板 AVD 各通过 2/2；系统 picker/真实持久授权另行验收，实施计划见 `note-selection-ai-review-plan.md` task 7。
- [x] SAF 新建项目在 Xiaomi 真机端到端创建并直开通过；持久权限沿用父 tree、manifest 文件名不追加扩展名、picker 返回后弹窗保留，见 `features/project-create.md`。
- [x] SAF 打开既有项目及第 31 页笔记保存/重载已通过真实 DocumentsUI 和用户 69 页项目副本验收；task 12 仅剩上述小米平板目标机的合并核心流程。
- [x] 用户导入的 69 页项目 API 34 AVD 独立副本复现“打开项目后先创建 PDF 批注、再写笔迹”顺序；旧版产生 `annotations (1).json`，修复后 PDF `text` 与 `pen` 共存于单个 `annotations.json`，应用重启后项目仍恢复第 1 页并可读盘。原始项目/基线副本未改；此 AVD 证据不代替小米 Pad 5 Pro 实体手写笔验收。
- [x] `ProjectCreatorSafTest` 在 Pixel Tablet API 34 AVD 安装并通过（1/1）；Xiaomi MIUI 上的 `INSTALL_FAILED_USER_RESTRICTED` 仍是设备限制，SAF Provider 创建/打开行为已由真机手工端到端验证。
</details>

- [x] 13. 分支、变更日志、PR 准备（分支基于 beta、CHANGELOG 随推更新、产物独立于 release/，待开 PR）
  - `codex/android-port` 基于 `beta`，PR 目标 `beta`。
  - 每次推送附 `CHANGELOG.md` 未发布区条目。
  - 安卓产物独立，不混入桌面 `release/`。
  - _Requirement: 构建与测试_

- [x] 14. 桌面菜单分组与自适应导航（平板常驻可折叠侧栏、手机模态抽屉、桌面分组与已有 handler 已接通；API 35 Pixel Tablet AVD 已做布局检查）
  - 窄窗口使用模态抽屉，宽窗口使用常驻可折叠侧栏，断点按窗口宽度而非设备型号判断。
  - 保留桌面菜单分组与顺序；已支持命令复用现有 handler，未接线功能明确显示禁用原因。
  - Compose 回归测试确认菜单开合不重置工作区状态；实际项目内旋转/断点变化的整机验收仍需在 task 12 补做。
  - _Requirement: 布局与适配、桌面菜单语义一致_

- [x] 15. 阅读器会话恢复（经用户确认，Activity 重建与 Android 系统回收进程后均须恢复项目、页码、归一化视口、笔工具和未保存笔记草稿；不更改项目文件格式。模拟器 SIGKILL 任务恢复烟测已通过；自然低内存回收与真机验证仍属于 task 12 的环境验收）
  - [x] Activity 级 `ReaderSessionViewModel`、紧凑 `SavedStateHandle` 检查点、归一化 PDF 视口书签和按 SAF 目标/项目/页隔离的 app-private `AtomicFile` 草稿日志已实现。
  - [x] JVM 单测、AtomicFile 仪器测试、Activity 重建与 fresh-ViewModel 恢复/版本冲突测试已实现；Pixel Tablet API 34、API 35 各 69 项 app instrumentation tests 通过。
  - [x] `:app:assembleDebug` 通过；四 ABI debug APK 均通过 16 KB 对齐检查。
  - [x] Pixel Tablet API 34 AVD 通过 DocumentsUI 授予持久 SAF 目录权限，编辑未保存草稿后将 Activity 退到后台，并以应用 UID 执行 SIGKILL；重新调回原任务后项目和草稿均恢复，界面显示本机草稿恢复状态。此验证覆盖进程死亡与任务恢复，不代表自然低内存杀进程或真机测试。
  - _Requirement: 阅读器状态恢复、草稿持久性与冲突保护_

- [x] 16. 阅读器直接页码跳转（顶栏页数控件、输入校验、脏笔记 revision 保存与目标页状态加载；契约见 `features/page-jump.md`，实施拆分见 `page-jump-plan.md`）
  - 只能跳转至 `1..totalPages` 的整数页码；输入无效时禁用确认。
  - 脏笔记须先按捕获的页 revision 保存；冲突时保留当前页和草稿。
  - 成功跳转后，目标页笔记、笔迹及其 revision 基线正确，视口复位。
  - `PageJumpPolicyTest` 3 项通过；真实 `MainActivity` 的页码校验、脏笔记保存/冲突保留、目标页笔迹/视口加载及保存中防重入测试在 API 34 / 4 KB、API 35 / 4 KB、API 35 / 16 KB 平板 AVD 各通过 3/3。
  - 新鲜 app/Agent JVM 测试 178/178、56/56；Android instrumentation Kotlin 编译和 `assembleDebug` 通过；四 ABI APK 通过 16 KB 对齐检查。
  - _Requirement: 阅读与笔记，桌面页码导航语义一致_

- [x] 17. 笔记导出（`File > Export notes` 支持桌面 v1 整份 JSON 备份和当前页完整 Markdown，通过 SAF 选择输出位置；不写项目源文件）
  - 脏可写页先走既有 revision 保存；只读项目中的恢复草稿只覆盖导出快照，不写回项目。
  - 验证单个聚焦 JVM payload 测试、一个 Android 14/API 34 平板 AVD 导出闭环和 `:app:assembleDebug`；不扩展其他设备/API 矩阵。
  - 笔记导入独立切片已完成，契约见 `features/note-import.md`，实施记录见 `note-import-plan.md`。
  - 契约见 `features/note-export.md`，实施拆分见 `note-export-plan.md`。
  - _Requirement: 桌面菜单分组与数据导入/导出一致性_

- [x] 18. 笔记导入（`File > Import notes` 支持桌面兼容 JSON 按页导入和当前页 Markdown/TXT 插入；只写笔记文档）
  - JSON 导入确认后跳过非法/越界条目，空字符串清空对应页；未列出的页面保持不变，整批以单次 revision-guarded SAF 写入提交。
  - Markdown/TXT 替换当前编辑选区；若处于预览模式，切换到编辑并插入当前页末。
  - 聚焦 JVM 测试、唯一 Android 14/API 34 平板 AVD 闭环和 `:app:assembleDebug`；不扩展设备/API 矩阵。
  - 契约见 `features/note-import.md`，实施拆分见 `note-import-plan.md`。
  - _Requirement: 桌面菜单分组与数据导入/导出一致性_

- [x] 19. 新建项目可选 PDF 原文（新建窗口保留目录必选、PDF 可选；选中的 PDF 复制进 v1 项目并作为原文直接打开）
  - 空白页 PDF 可正常创建；未选择 PDF 时仍生成当前 Markdown 空白项目。
  - 2 个聚焦 JVM 测试、6 个 Android 14/API 34 平板 AVD 对话框/创建流程测试、`:app:assembleDebug` 通过；真机验收仍按 task 12。
  - 契约见 `features/project-create-optional-pdf.md`，实施拆分见 `project-create-optional-pdf-plan.md`。
  - _Requirement: 项目与数据兼容、PDF 阅读与笔记_

- [x] 20. Markdown 编辑器剪切/复制/全选（操作当前页原文和系统剪贴板；剪切更新草稿，保存继续按 revision 保护）
  - 剪切/复制只在有非空选区时启用，全选只在可写编辑器可用时启用；预览、只读、无项目和忙碌时禁用。
  - `menuClipboardCommandsCopyCutSelectAllAndSaveOnlyCurrentPage` 在唯一 Android 14/API 34 平板 AVD 通过（1/1），覆盖反向 UTF-16 选区、剪贴板、草稿、全选、预览门控及两页保存；`:app:testDebugUnitTest` 与 `:app:assembleDebug` 通过。小米平板 5 Pro 实机验收仍归 task 12。
  - 契约见 `features/markdown-clipboard-editing.md`，实施拆分见 `markdown-clipboard-editing-plan.md`。
  - _Requirement: 桌面菜单语义一致、阅读与笔记_

- [x] 21. Markdown 编辑器按 Markdown 粘贴（显式点击读取剪贴板纯文本，替换选区或插入光标处；剪贴板 HTML/图片不属于此命令）
  - 空剪贴板显示提示且草稿不变；编辑操作沿用原生 watcher、页级 revision 保存和相邻页隔离。
  - `pasteMarkdownUsesClipboardReplacesSelectionAndInsertsAtCaret` 在唯一 Android 14/API 34 平板 AVD 通过（1/1），覆盖空剪贴板、反向 UTF-16 选区替换、光标插入、草稿 watcher 和两页保存；`:app:testDebugUnitTest` 与 `:app:assembleDebug` 通过。小米平板 5 Pro 实机验收仍归 task 12。
  - 契约见 `features/markdown-clipboard-paste.md`，实施拆分见 `markdown-clipboard-paste-plan.md`。
  - _Requirement: 桌面菜单语义一致、阅读与笔记_

- [x] 22. Markdown 编辑器插入链接（沿用桌面源码模式 `[标签](https://example.com)`；空选区插入本地化标签占位词并选中）
  - 插入只改当前页草稿；保存仍按 revision 保护，预览复用现有 Markdown renderer。
  - `linkCommandWrapsSelectionInsertsAndSelectsPlaceholderSavesOnlyCurrentPage` 在唯一 Android 14/API 34 平板 AVD 通过（1/1），覆盖无项目/预览门控、反向选区、空选区占位词、标签 selection、两页保存和可点击链接 span；4 个 helper JVM 用例、完整 `:app:testDebugUnitTest` 与 `:app:assembleDebug` 通过。小米平板 5 Pro 实机验收仍归 task 12。
  - 契约见 `features/markdown-link-insertion.md`，实施拆分见 `markdown-link-insertion-plan.md`。
  - _Requirement: 桌面菜单语义一致、阅读与笔记_

- [x] 23. Markdown 编辑器任务列表前缀（沿用桌面源码模式逐行添加 `- [ ] `；预览 checkbox 不点按写回）
  - 复用既有段落变换、可写门控和 GFM renderer；字号另由 task 26 独立实现。
  - 9 个段落 helper JVM 用例、`paragraphCommandsPrefixSelectedLinesAndSaveOnlyCurrentPage` 在唯一 Android 14/API 34 平板 AVD 通过（1/1），覆盖预览门控、任务前缀、按页保存、相邻页隔离及未完成 `TaskListSpan`；完整 `:app:testDebugUnitTest` 与 `:app:assembleDebug` 通过。小米平板 5 Pro 实机验收仍归 task 12。
  - 契约见 `features/markdown-task-list-insertion.md`，实施拆分见 `markdown-task-list-insertion-plan.md`。
  - _Requirement: 桌面菜单语义一致、阅读与笔记_

- [x] 24. Markdown 编辑器撤销/重做（页级内存历史；500ms 合并输入，最多 80 步；新编辑清 redo）
  - 通过原生 watcher 记录键入与工具栏修改；Undo/Redo 更新当前页草稿，不自动保存，保存仍按 revision 保护。
  - 项目/页切换或编辑器重载清栈；只读/预览/无项目/忙碌/历史为空时禁用；笔迹撤销/重做保持独立。
  - 8 个 `MarkdownEditHistory` JVM 用例及完整 app JVM 测试通过；`markdownUndoRedoCommandsRestoreDraftAndSaveOnlyCurrentPage` 在唯一 Android 14/API 34 平板 AVD 通过（1/1），覆盖空历史、只读/预览门控、撤销/重做、分支编辑、文末光标和按页保存；`:app:assembleDebug` 通过。小米平板 5 Pro 实机验收仍归 task 12。
  - 契约见 `features/markdown-edit-undo-redo.md`，实施拆分见 `markdown-edit-undo-redo-plan.md`。
  - _Requirement: 桌面菜单语义一致、阅读与笔记_

- [x] 25. Markdown 编辑器下划线格式（沿用桌面 HTML `<u>文字</u>`，受限启用 Markwon 原生 UnderlineSpan）
  - 选区包裹或空选区占位均只更新当前页草稿；其他 HTML 默认处理器不启用，保存继续按 revision 保护。
  - `underlineCommandUsesDesktopMarkupAndRendersUnderlineSpan` 在唯一 Android 14/API 34 平板 AVD 通过（1/1），覆盖预览/无项目门控、既有粗体嵌套、占位选区、编辑器和预览 UnderlineSpan、其他 HTML handler 关闭及相邻页保存隔离；完整 app JVM 256 项、`:app:assembleDebug` 通过。小米平板 5 Pro 实机验收仍归 task 12。
  - 契约见 `features/markdown-underline-formatting.md`，实施拆分见 `markdown-underline-formatting-plan.md`。
  - _Requirement: 桌面菜单语义一致、阅读与笔记_

- [x] 26. Markdown 笔记字号（接通段落菜单桌面同款 12/15/18/20/24/28/32 档；作为全局 reader preference 持久化）
  - 字号应用于笔记编辑器与预览，不改笔记正文、项目格式或左侧原文阅读区；取消不保存，持久化失败保留旧值并显示错误。
  - 13 个聚焦设置 JVM 测试和唯一 Android 14/API 34 平板 AVD Activity 用例（1/1）通过；覆盖桌面七档、持久化/非法值回退、取消与应用、只读/预览门控、编辑和预览字号及 Activity 重建恢复；`:app:assembleDebug` 通过。小米 Pad 5 Pro 实机验收仍归 task 12。
  - 契约见 `features/markdown-note-font-size.md`，实施拆分见 `markdown-note-font-size-plan.md`。
  - _Requirement: 桌面菜单语义一致、阅读与笔记_

- [x] 27. 最近文档（Android SAF 适配：显示最多 12 个最近成功打开的项目，按 tree URI + root document ID 去重）
  - 私有 DataStore 保存项目元数据；重开前先保存当前脏页；移除只删历史记录，不触碰项目文件或 SAF 授权。PDF 页面替换时延后两个显示帧回收旧位图，避免仍被绘制的图片已回收。
  - 7 个聚焦存储 JVM 用例、`RecentProjectsActivityTest` 在 `Pixel_Tablet_API34` 通过（1/1）；覆盖空状态、SAF Provider 重开、脏页先保存及移除后项目保留。`:app:assembleDebug` 与 `git diff --check` 通过；小米 Pad 5 Pro 真机验收仍属 task 12。
  - 契约见 `features/recent-projects.md`，实施拆分见 `recent-projects-plan.md`。
  - _Requirement: 桌面菜单分组一致、项目管理与数据兼容_

- [x] 28. 笔迹选择与系统剪贴板（PDF 当前页单选、桌面兼容标记、粘贴/删除及页级撤销）
  - 本切片仅覆盖笔与荧光笔；矢量图形属于独立覆盖层/存储路径，不包含文字框/图片、多选或拖动缩放。
  - 6 个 feature-pen 聚焦 JVM codec 用例、真实 PDF reader 的 API 34 平板 AVD 流程（1/1）、`:app:compileDebugAndroidTestKotlin` 与 `:app:assembleDebug` 通过；流程覆盖点选、marker、粘贴坐标、删除及页内撤销/重做。小米 Pad 5 Pro 实体笔体验仍归 task 12。
  - 范围和 EARS 契约见 `features/ink-selection-clipboard.md`，实施拆分见 `ink-selection-clipboard-plan.md`。
  - _Requirement: 手写笔迹操作、桌面剪贴板数据兼容_

- [x] 29. PDF 原生批注选择与剪贴板（线/箭头/矩形/椭圆/文字/项目图片，单选、拖动、复制、粘贴、删除与页级撤销）
  - 与笔迹选择共用 Select/Copy/Paste/Delete 菜单，但普通 overlay 批注继续由 `ProjectAnnotationStore` 管理；笔/荧光笔仍走独立 `PenPageStore`。图片仅允许引用当前项目已有资源；富 HTML 行为由 task 38 补齐。
  - 契约见 `features/pdf-annotation-selection-clipboard.md`，实施拆分见 `pdf-annotation-selection-clipboard-plan.md`。唯一 Android 14/API 34 平板 AVD 聚焦验证；小米 Pad 5 Pro 实机验收仍归 task 12。
  - 8 个聚焦批注剪贴板/几何 JVM 用例、批注 Activity 流程及既有笔迹剪贴板回归（各 1/1）在 `Pixel_Tablet_API34` 通过；HTML 批注由 task 38 额外覆盖。Android 测试源码编译、`:app:assembleDebug` 和 `git diff --check` 通过。小米 Pad 5 Pro 实机验收仍未完成。
  - _Requirement: 桌面批注选择/剪贴板语义一致、项目数据兼容_

- [x] 30. 长 Markdown 页编辑布局与冷启动 ANR 回归（完整笔记含 39K+ 连续 token 时仍可交互，正文/选区/保存保持逐字符一致；API 34 AVD 可承担验收）
  - 以隔离的 69 页验收副本第 31 页复现；冷重开曾出现 pagenote 输入分发 ANR，首个可见 native 帧为 `minikin::getNextWordBreakForCache`，长 token 是待验证假设而非已确认根因。
  - [x] API 34 平板 AVD 四项聚焦 `NoteLongMarkdownEditorTest` 通过：40 KB 连续 token 布局、光标/复制映射、长文本粘贴、页跳转/编辑/保存及最近项目恢复均通过，原文精确保留且相邻页未变。
  - [x] API 34 平板 AVD 从“最近项目”真实重开 31 页 PDF 并恢复第 31 页 checkpoint；SAF 读写授权、长编辑器首轮布局/保存和相邻页隔离均通过，恢复低于 5 秒。此流程不模拟系统进程死亡。
  - [x] API 34 AVD 将最近项目恢复回归扩展到 69 页合成 PDF 与约 57 KB 的第 31 页 Markdown（含 40 KB 连续 token）；恢复、首轮布局、编辑和按页保存通过，恢复/布局低于 5 秒。未使用原用户 PDF，也不模拟系统进程死亡。
  - [x] 修正 PDF 位图替换后的 worker-thread 延迟回收：`Choreographer` 调用现派发至主 Looper，继续等待两个显示帧；无 Looper instrumentation 回归通过 1/1。
  - [x] API 34 平板 AVD 真正 `force-stop` 后冷启动，从最近项目重开隔离的 69 页项目，经真实页码对话框和显示中的软键盘跳转第 31 页；输入法收起后长页在 5 秒内可交互，无 ANR 对话框或新 `Input dispatching timed out`。编辑并保存 `COLDSTART_PROBE_20261020` 后 Provider 回读为 69 页，第 31 页 57,337 字符且标记在末尾，第 30/32 页保持原文；再次冷启动和跳页后标记仍在。冷启动 Activity 分别为 661 ms 与 750 ms。小米 Pad 5 Pro 实机未在本次验收。
  - SPEC：`features/large-markdown-page-layout.md`。仅运行相关 Android 14 AVD 回归、受影响 JVM 测试和 Debug 构建；不要求实体设备。
  - _Requirement: 阅读与笔记、阅读器会话恢复_

- [x] 31. Android 14 菜单禁用原因准确性（不再把尚未接入的应用命令归因于系统版本）
  - 通用未接入命令使用中性原因；附件管理、复杂 Office 编辑、Save & Return 显示各自准确的移动端范围/桌面会话原因。
  - `MainActivityLaunchTest.readerCommandsAreVisibleUnderDesktopMenuGroups` 在 API 34 平板 AVD 通过，覆盖禁用命令和英文/简体中文资源；`:app:testDebugUnitTest`、`:app:assembleDebug` 及 Android 测试源码编译通过。
  - 契约见 `features/adaptive-menu-navigation.md`；本任务只修正提示，不代表附件或 Office 编辑能力已移植。
  - _Requirement: 桌面菜单分组一致、Android 14 全功能目标_

- [x] 32. 项目附件管理（浏览 `src/assets`、通过 SAF 导入桌面兼容资源、插入可离线显示的图片引用、安全删除未引用资源）
  - 附件目录和文件列表从当前项目 SAF tree 读取；导入不得覆盖既有资源或写入来源绝对路径，取消/失败不得修改笔记。
  - 资源被任一页 Markdown 或 PDF 图片批注引用时禁止删除；删除未引用资源须二次确认，Provider 失败时明确报告。
  - SAF store/菜单/对话框/插图/引用保护删除已接入。聚焦 JVM 策略测试、API 34 AVD Compose 删除确认用例、测试源码编译及 Debug 构建通过；按用户允许的弱化测试范围，未跑 Picker/Provider 端到端，不声称已验证导入或 Provider 故障闭环。
  - 契约见 `features/project-attachments.md`，实施拆分见 `project-attachments-plan.md`。
  - _Requirement: 项目与数据兼容、阅读与笔记_

- [x] 33. Android 帮助菜单（关于、键盘快捷键、隐私/开源许可、诊断信息）
  - 每个帮助命令打开本地原生内容；诊断信息仅在用户显式操作后复制/分享，不自动上传、不包含模型密钥或笔记正文；接通桌面同款外接键盘翻页/保存快捷键。
  - 四个本地帮助页面已接入；诊断快照仅含 app/device/source type/page count，并由显式复制/分享动作输出。快捷键策略 JVM 测试、聚焦 API 34 Compose 用例 1/1、Android 测试源码编译及 Debug 构建通过；未启动系统分享目标应用。
  - _Requirement: 布局与适配、模型隐私_

- [x] 34. 双栏分隔线拖动调比例
  - 在平板左右并列阅读区提供可触控/手写笔拖动的分隔线，实时调整原文与笔记比例，限制在 20%–80%。
  - 拖动结束后持久化并同步设置页比例；拖动中不重建项目、不重置 PDF 视口或笔记草稿。
  - 比例策略聚焦 JVM 测试和 API 34 平板 AVD 实际拖动/释放持久化回调 1/1 通过；设置页仍可作为备用调节入口。
  - _Requirement: 布局与适配_

- [ ] 35. Android 14+ 功能一致性与桌面命令清单
  - 对照桌面 `index.html`/`src/main.js` 完成命令到 Android 行为清单；终态移除 `pendingCommand()` 占位项。
  - 以 API 34 为最低版本，平台 API 差异由兼容实现吸收，不按 OS 版本隐藏功能；PDF 文字提取现已在 API 34 走本地 PDFBox、API 35 走系统接口，保持用户能力一致。
  - 静态核对已确认除 `office_edit`/`save_return` 外，菜单命令均有 Android handler；PDF HTML 批注渲染/剪贴板差异另由 task 38 跟踪。最终对齐仍待所有 gap 实现后复核。
  - [x] 桌面命令清单 SPEC 与 JVM 校验已落地：`features/desktop-command-checklist.md` 记录全部命令 id 与行为，`DesktopCommandChecklistTest` 校验代码中的已接线与待办命令均在 SPEC 出现，且 `pendingCommand()` 仅保留 `office_edit` 与 `save_return`。`new_project` 已明确记录其目录选择与可选 PDF 导入行为。
  - 只验证受影响的用户流程与 API 34 AVD，不扩展 API 版本矩阵。
  - _Requirement: Android 版本与功能范围、桌面功能区与自适应导航_

- [ ] 36. 本地 Office 引擎与桌面格式编辑
  - 支持 `.doc/.docx/.ppt/.pptx` 项目源，在 pagenote 进程内离线阅读/编辑并保存回原格式；不调用外部 Office app 或远程服务。
  - 按 `office-editor-plan.md` 分为引擎可复现构建/嵌入、项目源与 PDF 预览、编辑与 Save & Return、可靠文字选区四个切片；先验证上游 LibreOfficeKit Android 原生引擎，不先放开无法渲染的扩展名。
  - [x] 第一切片固定待验证的 Collabora Gerrit `online` 提交 `a27b66cad6275afe672d73bd1bdfa7627a97c9f3`，并记录 Linux builder、NDK 与 16 KB 对齐的本机约束；该哈希不是构建或集成验收通过的声明。详见 `features/office-engine-next-slice.md`。
  - [x] 先落纯 core-project 保存就绪策略：按完整源字节 SHA-256 revision 守卫，区分外部冲突、只读目标、缺少恢复副本和可保存状态。`OfficeSavePolicyTest` 6/6 通过；未接 SAF/editor，也未开放 Office 文件入口。SPEC：`features/office-save-policy.md`。
  - 通过一个 API 34 平板 AVD 闭环覆盖导入、编辑、保存、重开；不建立跨 API 测试矩阵。
  - _Requirement: Office 源格式、布局与适配_

- [ ] 37. 桌面功能全量移植收尾审计
  - 对照 `desktop-ui-and-office-parity.md` 的菜单表、桌面功能入口与文档格式，逐项确认 Android 可执行行为，清除未实现占位和仅展示 UI。
  - 每个缺口落到独立 SPEC/任务，并在终态所有任务关闭前不宣称完整移植。
  - _Requirement: 背景与目标、桌面功能区与自适应导航_

- [x] 38. PDF 富 HTML 批注兼容（离线净化/渲染/剪贴板/选择与编辑）
  - 复用现有 `type: "html"` 项目格式和 APK 内 DOMPurify；安全限制、渲染尺寸与桌面 `.ink-rich-content` 行为以 `features/pdf-rich-html-annotation.md` 为准。
  - API 34 renderer 仪器测试与真实 PDF Activity 流程在 `Pixel_Tablet_API34` 各通过 1/1；覆盖格式/脚本/事件/危险 URL/外链/尺寸限制、系统 HTML 与桌面 marker 粘贴、净化复制、移动、删除/撤销和页数据隔离。聚焦 JVM 契约、Android 测试源码编译及 `:app:assembleDebug` 通过；真机仍归 task 12。
  - 完整实施拆分见 `pdf-rich-html-annotation-plan.md`；API 34 平板 AVD 为唯一常规仪器验收设备。
  - _Requirement: 桌面功能区与手写批注兼容_

- [x] 39. PDF 阅读页手势翻页与页码旁快捷按钮
  - 单指竖向滑动切换一页；手写笔、双指缩放、选择/批注模式不误触发。
  - 页码跳转旁提供上一页/下一页按钮，边界禁用，并复用统一页面切换和草稿保存逻辑。
  - 翻页策略 JVM 测试 4/4、PDF 视图滑动策略与真实三页 PDF 阅读器上滑/下滑及页码按钮共享状态的 API 34 AVD 流程通过。
  - SPEC：`features/reader-page-navigation.md`。

- [x] 40. 缩放笔迹坐标与页外书写设置
  - 缩放/平移时使用视口变换的逆变换采集笔迹、正变换渲染与命中；默认限制在 PDF 页内，可在阅读设置允许页外可见区域书写。
  - 保持桌面兼容的 `annotations.json` 坐标与既有压力、撤销/重做语义。
  - 几何/命中与 DataStore 定向单测、Debug APK 构建通过；API 34 View 仪器覆盖缩放/平移坐标回映及页外输入默认截断/启用保留。
  - SPEC：`features/zoomed-ink-and-extended-canvas.md`。
  - 回归边界补充：`features/zoomed-ink-regression-notes.md`。

- [x] 41. 当前页及后续页自动预解析
  - 阅读设置配置 0-10 页窗口（默认 3）；窗口含当前页，串行后台处理本地文字和按需离线 OCR，复用缓存，不调用云端模型。
  - 页/项目/源 revision 变化时取消旧队列并复用已完成结果；失败保留原文且明确警告。
  - 窗口/串行/重叠去重/页码切换与 revision 取消/失败隔离 JVM 用例、DataStore 持久化单测、API 34 平板设置滑块保存回归、仪器源码编译和 Debug APK 构建通过。
  - SPEC：`features/page-prefetch.md`。

- [x] 42. 手写笔末端停留识别几何形状
  - 笔工具在末端静止 550 ms 后预览识别 line/arrow/rect/ellipse；未达阈值或继续移动则保留自由笔迹。
  - 识别结果作为原生矢量项写入 `annotations.json`，不额外保存自由笔迹副本；复用原生批注页历史撤销/重做。
  - API 34 平板模拟器验证停笔提交、提前抬笔、高亮笔排除、移动取消、手势取消，以及阅读器落盘/撤销/重做。
  - SPEC：`features/dwell-shape-recognition.md`。

- [x] 43. 桌面工作流语义差异审计
  - 在命令 ID 清单之外，逐项比对桌面实际行为与 Android handler，确认并记录 standalone PDF/Markdown 打开、选中批注改色/改宽、HTML 富文本粘贴、AI prompt 模块编辑、独立 Markdown 主题和格式快捷键六项缺口。
  - 审计基线：`features/desktop-semantic-parity.md`；命令接线审计 `features/desktop-parity-gap-audit.md` 已明确仅覆盖 ID 层面。

- [x] 44. 编辑已选中批注的颜色与宽度
  - 选择一条自由笔迹或一个原生 PDF 批注后，通过现有颜色/宽度控件修改所选项；保留未知 JSON 字段和当前活动绘制样式。
  - 颜色与宽度分别进入对应页的原生撤销/重做历史；无选择时仅改变后续绘制样式。
  - 在保存互斥锁内基于最新页数据只补丁未变化的目标项；保留其他并发页项目。列表删除所选项时清除选择，删除更早项目时平移索引，避免样式误套到相邻项目；等价颜色不产生历史记录。
  - `PenPageStore` 与 app JVM 测试通过，含等价颜色无操作与删除索引映射；API 34 平板 AVD `PdfAnnotationSelectionClipboardActivityTest` 2/2 验证原生批注和自由笔迹的独立属性更新、并发字段保留、保存、撤销/重做；Debug APK 已构建。
  - SPEC：`features/desktop-semantic-parity.md` §44、`features/pen-style-controls.md`。

- [x] 45. 打开独立 PDF 与 Markdown 文件
  - 新增 `Open document` 入口，可选择 standalone `.pdf`、`.md` 或 `.markdown`；通过现有 New Project 流程选 SAF 父目录，文件名生成可编辑的安全项目名。
  - File 菜单与空白页入口共用 SAF 选择器；支持取消、替换和 Activity 重建时保留来源名称/URI 状态，同名目录拒绝覆盖，不支持扩展名显示可重选错误。
  - PDF 在复制前后均用 `PdfRenderer` 校验至少一页；Markdown 原始字节（含 BOM/CRLF，空文件也有效）复制到项目 `src/`，写 v1 manifest 并保留 `right.md`、`assets/`。
  - API 34 平板 AVD：`ProjectCreateOptionalPdfActivityTest` 10/10、`NewProjectDialogTest` 3/3；覆盖旧 PDF/空项目、Markdown 字节保真与首屏打开、取消、替换、实际持久化授权跨 Activity 重建及移除/取消/创建后释放、不支持格式、同名、预校验失败与复制后校验失败回滚。SAF fixture 不等同于系统文件选择器 UI或小米真机验收。
  - SPEC：`features/desktop-semantic-parity.md` §45、`features/standalone-document-import.md`；实施顺序：`standalone-document-import-plan.md`。

- [ ] 46. Markdown 笔记粘贴富 HTML
  - 显式“按 Markdown 粘贴”支持从剪贴板 `text/html` 转换常用语义格式，替换选区/插入光标；HTML 缺失时保留纯文本回退。
  - 转换本地完成，不执行脚本、事件属性或远程资源请求。
  - SPEC：`features/desktop-semantic-parity.md` §46。

- [ ] 47. AI 提示词模块编辑
  - 模型设置对齐桌面模块列表，支持逐模块修改、保存和恢复内置默认；Agent 后续读取应用本地覆盖值。
  - 提示词覆盖与 endpoint/model/key 分开保存，校验失败不覆盖最后有效内容。
  - SPEC：`features/desktop-semantic-parity.md` §47。

- [ ] 48. Markdown 阅读主题独立设置
  - 将 `classic`/`paper` Markdown 渲染主题与应用外观设置分离并持久化；只影响 Markdown pane，不改变 PDF 原色、UI 外观或笔记源内容。
  - SPEC：`features/desktop-semantic-parity.md` §48。

- [ ] 49. Markdown 编辑格式快捷键
  - 焦点位于可写 Markdown 编辑器时，Ctrl/Cmd+B、I、K 复用工具栏粗体、斜体、链接变换。
  - 对话框、非笔记文本框和阅读界面不截获这些快捷键；保留选区、光标及撤销历史语义。
  - SPEC：`features/desktop-semantic-parity.md` §49。

- [ ] 50. 阅读器交互增强端到端复核
  - 按用户提出的六项验收重新核实 PDF 竖向滑动、页码两侧上一页/下一页、缩放手写、可选页外画布、后续页自动预解析及停留识形；已有实现不视为本轮已验收。
  - 补足真实阅读器中识形写入原生批注、按页撤销/重做和写入失败回退自由笔迹的回归；检查 Settings 开关及预解析窗口实际接入。
  - 每个切片先有失败回归再实现，随后运行定向 JVM/Android 14 AVD 测试、仪器源码编译、Debug APK 构建；各小功能独立 commit、带 changelog 并推送。
  - SPEC：`features/reader-interaction-enhancements.md`；计划：`reader-interaction-acceptance-plan.md`。

- 实施顺序和测试/提交流程见 `reader-interaction-enhancements-plan.md`。

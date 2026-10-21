# 笔记富渲染：公式/代码/Mermaid/图片

## Scope

补齐 Android 阅读器的富内容渲染。普通 Markdown、公式和代码使用 Markwon 原生栈；项目附件图片只从 SAF 项目资源读取，内联 `data:` 图片不访问文件或网络。Mermaid 为复用桌面端完整语法，可在单个图表预览区域使用受限、仅加载 APK 内资源的 Android WebView；PDF、笔迹、编辑器、Agent 和其余界面仍为原生 Kotlin。任何富内容失败均保留可读源码并显示状态。页面准备管线不要求额外生成 Mermaid PNG。

## Renderer Selection

- Candidate comparison and migration gates are recorded in `../renderer-library-evaluation.md`.
- Markwon 4.6.2 remains the current compatibility baseline; library replacement is not implied by the evaluation. Markdown rendering, image loading, formula rendering, and Mermaid rendering are separate integration boundaries.
- The Pagenote SAF image handler remains authoritative. Do not replace it with a network-capable default loader unless a custom scheme/SAF resolver and blocked-network regression are preserved.
- JLatexMath's upstream Android repository is marked GPL-2.0. Before shipping or proposing a PR, record the resolved artifact license and any applicable exception in Android third-party notices. RaTeX is only a candidate until formulas and every bundled ABI pass visual and 16 KB checks.
- Imported project page 12 exposed Mermaid SVG overlap with later note text. A Pixel Tablet API 35 baseline measured a 1115x1272 preview host and an 856x332 diagram. The layout regression now reproduces the page-12 content in the real `NotePanel` preview at the tablet note-pane width and checks drawable overflow and rendered text pixels. Focused regression and all 66 app instrumentation tests pass on Pixel Tablet API 34 and API 35; app/notes unit tests, `assembleDebug`, and four-ABI 16 KB checks also pass. Real-device interaction and a final visual spot-check of the original project's pages 1, 35, and 69 remain under task 12.

## Render Contract

- 公式：行内 `$...$` 与块级 `$$...$$` 以及 ```math/latex/tex 代码块在预览中由原生 LaTeX renderer 实际绘制；编辑内容保留原 Markdown。有效公式验收须确认 renderer 实际绘出了非透明公式像素，不能只以 drawable/span 存在作为成功。未闭合/无效公式须保留原文并给出可见失败提示，不吞后文；无效 TeX 必须经过预览 renderer 配置的错误处理器进入可见 fallback。
- 代码：围栏代码块按本地 grammar 真实高亮。`codeLanguage` 别名归一（python3→python、c++→cpp、shellscript→bash 等，与桌面一致）；未知语言与未闭合围栏保留纯文本，不丢源码。
- Mermaid：Markdown 围栏必须离线绘制成图；空源、语法错误和 renderer 失败时保留源码并显示明确状态。仅使用 APK 内固定版本的 renderer 与 `WebViewAssetLoader` 本地资源；关闭文件/内容访问、拦截所有非应用资源请求、不暴露 JavaScript 原生桥接、不允许外部导航。每个原生 Markdown 预览宿主最多复用一个隐藏 WebView，串行生成 SVG，再交给 Markwon 图片 span 绘制；其余 Markdown、PDF 和笔记仍由原生 UI 绘制。图表须作为独立块占据与 drawable 高度相符的垂直空间；异步渲染完成后不得与前后段落或公式重叠。Mermaid 原文经 URL-safe Base64 私有图片 URI 保存，调用 JS 时作为 JSON 字符串数据传递；SVG 由 DOMPurify 过滤，移除 href 引用，且返回体不超过 256 KiB。100,000 字符以上的围栏继续显示原始代码；语法错误提示最多显示 180 字符源码片段，原笔记编辑内容不改动。以仓库锁定的 Mermaid 12.2.0 browser bundle 与 DOMPurify 3.4.17 为准，不在运行时联网加载依赖。
- 图片：`/attachments/<uuid>.<ext>` 经 `ProjectAssets.importAssets` 反向解析到 `src/assets/` 本地文件；存在时从 SAF 项目目录解码显示，缺失、不可读或解码失败时显示可见占位 + warning。SAF 查询、打开流返回 null/抛错，以及已打开流在读取中抛出 I/O 异常，都必须收敛为可见占位，不得留下空白图片或把异常逸出异步渲染。外部 http(s) 图只显示带来源信息的阻断占位 + warning，严禁发起网络请求；Markdown 图片离线策略独立于 Agent 的网络权限。Markwon 默认网络 scheme handler 必须移除或由本地阻断 handler 覆盖。
- 图片 scheme 白名单：仅允许本地 `pagenote-asset:` 与内联 `data:`；`http:`、`https:` 显示阻断占位，其他未识别 scheme 不得访问文件或网络。错误占位只显示安全来源摘要，不回显查询参数、片段、data URI 负载或本地绝对路径。
- 超长页：沿用 `NotePage.preview` 8000 字符截断 + hasMore 标记，透出统计不受截断影响（统计走全文）。

## Data Contract

- 新增 `NoteRichContent`：`extract(markdown)` 返回 `formulas: List<FormulaSpan>`、`codeBlocks: List<CodeBlock>`、`diagrams: List<MermaidBlock>`、`images: List<ImageRef>`、`errors: List<String>`、`warnings: List<String>`。
- 结构化语法诊断使用稳定枚举标识，不从错误文案反推类型：至少包含 `UNCLOSED_CODE_FENCE` 和 `UNTERMINATED_FORMULA`。现有 `errors`/`warnings` 继续满足兼容契约。
- `FormulaSpan(start, end, display)`、`CodeBlock(lang, source, closed)`、`MermaidBlock(source, state)`（state: READY/EMPTY/SUSPECT）、`ImageRef(url, kind)`（kind: LOCAL/MISSING/REMOTE）。
- 校验语义对齐桌面 `validateNote`：围栏未闭合→error；公式疑似未渲染分隔符→warning；mermaid 空源→warning。

## Preview Diagnostics

- 页笔记预览与 Markdown 源预览均在渲染内容上方显示本地化的轻量语法诊断；仅显示稳定诊断对应的固定文案，不在诊断区域展示 Markdown 片段、公式源码或图片 URL。
- 诊断只读原文并产生旁路状态，不改写、清洗或截断编辑数据；现有预览截断规则保持不变。
- 行内代码跨度按 CommonMark 的反引号运行长度配对，允许跨行以及多反引号分隔符。跨度内的 `$`/`$$` 不触发公式分隔符诊断；未配对的反引号不吞掉后续正文。
- `UNCLOSED_CODE_FENCE` 提示代码围栏未闭合；`UNTERMINATED_FORMULA` 提示公式分隔符未闭合。相同诊断在一页内只显示一次。

## Mermaid Layout Regression Notes

- The page-12 baseline showed the Mermaid `AsyncDrawableSpan` resolving to a 856x332 drawable inside a 1115x1272 tablet preview host. Re-rendering the `TextView` after the asynchronous drawable height becomes known reserves the block's vertical space.
- The remaining overlap came from `MermaidSvgDrawable.draw()` rendering SVG content outside its declared drawable bounds. Before clipping, the instrumentation test counted 15,553 non-transparent pixels beyond the bounds. Clipping the Canvas to the drawable rectangle removes that overflow; the test also verifies the following paragraph begins below the diagram and no Markdown glyph pixels are painted inside the diagram region.
- The focused regression passes on both Pixel Tablet API 34 and API 35. The complete 66-test app instrumentation suite passes on each AVD; app and notes JVM tests, `assembleDebug`, and all four ABI APK 16 KB checks pass. This does not replace the real-device interaction and original-project pages 1/35/69 spot-check tracked under task 12.

## Non-goals

- 不把应用、Markdown 编辑器或 PDF 阅读器改成 WebView 壳；WebView 只用于离线 Mermaid 图表预览，不加载网页或远端 Mermaid 服务。
- 不在页面准备管线生成 Mermaid PNG；图表仅在笔记/Markdown 预览时绘制。
- 图片编辑（裁剪/粘贴上传）不在本片，只做显示解析。

## Acceptance (EARS)

- When 预览含公式/代码块时，阅读器 shall 离线绘制公式并高亮已支持语言的代码。
- When 预览含有效行内或块级公式时，shall 绘制非透明公式像素，而不只创建 LaTeX span。
- When 预览含无法解析的公式时，shall 通过 renderer 错误处理器显示保留源码的可见 fallback，并继续显示公式后的 Markdown 内容。
- When 预览含 Mermaid 块时，阅读器 shall 离线绘制图表；渲染失败时保留源码并显示明确错误态。
- When Mermaid SVG 异步加载完成时，预览 shall 为图块保留足够行高，且图块不得与前后文字或公式发生视觉重叠。
- When Mermaid 节点标签含中文、引号和全角标点时，预览 shall 正确渲染图表、保留其前后 Markdown，并不得误报 renderer 失败。
- When 图片缺失、不可读或无法解码时，shall 显示可见占位 + warning，不以透明图静默吞错。
- When SAF 图片流在解码期间抛出 I/O 异常时，shall 将其转换为本地化可见 warning，不让异常逃逸图片异步加载器。
- When Markdown 包含 HTTP(S) 图片且应用拥有 Agent 网络权限时，shall 显示阻断占位 + warning，且不得连接该 URL。
- When 图片加载失败时，shall 只显示安全来源摘要，不回显 URL 查询参数、片段、data URI 负载或本地绝对路径。
- When 围栏未闭合时，shall 按纯文本显示并记 error。
- When 页笔记预览或 Markdown 源预览含未闭合围栏/公式时，shall 在预览内容上方显示本地化诊断，且不回显原文片段或 URL。
- When `$`/`$$` 出现在有效的跨行或多反引号代码跨度中时，shall 不生成未闭合公式诊断，且诊断过程不得修改原 Markdown。

## Verification

- JVM：公式预处理、别名归一、结构化围栏/公式诊断、跨行和多反引号代码跨度、缺失/外链图片判定。
- Android instrumentation：有效行内/块级公式绘制的真实 Drawable 像素，以及无效 TeX 经 renderer error handler 的源码保留、可见 fallback 和后续文本保留。
- Android instrumentation：两种 Markdown 预览均显示本地化诊断；确认诊断文案不包含输入中的 URL/公式/片段且预览内容仍保留原始 Markdown 语义。
- Android instrumentation：覆盖中文带引号 Mermaid 流程图，确认图表 span 的布局行高容纳实际 drawable，且前后 Markdown 顺序正确、无行重叠。
- Android instrumentation：在 tablet note-pane 宽度对真实 `NotePanel` 预览执行 page-12 Mermaid 回归，确认异步图表 drawable 不越界、后续正文不提前开始，且图表区域没有 Markdown 字形像素。
- Android AVD visual regression：用 69 页导入测试副本的第 12 页确认 Mermaid 图表与后续中文段落、公式没有视觉重叠；仅检查 span 存在或单行高度不够作为验收。
- Android instrumentation：LaTeX drawable/span、已知代码语言高亮、未知语言源码回退、缺图与损坏内联图占位、抛出 I/O 异常的本地图片 `InputStream` 占位；另通过提供 `DocumentsContract` 形状 URI 的 instrumentation `ContentProvider` 和目标应用真实 `ContentResolver` 集成验证查询/打开成功，以及查询拒绝或打开流失败时显示安全占位（不等同于系统 URI 持久授权验收）；HTTP(S) handler 不发起请求、敏感来源脱敏，以及 Mermaid WebView 的真实 SVG 绘制、源码安全传递、错误回退、离线资源白名单和无外部网络请求。
- `cd android && ./gradlew :feature-notes:testDebugUnitTest :app:assembleDebug` 通过；四 ABI APK 继续执行 16 KB 检查。

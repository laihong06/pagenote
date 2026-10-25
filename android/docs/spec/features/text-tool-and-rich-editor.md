# Text Tool Style Schema and Rich Text Editing Window（移植批次 4）

- 状态：已实施（分支 `codex/port-canvas-notes-features`，移植差距清单 §八）
- 范围：文本工具数据层（字体目录、文本样式 schema、UpdateTextStyle 撤销动作）与画布富文本编辑窗（contenteditable + DOMPurify 双向、样式动作撤销，画布对象复用 `type: "html"`）。
- 数据契约决策见 `../canvas-schema-decision.md` §6.5（D6）。

## 1. 文本工具数据层

### 1.1 字体目录与加载（TextFontCatalog / AnnotationTextFontLoader）

- `feature-annotations` 的 `TextFontCatalog` 是字体唯一注册中心：6 个内置条目（default/serif/mono/cursive/condensed/smallcaps），每条含稳定 `id`、Android 系统字体族名、WebView CSS 栈与中英文标签。桌面不捆绑字体二进制（渲染用 `'Segoe UI, Microsoft YaHei, sans-serif'` 系统栈），Android 同样不捆绑：`AnnotationTextFontLoader`（app）把 id 解析为 `Typeface.create(系统族, NORMAL)`（带缓存），未知 id 降级为默认条目（对齐 `shape` 字段的读侧降级策略）。
- `assets/fonts/` 为未来捆绑字体预留的目录（当前为空，未随包发布任何字体二进制）。

### 1.2 文本样式 schema（AnnotationTextStyle）

`type: "text"` 项的样式四元组 = 字体/字号/对齐/颜色：

| 字段 | 位置 | 取值 | 缺省 |
| --- | --- | --- | --- |
| `color` | canonical | `#RRGGBB` | 笔色 |
| `font` | extension | TextFontCatalog id | `default`（降级） |
| `fontSize` | extension | 20..500（文档单位） | `max(20, width*5)`（桌面派生式） |
| `align` | extension | `left\|center\|right` | `left`（桌面锚点语义） |

- 读侧容错：`fromItem` 对未知 `font`、越界 `fontSize`、未知 `align` 一律降级，不拒绝 item；写侧严格：`create` / `AnnotationTextStyleEditor.updateTextStyle` 拒绝非法值，Android 永不写出非法样式。
- 新建项默认 schema：`AnnotationTextStyle.forNewItem(笔色, 笔宽)` → `font=default`、`fontSize=derivedFontSize(笔宽)`、`align=left`，由 `AnnotationTextDraft.toItem(style=...)` 显式写出；不传 style 保持旧格式。
- 纯页更新：`AnnotationTextStyleEditor.updateTextStyle(page, index, …)` 只改 `type:"text"` 目标、等效值不落笔、保留其余全部字段（含 `pressures`/`layoutAnchor`/`shape` 等透传字段）。

### 1.3 UpdateTextStyle 撤销动作（AnnotationUpdateTextStyleAction）

- app 层 PenAction 适配器（注册 id `pdf.annotationTextStyle`，scope `pdf:<projectId>:<page>`），经统一 `PenActionStack` 提供 do/undo/redo；连续同 scope 样式编辑合并为一个撤销步（undo 恢复首个 before，redo 恢复末个 after），对齐批次 1 `PenPageStoreActions` 的 UPDATE_STYLE 合并语义。
- 构造器对内容 no-op 返回 null（不记录空动作）；阅读器 UI 的撤销仍走既有 `PdfAnnotationEditHistory` 快照路径，两路径不混用。

### 1.4 画布渲染与命中

- `PdfAnnotationOverlayItem` 解析三个样式扩展字段（同读侧容错规则）；overlay 文本绘制解析析 `Typeface`/对齐/显式字号，缺省回退桌面派生值。
- `PdfAnnotationClipboard.bounds` 文本包围盒使用生效字号并按对齐方式平移（left 锚点即 x，center 以 x 为中线，right 以 x 为右缘），选择框与命中测试与渲染一致。

## 2. 富文本编辑窗

### 2.1 编辑页（assets/rich-annotation/editor.html + editor.js）

- 离线 contenteditable 编辑页，与渲染页同源同安全姿态：仅 `https://appassets.androidplatform.net/assets/…` 资产、网络加载阻断、无文件/内容访问、CSP 与渲染页一致。
- **DOMPurify 双向**：`loadPagenoteRichEdit` 对输入 HTML 先净化再进 DOM（sanitize-in）；每次读出（命令结果/undo/redo/`retrievePagenoteRichEdit`）都对当前 DOM 再净化（sanitize-out）。净化配置与渲染页完全一致（`ADD_TAGS: annotation`、data:image/# URI 白名单、禁 iframe/form/input 等）。
- **样式动作撤销**：editor.js 维护内部 undo/redo 栈（深度 80，与桌面 rich-editor.js 一致）；样式命令恒为独立撤销步；输入按 500ms 合并（对齐桌面 `emit()` 语义）。空栈 undo/redo 为优雅 no-op。
- 命令白名单（JS 侧 + Kotlin 侧双重）：bold/italic/underline/strikeThrough/justifyLeft|Center|Right/insertUnorderedList/insertOrderedList/formatBlock（p/h1/h2/h3/blockquote）/foreColor（#RRGGBB）/fontName/removeFormat。

### 2.2 控制器与对话框（RichHtmlEditorController / RichHtmlAnnotationEditorDialog）

- 控制器复用渲染器的请求协议（requestId + `document.title` 状态 + `__pagenoteRichEditResult` 回读），全部主线程回调式 API：`loadHtml`/`runCommand`/`undo`/`redo`/`confirmHtml`；15s 请求超时；WebView 可聚焦可输入。
- Compose 对话框提供原生样式工具栏（格式/对齐/列表/段落块/目录字体下拉/色板/undo/redo），全部经 `RichHtmlEditorCommand` 校验白名单后才派发（fontName 限定目录 CSS 栈，foreColor 限定 #RRGGBB）；确认时空内容（仅标签/空白/实体）提示且不落盘。
- **画布对象复用 `type: "html"`**：确认后 HTML 再经离线渲染器净化并测量高度（`richHtmlRenderer.render(html, 500, null)`，与既有粘贴链路完全一致），新建项以两点 bbox `[[80,90],[580,90+height]]` 落盘；选中 html 批注时进入编辑模式，`PdfAnnotationClipboard.updateHtmlContent` 原位更新 `html` 并按新高度只移动第二个点的 y（宽度保留、其余字段不动）。落盘走 `persistNativeAnnotationPage`，与其它原生批注一样可撤销/重做。

### 2.3 入口

- 阅读菜单手写组新增 `ink_rich_text`（富文本），门控与 ink_text 一致（项目 + PDF + 可写）。

## 3. Verification

- `cd android && ./gradlew :feature-annotations:testDebugUnitTest :app:testDebugUnitTest :feature-pen:testDebugUnitTest :app:compileDebugAndroidTestKotlin`
- 批次结果：feature-annotations 38/38（新增 TextFontCatalogTest 4、AnnotationTextStyleTest 10）；app 399/399（新增 AnnotationUpdateTextStyleActionTest 5、RichHtmlEditorCommandTest 5，扩展 AnnotationTextDraftTest/PdfAnnotationClipboardTest/PdfAnnotationOverlayMapperTest/DesktopCommandChecklistTest）；feature-pen 104/104 回归；androidTest 源码编译通过。

## 4. 未覆盖（Not covered）

- 编辑窗 WebView 的真机/AVD 交互回归（输入、选区、样式命令的实际执行效果）未运行——仅静态资产与协议层实现，JVM 无法覆盖 WebView 运行时；沿用渲染页（`pdf-rich-html-annotation.md`）的 AVD 验证模式留给后续批次。
- `assets/fonts/` 未捆绑任何字体二进制（仓库无可再分发字体，系统字体族满足目录当前全部条目）；未来捆绑字体需补 `TextFontCatalog` 资产通道与许可文件。
- 桌面端不解释 `font`/`fontSize`/`align`：同一 text 项在桌面仍按 `width` 派生字号、默认字体渲染（D6 既定容忍点）。
- 富文本编辑窗的色板复用笔色 8 色与目录 6 字体；自定义 RGB/更多字号（execCommand fontSize 1-7 体系）未纳入本批。

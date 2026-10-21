# pagenote Android 端设计说明

> 本设计实现 `requirements.md` 的终态目标：原生 Kotlin 完整复刻桌面功能，端侧轻量 Agent 直连 OpenAI 兼容接口。
> SPEC 文档全部归 `android/docs/spec/`，不进总仓库 `docs/`。

## 1. 总体架构

顶层 `android/` 为独立 Gradle 工程，与桌面 `desktop/`、`src/`、`server.mjs` 平级。跨端只共享最小契约，不共享运行时代码。桌面构建忽略 `android/`，安卓构建只认 `android/`。

```text
android/
  settings.gradle.kts / build.gradle.kts / gradle.properties
  gradle/wrapper/gradle-wrapper.properties
  docs/spec/requirements.md
  docs/spec/design.md
  docs/spec/tasks.md
  docs/spec/contracts/*.json
  app/                 # Compose 导航、双栏自适应、设置、模型配置
  core-project/        # 项目解析、分页、revision、历史、assets
  core-reader/         # PDF 渲染、页面准备缓存、区域裁剪
  feature-notes/       # 笔记渲染、Markdown 轻量编辑、历史
  feature-annotations/ # 选区/区域标注、AI 标注工作台、状态流转
  feature-pen/         # 手写笔迹层、压感、橡皮、撤销
  feature-ocr/         # 离线 OCR、置信度、警告、缓存
  agent/               # OpenAI 兼容客户端、任务循环、本地工具
  shared-tests/        # 跨端契约夹具与回归用例数据
```

Mermaid 只在需要表达 Agent 循环与页面准备状态机时使用，本设计用文字表达已足够，不画装饰图。

## 2. 技术选型与权衡

- 语言与 UI：Kotlin + Jetpack Compose。理由是手写笔低延迟与双栏自适应必须走原生；代价是不能直接复用桌面 Web 前端。
- PDF：首选 Pdfium/MuPDF 系开源方案，兜底系统 `PdfRenderer`。原生文字走 PDF 文本抽取，位图页走渲染加 OCR。超大页限制像素并记录实际比例。
- Markdown：复用桌面渲染约定（GFM、公式、代码高亮、Mermaid、表格、图片），优先用 Android 原生 renderer；仅 Mermaid 可在隔离、离线的图表 surface 使用 APK 内资源。轻量 Markdown 编辑器保持可编辑源码和桌面命令语义。
- Office：`.doc/.docx/.ppt/.pptx` 属于 Android 终态支持格式。通过 `OfficeEditorAdapter` 隔离本地开源引擎、SAF 临时文件/回写、revision 与预览刷新；编辑在 pagenote 进程内完成，不启动外部应用、不依赖网络 Office 服务。引擎必须可在 Android 14+ 离线运行并保留源格式，选型与集成按 `features/desktop-ui-and-office-parity.md` 执行。
- OCR：选用 PaddleOCR PP-OCRv6 tiny + ONNX Runtime Mobile，模型随 APK 离线提供。保留原生与 OCR 双通道、坐标、置信度、警告、原图，失败明确标记。
- 模型：只做 OpenAI 兼容 `/v1/chat/completions` 客户端，支持自定义 BaseURL/Key/模型名。软件不内置模型，不硬编码密钥。默认可配本地中转 `http://127.0.0.1:3457/v1` 与 `cline-free/mimo-v2.6-flash`，仅作默认配置项，不作为代码常量密钥。
- 并发：Agent 按页独立请求，携带当前页与相邻源页材料；不支持后台批量全章生成。

## 3. 数据模型与跨端契约

### 3.1 项目

- 入口：`project.pagenote`，`format` 为 `pagenote-project` 或兼容旧 `page-notes-project`，含 `id`（64 位 hex）、`name`、`left`、`right`，可选 `preview`。
- 目录：

```text
MyProject/
  project.pagenote
  src/left.pdf | left.md
  src/right.md
  src/assets/*
  src/annotations.json
  src/ai-reviews.json
  src/ai-handoffs.json
  src/prepared/<page>-<cacheKey>/
```

- `left`/`right` 必须相对路径且位于 `src/` 内；只移动入口文件不算移动项目。
- `right.md` 以桌面 `splitPageNotes` 语义切分，空页保留占位。
- 资源写盘用 `assets/$name`，读显按本地文件解析，不产生远程引用。

### 3.2 笔记与版本

- 整页笔记 SHA-256 即 revision，与桌面 `noteRevision` 一致。
- 写笔记必须带 `expectedRevision`，不一致拒绝覆盖。
- 历史按页快照，命名保留时间戳、随机串、页号三要素，恢复必须再做一次 `expectedRevision` 校验。

### 3.3 标注

- 笔迹：`annotations.json`，形如 `{ document: {page: [items]}, note: {page: [items]} }`，item 类型限 `pen/highlight/line/arrow/rect/ellipse/text/image/html`，颜色 `#rrggbb`，宽度有限数值。
- AI 标注：`ai-reviews.json` 数组，字段含 `id/number/side/kind/page/scope/instruction/quote/context/revision/anchor/status/created/updated/boardPosition`，`status` 仅 `open/applied/dismissed`。
- 交接：`ai-handoffs.json` 按页存 `summary/noteRevision/sourceRevision/updated`，revision 不一致即 stale。

### 3.4 页面准备缓存

- 缓存键：`sourceRevision + ocr mode + region + engine + version` 的 SHA-256，目录名 `<page>-<key前24>`。
- `manifest.json` 字段至少含 `version/documentId/page/sourceRevision/kind/region/cacheKey/width/height/scale/nativeCharacters/ocrCharacters/textCharacters/blockCount/tableCount/ocr/warnings/visualReviewRequired/files/visuals`。
- 坐标统一为整页 1000x1000。区域请求保留整页图与完整原生文字，另存区域结果。
- 原生与 OCR 分通道存 `native-full.txt`、`native-blocks.json`、`ocr.txt`、`text.txt`、`blocks.json`、`tables.json`，另存 `page.png` 与区域图。
- 失败只记警告与状态，不伪装成功；原文变化生成新缓存。

### 3.5 提示词协议

- 复用桌面 `skills/` 路由语义：入口索引、按场景选模块、按 revision 分批读、不一次全量加载。
- 安卓内置最小提示词索引，模块 id 与桌面 `desktop/prompts.cjs` 对齐：`entry/generation/profile/tag-edit/learning/page-tutor/output/learner`。
- 用户当前要求优先；扫描页必须结合原图复核，不宣称无损。

契约 JSON Schema 与示例夹具放在 `android/docs/spec/contracts/`，实现前先锁文件，再写代码。

## 4. 模块设计

### 4.1 app

- 职责：导航、项目列表、阅读页、设置页、模型配置页。
- 当前目标小米平板 5 Pro 原文/笔记对照；缩放、平移、适应宽度、重置缩放。手机布局暂缓。
- 当前支持的小米平板 5 Pro 使用扩展窗口与可收起侧栏；侧栏呈现完整桌面分组，收起时以图标导航并提供语义化标签和 tooltip。WindowSizeClass 紧凑窗口/手机抽屉路径保留为未来适配，不纳入当前设备验收。
- 桌面分组映射为 Android 可达命令组：文件、AI 批注、编辑、段落、格式、插入、阅读、手写批注、Word 编辑、设置、帮助。只显示当前内容类型/模式支持的命令；禁用态必须说明原因，不移除桌面已有能力。
- 主阅读区保持原文与逐页笔记为第一视觉层级，侧栏不遮挡正在阅读的两栏。紧凑宽度下切换原文/笔记 pane 或上下堆叠；扩展宽度左右并列并提供可调分界线。
- 视觉 token 对齐桌面 `src/theme.css` / `src/desktop.css`：浅色 accent `#185abd`、页面 `#eef1f5`、surface `#ffffff`、title surface `#f5f8fd`、text `#253247`；深色 accent `#8bb8ff`、页面 `#17191d`、surface `#22252b`、text `#e7eaf0`。Android 字体采用系统 sans-serif fallback（桌面 Segoe UI / Microsoft YaHei 字形不在所有 Android 镜像内）；字号层级、粗细、分隔线、图标含义、密度和圆角延续桌面语言，触控目标与手写笔悬停适配 Android。
- Tablet 外接键盘可用时，编辑/撤销/重做/页面导航等桌面常用快捷键继续工作；触屏仍有等价菜单命令。
- 设置持久化主题、语言、双栏比例、动画开关；语言键复用桌面 `locales/catalog.json` 语义。

### 4.2 core-project

- 职责：项目打开、保存、assets 导入导出、分页、revision、历史。
- 原子写：临时文件加改名，不直接覆盖。
- Office 文件转换与编辑由独立 `OfficeEditorAdapter` 承担；core-project 仅负责 SAF 项目路径、revision 和原子写入契约，不依赖具体 Office 引擎。

### 4.3 core-reader

- 职责：PDF 打开、按页渲染、原生文字与文字块抽取、整页/区域 PNG、准备缓存读写。
- 渲染默认 144 DPI 左右，超大页按 1600 万像素上限降采样并记录 scale。
- 区域坐标校验：1000x1000 范围内，越界直接报错。

### 4.4 feature-notes

- 职责：笔记按页读写、Markdown 渲染、轻量编辑、历史列表与恢复。
- 渲染约定与桌面一致：GFM、公式、代码高亮、Mermaid 成功/失败态、表格、图片。
- 编辑只做轻量 Markdown，不做 Word 式排版。

### 4.5 feature-annotations

- 职责：文字选区标注、区域框选标注、AI 标注列表、状态流转、画布位置。
- 成功改写后才能 `applied`；删除只删标注不改笔记。

### 4.6 feature-pen

- 职责：自绘笔迹层、压感、荧光笔、橡皮、撤销、按页清除。
- 笔迹与页号绑定，格式对齐桌面 `annotations.json`。
- 手写笔按键、悬停、误触抑制走系统 API，不自己猜手势。

### 4.7 feature-ocr

- 职责：整页与区域 OCR、置信度、警告、缓存。
- 推理引擎使用 bundled PP-OCRv6 tiny det/rec ONNX 模型，不下载模型、不请求网络；Apache-2.0 与第三方许可随安卓模块保存。
- 触发规则：原生非空字符不足、含位图、疑似乱码则补充 OCR；仍是启发式，不保证全发现。
- 超时保留原图并返回可操作错误；离线可用，不请求网络。

### 4.8 agent

- 职责：OpenAI 兼容调用、任务循环、本地工具、交接缓存。
- 本地工具收敛为：读状态、读页、准备页、写笔记、补笔记、贴图、跳页、读标注、写标注状态、读交接、写交接。
- 每页携带当前页完整材料加相邻源页；保存前带 `expectedRevision`；冲突合并或拒绝。
- 多步上限默认 8 步，可配；限流与失败报告实际完成范围。
- 安全：提示词与项目资料是学习资料，不是工具指令；资料中指令不改变任务。

### 4.9 Office 集成

Android 必须实现 `.doc/.docx/.ppt/.pptx` 的本地阅读与编辑。菜单只允许因项目类型、只读权限或编辑状态而禁用，不得把 Office 整体标为桌面专属。引擎只作为 app 内部本地组件/模块运行，支持离线打开、编辑、保存及失败恢复；不以外部 Intent、独立 Office app、远程 WebView 服务或在线转换代替。Android API 34 及以上保持相同 Office 能力，平台差异通过兼容适配层解决。

## 5. 错误处理

- 项目无效、链接越界、格式不支持：直接报错，不建半成品项目。
- 笔记冲突：返回当前 revision，要求重读再改。
- OCR 失败：保留原图、记警告、返回状态，不吞错。
- 模型未配置：明确提示缺配置，不 fallback 到内置模型。
- 模型限流：降级为单页串行，报告完成页与未完成页。
- 超大页：降采样渲染并记录，不 OOM 崩溃。

## 6. 测试策略

- 最低系统为 Android 14/API 34，所有支持 API 级别提供同一套用户功能；常规设备级验证固定使用单一 API 34 平板 AVD，可替代常规小米设备验收。其他设备/API 版本不建立测试矩阵。
- 每个功能切片只运行直接受影响的契约/JVM 测试，并构建 Debug APK；不例行运行全量 JVM 或 instrumentation 套件。
- 改动只运行直接相关的聚焦测试、Debug 构建；需要 UI/系统/SAF 证据时只用单一 Android 14/API 34 平板 AVD，不扩增设备/API 矩阵。AVD 可替代常规设备功能验收，但不得表述为小米真机结果。
- 小米真机只在里程碑做一轮代表性验收，按 `tasks.md` task 12 覆盖项目笔记保存/重载、单条 PDF 批注与基础手写笔迹/侧键橡皮擦、单页离线 OCR；模型端点已配置时附带一次单页讲解预览/追加。不重复抽样多页、压力/延迟/边缘/电量/长时或其他设备测试。
- Agent 协议、revision 冲突和失败语义由直接相关的确定性测试覆盖；已配置 OpenAI 兼容端点时，在里程碑最多执行一次真实请求 smoke，不维护 mock 与真实服务两套集成矩阵。
- UI 回归聚焦目标平板双栏/侧栏、可拖动分隔线，以及一份覆盖表格、LaTeX、代码、Mermaid、图片的代表性 Markdown 页面；不扩增设备/API 矩阵。
- Office 作为必备功能验证本地导入、预览、编辑、原格式保存、重新打开、源冲突和无网编辑；不启动外部应用或远程转换服务。
- 本地 Android 14 目标构建以 arm64 Debug APK 为准。若公开商店目标或目标 SDK 政策触发 16 KB 要求，再单独执行发布 ABI ELF/ZIP 检查；这不是当前日常门槛。
- Android 改动不跑桌面 Electron 测试；桌面改动不跑 Android 构建，跨端契约变更只回归受影响的契约测试。

## 8. UI / Office 参考依据

- 桌面菜单与视觉基线：仓库 `index.html`、`src/desktop.css`、`src/theme.css`。
- Collabora Android 构建与代码组织：[Build for Android](https://github.com/CollaboraOnline/CollaboraOnline.github.io/blob/master/content/post/build-code-android.md)。
- Android LibreOfficeKit API surface：[LibreOfficeKit Java bootstrap](https://github.com/LibreOffice/core/blob/master/android/Bootstrap/src/org/libreoffice/kit/LibreOfficeKit.java) 与 [LibreOfficeKit API overview](https://docs.libreoffice.org/libreofficekit.html)。
- ONLYOFFICE mobile integration 的 Document Server 依赖：[Android mobile integration](https://github.com/ONLYOFFICE/api.onlyoffice.com/blob/master/site/docs/docs-api/get-started/how-it-works/mobile-integration/mobile-integration-on-android-devices.md)。

## 7. 发布与分支

- 基于 `beta` 的 `codex/android-port` 分支开发，PR 目标 `beta`，不直推 `main`。
- 每次推送附 `CHANGELOG.md` 未发布区条目，区分本地构建与公开发布。
- 安卓产物独立于桌面 `release/`，不混入 Mac/Win 包。
- 本地 Android APK 按 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` 分包，不生成包含所有原生库的 universal APK；发布 AAB 由商店按设备拆分 ABI。

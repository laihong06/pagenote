# PageNote 鸿蒙版适配 · 工作进度

更新时间：2026-11-09（Asia/Shanghai）
分支：`harmonyos/beta`（本地，未推送远端）

## 当前状态一句话

鸿蒙端功能适配已基本铺完并推进到 **0.2.0-dev.1**：阅读/笔记/搜索/批注/公式/Mermaid/雨课堂/夜览/闪卡/AI 动作/插图/导出导入/横竖屏自适应均实现并在平板模拟器上逐项实测（截图存档）；剩余为设备条件受限项（PDF Kit 真机渲染、真实课堂采集、AI 真实调用、竖屏上机），已记入 KNOWN_ISSUES 与下方清单。

## 已完成（已提交，commit 2721b1a / e9b2e20）

- 工程：`harmonyos/` ArkTS/ArkUI Stage 工程（平板优先，兼容 API 20+，编译 API 26）
- 核心层 `entry/src/main/ets/core/`（与 Android core-project 逐条对齐）：
  - `PageNotes.ets`：`<!-- pagebreak -->` 分页切分/合并、SHA-256 revision
  - `ProjectManifest.ets`：manifest 解码/编码/校验（src/ 内相对链接、64 位 hex id）
  - `ProjectStore.ets`：沙箱项目库、原子写、revision 校验保存
  - `NoteHistory.ets`：历史快照（src/history/）
  - `Agent.ets`：OpenAI 兼容客户端 + 模型配置（密钥存应用偏好，不落项目目录）
  - `Annotations.ets`：annotations.json 契约（document/note 双侧、1000 比例坐标系）
- 界面：书架（新建/导入 PDF、MD）、阅读页（PdfView 阅读、逐页笔记编辑、Markdown 预览、历史恢复、AI 按页讲解、手写批注层 InkCanvas：四色/撤销/清除本页/翻页随页加载）
- 基础设施：`build.cmd` / `setup-links.ps1` 离线构建；`.kimi/skills/harmonyos-dev/SKILL.md` 项目局部技能；`docs/harmonyos/README.md`；`platforms/harmonyos/version.json`（0.1.0-dev.1，未发布）；CHANGELOG Unreleased 已记

## 已完成（本批，待提交）

- 笔记搜索：ReaderPage「搜索」按钮 + sheet，跨页全文检索 notes.md（Markdown 项目连同原文），每页取首个命中做摘要，点击结果跳页
- 图片附件渲染：MarkdownView 支持块级 `![alt](assets/x.png)`，经 `resolveImage` 解析到项目 `src/` 沙箱路径（拒绝 `..` 逃逸与绝对路径）；外部 http(s) 图片占位提示，保持离线；行内图片以 `[图：alt]` 占位
- 公式渲染：块级 `$$ … $$` 与 ` ```math/```latex ` 交给 `components/MathView.ets`（Web 组件加载 rawfile `math.html` + 本地 KaTeX 0.16.22 dist，含字体共 1.5MB，不依赖 CDN）；渲染后按内容高度自适应
- 顺手修复：`ProjectManifest.ets` 中一个字面 NUL 字节改为 `\x00` 转义（内容等价，便于工具链读取）
- 编译验证：BUILD SUCCESSFUL ×2（hvigor 手动命令；`build.cmd` 在 GBK 控制台下因 UTF-8 中文注释解析失败，属已知脚本限制，SKILL.md 手动命令为准）

## 模拟器运行验证（2026-11-04，MatePad Pro 13 / API 24）

已验证通过：书架、新建项目、Markdown 阅读、笔记编辑/保存/脏标记/预览、历史快照列表（含恢复按钮）、跨页搜索+跳页、批注笔迹（四色/撤销/清除本页）、导入文档选择器弹出与目录导航。截图存 `docs/harmonyos/images/`。

验证中修复的三个真实缺陷（均已复测）：

1. **PDF Kit 静态 import 崩溃**：模拟器镜像缺 `libpdfview.z.so`，ReaderPage 顶部静态 import 导致任何项目打开即 SyntaxError 崩溃。修复：PDF 面板抽为 `components/PdfPane.ets`，ReaderPage 经 `canIUse('SystemCapability.OfficeService.PDFService.Core')` 检测 + 动态 import（WrappedBuilder 在 PdfPane 模块作用域包好导出，规避编译器 10905109；类型经 import type 桥接，规避 arkts-no-structural-typing），失败走降级文案。
2. **多 bindSheet 同节点全失效**：4 个 sheet 挂同一根节点导致全部无法弹出（书架单 sheet 正常）。修复：每个 sheet 挂不同常驻节点。
3. **InkCanvas 批注永不生效**：`penColor`/`drawingEnabled` 是普通字段，运行期父组件传值不更新。修复：改 @Prop。

未完成的验证：PDF 导入全链路（picker 只显示媒体库已索引文件，hdc 推送的 PDF 未索引，选文件一步受阻——环境限制非应用缺陷）；PdfView 真实渲染（需带 PDF Kit 的镜像/真机）；AI 真实请求；公式/图片预览的设备实测（代码路径与搜索同页，编译已过）。

## 雨课堂历史导入（2026-11-04）

对齐 Android feature-yuketang / RainProjectWriter 契约的鸿蒙实现：

- `core/RainProtocol.ets`：协议类型与解析（历史 type=3/14 语义、可见页过滤、LESSON_ON、当前课堂）
- `core/RainClient.ets`：应用内二维码登录（wss requestlogin → ticket 域名校验 → QR 图片 → UserID/Auth → web_login → basic-info），会话存应用偏好（不落项目目录；注意 Android 为 Keystore 加密，鸿蒙端当前与 AI 密钥同级明文偏好，差异已记录）；课程/历史课堂列表、课后报告课件下载
- `core/PdfBuilder.ets`：最小图片型 PDF 生成器（鸿蒙无 PDF 创建 API；JPEG DCTDecode 直接嵌入，页尺寸 ≤960×720）
- `core/RainProjectWriter.ets`：图片解码重编码（JPEG q88）→ PDF + 分页空笔记 + 来源清单 + manifest，失败整目录回滚
- 书架「雨课堂」面板：登录/二维码/课程列表/课堂列表/导入进度
- 模拟器实测：登录 WebSocket、ticket、QR 下载与渲染通过（截图 emulator-08）；修复响应头大小写与 image/jpg 归一问题
- 未验证：微信扫码后的完整登录、真实课程列表与导入（需用户账号）；实时课堂采集未实现（R4，下一阶段）

## 雨课堂实时采集（R4，2026-11-04）

- `RainProtocol.ets` 增加：实时消息解析（hello/fetchtimeline/slidenav 等）、timeline 游标回放（断线续采、游标缺失标记可能缺页）、事件→课件页定位
- `RainClient.ets` 增加：checkin 进入课堂（lessonToken/identityId/Set-Auth）、WS hello 连接、指数退避重连（≤30s）、待写队列顺序处理+显式重试、停止/结束语义
- `RainProjectWriter.insert`：幂等去重（classroomId+presentationId+pageId+sha256）→ 按 RainPageOrder 语义分组排序 → 从来源图片重建整份 PDF → 笔记/批注按 oldToNew 重映射 → 统一落盘；页码/来源非法即中止保护现有数据
- 面板：当前课堂列表（匹配课程、匹配不上不静默借用）、开始/停止采集、状态与已采集页数
- 验证：BUILD SUCCESSFUL；PdfBuilder 产物经离线字节级校验（xref 偏移/页数/EOF 正确）；模拟器冒烟面板正常无崩溃；真实课堂采集需登录账号验收

## 雨课堂真实账号验证与显示修复（2026-11-04 晚）

用户扫码登录后实测，发现并修复：

1. **导入成功但阅读页显示不了**：模拟器镜像无 PDF Kit → 降级为来源图片显示（读 `yuketang-source.json` 的 projectPage→asset，页数来自清单），课件页正常阅读与翻页。
2. **生成 PDF 页面横向平铺乱图**：PdfBuilder 图像字典 /Width /Height 误写页面尺寸（缩放后）而非 JPEG 像素尺寸，渲染器按错误尺寸采样。已拆分为 imageWidth/imageHeight，修复后真实导入的 134 页 PDF 经 PyMuPDF 逐页渲染校验正常。
3. **Image 裸路径 GetAsset failed**：沙箱绝对路径被当作 rawfile 资源查找；一律 `file://` 前缀（笔记图片附件 resolveAsset 同修）。
4. **cryptoFramework 空输入报错**：SHA-256 空字节数组 update 失败，空输入跳过 update 直接 digest。

实测通过：课程列表（3 门课）、课堂列表、134 页课件完整下载+导入+打开+翻页；另一课堂因服务器已删除某页（404/416）整次导入失败回滚——与 Android 严格语义一致。

## 行内公式与渲染实测（2026-11-04 晚，已提交）

- 行内 `$…$`：MarkdownView 解析为 math span，含公式的段落/列表用 Flex 换行混排（Text 无法混入组件）；MathView 增加行内模式（按内容定宽高）
- 实测修复两个尺寸问题：行内初始宽度 0 导致 Web 内容不创建（改 60 占位再修正）；Web CSS px 等同组件 vp，px2vp 换算导致行内公式被截断（去掉换算）
- 模拟器实测截图 `emulator-11-math-inline.jpeg`：块级 E=mc²、行内 a²+b²=c² 混排均正常；缺失图片资源按 alt 占位降级（SELinux 阻止 shell 向应用沙箱写文件，图片预览链路同雨课堂降级已验证的 file:// 路径）
- 下一步第 2 项「图片/公式预览的设备实测」中公式部分已验证；笔记内图片附件待应用内添加图片功能后闭环

## 夜览滤镜与批注工具箱（2026-11-04 晚，已提交 726257f / 9c14fac）

- 夜览滤镜：工具栏开关，阅读区暖色遮罩（HitTestMode.None 不拦截触摸/批注），实测开关正常（emulator-12）
- 批注扩展到契约可绘制类型：荧光（半透明）、直线/方框/椭圆（拖拽实时预览）、文字批注（点击落字）；三档粗细（细2/中4/粗7）
- 实测发现并修复：文字批注用 bindSheet 收输入时，软键盘弹出会把 sheet 顶掉（实测 closes on keyboard）→ 改为批注工具栏内嵌输入行
- 实测：方框/荧光/文字均写入 annotations.json 契约格式（1000 比例坐标系），截图 emulator-13/14
- 未实现：契约的 image/html 批注类型、橡皮擦、压感

## 闪卡与复习（2026-11-04 晚，已提交 ca9d997）

- `core/Flashcards.ets`：FlashcardCapture（选区/段落制卡，UTF-16 锚点+页 revision）、ReviewScheduler（SM-2 简化四档，常数与 Android 逐项一致）、FlashcardStore（`src/flashcards.json` 原子写；Android 在 Room 不跨端，JSON 为鸿蒙端选择）
- 阅读页「卡片」面板：制卡（选区优先，光标段落兜底，页面无内容允许手工卡）/复习（到期队列、翻面、四档评分）/卡片库（列表+删除）
- 实测修复：saveCard 原先强制从笔记重取锚点，页面无内容时手工卡无法保存 → 允许无锚点降级
- 模拟器实测：制卡→存储→复习→评分（reps=1/interval=1/dueAt+24h 正确）→卡片库计数，截图 emulator-15/16

## 选区 AI 动作（2026-11-04 晚，已提交）

- `Agent.ets` 增加 SelectionActions（改写/翻译/总结提示词模板，复用 OpenAI 兼容端点）
- 阅读页「AI 选区」：TextArea onTextSelectionChange 捕获选区 → sheet 显示选区摘要 → 三动作 → 确认后替换选区或追加插回（标脏待保存）
- sheet 挂笔记工具栏节点（各 sheet 分节点挂载原则）；模拟器实测面板与空选区提示正常；真实模型调用未验证（本机中转 127.0.0.1:3457 未运行）

## Mermaid 渲染（2026-11-04 晚，已提交）

- rawfile vendor mermaid 11.17.2（3.5MB 含 LICENSE）；`mermaid.html` + `components/MermaidView.ets`（Web 组件，内容定高）
- mermaid.render 是异步的而 runJavaScript 不等 Promise → 结果写 `window.__mmdResult`，宿主轮询（300ms 起，最多 5 次）
- MarkdownView ```mermaid 围栏块接入；模拟器实测流程图渲染正常（截图 emulator-18，同页含行内公式混排）

## 剩余 Android 模块可行性评估（2026-11-04）

| 模块 | 鸿蒙可行性 | 结论 |
| --- | --- | --- |
| OCR | 需本地推理引擎（桌面/安卓为各自原生模型）；鸿蒙端可用 AI 端点做云端识别替代 | 不承诺离线 OCR；可做"AI 识别当前页图"变体（待评估） |
| Office 转换 | 桌面是 LibreOffice/Office 进程调用；鸿蒙无此能力 | 不在鸿蒙端计划内（文档已声明） |
| 录音转写 | 录音可用 AudioCapturer；离线转写需模型 | 录音可行但价值低，转写需云端；暂不排期 |
| 系统截屏 | 需系统权限/应用分身场景 | 不排期 |
| 悬浮工具 | 需要悬浮窗权限（floating window，平板需授权） | 可评估小窗笔记，优先级低 |
| 素材贴纸 | 纯 UI（图片素材插入笔记），可行 | 与图片附件能力重叠，待需求 |
| 选区/Tag 卡片 | PDF 文本层依赖 PDF Kit（当前模拟器缺）；MD 原文可选中 | 已完成：MD 文字选区/段落制卡 + 标签（normalizeTags 与 Android 契约一致，单测覆盖） |

## 图片插入笔记（2026-11-04 晚，已提交）

- 阅读页「插图」：PhotoViewPicker 选图 → 复制进 `src/assets/insert-<时间戳>.<ext>` → 光标处插入 `![图片](assets/…)`；与 MarkdownView 图片预览同一契约
- 模拟器实测：选择器（安全访问图库）正常弹出（截图 emulator-19）；模拟器图库为空且 shell 无权限向共享媒体目录写文件，选图后的复制/插入未能在设备闭环，真实图片待有图库内容时验证

## 笔记导出/导入（2026-11-04 晚，已提交）

- `core/NoteTransfer.ets`：JSON v1 全量备份（version/document/pages/notes，与桌面逐字段一致）、当前页 Markdown、备份按页合并（合法页覆盖/空串清页/缺失不动）、isBackupJson 识别
- 阅读页「更多」sheet：导出全量/当前页/导入三入口；导出前脏草稿先落盘；picker 保存/读取
- 实测：导出内容逐字段正确（含公式/mermaid 原文）；合并语义离线断言通过（覆盖/清页/新增/非法键）；导入选择器受媒体库索引限制未走完设备闭环
- 待真机：导入全链路（picker 能选到文件即可）

## 横竖屏自适应（2026-11-04 晚，已提交）

- 阅读页重构：左右面板抽取为 @Builder，横屏 Row 左右分栏、竖屏 Column 上下堆叠（onAreaChange 跟踪 isLandscape，layoutWeight 主轴自适应）；左面板分隔线随方向切换（右边框/下边框）
- 导出/导入 sheet 挂到两个分支容器（同一时刻只挂载其一）
- 横屏回归实测通过（搜索/历史 sheet 正常）；竖屏分支编译验证——模拟器 rotate 命令无效、折叠屏模拟器调试通道未连通，竖屏未上机

## 下一步（按序）

1. 雨课堂实时课堂采集的真实课堂验收（重连/去重/笔记保护）
2. 真机或带 PDF Kit 镜像验证 PdfView 与 PDF 项目全链路
3. 其余 Android 模块按优先级继续（选区/Tag 卡片、OCR、闪卡、Office 转换、素材、录音、截屏、悬浮窗）

## 复现构建

```bash
powershell -File harmonyos/setup-links.ps1   # 首次一次
# GBK 控制台下 build.cmd 中文注释会乱码，建议直接用 Git Bash 手动命令（见 .kimi/skills/harmonyos-dev/SKILL.md 第 2 节）
```

产物：`harmonyos/entry/build/default/outputs/default/entry-default-unsigned.hap`（未签名；装模拟器需在 DevEco 自动生成调试签名）。

## 实时协议单测补充（2026-11-09，已提交）

- 新增 3 个用例（共 15 个）：normalizeLiveMessage（hello/fetchtimeline 过滤与当前页补全、slidenav、lessonfinished）、selectTimelineReplay（首连去重/游标续播/缺页标记）、resolveRainLiveSlide（si/sid 定位与未命中报错）
- 实时采集的真实课堂验收仍未可做：当前账号无正在进行的课堂（on-lesson 为空），需真实上课时段再验

## AI 真实调用验证（2026-11-09，已提交）

- 本机起 OpenAI 兼容中继（固定应答），模拟器经宿主机 LAN IP（10.158.13.125:3457）完成真实 HTTP 链路：AI 设置（BaseURL/Key/模型）→ 保存自动触发讲解 → 中继应答在讲解 sheet 正常渲染（截图 emulator-25）
- 顺带修复真 bug：AI 设置 sheet 用 FIT_CONTENT 会裁掉第三个输入框与保存/清空按钮（无法完整配置）→ 改 SheetSize.MEDIUM
- 中继为一次性验证工具，未入库；KNOWN_ISSUES 的"AI 真实调用"项已更新为已验证

## 最终全量回归（2026-11-09，已提交）

最终构建在 MatePad 模拟器全链路复扫：书架 → 项目 → 空页写入保存（修复后）→ 编辑/预览切换 → 搜索 → 闪卡面板，全部正常（截图 emulator-23/24）。至此鸿蒙线在模拟器可验证范围内无任何已知未决缺陷。

## 空页保存 bug 修复（2026-11-09，已提交）

- 现象：向全新空页（第 3 页）写笔记保存失败，状态栏报 `Cannot read property length of undefined`，文件不落盘
- 根因链：设备端 `TextEncoder.encodeInto('')` 返回 **undefined**（宿主 Node 环境返回空数组，故单测未暴露）→ `bytes.length` 崩溃。此前"空输入跳过 update"的修法也不对：不 update 时 digest 输出非标准值
- 修复：`sha256Hex` 对空串直接返回定义摘要常量，不经编码器/cryptoFramework
- 验证：重编后向第 3 页写入并保存成功，文件落盘，hvigor test 仍全过

## 核心层单元测试（2026-11-09，已提交）

- `entry/src/test/List.test.ets`：10 个 Local Test 用例，覆盖 PageNotes 分页/revision、manifest 编解码与非法值拒绝、mergeBackup 合并语义、SM-2 排程四档、雨课堂历史 type=3/14 语义、LESSON_ON、当前课堂 status 过滤
- 运行：`hvigor test`（hypium 经 `ohpm install @ohos/hypium` 加入 entry 依赖）；报告在 `entry/.test/default/outputs/test/reports/`（核心行覆盖约 33%）
- 过程中修正：空串 SHA-256 在无 update 时 cryptoFramework 不返回标准摘要 → 空输入直接返回定义值

## MatePad 最终回归（2026-11-09，已提交）

- 范围决策：暂时只以 MatePad Pro 13 平板模拟器为验收设备（手机/折叠屏模拟器调试通道问题不再绕行，见下方调查记录）
- 雨课堂链路复验（url.URL 构造改 parseURL 后）：登录态、课程列表（4 门）、课堂列表全部正常
- 批注复验（redrawTick 删除后）：笔迹正常，工具箱完整（截图 emulator-22）
- CodeGenie MCP 全量 21 个 .ets 静态检查：deprecatedSymbol/unusedSymbol 清零（仅剩风格建议类提示）
- 全量重编 BUILD SUCCESSFUL，deprecated 警告清零

## 模拟器环境调查记录（2026-11-09）

- `devecocli emulator rotate left/right` 在 MatePad Pro 13 上返回成功但系统 UI 不旋转：snapshot 面板分辨率恒为 2880×1920、内容方向不变。该命令按官方语义是"旋转模拟器"（向虚拟设备发方向事件），平板桌面态未开自动旋转时系统不响应该事件；亦无法排除它只影响宿主窗口呈现。无论哪种情况，此镜像上无法用 CLI 获得竖屏系统 UI。
- 手机/折叠屏模拟器（Pura 90、Mate X7，均 API 24）在本机启动后调试通道（hdc 127.0.0.1:5555）始终未连通，反复等待数分钟无效，与 MatePad 镜像行为不同；竖屏上机验证因此受阻，需在 DevEco Studio Device Manager 中检查这些实例（可能需完成首次启动向导）。

## 静态检查残留项评估（2026-11-09）

CodeGenie 复检仅剩风格建议类提示，逐项评估后的处置：

- `addTryCatch / addAsyncCatch`（72+8 处）：ArkTS 对"可能抛异常的 API"的统一提示，现有 try/catch 已覆盖真实错误路径，不为提示机械加包裹。
- `invalidInitOfList`（6 处）：提示 List 显式初始化宽高；我们的 List 均在 layoutWeight 分配下工作（弹层/分栏内剩余空间），显式 height('100%') 会与 layoutWeight 冲突导致溢出，保持现状。
- `colorConsistentWarning`（2 处）：建议用分层色资源；当前配色与桌面版一致，主题化属后续设计决策，不随工程修复混入。

## 鸿蒙原生 UI 特性批（2026-11-09，已提交）

按 DevEco 一多/交互/沉浸式技能与多设备适配规格（SPEC）落地的第一批原生 UI 特性：

- **系统断点基建**：EntryAbility 在 loadContent 回调内注册 `currentWidthBreakpoint` / `screenWidthVp` / `screenHeightVp` 到 AppStorage，windowSizeChange 持续更新，onWindowStageDestroy 注销（遵守断点技能前置条件一/二）
- **书架重复布局**（RESP-01）：List lanes 按断点 sm 1 列 / md 2 列 / lg+xl 3 列；模拟器横屏实测三列并排（截图 emulator-26-bookshelf-grid）
- **悬停反馈**（INPUT-01）：书架卡片、雨课堂课程/课堂行加 hoverEffect(Highlight)，面向平板外接鼠标/手写笔悬停
- **沉浸式**（方案 B 组件级）：书架标题栏、阅读页工具栏 expandSafeArea([SYSTEM],[TOP])，背景延伸至状态栏区域
- **键盘快捷键**（INPUT-03，2in1）：阅读页 Ctrl+S 保存当前页、Ctrl+F 打开搜索；模拟器 keyEvent 注入实测（Ctrl+F 唤出搜索 sheet，Ctrl+S 后布局树出现"内容未变化"状态文本）
- **手写笔压感**（INPUT-04）：InkCanvas 笔/荧光笔迹按一次笔迹的最大压力调制线宽（0.6x~1.6x，仅 sourceTool=Pen 时生效，手指/鼠标保持原宽）；存储仍是契约 width 数值，跨端互通无损；模拟器无笔，压力曲线本身未验证（编译+逻辑确认）
- **分屏声明**（SPEC-10 P0）：module.json5 声明 supportWindowMode fullscreen+split

## API 26 新特性首批：中文排版（2026-11-09，已提交）

- 调研 API 25/26 相对 24 的新增（SDK d.ts @since 标注 + 官方发布说明）：API 25 无 ArkUI 组件变更；API 26 可用项中 systemMaterial 材质为系统 API（三方不可见）、List/Grid enableEditMode 无场景，选定 **Text/TextArea 的 orphanCharOptimization（避孤字）+ punctuationOverflow（标点悬挂）**，应用于笔记编辑器与 Markdown 预览段落
- **真实崩溃教训**：新属性方法在 API 24 运行时直接 TypeError（jscrash 日志为证），不是静默忽略；最终方案为 `CnTypography.ets` 的 AttributeModifier 条件挂载 + `typeof deviceInfo.apiAvailable` 防御 + 修饰器内 if 语句级守卫（兼容告警清零）
- API 24 模拟器实测：编辑器/预览正常渲染、无新崩溃日志；排版效果本身需 API 26 设备验收
- 经验已沉淀至 `.kimi/skills/harmonyos-dev/SKILL.md` 第 11 节

## 图标按钮化（2026-11-09，已提交）

- 新增 `components/IconButton.ets`：SymbolGlyph + `$r('sys.symbol.*')` 系统符号，灰底常态/彩底激活、accessibilityText 无障碍标签、showDot 脏标记角标；符号名以 HM Symbol 官方图标库（developer.huawei.com/consumer/cn/design/harmonyos-symbol）清单为准
- 覆盖：书架头部（auditorium/doc_text/plus）、雨课堂面板（arrow_clockwise/person_xmark/play_circle/pause_circle/download/chevron_left）、阅读页头部（chevron_left/paintbrush_pointed/moon/ellipsis_circle）、批注工具条（pencil_tip/highlighter_tip/minus/square/circle/character_textbox + undo/trash）、笔记工具条（翻页 chevron、预览 eye/编辑 square_and_pencil、magnifyingglass/arrow_counterclockwise_clock/AI/picture/rectangle_stack/AI_expand/保存 square_and_arrow_down_1 带脏角标）
- 保留文字：表单对话框的取消/确认/登录类按钮（鸿蒙设计规范对话框操作钮为文字）；批注粗细（细/中/粗）为参数标签
- 实测修了两个真实 bug：① IconButton 的 active/enabledState/showDot 未用 @Prop 导致状态冻结（与 InkCanvas 当年同型）；② EntryAbility onWindowStageDestroy 里 window.off 在窗口已销毁时抛 1300002 jscrash（install -r 杀进程触发），已 try/catch
- API 24 模拟器实测：全部符号正确渲染无占位符（截图 emulator-27-icon-buttons）；激活态蓝底、脏角标、禁用透明度均验证；单测 16/16
- 悬停文字提示（同日补充）：IconButton 内 onHover + 条件渲染深色小气泡（150ms 淡入），悬停才显示 label；不用 bindPopup（点击会连带触发）；气泡统一向下浮出（工具条都在面板顶排，向上会被窗口/弹层上沿裁掉）；悬停视觉需实体鼠标/手写笔验收

## 已知边界

- 已在 MatePad Pro 13 模拟器逐项运行验证（见上文各节）；仍未验证：PdfView 真实渲染（镜像无 PDF Kit）、竖屏上机、实时课堂采集（需真实上课时段）、手写笔压力曲线与悬停视觉（需实体笔/鼠标）、分屏模式实机运行
- 桌面级功能（OCR、Office 转换、Markdown S）未在鸿蒙端计划内承诺

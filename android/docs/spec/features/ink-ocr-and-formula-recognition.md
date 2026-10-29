# Handwriting OCR and Formula Recognition（移植批次 6）

- 状态：已实施（分支 `codex/port-canvas-notes-features`，移植差距清单 §八）
- 范围：手写墨迹 → 可编辑文本（选区→识别→插入、OcrConvert 撤销、转写对话框、识别语言/模型设置），以及手写公式 → LaTeX（pix2tex 类开源模型选型→ONNX 引擎、束搜候选、预览修正对话框、插入笔记块公式/画布公式素材）。闭源 native 识别引擎不可移植，全部替换为开源离线模型。

## 1. 手写 OCR（笔迹转文字）

### 1.1 墨迹 → 位图（InkBitmapComposer / InkOcrSelection）

- 选区复用区域 OCR 的状态机（`ReaderOcrState`：SELECTING→RECOGNIZING→RESULT）与 `PdfPageViewport` 单一选区通道：三种模式（区域 OCR / 手写 OCR / 公式识别）互斥，按活跃模式路由选区手势与覆盖层（`routeOcrSelection`/`activeOcrSelectionOverlay`）。
- 坐标契约：选区是 0..1000 归一化页坐标（x 按页宽、y 按页高归一化，`mapPdfSelectionToOcrRegion`）；笔迹文档空间 y 上限是 `1000×页高/页宽`（`PenCanvasGeometry.pageMaxY` 语义）。`InkOcrSelection.documentRect(region, aspect)` 做这层逆变换，选中判定复用批次 1 的 `PenRectHit`（polyline/段相交，pen/highlight 之外的类型不参与）。
- 位图化：`InkBitmapComposer.render` 把选区内 pen/highlight 笔画按文档单位→像素比例绘制到白底 ARGB 位图（长边约 1280px、四周 16px 边距，压感宽度与荧光笔 alpha 与画布一致），作为 `OfflineOcrEngine` 的输入；调用方位图所有权不变。

### 1.2 选区 → 识别 → 插入（数据管线 InkOcrConversion / canonicalItemKey）

- 识别输入来自内存笔迹模型（`penStrokes`，含尚未落盘的笔画）；删除目标以**内容指纹**记录（`canonicalItemKey`：Map 规范化字符串，键序无关、整型化数值差异（`3` vs `3.0`）无关），落盘时按指纹匹配最新页 item，容忍识别-确认窗口内的下标漂移。
- 落盘（`MainActivity.commitInkConversion`，手写文本与公式素材共用）：`penSaveMutex` 锁内读最新页 → `InkOcrConversion.convertPage`（指纹删 pen/highlight + 追加产物）→ 整页写回（`ProjectAnnotationStore.writePage`）→ `annotationEditHistory.record`（一个快照 = 一个撤销步）→ 同步内存模型：批注页/overlay/选中态 + `PenPageStore.reloadPage`（新增 API：外部整页替换后重建笔迹页并作废该页笔迹本地撤销栈）。
- 转写产物是 `type:"text"` 项（批次 4 样式 schema）：锚点=选区左上角，字号按选区高度推导并夹在 20..500，左对齐、缺省字体（`InkOcrConversion.buildTextItem`）。

### 1.3 OcrConvert 撤销（AnnotationOcrConvertAction）

- 统一栈 PenAction 适配器（注册 id `pdf.ocrConvert`，scope `pdf:<projectId>:<page>`）：一次转换 = 页 before/after 快照，do/undo/redo 重放整页；连续转换不合并（每次独立撤销步）。与批次 1/4 适配器同构；阅读器 UI 撤销仍走既有 `PdfAnnotationEditHistory` 快照路径，两路径不混用。
- 转写对话框的"移除原笔迹"开关缺省开启（对齐 touchnotes 笔迹转文字语义）；关闭时仅追加文本项（`convertPage` 空指纹路径）。

### 1.4 转写对话框与设置

- `InkOcrTranscribeDialog`：识别文本可编辑（行按块拼合）、状态/低置信度警示显式呈现（复用 `OcrResultPolicy.needsVisualReview`）、确认后走 §1.2 落盘。
- 设置（`ReaderSettings` 新增 `ocrLanguage`/`latexOcrModel`，DataStore 持久化，阅读设置对话框新增"OCR 识别"节）：识别语言 system/zh/en 经 `OcrModelCatalog.resolveForLanguage` 解析为承载模型（随包 PP-OCR 轻量模型 zh+en 混合，语言回落不失败）；`OfflineOcrEngine.create` 增加模型参数（缺省随包模型，既有调用点行为不变），模型切换时引擎重建（`ensureOcrEngine` 跟踪已装载模型 id）。

## 2. 公式识别（手写公式 → LaTeX）

### 2.1 模型选型（LatexModelCatalog，pix2tex 类 → ONNX）

| 候选 | 许可 | 形态 | 结论 |
| --- | --- | --- | --- |
| Mathpix | 闭源商业 API | 云服务 | 排除：离线 OCR 契约禁止网络调用 |
| **pix2tex**（Image-to-Markup，Lukas Blecher） | MIT | ViT 编码器 + 自回归 transformer 解码器 | **选定**：导出 encoder/decoder 两个 ONNX 图 + tokens.json 词典 |
| texify | Apache-2.0 | GGUF/llama 本地栈 | 备选：运行时栈与现有 ONNX Runtime 不一致，留作后续 |

- 部署契约：`models/formula/pix2tex-vit/{encoder,decoder}.onnx + tokens.json`（词典=字符串数组，下标即 id，保留前缀 0..2=`<pad>`/`<bos>`/`<eos>`）。**仓库本批次未捆绑模型二进制**（无法在本环境验证导出产物数值正确性）；资产缺失时 `LatexOcrEngine.create` 抛出带路径的 `OfflineOcrException`，UI 呈现"模型未捆绑、可手动输入修正"，插入链路在无模型时仍完整可用。

### 2.2 引擎与候选（feature-ocr）

- `LatexOcrEngine`：结构对齐 `OfflineOcrEngine`——IO 线程装载、互斥串行推理、失败折叠为 `failed` 结果 + warning（不抛出）、close 幂等；全量注入构造器（`withInference`）供 JVM 测试。
- 预处理 `LatexPreprocessor`/`LatexImageLayout`：灰度→保持纵横比缩放（高 192px、宽上限 672px、右/下补背景）→ CHW float，归一化常数沿用 pix2tex `TransformImage`（mean 0.7931 / std 0.1738，纸白墨黑不反转）。
- 解码 `LatexBeamDecoder`：每步 log-softmax 累计、束宽 4、EOS 终止、去重、长度惩罚（GNMT 式 `logProb/len^α`）、超步数截断收尾；输出排序候选（token 序列 + 归一化置信度）。
- ONNX 编排：encoder 单跑得 memory；decoder 按步把全部存活束合成一个 batch（tokens (B,L) int64 + memory），取每行最后位置 logits（`copyLastPositionLogits`，纯 JVM 已测）。

### 2.3 预览修正对话框与插入

- `FormulaOcrDialog`：候选 chips（置信度百分比，点选载入）、LaTeX 可编辑、离线预览复用 JLatexMath（`FormulaMaterialRenderer`，与公式素材/笔记渲染同源；渲染失败显式占位不阻塞修正）；无模型/失败时仍可手动输入。
- 插入笔记：`MarkdownLatexBlockInsertion`（与桌面 `$$` 块模板逐字符一致，替换当前选区，正文=修正后 LaTeX；UTF-16 选区计算与 `MarkdownFormulaInsertion` 同规则）。
- 插入画布（公式素材，D7 schema）：LaTeX → `FormulaMaterialRenderer` 位图 → 缓存临时 PNG → 既有 `ProjectAttachmentStore` 附件导入 → `FormulaMaterialInsertion.createItem`（image 项 + `latex`/`latexColor` 扩展字段，锚点=选区左上角、显示宽 350 文档单位）→ `commitInkConversion`（可选指纹删笔迹，同 §1.2 一条撤销步）。可选"同时保存到素材库"：`materials.json` upsert（kind=FORMULA，失败仅告警不影响画布落盘）。

## 3. Verification

- `cd android && ./gradlew :feature-ocr:testDebugUnitTest :feature-pen:testDebugUnitTest :feature-annotations:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :feature-ocr:compileDebugAndroidTestKotlin :app:assembleDebug`
- 批次结果：feature-ocr 44/44（新增 LatexTokenizerTest 7、LatexBeamDecoderTest 5、LatexImageLayoutTest 5、LatexModelCatalogTest/OcrModelCatalogTest 5）；feature-pen 109/109（新增 PenPageStoreReloadTest 5）；feature-annotations 74/74 回归；app 454/454（新增 InkOcrPipelineTest 12、AnnotationOcrConvertActionTest 4、FormulaMaterialInsertionTest 4、MarkdownLatexBlockInsertionTest 4，扩展 ReaderSettingsTest（+2）、DesktopCommandChecklistTest）；androidTest 源码编译与 assembleDebug 通过。

## 4. 未覆盖（Not covered）

- **pix2tex ONNX 模型二进制未随仓库捆绑**：引擎/词典/束搜/张量编排均已实现并经注入式 JVM 测试，但真实 encoder/decoder ONNX 与 tokens.json 需按 §2.1 契约从 pix2tex MIT 权重导出后放入 `feature-ocr/src/main/assets/models/formula/pix2tex-vit/` 才能真机推理；导出脚本与数值对齐（含预处理反转细节校准）留待模型落地批次。
- 真机/AVD 交互回归未运行（JVM 无法覆盖触摸选区、位图渲染与 WebView 级 UI）：框选、识别等待、对话框确认、撤销恢复笔迹的端到端验收沿用 AVD 验证模式留给后续批次。
- 手写识别质量受随包 PP-OCR 模型能力限制（面向印刷体优化）；换装手写专用 PP-OCR/HWNet 类 rec 模型只需在 `OcrModelCatalog` 追加条目，不改变设置与引擎接口。
- 桌面端不解释 `ocrLanguage`/`latexOcrModel` 设置（应用本地 DataStore，不进项目文件）；公式素材的桌面渲染仍按附件位图（D7 既定容忍点）。

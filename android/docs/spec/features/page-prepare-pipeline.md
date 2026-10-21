# 页面准备管线：区域 PNG、Markdown 表格、统一调用

## Scope

把桌面 `preparePageData` + `createPagePreparer` 的剩余语义接到安卓端：区域 PNG 生成、Markdown 表格抽取、缓存失效与刷新重试，并由页面准备流程统一调用。OCR 推理本身已有，不在本片内重做；Mermaid PNG 渲染暂不移植（只保留源码透出），原因记入 Non-goals。

## Region PNG Contract

- 输入为整页渲染位图与 0..1000 归一化区域；像素矩形用 floor(left/top)、ceil(right/bottom)，裁剪到Bitmap
 bounds，零面积或完全越界抛 IllegalArgumentException。
- 与桌面 sharp.extract 语义对齐：left/top 向下取整保证覆盖，width/height 至少 1px 且不超右/下界。
- 调用方原图 ownership 不变；裁剪出的 region Bitmap 由调用方负责 recycle（已有 `RegionOcrProcessor.encodeImages` 为范例）。

## Markdown Table Contract

- 对 Markdown 笔记页抽取 GFM 表格：headers 为表头文本数组，rows 为每行单元格文本数组，source 保留表格源码。
- 对齐桌面 Marked lexer 语义：只有合法表头分隔行（`---|---` 及其变体）才算表格；分隔行不对齐的按普通文本忽略。
- 单元格内行内 markdown（加粗、行内代码、链接）只取纯文本，不保留标记符号。
- 无表格返回空列表，不抛错；表格只用于 Agent 上下文与准备缓存 `tables.json`，不改变笔记原文。

## Pipeline Contract

- 统一入口 `PagePreparePipeline.prepare(page, region, mode, refresh)`：先按 `PreparedCache.cacheKey` 查项目缓存（复用 `PreparedRegionCacheStore`），命中直接返回；`refresh=true` 跳过读取强制重做。
- 未命中时按源类型分支：PDF 走原生文字→区域裁剪→按需 OCR（复用既有引擎与超时策略）；Markdown 走全文→表格抽取（mermaid 只记 source，不渲染 PNG，记一条 warning）。
- 写缓存时 manifest 最后写；`tables.json` 与 `text.txt` 同桌面字段落盘；失败不丢原文结果，记 warning。
- 准备期间原文变化（sourceRevision 漂移）抛错重试，不写脏缓存。

## Wiring Status

- `RegionOcrProcessor.recognize(..., refresh)` 即区域 OCR 的统一调用点：`refresh=false` 先读缓存命中直返，`refresh=true` 跳读强写；`sourceRevision == null` 跳过读写并记 `notCachedWarning`。
- PDF 区域 OCR manifest 记录与位图一致的 `PdfPagePolicy.renderScale`，并由 OCR 文本与 blocks 生成 `ocrCharacters`、`textCharacters`、`blockCount`。
- OCR 推理和区域 PNG 编码当前顺序各裁一次区域图；共享裁剪对象的性能优化待真机 profiling 证明有收益后再做。
- `PagePreparePipeline`（core-reader 纯内存）只定缓存语义：命中直返 / refresh 重做 / revision 漂移拒用旧缓存，不直接碰 SAF；SAF 读写仍由 `RegionOcrProcessor` + `PreparedRegionCacheStore` 执行。
- Markdown 表格抽取（`MarkdownTables`）暂只用于 Agent 上下文组装，不写入区域 OCR 缓存（该缓存 `kind == "pdf"`）；`tables.json` / `tableCount` 待 Markdown 准备缓存独立切片再接，本版不伪称已落盘。

## Non-goals

- Mermaid PNG/SVG 渲染不移植：移动端无 headless 浏览器管线，源码透出 + warning 已满足 Agent 可读需求。
- 此页面准备切片不承担 Office 文件转换；Office 原文件到可分页预览的流程由 `desktop-ui-and-office-parity.md` 定义的独立 `OfficeEditorAdapter` 负责，本模块只消费其派生页图/文字并维护自己的缓存语义。

## Acceptance (EARS)

- When 给定整页图与有效区域时，管线 shall 生成与桌面同语义的区域 PNG 矩形（floor/ceil + 裁剪）。
- When 准备 Markdown 页时，管线 shall 抽出全部 GFM 表格（headers/rows/source）并计入 tableCount。
- When 缓存命中且未要求 refresh 时，管线 shall 直接返回缓存而不跑推理。
- When 要求 refresh 或缓存失效时，管线 shall 重做并覆盖，manifest 最后写。

## Verification

- JVM：区域矩形边界（非整除、边缘裁剪、零面积拒绝）、Markdown 表格抽取（多表、无表、非法分隔行、行内标记 stripping）。
- `cd android && ./gradlew :core-reader:testDebugUnitTest :feature-ocr:testDebugUnitTest --offline` 通过。

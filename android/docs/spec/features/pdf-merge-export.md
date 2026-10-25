# Android PDF 页合并导出 + convert note

**Status:** 已实施（分支 `codex/port-canvas-notes-features`，移植差距清单 #15 的 Android 侧导出增强）。
桌面无对应命令；这是 Android 端新增的导出能力（`desktop-command-checklist.md` 已标注）。

## 行为

- `File > 合并导出 PDF`（命令 id `export_merged_pdf`）：仅 PDF 项目可用；打开对话框预填 `1-总页数`。
- **页码范围**：接受 `1-3,5` 形式（含中文顿号/逗号分隔），升序去重合并；空、越界、倒序区间、非法记号直接报"页码范围无效"，不做静默截断。
- **convert note（附加笔记页）**：勾选后，为每个所选页把该页笔记 Markdown 保守剥离为纯文本（标题/引用/列表前缀、行内强调、链接、图片 alt、围栏），用 Android 系统字体（支持 CJK）按源页 MediaBox 尺寸排版为 PDF 页，逐字测量断行、按行数分页，插入在对应源页之后。空白笔记不产生笔记页。
- 合并核心用 PDFBox：所选源页按给定顺序拼入新文档，笔记页 PDF 字节随后插入；importPage 与源文档共享页资源，所有参与文档保持打开直至保存完成；越界页抛错而非静默丢弃；空笔记字节条目跳过。
- 快照语义：与笔记导出一致——先按 revision 流程保存脏页，再取笔记快照；导出内容不随后续编辑变化。
- 输出走系统 `CreateDocument("application/pdf")`（建议名 `<项目名>-合并页.pdf`，非法文件名字符替换为下划线）；导出先落 app 缓存临时文件，SAF 结果写入后清理。取消选择器或写入失败时清理临时文件并如实报告，不修改项目文件。
- 加密/口令项目照常打开后使用（笔记已在解锁态）；不额外复制批注/笔迹/资产，仅源页 + 笔记文本页。

## 验证

- `PdfPageMergePlanTest`：范围/单页/去重/中文分隔/空与非法与越界与倒序拒绝/零页文档。
- `PdfNotesMergeExporterTest`：按给定顺序合并、笔记页紧跟对应源页（MediaBox 尺寸标识页面）、空笔记条目跳过、越界页拒绝。标准十四字体的 JVM 单测环境缺 pdfbox-android 的 AFM 资源（Android 上由 `PDFBoxResourceLoader` 注入），故用页面几何断言；笔记页文本渲染（`NotePagePdfRenderer`，android.graphics.pdf）由 Android 运行时承担，本批次未做 AVD 仪器验证。
- `NotePlainTextTest`（feature-notes）：Markdown 剥离/围栏/空输入/行内标记。
- `:app:assembleDebug` 与 AndroidTest 源码编译通过；SAF 端到端（对话框→选择器→写出）未在 AVD 复跑。

## 关联

- 基线契约：`note-export.md`（快照语义、SAF 恢复模式沿用其思路；不引入挂起快照跨进程恢复）。
- 任务：`../tasks.md`（移植差距清单 #15）。

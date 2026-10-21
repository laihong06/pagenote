# 超长笔记页预览截断

## Scope

Task 5 切片：笔记预览（Markwon 左窗等同）对超长单页做显式截断呈现，避免一次装入全文导致卡顿或误以为内容丢失。本片只做呈现层截断，编辑态仍为整页原文，不分片保存。

## Behavior

- 阈值：复用 `NotePage.PREVIEW_LIMIT`（8000 字符），与 `NotePage.preview` / `NoteRange.slice` 语义一致。
- 预览：超过阈值的页只渲染前 8000 字符，并在预览底部展示截断提示（含原文总长度与已预览长度）。
- 编辑：EditText 仍加载整页原文，可正常编辑保存；截断只影响预览渲染，不影响 revision 计算与落盘。
- 统计：富块透出统计走全文，不受预览截断影响（沿用 note-rich-render 约定）。
- Markdown 源左窗：沿用同一阈值与提示文案（已落地 `MarkdownSourcePane` 的 preview/truncated 分支，本片统一文案与阈值来源）。

## Acceptance (EARS)

- When 预览页超过 8000 字符时，shall 只渲染前部并展示截断提示（含总长度）。
- When 预览页未超限时，shall 不展示截断提示。
- When 用户切换到编辑时，shall 仍可编辑整页原文且保存语义不变。

## Verification

- JVM：`NotePage.preview` 长/短页用例已存在；本片补预览提示文案的仪器编译覆盖。
- `cd android && ./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --offline` 通过；真机待 task 12。

# 区域批注创建与列表

## Scope

Task 6 切片：补 `rect` 类型区域批注的创建、列表、删除 UI。复用既有批注模式点选与 OCR 选区坐标链路，对齐桌面 `points: [p1, p2]` 对角语义。`ellipse/line/arrow/image/html` 仍只读展示，不在本片新增创建入口。

## Behavior

- 批注模式下点选落点后，文本输入框新增类型选择：文字（单点 text）与区域（对角 rect，默认笔色、宽 3.0）。
- 区域条目：`type=rect`，`points=[[x1,y1],[x2,y2]]`（顺序按落点与拖动终点归一化为左上+右下，零面积拒绝），`text` 可空（≤10000，空则列表显示类型名）。
- 既有 OCR 选区（`ReaderOcrState.SELECTING`）不直接生成批注：选区仅用于 OCR，避免手势冲突；区域批注仍走批注模式点选两次（起点+终点）或单点默认 200x120 大小。
- 列表：`rect` 与 `text` 同列可删；其它类型只读展示。删除二次确认，沿用全量页写回。
- Markdown 源态禁用批注模式入口（沿用既有 `isMarkdownReading` 禁用语义）。

## Acceptance (EARS)

- When 在批注模式确认区域批注时，shall 按对角两点落盘 rect 条目并保留其它条目。
- When 区域为零面积时，shall 拒绝创建并提示，不落盘。
- When 列表含 rect 时，shall 可删除且只移除目标条目。

## Verification

- JVM：rect 生成校验（归一化、零面积拒绝、文本截断）。
- 仪器编译：批注弹窗与删除链路复用既有 tags。
- `cd android && ./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --offline` 通过；真机落点精度待 task 12。

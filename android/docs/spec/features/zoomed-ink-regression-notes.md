# Zoomed Ink Regression Notes

Read-only regression companion to `zoomed-ink-and-extended-canvas.md`.
No runtime behavior is changed by this note.

## Audited sources

- `android/docs/spec/features/zoomed-ink-and-extended-canvas.md`: EARS and coordinate contract.
- `android/feature-pen/src/main/java/io/pagenote/feature/pen/PenCanvasGeometry.kt`: outside-page clamp logic.
- `android/app/src/main/java/io/pagenote/android/ReaderSettingsStore.kt`: `allowInkOutsidePage` persistence.
- Supporting defaults: `android/app/src/main/java/io/pagenote/android/ReaderSettings.kt`.

## Regression要点

1. 逆变换采集 / 正变换渲染命中
   - 输入采集走视口逆变换：`PenCanvasGeometry.toDocumentPoint()` 先减去页面原点，再除以 `pixelsPerDocumentUnit`。
   - 渲染与选择/擦除走正变换：`toViewX()` / `toViewY()` 把已存储点映射回屏幕；命中测试用屏幕空间距离判定。
   - 回归时重点检查缩放/平移后“落笔位置 = 渲染位置”，以及保存重载后仍对齐。
2. 页内默认钳制
   - `allowOutsidePage=false` 为默认；此时 `toDocumentPoint()` 默认 `clampToPage=true`。
   - X 钳制到 `[0, 1000]`，Y 钳制到 `[0, pageMaxY]`，其中 `pageMaxY = pageHeightPx * 1000 / pageWidthPx`。
   - 画布表面覆盖阅读器视口并裁剪到视口，但不裁剪到变换后的 PDF 页面矩形；页外可见边距的输入在默认模式下应钳制到最近页边。
3. 开关仅影响新输入
   - `allowInkOutsidePage=true` 只允许新输入保留可见边距坐标；已存在的页外点保持可读，不重写。
   - 页码变更、视口变更不得改写已存储坐标。
   - 页外新点仍在同一页面坐标空间内，并受已有绝对坐标校验上限约束。
4. 设置持久化
   - `ReaderSettings.allowInkOutsidePage` 默认 `false`。
   - `ReaderSettingsStore` 用 DataStore 键 `allow_ink_outside_page` 读写该开关；保存后重新打开应用应恢复选择。
   - 该开关属于应用设置，不写入项目文件。
5. 坐标格式不变
   - 仍使用页面宽度归一化的 1000 单位坐标空间；Y 不按高度归一化，纵向页面可超过 1000。
   - 压力值沿用现有笔迹校验/默认值规则；序列化仍沿用 `annotations.json` 形状。

## 已有单测与构建证据引用（未重跑）

- 几何往返：`PenCanvasGeometryTest.mapsViewportPointsThroughZoomAndPanAndRendersBackToSameScreenLocation`。
- 页外开关行为：`PenCanvasGeometryTest.extendedCanvasStoresVisibleMarginCoordinatesOnlyWhenEnabled`。
- 变换后命中：`PenCanvasGeometryTest.hitTestingUsesScreenSpaceAfterViewportTransform`。
- 命中回归：`hitTestFindsTopmostFreehandStrokeWithinPixelRadius`、`hitTestFollowsRectangleAndEllipseOutlines`、`hitTestIncludesArrowHeadButIgnoresNonCanvasAnnotations`。
- 默认与持久化：`ReaderSettingsStoreTest.allowInkOutsidePageDefaultsOffAndSurvivesOpeningAnotherStore`、`ReaderInteractionDefaultsTest` 中的默认关闭断言。
- 构建证据：沿用父规格 `Verification` 段声明的 JVM 几何测试、codec/persistence 测试与 API 34 平板 AVD 覆盖声明；本笔记未重新执行构建或测试。

## Worker 边界

- 本次写集仅限新建本文档；未修改 Kotlin/Java/XML，未碰桌面 Mac/Windows 打包与 `release/`。
- 未跑构建，未 commit。

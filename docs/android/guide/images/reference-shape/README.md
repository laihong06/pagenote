# 参考直线识形运行截图

- `reference-line.png`：Android 14 API 34、ARM64 `Pixel_Tablet_API34_Acceptance` AVD、横屏 2560 × 1600、320 dpi、浅色主题，使用测试生成的 PDF。
- `PdfAnnotationSelectionClipboardActivityTest.referenceSampledLinePersistsAndUndoRemovesIt` 注入多段 stylus `MotionEvent` 并停留；截图在原生直线落盘后、撤销前采集。测试读取页面数据核对采样端点并检查一次撤销清除直线。运行产品代码 commit `0458e5b`。
- 此图是横屏平板上的运行结果，不含改前截图，也不代表实体触控笔、其他设备或参考应用运行时差分；桌面往返和维护者视觉对比尚未完成。

## 参考折线运行截图

- `reference-polyline.png`：Android 14 API 34、ARM64 `Pixel_Tablet_API34_Acceptance` AVD、横屏 2560 × 1600、320 dpi，合成 PDF 项目。`PdfAnnotationSelectionClipboardActivityTest.referencePolylinePersistsAndUndoesAsOneStroke` 模拟先横后竖的 stylus 输入；截图在单个 `pen` 对象保存后、撤销前采集。运行产品代码 commit `0458e5b`。
- 截图包含测试工作区和打开的菜单面板。端到端测试检查落盘对象仍为带 `pagenotePolyline` 的多点 `pen`，一次撤销后对象被移除。实体触控笔、真机、桌面往返和参考 App 运行时对比仍未验收；此图不证明曲线、闭合图形或多材料选择。

## 参考曲线运行截图

- `reference-curve.png`：Android 14 API 34、ARM64 `Pixel_Tablet_API34_Acceptance` AVD、横屏 2560 × 1600、320 dpi，合成 PDF 项目。`PdfAnnotationSelectionClipboardActivityTest.referenceCurvePersistsAndUndoesAsOneStroke` 输入弧线；图中为参考 `BezierTool` 路径保存后的曲线。运行产品代码 commit `0458e5b`。
- 阅读器端到端测试确认保存为 33 点 `pen` 对象并可一次撤销。截图来自菜单切换附近，包含打开的菜单面板；它记录实际运行状态，不代表最终菜单视觉对比。实体触控笔、桌面往返和参考 App 运行时差分仍未验收。

## 停留识形与异步保存的撤销顺序

- 验收用例 `REF-HISTORY-02`：`PdfAnnotationSelectionClipboardActivityTest.lateShapeSaveDoesNotOverrideNewerInkGestureOrder`。锁住异步笔迹保存，先完成一条停留识别直线，再写入较新的自由笔迹并放开保存队列；撤销一次应只移除后写的自由笔迹，保留直线。
- 回归确认识形动作先预留历史位置，原生图形写入完成后再完成该历史事务；写入中的最新事务不会使撤销落到更旧操作。运行环境为 Android 14 API 34、ARM64 `Pixel_Tablet_API34_Acceptance` AVD、横屏 2560 × 1600、320 dpi。合成 stylus 输入，不是实体手写笔。
- 该修正只保证这类识形图形与后续笔迹的撤销顺序，不代表参考 App 的统一动作栈已完整移植；真机和桌面往返尚未验收。

## 参考闭合椭圆识形（横屏平板）

- 运行环境：Android 14 API 34、ARM64 `Pixel_Tablet_API34_Acceptance` AVD、横屏 2560 × 1600、320 dpi、浅色主题。页面为测试生成的 PDF；产品运行代码 commit `0458e5b`。
- `reference-closed-ellipse-before.png` 是阅读器启用钢笔后、尚未输入闭合笔迹的状态；`reference-closed-ellipse.png` 是合成 stylus 输入停留识别后保存的椭圆。二者均由 `PdfAnnotationSelectionClipboardActivityTest.referenceClosedEllipsePersistsAsOneClosedPolylineAndUndoes` 从真实应用运行中采集。
- 用例检查磁盘页面中仅有一个 `type: "pen"` 对象、带 `pagenotePolyline` 字段、首尾闭合，并检查一次撤销清空该笔迹。输入是仪器测试生成的触控笔事件，不代表实体触控笔。桌面重开保存往返、参考 App 运行时差分尚未验收。

识别前：

![横屏平板中启用钢笔、尚未画闭合图形](reference-closed-ellipse-before.png)

识别后：

![横屏平板中识别并保存的闭合椭圆](reference-closed-ellipse.png)

## 原生矢量图形区域擦除

- Android 14 API 34、ARM64 `Pixel_Tablet_API34_Acceptance` AVD、横屏 2560 × 1600、320 dpi、浅色主题；项目使用测试生成的 PDF，在 `annotations.json` 预置原生矩形。运行代码 commit `0458e5b`。
- `reference-area-erase-native-before.png` 显示擦除前的完整矩形；`reference-area-erase-native-erasing.png` 显示手势中途的圆形边界及矩形上边的擦除缺口；`reference-area-erase-native-after.png` 显示擦除后保存的线段；`reference-area-erase-native-undo.png` 显示菜单撤销恢复的原矩形。
- 由 `PdfAnnotationSelectionClipboardActivityTest.areaEraserCutsNativeRectangleAnnotationAndUndoRestoresIt` 运行时采集。测试通过 stylus `MotionEvent` 注入真实 `PenCanvasView`，并检查磁盘页面数据和一次撤销；这是模拟器交互证据，不是实体触控笔验收。关闭保存成功提示后，撤销截图中没有常规保存提示。

![擦除前](reference-area-erase-native-before.png)
![橡皮擦手势中，显示边界与缺口](reference-area-erase-native-erasing.png)
![擦除后](reference-area-erase-native-after.png)
![撤销恢复后](reference-area-erase-native-undo.png)

## 原生图形与自由笔迹混合撤销顺序

- `reference-mixed-history-before.png` 与 `reference-mixed-history-undone.png` 由 `PdfAnnotationSelectionClipboardActivityTest.interleavedShapesAndInkUndoInGestureOrder` 在 Android 14 API 34 ARM64 `Pixel_Tablet_API34_Acceptance` AVD、横屏 2560 × 1600、320 dpi 上采集；运行代码为 `0458e5b`。测试在同一页依次写入原生图形、自由笔迹、原生图形，再连续撤销三次并重做三次。识别手势由多段 stylus 事件采样后停留触发。
- 测试检查每一步的 `annotations.json` 类型数量；三次撤销后页面对象为空，截图显示画布已清空。重做后恢复到两个原生图形和一个自由笔迹。输入为注入到真实阅读器 `PenCanvasView` 的合成 stylus `MotionEvent`，不是实体触控笔。
- 该实现将操作顺序路由到 Android 现有的批注历史和笔迹历史，未移植参考应用的中心 `ActionStack` 对象模型。区域橡皮跨层单步事务已有单独验证；其他跨层操作、保存失败竞态、OCR 与进程重建仍未完成验证。

初始状态（图形—笔迹—图形）：

![原生图形与笔迹交错编辑](reference-mixed-history-before.png)

连续撤销三次后的空白画布：

![三次撤销后画布为空](reference-mixed-history-undone.png)

## 跨层区域擦除单步撤销与重做

- 验收用例 `REF-ERASE-03`：`PdfAnnotationSelectionClipboardActivityTest.oneAreaEraseGestureAcrossFreehandAndNativeShapeIsOneUndoStep`。在同一条连续区域橡皮手势中擦过原生矩形和自由笔迹，检查两层都被切开；一次撤销恢复两层，一次重做再次擦除。
- 运行环境：Android 14/API 34、ARM64 `Pixel_Tablet_API34_Acceptance` AVD、横屏 2560 × 1600、320 dpi、浅色主题；测试生成的 PDF、合成 stylus `MotionEvent`，不是实体触控笔。运行代码 commit `0458e5b`。
- 四张完整屏幕截图来自应用运行中的 `UiAutomation.takeScreenshot()`：`reference-area-erase-mixed-before.png`（擦除前）、`reference-area-erase-mixed-erased.png`（一条手势擦除两层）、`reference-area-erase-mixed-undo.png`（一次撤销恢复矩形和笔迹）、`reference-area-erase-mixed-redo.png`（一次重做再次擦除）。
- 页面文件格式不变。实现只把同一擦除手势关联的笔迹历史和原生批注历史作为一个用户可见撤销项；它仍使用 Android 两套页面快照，并未完整移植参考项目的中心 `ActionStack`。其他跨层编辑、失败注入、进程重启和真机笔迹尚未验证。

擦除前：

![跨层区域擦除前的横屏平板画面](reference-area-erase-mixed-before.png)

擦除后：

![一次区域橡皮手势擦除原生矩形和自由笔迹](reference-area-erase-mixed-erased.png)

一次撤销恢复：

![一次撤销恢复跨层擦除前的矩形与笔迹](reference-area-erase-mixed-undo.png)

一次重做：

![一次重做再次擦除矩形与笔迹](reference-area-erase-mixed-redo.png)

## 参考区域多材料选择

- 验收用例 `REF-SELECT-01`：`PdfAnnotationSelectionClipboardActivityTest.referenceRegionSelectionDeletesMixedMaterialsAndUndoesAsOneAction`。在选择工具下套索选中两段自由笔迹和一个原生矩形，删除后执行一次撤销；用例检查页面数据为空并由一次撤销恢复原始三项。
- 回归用例 `REF-SELECT-02`：`InkSelectionClipboardActivityTest.selectCopyPasteDeleteAndUndoOperateOnCurrentPdfPage`。选中一段笔迹、复制并粘贴，再删除粘贴项；检查旧选区不会残留，并验证一次撤销／重做和页面持久化。
- 运行环境：Android 14/API 34、ARM64 `Pixel_Tablet_API34_Acceptance` AVD，横屏 2560 × 1600、320 dpi、浅色界面。运行代码 commit：`0458e5b`。测试使用合成 PDF 和注入到真实阅读器 `PenCanvasView` 的 stylus `MotionEvent`，不是实体手写笔。
- 四张 2560 × 1600 完整屏幕图均由上述 Activity 用例通过 Android `UiAutomation.takeScreenshot()` 从运行中的应用采集；不是拼接或设计稿。用例本身和笔迹模块 54 项设备测试在该横屏平板通过。全量 App 设备套件本次未完成：运行到 99/276 时已观察到 8 项失败后停止；其中单选复制粘贴回归已修复并单独重跑通过，剩余 7 项涉及书架窄屏导航节点、Markdown/Mermaid 预览超时、3D 截图像素差、笔记导入导出菜单节点和长 Markdown 编辑超时，尚未定位或单独复验。
- 选择逻辑参照恢复源码中的 `Selector.selectMaterials`、`Material.isInRegion` 和 `SelectionManager.deleteMaterialsAction`：框选材料并聚合选择边界，删除生成可撤销操作。当前适配只实现混合材料的套索选择、删除及一次撤销；组复制、移动和样式编辑尚未实现，不能据此声称完整移植了参考项目选择器。Android 以外的平台与项目文件格式未改动。

套索前：

![横屏平板上启用选择工具，页面有两段笔迹和一个矩形](reference-region-selection-before.png)

套索选中两段笔迹和原生矩形：

![横屏平板上的混合材料聚合选框](reference-region-selection-selected.png)

删除后：

![横屏平板上的材料删除结果](reference-region-selection-deleted.png)

一次撤销后恢复三项材料：

![横屏平板上的混合材料撤销结果](reference-region-selection-undo.png)

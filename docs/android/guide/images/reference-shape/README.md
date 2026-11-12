# 参考直线识形运行截图

- `reference-line.png`：Android 14 ARM64 模拟器、1080 × 2400、420 dpi、英语浅色主题，使用合成 PDF。
- 产品代码对应 `f60381e`；通过仪器测试模拟多次手写笔移动并停留，截图显示保存后的直线和自动选框。测试随后读取文件核对参考采样端点并执行撤销。
- 未提供同一操作的改前截图。此图不证明箭头、折线、真实手写笔、横屏或其他设备效果；参考应用运行时差分及维护者视觉审查尚未完成。

## 参考折线运行截图

- `reference-polyline.png`：Android 14 ARM64 模拟器、1080 × 2400、420 dpi，合成 PDF 项目。通过 `PdfAnnotationSelectionClipboardActivityTest.referencePolylinePersistsAndUndoesAsOneStroke` 模拟先横后竖的手写笔输入；截图在一个 `pen` 对象保存后、撤销前采集。
- 本截图展示运行后的折线渲染，底部笔记编辑面板是测试项目工作区的一部分。对应产品代码 commit 记录在本文件的 Git 历史中；相同轨迹的基线截图尚未采集。
- 端到端测试验证落盘仍为单个多点 `pen` 对象且带 `pagenotePolyline`，一次撤销后该对象被移除。桌面运行/重开、真机、横屏及参考 App 运行时对比仍未验证。此截图不证明曲线、闭合图形、统一动作历史或多材料选择。

## 参考曲线运行截图

- `reference-curve.png`：Android 14 ARM64 模拟器、1080 × 2400、420 dpi，合成 PDF 项目。测试输入一段弧线；图中显示参考 `BezierTool` 路径提交后的多点曲线笔迹。
- 对应产品源码在本次 PR 的曲线里程碑提交中。阅读器端到端测试确认保存为 33 点 `pen` 对象并可一次撤销；参考程序运行时差分、桌面打开/保存往返、真机和横屏尚未验证。截图来自菜单切换附近的运行状态，不能代表最终菜单视觉验收。

## 原生矢量图形区域擦除

- Android 14 API 34、ARM64 AVD、1080 × 2400、420 dpi；项目使用测试生成的 PDF，在 `annotations.json` 预置原生矩形。产品运行代码为 commit `5a66323`。
- `reference-area-erase-native-before.png` 显示擦除前的完整矩形；`reference-area-erase-native-erasing.png` 显示手势中途的圆形擦除边界和已产生的缺口；`reference-area-erase-native-after.png` 显示擦除后保存的线段；`reference-area-erase-native-undo.png` 显示菜单撤销恢复的原矩形。
- 由 `PdfAnnotationSelectionClipboardActivityTest.areaEraserCutsNativeRectangleAnnotationAndUndoRestoresIt` 运行时采集。测试通过 stylus `MotionEvent` 注入真实 `PenCanvasView`，并检查磁盘页面数据和一次撤销；这是模拟器交互证据，不是实体触控笔验收。关闭保存成功提示后，撤销截图中没有常规保存提示。

![擦除前](reference-area-erase-native-before.png)
![橡皮擦手势中，显示边界与缺口](reference-area-erase-native-erasing.png)
![擦除后](reference-area-erase-native-after.png)
![撤销恢复后](reference-area-erase-native-undo.png)

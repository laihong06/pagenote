# 参考直线识形运行截图

- `reference-line.png`：Android 14 ARM64 模拟器、1080 × 2400、420 dpi、英语浅色主题，使用合成 PDF。
- 产品代码对应 `f60381e`；通过仪器测试模拟多次手写笔移动并停留，截图显示保存后的直线和自动选框。测试随后读取文件核对参考采样端点并执行撤销。
- 未提供同一操作的改前截图。此图不证明箭头、折线、真实手写笔、横屏或其他设备效果；参考应用运行时差分及维护者视觉审查尚未完成。

## 参考折线运行截图

- `reference-polyline.png`：Android 14 ARM64 模拟器、1080 × 2400、420 dpi，合成 PDF 项目。通过 `PdfAnnotationSelectionClipboardActivityTest.referencePolylinePersistsAndUndoesAsOneStroke` 模拟先横后竖的手写笔输入；截图在一个 `pen` 对象保存后、撤销前采集。
- 本截图展示运行后的折线渲染，底部笔记编辑面板是测试项目工作区的一部分。对应产品代码 commit 记录在本文件的 Git 历史中；相同轨迹的基线截图尚未采集。
- 端到端测试验证落盘仍为单个多点 `pen` 对象且带 `pagenotePolyline`，一次撤销后该对象被移除。桌面运行/重开、真机、横屏及参考 App 运行时对比仍未验证。此截图不证明曲线、闭合图形、统一动作历史或多材料选择。

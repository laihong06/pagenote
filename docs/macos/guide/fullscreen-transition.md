# macOS 原生作品全屏切换回归

环境：macOS 25.5.0、Apple Silicon arm64、本机 Electron 窗口，2 倍密度。示例为本地合成原文，不访问模型或账号。截图来自 PageNote 实际运行，不是设计图。

- [修复前：全屏作品](images/fullscreen-transition/before.png)：运行源码 `d2d6e610700e8cee94596c51b4aabffc46d491f1`。进入全屏后迅速操作并返回，实际退出接口未完成，自动化等待 20 秒超时，故没有成功返回后的截图。
- [修复后：全屏作品](images/fullscreen-transition/after/native-focus-overview.png)和[返回后的原文与 SVG 交互](images/fullscreen-transition/after/native-svg.png)：运行源码 `088b606f572c6a01d25908cc43f33ef6185b1733`，点击返回后退出全屏；复用现有完整场景验证交互写入原文及关闭重开恢复。[采集元数据](images/fullscreen-transition/after/provenance.json)。

本次修复未调整布局、字体或配色；观察到的交互区别是快速返回时等待 macOS 原生全屏进入完成。系统切换异常或超时会提示失败并允许重试。截图不证明不同 Mac 设备和显示器均通过。

## 各原生模块的实际运行图

分别对应真实交互后的 [SVG 基因](images/fullscreen-transition/after/native-svg.png)、[Canvas 采样](images/fullscreen-transition/after/native-canvas.png)、[种群](images/fullscreen-transition/after/native-population.png)、[轮盘](images/fullscreen-transition/after/native-wheel.png)、[交叉](images/fullscreen-transition/after/native-crossover.png)、[变异](images/fullscreen-transition/after/native-mutation.png)、[图表](images/fullscreen-transition/after/native-chart.png)、[Three.js](images/fullscreen-transition/after/native-three.png)、[划过](images/fullscreen-transition/after/native-brush.png)及 [GPU 降级](images/fullscreen-transition/after/native-gpu-fallback.png)。这些图片不代替其他平台的运行证据；视觉参考的既有差异见[对照记录](../../markdown-s/VISUAL-REVIEW.zh-CN.md)，本次没有重新设计或宣称视觉等同。

# 参考应用停留识形源码恢复记录

调查目标：使用用户提供的参考应用实现，而不是用自行设计的识别规则冒充原实现。

## 已确认的调用链

- `TouchEventManager` 的停留识形定时器默认 400ms；触点移动和单指状态控制消息取消、重新投递，快捷识形另有抬笔分支。
- `ManagerHandler.recogniseShape` 取 `touchPoints`，调用 `GraphUtils.recogniseShape`；识别后创建材料、保存动作并更新页面。识别失败返回原笔迹。
- `GraphUtils.recogniseShape` 首先处理折线、线、箭头及曲线；闭合轮廓分支通过位图、OpenCV 的灰度转换、模糊、阈值、轮廓提取、多边形逼近及椭圆拟合来判断图形。
- 参考源码位于本机 `/Users/broccoli/Project/note/handwriting-kernel/reference-source/com/newskyer/paint/`，原始 DEX 指令在同级 `dex-smali/`。PageNote 已有本机构建的 OpenCV Android 包，但这不等于识形算法已经移植。

## 当前移植状态和剩余差异

生产路径位于 `PenCanvasView`：开放折线、直线、箭头和曲线分别走源码适配分支；闭合轮廓使用 `ReferenceClosedShapeRecognizer` 的 OpenCV 流程。Android 阅读器已端到端验证折线、33 点曲线、椭圆、圆形、矩形和三角形写入及单步撤销。圆形保留现有注释主类型 `ellipse`，同时写入已有目录扩展 `shape: "circle"`；不新增文件格式主类型。

2026-11-13 修正了参考形状保存时丢失圆形类别的问题：恢复版 `GraphUtils` 会把圆形创建为 `CircleTool`，Android 之前只保留 `ellipse` 主类型。现在识别结果携带现有目录 kind，转换为 annotations 时保留 `shape: "circle"`；阅读器 API 34 横屏平板测试验证页面落盘、截图和一次撤销。

开放曲线识别现在还覆盖 `GraphUtils.isCurveAndCreate` 的五次最小二乘回退：对 X/Y 两个方向分别拟合六系数模型，按参考顺序执行单调采样、5dp 误差与离群点判定，再比较候选并检查包围盒面积。Android 对拟合自变量做归一化并用稳定高斯消元，保留算法结构但不保证与参考实现的浮点逐位一致。`REF-CURVE-04` 在 Android 14 API 34 横屏 Pixel Tablet AVD 的阅读器中验证一条被直接 Bezier 分支拒绝的曲线写入兼容 `pen` 后，一次撤销移除。运行结果、全屏截图和环境记录见 `docs/android/guide/images/reference-shape/README.md` 的“参考曲线最小二乘回退”。

当箭头分类未被更早的折线／直线分支消费，并继续进入曲线回退时，Android 按源码点数和位置条件从拟合尾部生成三段采样箭头头部，大小随当前笔宽缩放；识别器级 `REF-CURVE-05` 回归覆盖这一分支。阅读器落盘、撤销和截图当前覆盖非箭头曲线回退。六系数模型少于 6 个采样点时无稳定解，Android 明确拒绝 3–5 点输入，不复现参考方法中空系数导致的退化结果。

这不是完整源码级移植的完成声明。`GraphUtils.recogniseShape` 反编译结果仍含 `??` 类型、未解析变量和不可编译分支，并依赖原应用的 `PanelManager`、页面材料、坐标变换和 OpenCV；`ManagerHandler.handleMessage` 也只能从 smali 确认部分控制流。Android 闭合识别在局部 ROI 上运行，而参考源码使用全屏画布；开放图形坐标缩放仍有近似；快速识形、部分硬件条件和参考笔型分支未移植。`ReaderActionStack` 适配列表／游标和混合操作顺序，但底层仍调用两套 PageNote 页面历史，没有替换为参考 App 全部 `Action` 对象。实体笔真机、原应用运行时差分、桌面重开与保存往返尚未完成；研究包中 `BasePenExtend.onTouchEvent`、`ManagerHandler.handleMessage` 的缺失控制流仍待恢复。

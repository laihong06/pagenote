# 参考应用停留识形调用链

静态分析用户提供的恢复 DEX（SHA-256 `846b574215037f6fe747779677fb848121ddf37aad465fd48f97e77fbc4d6018`），通过 JADX 逐方法检查；未证明所有设备或设置下运行表现相同。

- `ManagerHandler.handleMessage` 的 `MSG_PAINT_SHAPE_AUTO` 分支会检查页面图层、激光笔、触摸状态、普通/快速自动形状及设备条件，然后调用 `recogniseShape(-1f)`。Java 反编译失败，该分支以完整 smali 检查。
- 恢复源码中的 `TouchEventManager.TIME_PAINT_SHAPE_AUTO` 为 400 ms；普通停留模式在满足位移条件并采样时重置该消息的定时。PageNote 采用同一延迟，但当前触摸采样、位移判定与参考实现仍不同；快速自动形状及设备特例也未移植。
- `TouchEventManager.smali` 的普通单指移动分支按屏幕坐标计算位移平方，与 `dpiTopixel(getLongPressedDistance())` 比较；大于阈值时采样并重置定时，连续静止计数超过 10 时先改为 8 而不采样，计数不大于 1 时更新坐标基准。默认长按距离为 5。PageNote 已将此状态机用于普通笔的停留定时和开放直线/箭头分类；回退识别器仍使用完整页面坐标笔迹，且保留最小路径长度与现有预览取消逻辑。参考硬件加速/4K 的密度换算、快速识形及多指条件未移植。
- `ManagerHandler.recogniseShape` 获取 `TouchEventManager` 的触摸点并调用 `GraphUtils.recogniseShape`；成功后创建 `AddMaterialAction`、添加材料和动作，再更新屏幕与画布状态。`sendRecognize` 的 800 ms 对应另一个 `MSG_RECOGNIZE_HW` 消息，不能作为自动形状停留时间。
- `GraphUtils.recogniseShape` 的 Java 反编译存在缺失局部变量，smali 显示开放路径会调用 `isBrokenLine`、`isLine`、曲线识别等；闭合路径使用 OpenCV 的轮廓检测、轮廓近似和椭圆拟合等。不能把现有 `PenShapeRecognizer` 简单的路径长度、边框和简化判断称为源码复刻。
- 当前项目已在 `feature-ocr` 使用 OpenCV 4.10.0 定制 AAR，参考调用链不一定需要引入第二套运行库。恢复源码中开放路径依次调用 `isBrokenLine`、`isLine` 和曲线创建；首尾接近且点数大于 10 的分支使用设备密度相关的闭合阈值。参考坐标是触摸屏坐标，而现有识别器输入是页面坐标，复用阈值前必须映射并验证缩放与旋转。
- 已将 `GraphUtils.isBrokenLine` 的拐点索引部分移植为 `ReferenceBrokenLine`，输入和最小距离显式使用屏幕坐标；保留的 Java 测试参照取自恢复源码，仅替换点类型、面板缩放依赖和日志，10,000 条固定种子合成轨迹的索引结果一致，包含产生拐点的轨迹。该对照不是原应用运行时差分；折线分支现已在阅读器中优先输出单个多点笔迹，并保留直连显示标记。
- 已移植 `GraphUtils.isLine` 的控制点、箭头及其位置判定；其 JADX 输出存在未初始化和缺失的局部变量分支，测试参照按 `GraphUtils.smali` 的 `:cond_1b0`–`:goto_1f2` 控制流修正。10,000 条固定种子合成轨迹的结果与该测试参照一致，包括直线与箭头分类；这并非原应用运行时差分。阅读器对开放直线/箭头优先使用参考屏幕采样和分类，并将采样端点转为页面坐标；参考的图像到屏幕缩放目前以 PageNote 视口缩放近似，精确映射、抬笔吸附、参考笔宽及其他分支尚未完成，未命中时仍用原识别器。
- 参考 `isBrokenLine` 返回折点时，阅读器现在按原调用次序将首点、折点、尾点转为页面坐标并应用 `toImageWidth(20)` 轴吸附。为保留跨端旧文件形式，折线落盘为单个多点 `type: "pen"` 对象，附 `pagenotePolyline: true`；Android 识别预览及持久化渲染按这些点直连，普通 `pen` 仍沿用原平滑曲线。区域橡皮切分该笔迹后保留扩展标记。端到端模拟器回归覆盖识形保存为单对象及一次撤销移除；桌面源码现有 `pen` 分支按点连接且校验允许扩展字段，但桌面运行/重新打开往返尚未验证。这仍不等同于参考 `ShapeTool` 材料：笔迹样式、选择、统一历史和编辑语义仍有差异。
- 当前验收用例：`REF-POLYLINE-01..04` 覆盖折线单笔保存、直连渲染、单步撤销和擦除后标记保留；`REF-CURVE-01/02` 覆盖曲线公式、33 点采样及画布写入，`REF-CURVE-03`（`referenceCurvePersistsAndUndoesAsOneStroke`）覆盖阅读器落盘和单步撤销。曲线及折线保持 `pen` 主类型并使用 PageNote 扩展字段。
- 曲线分支现按源码 `BezierTool.getPath` 公式执行：参考采样控制点是绘制曲线经过的中点，实际二次控制点为 `midpoint + 2 * (sampledControl - midpoint)`；生成33个等距参数采样点，保存在多点 `pen` 的 `pagenoteCurve` 扩展字段中。每段按直线渲染能被现有桌面 `pen` 解释；尚未验证桌面实际运行打开／保存往返。当前分类器对完全平直的长轨迹也会给出内点控制索引；参考调用链因此走 `BezierTool`，结果仍是平直曲线。
- 当 `isLine` 未返回直线，且检测到的贝塞尔控制点不满足 `directDistance > min(boundsWidth, boundsHeight)` 时，参考实现会进入 `GraphUtils.isCurveAndCreate`。Android `ReferenceCurveFitter` 已按该方法移植六系数五次最小二乘拟合、分别拟合 `y(x)`／`x(y)`、单调采样、5dp 偏差过滤、越界计数、候选比较和面积比校验；拟合输入仍是屏幕采样点，结果适配为兼容多点 `pen` + `pagenoteCurve`。为避免原始坐标高次方病态，Android 在求解前将自变量归一化，使用稳定高斯消元，因此这是结构级移植，仍可能与参考浮点舍入结果有差异。
- `REF-CURVE-04`（`PdfAnnotationSelectionClipboardActivityTest.referenceCurveLeastSquaresFallbackPersistsAndUndoesAsOneStroke`）用端到端阅读器轨迹落入该回退分支，检查保存为超过固定 33 点贝塞尔采样数的曲线材料、截图和一次撤销清除。运行验收是合成 stylus 输入，不代表实体触控笔、原参考应用运行时差分或桌面往返。
- 当 `isLine` 把输入标记为箭头，但它未被前置折线／直线分支消费而继续落入曲线回退时，若 `arrowPosition >= 8`、拟合点数大于 10，Android 会从拟合曲线尾部按参考 `GraphUtils.d(...)` 规则生成并追加三段采样箭头头部；箭头大小随当前笔宽缩放。`REF-CURVE-05`（`ReferenceOpenShapeRecognizerTest.curveFallbackRetainsArrowHeadWhenOpenShapeRecognizerHandlesTheStroke`）验证箭头分类路径确实进入该回退并保留头部点。该箭头特例目前有识别器级回归，没有单独的阅读器落盘截图；`REF-CURVE-04` 的阅读器截图、保存及撤销覆盖非箭头曲线。
- Android 拟合器至少需要 6 个点，才能解六系数模型。参考方法入口允许 3 个点，但其最小二乘系数在 3–5 点时为空，后续拟合会退化；PageNote 将该输入明确拒绝，而不复现退化输出。`ReferenceOpenShapeRecognizerTest.curveFallbackRejectsInputsTooShortForItsSixCoefficientFit` 固定该边界。
- 目前还需要验证参考采样和坐标映射在不同屏幕缩放、旋转和边缘位置下的差异，完成原程序运行时差分、桌面读写往返及真机性能验证。两个后台线程的队列改动只改善一个模拟的慢任务场景，不代替这些验收。

## 参考闭合形状识别（Android 适配）

- `ReferenceClosedShapeRecognizer` 根据恢复源码 `GraphUtils.recogniseShape` 的 OpenCV 分支移植闭合轮廓路径：闭合采样路径栅格化、灰度化、5×5 高斯模糊、阈值 60、外轮廓提取；按 1%／3.2% 周长近似、轮廓面积比、椭圆拟合和角度阈值进行分类。OpenCV 使用项目已有的 4.10.0 AAR，识别在停留识形后台队列执行。
- PageNote 格式适配：圆形落为现有 `ellipse` 并带已有目录扩展 `shape: "circle"`；四个近直角角点落为 `rect`；非圆椭圆、三角形及其他闭合轮廓落为首尾闭合且带 `pagenotePolyline: true` 的多点 `pen`。没有新增文件主类型。
- 为控制平板每次识别的内存分配，Android 在笔画附近含模糊余量的局部 ROI 内运行 OpenCV；参考实现使用全画布图像。这是局部适配，不声称逐像素相同；屏幕边缘笔画与原应用运行时差分尚未验收。
- `REF-CLOSED-01`：Android 横屏平板阅读器中闭合非圆椭圆保存为单个带闭合点的 `pen`，一次撤销清除。
- `REF-CLOSED-02`：`referenceClosedCirclePersistsAsCircleMaterialAndUndoes` 覆盖闭合圆保存为 `ellipse` + `shape: "circle"`，并验证两点参数及一次撤销。
- `REF-CLOSED-03`：`referenceClosedRectangleAndTrianglePersistAndUndoAsOneMaterialEach` 覆盖近直角矩形保存为原生 `rect`、三角形保存为闭合多点 `pen`；各自一次撤销清除整项。识别前、保存后和撤销截图见 Android 截图索引。
- 三个阅读器用例在 Android 14 API 34 ARM64 `Pixel_Tablet_API34_Acceptance` AVD、横屏 2560×1600、320 dpi 上以合成 stylus `MotionEvent` 验收；OpenCV 闭合轮廓仪器用例 4/4 通过。它们不代表实体触控笔、每种图形阈值、桌面重开或原应用运行差分。

## 原生矢量图形区域擦除（Android 适配）

- 原生批注与自由笔迹分属不同的渲染层：`PenCanvasView` 的擦除回调只能直接修改自由笔迹，矩形等原生图形由 `PdfAnnotationOverlay` 读取 `annotations.json` 绘制。Android 阅读页现把同一条擦除手势的页面坐标片段送到原生批注路径，并在手势期间更新覆盖层。
- 原生图形先按参考 `ShapeTool.split`／`EllipseTool.split` 采样路径，再交给恢复版 `Pen.split_v2` 的区域切分逻辑；自由笔迹和原生批注共用一条擦除手势，手势过程展示圆形边界，取消时恢复预览。切开的原生图形以现有多点 `pen` 格式写入，未增加桌面端主类型。
- `REF-ERASE-01/04` 覆盖直线、箭头、矩形与椭圆的区域切分几何和连续碎片；`REF-ERASE-02/03` 覆盖阅读器原生矩形落盘、跨自由笔迹／原生图形单步撤销与重做；横屏平板选择／剪贴板仪器类 25/25 通过。参考擦除器的其他预览样式、平滑配置、实体触控笔和桌面运行差分仍未验收。
- `REF-ERASE-01`：直线、箭头、矩形、椭圆可按区域切分，页面内无关文本保留（`PdfAnnotationAreaEraserTest`）。
- `REF-ERASE-02`：Android 14 模拟器中擦除原生矩形后，`annotations.json` 存在线段缺口；阅读器撤销一次后原矩形恢复，运行时显示擦除边界且不弹保存成功提示（`PdfAnnotationSelectionClipboardActivityTest.areaEraserCutsNativeRectangleAnnotationAndUndoRestoresIt`）。输入由测试合成的 stylus `MotionEvent` 发送到真实 `PenCanvasView`，不代表实体笔真机验收。

## 混合材料选择和撤销（Android 适配）

- `REF-SELECT-01..05` 覆盖自由笔迹与原生图形混合套索选择、按首个笔迹采样点判定命中、组复制／粘贴／移动／改色／删除，以及对应单步撤销／重做。组粗细修改尚未实现。
- 阅读器按项目和页面维护统一操作顺序；跨层区域擦除、混合组编辑和识形可按一个用户操作撤销。当前是 Android 层对恢复源码 `ActionStack` 列表／游标语义的适配，底层页面变更仍通过笔迹与批注存储历史执行，并非参考 App 全部 `Action` 类的源码移植。
- `PdfAnnotationSelectionClipboardActivityTest` 25/25 在 Android 14 API 34 ARM64 横屏 AVD 通过；合成 stylus 输入、进程重启后的历史、其他 Android 设备、桌面往返及参考 App 运行时差分未验收。

# 参考应用停留识形调用链

静态分析用户提供的恢复 DEX（SHA-256 `846b574215037f6fe747779677fb848121ddf37aad465fd48f97e77fbc4d6018`），通过 JADX 逐方法检查；未证明所有设备或设置下运行表现相同。

- `ManagerHandler.handleMessage` 的 `MSG_PAINT_SHAPE_AUTO` 分支会检查页面图层、激光笔、触摸状态、普通/快速自动形状及设备条件，然后调用 `recogniseShape(-1f)`。Java 反编译失败，该分支以完整 smali 检查。
- 恢复源码中的 `TouchEventManager.TIME_PAINT_SHAPE_AUTO` 为 400 ms；普通停留模式在满足位移条件并采样时重置该消息的定时。PageNote 采用同一延迟，但当前触摸采样、位移判定与参考实现仍不同；快速自动形状及设备特例也未移植。
- `TouchEventManager.smali` 的普通单指移动分支按屏幕坐标计算位移平方，与 `dpiTopixel(getLongPressedDistance())` 比较；大于阈值时采样并重置定时，连续静止计数超过 10 时先改为 8 而不采样，计数不大于 1 时更新坐标基准。默认长按距离为 5。PageNote 已将此状态机用于普通笔的停留定时和开放直线/箭头分类；回退识别器仍使用完整页面坐标笔迹，且保留最小路径长度与现有预览取消逻辑。参考硬件加速/4K 的密度换算、快速识形及多指条件未移植。
- `ManagerHandler.recogniseShape` 获取 `TouchEventManager` 的触摸点并调用 `GraphUtils.recogniseShape`；成功后创建 `AddMaterialAction`、添加材料和动作，再更新屏幕与画布状态。`sendRecognize` 的 800 ms 对应另一个 `MSG_RECOGNIZE_HW` 消息，不能作为自动形状停留时间。
- `GraphUtils.recogniseShape` 的 Java 反编译存在缺失局部变量，smali 显示开放路径会调用 `isBrokenLine`、`isLine`、曲线识别等；闭合路径使用 OpenCV 的轮廓检测、轮廓近似和椭圆拟合等。不能把现有 `PenShapeRecognizer` 简单的路径长度、边框和简化判断称为源码复刻。
- 当前项目已在 `feature-ocr` 使用 OpenCV 4.10.0 定制 AAR，参考调用链不一定需要引入第二套运行库。恢复源码中开放路径依次调用 `isBrokenLine`、`isLine` 和曲线创建；首尾接近且点数大于 10 的分支使用设备密度相关的闭合阈值。参考坐标是触摸屏坐标，而现有识别器输入是页面坐标，复用阈值前必须映射并验证缩放与旋转。
- 已将 `GraphUtils.isBrokenLine` 的拐点索引部分移植为 `ReferenceBrokenLine`，输入和最小距离显式使用屏幕坐标；保留的 Java 测试参照取自恢复源码，仅替换点类型、面板缩放依赖和日志，10,000 条固定种子合成轨迹的索引结果一致，包含产生拐点的轨迹。该对照不是原应用运行时差分；已用于直线分类前的折线优先检查，折线材料本身尚未移植。
- 已移植 `GraphUtils.isLine` 的控制点、箭头及其位置判定；其 JADX 输出存在未初始化和缺失的局部变量分支，测试参照按 `GraphUtils.smali` 的 `:cond_1b0`–`:goto_1f2` 控制流修正。10,000 条固定种子合成轨迹的结果与该测试参照一致，包括直线与箭头分类；这并非原应用运行时差分。阅读器对开放直线/箭头优先使用参考屏幕采样和分类，并将采样端点转为页面坐标；参考的图像到屏幕缩放目前以 PageNote 视口缩放近似，精确映射、抬笔吸附、参考笔宽及其他分支尚未完成，未命中时仍用原识别器。
- 参考 `isBrokenLine` 返回折点时，阅读器现在按原调用次序将首点、折点、尾点转为页面坐标并应用 `toImageWidth(20)` 轴吸附。为保留跨端旧文件形式，折线落盘为单个多点 `type: "pen"` 对象，附 `pagenotePolyline: true`；Android 识别预览及持久化渲染按这些点直连，普通 `pen` 仍沿用原平滑曲线。区域橡皮切分该笔迹后保留扩展标记。端到端模拟器回归覆盖识形保存为单对象及一次撤销移除；桌面源码现有 `pen` 分支按点连接且校验允许扩展字段，但桌面运行/重新打开往返尚未验证。这仍不等同于参考 `ShapeTool` 材料：笔迹样式、选择、统一历史和编辑语义仍有差异。
- 当前验收用例：`REF-POLYLINE-01`（折线识别并作为单个多点笔迹保存）、`REF-POLYLINE-02`（Android 折点处直线渲染）、`REF-POLYLINE-03`（单次撤销移除整条识别笔迹）、`REF-POLYLINE-04`（区域切分后扩展标记保留）。曲线与闭合形状仍需按参考分支（含 OpenCV 路径）逐项移植，不能从现有回归推断支持。
- 曲线分支现按源码 `BezierTool.getPath` 公式执行：参考采样控制点是绘制曲线经过的中点，实际二次控制点为 `midpoint + 2 * (sampledControl - midpoint)`；生成33个等距参数采样点，保存在多点 `pen` 的 `pagenoteCurve` 扩展字段中。每段按直线渲染能被现有桌面 `pen` 解释，但桌面运行往返尚未检查。当前分类器对完全平直的长轨迹也会给出内点控制索引；参考调用链因此走 `BezierTool`，结果仍是平直曲线。验收用例 `REF-CURVE-01` 覆盖二次曲线控制点与采样，`REF-CURVE-02` 覆盖画布写入带标记单笔；阅读器文件落盘/撤销仍待端到端验证。
- 目前需要继续确认每个分支的阈值、采样与坐标转换，适配参考材料到 PageNote 文件格式，并完成原程序差分、失败回退和真机性能验证。两个后台线程的队列改动只改善一个模拟的慢任务场景，不完成识形移植。

# 参考应用停留识形源码恢复记录

调查目标：使用用户提供的参考应用实现，而不是用自行设计的识别规则冒充原实现。

## 已确认的调用链

- `TouchEventManager` 的停留识形定时器默认 400ms；触点移动和单指状态控制消息取消、重新投递，快捷识形另有抬笔分支。
- `ManagerHandler.recogniseShape` 取 `touchPoints`，调用 `GraphUtils.recogniseShape`；识别后创建材料、保存动作并更新页面。识别失败返回原笔迹。
- `GraphUtils.recogniseShape` 首先处理折线、线、箭头及曲线；闭合轮廓分支通过位图、OpenCV 的灰度转换、模糊、阈值、轮廓提取、多边形逼近及椭圆拟合来判断图形。
- 参考源码位于本机 `/Users/broccoli/Project/note/handwriting-kernel/reference-source/com/newskyer/paint/`，原始 DEX 指令在同级 `dex-smali/`。PageNote 已有本机构建的 OpenCV Android 包，但这不等于识形算法已经移植。

## 当前缺口

生产路径位于 `PenCanvasView`，开放图形由 `ReferenceOpenShapeRecognizer` 处理，闭合轮廓由 `ReferenceClosedShapeRecognizer` 处理；结果再适配为现有 `annotations.json` 类型。2026-11-13 修正一处与参考阈值不符的额外门槛：原 `GraphUtils.isLine` 在采样端点距离超过 15px 后才继续判断，`createLine` 还要求长度超过 6dp；Android 原先另要求累计笔迹达到 48 个文档单位，导致符合参考屏幕阈值的短线漏识别。现在由对应识形分支执行阈值判定，`REF-SHAPE-05` 在 Android 14/API 34 横屏平板上覆盖 16px 采样端距、落盘为原生直线和一次撤销。

这不是完整源码级移植的完成声明。`GraphUtils.recogniseShape` 反编译结果仍含 `??` 类型、未解析变量和不可编译分支，并依赖原应用的 `PanelManager`、页面材料、坐标变换和 OpenCV；`ManagerHandler.handleMessage` 也只能从 smali 确认部分控制流。仍需与参考 App 的同组真实输入逐项运行差分，并覆盖不同笔型、真机性能和桌面重开文件验证。用户提供的研究包中 `BasePenExtend.onTouchEvent` 与 `ManagerHandler.handleMessage` 的缺失控制流也仍在待恢复清单内。

# 参考应用停留识形源码恢复记录

调查目标：使用用户提供的参考应用实现，而不是用自行设计的识别规则冒充原实现。

## 已确认的调用链

- `TouchEventManager` 的停留识形定时器默认 400ms；触点移动和单指状态控制消息取消、重新投递，快捷识形另有抬笔分支。
- `ManagerHandler.recogniseShape` 取 `touchPoints`，调用 `GraphUtils.recogniseShape`；识别后创建材料、保存动作并更新页面。识别失败返回原笔迹。
- `GraphUtils.recogniseShape` 首先处理折线、线、箭头及曲线；闭合轮廓分支通过位图、OpenCV 的灰度转换、模糊、阈值、轮廓提取、多边形逼近及椭圆拟合来判断图形。
- 参考源码位于本机 `/Users/broccoli/Project/note/handwriting-kernel/reference-source/com/newskyer/paint/`，原始 DEX 指令在同级 `dex-smali/`。PageNote 已有本机构建的 OpenCV Android 包，但这不等于识形算法已经移植。

## 当前缺口

`GraphUtils.recogniseShape` 反编译结果含 `??` 类型、未解析变量和不可编译分支，并依赖原应用的 `PanelManager`、页面材料、坐标变换和 OpenCV。`ManagerHandler.handleMessage` 也只能从 smali 确认细节。必须逐段核对原 DEX、恢复可编译控制流，建立原实现与移植实现对同一输入的结果对照，再接入生产识形路径。

目前生产代码仍是既有 `PenShapeRecognizer`，不是参考源码级实现；本次不将其行为或效果宣称为复刻完成。源码恢复、不同笔型与图形的基准样本、真机延迟与跨平台保存仍待完成。

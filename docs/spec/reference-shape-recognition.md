# 参考应用停留识形调用链

静态分析用户提供的恢复 DEX（SHA-256 `846b574215037f6fe747779677fb848121ddf37aad465fd48f97e77fbc4d6018`），通过 JADX 逐方法检查；未证明所有设备或设置下运行表现相同。

- `ManagerHandler.handleMessage` 的 `MSG_PAINT_SHAPE_AUTO` 分支会检查页面图层、激光笔、触摸状态、普通/快速自动形状及设备条件，然后调用 `recogniseShape(-1f)`。Java 反编译失败，该分支以完整 smali 检查。
- `ManagerHandler.recogniseShape` 获取 `TouchEventManager` 的触摸点并调用 `GraphUtils.recogniseShape`；成功后创建 `AddMaterialAction`、添加材料和动作，再更新屏幕与画布状态。`sendRecognize` 的 800 ms 对应另一个 `MSG_RECOGNIZE_HW` 消息，不能作为自动形状停留时间。
- `GraphUtils.recogniseShape` 的 Java 反编译失败，smali 显示开放路径会调用 `isBrokenLine`、`isLine`、曲线识别等；闭合路径使用 OpenCV 的轮廓检测、轮廓近似和椭圆拟合等。不能把现有 `PenShapeRecognizer` 简单的路径长度、边框和简化判断称为源码复刻。
- 目前需要继续确认每个分支的阈值、采样与坐标转换，适配参考材料到 PageNote 文件格式，并完成原程序差分、失败回退和真机性能验证。两个后台线程的队列改动只改善一个模拟的慢任务场景，不完成识形移植。

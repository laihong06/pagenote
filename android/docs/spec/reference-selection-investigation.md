# 参考应用选择功能调查

参考实现的 `SelectAction` 负责输入与选择流程，`SelectionManager` 管理多个材料及复制、删除、颜色和层级操作，`Selector` 负责选择路径、边框、控制点及命中测试；`ShapeSelectAction` 与 `AreaSelectAction` 分别处理图形与区域选择。当前 Android 主要是单条笔迹点选以及独立的矢量批注选中，尚未实现等价的统一多材料选择。

用户提供的业务 DEX 中相关类已重新提取到正式参考目录 `/Users/broccoli/Project/note/selection-research/`；DEX SHA-256 为 `846b574215037f6fe747779677fb848121ddf37aad465fd48f97e77fbc4d6018`。`SelectAction.handleTouch` 的 JADX 输出仍含 `??`，需要对照 DEX/smali 恢复控制流与原始依赖。该调查不代表源码级选择功能已移植或验收。

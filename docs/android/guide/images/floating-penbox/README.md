# 悬浮笔盒运行截图

这些是运行中的 PageNote Android 截图，使用合成测试项目；参考 App 的界面及资源不作为运行证据。

- 环境：Android 14、ARM64 模拟器、1080 × 2400、420 dpi、浅色主题。
- `before.png`：原笔栏，运行代码 `67e59a0b0efc54c920a09971e704eb65e6ffc030`。
- `after.png`：浮动笔盒，运行代码 `42bd53375a1221935e3a63fde67164e4b4b3f013`；与改前图使用相同的 PDF 测试页面。
- `style.png`、`collapsed.png`、`groups.png`：同一改后代码的样式菜单、收起和笔盒组菜单，使用另一合成项目。
- 拖动吸附、排序和复制的操作由 `FloatingPenBoxTest` 在模拟器中执行；这里的静态图不能证明动态手势、像素级一致或真机体验。横屏和平板完整界面仍待补充。

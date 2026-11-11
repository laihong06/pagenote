# 悬浮笔盒运行截图

这些是运行中的 PageNote Android 截图，使用合成测试项目；参考 App 的界面及资源不作为运行证据。

- 环境：Android 14、ARM64 模拟器、1080 × 2400、420 dpi、浅色主题。
- `before.png`：原笔栏，运行代码 `67e59a0b0efc54c920a09971e704eb65e6ffc030`。
- `after.png`：浮动笔盒，运行代码 `ef2d84173d8e40c71989d09abe10410fc9f9a53c`；与改前图使用相同的 PDF 测试页面。
- `style.png`、`collapsed.png`：同一改后代码的样式菜单和收起状态，使用另一合成项目。
- 拖动吸附、排序和复制的操作由 `FloatingPenBoxTest` 在模拟器中执行；这里的静态图不能证明动态手势、像素级一致或真机体验。横屏和平板完整界面仍待补充。

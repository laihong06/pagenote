# Android 菜单与设置

[Android 首页](../README.md) · [工作区教程](workspace.md) · [功能表](../features.md)

本页展示未发布的 Android 开发源码。截图来自 Android 14／API 34、ARM64 模拟器、浅色主题：横屏为 2560×1600、320 dpi，窄屏为 1080×1920、420 dpi。内容为自编测试项目。菜单和书架截图的改前运行代码为 `476b354`，改后为 `b67d96c`；全页设置截图的改前运行代码为 `b67d96c`，改后为 `511a343`。维护者视觉审查另行进行。

## 阅读菜单

1. 在阅读页面点击左上角菜单按钮。
2. 菜单先显示“文件、编辑、插入、阅读、手写”等分组；点击分组查看其操作，再次点击可收起。打开另一组时，之前的分组自动收起。
3. 在窄屏，点击菜单外部或系统返回可关闭侧栏；平板可点击侧栏顶部收起按钮。

![改前：菜单命令全部展开](images/menu/before-menu.png)

![改后：菜单按组收起](images/menu/after-menu.png)

![展开文件分组](images/menu/after-menu-expanded.png)

## 分类设置

在书架点击设置，或在阅读菜单的“设置”分组打开设置，进入独立的设置页面。平板左侧选择分类、右侧显示内容；手机先显示分类列表，点击后进入详情，返回可逐级回到列表和原来的书架或阅读页。

- **通用外观**：书架目录、主题、纸张背景和语言。
- **阅读手写**：双栏比例、预加载、翻页动画、手写边界及夜览滤镜。
- **识别录音**：OCR、公式识别、录音和离线转写。
- **搜索**：搜索索引范围和重建入口。
- **Agent**：在设置页中进入模型和提示词配置。

切换分类不会丢失设置草稿，点击“保存”统一保存；未保存时退出设置页会放弃草稿。进入 Agent 配置前会先保存当前设置。这里的设置保存与笔记后台自动保存是两个不同操作。设备重建页面后，设置草稿和所在分类会恢复。

![改前：平板设置弹窗](images/settings-page/before-tablet.png)

![改后：平板设置双栏](images/settings-page/after-tablet.png)

![平板 Agent 模型配置](images/settings-page/after-tablet-agent-model.png)

![改前：手机设置弹窗](images/settings-page/before-phone.png)

![改后：手机设置分类](images/settings-page/after-phone-list.png)

![手机设置详情](images/settings-page/after-phone-detail.png)

![手机 Agent 模型配置](images/settings-page/after-phone-agent-model.png)

## 窄屏书架

搜索框在窄屏单独占一行；视图切换和刷新移到右上角“更多”菜单。目录、搜索、新建和打开项目仍可使用。

![改前：窄屏搜索框被挤没](images/menu/before-phone-bookshelf.png)

![改后：窄屏书架](images/menu/after-phone-bookshelf.png)

![窄屏菜单](images/menu/after-phone-menu.png)

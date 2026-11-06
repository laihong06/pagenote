# PageNote Windows 版

PageNote 将课件与逐页笔记并排显示。Windows 版面向键盘、鼠标和桌面文件操作，使用 Electron 桌面程序；不是 Android 界面的电脑版截图。[返回项目总览](../../README.md)。

## 下载与版本

- 公开测试版：**1.1.0-beta.4**，[安装包](https://github.com/laihong06/pagenote/releases/download/v1.1.0-beta.4/pagenote-1.1.0-beta.4-windows-x64-setup.exe) · [便携 ZIP](https://github.com/laihong06/pagenote/releases/download/v1.1.0-beta.4/pagenote-1.1.0-beta.4-windows-x64.zip) · [发布说明与校验](https://github.com/laihong06/pagenote/releases/tag/v1.1.0-beta.4)。
- 当前开发版：**1.1.0-beta.5-dev.2**。已构建本机体验包，未公开发布；上面的 Beta 4 下载不含原生 Markdown S 等本轮改动。
- 正式版入口：[Windows 稳定线](https://github.com/laihong06/pagenote/tree/windows/main)。测试包不使用正式包替代。

要求 Windows x64。安装前退出旧应用；便携包完整解压，保留程序旁的资源文件。Beta 使用独立安装身份和数据目录，但主动打开的项目仍是原文件，试用前可复制项目。

## Windows 的主要操作

| 模块 | 你可以做什么 | 操作方式与限制 |
| --- | --- | --- |
| 阅读与笔记 | 左侧课件、右侧对应页讲解，编辑后保存到项目 | 鼠标选择、页码导航、键盘输入 |
| 项目与书架 | 打开项目文件夹，查找课件与笔记 | 桌面文件菜单和书架 |
| 批注与手写 | 给选区添加意见，画笔标记 | 鼠标或本机输入设备；手写硬件另测 |
| AI 协作 | 连接外部 Agent，读取材料并生成／修改笔记 | 需要自行配置客户端；软件不内置大模型 |
| Office | 借助本机 Office 转换和支持的编辑流程 | 依赖本机 Office，不能套用 Android 操作 |
| 原生 Markdown S | 操作基因、Canvas、轮盘、图表和三维课件 | 当前开发源码／本地包功能；见[配图教程](guide/markdown-s.md) |

## 界面与截图

下面为 Windows 开发源码实际运行图，不代表 Beta 4 安装包的完整界面；[截图来源](guide/images/markdown-native/provenance.json)。

![Windows 原生交互笔记](guide/images/markdown-native/native-svg.png)

## 教程与版本记录

[按功能查看完整清单与截图状态](features.md) · [操作教程](guide/README.md) · [更新日志](CHANGELOG.md) · [各版本说明](releases/README.md) · [独立版本配置](../../platforms/windows/version.json)

教程和对应图片共同保存在 guide/ 目录，未采集的本系统截图明确标为待补；不以其他系统的截图冒充。开发源码不等于已发布包。

## 开发入口

[windows/beta](https://github.com/laihong06/pagenote/tree/windows/beta) 是平台测试线；共享代码先到 beta，再通过同步 PR 进入平台。构建：node scripts/platform.mjs windows build；本地打包：node scripts/platform.mjs windows package --dir。

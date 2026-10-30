# PageNote

把课件和逐页笔记放在一起，边读、边批注、边补充讲解。Windows、Mac 和 Android 使用不同的界面与系统适配，功能和版本分别维护。

## 选择你的系统

| 系统 | 独立使用说明 | 当前状态 |
| --- | --- | --- |
| Windows 电脑 | [Windows README：下载、功能、截图和教程](docs/windows/README.md) | x64；有正式版历史，本分支为测试开发 |
| Mac 电脑 | [macOS README：下载、原生操作和限制](docs/macos/README.md) | Apple Silicon；实验性，Intel 包不可用 |
| Android 平板 | [Android README：APK、触控与手写操作](docs/android/README.md) | Android 14+、ARM64；独立原生应用 |
| Linux 电脑 | [Linux README：运行、功能、截图和限制](docs/linux/README.md) | Ubuntu x64 本地验证；未发布安装包 |
| 鸿蒙 | 尚未提供实现与下载 | 规划中 |

**下载请进入对应系统的 README。** 已发布包、开发源码和本地体验包分别标记；Windows 的截图与操作不代表 Android 或 Mac。

## 版本与分支怎么区分

- 每个平台各自维护版本、教程、截图和更新日志；一个平台更新不自动发布其他平台。
- 根 main 保留稳定历史与项目入口，根 beta 汇集共享改动；平台维护线见[分支地图](docs/BRANCHES.zh-CN.md)。
- 功能试验使用短期分支，合并完成后删除分支；历史提交和归档仍可恢复。

当前为 **beta 共享集成源码**，不是所有平台都已验收的新发行版。[源码边界](apps/README.md) · [贡献与 PR 规范](CONTRIBUTING.md) · [已知问题](KNOWN_ISSUES.md)。

## 项目资料

[原生 Markdown S](docs/markdown-s/NATIVE-MARKDOWN.zh-CN.md) · [AI 教学与表达路由](docs/AI-REPRESENTATION-ROUTING.zh-CN.md) · [许可与历史授权](LICENSING.md) · [English](README.en.md)

新增原创内容采用 PolyForm Noncommercial 1.0.0；个人非商业使用和修改允许，其他商业用途须依许可另行授权。

# PageNote · Windows

PageNote 把课件与逐页笔记并排显示，帮助阅读、批注和学习。这里是 Windows 系统的文档入口；整个项目见[总 README](../../README.md)。

- [功能结构与截图覆盖](features.md)
- [配图教程](guide/README.md)
- [版本更新日志](CHANGELOG.md)
- [逐版本发布说明](releases/README.md)
- [平台版本配置](../../platforms/windows/version.json)

## 下载与版本

本分支为测试开发，开发版本 **1.1.0-beta.5-dev.2** 尚未发布。最后公开测试版为 **1.1.0-beta.4**，并不包含本次合并的所有新功能。

[下载 Windows 测试包](https://github.com/laihong06/pagenote/releases/download/v1.1.0-beta.4/pagenote-1.1.0-beta.4-windows-x64-setup.exe) · [原发布及校验](https://github.com/laihong06/pagenote/releases/tag/v1.1.0-beta.4)

Windows x64；安装前退出旧应用，便携包完整解压。稳定版入口保留在[现有 main](https://github.com/laihong06/pagenote/tree/main)，不要用稳定包替代此测试下载。

## 开发与验证

在 Windows 上运行 npm ci，然后 node scripts/platform.mjs windows build；打包使用 node scripts/platform.mjs windows package --dir。

共享功能仍复用完整既有源码，避免目录搬迁丢失能力；[源码边界](../../apps/README.md)与[整合验收记录](../INTEGRATION-2026-10-30.md)说明范围。

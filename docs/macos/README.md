# PageNote · macOS

PageNote 把课件与逐页笔记并排显示，帮助阅读、批注和学习。这里是 macOS 系统的文档入口；整个项目见[总 README](../../README.md)。

- [功能结构与截图覆盖](features.md)
- [配图教程](guide/README.md)
- [版本更新日志](CHANGELOG.md)
- [逐版本发布说明](releases/README.md)
- [平台版本配置](../../platforms/macos/version.json)

## 下载与版本

本分支为测试开发，开发版本 **1.1.0-beta.5-dev.1** 尚未发布。最后公开测试版为 **1.1.0-beta.4**，并不包含本次合并的所有新功能。

[下载 macOS 测试包](https://github.com/laihong06/pagenote/releases/download/v1.1.0-beta.4/pagenote-1.1.0-beta.4-mac-arm64.dmg) · [原发布及校验](https://github.com/laihong06/pagenote/releases/tag/v1.1.0-beta.4)

仅 Apple Silicon arm64；Intel 构建不可用。macOS 仍为实验性测试版，尚无正式版；原生窗口和 Office 行为须在 Mac 验证。

## 开发与验证

在 macOS 上运行 npm ci，然后 node scripts/platform.mjs macos build；打包使用 node scripts/platform.mjs macos package --dir。

共享功能仍复用完整既有源码，避免目录搬迁丢失能力；[源码边界](../../apps/README.md)与[整合验收记录](../INTEGRATION-2026-10-30.md)说明范围。

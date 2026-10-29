# PageNote · Android

PageNote 把课件与逐页笔记并排显示，帮助阅读、批注和学习。这里是 Android 系统的文档入口；整个项目见[总 README](../../README.md)。

- [功能结构与截图覆盖](features.md)
- [配图教程](guide/README.md)
- [版本更新日志](CHANGELOG.md)
- [逐版本发布说明](releases/README.md)
- [平台版本配置](../../platforms/android/version.json)

## 下载与版本

本分支为测试开发，开发版本 **1.0.0-beta.6-dev.1** 尚未发布。最后公开测试版为 **1.0.0-beta.5**，并不包含本次合并的所有新功能。

[下载 Android 测试包](https://github.com/laihong06/pagenote/releases/download/android-v1.0.0-beta.5/pagenote-android-1.0.0-beta.5-arm64-v8a.apk) · [原发布及校验](https://github.com/laihong06/pagenote/releases/tag/android-v1.0.0-beta.5)

要求 Android 14+；公开包为 ARM64，沿用贡献者签名。新 debug/preview 包不能冒充该已发布包；真机手写和系统权限需另测。

## 开发与验证

在仓库 android/ 运行 gradlew.bat（Windows）或 ./gradlew：testDebugUnitTest、compileDebugAndroidTestKotlin、assembleDebug。开发版本由本系统 JSON 配置读取。

共享功能仍复用完整既有源码，避免目录搬迁丢失能力；[源码边界](../../apps/README.md)与[整合验收记录](../INTEGRATION-2026-10-30.md)说明范围。

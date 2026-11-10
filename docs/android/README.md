# PageNote Android 平板版

PageNote 的 Android 版是 **Kotlin／Jetpack Compose 原生应用**，围绕平板触控、手写和系统文件授权设计。它不是 Windows／Mac 桌面程序移植出的相同界面，功能与操作以本页及 Android 教程为准。[返回项目总览](../../README.md)。

## 下载与版本

- 公开测试版：**1.0.0-beta.5**，[下载 ARM64 APK](https://github.com/laihong06/pagenote/releases/download/android-v1.0.0-beta.5/pagenote-android-1.0.0-beta.5-arm64-v8a.apk) · [说明、源码与校验](https://github.com/laihong06/pagenote/releases/tag/android-v1.0.0-beta.5)。
- 开发版本：**1.0.0-beta.6-dev.1**，源码中的新增模块不代表上述 APK 已包含。
- 要求 **Android 14+、ARM64**；尚无获批稳定版。公开包沿用贡献者签名，开发 debug／preview 包不能冒充同一发行包。

## Android 的操作与功能

| 模块 | 平板上怎么用 | 重要区别／限制 |
| --- | --- | --- |
| 书架与文件 | 通过系统文件选择器授权目录，再导入或打开项目 | Android 文档授权机制，不是桌面文件路径操作 |
| 阅读与笔记 | 触控翻页、选择文字，在笔记区编辑 | 布局及工具栏独立实现 |
| 手写与批注 | 选择笔、颜色及粗细，用手写笔或触控标注 | 真机压感、侧键和持久授权需另验 |
| 内置 AI 入口 | 配置自己的兼容模型服务，再按页讲解或修改 | 不等同桌面外部 MCP 客户端流程 |
| OCR 与搜索 | 识别扫描页，索引支持的本地内容 | 需要本地模型；识别质量依材料而定 |
| 学习与辅助工具 | 开发源码含闪卡、录音转写、素材、悬浮工具等模块 | 逐项状态见功能表，不能全部算作公开包承诺 |

**不具备桌面完整功能对等性。** Android 笔记预览已提供实验性的原生 Markdown S 遗传算法模块；八章界面已在 Android 14 模拟器运行并留下真实截图，项目笔记文件的保存后重读代表路径通过 instrumentation。不执行 HTML/JavaScript；桌面 Three.js/WebGL 的真实三维场景尚未实现，Android 当前为 Canvas 2D 旋转投影。完整 Activity 关闭/重启验收仍待完成，详见[指南](guide/markdown-s.md)。Office 进程内编辑未实现。共享项目格式不代表共享全部界面与能力。

## Android 截图与验证状态

本轮各模块平板截图待补。不得以 Windows／Mac 截图替代；现有模拟器／真机验证范围见[已知问题](../../KNOWN_ISSUES.md)和[发布说明](../../android/docs/RELEASE-1.0.0-beta.5.md)。最新共享 CI 的 Android 单测／编译任务失败，未将其称为全绿。

## 教程与版本记录

[按功能查看完整清单与截图状态](features.md) · [操作教程](guide/README.md) · [更新日志](CHANGELOG.md) · [各版本说明](releases/README.md) · [独立版本配置](../../platforms/android/version.json)

教程和对应图片共同保存在 guide/ 目录，未采集的本系统截图明确标为待补；不以其他系统的截图冒充。开发源码不等于已发布包。

## 开发入口

[android/beta](https://github.com/laihong06/pagenote/tree/android/beta) 是独立平台测试线。源码保留在 android/，尚未搬迁到 apps/android/；不为目录形式破坏 Gradle 工程。Windows 使用 android/gradlew.bat；其他系统使用 ./gradlew，运行 testDebugUnitTest、compileDebugAndroidTestKotlin、assembleDebug。

本地真机测试包：在仓库根目录运行 `./scripts/update-android-apk.sh`，构建成功后会更新主工作目录中 `android/build/apk-latest/` 下的 ARM64 和 ARMv7 debug APK、SHA-256 与构建信息；可通过 `PAGENOTE_APK_DIR` 覆盖输出目录。包来自运行脚本所在的工作树，不会自动合并到主工作目录的源码。大多数现代手机使用 `PageNote-latest-arm64.apk`，要求 Android 14 或更高版本。这个命令需要在每次希望更新固定目录时运行；普通 `assembleDebug` 不会自动更新该目录。本地包使用独立的 debug 应用 ID 和本机调试签名，不是公开发行包。

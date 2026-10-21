# Android 1.0.0-beta.5

Android 平板测试版，来自 obroccolio 的 PR #10，仅接入 beta；不改变 Windows 正式版、桌面 Beta 4 或已冻结的 Windows 晋级候选。

- 环境：Android 14（API 34）及以上、ARM64；不提供旧 Android 或 x86 安装包。
- 功能：PDF/Markdown 双栏逐页阅读与笔记、批注与手写、离线 OCR、兼容 OpenAI 接口的内置讲解 Agent、提示词编辑、Markdown 主题及格式快捷键。AI 服务需自行配置 HTTPS 地址、模型及密钥。
- 测试标识：`io.pagenote.android.beta`，版本 `1.0.0-beta.5` / versionCode 5，与未来正式应用分开。
- 安装：下载 APK，在系统提示中允许当前来源安装。用复制的项目测试，避免跨设备同时编辑唯一原件。
- 签名：保留贡献者原始本地 debug 密钥签名；不是应用商店正式签名。后续同包名升级须使用同一签名，主仓库目前未持有该私钥；签名变更时先备份项目和必要设置，不要直接卸载唯一数据副本。
- 限制：Office 编辑及 Save & Return 尚未实现；桌面功能一致性任务 35/37 未完成。部分桌面提示词路由模块尚未全量移植，不宣称与桌面完全等价。小米 Pad 5 Pro 压感与侧键、真实文件授权和在线 AI 全流程仍需实测。

贡献者报告：API 34 平板模拟器 19 项定向仪器测试、定向 JVM 测试、Beta APK 构建、16 KB 对齐及安装启动通过；最后这轮未重新在小米真机验收。维护者本地完成 12 项 Python 辅助脚本测试与桌面发布版本一致性检查；本机缺少 Android SDK，未冒称重建 APK 或重跑 Android 仪器测试。发布流水线另外验证 APK SHA256、应用标识/版本/最低系统、签名和 ZIP 对齐。

APK 原样转存贡献者已发布构建，SHA256：`56820881beb69e82ef2aa4a79892f0412e4a63fc829d954de0888b41b331de11`。

贡献者标签 `android-v1.0.0-beta.5` 位于 `64f3527f96c867d8a2427f9919470b9ac25af8a3`，PR 头为 `0b84e5d63eabd1afe9ea675e55766b4bd5fa7e6e`。两者 Android 目录树均为 `6e6ed47df930d91613c76a02ed4039e361e93938`；主仓库发布标签另固定集成提交，详见附件 PROVENANCE.txt。源码包仅含 Git 跟踪文件，不含用户项目与密钥。

问题反馈：https://github.com/laihong06/pagenote/issues 。请附系统版本、设备、软件版本、复现步骤和脱敏截图。

## English

Experimental Android 14+ ARM64 tablet beta, integrated from obroccolio PR #10 on beta only. Includes page-linked PDF/Markdown reading, notes, ink, offline OCR and an OpenAI-compatible tutoring agent. Configure your own HTTPS endpoint, model and key. Office editing and full desktop parity remain incomplete; physical tablet, stylus buttons and live-service workflows are not fully verified.

The APK is the contributor's unchanged debug-key-signed sideload build. Future upgrades require the same signing key, which the maintainer does not hold. Back up projects before testing or uninstalling. Contributor AVD/JVM/build/alignment results and maintainer helper checks are distinguished above; this is not a maintainer Android rebuild or a claim of full real-device acceptance. SHA256 and source provenance ship with this release. Windows stable and its frozen promotion candidate remain unchanged.

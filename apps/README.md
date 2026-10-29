# 应用入口与源码边界

- `desktop/windows/main.cjs`：Windows 打包入口。
- `desktop/macos/main.cjs`：Mac 打包入口。
- 两个入口复用根 `desktop/` 的完整 Electron 主进程、`src/` 阅读界面及服务，避免重构时漏掉 Office、MCP、OCR、书架或雨课堂。历史入口保留兼容。
- Android 的 Gradle 工程仍位于根 `android/`，其独立入口为 `android/app/src/main/AndroidManifest.xml`；移动它会影响原生、模型与构建路径，本轮保留完整工程。

平台差异通过明确的入口、`platforms/<platform>/version.json` 与打包配置隔离，不复制共用实现。源目录的进一步物理移动需另附等价验证，不能以“清理目录”为由删功能。

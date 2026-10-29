# 平台版本与构建入口

每个平台的 `version.json` 是其开发包版本来源，`published` 单独记录最后公开制品，不能把开发版本写成已发布版本。Windows 更新不修改 Mac 或 Android 配置。

```powershell
node scripts/platform.mjs windows info
node scripts/platform.mjs windows check
node scripts/platform.mjs windows build
node scripts/platform.mjs windows package --dir
```

在 Mac 上将 `windows` 替换为 `macos`；Android 使用 `cd android` 后运行 `./gradlew` 或 `gradlew.bat`。Gradle 读取 Android 自己的 JSON 元数据。Windows 与 Mac 的 `package` 命令先运行 `build`，保证读取到最新资源。

开发构建保持 beta 安装身份，不改变已发布包。当前仅开放测试集成；稳定版晋级与公开发布仍需执行发布政策。平台命名标签不自动发布二进制；发布工作流必须验证标签、版本、完整制品和批准记录，禁止使用开发编号公开替换历史包。

> Current policy / 当前准则：以 [发起人确认的分级发布基准](RELEASE-POLICY.md) 为准。本文的历史验收条目不自动阻塞 Windows v1.0；未完成的非阻断工作记入 [KNOWN_ISSUES.md](../KNOWN_ISSUES.md)。

# GitHub 发布教程

## 首次上传源码

源码仓库：[laihong06/pagenote](https://github.com/laihong06/pagenote)。普通用户在 [Releases](https://github.com/laihong06/pagenote/releases) 下载安装包。

首次创建自己的分支仓库时，可在源码目录执行以下命令；其他维护者需替换账号：

```powershell
git init
git add .
git commit -m "Prepare pagenote beta release"
git branch -M main
git remote add origin https://github.com/laihong06/pagenote.git
git push -u origin main
```

提交前检查 `.gitignore`：不要上传 `node_modules`、`release`、个人项目、测试运行目录、MCP 密钥或签名证书。GitHub **Code → Download ZIP** 只提供源码；给普通用户的安装文件应放在 **Releases**。

## 构建

使用 Windows x64、Node.js 22.13+：

```powershell
npm ci
npm run build
npm run test:release
npm run package:desktop
node scripts/release-artifacts.mjs
```

`release/` 中应包含安装向导 EXE、Windows ZIP 和 `SHA256SUMS.txt`。先在独立 Windows 用户账户或干净虚拟机中完成安装、升级、卸载、项目保存和重开测试。

## macOS 构建

在 Mac 上使用 Node.js 22.13+ 和 Xcode Command Line Tools。构建会自动编译原生 AppKit 设置组件（arm64，macOS 12+）：

```sh
npm ci
npm run build
npm run test:release
npm run test:mcp
npm run test:mac-office
npm run package:mac
node scripts/release-artifacts.mjs
```

`test:mac-office` 需要已激活的本机 Word / PowerPoint。`package:mac` 仅输出 Apple Silicon（arm64）的 DMG / ZIP；arm64 应用目录可运行 `npm run package:mac:local`。Mac 验收还需确认 Finder 双击项目、Dock 重开窗口、Command 菜单快捷键及 Office 自动化授权。当前构建未做开发者签名与公证；正式分发前应配置 Apple 开发者身份并在干净机器验证。

## 发布 Release

1. 更新 `package.json` 与 `package-lock.json` 的版本。
2. 验证本地或 CI 测试结果，并阅读发布准备报告。
3. 在 GitHub 创建带版本号的 tag，例如 `v2.0.0-beta.5`。
4. 使用 **Actions → Release artifacts** 手动运行工作流，或推送 tag。
5. 工作流只生成构建产物，不自动公开发布。下载 artifacts，检查安装包、ZIP 和校验值。
6. 在 **Releases → Draft a new release** 中填写中英文变更说明，上传 EXE、ZIP、`SHA256SUMS.txt`。测试版勾选 **pre-release**。
7. 未完成签名、杀毒复核和独立终端验收时，清楚标记测试版及验证范围。正式稳定版完成这些检查后再发布。

## 签名与报毒

当前本地版本未签名。配置有效的 Windows 代码签名证书后，按 [electron-builder 官方签名指南](https://www.electron.build/docs/features/code-signing/code-signing-win/) 设置受保护的凭据。不要把 `.pfx`、密码或密钥提交到 Git。

曾有旧版便携自解压包被火绒拦截，尚未取得厂商的误报确认。不能承诺更换包装就不报毒，也不能要求用户关闭防护。发布前请向对应厂商提交检测日志与样本复核；未经确认的构建应继续标为测试版。

## 用户教程应写明

- 下载 Release 安装包，而不是源码 ZIP。
- ZIP 版需要完整解压，不能只取 EXE。
- Word/PPT 转换需要本机 Office；没有 Office 的用户可先导出 PDF。
- MCP 需要外部 AI 客户端，不包含免费 AI 服务。
- 项目文件夹必须整体移动；卸载程序不会删除项目和用户数据。

当前构建配置显式设置了 `build.win.signExecutable: false`。准备正式签名时，先改为 `true`，再配置签名凭据并重新构建、验证签名。

## 版本一致性与脚本发布

源码推送不等于安装包发布。开发期间README可以继续指向已发布版本；发布新包时必须同步package.json、package-lock.json、README中英文下载入口、日期化更新日志及Git标签。

1. `npm run test:version` 检查基础版本约束；`npm run check:release` 额外检查下载入口和已发布更新日志。
2. 构建、测试最终包，将安装EXE、完整ZIP、源码ZIP放入独立 `release/publish-版本号/`，对该目录运行 `node scripts/release-artifacts.mjs 目录`。
3. 提交发布改动，在该提交上创建并推送匹配的版本标签。发布脚本验证远端标签指向当前提交。
4. 编写 `docs/RELEASE-NOTES-版本号.md`，执行 `node scripts/publish-release.mjs draft 产物目录` 上传并核验草稿；确认完整后执行同命令的 `publish` 模式。
5. 确认Release已公开且资源齐全，再同步主分支下载入口。`status` 模式可读取发布状态。脚本从package.json推导版本，使用现有Git凭据或环境令牌，不在文件内保存密钥。

已公开版本的安装包不可悄悄替换成另一份内容；有变化时发布新的版本号。测试版不会标为正式稳定版。

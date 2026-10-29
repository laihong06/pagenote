# 2026-10-30 整合与管理结构迁移

## 基线与保留范围

- 远程共享 beta：`8e59d22`。
- 稳定历史 main：`081a17c`，保持不变，不将本次合并视为正式晋级批准。
- PR #13：`6235809`，包含 PR #11 的 `f17cb77`；完整保留其书架、雨课堂和 Android 批次 0–12 实现。
- PR #14：`79cca35`，保留多平台管理、截图、模块说明和版本文档要求。
- 本地 Android 布局预览：`aaf2a76` 移植为 `65a87c9`；合并时同时保留书架返回入口、阅读器折叠行为、新功能资源和 preview 工具栏。
- 原工作区未跟踪文件不加入合并、不覆盖用户改动。

## 实际结构

平台版本位于 `platforms/<platform>/version.json`；桌面独立入口位于 `apps/desktop/windows` 与 `apps/desktop/macos`。现有 `desktop/`、`src/`、服务、MCP 和 Android Gradle 工程保留运行路径，不做破坏性搬迁。共享主机复用完整功能，平台打包入口和版本已经分开。

每个系统独立 README、功能表、guide 内教程与图片、CHANGELOG 及 releases 索引。Windows 与 Mac 可分别推进开发号；Android 不复用已发布 beta.5 的版本与制品。

## 分支与发布

共享 beta 接收整合和共享修复；平台 beta 分别作为后续平台开发入口。Windows 稳定线从现有已批准 main 建立；Mac 保留现有实验性基线，不宣称正式版；Android 尚无获批稳定版本，不从未批准测试代码伪造正式基线。分支创建与远程状态在整合完成后核对。

平台新标签命名不触发旧 `v*` 发布流程。本轮不创建公开 Release、不替换旧制品、不更改稳定下载。正式版仍需针对本平台的实测与明确批准。

## 验证记录

2026-10-30 更新：PR #15 已合入 beta（5a779a5）；#11、#13、#14 均已被 GitHub 标记为已合并。提交 9990f82 的 Actions [40920535128](https://github.com/laihong06/pagenote/actions/runs/40920535128) 四项全部通过：平台元数据、Windows、macOS、Android。下方“待执行／等待重跑”保留为过程记录，以本更新为当前结论。Android 云端单测、应用及测试源码编译、Debug APK 均通过；本机 TLS 失败不作为源码编译失败的结论。Windows 本地包生成遇下载超时，尚未完成该包实测；云端构建不替代安装包验证。

已建立 windows/beta、macos/beta、android/beta，均来自验证后的共享 beta；windows/main 来自既有获批 main。macOS 与 Android 稳定线等待对应稳定基线获批，暂不创建冒充稳定版的分支。平台 beta 是后续入口；共享格式与跨平台更改仍提交共享 beta，按提交同步平台分支并记录。

本文件的结果在执行后补充。已有贡献者历史测试保留在 CHANGELOG 与 PR 中，不冒充本轮重跑。

- 平台版本隔离、打包入口、beta 身份与关联检查：通过；原有版本测试与公式测试通过，平台文档 101 个链接通过。
- Windows 桌面 npm run build、完整 npm test、书架五项与雨课堂九项专项全部通过；format:check 通过。Mac 首轮云端构建与多数回归通过，最后 trackpad 因干净环境缺 tmp 目录中断；已补工作区准备步骤，等待重跑。
- Android JVM、测试编译与 Debug APK：待执行。
- Android 模拟器核心交互：待执行。
- Windows 新界面截图：五张已采集并检查，见 docs/windows/guide/README.md；均为真实应用与合成材料，未执行在线 AI 请求。
- Mac 真实运行：本机为 Windows；新增 macOS CI 和截图采集入口，不宣称本地 Mac 通过。

## 仍需逐项确认的范围

真实 Office 编辑、真实雨课堂课堂、在线模型服务、物理手写笔、Mac 原生窗口和所有功能的美学确认，不能由编译或模拟器结果替代。未覆盖内容如实标注，按既有发布政策处理，不虚构全环境通过。

### 首轮失败与修正

- npm 在线依赖安装因连接中断失败；本机复用同版本已有依赖，并按锁文件完整性恢复新依赖。CI 使用干净 npm ci。
- Android Java HTTPS 握手失败，使用仅本机 HTTPS 验证转发排查，不把临时代理或机器路径提交仓库。
- Windows 书架测试中的符号链接需要额外权限；目录场景改用 junction，继续验证跨项目路径逃逸被拒绝，保留断言。
- 首轮 CI 的 Android SDK 默认 tools 包已停用，显式安装 platform-tools、API 35 与对应 build-tools；Windows/Mac 首轮干净工作区缺 tmp 目录已修正。

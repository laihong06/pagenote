# 桌面书架验证记录

实施：2026-10-22 至 2026-10-23（Asia/Shanghai）。
范围：Windows / Mac 共用实现；初始本地开发版本 `1.0.1-dev.7`，Mac arm64 实际打包验证。
PR 集成版本为 `1.1.0-beta.5-dev.1`，沿用上游独立 Beta 身份与数据目录。

## 已实现

- 首次绑定目录，启动/返回/窗口激活时受限扫描，网格/列表、搜索、排序、最近文档入口。
- 默认 PDF 完整页面封面、Markdown `Note` 纸本，四种配色、标题、图片、PDF 指定页及重置。
- 兼容 Android 的 `src/bookshelf.json` v1；自定义图片保存在项目 assets。
- 返回书架先保存；失败留在阅读器并保留草稿；按项目路径区分同 ID 副本和阅读位置。
- 新建/书架独立导入默认使用绑定目录；旧“导入左侧”行为保留；目录更换不搬移文件。
- 雨课堂默认目标目录、可显式改选、首张真实页建项目入架，不打断原书阅读。
- 原生文件菜单、网页设置与 Mac 原生通用设置接入目录选择。

## 测试证据

`npm run test:bookshelf` 五个脚本通过，并使用
`DESKTOP_EXE=.../release/mac-arm64/pagenote.app/Contents/MacOS/pagenote`
在打包应用重跑 UI 流程：

- `bookshelf-store.mjs`：嵌套/旧格式项目、重复 ID、损坏项目、失效目录、数量上限、
  符号链接、绑定持久化、旧 key 失效及损坏本机配置重新绑定恢复。
- `bookshelf-covers.mjs`：v1 封面字段、PDF 源、标题/路径/版本检查、图片复制、非法图片、
  20 MiB 输入限制、资产符号链接拒绝、图片变更缓存失效、损坏 sidecar 回退及重置不删资产。
- `bookshelf-projects.mjs`：绑定目录默认创建、重名不覆盖、独立导入、失败不留半项目；
  扫描后源文件/笔记被换为符号链接时，打开及笔记写入拒绝，项目外字节不变。
- `bookshelf-ui.mjs`：实际目录选择桥接（系统选择结果由测试替身提供）、启动/重启、
  PDF 非空像素、`Note`、搜索/列表、标题/纸本自定义、保存失败保护、同 ID 副本交替
  读写、最近文档入口、中英文/明暗主题、独立 PDF 导入、页码恢复及新建自动入架。
- `yuketang-save-race.mjs`：暂停课件事务读取旧笔记后并发自动保存，确认同一写入队列
  保留最新 portable 笔记；项目重新打开亦等待待写入队列。

其他通过项：

- `node tests/project-launch.mjs`、`node tests/project-open.mjs`：显式文件冷/热启动不被书架遮挡。
- `node tests/projects.mjs`：既有项目/左侧替换、笔记、资源、移动重开与批注回归。
- `node tests/menus.mjs`：菜单、雨课堂入口、已有目录选择桥接及工具布局。
- `node tests/native-settings.mjs`：实际 AppKit 窗口创建、复用、记忆和退出清理。
- `npm run test:yuketang`：九个定向协议/存储/路由脚本。
- `node tests/yuketang-ui.mjs`：实际打包应用内模拟课程、考勤确认门控、绑定目录免重复
  选择、首张前无占位、首张建项目不切走旧书、第二页追加及笔记保留；不连接真实课堂。
- `npm run format:check`、`npm run test:version`、`git diff --check`。

各主功能及故障有对应 RED/GREEN 记录，补充断言也纳入最终回归。过程中修正了旧清单缺少 created 时的最近
文档排序异常、不可见的返回按钮、英文排序文本裁切和实际打开/保存路径校验。

最终模拟采集复测还暴露了原有两条写入队列的竞态：页面追加已读旧 right.md，而自动
保存先完成时，追加事务随后会以旧内容覆盖 right.md。通过真实文件 I/O 暂停点稳定
复现为 RED；页面本地事务改为与自动保存共用 writeQueue，图像下载仍在队列外，
并在项目重开前等待该队列，定向回归变为 GREEN。

## 本地应用操作

- `npm run package:mac:local` 成功；只构建 arm64，未签名。既有 Vite 大分块及构建器
  重复依赖提示仍存在，不是失败。打包内容确认包含 `desktop/bookshelf.cjs`。
- 检查最终 asar 时间晚于 src / desktop / dist / locales / skills 及顶层运行时输入；
  包内原生设置可执行文件晚于 Swift 输入。包版本仍遵循项目的 Mac 实验渠道策略，
  不只用相同版本号作为新代码证据。
  最终 asar 为 `2026-10-22T16:11:57.166Z`，最新运行时输入为
  `2026-10-22T16:11:52.012Z`；即本地 2026-10-23 的最终构建。
- 正常点击应用菜单 Quit，随后从固定 `release/mac-arm64/pagenote.app` 重新打开；
  实际用户配置中首页显示未绑定书架，未自动打开旧阅读器。
- 实际打开原生书架目录选择器后取消，确认绑定没有改变；未选择或搬移用户旧目录。
- 原生设置入口启动了包内辅助进程；CUA 定位辅助窗口受同 bundle ID 多副本及捕获错误
  限制，未手工逐控件验证新增的目录改选行。AppKit 窗口创建与退出由上述独立测试验证。
- 队列修复后，最终包书架五脚本通过，模拟采集 UI 连续两次通过；最后从用户固定入口
  重开，确认书架及“最近文档”控件可见，保留未绑定状态供用户选择自己的目录。

截图是隔离测试数据，不包含用户课件：

- `release/bookshelf-desktop.png`（1440x960）
- `release/bookshelf-desktop-compact.png`（900x620）
- `release/bookshelf-desktop-dark.png`（暗色）

## 边界与决策

- Windows 共用代码已接入，但没有 Windows 机器运行验证，也未发布 Windows 新安装包。
- 按用户要求采用精简测试；未跑全量历史 Office/设备矩阵、真实雨课堂、20MP 解码压力、
  专门的缩略图并发压力或独立 capture 窗口专项测试，不宣称这些项目已通过。
- 本轮是作者自检，没有新建子代理或独立审查代理。
- 首张采集页只刷新书架，不擅自切走当前阅读；历史导入属于明确操作，仍打开新项目。
- 实现阶段保留混合改动工作区，未自动提交/合并/推送，也未发布 Release 或改公开下载。
- Lucide 精确锁定版本并加入打包许可证清单；npm 安装造成的无关 libc 锁文件变化已恢复。

## PR 交付复验（2026-10-23）

- 用户已授权验证后推送功能 PR；复用上游已有 PR #11，避免重复提交雨课堂基础实现。
- 重新运行打包 Mac arm64 的书架五脚本、雨课堂九脚本、模拟课堂 UI、项目冷/热启动、
  格式和版本检查，均通过；不连接真实课堂。
- Android app / feature-yuketang JVM 测试显式重跑通过；debug APK 和仪器测试 APK
  编译通过。通过 `adb install -r` 保留数据安装，不运行会卸载应用的 Gradle 设备任务。
- API 34 平板 AVD 的 BookshelfRepositoryTest、BookshelfHomeTest、BookshelfFlowTest
  三项通过：扫描、首页、新建、保存返回、自定义封面、搜索/列表及重建恢复均覆盖。
- 仅推送功能分支并更新 PR，保留 CHANGELOG；不合并 main，不发布二进制或更新下载链接。

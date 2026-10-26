# 已知问题与未验证范围 / Known issues

Windows v1.0 已由发起人批准正式发布，macOS 仍为测试版。以下项目是风险与待办记录，不代表都已复现为产品缺陷，也不自动阻塞本次 Windows 发布。当前发布依据见 [发布基准](docs/RELEASE-POLICY.md)。

| 项目 | 状态与后续处理 |
|---|---|
| 历史长测约 54 分钟时窗口关闭 | 原因未明；重开后已保存内容恢复。历史中断不记作一小时通过，保留后续排查。 |
| Mac 全流程与环境覆盖 | 贡献者报告部分实机测试，维护者未独立复验全部 Mac 环境；继续测试版。 |
| Mac 新增设置、主题与触控板手势 | 贡献者在 Apple Silicon 本机及 arm64 包验证自动化流程；物理触控板手工验收、Intel Mac 包和新增共享界面的 Windows 实机复验尚未单独记录，保持测试版。 |
| 桌面书架验证范围 | Windows/Mac 共用实现已接入；Mac arm64 本地包完成隔离书架流程验证，Windows 运行未验证。Mac 新增原生设置目录行未手工逐控件验收；具体测试与边界见 [书架验证记录](docs/spec/bookshelf-desktop/validation.md)。本轮未发布新安装包。 |
| 雨课堂账号与实时课堂 | 桌面/Android 协议回归通过；Android 14 平板 AVD 已完成真实账号登录及 82 页历史导入，且经用户授权验证中途加入真实课堂补采 18 页。观察期间没有新的实时翻页，真实连续追加尚未完整验收；多页追加、去重、保留笔记已通过本地 SAF 回归。Mac arm64 已冷启动并通过打包应用内模拟课堂采集；桌面应用内真实账号导入、Windows 运行尚未验证。此前 Windows 目录包构建提示 `mcp-dist/mcp-node.exe` 缺失，本轮未重建 Windows 包。真实课堂测试需用户明确确认可能产生的签到记录。 |
| Android 实机与完整服务流程 | 以 Android 14/API 34 平板 AVD 做常规验收；小米 Pad 5 Pro 上的长时间笔迹/延迟、侧键、真实 DocumentsUI 持久授权、用户的 69 页项目及 OpenAI 服务端到端仍未全部验证。Office 进程内编辑尚未实现；不宣称与桌面端全量等价。 |
| Android API 34 平板 AVD 仪器测试 | `Pixel_Tablet_API34` 上书架、阅读器工具与页码跳转定向套件 13/13 通过。全量连接套件共 211 项，41 项失败、4 项跳过；失败主要涉及测试在书架冷启动状态查找阅读器控件，以及大屏展开菜单的选择器。未观察到应用崩溃或 ANR。需修正测试前置状态/布局选择器并重跑全量套件后，才能将全量仪器测试记为通过。 / On `Pixel_Tablet_API34`, the focused bookshelf, reader-tools, and page-jump suite passed 13/13. The full connected suite ran 211 tests with 41 failures and 4 skips, mainly from tests looking for reader controls while still on the bookshelf and selectors for the expanded large-screen menu. No app crash or ANR was observed. Fix the test setup/layout selectors and rerun before recording the full instrumentation suite as passing. |
| Android 窄笔盒尺寸 | 笔工具带采用 44dp，按 Android 参考密度约 7 毫米；尚未在小米平板上进行本轮物理宽度测量或新增圆珠笔/激光笔的实体笔验收。API 34 平板模拟器可代替本轮功能验收，不作为阻塞源码 PR 的全设备门槛。 |
| 多种 Office、DPI、双屏和低配置设备 | 未穷尽环境；报告具体条件后逐项处理。 |
| Windows 签名与安全软件 | 当前未签名。早期 NSIS 安装包曾被火绒查杀，未取得厂商复核结论；不将其宣称为误报。新文件以实际检测为准。 |
| Mac 签名、公证和首次打开 | 当前构建未签名、公证；系统可能拦截，需后续完善分发体验。 |
| OCR 运行库与识别质量 | 部分 Windows 环境可能缺少 VC++ 运行库；OCR 不是无损识别，图表仍需结合原页图查看。 |
| 旧 2.0 测试版升级至 1.0 | 有意重置首个正式版本编号；完全退出旧版，手动安装或完整解压新 ZIP。若系统拒绝覆盖，可卸载应用后安装；先备份项目，保留用户数据。 |
| AI、Word/PPT 外部依赖 | 不内置大模型；AI 客户端、服务额度和本机 Office 授权需用户自行准备。 |

遇到 Bug 请[提交 Issue](https://github.com/laihong06/pagenote/issues/new)，附版本、系统、操作步骤及脱敏截图；不要上传私人课件或连接密钥。联系：chennanfeng310@gmail.com。

Windows stable is founder-approved; macOS remains experimental. Android is an Android 14+ ARM64 tablet beta; the Xiaomi Pad 5 Pro, full pen behavior, real SAF grants, user PDF and live OpenAI workflow are not all verified end to end, and in-process Office editing is deferred. Pending work also includes the unexplained interrupted soak, wider environment coverage, signing/notarization, antivirus review, and OCR prerequisites. These are tracked risks under the early-stage release policy, not claims that every environment has passed.

## beta 1.1 测试记录

- beta.4：GitHub macOS 15 arm64 的 Paper 主题回归在“切换排版后拖动源 Markdown 手写线条”的位置断言失败（预期发生位移，检测到线条基本停留原位）。同一套测试在 Windows 通过；尚未区分命中位置、自动化事件与产品定位缺陷。保留为非阻断 Beta 待办，Mac CI 单独展示该诊断结果，不算通过。PDF/笔记阅读与 Mermaid 底栏逐帧回归独立验证。

- 新分层工作流覆盖材料准备、缓存、分页续读、Tag 范围、意见凭据、用户偏好版本保护及实时入口编辑。
- 完整 MCP 回归在“静默更新后通过页码输入跳到第 3 页”的等待断言发生超时；记录显示页码输入为 3、阅读状态仍为 1，已保存的第 3 页笔记存在。两次结果和截图保留在本机 tmp；最小翻页复现未重现，根因尚未确认。不得将此轮完整 MCP 套件写为通过，后续继续排查。正式 v1.0 未被本次 beta 改动覆盖。

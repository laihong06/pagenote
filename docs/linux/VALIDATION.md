# Linux 验证记录

来源：共享 beta `6388245`；Linux 首次适配 PR。环境：Ubuntu 22.04.5 x86_64，Node.js 22.22.0、Electron 44.7.2、LibreOffice，本机图形会话。日期：2026-10-30。以下为贡献者自测，不代表 CI 或维护者复核。

运行代码提交：`9b403d8811c11b3a517f90b8ba065738c1e20982`。后续仅测试／文档／截图补充，应用代码相同；截图来源中的 modified integration worktree 指生成的证据文件，不代表运行界面经过后续修改。Linux 包版本 `1.1.0-beta.5-dev.3`，LibreOffice 7.3.7.2。窗口 1440×960，内容视口 1440×928，缩放 100%、density 1、浅色主题。专注模式尺寸逐张见 provenance。

| 命令／验证项 | 本次结果 | 说明 |
| --- | --- | --- |
| `PAGENOTE_ELECTRON_DIST="$PWD/node_modules/electron/dist" npm run package:linux:local` | 通过 | 完整生产构建与独立 Linux 本地包，复用 Electron 44.7.2 |
| `npm run test:platforms` | 通过 | Windows/Mac/Linux 元数据和入口、beta 身份及文件关联隔离、134 项文档链接 |
| `npm run test:linux` | 通过 | 源码桌面 PDF 导入、渲染、笔记写入与重新读取；LibreOffice ODT/DOCX/PPTX 中文路径转换及缺依赖提示 |
| `DESKTOP_EXE=<Linux包> node tests/linux-smoke.mjs` | 通过 | 实际包启动与 PDF／笔记持久化 |
| `DESKTOP_EXE=<Linux包> node tests/bookshelf-ui.mjs` | 通过 | 封面、主题、导入、阅读位置、创建、保存失败保护与重启 |
| `DESKTOP_EXE=<Linux包> node tests/workflows.mjs` | 通过 | 离线模拟 AI 工作流、缓存、资料与偏好持久化 |
| `node tests/prompt-routing.mjs` | 通过 | 共享教学路由与表达模块 |
| `npm run test:markdown-native` | 通过 | 数据契约、交互写回同一文件与演示项目 |
| `npm run test:yuketang` | 通过 | 九项本地模拟回归，未登录真实账号 |
| `DESKTOP_EXE=<Linux包> node scripts/capture-platform-guide.mjs docs/linux/guide/images/current` | 通过 | 书架、阅读、编辑、批注工具、AI 面板五张真实截图 |
| `PAGENOTE_GUIDE_MARKDOWN=examples/markdown-s-native.md PAGENOTE_GUIDE_NATIVE=1 DESKTOP_EXE=<Linux包> node scripts/capture-platform-guide.mjs docs/linux/guide/images/markdown-native` | 通过 | SVG/Canvas/图表/3D 分别截图；轮盘拖动与键盘、交叉、变异、旋转和刷选；同文件保存及重启恢复 |

截图使用合成资料；本地检查过完整阅读窗口、AI 面板、Canvas 和 3D 结果。维护者布局审查仍待 PR 审查。CI 结果在 PR 中跟踪，不将新增 CI 配置视为已经通过。

未测：AppImage/deb 安装、其他发行版/架构/Wayland、多屏、真实 AI 与雨课堂账号、复杂 Office 版式、与所有历史版本往返读写。Mac/Windows/Android 的真实运行未在本机执行；平台配置隔离由自动化检查验证。保留共享已知问题。

回退：旧版源目录及本地包仍保留；beta 不自动迁移旧用户配置。源码 PR 不上传私人配置、依赖目录或本地二进制，不创建公开 Release。

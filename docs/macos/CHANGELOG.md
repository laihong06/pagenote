# macOS 更新日志

## Unreleased

共享 AI 提示词接入“表达与交互”模块和双轴路由，生成与知识性修改采用统一标准；Mac 原生设置增加对应模块选择项。本轮实际 UI／MCP 回归在 Windows 执行，Mac 原生设置需独立验证；不提升 Mac 公开制品版本。

共享 Markdown S 新增原生 SVG／Canvas／图表／Three.js 实验册与同一 Markdown 文件保存，不以 HTML iframe 作为主方案。专注模式、章节导航及模板导入接入共享代码；本次截图和保存重开验证来自 Windows，Mac 需本轮 CI／实机独立核验，不沿用截图结论。

增加共享桌面演示启动脚本，使用本系统 Electron 路径；本次启动专项在 Windows 验证。

共享 RenderScope 增加取消和完成后的引用释放，具体平台回归另行记录。

共享桌面宿主增加最小化／隐藏时停止预览通知；Mac 原生窗口行为以本平台 CI／实测为准。

共享预览接入删除清理与后台停止；本次项目重启实测在 Windows 完成，Mac 系统行为待验证。

平台 CI 新增 Markdown S 真实 Electron 专项；Mac 结果以实际 CI 为准，不能沿用 Windows 通过结论。

共享 Markdown S 预览增加每视图两个演示上限；本轮新增多引擎验证在 Windows 执行，不宣称 Mac 已通过。

共享 Markdown S 内核新增 Mermaid 优先级、取消与超时队列；本轮真实交互在 Windows 验证，Mac 新内核运行待验证，尚未发布。

开发版本 1.1.0-beta.5-dev.1：整合书架、雨课堂及平台管理结构。保留原有阅读、编辑、手写、Office、OCR、MCP 与设置入口；平台版本独立读取。

本次为源码与本地验证，未公开发布。[验收范围](../INTEGRATION-2026-10-30.md)。

## 1.1.0-beta.4

沿用此前已发布制品；[版本说明](releases/1.1.0-beta.4.md)。

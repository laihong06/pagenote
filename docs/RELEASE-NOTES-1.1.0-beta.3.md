# pagenote 1.1.0-beta.3 · Windows Beta

这是独立于 v1.0 正式版的测试发布。下载 Windows 安装包或完整 ZIP，源码包用于开发；校验值见 SHA256SUMS.txt。GitHub 标记为 Pre-release，不替换正式版 Latest。

本次包含分层可编辑提示词、生成/修改任务路由、本地材料准备、候选学习偏好、中英文路由图，以及 Windows 连续捏合缩放、双向平移和缩放后的批注定位改进。

以 **pagenote Beta** 安装，独立的安装标识、快捷方式和 `%APPDATA%\pagenote-beta` 数据目录，可与正式版并存。不接管 `.pagenote` 文件关联，也不自动导入正式版数据。打开复制后的项目进行试用；主动打开同一个项目时，对该项目的修改仍会写回同一文件。

Mac 此版本尚无对应安装包。Windows 物理触控板尚未实机复验；已通过 Chromium 手势事件验证。完整 MCP 回归曾有翻页超时，见 KNOWN_ISSUES.md；不宣称所有回归均通过。测试版安装包未签名，历史安全软件拦截记录仍保留。

English: This Windows prerelease installs alongside stable with separate app identity, shortcuts and local data. It includes layered AI workflows and Windows reading gestures. Explicitly opened projects remain shared files; copy projects before testing. No matching macOS binary is provided. See KNOWN_ISSUES.md for pending verification and limitations. This prerelease does not replace stable Latest.

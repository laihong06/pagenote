# Windows 共享同步记录

来源：共享 beta 的 6388245（包含 PR #19、#20）；目标：windows/beta；旧基线：5a779a5。通过单独 PR 同步，未从其他平台分支整支合并。无冲突。

来源0131472的Windows CI 41015901025完整通过；829c517本地Windows打包交互、Markdown写入和完整重启恢复通过。本次额外变化为平台导航文档，平台元数据／链接检查通过。Mac、Android不继承Windows结果，其分支不随本PR更新运行代码。

保留平台根README；公开下载仍beta.4，当前dev.2不作为公开发行。未修改任何稳定线代码，未创建Release。

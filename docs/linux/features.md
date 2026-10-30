# Linux 功能与截图覆盖

本表描述 Linux 本地验证范围，不代表全部跨平台能力已验收。所有图片来自隔离配置及合成资料；来源提交与窗口参数见图片目录的 `provenance.json`。

| 模块 | 用途与入口 | 操作及结果 | 截图／教程 | 状态及限制 |
| --- | --- | --- | --- | --- |
| 书架 | 启动后的书架，绑定项目目录 | 选择目录、点击项目卡片；进入阅读并恢复位置 | [书架](guide/images/current/bookshelf.png) | 界面与重启回归通过 |
| 文档阅读 | 打开文件／打开项目 | 导入 PDF 或项目，左右并排阅读 | [阅读](guide/images/current/reading.png) | PDF 渲染与保存重读通过 |
| 笔记编辑 | 右侧笔记编辑入口 | 编辑 Markdown，保存到项目；退出编辑继续阅读 | [编辑](guide/images/current/editing.png) | 共享编辑界面；工作流回归通过 |
| 批注工具 | 阅读界面的批注工具 | 按需展开，选择画笔，切回鼠标退出 | [工具](guide/images/current/ink-tools.png) | 提供真实展开状态；不宣称完整手写设备验收 |
| AI 工作台 | 菜单的笔记处理入口 | 填写任务、选择服务后处理笔记；关闭返回阅读 | [工作台](guide/images/current/ai-workbench.png) | 本地路由回归；真实 AI 调用未测 |
| Office 导入 | 打开文件 | 选择 Word/PowerPoint，由 LibreOffice 转为 PDF | [转换验证](VALIDATION.md) | 无专属界面，复用 PDF 阅读；Unicode 路径转换通过，复杂版式未测 |
| 原生交互笔记 | 右侧 Markdown S 学习笔记 | 修改控件后写回同一 Markdown 文件，重开保留 | [模块证据](guide/native.md) | 复用共享实现；逐模块本机证据，不宣称新引擎 |
| 雨课堂 | 共享 beta 的雨课堂入口 | 登录后获取课件／课堂资料 | [验证范围](VALIDATION.md) | 模拟回归通过，真实登录与在线导入未测、无账号截图 |
| OCR | 共享识别流程 | CPU 识别图片文字 | [限制](README.md) | 保留 Linux 原生库；本提交未重新验证 OCR，未附功能截图 |
| 浏览器启动 | `npm run launch` | 启动服务后由 xdg-open 打开地址 | [运行说明](README.md) | 无新增界面；浏览器完整流程未测 |
| Linux 打包 | `npm run package:linux:local` | 生成独立 beta 可执行目录 | [构建说明](README.md) | 无用户界面；本地包验收见验证记录，安装器未测 |

# Windows 功能结构与截图覆盖

[Windows 入口](README.md) · [编号教程](guide/README.md) · [图片来源](guide/capture.md)

此表说明开发源码的功能和证据，不是公开 beta.4 安装包承诺。本轮沿用仓库原 Windows 图；原来源工作区有修改，未将其改标为当前源码实测。功能存在、截图覆盖、云端测试和维护者验收分别记录。

| 模块 | 用途与入口 | 操作、结果与退出 | 教程 | Windows 图片 | 状态／限制 |
| --- | --- | --- | --- | --- | --- |
| 项目与书架 | 启动后管理课件笔记本 | 选书架目录→打开封面；左上角返回书架 | [1](guide/README.md#module-1) | [书架](guide/images/current/bookshelf.png) | 已有实际界面图；本轮未重测系统目录权限 |
| 原文与逐页笔记 | 阅读器左侧材料、右侧说明 | 页码切换对应笔记，独立滚动；返回书架退出 | [2](guide/README.md#module-2) | [阅读](guide/images/current/reading.png) | 有基础双栏图；不同文档、DPI 与双屏另验 |
| 编辑与保存 | 编辑菜单→文件编辑→笔记区域 | 改当前页→完成编辑／返回双栏；核对保存提示 | [3](guide/README.md#module-3) | [编辑](guide/images/current/editing.png) | 图证明编辑布局，不代替全部保存冲突测试 |
| 公式、代码、Mermaid | 在 Markdown 笔记中表达公式和流程 | 编辑源码后预览；错误输入需修正源码 | [4](guide/README.md#module-4) | [渲染](guide/images/current/reading.png) | 合成材料；不是 OCR 或 AI 输出证据 |
| 选区、Tag 与批注 | 阅读器选择文字或框选范围 | 留下局部意见，关闭面板回阅读 | [5](guide/README.md#module-5) | 当前精确选区／Tag 过程待补 | 普通阅读图不能代替这些操作结果 |
| AI 工作台 | AI 标注／修改意见菜单 | 打开对应侧工作台记录要求；关闭继续阅读 | [6](guide/README.md#module-6) | [工作台](guide/images/current/ai-workbench.png) | 需要自行连接客户端；未用图片声称真实服务已调用 |
| 提示词与模型配置 | 设置中的 AI 模块 | 修改模块并保存，恢复默认针对当前项 | [7](guide/README.md#module-7) | 当前 Windows 设置图待补 | Mac AppKit 图不代表 Windows |
| 画笔与荧光笔 | 阅读器左侧画笔入口 | 按需展开颜色／粗细，切选择工具退出 | [8](guide/README.md#module-8) | [工具展开](guide/images/current/ink-tools.png) | 未用一张工具图证明真机压感、所有撤销或持久化 |
| 原生 Markdown S | 打开 genetic-lab 笔记 | 点基因、采样、世代及三维旋转；返回笔记退出专注 | [逐模块教程](guide/markdown-s.md) | [SVG](guide/images/markdown-native/native-svg.png)、[Canvas](guide/images/markdown-native/native-canvas.png)、[图表](guide/images/markdown-native/native-chart.png)、[三维](guide/images/markdown-native/native-three.png) | 既有 Windows 运行与保存记录；仅此已实现模板，不是任意脚本引擎 |
| OCR | 页面准备／识别工具 | 本地模型识别后人工核对，再用于讲解 | [9](guide/README.md#module-9) | 识别过程和结果图待补 | 依赖模型与运行库，不能把公式渲染图标为 OCR 通过 |
| Office | 文件导入／外部编辑入口 | 借助本机 Office 转换，外部保存后返回刷新 | [10](guide/README.md#module-10) | 当前待补 | 本轮未操作真实 Office，不承诺进程内完整编辑 |
| 雨课堂 | 文件／雨课堂入口 | 账号登录，选择课程导入或授权采集 | [11](guide/README.md#module-11) | 当前待补 | 需用户账号与授权；无本轮在线操作 |
| 历史、备份与迁移 | 文件菜单和项目目录 | 按菜单恢复；先复制完整项目再迁移 | [12](guide/README.md#module-12) | 当前待补 | 不等于内置跨设备云同步；未做本轮恢复验证 |
| 附件与导出 | 插入／导出菜单 | 选择文件或输出格式，再核对落盘文件 | [13](guide/README.md#module-13) | 当前待补 | 本轮未验证输出文件与升级兼容 |
| 主题、缩放与位置 | 设置、阅读器底部缩放／页码 | 调整显示，切页后保存阅读状态 | [14](guide/README.md#module-14) | [当前浅色界面](guide/images/current/reading.png) | 仅现有浅色状态；深色、窄窗及位置恢复过程待补 |

未覆盖项明确保留待补，不以其他系统图片补位。当前源码 Windows 云端通过范围和本轮作者未测范围见[审查记录](../review/PR13-evidence-2026-10-30.md)。

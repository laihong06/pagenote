# macOS 功能与截图覆盖

[Mac 入口](README.md) · [教程](guide/README.md) · [截图来源](guide/capture.md)

以下是共享 beta 源码 `6388245cbc919ca7b1335fbf59f36abc6cf5a5be` 在真实 macOS 上的走查。平台配置仍为 `1.1.0-beta.5-dev.1`；直接从共享源码启动的 Electron 显示公共开发版本 `1.1.0-beta.5-dev.2`。没有改动这两个版本，也没有发布新 Mac 包。

| 模块 | 用途 | 实际入口 | 操作／结果与退出 | 教程 | 本平台截图 | 本轮状态与限制 |
| --- | --- | --- | --- | --- | --- | --- |
| 书架与项目 | 在一个目录中查找课件笔记本 | 启动书架→选择书架目录 | 通过 macOS 选择器选目录→打开封面；左上角返回书架 | [阅读](guide/reading.md#从书架进入) | [完整 Mac 窗口](guide/images/current/window-bookshelf.jpg) | 已打开合成项目；未验证全部存储权限场景 |
| PDF／Markdown 与逐页笔记 | 左侧材料、右侧当前页说明 | 阅读器页码和双栏 | 翻页显示对应笔记；两侧独立滚动 | [阅读](guide/reading.md) | [整窗](guide/images/current/window-reading.jpg)、[公式和图表](guide/images/current/reading.png) | 本轮以 Markdown 原文走查；PDF、长文档回归另验 |
| 笔记编辑 | 在当前页改正文、代码和公式 | 编辑菜单→文件编辑→选择笔记区域 | 展开编辑→修改→完成编辑／返回双栏 | [编辑](guide/reading.md#编辑与返回双栏) | [展开编辑](guide/images/current/editing.png) | 真实编辑界面已采集；本轮未进行大规模编辑回归 |
| 画笔与荧光标记 | 给阅读内容加本地标记 | 左侧画笔入口 | 展开颜色和粗细；切选择工具收起 | [工具](guide/reading.md#画笔与修改意见) | [画笔展开](guide/images/current/ink-tools.png) | 已打开工具；真机笔、触控板和持久化专项未重跑 |
| 选区意见与工作台 | 把修改要求留在对应材料一侧 | AI 标注／修改意见菜单→右侧修改意见 | 选择范围并记录要求；关闭工作台回阅读 | [工作台](guide/reading.md#画笔与修改意见) | [AI 工作台](guide/images/current/ai-workbench.png) | 本轮打开工作台；没有伪造在线模型结果 |
| 原生外观设置 | 设置系统跟随、浅深色和 Markdown 配色 | Command + 逗号→外观 | 更改项即时生效；关闭设置窗继续阅读 | [AppKit 设置](guide/ai-settings.md) | [原生外观](guide/images/current/native-appearance.jpg) | 真实 AppKit 窗口；本轮只查看，未更改个人偏好 |
| 原生阅读设置 | 配置换页动画与编辑展开 | 原生设置→阅读 | 切开关即时生效；关闭窗退出 | [设置](guide/ai-settings.md) | [原生阅读](guide/images/current/native-reading-settings.jpg) | 已打开；不代表全部平台 CI 通过 |
| AI 提示词模块 | 配置通用教学和表达要求 | 原生设置→AI→提示词模块 | 选择模块→编辑→保存；恢复默认针对当前模块 | [AI 设置](guide/ai-settings.md) | [表达与交互](guide/images/current/native-ai-settings.jpg) | 已查看真实模块；未发送模型请求或修改密钥 |
| 原生 Markdown S：SVG | 点基因，直观看到编码改变 | 导入 `genetic-lab` 笔记→专注作品 | 点“基因 1”：`100001010000`→`000001010000`；返回笔记退出专注 | [SVG](guide/markdown-s.md#svg基因) | [前](guide/images/markdown-native/native-svg-before.jpg)／[后](guide/images/markdown-native/native-svg-after.jpg) | 已操作，并检查写回同一 Markdown 文件 |
| 原生 Markdown S：Canvas | 查看采样位置对应的适应度 | 作品章节“03 适应度地形” | 采样位置 50→51，结果 22.79→20.79 | [Canvas](guide/markdown-s.md#canvas采样) | [前](guide/images/markdown-native/native-canvas-before.jpg)／[后](guide/images/markdown-native/native-canvas-after.jpg) | 已操作，`position: 51` 已写入文件 |
| 原生 Markdown S：图表 | 对比演化世代的最佳值 | 作品章节“07 演化记录” | 世代 0→1，最佳适应度 67.22→73.02 | [图表](guide/markdown-s.md#原生数据图表) | [前](guide/images/markdown-native/native-chart-before.jpg)／[后](guide/images/markdown-native/native-chart-after.jpg) | 已操作，`generation: 1` 已写入文件 |
| 原生 Markdown S：Three.js | 查看三维染色体与旋转状态 | 作品章节“08 三维染色体” | 继续旋转→暂停旋转；再次点击可暂停 | [三维](guide/markdown-s.md#threejs三维课件) | [初态](guide/images/markdown-native/native-three-before.jpg)／[运行](guide/images/markdown-native/native-three-running.jpg)／[暂停](guide/images/markdown-native/native-three-after.jpg) | 模型实际显示和按钮状态已采集；拖拽、GPU 恢复、暂停状态重启保真未在本轮验收 |
| OCR | 为扫描页准备文字和图片 | 页面准备／OCR 工具 | 安装兼容本地模型后识别、核对结果 | [未验证范围](guide/README.md#本轮未验证的模块) | 当前结果图待补 | 本轮未运行模型，不用普通笔记图代替 OCR 证据 |
| Office 阅读与外部编辑 | 借助本机 Word／PowerPoint 转换或修改材料 | 文件导入／外部编辑 | 授予系统自动化权限；外部保存后返回刷新 | [Mac Office](../MAC-OFFICE.md) | 本轮待补 | 本轮未调用 Office，既有说明不等于当前源码已验收 |
| 雨课堂 | 配置账号、导入历史课件或授权采集 | 文件／雨课堂入口 | 需要账号、网络与课程授权 | [未验证范围](guide/README.md#本轮未验证的模块) | 本轮待补 | 无账号请求；不展示私人课程 |
| 历史、备份、附件与导出 | 恢复笔记或携带完整项目迁移 | 文件／编辑菜单及项目目录 | 按菜单恢复／导出；迁移复制完整项目目录 | [项目格式](../PROJECTS.zh-CN.md) | 本轮待补 | 本轮未执行恢复、导出和升级验收 |

“原生 Markdown S”当前指 `genetic-lab` 这一已实现模板，不是通用任意脚本引擎。SVG、Canvas、图表、Three.js 是同一作品里的不同显示模块，各有独立画面和操作记录。旧 HTML 沙盒只作兼容，不能据此扩大原生能力。

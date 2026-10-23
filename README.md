# pagenote · 正式版

**许可：新增原创内容采用 PolyForm Noncommercial 1.0.0。允许个人非商业下载、使用和修改；超出许可范围的商业用途须另行授权。[适用范围与历史授权例外](LICENSING.md)。**

**[⬇ Windows 正式版安装包](https://github.com/laihong06/pagenote/releases/download/v1.0.0/pagenote-1.0.0-windows-x64-setup.exe)** · [正式版免安装 ZIP](https://github.com/laihong06/pagenote/releases/download/v1.0.0/pagenote-1.0.0-windows-x64.zip) · [正式版源码](https://github.com/laihong06/pagenote/releases/download/v1.0.0/pagenote-1.0.0-source.zip)

这里是 **main 正式分支**，本页介绍和下载对应 Windows **v1.0.0**。新功能与测试版安装包请前往 [beta 分支](https://github.com/laihong06/pagenote/tree/beta)。macOS 尚未发布正式安装包。

<img src="build/icon.png" alt="pagenote 双页图标" width="88">

[English](README.en.md) · 简体中文

pagenote 把课件和讲解放在一起：左边读原文，右边看对应页的笔记。

## 做这个工具的初衷

AI 浪潮扑面而来，用 AI 辅助学习早已是寻常选择。大家常常把课件、论文交给 AI，期待它顺着已有的知识基础慢慢铺陈，拾级而上，一步步把难懂的知识点讲明白。

但这套学习方式，一直有着很现实的短板。阅读时总要在两个窗口来回切换，原文和讲解各占一边，翻着翻着就对不上内容。AI 有时还会调整原文的行文顺序，打乱课件本身的逻辑，跟着读的时候很容易跟不上节奏。如果一次性让 AI 解析完整份材料，又容易受上下文和输出长度制约，大多只能拿到概括性的总结，细致的推导、专业符号和配套例子经常被省略。看似已经看懂了内容，等到真正做题实操，依旧难以灵活运用。

这些细碎又恼人的痛点，催生了 Pagenote 的想法。它希望把原文和讲解按页面牢牢对应在一起，每一页都如同一次独立的请教，从既有认知出发，补齐所有省略的中间步骤，承接前文继续往下推进。遇到难以理解的片段，可以选中文字、框选图片，简单备注需求，让 AI 换例子、换思路重新解读；笔记能够随学随改，不用反复复制内容，一次次重复交代前置背景。同时充分发挥 AI 多样的能力：抽象概念搭配示意图，算法附上可运行代码，数理推导生成公式，思考流程绘成框图，让抽象的知识变得直观易懂，这就是 Pagenote 最初的设计初衷。

## 功能一览

以下截图使用自编学习示例，拍摄于 beta.5 阶段；用于展示双栏阅读、编辑与标注流程，不代表 Mac 的原生窗口外观。

### 原文与讲解，按页对照

左边阅读文档，右边查看当前页的 Markdown 笔记。翻到下一页，对应讲解随之切换，原文与笔记各自滚动。支持 PDF、Markdown；Word 和 PowerPoint 可借助本机 Microsoft Office 转换阅读。

![双栏阅读：左侧原文，右侧对应页讲解](docs/images/paired-reading.png)

### 公式、代码和流程图

笔记支持 LaTeX 公式、代码高亮、Mermaid 图表、表格和图片粘贴。推导、验证代码和流程可以放在同一页，阅读时直接显示渲染结果。

代码高亮在本机完成，不需要联网下载语法。显示异常时，可参考 [代码渲染说明](docs/CODE-RENDERING.md) 提供版本和最小示例，便于排查。

公式若显示成一串反斜杠源码，可能是复制后的转义格式没有被识别。当前版本已补充这类兼容处理，具体范围见 [公式渲染说明](docs/FORMULA-RENDERING.md)。

![Markdown 中的公式、Python 代码高亮和 Mermaid 流程图](docs/images/markdown-rendering.png)

### 需要修改时，再展开编辑

通过编辑菜单选择右侧笔记，展开编辑区域和对应工具。直接修改渲染后的内容，代码高亮随输入更新；完成后回到双栏阅读。Word、PDF 和 PPT 的源文件编辑交给本机默认应用，保存后回到 pagenote 更新预览。

![笔记展开后的编辑区域与工具菜单](docs/images/note-editing.png)

### 把修改要求留在具体位置

选中文字或框选区域，就能添加 AI 标注。比如“这一段没懂，补个例子”，或“这里公式太密，先用文字解释”。批注窗口在对应一侧打开，可以拖动，不必离开当前页面。

![选中文字后，在同侧浮窗填写 AI 修改要求](docs/images/ai-annotation.png)

标注汇集到对应一侧的工作台，也可以记录整篇的讲述风格要求。双击页上的标注编号可以打开工作台，右键可以删除标注。连接的 AI 可通过 MCP 读取这些要求，再修改对应笔记。

![AI 标注工作台中的局部修改记录](docs/images/ai-workbench.png)

目前的 Pagenote，支持双栏阅读、逐页笔记和 AI 标注，借助 MCP 接口，允许 AI 读取和修改页面内容。软件本身并不内置大模型，逐页精讲、图像生成这类功能，需要搭配具备对应能力的 AI 客户端配合使用。

连接 AI 后，请按 [逐页精讲约定](docs/AI-TEACHING.zh-CN.md) 执行：每页单独发起讲解请求，给足本页上下文，带入当前页及前后相邻源页，不等待上一页讲稿。不要一次生成整章后再拆页。

当前版本内置了 [逐页精讲技能](skills/pagenote-page-tutor/SKILL.md)。在 **AI 标注 → 复制逐页精讲提示词** 中复制后交给 AI，即可复用这些要求；支持 MCP 提示模板的客户端可调用 `pagenote-page-tutor`，填写学习基础、页码范围和目标。AI 也能直接读取资源 `page-notes://skills/page-tutor`。模板提供讲解要求，不会在本机自行调用大模型。

**设置 → AI 设置** 可以分别修改系统提示词、学习讲解、逐页精讲、AI Tag 修改、输出格式与学习背景模块，也可以恢复默认。它适用于通用的文档协作；逐页精讲技能只在相关学习任务中使用，不会把摘要、翻译或简答强行变成长篇讲解。提示词保存在本机；系统提示词保存后需重新连接客户端，模块修改在下次读取时生效。MCP 会提供这份默认指令，实际系统提示层由客户端决定。

当前版本支持按需读取当前页，原文与笔记分别获取，局部修改只读相关段落。AI 可以把简短的页间知识交接单独保存到项目，需要跨会话续做时使用，不作为并行生成前提；原文或笔记变化后会提示交接过期。减少重复读取，让上下文留给当前页的详细解释，具体参数见 [MCP 接口说明](MCP接口说明.md#按需读取与知识交接)。

当前版本还内置离线中英文 OCR，能为 AI 准备逐页文字、坐标和置信度、整页或区域图片，以及 Markdown 表格和 Mermaid 图像，结果随项目缓存。原文和原图保留，识别不准的公式或图表仍需核对，详见 [本地页面准备](docs/LOCAL-PREPARATION.md)。

**当前公开版本：1.0.0。** 已安装旧版的用户需要手动升级：关闭旧版后安装新版，ZIP 版请完整解压到新目录，不要只替换 EXE。 后续继续改进，欢迎拿真实的学习材料试用，也欢迎告诉我哪里用起来不顺。

## 下载与安装

[下载 Windows x64 安装包](https://github.com/laihong06/pagenote/releases/download/v1.0.0/pagenote-1.0.0-windows-x64-setup.exe) · [完整 ZIP 分发包](https://github.com/laihong06/pagenote/releases/download/v1.0.0/pagenote-1.0.0-windows-x64.zip) · [发布说明与校验值](https://github.com/laihong06/pagenote/releases/tag/v1.0.0)

1. 在本仓库的 **Releases** 中下载 `pagenote-版本-windows-x64-setup.exe`。浏览源码时的 **Code → Download ZIP** 是源码包，不能直接安装。
2. 双击安装包，按向导选择安装位置。安装仅针对当前用户，通常不需要管理员权限。
3. 从桌面或开始菜单启动 **pagenote**。
4. 从 **设置 → 语言 / Language** 选择简体中文或 English。

也可下载 Windows ZIP 分发包，解压整个文件夹，再运行其中的 `pagenote.exe`。不要单独移动 EXE，它依赖同目录的资源。

正式上传 Release 前，应公布 `SHA256SUMS.txt` 并完成安全扫描。当前构建没有发行者签名；如果杀毒软件拦截，请停止运行并向维护者提供日志，**不要关闭防护或添加排除项**。

### macOS 状态

macOS 仍处于测试阶段，本页不提供正式版下载。对应测试构建的供应情况见 [beta 分支](https://github.com/laihong06/pagenote/tree/beta)，平台说明仍保留在 [Mac 接入文档](docs/MAC-OFFICE.md)。

## 这次 Mac 适配带来的变化

Mac 适配由 [obroccolio](https://github.com/obroccolio) 在 [PR #3](https://github.com/laihong06/pagenote/pull/3) 中贡献：Finder 打开项目、Dock 唤回窗口、原生菜单与 Command 快捷键，以及本机 Word/PPT 转换和保存刷新。MCP 可使用独立 Word 窗口读取和进行带版本检查的修改，Word 截图通过临时 PDF 渲染。Apple Silicon 与 Intel 均有构建入口，普通用户无需另装 Node.js。

本仓库已合入适配代码；上方 Mac 包由贡献者单独发布，可能与合并后的源码存在差异。Windows 的验收结果不直接适用于 Mac，具体限制与待办统一记在 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。

## 其他操作

- **手形工具**用于平移页面；**画笔**提供书写、荧光笔和橡皮擦。
- Ctrl + 鼠标滚轮缩放当前区域；笔记缩放以字号变化实现。
- 左侧页间分界越过视图中线时，切换对应笔记；可在设置中关闭切换动画。
- 自动保留笔记历史，支持恢复；项目、笔迹和标注保存在本机。
- 项目交给外部 AI 客户端处理时，数据的使用方式取决于该客户端的隐私政策。

## 项目格式

从 **文件 → 新建项目**创建，指定名称和保存目录。项目可以整体移动：

```text
我的项目/
  project.pagenote          # JSON 项目入口，使用相对链接
  src/
    left.md                # 左侧源文件；导入后可能为 left.pdf / left.docx 等
    right.md               # 右侧笔记，以 <!-- pagebreak --> 分页
    assets/                # 图片及附件
    annotations.json       # 笔迹（产生笔迹后保存）
    ai-reviews.json         # AI 标注（产生标注后保存）
```

两个平台都可以从 **文件 → 打开项目** 读取 `project.pagenote`。Windows 安装版会注册双击打开；Windows ZIP 版若没有关联文件类型，在分发目录运行一次 `register-project-files.cmd`，移动软件后需重新注册。Windows 若已有其他默认应用，请在“打开方式”里选择 pagenote。Mac 将应用移入 Applications 后可使用 Finder 打开项目；若未关联，可在“打开方式”中选择 pagenote，无需运行 Windows 的 `.cmd` 脚本。

旧版 `project.page-notes.json` 也可从软件里打开。入口中的 `left` 与 `right` 链接是文件来源，打开时按链接重新读取 `src`；不要只移动入口文件。

新版将品牌改为 pagenote。检测到旧版默认文档数据且新版尚无文档数据时，会复制文档数据，保留旧目录。已有项目文件夹无需改名或重建。

beta.3 修复了部分 Windows 加密或重定向数据目录中的迁移、临时文件替换和跨盘复制错误。复制中断时保留恢复文件；这些兼容路径不能提供与原子重命名完全相同的断电保证。

## 系统要求

- Windows 10 / 11，x64：v1.0 正式版，依据 LTH0001 测试与发起人本机补测，由发起人批准发布。
- macOS 12 或更高：测试版，支持 Apple Silicon 与 Intel 构建；Windows 的正式版状态不适用于 Mac。
- ARM64 Windows 暂未验证。
- PDF 与 Markdown 不要求安装 Office。Word/PPT 导入、项目预览及保存后刷新需要本机已授权的 **Microsoft Word / PowerPoint**，Windows 与 macOS 均可使用。
- macOS 首次接入时，在系统提示中允许 pagenote 控制 Word / PowerPoint；被拒绝后可在 **系统设置 → 隐私与安全性 → 自动化** 中开启。Mac 的 Word 编辑使用独立窗口，MCP 支持读取、选区、带版本检查的替换及 PDF 渲染截图。详见 [Mac Office 说明](docs/MAC-OFFICE.md)。
- 安装版包含 MCP 所需 Node.js 运行时，普通用户不需要安装 Node.js。

## 从源码运行

安装 Node.js 22.13 或更高版本，然后在项目根目录执行：

```bash
npm ci
npm run build
npm run desktop
```

开发网页模式：`npm run dev`。生成 Windows 安装包和 ZIP：`npm run package:desktop`；文件夹版：`npm run package:dir`。生成 macOS DMG 与 ZIP（Apple Silicon 和 Intel 双架构）：`npm run package:mac`；仅本机架构的文件夹版：`npm run package:mac:local`。测试套件在 Windows 与 macOS 上均可运行。发布流程见 [中文发布教程](docs/RELEASING.zh-CN.md) 与 [English release guide](docs/RELEASING.en-US.md)。

## 工程与发布状态

术语和翻译集中在 `locales/catalog.json`，语言设置持久化保存；用户文档内容不参与界面翻译。详细术语依据见 [术语规范](docs/TERMINOLOGY.md)。

验收范围及仍需完成的工作见 [发布准备报告](docs/RELEASE-READINESS.md)。提交问题时请注明版本、系统、复现步骤和脱敏日志；不要上传个人项目或 MCP 连接密钥。

## 反馈与联系

使用中遇到 Bug，欢迎[提交 Issue](https://github.com/laihong06/pagenote/issues/new)。附上软件版本、操作步骤和截图，会更方便我复现和排查。

有建议，或想交流一下这个工具，也可以发邮件给我：[chennanfeng310@gmail.com](mailto:chennanfeng310@gmail.com)。

## 许可

参见 [LICENSE](LICENSE) 和 [许可范围](LICENSING.md)。发起人新增原创内容采用 PolyForm Noncommercial 1.0.0；历史 MIT 版本及已有贡献保留原授权和声明，第三方依赖仍遵守各自许可。本项目相关部分属于源码可见、限制商业用途的软件。商业授权咨询：[Email](mailto:chennanfeng310@gmail.com)。

MCP 接入文档：[中文](MCP接口说明.md) · [English](docs/MCP.en-US.md)

编辑方式和图标说明：[本轮调整](docs/EDITOR-REFERENCE.md)。
单独导入的文件编辑的是 pagenote 的本地副本；项目编辑打开 `src` 中链接的文件。保存后切回 pagenote。

beta.5提供统一提示词索引 `page-notes://prompts/index`：初始生成、AI Tag 修改和普通编辑按不同流程读取模块，正文按版本复用。逐页建议并发数为 `max(1, floor(总页数 / 10))`，受客户端、服务配额及本机资源限制。提交前可调用本地 `page_notes_validate_note` 检查格式与附件，避免把截图返工当成固定流程。它不保证知识正确或所有设备渲染一致。完整文字见 [提示词清单](docs/PROMPTS.zh-CN.md)。

## 贡献与致谢

感谢 [obroccolio](https://github.com/obroccolio) 完成 Mac 适配与安装包分发；感谢 [LTH0001](https://github.com/LTH0001) 提供公式修复回归、RC 验收与测试改进。Windows v1.0 的发布依据是 LTH0001 的测试加上项目发起人的本机补测，由发起人综合结果批准。欢迎 Fork 修改，也欢迎提交 PR；请同时更新变更日志，并注明验证过的平台和范围。

[发布基准](docs/RELEASE-POLICY.md) · [已知问题](KNOWN_ISSUES.md)

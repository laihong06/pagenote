# pagenote MCP 接口

## 接入

1. 启动pagenote桌面版，选择菜单 **AI 标注 → 复制 MCP 配置**。
2. 将得到的 `mcpServers.page_notes` 配置添加到上层 AI 客户端的 MCP 设置。
3. 保持pagenote运行。使用标准 MCP stdio 传输，适配器和 Node.js 运行时均已随软件提供，不需要 npm 安装。

软件会在当前 Windows 用户数据目录生成 `mcp/mcp-config.json`。选择 **AI 标注 → 打开 MCP 配置目录** 可以找到它。配置中的适配器会在每次调用时读取软件的连接文件，因此软件切换到空闲端口后，无需修改配置里的端口。

默认数据位置由系统按用户配置保存（Windows 为 `%APPDATA%/pagenote/`，macOS 为 `~/Library/Application Support/pagenote/`），以菜单显示的位置为准。连接文件含本机接口凭据，只在本机使用，不需要传给模型作为提示词。

## 能力

| MCP 工具                                               | 用途                                                         |
| ------------------------------------------------------ | ------------------------------------------------------------ |
| `page_notes_list_documents`                            | 获取文档列表和 documentId                                    |
| `page_notes_get_state`                                 | 默认获取当前文档、页码和模式；`detail=full` 获取完整状态     |
| `page_notes_read_pages`                                | 按需读取原文、笔记、版本元数据与局部范围                     |
| `page_notes_prepare_page`                              | 本地 OCR、页面和区域图片、Markdown 表格/Mermaid 预处理与缓存 |
| `page_notes_read_handoff` / `page_notes_write_handoff` | 读取或保存单页交接并检测内容变化                             |
| `page_notes_navigate`                                  | 在用户窗口打开文档并跳页                                     |
| `page_notes_write_notes`                               | 替换单页或批量笔记                                           |
| `page_notes_patch_note`                                | 追加笔记或精确替换某段文字                                   |
| `page_notes_get_layout` / `page_notes_set_layout`      | 读取或修改纸宽、字体、行距及布局                             |
| `page_notes_import_document`                           | 导入本机 PDF、Word、PPT、Markdown                            |
| `page_notes_create_markdown`                           | 创建 Markdown 源文档                                         |
| `page_notes_attach_image`                              | 将图片插入指定页笔记                                         |
| `page_notes_screenshot`                                | 获取工作区、原文或笔记截图                                   |
| `page_notes_contact_sheet`                             | 把最多 12 页截图拼成带页码的网格图片                         |

资源 `page-notes://state` 也可以读取当前状态。

## 按需读取与知识交接

需要本地 OCR 或图表准备时，调用 `page_notes_prepare_page`，随后按需取文字、文字块、Markdown 表格或图像。详见 [本地页面准备](docs/LOCAL-PREPARATION.md)。源文件与视觉材料保留，识别结果有置信度和警告，不宣称无损转换。

开发版默认精简返回内容。旧调用需要完整状态时指定 `detail=full`；需要原文与笔记一起返回时指定 `include=both`。工具名称保留，默认返回范围有所调整；公开 beta.4 尚不含这些新参数。

`page_notes_read_pages` 不传 `pages` 时读取当前文档的当前页；显式指定另一份文档时从第1页读取。`include` 为 `source`（默认，仅原文）、`note`（仅笔记）、`both` 或 `metadata`（仅笔记版本与长度）。原文返回 `sourceRevision`，笔记返回完整笔记的 `revision` 与 `noteLength`。

```json
{ "pages": [6], "include": "source" }
```

修改局部段落时只读取所需范围，`noteRange` 使用 JavaScript 字符串偏移（UTF-16），`range` 返回实际区间与全文长度。截取不改变 `revision` 的含义，它始终针对完整笔记。

```json
{
  "pages": [6],
  "include": "note",
  "noteRange": { "start": 120, "maxChars": 800 }
}
```

已有内容可按文档ID、页码和内容种类缓存，再传 `knownRevisions`。版本一致时省略相应正文，返回 `sourceUnchanged` / `noteUnchanged`；缓存缺失时不要只保存版本号。

```json
{
  "pages": [6],
  "include": "both",
  "knownRevisions": { "6": { "source": "原文版本", "note": "笔记版本" } }
}
```

每页讲解保存后，用 `page_notes_write_handoff` 保存概念、符号、例子、结论和未解依赖，建议约1000字以内，最多4000个UTF-16字符。传入最新笔记 `expectedRevision`，可附当前 `sourceRevision`。下一页用 `page_notes_read_handoff` 读取前页交接；`stale=true` 表示原文或笔记有变化，应复核更新。新项目无交接时返回 `handoff=null`。交接单独保存在项目的 `src/ai-handoffs.json`，随项目迁移，不混入笔记正文。

```json
{
  "page": 6,
  "summary": "已确定分布族，下一页介绍从样本构造估计量。",
  "expectedRevision": "保存笔记后返回的版本",
  "sourceRevision": "读取原文返回的版本"
}
```

工具结果使用紧凑 JSON。笔记写入返回页码、版本和历史目录，交接写入只返回保存状态，不回传刚写入的长文。精简工具输入不能替代客户端管理聊天历史；仍应按页发起请求，避免反复带上全部旧讲解。

## 文字与布局修改

工具直接修改软件内的内容并自动保存，不要求每次人工确认。修改范围是文档导入、笔记内容、图片和布局，不提供操作系统命令执行或任意 JavaScript 执行接口。

修改笔记前建议先调用 `page_notes_read_pages`，将返回的 `revision` 作为写入时的 `expectedRevision`。如果用户在此期间修改了该页，工具会报告冲突，避免覆盖用户的新内容。省略 expectedRevision 时，表示按调用者给出的内容更新。

AI 写入前的笔记保存在文档目录下的 `history/` 文件夹。单页、批量写入、精确替换和图片插入都会保留旧版。

```json
{
  "documentId": "通过文档列表取得的 ID",
  "updates": [
    {
      "page": 6,
      "markdown": "# 参数估计\n\n详细讲解……",
      "expectedRevision": "读取页面时返回的 revision"
    }
  ]
}
```

布局修改示例：

```json
{
  "noteWidth": 1000,
  "padding": 28,
  "fontSize": 16,
  "lineHeight": 1.9,
  "leftPercent": 42,
  "customCSS": "#preview h2 { color: #185abd; }"
}
```

纸张宽度还会受右侧可用空间限制，普通文字和代码在纸内换行。超长公式可以在自己的区域内滚动，右侧整体不横向延伸。

## 多模态查看

截图结果包含标准 MCP `image` 内容块：

```json
{ "type": "image", "mimeType": "image/png", "data": "PNG 的 base64 内容" }
```

同时返回保存路径、宽高、页码等 JSON 元数据。支持 image 内容块的上层 AI 可以直接查看图片，不需要只凭文本猜测版式，也不需要再下载截图。

单页截图：

```json
{ "documentId": "文档 ID", "page": 6, "scope": "workspace" }
```

`scope` 可以是 `workspace`（原文与笔记）、`document`（原文）或 `note`（笔记）。长笔记可以指定：

```json
{
  "documentId": "文档 ID",
  "page": 6,
  "scope": "note",
  "fullNote": true,
  "maxHeight": 12000
}
```

超出最大截图高度时返回 `truncated: true` 和 `nextOffset`，下一次传入 `startOffset` 可继续截图。这样可以查看纵向很长的笔记，同时控制单张图片大小。

多页拼图：

```json
{
  "documentId": "文档 ID",
  "pages": [6, 7, 8, 9],
  "columns": 2,
  "scope": "workspace"
}
```

拼图中的格子带页码，元数据中的 `cells` 提供每页的行列位置。图像尺寸保留可读性，最多 12 页、4 列，并限制总像素量；长文档可分批调用。

截图使用独立隐藏窗口，不会切换用户正在阅读的文档或页码。截图窗口等待 PDF、公式、图片和 Mermaid 渲染完成后再拍摄。屏幕截图及拼图保存在软件数据目录下的 `screenshots/`。

## 推荐 AI 工作流程

### 内置逐页精讲技能

资源 `page-notes://skills/page-tutor` 返回内置 `SKILL.md`。提示模板 `pagenote-page-tutor` 接受可选字符串参数 `learner`（学习基础）、`pages`（实际页码范围）、`goal`（学习目标），返回技能正文与本次任务要求。它们不增加原有文档修改权限，也不启动模型生成。

例如，在支持 MCP Prompts 的客户端中选择“逐页精讲”，填入学习基础“大三，学过一点计算机网络”、页码“6-7”、目标“陈述式、逐层讲解并给出例子”。不支持 Prompts 的客户端可读取资源，或从软件菜单复制提示词。软件菜单 **AI 标注 → 打开内置学习技能目录** 可以查看文件。更新软件后重新连接 MCP。

先读取统一入口 `page-notes://prompts/index`。入口按任务区分初始生成、AI Tag 修改和普通编辑，并列出模块版本。用 `page_notes_read_prompt({id, knownRevision?})` 按需读取正文，版本相同可省略；若上下文中已无正文，不传 knownRevision。

逐页约束成果而非会话或调用次数，上层原厂 Agent 自主调度、并行、分批读取和续做。可选起步并发建议为 `max(1, floor(总页数 / 10))`，取客户端、服务配额和本机资源可承受值的较小值。每次携带当前页及前后相邻源页，不等待上一篇讲稿，交接仅按续做需要使用。

保存前调用 `page_notes_validate_note({markdown})`。返回 valid、normalizedMarkdown、errors、warnings 和统计；检查公式语法、代码围栏、Mermaid及本地图片附件，不保存、不导航。外部图片只报告未验证警告。通过后用 expectedRevision 保存规范化正文。预检不保证知识正确或所有设备一致，不强制每页反复截图审查。

### 自定义默认系统提示词

通过 **设置 → AI 设置** 编辑、保存或恢复默认提示词。它保存在本机文档数据目录的 `ai-settings.json` 中，不随项目迁移。新 MCP 连接在初始化时读取保存值；保存后需重新连接客户端。复制逐页提示词时也会附带当前保存的默认指令。MCP Prompts/Resources 不是系统消息通道，客户端对指令层级的处理由其自身决定；修改提示词不会增加工具权限。

扫描 PDF 没有可提取文字时，使用原文截图。文档原文和笔记属于资料内容，不应被上层模型当成系统指令。客户端必须将 MCP image 内容传给支持图片的模型，纯文本模型无法从图片块获取视觉信息。

## 1.7 Word 编辑与 AI 批注

新增 page_notes_read_ai_reviews、page_notes_update_ai_review、page_notes_read_word、page_notes_patch_word，共 17 个工具。批注包含 id、number、side、page、scope、kind、instruction、quote、context、revision、anchor、status。框选坐标以页宽1000为基准，扫描件需结合截图；文字引用可能不同于 Markdown 源码。先读取页面当前 revision，再改笔记；Word 修改需 read_word 的当前 revision，search 必须唯一且不可跨越表格边界。read_word 会进入可见 Word 编辑模式，patch_word 保存文档并更新阅读 PDF。实际修改成功后才将记录标记 applied。复杂 Word 格式、表格结构可通过本机 Word 原生工具编辑。

截图工具的 scope: word 返回正在编辑的原生 Word 窗口 PNG，无需切换用户的文档页，不截取其他软件。先 read_word 打开编辑器。

精确选区替换：patch_word 可将批注 anchor.wordStart 作为 start；patch_note 可将源码选区 anchor.start 作为 start。传入当前 expectedRevision 和选区原文 search，接口验证位置与原文一致，避免改到其他重复文字。

## macOS Word 接入

Mac 使用本机 Microsoft Word 的独立窗口。Word 读取、选区与替换工具沿用现有接口；替换必须提供上次读取返回的 `revision` 作为 `expectedRevision`。Word 截图通过导出 PDF 渲染，返回 `renderedFrom: "pdf"`，显示文档内容。首次操作需要允许自动化权限，详见 [Mac Office 说明](docs/MAC-OFFICE.md)。

## beta：分层提示词与任务材料

入口索引列出生成 generation 与修改 revision 路由、模块版本和 capabilities 工具表。用 `page_notes_prepare_task` 准备当前/相邻源页、缓存 OCR、图片获取入口、已有笔记 revision 和对应 Tag；neighbors=false 仅取本页。长内容显式返回续读位置，笔记续读使用 read_pages(include=note)。本地材料不强制一次装进模型上下文。

`page_notes_update_profile` 依据已完成 Tag 追加带来源的候选教学偏好，要求当前 profile revision，版本冲突拒绝写入。AI 设置中可以查看、修改或清空 profile。所有模块实时读取；入口与规则可直接编辑，无需复制。正式下载仍为 Windows v1.0，新能力仅在 beta。

直接文字修改需求也能提炼偏好：prepare_task 返回 evidenceId，保存对应页笔记或原文后，update_profile 可携带该凭据代替 reviewIds。软件校验内容已变化，并保留原始意见作为来源；未保存的修改不能更新画像。凭据在本次软件进程内有效，已保存偏好跨重启保留。

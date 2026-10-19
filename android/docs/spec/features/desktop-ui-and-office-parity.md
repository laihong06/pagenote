# Android 桌面功能区与 Office 编辑对齐

> 状态：桌面菜单分组与自适应侧栏已实现；全量用户功能移植仍在进行。本文件同时是 Android Office 编辑与桌面功能对齐契约。实现计划见 `../tasks.md`。
> 最低系统为 Android 14/API 34；API 34 及以上功能一致。常规设备验收可用单一 API 34 平板 AVD 代理小米平板 5 Pro。

## 1. 目标与边界

Android 端须让熟悉 pagenote 桌面版的用户识别相同的菜单分组、命令语义、颜色和阅读/笔记工作区；交互改为 Compose 原生、触控与手写笔优先。移植全部桌面用户可见功能，不复刻桌面宿主运行时。

Android 支持 PDF、Word、PowerPoint 和 Markdown 文件。桌面 MCP 学习工作流由端内 Agent 提供；Windows COM、macOS AppleScript 与桌面 MCP transport 不跨平台搬运。

不把电子表格、账号体系、云协作或远程文档转换服务纳入此 SPEC；它们不是当前桌面导入格式/本机流程的等价移植目标。

## 2. 响应式导航

使用 Compose Material 3 Window Size Class 决定布局，不只根据设备品牌或方向判断：

- Compact（手机）：应用栏放文档标题、当前页和菜单按钮；菜单以模态抽屉打开，按桌面类别呈现命令。抽屉关闭后回到原文/笔记，不重建项目状态。
- Expanded（平板/大窗口）：显示常驻但可折叠的侧栏。展开态显示分组标题和命令；收起态显示图标导航，图标有 TalkBack 标签和 tooltip，点击后展示对应命令面板。
- Medium：在可用宽度允许双栏时用可折叠侧栏；空间不足时使用抽屉。根据窗口宽度和两栏最小可读宽度决定，不硬编码“平板”设备名单。
- 阅读主体：宽窗口中原文在左、逐页笔记在右并可调分隔；紧凑窗口提供原文/笔记切换或纵向堆叠。翻页继续保持同页同步，侧栏不得覆盖/重置 PDF 缩放、滚动或笔迹草稿。
- 进入全屏 Office 编辑时仍保留 pagenote 返回/保存流程；可将编辑 surface 扩展至全屏，但退出后回到原项目、页码和双栏状态。

### 桌面分组映射

| 桌面分组 | Android 命令面板 |
| --- | --- |
| 文件 | 打开/新建项目、最近文档、附件、导入/导出笔记、历史版本 |
| AI 批注 | 创建/查看批注、整体或选区需求、调用端内 Agent、处理结果 |
| 编辑 | 撤销、重做、剪切、复制、全选 |
| 段落 | 字号、标题级别、无序/有序/任务列表、引用 |
| 格式 | 粗体、斜体、删除线、下划线 |
| 插入 | 代码、公式、表格、链接、图片、Markdown 格式粘贴 |
| 阅读 | 页码输入与跳转、上一页/下一页、缩放、适合窗口、重置视口 |
| 手写批注 | 选择、笔、荧光笔、橡皮、线/箭头/形状/文字、颜色/宽度、撤销/重做、复制/粘贴/图片/删除 |
| Word 编辑 | 本地打开/编辑 Word 与 PowerPoint 文件，保存回原格式；AI 选区批注接入可用文本锚点 |
| 设置 | 外观、Markdown 主题、语言、笔记分页动画、双栏比例、模型设置 |
| 使用说明 | 当前版本帮助、快捷键、隐私/许可证与诊断入口 |

菜单保留桌面分组标签及组内命令顺序。不可用命令应显示禁用态和原因；不得因“移动端简化”而隐藏桌面已有功能。高频翻页、缩放、笔迹工具可在内容区保留快捷入口，但仍需能从同名分组找到。

## 3. 视觉基线

以 `src/desktop.css` 和 `src/theme.css` 为单一视觉参照：

| Token | Light | Dark |
| --- | --- | --- |
| Accent | `#185abd` | `#8bb8ff` |
| Page | `#eef1f5` | `#17191d` |
| Surface | `#ffffff` | `#22252b` |
| Raised surface | `#f5f8fd` | `#2a2e36` |
| Text | `#253247` | `#e7eaf0` |
| Muted text | 桌面 `#536276` 系 | 桌面 `#aab4c3` 系 |

Android 使用原生 sans-serif 字体 fallback；Segoe UI/Microsoft YaHei 只作桌面字形参考，不打包私有字体。保持桌面蓝色焦点/选中语义、轻分隔线、紧凑信息密度和克制圆角；触控目标不低于 Android 无障碍建议尺寸。深浅主题、focus、disabled、pressed、错误和保存状态都须有独立可读对比度。文档像素不随 UI 主题反色。

## 4. Office 编辑要求

### 4.1 用户可见行为

- 支持桌面已有的 `.doc`、`.docx`、`.ppt`、`.pptx` 输入；保持源文件格式，源文件仍是项目权威数据。
- 阅读模式以 PDF 页/幻灯片预览表现，并将页数与右侧逐页笔记对齐。转换只在本机完成，不上传文档。
- 编辑在 pagenote 进程/Activity 内完成，不启动外部 Office 应用。返回时可保存、放弃或取消；保存成功必须重新生成预览。
- 有原生文字选区的引擎能力时，允许“选中文字 → AI 批注”；否则仅对真实支持的文字提供选区入口，图像页/幻灯片仍可区域框选和 OCR，不能创建伪造文字锚点。
- 若格式、字体或复杂版式存在引擎限制，编辑前显示明确兼容提示；保存不得悄悄降为 PDF/图片或覆盖原文件。

### 4.2 引擎边界与候选

`OfficeEditorAdapter` 隔离引擎、SAF 与 pagenote 状态。优先验证 Collabora Office Android / LibreOfficeKit：官方构建说明显示其 Android native 部分需在 Linux 构建，源代码位于 online monorepo，因此它是本地开源候选但不是可直接添加的轻量 Maven widget。ONLYOFFICE 官方 Android mobile integration 需要 Document Server URL，不能满足离线本地要求。当前没有声称候选已完成嵌入；工程任务必须验证实际可集成性并继续推进完整移植，不得把候选研究当作交付完成。

引擎验证是实现任务的一部分，不是询问后即可停止的门槛。至少证明本地 Activity/View 生命周期、离线编辑保存、SAF/临时文件桥接、源格式保持、预览刷新、许可证和目标设备可运行；如果候选不适合嵌入，继续评估本地开源引擎或在 Android 工程中做可维护适配，不得回退为外部 app/云服务器或留下禁用菜单项。

### 4.3 数据与保存事务

- 项目 `manifest.left` 可指向桌面已支持的 Office 源文件；Android 需要扩大现有 PDF/Markdown extension allowlist，并补齐 project schema 示例/夹具。
- `src/` 中保留原始 Office 文件。PDF 页面、缩略图和 OCR/页面准备结果均为可丢弃派生缓存；缓存键至少包含源 SHA-256、格式、转换引擎和版本。
- 编辑会话使用临时副本。保存前比较启动时的源 revision；源已被外部修改时提示重载/另存/取消，不覆盖新内容。支持原子替换的 SAF Provider 上原子写回；不支持时先保留带明确名称的恢复副本并在 UI 中说明。
- 保存成功后重新计算源 revision、PDF 页数与缓存键；旧预览和旧 OCR 结果不得继续被 Agent 当作当前内容。笔记、笔迹、AI 批注不得静默删除；页数变化时将越界数据保留并提示用户处理。
- 进程被系统回收、空间不足、权限撤销或转换失败时，项目源文件仍保持最后一次已保存版本；恢复副本与失败状态可查询。

## 5. EARS 验收

- When 用户在手机点击菜单按钮，the app shall 打开可滚动的模态抽屉，显示桌面同名菜单组，并在返回键/选取命令后按规则关闭。
- When 用户在平板收起或展开侧栏，the app shall 保持所选分组、当前项目/页码、滚动/缩放和未保存编辑状态。
- When 用户由手机切换到平板窗口或旋转设备，the app shall 从抽屉切换为侧栏并保持当前操作不中断。
- When 某菜单命令依赖当前编辑模式或文件类型时，the app shall 显示正确启用状态；禁用时能访问原因和相应替代动作。
- When 用户打开 `.doc/.docx/.ppt/.pptx` 项目时，the app shall 在离线状态生成可读预览，保留原文件并对齐分页笔记。
- When 用户保存 Office 文档时，the app shall revision-check、写回源格式、刷新 PDF/OCR 派生缓存并保持用户笔记/笔迹/批注。
- When Office 保存、SAF 权限或格式处理失败时，the app shall 保留最后一次有效源文件及可恢复副本，并明确报告失败，绝不提示“已保存”。
- When Office 编辑器没有可靠的文本选区或页锚点时，the app shall 不创建假的 AI 批注定位数据。
- When Android 无网时，Office 阅读和编辑、Markdown/Mermaid 预览、离线 OCR 均不得尝试访问远程 renderer 或 Office server。

## 6. 验证矩阵

### Compose / UI

- Android 14 平板：横屏双栏、侧栏展开/收起、可拖动分隔线、菜单命令和阅读/笔记状态保留。
- API 34 及以上不得因系统版本变化隐藏命令或降低能力；兼容路径可以不同，但用户结果相同。
- 桌面功能清单按 §2 表格逐项追踪到 Android command/action；终态不留下“未接线”占位命令。

### Office

- Fixture 覆盖 `.doc`/`.docx`/`.ppt`/`.pptx`、多页/多幻灯片、常见字体、表格、图片、公式、页眉页脚和大文件。
- 飞行模式执行导入 → 阅读 → 编辑一处文字/对象 → 保存 → 关闭 → 重开；断言源格式、内容、revision、预览和页码关系。
- 验证未保存退出、外部修改冲突、SAF 只读/权限撤回、Provider 不支持 rename、磁盘空间不足和引擎崩溃恢复。
- 目标设备构建以 arm64 APK 为主；发布所需 ABI/page-size 合规按仓库发布政策单独处理，不改变 Android 14 用户功能。

## 7. 关联

- 菜单导航实施契约：`adaptive-menu-navigation.md`；任务与逐项验证：`../adaptive-menu-navigation-plan.md`。
- 页码输入与 Android 跳转验收：`page-jump.md`、`page-jump-plan.md`。
- 父规格：`../requirements.md`、`../design.md`、`../tasks.md`。
- 当前功能契约：`reader-shell.md`、`reader-viewport.md`、`pen-canvas.md`、`note-rich-render.md`、`openai-model-settings.md`。
- 桌面实现参照：仓库根目录 `index.html`、`src/main.js`、`src/theme.css`、`src/desktop.css`、`desktop/word.cjs`、`desktop/mac-office.cjs`。
- Collabora：[Android build guide](https://github.com/CollaboraOnline/CollaboraOnline.github.io/blob/master/content/post/build-code-android.md)、[LibreOfficeKit API](https://docs.libreoffice.org/libreofficekit.html)。
- ONLYOFFICE：[Android mobile integration](https://github.com/ONLYOFFICE/api.onlyoffice.com/blob/master/site/docs/docs-api/get-started/how-it-works/mobile-integration/mobile-integration-on-android-devices.md)。

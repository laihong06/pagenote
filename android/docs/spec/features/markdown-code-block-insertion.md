# Android Markdown 代码块插入

## 范围

为 Android 可写页笔记接通 Insert 菜单的 Code block 命令。Android 使用 Markdown 源码编辑器，按桌面 `setupMarkdownTools` 的源码模式行为插入 Python fenced code block；不模拟桌面富文本 DOM，也不引入第三方编辑器或依赖。

## 行为

- 命令仅在当前项目有可写 Markdown 编辑器且应用不忙时启用；无项目、只读、预览、不可编辑或忙碌时不修改笔记。
- 桌面源码模式的 opening/closing 文本固定为 `\n```python\n` 与 `\n```\n`。非空选区内容原样置于围栏中；不转义内容内已有反引号，不改语言标记。
- 选区折叠时插入本地化占位文本（英文 `Code`，简体中文 `代码`），并选中占位文本；非空选区时保留其内容并在结果中继续选中代码正文。
- 选区反向时规范化，越界时夹取到 Markdown UTF-16 边界。替换只影响当前选区，结果选区准确覆盖 fenced block 内部代码正文。
- 通过原生 `Editable.replace` 更新编辑器；现有 watcher 更新当前页草稿。命令不自动保存、不改其它页，保存仍走现有 revision 检查。
- 预览复用当前本地 Markdown renderer，代码块离线显示并使用现有语言高亮规则。

## 非目标

- 本切片不提供语言选择、自动识别语言、围栏碰撞转义、源码格式化或代码运行。
- 不启用公式、表格、链接、粘贴 Markdown 等其它 Insert 命令。

## 验收（EARS）

- When 用户在可写笔记中选择 Code block，Android shall 按桌面源码模式围栏替换当前选区，保留正文并选中围栏内代码。
- When 用户在无选区位置插入 Code block，Android shall 插入 `Code`/`代码` 占位代码块并选中占位文本。
- When 选区反向、越界、含 emoji 或跨多行时，Android shall 规范化/夹取 UTF-16 范围，保留选中源码并返回准确的正文选区。
- When 命令发生在预览、只读、无项目或忙碌状态时，Android shall 保持 Markdown 不变并禁用菜单命令。
- When 用户保存并预览当前笔记时，Android shall 仅保存当前页，且通过现有离线 renderer 显示 fenced code block。

## 验证范围

- JVM：覆盖桌面围栏精确文本、选区正文保留、多行/空格/emoji、折叠选区占位词、反向/越界范围和内部选区偏移。
- Android 14/API 34 平板 AVD：一项聚焦 Reader 仪器流程，验证命令门控、选中文本插入、草稿、页级保存、预览和另一页不变；不启动外部 UI。
- 构建：新增 helper 聚焦 JVM 测试、该单项 API 34 AVD 仪器测试、`:app:assembleDebug`。
- 不运行其它 API、OEM、设备、页面大小或性能矩阵；小米平板 5 Pro 实机验收仍归父 SPEC task 12。

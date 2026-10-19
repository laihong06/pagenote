# Android Markdown 公式插入

## 范围

为 Android 可写页笔记接通 Insert 菜单的 Formula 命令。Android 使用 Markdown 源码编辑器，严格沿用桌面 `setupMarkdownTools` 的源码模式块公式模板；不模拟桌面富文本 DOM，也不新增编辑器依赖。

## 行为

- 命令仅在当前项目有可写 Markdown 编辑器且应用不忙时启用；无项目、只读、预览、不可编辑或忙碌时不修改笔记。
- 插入格式固定为 `\n\n$$\n` + 正文 + `\n$$\n\n`。非空选区原样作为公式正文；不转义或改写 LaTeX。
- 选区折叠时使用桌面占位公式 `E=mc^2`，并在结果中选中正文；非空选区保留并继续选中其公式正文。
- 选区反向时规范化，越界时夹取到 Markdown UTF-16 边界。替换仅影响选区，选区偏移覆盖公式正文而非分隔符。
- 通过原生 `Editable.replace` 更新编辑器；现有 watcher 更新当前页草稿。命令不自动保存、不改其它页，保存仍走现有 revision 检查。
- 预览复用当前离线 LaTeX renderer；块公式标记不应作为可见 Markdown 源文本出现。

## 非目标

- 不提供行内 `$...$` 公式、LaTeX 模板选择、公式编辑器或 TeX 源码校验。
- 不启用 Insert 菜单中的表格、链接或粘贴 Markdown 命令。

## 验收（EARS）

- When 用户在可写笔记中对选区执行 Formula，Android shall 使用桌面源码模式 `$$` 块公式包裹选区并继续选中公式正文。
- When 用户在无选区位置执行 Formula，Android shall 插入并选中 `E=mc^2` 占位公式。
- When 选区反向、越界或含补充平面字符时，Android shall 规范化/夹取 UTF-16 范围、保留选中源码并返回准确的公式正文选区。
- When 命令发生在预览、只读、无项目或忙碌状态时，Android shall 保持 Markdown 不变并禁用菜单命令。
- When 用户保存并预览当前笔记时，Android shall 仅保存当前页，并通过现有离线 renderer 显示块公式。

## 验证范围

- JVM：覆盖精确块公式模板、正文保留与选区、空选区占位公式、emoji/多行内容、反向/越界范围及周围文本不变。
- Android 14/API 34 平板 AVD：一项聚焦 Reader 仪器流程，验证菜单门控、选区公式插入、草稿、页级保存与公式预览；不启动外部 UI。
- 构建：新增 helper 聚焦 JVM 测试、该单项 API 34 平板 AVD 仪器测试、`:app:assembleDebug`。
- 不运行其它 API、OEM、设备、页面大小或性能矩阵；AVD 仅为平台代理，小米平板 5 Pro 真机验收仍归父 SPEC task 12。

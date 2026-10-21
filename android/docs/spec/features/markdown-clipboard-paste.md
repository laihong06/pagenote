# Android Markdown 剪贴板粘贴

## 范围

定义 Android Markdown 页笔记菜单中的 `Insert > Paste as Markdown`。纯文本基线已实现；富 HTML 转换扩展由 `markdown-rich-clipboard-paste.md` 定义。

命令仅在用户主动点击后读取系统剪贴板第一项，优先将 HTML 转成 Markdown，其次使用 plain text。图片 URI 不读取，HTML 图片只保留 alt 文本。本切片不自动保存，也不改变项目格式。

## 行为

- 仅当当前页存在可写 Markdown 编辑器且应用不忙时启用；无项目、只读、预览或编辑锁定时禁用。
- 只有用户显式点击菜单命令时才读取 `ClipboardManager.primaryClip`。
- HTML 存在且转换结果非空时，按富 HTML SPEC 转换；否则将非空纯文本原样插入，不做 Markdown 重写。
- 插入后光标落在新文本之后；通过现有 `Editable` watcher 更新当前页草稿，不自动保存。
- 剪贴板缺失、无项目项、或 HTML 与纯文本均无可用内容时，显示本地化提示且不改变笔记草稿。
- 当前页手动保存继续走既有 revision 冲突保护，其他页保持不变。

## 验收（EARS）

- When 用户在可写 Markdown 笔记中显式选择 `Paste as Markdown` 且剪贴板首项含可转换 HTML 时，Android shall 优先将 HTML 转为 Markdown 并插入/替换选区。
- When 剪贴板 HTML 不存在或无有效转换内容且首项含非空纯文本时，Android shall 用该文本替换当前选区或插入到光标处，并将光标移至插入文本末尾。
- When 用户处于预览、只读、无项目或应用忙碌状态时，Android shall 禁用该命令且不读取剪贴板或修改笔记。
- When 用户显式选择该命令但剪贴板无可用纯文本时，Android shall 显示本地化空剪贴板提示且保持笔记不变。
- When 用户保存粘贴后的草稿时，Android shall 仅保存当前页并保留现有 revision 冲突保护。

## 验证范围

- Android 14/API 34 平板 AVD：一个 MainActivity 仪器用例覆盖空剪贴板、选区替换、折叠光标插入、光标位置和两页保存。
- 构建：受影响 app JVM 测试、单项 API 34 平板 AVD 仪器测试及 `:app:assembleDebug`。
- 小米平板 5 Pro 综合实机验收仍归父 SPEC task 12。

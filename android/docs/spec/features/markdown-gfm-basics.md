# Android GFM 基础渲染

## Scope

为 Android Markdown 预览补齐桌面笔记已有的 GitHub Flavored Markdown 删除线与任务列表基础语法，并确保源码编辑区保留可编辑的 Markdown 标记。继续使用项目现有 Markwon 4.6.2，不引入 WebView 或自定义 Markdown 解析器。

## Behavior

- `~~text~~` 在只读预览中显示为删除线；源码编辑区仍保留原有 Markdown 标记。
- `- [ ] item`、`- [x] item`、`- [X] item` 在只读预览中显示为未完成/已完成任务项；大小写语义与 GFM 一致。
- 任务列表只读渲染状态由 Markdown 源文决定。点击复选框不修改状态或保存项目；用户通过编辑源文中的标记更改状态。
- 既有表格、公式、代码高亮、图片和 Mermaid 插件行为保持不变。

## Acceptance (EARS)

- When Android 预览包含 `~~text~~` 的 Markdown 时，the app shall 将目标文本表示为删除线，并允许源码编辑器继续编辑原标记。
- When Android 预览未完成或已完成的 GFM 任务项时，the app shall 以对应勾选状态显示列表项，并将 `x` 与 `X` 视为已完成。
- When 用户在源码编辑区编辑 GFM 文档时，the app shall 保留删除线与任务项标记，不把源码转换成预览文本。
- When 用户点按预览中的任务复选框时，the app shall 不改变 Markdown 源文或项目状态。
- When 编辑或预览文档同时包含既有富内容时，the app shall 保持表格、公式、代码、图片及 Mermaid 的现有渲染行为。

## Verification

- 一个 Android 14/API 34 定向仪器测试验证预览删除线 span、未勾选/`x`/`X` 三种任务状态及编辑器源码标记保留。
- 运行 Android notes 模块 JVM 测试、该单项仪器测试、Debug APK 构建；不运行其他 API、机型或性能矩阵。

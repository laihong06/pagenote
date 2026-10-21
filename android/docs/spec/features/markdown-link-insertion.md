# Android Markdown 链接插入

## 范围

为 Android 可写页笔记接通 Insert > Link。Android 使用 Markdown 源码编辑器，行为复用桌面源码模式的 `[链接文字](https://example.com)` 模板和标签文本选中语义；预览使用现有 Markwon 链接渲染。

## 行为

- 命令仅在可写 Markdown 编辑器可用且应用不忙时启用；无项目、只读、预览或忙碌时不修改笔记。
- 非空选区的原文作为链接标签，插入 `[<原文>](https://example.com)`，并选中 `<原文>`，以便用户继续直接改标签。
- 折叠选区时以本地化占位词 `链接文字` / `Link text` 填充标签，并选中占位词。
- 固定目标 URL 为桌面源码模式现有的 `https://example.com`；不弹出配置对话框。
- 选区反向时规范化，越界时夹取到 Markdown UTF-16 边界。替换只影响选区，标签前后其它笔记保持不变。
- 通过原生 `Editable.replace` 更新编辑器；现有 watcher 更新当前页草稿。命令不自动保存、不改其它页，保存仍走现有 revision 检查。
- 预览继续使用现有离线 Markdown renderer，不另造链接 span 或网络加载流程。

## 非目标

- 不新增 URL 输入对话框、链接编辑器或 URL 校验/编码。
- 不对用户选中的标签转义 Markdown；保留桌面源码模式的原样包裹行为。
- 不允许网页导航或网络加载作为插入命令的副作用。

## 验收（EARS）

- When 用户在可写笔记中执行 Link 且选中非空文本时，Android shall 使用原文替换选区并生成桌面一致的 Markdown 链接，同时只选中链接标签。
- When 用户在折叠选区处执行 Link 时，Android shall 插入固定 URL 与本地化标签占位词，并选中占位词。
- When 选区反向或越界时，Android shall 规范化/夹取 UTF-16 范围，并保留选区之外的笔记文本。
- When 命令发生在预览、只读、无项目或忙碌状态时，Android shall 禁用命令且保持 Markdown 不变。
- When 用户保存并预览当前笔记时，Android shall 仅保存当前页，且现有 renderer 将生成内容识别为 Markdown 链接。

## 验证范围

- JVM：覆盖固定 URL/模板、选区替换、折叠选区本地化占位词、反向/越界 UTF-16 范围、标签选中区与周围文本保持。
- Android 14/API 34 平板 AVD：一项聚焦 Reader 仪器流程验证菜单门控、反向选区替换、空选区占位词、原生选区、当前页保存及预览链接 span；不启动外部 UI。
- 构建：新增 helper 聚焦 JVM 测试、该单项 API 34 AVD 仪器测试、`:app:assembleDebug`。
- 不运行其它 API、OEM、设备、页面大小或性能矩阵；AVD 仅为平台代理，小米平板 5 Pro 真机验收仍归父 SPEC task 12。

# Markdown 项目图片插入

## 范围

为 Android 可写页笔记接通桌面“插入图片”命令：通过 Android 文档选择器选取本机图片，复制到当前项目的 `src/assets/`，并在当前编辑光标/选区插入可离线预览的 Markdown 图片引用。项目资源 URI 持续使用 SAF，不把来源 URI 或绝对路径写入笔记。

只处理桌面附件上传当前接受、且 Android 现有 `BitmapFactory` renderer 能解码的 PNG/JPG/JPEG/GIF/WebP。SVG 与剪贴板图像粘贴不属于本切片；导入时不得把当前 renderer 无法解码的输入伪装为成功。

## 行为

- 图片命令只在当前项目存在可写 Markdown 编辑器且应用不忙时启用。选择器使用 `ACTION_OPEN_DOCUMENT`/`OpenDocument` 的 `image/*` 类型；用户取消时不改笔记或项目。
- 选择成功后，应用立即通过临时只读 URI 流把文件复制到应用私有暂存区，限制最大输入为桌面上传的 200 MiB、解码尺寸不超过 2400 万像素，并在创建项目资源前检查文件非空且 `BitmapFactory` 可解码。来源 URI 不持久化。
- 资源名由随机 UUID 和已验证的图片扩展名组成，写入当前项目 `src/assets/<uuid>.<ext>`；不得覆盖既有资源。缺失 `assets/` 目录时可按 SAF 能力创建，否则明确失败。
- SAF 写入成功后，在打开选择器时捕获的同一项目、同一页和未变化的笔记草稿中，以 `![<安全 alt 文本>](assets/<uuid>.<ext>)` 替换当时选区，光标落在插入文本末尾。插入经原生编辑器 watcher 更新草稿，不自动保存笔记。
- 页面/项目/笔记在选择器期间变化时，不得把资源引用插入错误位置；能删除时清理本次新建的孤立资源。任何导入失败均不得插入 Markdown 引用；部分 SAF 文件须尽力删除，若 Provider 拒绝清理则明确报告残留资源名。
- 后续预览复用现有 `NotePreviewRewrite`、`NoteAssetSchemeHandler` 和 `ContentResolver`，图片必须从本地项目资源离线显示；不引入网络读取。
- 剪贴板图片识别/粘贴、图片裁剪/压缩、SVG 渲染和资源管理/删除均为独立后续工作。

## 验收（EARS）

- When 用户在可写 Markdown 页选择一张受支持图片时，Android shall 把图片写入当前项目 `src/assets/`，并在捕获的当前页编辑选区插入项目相对 Markdown 图片引用。
- When 用户取消选择器或选择图片读取、解码、SAF 创建/写入失败时，Android shall 保持笔记草稿不变，不插入失效引用，并清理已创建的部分资源；清理失败时显示具体残留文件名。
- When 用户在选择器期间切换项目、页或修改笔记时，Android shall 拒绝把资源引用写入新的上下文，并尽可能删除本次未引用的资源。
- When 用户保存并重新打开项目时，Android shall 保留 Markdown 引用和对应资源；在断网预览时，shall 通过现有本地 renderer 解码该资源。
- When 编辑器处于无项目、只读、预览或忙碌状态时，Android shall 禁用图片命令，不启动选择器。

## 验证范围

- JVM：安全扩展名/alt 文本、项目相对引用、选区替换范围和末尾光标。
- Android 14/API 34：一项 MainActivity/SAF 聚焦仪器流程，通过测试注入驱动 `OpenDocument` 结果回调，不启动系统 UI；覆盖取消、成功导入/预览/保存以及一次失败清理。
- 构建：受影响 app JVM 测试、该单项 API 34 仪器测试、`:app:assembleDebug`。
- 不运行其他 API、OEM、设备、页面大小或性能矩阵；小米平板 5 Pro 综合实机验收仍按父 SPEC task 12 执行。

# Android 项目附件管理

## 范围

启用 File > Attachments 命令，用于查看和维护当前 pagenote 项目的 `src/assets/`。附件始终属于项目，不复制到应用全局上传目录；读写通过项目已授权的 SAF tree URI。Android 支持图片离线预览与 Markdown 引用；桌面带来的其他附件作为项目资源保留，不在此功能中打开或编辑。

## 行为

- 有项目时可浏览 `src/assets/` 下的普通文件；缺少目录视为空列表。无项目时使用现有菜单门控。
- 通过 Android 文档选择器导入桌面附件协议允许的图片、PDF、Markdown、TXT、MP3、MP4、DOCX、PPTX。保存为 UUID 文件名并保留已校验扩展名，不覆盖已有文件；不保存原始 URI 或绝对路径。
- 从列表将图片插入当前可写笔记时，生成桌面兼容 `![alt](assets/<uuid>.<ext>)`；插入只更新当前页草稿，不自动保存。其他文件可浏览并保留为资源，但本功能不伪装成 Android 原生 Office/媒体编辑器。
- 以当前全部分页笔记和 `annotations.json` 检查资源引用。被任一页 Markdown 或 PDF 图片批注引用的资源不得删除；未引用资源删除前需二次确认。
- 选择取消、文档读取失败、SAF 创建/写入失败、上下文变化或引用校验失败时，不插入无效引用；清理部分写入失败时显示残留资源名。
- 附件文件名必须是单个安全路径段；路径解析不得接受分隔符、`.`/`..` 或任意外部 URI。文件内容与项目数据不上传网络。

## 验收（EARS）

- When 用户打开已有项目的附件命令时，Android shall 列出项目 `src/assets/` 普通文件，且不读取或修改项目外目录。
- When 用户导入允许类型的文件时，Android shall 以不冲突的项目相对资源名写入文件，并在取消/失败时保持当前笔记和已有资源不变。
- When 用户将图片插入当前可写笔记时，Android shall 插入可离线解析的桌面兼容 Markdown 引用，且不自动保存。
- When 资源仍被任一页笔记或 PDF 图片批注引用时，Android shall 拒绝删除；未引用资源只有在显式二次确认后才可删除。
- When Android 无网络时，附件列表、导入、删除和项目图片预览 shall 继续使用本地 SAF 资源，不发起网络请求。

## 验证

- JVM：安全资源名、附件类型/扩展名验证、引用扫描、被笔记或批注引用时拒绝删除。
- Android 14/API 34 平板 AVD：`ProjectAttachmentsDialogTest` 覆盖图片插入回调和删除二次确认；当前弱化验证不跑真实 SAF Picker/Provider Activity 端到端，SAF 存储操作的集成覆盖留作后续补充，不将其描述为已实测。
- 仅运行上述聚焦测试、受影响 app JVM 测试、Android 测试源码编译和 `:app:assembleDebug`。

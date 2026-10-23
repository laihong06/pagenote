# 统一项目目录（Beta 5 开发版）

适用于桌面 `1.1.0-beta.5` 开发源码及后续对应构建；已发布 Beta 4 安装包不会随源码推送自动更新。Android 测试包保持独立版本，按入口中的路径互相读取项目。

```text
课程项目/
├── project.pagenote
└── src/
    ├── source.pdf          # 左侧源文档：也可以是 md/docx/pptx 等
    ├── notes.md            # 右侧原始 Markdown，包含逐页分隔符
    ├── assets/             # 笔记和原文引用的托管附件
    ├── annotations.json    # 有手写等批注时保存
    ├── ai-reviews.json     # 有 AI Tag 时保存
    └── ai-handoffs.json    # 有页间交接时保存
```

Office 文档可另有 `src/view.pdf` 转换预览，`src/notes.json` 是兼容性副本；它们都不替代源文档及 `notes.md`。打开项目时从入口链接读取原文和 Markdown，应用缓存可以重新生成。

## 创建与导入

- “新建项目”创建项目目录、入口和 `src/source.md`、`src/notes.md`。可在新建窗口指定父目录。重名时提示错误，不覆盖已有目录。
- 未打开项目时直接导入文件，会自动在应用数据目录的 `documents-data/projects/`（独立网页运行时为数据目录的 `projects/`）下创建一个以文档命名的项目。重名自动加序号，不覆盖其他项目。
- 已打开项目时导入左侧文件，替换该项目的左侧链接和源文件，保留右侧笔记、批注及项目 ID。原始外部文件不被修改。
- 导入或编辑右侧笔记后，内容保存回入口指定的 Markdown；使用空行包围的 `<!-- pagebreak -->` 分隔对应页面，空白页也保留。

## 项目入口

`project.pagenote` 是 JSON，最小结构示例：

```json
{
  "format": "pagenote-project",
  "version": 1,
  "id": "64位十六进制项目标识",
  "name": "课程项目",
  "created": "2026-10-23T00:00:00.000Z",
  "left": "src/source.pdf",
  "right": "src/notes.md"
}
```

示例中的 ID 是说明文字，实际 ID 由软件生成。链接使用相对路径，只允许指向项目 `src` 内的文件；左右文件不得相同。Word/PPT 项目可以另有 `preview: "src/view.pdf"`。

## 兼容与迁移

既有 `project.pagenote` 或 `project.page-notes.json` 继续按入口里的 `left/right` 链接打开，支持原来的 `src/left.*` 与 `src/right.md`，不强制重命名。旧独立文档在界面打开时复制转换为完整项目，保留缓存中的原文、笔记、历史和 ID；不批量删除旧数据。

移动到其他位置或交给别人时，复制整个项目目录，再通过“文件 → 打开项目”选择入口。复制前完成保存并退出编辑。项目中的图片等附件必须一起带走；只发送入口文件无法携带源文件和笔记。不要在两台设备上同时编辑同一份项目副本后直接相互覆盖。

## English

Desktop Beta 5 development sources use a folder containing `project.pagenote` and a `src/` directory for the source document, raw page-linked `notes.md`, assets and optional annotation/AI metadata. Links are relative. Existing manifests and `left.*` / `right.md` names remain supported. Standalone imports create a project automatically; old standalone entries are copied into projects when opened, preserving their managed history. Existing-project imports replace the left document while retaining notes. Copy the complete folder to move or share it, and reopen the manifest. Published Beta 4 binaries are unchanged.

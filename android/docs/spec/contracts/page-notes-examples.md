# 分页与 revision 示例

## splitPageNotes 语义

- 分隔符：`<!-- pagebreak -->`，允许两侧空白与换行变体。
- 空页保留占位，页号从 1 开始连续。
- 示例输入：

```md
# 第一页

<!-- pagebreak -->

# 第二页

<!-- pagebreak -->


```

- 切分结果：第 1 页为 `# 第一页`，第 2 页为 `# 第二页`，第 3 页为空字符串。
- 合并用 `\n\n<!-- pagebreak -->\n\n` 连接，保持页数不变。

## revision 语义

- revision 为整页笔记 UTF-8 的 SHA-256 hex，共 64 位小写。
- 空页 revision 为空字符串的 SHA-256。
- 写操作必须带 `expectedRevision`，不一致拒绝覆盖。

## 资源语义

- 写盘用 `assets/$name`，读显按项目 `src/assets/` 本地文件解析。
- 缺失资源显示占位，不吞页。

# 大型 Markdown 页编辑布局

## 范围

保证 Android 14 阅读器可以打开并编辑较大的逐页 Markdown 笔记，包括包含数万字符无空格 token 的行（例如内联 data URI），而不触发系统 ANR。完整笔记仍是项目数据的唯一来源；布局优化不得截断、规范化或改写保存内容。

## 行为

- 页笔记编辑器须保留完整 Markdown 正文；不得复用预览的 8000 字符截断策略。
- 长连续 token 的布局须避免在主线程执行足以阻塞输入分发的重复扫描。
- PDF 页面位图替换后的延迟回收依赖 `Choreographer`，必须从主 Looper 调度；加载协程恢复在线程池时不得把该异常误报为项目/页面打开失败。
- 编辑、选区、复制/粘贴、撤销/重做及页级 revision 均继续针对原始 Markdown 字符序列工作；任何仅用于显示的断行辅助都不得进入草稿、剪贴板、历史或项目文件。
- 冷启动重开项目、恢复页码或跳转到长笔记页时，阅读器不得出现系统 ANR 对话框；加载失败仍须以可恢复状态呈现，不得丢失笔记。
- 处理不得发起网络请求，不修改 pagenote 项目格式或 SAF 文件结构。

## 验收（EARS）

- When 页面 Markdown 含至少 39,000 个连续 ASCII 字符且用户打开编辑页时，Android shall 在系统输入分发 ANR 阈值内完成首轮布局并保持编辑器可交互。
- When 用户编辑或保存包含长连续 token 的笔记时，Android shall 保留逐字符原文及页 revision 语义，且不得把显示辅助字符写入项目、剪贴板或历史。
- When 用户冷启动后恢复或跳转到该页时，Android shall 显示完整笔记且不触发系统 ANR。
- When 页面位图需要延迟回收且调用方没有主 Looper 时，Android shall 将两帧回收调度到主 Looper，并保持位图在此之前可绘制。

## 验证

- 为原生编辑视图增加一个包含 40 KB 连续 base64 风格字符、前后带 Markdown 的聚焦 API 34 instrumentation 回归；验证创建、测量、布局和 selection 恢复均在 5 秒系统 ANR 阈值内，并断言源文本精确不变。
- 在 API 34 平板 AVD 上从“最近项目”重开 SAF 测试项目：生成 69 页 PDF，第 31 页 Markdown 约 57 KB 且含 40 KB 连续 ASCII token；恢复第 31 页 checkpoint，覆盖 SAF 项目/笔记读取、编辑器首轮布局、编辑和按页保存。此项用于提高事故规模的回归覆盖；冷进程恢复另由 task 30 验收。
- 增加一个 API 34 instrumentation 回归：从无 Looper 的 worker 请求回收位图，确认不抛异常且仍经主线程两帧后释放。
- 在唯一 API 34 平板 AVD 上，以隔离的 69 页验收副本冷启动后重开第 31 页，确认无 ANR、正文可编辑，保存回读后只有当前页的测试标记变化。实体机差异不作为交付门槛。
- 只运行该回归、受影响 app JVM 测试和 `:app:assembleDebug`；不新增其他 API、OEM 或设备矩阵。

## 证据与假设

- 2026-10-19 实机 `io.pagenote.android.debug` ANR 为输入分发超时，过滤后的首个可见 native 帧是 `minikin::getNextWordBreakForCache`。
- 隔离副本第 31 页约 57 KB，最长连续 base64 风格 token 约 39.6 KB。Android Minikin 的该辅助函数逐字符搜索下一个词边界，因此长 token 仍是候选因素，但不是已证实根因。
- 2026-10-19 将 SAF 最近项目恢复回归扩展到 69 页合成 PDF 与约 57 KB 第 31 页笔记（40 KB 连续 token）；API 34 平板 AVD 通过恢复、首轮布局、编辑及页级保存，恢复/布局断言低于 5 秒。测试未使用原用户 PDF，也未模拟系统进程死亡；小米实机 ANR 尚未复现，作为环境差异保留。
- 2026-10-19 API 34 平板 AVD 的 `NoteLongMarkdownEditorTest` 三项用例覆盖 40 KB 连续 token 编辑、页跳转与保存，以及经真实 SAF “最近项目”重开 69 页 PDF 并恢复第 31 页；恢复/布局低于 5 秒门槛，保存后只有目标笔记页变化。另一个 AVD instrumentation 回归从无 Looper 的 worker 请求位图回收，确认不抛 `Choreographer` 异常且仍延迟两帧。修复已将位图回收请求派发到主 Looper。AVD 未复现此前小米 Pad 5 Pro 冷启动 ANR；冷进程恢复仍需由 task 30 的 AVD 验收补足。
- 一次早期计时从挂载测试项目开始，混入项目/Activity 初始化，结果不作为编辑器 layout 根因证据。后续计时改为针对已打开阅读器的实际页跳转。

## 关联

- 父 SPEC：`../requirements.md`、`../design.md`、`../tasks.md`，task 30。
- 编辑器：`note-rich-render.md`、`note-overlong-preview.md`；会话恢复：`reader-session-restoration.md`。

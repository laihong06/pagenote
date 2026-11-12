# 参考应用动作历史调查

依据用户提供的运行时恢复 DEX（SHA-256 `846b574215037f6fe747779677fb848121ddf37aad465fd48f97e77fbc4d6018`）通过 JADX 静态检查；这些观察不等同于真机运行验证。为避免受单类源码截断影响，将原 DEX 作为 `classes.dex` 临时包装后，逐方法读取完整反编译结果。

- `com.newskyer.paint.core.ActionStack` 保存 `actions` 列表及 `current` 游标。`add(Action)` 在游标插入动作、游标前进并删除其后的重做分支；`undo()` 在可撤销时先将游标减一；`redo()` 在可重做时将游标加一。`add(Page, Action)` 还记录动作所属页面并标记共享动作。
- `PanelManagerExtend.undoLastAction` 取得当前动作栈，先调用其 `undo()`，再对当前动作调用 `undo(manager)`；`redo` 在游标移动前取得当前动作，再调用其 `redo(manager)`。两者还会清理选择、按动作重绘并恢复动作图层。
- `PanelManager.d0` 在普通页面返回当前页面的 `ActionManager` 动作栈；连续固定页面及关联窗口存在共享栈分支。因此不能把两个独立的笔迹与批注栈用“最后一次类型”当成参考实现。
- 原应用动作栈游标在调用具体动作前移动，静态代码不能证明异常后的恢复语义。PageNote 当前先执行动作、成功后才移动栈项，是为保留失败时的重试入口而作的适配；若动作在抛错前已部分修改状态，动作自身仍须负责回滚。

## 当前移植状态

- Android 阅读器现用 `ReaderEditHistoryOrder` 记录每页最近的编辑层，并把撤销/重做路由到现有 `PdfAnnotationEditHistory` 或 `PenPageStore`。这让图形—笔迹—图形序列按用户操作顺序撤销和重做，但不是参考 App 的动作对象 `ActionStack` 移植，也还不是一个承载所有类型动作数据的中心栈。
- 原生图形历史默认只保存非笔迹项目；落盘 JSON 数字的文本形式不同时按数值比较，避免新增笔迹或 `2.0`/`2` 格式差异清空此前图形历史。需要同时恢复笔迹和图形的 OCR 转换历史仍可显式保存整页。
- `REF-HISTORY-01`：在 Android 14 ARM64 模拟器依次创建图形、笔迹、图形；三次撤销按图形、笔迹、图形移除，三次重做按原顺序恢复（`PdfAnnotationSelectionClipboardActivityTest.interleavedShapesAndInkUndoInGestureOrder`，通过）。`ReaderEditHistoryOrderTest` 覆盖跨类型顺序、分支清除、页面隔离与页面重映射。
- 仍需处理异步保存失败时跨层历史原子回滚、容量一致性、OCR 等整页变更与旧笔迹历史对齐、同一擦除手势跨自由笔迹/原生图形的单事务，以及应用进程生命周期。区域橡皮的一次手势若同时改了两个层，目前还不能保证只需一次撤销。

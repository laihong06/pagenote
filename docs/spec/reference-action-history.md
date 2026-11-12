# 参考应用动作历史调查

依据用户提供的运行时恢复 DEX（SHA-256 `846b574215037f6fe747779677fb848121ddf37aad465fd48f97e77fbc4d6018`）通过 JADX 静态检查；这些观察不等同于真机运行验证。为避免受单类源码截断影响，将原 DEX 作为 `classes.dex` 临时包装后，逐方法读取完整反编译结果。

- `com.newskyer.paint.core.ActionStack` 保存 `actions` 列表及 `current` 游标。`add(Action)` 在游标插入动作、游标前进并删除其后的重做分支；`undo()` 在可撤销时先将游标减一；`redo()` 在可重做时将游标加一。`add(Page, Action)` 还记录动作所属页面并标记共享动作。
- `PanelManagerExtend.undoLastAction` 取得当前动作栈，先调用其 `undo()`，再对当前动作调用 `undo(manager)`；`redo` 在游标移动前取得当前动作，再调用其 `redo(manager)`。两者还会清理选择、按动作重绘并恢复动作图层。
- `PanelManager.d0` 在普通页面返回当前页面的 `ActionManager` 动作栈；连续固定页面及关联窗口存在共享栈分支。因此不能把两个独立的笔迹与批注栈用“最后一次类型”当成参考实现。
- 原应用动作栈游标在调用具体动作前移动，静态代码不能证明异常后的恢复语义。PageNote 当前先执行动作、成功后才移动栈项，是为保留失败时的重试入口而作的适配；若动作在抛错前已部分修改状态，动作自身仍须负责回滚。

PageNote 的 `PenActionStack`、笔迹和批注动作适配器仍未接入阅读器统一历史。`PdfAnnotationSelectionClipboardActivityTest.interleavedShapesAndInkUndoInGestureOrder` 是已复现失败、暂时跳过的端到端用例。尚需处理异步落盘、动作失败、无变化动作、撤销分支、容量淘汰、页面/会话生命周期以及混合笔迹与图形的单事务，才能启用它。

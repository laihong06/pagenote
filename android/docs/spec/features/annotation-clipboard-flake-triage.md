# 批注剪贴板 flake 分步排查计划（未修复，未关闭 task 12）

> 状态声明：本文仅为 `PdfAnnotationSelectionClipboardActivityTest.nativeAnnotationSelectCopyPasteMoveDeleteUndoAndRedoPreserveFreehandItems`
> 已知 flake 的排查计划文档，**不修复代码，不修改测试，不声称 task 12 已关闭**。
> 只读依据：`android/docs/spec/tasks.md` task 12、`android/docs/spec/features/task12-remaining-acceptance.md`、
> `android/app/src/androidTest/java/io/pagenote/android/PdfAnnotationSelectionClipboardActivityTest.kt`（只读，未改）。
> task 12 验收主体仍以 `tasks.md` task 12 与 `task12-remaining-acceptance.md` 为准。

## 1. flake 签名

- 失败用例：`nativeAnnotationSelectCopyPasteMoveDeleteUndoAndRedoPreserveFreehandItems`
- 文件：`android/app/src/androidTest/java/io/pagenote/android/PdfAnnotationSelectionClipboardActivityTest.kt`
- 超时点：第 193 行 `composeRule.waitUntil(10_000)`，条件为
  `reader_menu_command_copy_ink` 的 `assertIsEnabled()` 成功。
- 现象：在 `Pixel_Tablet_API34` 连续两次复现 `waitUntil(10s)` 超时。
- 对照通过用例：同文件 `endpointDwellPersistsNativeShapeAndUsesAnnotationUndoHistory` 通过。
- 前置条件已满足（第 183–186 行通过）：`pdf_ink_canvas` 宽度大于 0，
  且 `annotations.readPage("document", 1).size == 3` 已成立，说明 fixture 落盘与画布挂载不是本次超时点。

## 2. 复现场景（按测试步骤还原，不改测试）

1. 安装 fixture：`installFixture(resolver)` 写入 manifest、两页 PDF、notes。
2. 预置 `document` 第 1 页 2 条批注（`rect` + `line`）与第 2 页 1 条 untouched rect；
   预置 1 条 `originalStroke` 到 `ProjectPenStore`（DOCUMENT 第 1 页）与内存 `PenPageStore`。
3. 进入阅读器 session：第 1 页、可写笔记、可写笔迹、`PEN` 工具。
4. 等待画布就绪（15s 门）：`pdf_ink_canvas` 宽度 > 0 且第 1 页批注数为 3。
5. 点击 `reader_menu_command_select_tool` 进入选择模式。
6. 在 `pdf_ink_canvas` 上执行 `click(Offset(width * 0.25f, width * 0.10f))`，
   目标为预置 `rect [[100,100],[400,400]]` 的可命中区域。
7. 进入 flake 点：第 193 行等待 `reader_menu_command_copy_ink` 变为 enabled（10s 门）。
8. 复现时固定环境：唯一 `Pixel_Tablet_API34`，单测独立运行与整文件运行各至少记录一次，
   记录是否为“连续两次复现”之外的偶发比例（例如 2/2、2/5、1/10）。

## 3. 日志采集点

### 3.1 Compose 等待条件侧

- 在超时前后抓取 `reader_menu_command_copy_ink` 的语义状态：
  是否存在、是否可见、enabled/disabled、是否有“未选中任何批注”的禁用原因。
- 抓取 `pdf_ink_canvas` 的语义尺寸与实际点击坐标换算：
  `width * 0.25f / width * 0.10f` 落到 width-1000 坐标系后的点是否确实命中 `rect`。
- 抓取选择态：当前选中项是 `rect` / `line` / 空选 / 多选，选中索引是否为预期的第 0 项。
- 若条件允许，加一次性临时日志（排查后移除或转正式日志由实现者决定）：
  点击命中测试结果、选中态变化回调、copy 菜项 enabled 计算输入。

### 3.2 落盘回调侧

- 本次超时点不在落盘等待（`waitForAnnotations` 在第 206 行之后），但需先排除前置写脏状态：
  确认第 183–186 行通过时的第 1 页 3 条内容顺序与类型是否为 `[rect, line, pen]` 预期。
- 采集 `ProjectAnnotationStore.readPage("document", 1/2)` 在点击前后的快照，
  确认第 2 页 untouched 页未变，避免把跨页污染误判为选中失败。
- 采集 `ProjectPenStore.readPage(DOCUMENT, 1)` 是否仍为 `[originalStroke]`，
  排除笔迹 store 异常抢占选中态的干扰。
- 后续步骤若继续超时，对比 `waitForAnnotations` 的 `readPage` 轮询是否本身变慢
  （SAF fixture provider 延迟），与本次“菜单 enabled 未翻转”区分开。

### 3.3 instrumentation 输出侧

- 保留完整 `--info` 日志与 JUnit XML：`waitUntil` 超时堆栈、失败时的 semantics tree dump。
- 失败截图/录屏（如 runner 支持）：确认 `select_tool` 是否真的切到选择模式，
  以及点击后是否有选中框/高亮出现。
- 记录运行模式：单测单独跑、整文件 2 项一起跑、`feature-pen` 前后跑，分别记录通过率。
- 记录 AVD 状态：`Pixel_Tablet_API34` 冷启动 vs 热复用、动画开关、键盘/焦点抢占、并发测试。

## 4. 与通过用例 endpointDwell 的差异对照

| 维度 | 通过：`endpointDwellPersistsNativeShapeAndUsesAnnotationUndoHistory` | flake：`nativeAnnotationSelectCopyPaste...` |
| --- | --- | --- |
| 输入方式 | 原生 `dispatchStylus`（DOWN/MOVE/UP 压感笔事件直发 `PenCanvasView`） | Compose `performTouchInput click/down-move-up` 走语义节点 |
| 触发路径 | endpoint dwell 生成 `line`（`Thread.sleep(650)` + 700ms dwell） | 先点 `select_tool` 再 click 选中已有 `rect` |
| 等待语义 | `waitForAnnotations` 等 SAF 落盘出现 `line` | 第 193 行等菜单 `copy_ink` enabled（选中态驱动） |
| fixture | 空第 1 页 + 运行时写入 1 笔迹 + dwell 生成 1 line | 预置 2 批注 + 1 笔迹，第 183 行要求读到 3 条 |
| 断言重点 | `pressures` 不进 vector 序列化、undo/redo、跨页不变 | 复制/粘贴/移动/缩放/删除/undo/redo 全链路 + freehand 不变 |
| flake 假设方向 | 落盘与 overlay 刷新链路在本用例中已证明可行 | 优先怀疑“点击未命中/选中态未发布/菜单 enabled 计算滞后”，而非 SAF 落盘 |

对照结论（待验证，不是定论）：
通过用例走原生触摸 + 落盘断言；flake 用例走 Compose 点击 + 选中态菜单断言。
因此排查应先收敛点击命中与选中态发布，其次才是并发/SAF/主线程繁忙导致的 enabled 刷新延迟。

## 5. 分步排查顺序

1. 先看 semantics tree dump：超时瞬间 `copy_ink` 是 missing、disabled 还是 invisible。
2. 再看选中态：点击坐标换算后是否命中 `rect`；若命中但选中为空，查选中发布链路。
3. 再看模式切换：`select_tool` 点击后是否真正进入选择模式，有无被笔迹绘制/重组覆盖。
4. 再看时序：点击与 `waitUntil` 之间是否需要一帧 idle；对比第 306–310 行第二次 copy 等待是否同样脆弱。
5. 再看环境：单跑 vs 整文件跑、冷启动 vs 热复用、录屏开/关、前一个 dwell 用例残留 session 是否干扰。
6. 最后才扩大到落盘/SAF：`readPage` 轮询延迟、fixture provider 延迟、主线程繁忙。
7. 形成最小复现子集后，再由实现者决定是修命中逻辑、修 enabled 状态机，还是修测试时序。
   本文件不预设修复方案。

## 6. 修复后重跑门（验收口径，不在本文件执行）

- 本测试文件 2/2：`PdfAnnotationSelectionClipboardActivityTest` 两项全过。
- `feature-pen` 7/7 通过（与 `task12-remaining-acceptance.md` 第 1 条口径一致）。
- 建议附带记录 `PdfPageViewportNavigationTest` 1/1、`ReaderSettingsPagePrefetchTest` 1/1 未退化。
- 在 `Pixel_Tablet_API34` 上至少连续两次通过才认为 flake 收敛；单次通过不关闭调查。
- 不要跑构建：本文件为文档任务，不执行上述重跑；重跑由后续实现/验证步骤执行。

## 7. 非目标与边界

- 不改 Kotlin/Java/XML，不改测试断言与等待时长，不修代码。
- 不声称 task 12 已关闭；task 12 剩余项仍以 `tasks.md` task 12 为准。
- 不扩大设备矩阵：仍限定唯一 API 34 平板 AVD，不引入手机/OEM/API 35/16 KB 矩阵。
- 冷启动第 31 页 ANR 归 task 30，不在本文件调查范围内。

## 关联

- 任务：`android/docs/spec/tasks.md` task 12（验收主体）。
- 验收口径：`android/docs/spec/features/task12-remaining-acceptance.md`。
- 测试（只读）：`android/app/src/androidTest/java/io/pagenote/android/PdfAnnotationSelectionClipboardActivityTest.kt`。
- 契约参考（未改）：`android/docs/spec/features/ink-selection-clipboard.md`。

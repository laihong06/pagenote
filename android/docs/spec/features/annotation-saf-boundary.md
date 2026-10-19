# 标注 SAF 数据边界：通用读写与笔迹共存

## Scope

把 `AnnotationStore` 的 File 语义扩展到 SAF 项目：文字/区域/图形等全部批注类型可创建、列表、删除，与现有手写笔迹读写共存，互不丢数据。AI review 改写/状态流转与 handoff 交接 UI 仍在 Task 6 后半段，本片只做数据边界。

## Problem

- `ProjectPenStore.readPages` 只保留 pen/highlight（`freehandItems` 过滤），读一次丢一次其它类型。
- `ProjectPenStore.writePage` 经 `AnnotationPageMerger.replacePage` 只替换 freehand 条目，保留了其它类型——写侧是对的，但读侧先天缺。
- `AnnotationStore.writePage/deletePage` 是 File 版，SAF 项目用不上；`ReviewStore` 同理。

## Data Contract

- 新增 `ProjectAnnotationStore(resolver, sourceDirectoryUri, annotationsUri)`：`readPage(side, page): JsonArray` 返回该页全部条目（不再过滤类型），`writePage(side, page, items)` 全量替换该页（先校验 `AnnotationValidation.validateItems`），`deletePage(side, page)` 删整页，无文件/无页时返回 false。
- 写前读最新 root，替换目标页后整体写回；`annotations.json` 不存在且 items 非空时经 `DocumentsContract.createDocument` 创建；空写空文件时不创建（no-op false）。
- 手写笔迹继续走 `ProjectPenStore.writePage`：它只替换 freehand 条目、保留其它类型；通用写与笔迹写串行化（调用方持同一 Mutex），后写者读到前者结果，不互相覆盖。
- 损坏的 annotations.json 读时抛 IOException（与现有 `readRoot` 一致），不静默吞。

## Acceptance (EARS)

- When 读取含 text/rect 批注的页时，SAF 边界 shall 返回全部条目而不只笔迹。
- When 通用写替换某页时，shall 只改该页，其它页与未知字段原样保留。
- When 笔迹写与通用写交替时，shall 各自保留对方类型条目。
- When 文件缺失且写入空页时，shall 不创建文件并返回 false。

## Verification

- JVM（Fake ContentResolver 或 robolectric 风格的 SAF fake）：读写往返、类型保留、删页、空写不建文件。
- 若 SAF fake 成本过高，退为 File 版等价路径测试 + SAF  thin 适配编译；ledger 注明。
- `cd android && ./gradlew :app:testDebugUnitTest --offline` 通过。

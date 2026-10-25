# Canvas Schema Decision（annotations.json 类型集演进与桌面互通约束）

- 状态：已接受（移植批次 0，分支 `codex/port-canvas-notes-features`）
- 范围：`src/annotations.json` 的类型集演进策略、未知字段透传机制、图层 schema 草案、形状参数化子类型，以及 Android 与桌面端的互通硬约束。
- 性质：决策文档。本批次只新增本文档与 `feature-pen` 透传单测，不改任何 schema 文件或运行时代码。

## 1. 基线事实（现状证据）

### 1.1 桌面端是类型集与校验规则的单一事实来源

桌面服务端 `validAnnotationItems`（`server.mjs:826-860`）是唯一权威校验器：

- `type` 为封闭枚举 9 种：`pen / highlight / line / arrow / rect / ellipse / text / image / html`（`server.mjs:830-838`）。
- `color` 匹配 `^#[a-f0-9]{6}$`（大小写不敏感）；`width` 有限值且 `0.1 ≤ width ≤ 100`；`points` 为 1..30000 个 `[x, y]`，每个分量有限且 `|n| ≤ 1000000`。
- 按类型附加约束：`text ≤ 10000` 字符、`html ≤ 100000` 字符、`image.url` 匹配 `/attachments/<uuid36>.(png|jpe?g|gif|webp)`。
- **该校验器只检查已知字段，不拒绝未知字段**：item 上的任何额外键（`pressures`、`layoutAnchor` 等）原样落盘。
- 写入语义：`PUT /api/documents/:id/annotations/:side/:page` 只整体替换 `all[side][page]`（`server.mjs:919-921`），替换前把旧全文写入 `annotation-history/` 备份（`server.mjs:912-918`）；root 上其他键、其他 side/page 原样保留；读取（GET，`server.mjs:870-887`）对 root 不做 schema 校验，文件不存在时回退 `{document:{},note:{}}`。

桌面渲染端 `src/annotations.js` 与之呼应：粘贴校验使用同一 9 种类型表（`src/annotations.js:596-606`）；撤销深度 80（`src/annotations.js:49`）；item 对象在内存中整只克隆保留，未知字段随对象存活。

### 1.2 Android 端现有镜像与透传实现

- 自由笔迹路径：`PenStroke`（`android/feature-pen/src/main/java/io/pagenote/feature/pen/PenStroke.kt:16-49`）持有 8 个 canonical 字段 + `extraFields`；`toAnnotationsItem()` 写出 canonical 字段并追加非 canonical 的 extension 字段（`PenStroke.kt:38-40`），canonical 键名集合 `CANONICAL_ANNOTATION_FIELDS`（`PenStroke.kt:45-47`）。
- 校验镜像：`PenValidation`（`PenStroke.kt:52-105`）逐条对齐桌面 `validAnnotationItems`（含每页 2000 条上限）。
- 解析：`PenPageStore.parseAnnotationsItem`（`android/feature-pen/src/main/java/io/pagenote/feature/pen/PenPageStore.kt:163-197`）把非 canonical 键收进 `extraFields`（`PenPageStore.kt:182-186`）；`pressures` 缺失或长度不足时按点补默认值 1.0（`PenPageStore.kt:178-179`）。
- 通用批注路径：`AnnotationValidation`（`android/feature-annotations/src/main/java/io/pagenote/feature/annotations/AnnotationStore.kt:18-86`）同样镜像桌面规则，另为 `highlight` 接受 4 元 `rect` 简写（`AnnotationStore.kt:41-45`，注释声明由 contract regression 覆盖）。
- 页合并：`AnnotationPageMerger.replacePage`（`android/app/src/main/java/io/pagenote/android/AnnotationPageMerger.kt:16-41`）只替换目标页内的 `pen/highlight`（`AnnotationPageMerger.kt:6,12`），root 未知键与其他页面原样保留；SAF 写入走 `SafRecoverableDocumentWriter` 恢复副本（`ProjectPenStore.kt:103-111`）。
- 读取过滤：`ProjectPenStore` 只把 `pen/highlight` item 交给 `PenPageStore` 解析（`android/app/src/main/java/io/pagenote/android/ProjectPenStore.kt:170-177`），其余类型不进入自由笔迹模型。
- 契约 schema：`android/docs/spec/contracts/annotations.schema.json` 的 item 允许 `additionalProperties: true`，但 root 声明 `additionalProperties: false`（仅 `document/note`），与 §3 记录的根级透传保证存在冲突，见决策 D3。

## 2. 决策 D1：类型集演进策略

1. **枚举跟随桌面**：Android 的两处类型枚举（`PenValidation.STROKE_TYPES`，`PenStroke.kt:53-55`；`AnnotationValidation.TYPES`，`AnnotationStore.kt:20-22`）必须与桌面 `server.mjs:830-838` 保持一致。新类型先落桌面服务端校验器与渲染器，Android 再同步镜像；**Android 不单方面新增 `type` 枚举值**，避免写出桌面 400 拒绝（`server.mjs:894-896`）的数据。
2. **未知类型的读行为分级**：
   - 自由笔迹路径：非 `pen/highlight` 的 item（含未来新类型）不进入 `PenStroke` 模型（`ProjectPenStore.kt:170-177`），页合并时原样保留（`AnnotationPageMerger.kt:12`）——Android 读到不认识的类型时"不改写、只透传"。
   - 通用写路径：`AnnotationValidation.validateItem` 拒绝未知 `type`（`AnnotationStore.kt:32`），保证 Android 主动写出的页面永不含未镜像类型。
3. **只增不删**：已发布类型永不从枚举移除；双端旧版本长期存在，删除枚举值会让旧端把历史数据当非法。
4. **一致性锁定**：本批次先以 `feature-pen` 单测锁透传语义（§7）；"两处枚举与桌面校验器三方一致"的共享回归测试列入后续批次（§9）。

## 3. 决策 D2：未知字段透传机制

分层规则（读入 `PenPageStore.kt:182-186`，写出 `PenStroke.kt:38-40`，页合并 `AnnotationPageMerger.kt:24-40`）：

| 层级 | 规则 |
| --- | --- |
| item 内 extension 字段 | 收进 `extraFields`，序列化时追加在 canonical 字段之后；**不得覆盖 canonical 字段**（`PenStroke.kt:39` 按 `CANONICAL_ANNOTATION_FIELDS` 过滤） |
| item 生命周期 | extension 字段随 item 存在而保留、随 item 删除（橡皮/清页/撤销重做快照）而消失，不产生孤儿字段 |
| 页级 | 目标页内非自由笔迹 item 原样保留（`AnnotationPageMerger.kt:12`） |
| root 级 | root 未知键与未知 side 原样保留（`AnnotationPageMerger.kt:24-29` 的 mutable 拷贝只改写 `side` 键）；读取侧未知 side 被忽略（`PenPageStore.kt:132`） |
| 数值保真 | extension 值不做类型收敛，经 `JSONObject ↔ Kotlin Map` 往返原样透传（`ProjectPenStore.kt:180-203`） |

已知跨端 extension 字段登记（互通时必须遵守）：

| 字段 | 写入方 | 读取方义务 |
| --- | --- | --- |
| `pressures` | Android（`PenStroke.kt:33`，恒写） | 桌面忽略不解释；Android 读取时缺失/短数组按点补 1.0（`PenPageStore.kt:178-179`） |
| `layoutAnchor` | 桌面 note 侧重投影锚（写入 `src/annotations.js:216-231`，rebase/复制粘贴时删除，`src/annotations.js:275,616`） | Android 不写入、不解释，但必须原样保留（走 `extraFields` 通道，`layoutAnchor` 不在 canonical 集合内） |

约束：`CANONICAL_ANNOTATION_FIELDS` 是保留字表；未来把某个 extension 字段升格为 canonical 时，必须同版本升级该集合并保持"extension 不得覆盖 canonical"的过滤语义不变。

## 4. 决策 D3：图层 schema 草案

现状：root 只有两个固定"面" `document` / `note`（`PenStroke.kt:7-14`；桌面 `src/annotations.js:17`），渲染层按 `(side, page)` 挂载 SVG ink layer（`src/annotations.js:450-474`）。v1 没有命名图层概念。

草案（v2，仅约定不实装）：

1. **不改变 v1 root 形状**：`document` / `note` 永远是合法且优先的容器，等价于两个保留图层。
2. **命名图层走保留 root 键 `layers`**，旧端按 D2 的 root 级透传规则原样保留、不解释；新端只在用户显式创建命名图层时才写入该键。
3. **item 归属用 extension 字段 `layer`**（字符串 id，缺省视为所属 side 的默认图层）；图层 id 在 `layers` 内唯一，不允许与 `document` / `note` 冲突。
4. **Android 在图层特性落地前不得写出 `layers` 或 `layer` 字段**；本决策只冻结键名与放置位置，字段细节由实装批次补契约样例。

```json
{
  "document": {"1": []},
  "note": {"1": []},
  "layers": {
    "version": 1,
    "order": ["ink-1"],
    "items": {
      "ink-1": {
        "side": "document",
        "name": "layer name",
        "visible": true,
        "locked": false,
        "opacity": 1.0
      }
    }
  }
}
```

配套决策：`contracts/annotations.schema.json` 的 root `additionalProperties: false` 与根级透传保证（`features/pen-project-integration.md`「unrelated root fields are retained」及 `AnnotationPageMerger` 行为）冲突。后续批次应把 root 放宽为 `additionalProperties: true`（`document/note` 仍为 `required`），并追加 `layers` 草案定义；在本批次内仅记录决策，不改契约文件。

## 5. 决策 D4：形状参数化子类型（two-point 约定）

桌面渲染 `shape()`（`src/annotations.js:102-190`）确立了全部形状的参数化方式：**一切矢量形状由 `points[0]` / `points[1]` 两点参数化**，没有 center+radius 或 w+h 的替代编码。

| type | 参数化 | 依据 |
| --- | --- | --- |
| `pen` / `highlight` | 折线全量点；单点退化为点 `M a l.1 .1` | `src/annotations.js:113-123` |
| `line` / `arrow` | `points[0]→points[1]`；arrow 头部尺寸由 `width` 派生（`14 + width*2`） | `src/annotations.js:124-131` |
| `rect` | 对角两点，绘制取 min/max | `src/annotations.js:132-139` |
| `ellipse` | bbox 两点 → `cx/cy/rx/ry` | `src/annotations.js:140-147` |
| `image` | 两点 bbox，最小边 10；`url` 必须是项目附件 | `src/annotations.js:148-157` |
| `html` | 两点 bbox，最小边 50 | `src/annotations.js:158-165` |
| `text` | 单锚点 `points[0]` + `text` 字段，字号由 `width` 派生 | `src/annotations.js:172-188` |

Android 约束：

1. 矢量项写盘必须恰好 2 点（末端停留识形 task 42 已按此落盘，见 `tasks.md:350-354`）；`text` 恒 1 点。
2. 不得发明替代参数化；新形状若无法用两点表达，须先修订桌面 `shape()` 并同步本表。
3. `pressures` 恒写对所有类型（`PenStroke.kt:33`）；桌面忽略该字段，属既定互通容忍点，不为矢量类型做特殊分支。
4. 退化语义对齐桌面：`rect/ellipse` 两点重合退化为零尺寸/点，`line` 单点由 `points[1]` 缺省回退 `points[0]`（`src/annotations.js:103`）。

## 6. 桌面互通硬约束清单

1. 坐标系 width-1000：x ∈ 0..1000，y 按纵横比可超 1000（`src/annotations.js:35-38`）；安全上限 `|coord| ≤ 1000000`。
2. 数值边界：`width` 0.1..100、点数 1..30000、每页 ≤2000 item、`text ≤10000`、`html ≤100000`、image url 限定 `/attachments/<uuid36>.(png|jpe?g|gif|webp)`（`server.mjs:826-860` ↔ `PenStroke.kt:53-105`）。
3. 写语义差异：桌面 PUT 整页替换（`server.mjs:919-921`）；Android 自由笔迹路径只替换页内 `pen/highlight`（`AnnotationPageMerger.kt:12`）。两端对同一页并发写无跨进程 CAS，Android 侧由进程内串行化 + SAF 恢复写入缓解（`tasks.md:69` 已记录该限制）。
4. 剪贴板 marker：`PAGE_NOTES_ANNOTATION:\n` + item JSON（`src/annotations.js:587-589`）。
5. `pressures` / `layoutAnchor` 按 §3 登记表处理。
6. 桌面写前落 `annotation-history/` 备份；Android 落 SAF 恢复副本并报告副本名（`ProjectPenStore.kt:106-110`）。

## 7. 本批次测试加固

`PenStrokeTest` 原有 10 个用例只覆盖 `PenValidation` 校验，不覆盖 `PenStroke.kt:16-48` 的序列化与透传。本批次在同一测试类新增（不改动既有用例）：

- canonical 字段序列化形状（`type/color/width/points/pressures`，`text/html/url` 仅非空写出）；
- extension 字段透传与嵌套值保真；
- extension 字段不得覆盖 canonical 字段（含逐个 canonical 键的负面用例）；
- `toAnnotationsItem → loadAnnotationsMap → extraFields` 全链路往返。

既有相关覆盖（未改动）：`PenPageStoreTest.selectedStrokeStyleChangePreservesGeometryAndSupportsUndoRedo`（样式更新保留 `extraFields`，`PenPageStoreTest.kt:114-127`）、`AnnotationPageMergerTest`、`pen-extension-field-preservation-plan.md` 对应回归。

## 8. 验证

- 命令：`./gradlew :feature-pen:testDebugUnitTest`（`android/` 目录下执行），结果见提交说明。

## 9. 未覆盖与后续批次

- `PenValidation.STROKE_TYPES` / `AnnotationValidation.TYPES` / 桌面 `server.mjs` 三方一致的共享契约回归（建议落 `android/shared-tests/`）。
- `contracts/annotations.schema.json` root 放宽为 `additionalProperties: true` 并补 `layers` 定义（D3 配套）。
- `layers` / `layer` 字段实装与桌面端同步方案。
- `AnnotationValidation` 的 highlight `rect` 简写来源核对与文档化（当前仅代码注释声明，`AnnotationStore.kt:41-45`）。

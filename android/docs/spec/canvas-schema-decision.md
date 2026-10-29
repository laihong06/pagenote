# Canvas Schema Decision（annotations.json 类型集演进与桌面互通约束）

- 状态：已接受（移植批次 0 建立；移植批次 2 增补 D5 形状目录决策并落地；移植批次 4 增补 D6 文本样式 schema 决策并落地；移植批次 5 增补 D7 贴纸/素材/公式决策并落地，分支 `codex/port-canvas-notes-features`）
- 范围：`src/annotations.json` 的类型集演进策略、未知字段透传机制、图层 schema 草案、形状参数化子类型、文本工具样式 schema 扩展字段，以及 Android 与桌面端的互通硬约束。
- 性质：决策文档。批次 0 只新增本文档与 `feature-pen` 透传单测；批次 2 按 D5 落地 `ShapeCatalog` 与形状对话框接线；批次 4 按 D6 落地 `TextFontCatalog`/`AnnotationTextStyle` 与 UpdateTextStyle 动作；均不改任何 schema 契约文件。

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
| `shape` | Android（批次 2，目录形状 kind id，`PdfAnnotationVectorDraft`） | 桌面按基类型渲染并原样保留；Android 读取时未知 id 按基类型降级（D5） |
| `font` / `fontSize` / `align` | Android（批次 4，`type:"text"` 项的文本样式，见 D6） | 桌面忽略不解释（字号仍按 `width` 派生渲染）；Android 读取时未知字体 id / 越界字号 / 未知对齐键降级为默认（D6） |
| `sticker` / `angle` / `flipX` / `flipY` | Android（批次 5，`type:"image"` 项的贴纸身份与摆放姿态，见 D7） | 桌面忽略不解释（按未旋转 bbox 渲染 image）；Android 读取时越界角度/非布尔镜像降级为默认、非法来源视为普通图片（D7） |
| `latex` / `latexColor` | Android（批次 5，`type:"image"` 项的公式源，见 D7） | 桌面忽略不解释（仍按附件位图渲染）；Android 读取时非法行丢弃、非法颜色降级为 canonical `color`（D7） |

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

## 6. 决策 D5：形状目录参数化子类型（ShapeCatalog 扩展字段）

批次 2 落地 `feature-pen` 的纯 JVM 形状几何目录 `ShapeCatalog`（`android/feature-pen/src/main/java/io/pagenote/feature/pen/ShapeCatalog.kt`）：24 种 kind 的轮廓折线生成、包围盒与 stroke hit-test，全部单测覆盖。序列化与互通规则如下：

1. **不新增 `type` 枚举（延续 D1）**：目录形状落盘时 `type` 仍取桌面 9 类型中的基类型——线族 `line`（直线/虚线/单双箭头/坐标轴/波浪/锯齿）、面族 `rect`（矩形族与全部多边形/星形/十字）、椭圆族 `ellipse`（椭圆/正圆）。kind id 写入 item extension 字段 **`shape`**（字符串，如 `"pentagon"`），走 D2 的透传通道；桌面端按基类型渲染并原样保留 `shape` 字段，不会 400 拒绝（`server.mjs` 只校验已知字段）。
2. **两点参数化（延续 D4）**：kind 的几何由 `points[0]`/`points[1]` 完全参数化，恒写 2 点。线族保留拖拽方向（first→second），面族按对角两点 min/max 归一化为包围盒；`square`/`circle` 在包围盒内取中心内接（min 边）。退化语义按基类型族区分：线族拒绝两点重合、面族拒绝零面积（`PdfAnnotationVectorDraft.create`）。
3. **kind id 只增不删**：`ShapeCatalog.byId` 是唯一注册中心，id 一旦发布不得改名或移除；未知 id 读侧按基类型降级渲染、不报错。
4. **基础四项不带 `shape`**：`line/arrow/rect/ellipse` 既有落盘格式不变（无 `shape` 键），与桌面渲染一一对应；只有目录扩展项写 `shape`。
5. **虚线是描边样式不是几何**：`dashed-line`/`dashed-rect` 与实线 kind 共享同一轮廓（hit-test 一致），渲染端按 `ShapeStrokeStyle` 加 dash 效果。
6. **降级渲染边界（本批次现状）**：Android overlay（`PdfAnnotationOverlay`）尚未消费 `shape` 字段，目录扩展项在预览与重载时按基类型渲染（矩形/直线/椭圆）；渲染接线由后续批次完成，落盘数据已按本决策定型。

## 6.5 决策 D6：文本工具样式 schema 扩展字段（批次 4 落地）

批次 4 在 `feature-annotations` 落地文本样式 schema（`TextFontCatalog.kt` + `AnnotationTextStyle.kt`），`type:"text"` 项的字体/字号/对齐/颜色按以下规则存储：

1. **不新增 `type` 枚举（延续 D1）**：样式全部走 D2 透传通道的 item extension 字段；`color` 沿用 canonical 字段。字段名冻结为 `font`（字符串）、`fontSize`（数值，文档单位）、`align`（`left|center|right`）。
2. **字号与桌面派生兼容**：桌面渲染字号恒为 `max(20, width*5)`（`src/annotations.js:178`）。`fontSize` 缺省时 Android 按同式派生；显式取值范围冻结为 `20..500`（与派生值域一致）。桌面忽略 `fontSize`，同一 item 在桌面仍按 `width` 派生渲染——这是既定互通容忍点，不为桌面改渲染。
3. **字体目录单一注册中心**：`font` 值必须是 `TextFontCatalog` 已注册 id；目录条目映射 Android 系统字体族 + WebView CSS 栈（桌面不捆绑字体二进制，Android 同样不捆绑；`assets/fonts/` 预留为未来捆绑字体的位置）。id 只增不删；未知 id 读侧降级为默认（同 D5 §3）。
4. **读侧容错、写侧严格**：读取时未知 `font`、越界 `fontSize`、未知 `align` 一律降级为默认，不拒绝 item（保证桌面写入的历史数据永不被 Android 判非法）；Android 主动写出前经 `AnnotationTextStyle.create`/`AnnotationTextStyleEditor.updateTextStyle` 校验，永不写非法值。
5. **默认 schema（文本工具新建项）**：`font=default`（目录默认）、`fontSize=derivedFontSize(当前笔宽)`、`align=left`，随 `AnnotationTextDraft.toItem(style=...)` 显式写出；无样式参数的调用保持旧格式（不带扩展字段），既有调用方与测试不变。
6. **UpdateTextStyle 撤销动作**：样式更新以 `AnnotationUpdateTextStyleAction`（app，注册 id `pdf.annotationTextStyle`）适配到批次 1 的统一 `PenAction` 栈，连续同 scope 样式编辑合并为一个撤销步（对齐 `PenPageStoreActions` UPDATE_STYLE 合并语义）；阅读器 UI 仍走既有 `PdfAnnotationEditHistory` 快照撤销，两条路径不混用。

## 6.7 决策 D7：贴纸与素材系统数据层（批次 5 落地）

批次 5 移植 touchnotes 的贴纸系统（`draw.sticker.StickerManager` / `paint.core.StickerManager`：
zip 资产包导入、`stickers/` 库目录、`_sticker` 二进制容器、GifImage 动效贴纸、面板拖拽排序）与
素材系统（`StickerInfo{materials, rect, scale}` 保存选区、`FormulaMaterial` 公式位图），全部落在
数据层，UI 接线归画布重做批次。

1. **贴纸对象不新增 `type` 枚举（延续 D1）**：贴纸落盘为 `type:"image"` 项——位图走项目附件通道
   （`url: /attachments/<uuid36>.(png|jpe?g|gif|webp)`，复用 ProjectImageImporter 的 assets/ 落盘与
   尺寸校验），几何走 D4 两点 bbox（最小边 10），身份与姿态走 D2 扩展字段：`sticker`（来源相对路径，
   缺省=普通图片批注）、`angle`（度，规范域 (-180,180]，0 不写）、`flipX`/`flipY`（布尔，false 不写）。
   拖拽/缩放数据层（`AnnotationImageTransform`）：平移与等比缩放改写 canonical `points`，旋转/镜像只改
   扩展字段；桌面按未旋转 bbox 渲染并透传字段——与 D5 §6 同构的既定降级容忍点。
2. **GIF 贴纸零新字段**：`url` 的 `.gif` 后缀即动效标记。帧元数据（画布尺寸/帧数/逐帧时延/
   NETSCAPE 循环数）由纯字节解析器 `GifMetadataParser`（app）先行提供，像素解码归渲染层；时延
   <20ms 按浏览器惯例回退 100ms。
3. **贴纸资产包格式**：zip，条目为图片（png/jpg/jpeg/gif/webp），允许一层子目录作分组；上限
   200 条目/单条 20MB/总量 200MB；拒绝路径穿越、绝对路径、深层级、重名（`StickerPackArchive`）。
   SAF 导入（`ProjectStickerPackImporter`）落 `<project>/stickers/<pack>/[group/]<file>`，包名与
   文件名安全化 + 唯一化（`name_1` 递增），失败全量清理（对齐 ProjectImageImporter 的残留上报语义）。
4. **贴纸面板数据/拖拽排序**：排序持久化为 `stickers/order.json`（version 1，`{"folders":{path:
   [names]}}`）；顺序表是**优先覆盖**——表中存在的名字按表排列，未列入的名字按名称不区分大小写
   追加（新增贴纸零配置可见）；拖拽落点对齐 ItemTouchHelper `onMove` 的 from/to 语义。坏文件
   容错为默认排序。
5. **素材库是独立文件 `<project>/materials.json`**（version 1）：不进 annotations.json root（不触碰
   D3 的 root 键名预留），桌面不读取不解释。素材 = 一组经桌面校验镜像（AnnotationValidation）的
   item + 名称 + kind（selection/sticker/formula）；上限 100 条/条内 50 item/名称 80 码点；修改前
   全量备份 `materials-history/` + 原子写（对齐 AnnotationStore 姿态），SAF 侧走恢复写入。
6. **公式素材 = image 项 + `latex` 扩展**（对齐 touchnotes `Image.mLatexTexts`/`latexColor`）：
   `latex`（字符串数组 1..8 行，每行 ≤2000 字符）+ `latexColor`（`#rrggbb`，缺省取 canonical
   `color`）；位图附件是渲染产物，latex 字段保留可重渲染性。渲染数据层复用应用既有 JLatexMath
   依赖（ru.noties:jlatexmath-android，与笔记 Markdown 公式同源）：JLatexMathDrawable 测行 →
   `FormulaMaterialLayout`（复刻 touchnotes 布局常量 100/40/40/20）→ ARGB_8888 位图逐行绘制。
7. **读侧容错、写侧严格（延续 D5 §3 / D6 §4）**：所有 D7 扩展字段读侧非法值降级默认、绝不拒绝
   item；Android 主动写出经 `create`/`upsert` 校验，永不写非法值。

## 7. 桌面互通硬约束清单

## 6. 桌面互通硬约束清单

1. 坐标系 width-1000：x ∈ 0..1000，y 按纵横比可超 1000（`src/annotations.js:35-38`）；安全上限 `|coord| ≤ 1000000`。
2. 数值边界：`width` 0.1..100、点数 1..30000、每页 ≤2000 item、`text ≤10000`、`html ≤100000`、image url 限定 `/attachments/<uuid36>.(png|jpe?g|gif|webp)`（`server.mjs:826-860` ↔ `PenStroke.kt:53-105`）。
3. 写语义差异：桌面 PUT 整页替换（`server.mjs:919-921`）；Android 自由笔迹路径只替换页内 `pen/highlight`（`AnnotationPageMerger.kt:12`）。两端对同一页并发写无跨进程 CAS，Android 侧由进程内串行化 + SAF 恢复写入缓解（`tasks.md:69` 已记录该限制）。
4. 剪贴板 marker：`PAGE_NOTES_ANNOTATION:\n` + item JSON（`src/annotations.js:587-589`）。
5. `pressures` / `layoutAnchor` 按 §3 登记表处理。
6. 桌面写前落 `annotation-history/` 备份；Android 落 SAF 恢复副本并报告副本名（`ProjectPenStore.kt:106-110`）。

## 8. 本批次测试加固

`PenStrokeTest` 原有 10 个用例只覆盖 `PenValidation` 校验，不覆盖 `PenStroke.kt:16-48` 的序列化与透传。本批次在同一测试类新增（不改动既有用例）：

- canonical 字段序列化形状（`type/color/width/points/pressures`，`text/html/url` 仅非空写出）；
- extension 字段透传与嵌套值保真；
- extension 字段不得覆盖 canonical 字段（含逐个 canonical 键的负面用例）；
- `toAnnotationsItem → loadAnnotationsMap → extraFields` 全链路往返。

既有相关覆盖（未改动）：`PenPageStoreTest.selectedStrokeStyleChangePreservesGeometryAndSupportsUndoRedo`（样式更新保留 `extraFields`，`PenPageStoreTest.kt:114-127`）、`AnnotationPageMergerTest`、`pen-extension-field-preservation-plan.md` 对应回归。

批次 2（D5 落地）新增：

- `ShapeCatalogTest`（`feature-pen`）：目录规模 ≥21、id 唯一且基类型合法、轮廓确定性、线族方向保留、面族包围盒归一化、闭合形状闭合、虚线与实线共享轮廓、多边形顶点数、退化两点语义、stroke hit-test 命中/未命中与容差半径、内接 square/circle。
- `PdfAnnotationVectorDraftTest` 扩展：目录扩展项序列化为基类型 + `shape` 扩展字段、基础四项不写 `shape`、对话框全部工具经 `create` 落盘两点恒为 2、未知/错配 kind 与按族退化拖拽的拒绝路径。

批次 5（D7 落地）新增：

- `StickerAnnotationTest`（feature-annotations，12 例）：canonical image 项 + 两点 bbox、默认姿态不写扩展字段、姿态字段与 bbox 归一化、角度规范域、写侧拒绝清单（坏资产名/颜色/宽度/退化 bbox/路径穿越/越界角度/坐标越界）、读侧往返与未知字段共存、读侧降级（越界角度/非布尔 flip/空来源）、非 image 与畸形 canonical 拒绝、GIF url 派生、摆放几何纵横比/中心对齐/退化拒绝、落盘项通过 AnnotationValidation 镜像。
- `AnnotationImageTransformTest`（feature-annotations，11 例）：读姿态、平移、绕中心/绕角缩放与退化拒绝、旋转累加与规范域、镜像翻转、旋转命中测试（含容差）、applyToItem 重写姿态并保留未知字段、回正清除姿态字段、写读往返。
- `CanvasMaterialTest`（feature-annotations，8 例）+ `FormulaAnnotationTest`（5 例）：素材编解码往返、newEntry uuid 与非法 item 拒绝、解码跳过非法条目、未知 version 拒绝、upsert 覆盖与 FIFO 上限、删除、store 备份/去重/缺失文件、公式行/色校验、latex 数组与颜色降级、merge 写入与清除、落盘项通过校验镜像。
- `StickerPackArchiveTest`（app，10 例）：根级/一层分组解析、路径与扩展名拒绝、目录条目跳过、非图片/穿越/重名/空包/超限条目/超条目数拒绝、MIME 白名单。
- `StickerPanelOrderTest`（app，7 例）：order.json 编解码往返、容错（坏 JSON/未知 version/非字符串项）、顺序合成（覆盖+新增追加+陈旧项不占位）、默认不区分大小写排序、applyMove 语义、拖拽→落盘→重读全链路。
- `GifDocumentMetadataTest`（app，7 例）：头/尺寸/帧数、GCT 偏移、逐帧时延、零时延浏览器回退、NETSCAPE 循环数（无限/3 次/缺省）、动效标记、非 GIF/截断/未知块拒绝。
- `FormulaMaterialLayoutTest`（app，5 例）：单行 padding、超高行归一、多行堆叠与水平居中、targetHeight 整体缩放（含 y 起点与行间距同步缩放）、非法输入拒绝。

## 9. 验证

- 命令：`./gradlew :feature-pen:testDebugUnitTest`（`android/` 目录下执行），结果见提交说明。

## 10. 未覆盖与后续批次

- `PenValidation.STROKE_TYPES` / `AnnotationValidation.TYPES` / 桌面 `server.mjs` 三方一致的共享契约回归（建议落 `android/shared-tests/`）。
- `contracts/annotations.schema.json` root 放宽为 `additionalProperties: true` 并补 `layers` 定义（D3 配套）；`shape` 扩展字段的契约样例（D5 配套）尚未入 schema。
- `layers` / `layer` 字段实装与桌面端同步方案。
- `AnnotationValidation` 的 highlight `rect` 简写来源核对与文档化（当前仅代码注释声明，`AnnotationStore.kt:41-45`）。
- 批次 2 遗留：`PdfAnnotationOverlay` 对 `shape` 扩展字段的目标形状渲染（当前按基类型降级，D5 §6.6）；画布手势按目录形状实时预览；`shape` 字段在样式编辑/选择/剪贴板链路中的保留回归（当前仅写路径覆盖）。
- 批次 5 遗留（UI 归画布重做批次）：贴纸面板 UI 与拖拽接线；贴纸上画布的"库文件 → 附件导入 → image 项"组合调用；GIF 帧像素解码/播放（当前仅帧元数据）；`angle`/`flipX`/`flipY` 的 overlay 目标渲染；公式位图 → 附件 → image 项的落盘接线；`FormulaMaterialRenderer` 的 JVM 级测试（依赖 android.graphics 与 JLatexMath，仅编译验证）；SAF 导入器（ProjectStickerPackImporter/ProjectStickerLibrary/ProjectMaterialStore）的 provider级行为验证（JVM 只覆盖纯逻辑，对齐 AnnotationSafBoundaryTest 的边界测试姿态）；贴纸/素材契约 schema 样例（sticker、latex 等扩展字段尚未入 contracts/annotations.schema.json）。

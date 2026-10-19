# Android PDF Image Annotation

## Scope

接通 `Handwriting > Insert Image`，允许在当前 PDF 页插入可离线保存的图片批注。沿用桌面 annotation schema 和项目资源格式；仅实现文档 PDF 侧，不扩展 Markdown 图片插入、批注选中/移动/缩放或 HTML 批注。

## Interaction

- 仅可写 PDF 项目且 Reader 不忙时启用命令；点击后使用 Android `OpenDocument` 选择 `image/*`。
- 选择器取消、拒绝的文件或上下文失效不得写入 annotation。开始选择时捕获项目身份、PDF session、页码和当前笔颜色/宽度；返回后若项目、session 或页码已变化，拒绝提交。
- 复用 `ProjectImageImporter` 的格式/尺寸校验与 SAF 清理，图片写入项目 `src/assets/`。导入成功后再以 `ProjectAnnotationStore` 追加 annotation；保存失败时删除本次新资源，清理失败须报告残留资源名。
- 插入位置沿用桌面可见区域语义：宽度 1000 坐标中的 `x=80`；Y 对齐当前页面可见顶部下方 80 dp，且最小为 90。图片宽度为 350 单位，高度按像素宽高比计算。
- 持久化对象使用桌面标准 `{type:"image", color, width, points:[[x1,y1],[x2,y2]], url:"/attachments/<uuid>.<ext>"}`。项目包中的图片文件存放在 `src/assets/<uuid>.<ext>`；桌面项目导入/导出负责在 `/attachments/` 与 `assets/` 间映射，Android 在 SAF 中按 UUID 从 `src/assets/` 解析标准批注 URL。颜色和线宽捕获当前笔样式；只追加当前页，不改写其他批注、页面或项目原文。
- 图片通过 SAF 项目资源离线读取，并与 PDF 同步缩放、平移和裁剪。批注 URL 仅接受标准 `/attachments/<uuid>.(png|jpg|jpeg|gif|webp)`，只映射到项目 `src/assets/` 下的单个文件名；绝不加载 HTTP(S)、外部绝对路径或任意 URI。缺失/损坏资源显示可见占位，不得导致 PDF 页面失败。
- 预览解码在后台执行，仅为当前页保留有界解码结果；长边不超过 2048 px。页面或项目变化后旧页结果不得绘入新页。

## Acceptance (EARS)

- When 用户在可写 PDF 项目点击 `Handwriting > Insert Image` 并选中受支持图片时，Android shall 将图片复制到项目 assets 并追加标准 `type=image` 批注到捕获的当前页。
- When 用户取消选择、文件不受支持、写入失败或项目/session/页码在选择期间变化时，Android shall 不创建批注；如果已导入新资源，则尽力删除它并报告无法清理的残留名称。
- When 当前 PDF 页包含有效的本地 image 批注时，Android shall 按桌面 width-1000 坐标、350 单位宽度和原图宽高比绘制，并随 PDF 缩放/平移及页面裁剪。
- When image URL 无效或资源不可读/损坏时，Android shall 拒绝外部访问、显示可见占位并继续渲染 PDF 和其他批注。
- When 图片批注成功写入时，Android shall 保留其他 annotation 数据和其他页内容，并刷新当前 overlay。

## Verification

- JVM：验证桌面位置换算、宽高比、schema 序列化与非法 URL/坐标拒绝。
- 唯一 Android 14/API 34 平板 AVD：一条应用内闭环验证空项目/Markdown 门控、取消、导入与持久化、相邻页隔离及真实图片 overlay 像素；picker 由测试注入，测试不启动 DocumentsUI 或其他 app。
- 构建：运行受影响 JVM 用例、`:app:compileDebugAndroidTestKotlin` 和 `:app:assembleDebug`；仅在唯一 API 34 平板 AVD 安装/运行聚焦仪器测试。小米平板 5 Pro 验收仍归 task 12。

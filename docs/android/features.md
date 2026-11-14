# Android 功能结构与截图覆盖

[Android 入口](README.md) · [配图教程](guide/README.md) · [截图环境与复现](guide/capture.md)

本表描述 PR #36 的 Android 开发源码，运行验收对应提交 `bbd4fb7780a5dc9615b27e1eae7d1851a1527b45`；**不是公开 beta.5 APK 的完整功能承诺**。“本轮已操作”表示在 API 34 模拟器实际打开或操作过；配置画面不等于模型、联网服务或真机硬件已验收。

| 批次／模块 | 通俗用途 | 实际入口 | 操作与退出／撤回 | 教程 | 真实截图 | 状态与限制 |
| --- | --- | --- | --- | --- | --- | --- |
| 基础：书架与项目 | 授权一个课件目录，再查找和打开笔记本 | 启动书架、阅读器左上角主页 | 选择目录→打开封面；主页返回书架 | [阅读](guide/reading.md#书架与目录授权) | [01](guide/images/current/01-bookshelf.png) | 本轮已打开合成 SAF 项目；长期授权、真实外置存储另验 |
| 基础：PDF 与逐页笔记 | 原文和当前页笔记并排阅读 | 阅读器页码、编辑／预览页签 | 默认连续上下滚动；页码与笔记跟随视口中心页；阅读设置可切回单页；编辑后保存，离页时保留草稿 | [阅读](guide/reading.md) | [原有阅读画面](guide/images/current/02-reading.png)；[连续阅读验收](guide/images/pdf-continuous/README.md) | Android 14/API 34 横屏平板已验连续滚动、页／笔记同步、草稿恢复和单页切换；恢复页码与所有导航组合未覆盖；Windows／macOS 未改 |
| 0：批注格式兼容 | 保留笔迹、压力样本和未知扩展字段 | 项目 `src/annotations.json`，无单独按钮 | 应用通过原有存储路径读写 | [格式边界](guide/advanced.md#项目与文件兼容) | 不适用：数据约定 | 底层能力；不能用截图替代往返兼容测试 |
| 1：笔、荧光笔与橡皮 | 在 PDF 上做手写标记，并按参考路径局部切分自由笔迹和原生图形 | 左侧笔工具／菜单→手写 | 选择笔型、颜色和粗细后书写；区域橡皮边界随手势显示；普通手势用撤销／重做 | [画布](guide/canvas.md)；[参考路径切分与横屏验收](guide/images/reference-shape/README.md#原生矢量图形区域擦除) | [02](guide/images/current/02-reading.png)；[八态擦除证据](guide/images/reference-shape/README.md#原生矢量图形区域擦除) | API 34 横屏平板已验收直线／箭头／矩形／椭圆切分及跨笔迹层单步撤销／重做；实体笔、压感、真机和桌面往返未验 |
| 1：停留识形 | 把钢笔手绘的开放线条和闭合图案识别为可保存材料 | 阅读器工具栏→钢笔 | 画直线、折线、箭头、曲线或闭合图形后短暂停留；用撤销移除整项 | [曲线与闭合形状](guide/images/reference-shape/README.md#参考曲线最小二乘回退)；[擦除与混合历史](guide/images/reference-shape/README.md#跨层区域擦除单步撤销与重做) | [参考识形横屏验收](guide/images/reference-shape/README.md#参考曲线最小二乘回退) | API 34 横屏平板已验收开放 Bezier 与最小二乘曲线、闭合椭圆／圆／矩形／三角形落盘和单步撤销；局部 OpenCV ROI、合成笔输入、参考 App 运行时差分、实体笔和桌面往返未验 |
| 1：选中、复制与跨页粘贴 | 套索混合选中自由笔迹与原生批注，可组复制、粘贴、移动、改色和删除 | 菜单→手写→选择／复制／粘贴 | 空白处拖动套索→拖动组或应用样式；删除后可撤销，粘贴保留相对位置 | [画布](guide/canvas.md#选中删除和撤销删除)；[套索与组编辑验收](guide/images/reference-shape/README.md#参考区域多材料选择) | [套索四态](guide/images/reference-shape/README.md#参考区域多材料选择) | API 34 横屏平板已验收组移动、改色、复制／粘贴、删除与撤销／重做；自由笔迹命中按首个采样点；组粗细修改、实体笔及桌面往返未验 |
| 1：矩形擦除、旋转策略 | 为画布变换和擦除提供底层规则 | 底层笔迹策略；本轮未验证独立界面入口 | 不把数据接口等同已经可操作的按钮 | [边界](guide/advanced.md#未完整接通的功能) | 不适用：本轮无独立入口证据 | 底层已合入，界面覆盖待补 |
| 2：24 种形状 | 给图文材料加直线、箭头、矩形和其他形状 | 菜单→手写→形状／左侧形状按钮 | 选形状后在 PDF 拖动；选择工具可选中和删除 | [形状操作](guide/canvas.md#画出一个形状) | [03](guide/images/current/03-shape-picker.png)、[04](guide/images/current/04-shape-created.png) | 本轮创建成功；**创建动作本身未进入撤销历史**，见 [04a](guide/images/current/04a-shape-creation-undo-disabled.png) |
| 3：纸张与项目口令 | 为项目设置背景，以及打开时的口令检查 | 菜单→文件→项目笔记设置 | 选择项目背景或设置口令→保存；取消放弃设置草稿 | [项目设置](guide/canvas.md#纸张和项目口令) | [08](guide/images/current/08-project-paper.png) | 口令检查不等于加密项目文件；本轮未设置口令或验收加密导出 |
| 4：富文本画布批注 | 在 PDF 上放有字体、颜色和格式的文字块 | 菜单→手写→富文本 | 在编辑窗输入→确认；取消关闭；选中已有文字块可再编辑 | [富文本](guide/canvas.md#富文本批注) | [07](guide/images/current/07-rich-editor.png) | 本轮打开实际编辑器；该画布对象使用受限 HTML，不是桌面原生 Markdown S |
| 5：素材、贴纸、公式、GIF 数据 | 保存素材描述、公式信息与 GIF 元数据 | 素材存储层；公式识别结果可保存到素材库 | 不提供第三方素材包；本轮未见完整素材浏览／插入入口 | [未接通项](guide/advanced.md#未完整接通的功能) | 不适用：没有完整素材面板 | **数据层／部分接线**；不宣称已有完整贴纸商店或 GIF 动画播放 |
| 6：手写与公式识别 | 把选区内手写识别为文字或公式 | 菜单→手写→手写 OCR／公式识别 | 框选→识别→核对结果，再插回笔记或画布；取消退出 | [模型条件](guide/advanced.md#ocr与公式识别) | [11](guide/images/current/11-ocr-models.png) | 本轮仅采集设置；模型未安装，未执行识别质量验收 |
| 7：全文搜索 | 在已索引的原文、笔记、OCR、转写中找内容 | 菜单→阅读→搜索；设置→重建索引 | 输入词→搜索→点结果定位；关闭返回阅读 | [搜索](guide/study-tools.md#全文搜索) | [16](guide/images/current/16-search.png) | 本轮以合成笔记搜索 `pheromone`；索引为空时先重建 |
| 8：录音、播放与离线转写 | 将录音和文字记录关联到 PDF 页 | 菜单→阅读→录音／转写；设置→ASR | 录音需麦克风授权；停止后保存胶囊，转写依赖本地模型 | [录音边界](guide/advanced.md#录音与离线转写) | [12](guide/images/current/12-audio-models.png) | 只采集设置；本轮未启用麦克风、未录真实声音、未跑 ASR |
| 9：Office 转 PDF 引擎切片 | 为未来接入本地转换引擎提供边界 | 菜单→Word 编辑／保存并返回（条件不足时禁用） | 当前没有注册可用引擎，不能按桌面 Office 教程使用 | [Office](guide/advanced.md#office与导入导出) | 无可用编辑结果图 | **引擎未接入**；不是已完成的 Android Office 编辑器 |
| 9：笔记合并、合并 PDF 导出 | 导入备份时选冲突策略；将 PDF 与笔记合并导出 | 菜单→文件→导入笔记／合并 PDF 导出 | 选择范围与策略，再由系统选择目标；取消不输出文件 | [导入导出](guide/advanced.md#office与导入导出) | [09](guide/images/current/09-merged-pdf-export.png) | 本轮打开导出范围窗；未生成或验收输出 PDF，合并策略窗截图待补 |
| 10：选区 AI 动作 | 对一段笔记改写、翻译、总结 | 编辑笔记并选中文字→菜单→Word 编辑→AI 动作 | 选动作后请求服务；检查结果后插回；关闭可放弃 | [AI](guide/study-tools.md#模型设置与选区动作) | [18](guide/images/current/18-selection-ai.png)、[14](guide/images/current/14-model-settings.png) | 本轮只打开动作窗，未发付费或联网模型请求；翻译服务需单独配置 |
| 10：系统截屏、OCR、取色 | 经系统授权抓取屏幕，再识别或取色 | 菜单→阅读→系统截屏 | 系统允许后处理截图；关闭面板退出 | [权限边界](guide/advanced.md#系统截屏) | 本轮待补 | 已有实现；本轮未授权 MediaProjection，不能列为已验收 |
| 基础 AI：逐页讲解与提示词 | 按当前页材料生成讲解；自定义教学要求 | 菜单→AI 标注→逐页精讲；设置→提示词 | 配置服务→填写目标→发起→检查并应用；取消退出 | [AI](guide/study-tools.md) | [13](guide/images/current/13-prompts.png)、[15](guide/images/current/15-page-tutor.png) | Android 内置兼容服务调用，与桌面 MCP 客户端流程不同 |
| 11：制卡、卡片库与复习 | 把重点做成正反面卡片，评分安排下次复习 | 选区→Word 编辑→制卡；卡片库／复习 | 填背面和标签→创建→翻面→评分；关闭回阅读 | [闪卡](guide/study-tools.md#从笔记制卡并复习) | [19](guide/images/current/19-flashcard-create.png)～[21](guide/images/current/21-flashcard-review.png) | 本轮完成创建、库查看、翻面和一次评分；统计不代表长期学习效果 |
| 12：悬浮笔记 | 阅读时在可移动小窗暂存想法 | 菜单→设置→悬浮窗，需“显示在其他应用上层” | 点悬浮球→笔记→保存；收起或关闭退出 | [悬浮](guide/floating.md) | [24](guide/images/current/24-floating-ball.png)、[25](guide/images/current/25-floating-note.png) | 本轮开关和切页签；低对比、换行和语言问题待修；未验证回写 |
| 12：剪贴板、常用语、计时器 | 在小窗取用文字，设置倒计时 | 悬浮面板对应页签 | 切页签，开始／暂停／重置计时；关闭停止服务 | [连续过程](guide/floating.md#按顺序查看操作) | [26](guide/images/current/26-floating-clipboard.png)～[29](guide/images/current/29-floating-closed.png) | 本轮计时器启动和关闭；未等待计时结束通知 |
| 12：夜览滤镜 | 在阅读内容上加暖色遮罩 | 菜单→设置→夜览滤镜 | 开启、调强度→保存；关闭开关可恢复 | [夜览](guide/advanced.md#夜览滤镜) | [22](guide/images/current/22-night-settings.png)、[23](guide/images/current/23-night-reading.png) | 本轮开启并显示实际遮罩 |
| 雨课堂整合 | 导入历史课件或授权采集 | 文件菜单→雨课堂 | 需账号、课程与网络；用户授权后操作 | [未验证范围](guide/advanced.md#本轮没有验证的范围) | 本轮待补 | 无账号和网络操作；不借旧截图声称当前服务可用 |
| Markdown S 遗传实验（实验性） | 读取 genetic-lab 声明块并交互，状态写回原 Markdown | 笔记预览 | 点击基因、调整实验参数；第八章使用原生 Filament 三维场景，失败时回退 Canvas | [范围、历史截图与验证](guide/markdown-s.md) | [三维场景](guide/images/markdown-s/filament-note-panel-3d.png) | 仅适配 version 1／genetic-lab；任意 HTML／JS／Three.js 围栏仍显示源码。历史手机 AVD 验证不等于本轮横屏整合验收 |
| 紧凑布局 preview | 单独体验精简工具栏 | 独立 preview 包 | 独立安装，不覆盖公开 beta 包 | [范围](guide/capture.md) | 本轮未采集 preview | 本轮所有 Android 新图来自 debug 包，不冒充 preview 验收 |

[完整限制与待补项](../../KNOWN_ISSUES.md)；维护者的布局审查、功能复核和发行批准仍需分别记录。

## 已整合的工作区能力

[后台 Agent 与自动保存](guide/workspace.md) · [全页设置](guide/menu-and-settings.md) · [密集笔迹绘制优化](benchmarks/ink-rendering-2026-11-06/README.md)。各记录标注其原运行代码；分支整合后的复验单独记录。

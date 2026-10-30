# 原生 Markdown S 验证记录（2026-10-30）

## 本轮交付

PageNote 内置渲染器直接挂载在 Markdown 排版中，原生组件内 iframe 数为零。交互修改声明式 `markdowns` 数据围栏，复用现有保存接口，分别写回项目 `src/notes.md` 和 `src/source.md`。没有生成需要单独保存的 HTML 文件。

Windows 10.0.26200 x64，仓库源码生产构建、Electron 44 开发宿主；不是安装包验收。截图视口与采集时间见两套 provenance.json；采集时是已标明的未提交工作树，不把旧 HEAD 当成本轮完整运行源码。随记录提供运行源码 SHA-256。

## 已执行

- `npm run build`：通过。
- `npm run test:markdown-native`：纯算法、状态替换和真实 Electron 演示通过。固定种子复现36个体、40代、最佳99.97；LF/CRLF、重复／缺失标识、过期视图状态拒绝写入通过。
- `npm run test:markdown-s`：历史沙盒专项通过，仅证明兼容性。
- `capture-platform-guide.mjs` 原生笔记模式：通过真实“导入笔记”入口选择完整参考 HTML，转换为已支持模板数据，未执行原网页脚本；各模块交互、文件同步和完整应用重启恢复通过。
- 同脚本源文档模式：通过；含第二页及混合换行，逐字核对修改前后围栏外内容，分页标记、中文和空行保留。
- 实测点击／键盘／连续划过基因、种群选择、轮盘拖动／方向键、交叉切点8、变异20%、第40代、三维拖动和暂停、专注模式章节定位。截图对应每个模块。
- 强制 WebGL 上下文丢失显示诊断，Markdown 状态保留；滚离及返回三维区域会释放并重建渲染器。

## 回归状态与边界

完整 `npm test` 在既有 `tests/menus.mjs:122` 的剪贴板断言失败：复制结果未包含 `name: pagenote-page-tutor`。单独隔离资料目录重试仍失败，原因未定；本轮没有修改原生菜单复制实现。此前项目布局、导入、启动、外部文档、迁移和 AI 设置检查通过。其后单独运行 box-tag、projects、i18n、math-input、multiline-display-math、formula-rendering、code-rendering、rich-editor、continuous、ai-workspace、trackpad、workflows，均通过。未将完整套件记为通过。

Mac 本轮实机、Android 原生执行、生产安装包、长时间 CPU／内存／显存、全部触摸与窄屏体验未验收。已接入 Windows/Mac CI，但结果必须以新提交 Actions 为准，不能引用旧提交的绿色状态。

HTML 导入仅识别已实现的遗传算法教学模板，并映射到内置模型；不保留任意网页的布局、脚本、外部依赖或未支持模块。没有宣称完整复刻参考的全部十章、逐像素视觉或所有长期使用场景。

## 查看证据

- [逐模块截图与视觉差距](VISUAL-REVIEW.zh-CN.md)
- [笔记截图来源](../windows/guide/images/markdown-native/provenance.json)
- [源文档截图来源](../windows/guide/images/markdown-native-source/provenance.json)
- [原生格式及保存架构](NATIVE-MARKDOWN.zh-CN.md)
- [截图运行源码指纹](NATIVE-RUNTIME-SOURCES.json)

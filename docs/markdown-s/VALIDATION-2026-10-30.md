# 2026-10-30 Markdown S 开发验证记录

范围：Windows 开发源码、真实 Electron 运行及注入当前源码的独立测试宿主；不是生产安装包或全部平台验收。指导文件已由发起人确认采用。PR #17 保持开发状态。

补充实测：`PAGENOTE_GUIDE_MARKDOWN=examples/markdown-s.md` 与 `PAGENOTE_VERIFY_PERSISTENCE=1` 运行采集脚本，真实编辑组件、flush 后直接读取项目 `src/notes.md`，随后关闭并重新启动 Electron；源码标记及其他引擎保留、预览默认不执行均通过。专项还验证了删除运行中卡片后，脱离文档的节点内 iframe 被移除，运行状态释放。后台自动停止已接线，尚未以真实系统切后台覆盖验收。

后续窗口实测发现 Windows 最小化并不总改变 `document.hidden`。已新增宿主 minimize/hide 通知作为补充，并验证真实最小化后 iframe 移除、恢复后可手动重跑；此前“后台未实测”的记录由此补充，不推广为所有后台／休眠场景通过。

| 对应验收 | 本轮证据 | 状态与范围 |
| --- | --- | --- |
| F01 | 生产构建；formula-rendering、code-rendering、rich-editor 脚本 | 已通过受测公式／代码／富文本场景，不代表所有历史笔记 |
| E02、E03 | markdown-s 专项源码往返、修改、撤销、重做 | 已通过测试宿主；实际项目关闭重开仍待专项覆盖 |
| R01–R04、R06 | HTML 点击、SVG、Canvas 像素、图表、Three.js WebGL 立方体及 readPixels | 已通过代表性样本；复杂模型、GPU 故障降级未完成 |
| S01 | 组件访问父 DOM 被拒绝 | 已通过，不等于全部宿主权限审计 |
| S03、S04 | CSP 拒绝 fetch，修改 frame name 后自导航被宿主拦截，目标服务请求计数为零 | 已通过受测请求／导航，不代表全部外传渠道均审计完成 |
| L01–L03 | 显式停止、替换笔记、双预览上限 | 已通过；后台／不可见自动治理、全部删除路径和长时间内存采样待完成 |
| 内核阶段一 | render-scope 与 render-queue 测试 | ABA 旧代失效、出队取消、优先级、超时保持互斥及失败后恢复通过；全任务 scope 和性能轨迹尚未完成 |
| 视觉 | Windows guide/markdown-s.md 与图片 provenance | 已采集并检查交互、图表、3D 画面；窄窗、深色、缩放与人工最终美学审查待完成 |

命令：`npm run build`、`node tests/render-scope.mjs`、`node tests/render-queue.mjs`、`npm run test:markdown-s`、`node tests/formula-rendering.mjs`、`node tests/code-rendering.mjs`、`node tests/rich-editor.mjs`。具体测试断言以对应脚本为准。CI 新增同一专项入口，后续以实际运行结果填写平台证据。

本地依赖缺失曾导致构建失败，恢复 lockfile 对应 lucide 后构建通过。未使用外部依赖替代、跳过图标或关闭沙盒绕过失败。

云端结果：提交 `302ddeb` 的 [Actions 40926253759](https://github.com/laihong06/pagenote/actions/runs/40926253759) 平台元数据、Windows、macOS、Android 四项全部通过；桌面包括 Markdown S 专项和完整回归，Android 为现有应用单测与编译，不代表 Android 已支持增强执行。后续提交必须查看自己的 CI，不能复用此结论。

本地最新源码已生产构建，并通过 `npm run demo:markdown-s` 打开独立真实项目；命令行启动验证通过。完整 `npm test` 在本轮生产构建后通过。演示入口的临时项目保留在仓库 tmp 内，不覆盖用户笔记。

仍需完成：Markdown S 更完整权限与故障降级样本、不可见区域与长期内存治理、长文档交互性能基线、Android 源码保真、安装包内运行和完整视觉验收。实际项目保存重开、移除卡片和 Windows 最小化生命周期已有上述证据。未执行项保持未执行，不晋级 stable、不宣称完成全框架重构。

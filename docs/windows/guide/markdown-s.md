# Markdown S 原生交互与保存

这是开发分支功能，公开 beta.4 安装包尚不包含。本轮截图和差距分析见 [逐模块审查](../../markdown-s/VISUAL-REVIEW.zh-CN.md)，数据格式见 [原生规范](../../markdown-s/NATIVE-MARKDOWN.zh-CN.md)。

1. 在开发目录运行 `npm run demo:markdown-s`，打开独立原生示例项目。
2. 直接操作基因、采样滑块、种群、轮盘、交叉、变异概率和代际图表，不需要点“运行 HTML”。
3. 三维染色体也由 PageNote 直接绘制：拖动、点击基因或暂停／继续。
4. 修改自动进入当前 Markdown。等待“已保存到本地”，或使用软件原有保存操作；无需另存 HTML。
5. 关闭并重新打开项目，组件参数和状态继续保留。可展开“Markdown 数据”查看当前持久化数据。
6. 通过原有“导入笔记”选择提供的遗传算法实验室 HTML，可转换为原生模板；自定义网页尚不支持通用转换。

示例：[原生 Markdown](../../../../examples/markdown-s-native.md)。所有示例和临时配置位于仓库 tmp 内，不覆盖已有笔记。

![原生 SVG](images/markdown-native/native-svg.png)

![原生 Canvas](images/markdown-native/native-canvas.png)

![原生三维染色体](images/markdown-native/native-three.png)

旧 [HTML 沙盒截图](images/markdown-s/provenance.json) 只是历史原型记录，不能作为原生交付证据。

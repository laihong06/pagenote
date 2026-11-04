import { webview } from '@kit.ArkWeb';

/**
 * 公式渲染：Web 组件加载 rawfile 里的本地 KaTeX（math.html + katex/，不依赖网络）。
 * displayMode=true 块级（占满宽度，按内容定高）；false 行内（按内容定宽高，混入文字行）。
 * 页面加载完成后把 LaTeX 传给 window.renderMath，用其返回的内容尺寸调整组件。
 */
@Component
export struct MathView {
  latex: string = '';
  displayMode: boolean = true;
  // 行内模式初始宽度不能为 0（零尺寸组件不创建 Web 内容），渲染后按内容修正
  @State private viewWidth: number = 60;
  @State private viewHeight: number = 48;
  private controller: webview.WebviewController = new webview.WebviewController();

  private render(): void {
    const script = `renderMath(${JSON.stringify(this.latex)}, ${this.displayMode})`;
    this.controller.runJavaScript(script)
      .then((result: string) => {
        // renderMath 返回 "宽,高"；runJavaScript 可能给字符串再加一层引号，先去引号
        const raw = result.replace(/^"|"$/g, '');
        const parts = raw.split(',');
        const cssWidth = Number(parts[0]);
        const cssHeight = Number(parts[1]);
        // Web 内 CSS px 相对组件 vp，无需再做 px2vp 换算
        if (!Number.isNaN(cssHeight) && cssHeight > 0) {
          this.viewHeight = cssHeight + (this.displayMode ? 8 : 2);
        }
        if (!this.displayMode && !Number.isNaN(cssWidth) && cssWidth > 0) {
          this.viewWidth = cssWidth + 4;
        }
      })
      .catch(() => {
        // 页面尚未就绪或脚本失败时保持默认高度
      });
  }

  build() {
    Web({ src: $rawfile('math.html'), controller: this.controller })
      .width(this.displayMode ? '100%' : this.viewWidth)
      .height(this.viewHeight)
      .backgroundColor(Color.Transparent)
      .overScrollMode(OverScrollMode.NEVER)
      .onPageEnd(() => {
        this.render();
      })
  }
}

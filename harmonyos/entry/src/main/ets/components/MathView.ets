import { webview } from '@kit.ArkWeb';

/**
 * 块级公式渲染：Web 组件加载 rawfile 里的本地 KaTeX（math.html + katex/，不依赖网络）。
 * 页面加载完成后把 LaTeX 传给 window.renderMath，用其返回的内容高度调整组件高度。
 */
@Component
export struct MathView {
  latex: string = '';
  @State private viewHeight: number = 48;
  private controller: webview.WebviewController = new webview.WebviewController();

  private render(): void {
    const script = `renderMath(${JSON.stringify(this.latex)})`;
    this.controller.runJavaScript(script)
      .then((result: string) => {
        const cssHeight = Number(result);
        if (!Number.isNaN(cssHeight) && cssHeight > 0) {
          this.viewHeight = this.getUIContext().px2vp(cssHeight) + 8;
        }
      })
      .catch(() => {
        // 页面尚未就绪或脚本失败时保持默认高度
      });
  }

  build() {
    Web({ src: $rawfile('math.html'), controller: this.controller })
      .width('100%')
      .height(this.viewHeight)
      .backgroundColor(Color.Transparent)
      .overScrollMode(OverScrollMode.NEVER)
      .onPageEnd(() => {
        this.render();
      })
  }
}

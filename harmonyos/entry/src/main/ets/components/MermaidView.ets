import { webview } from '@kit.ArkWeb';

/**
 * Mermaid 图渲染：Web 组件加载 rawfile 本地 mermaid（mermaid.html + mermaid/，不依赖网络）。
 * 渲染完成后按内容高度调整组件（Web 内 CSS px 等同组件 vp，不做 px2vp 换算）。
 */
@Component
export struct MermaidView {
  source: string = '';
  @State private viewHeight: number = 120;
  private controller: webview.WebviewController = new webview.WebviewController();

  private render(): void {
    // renderDiagram 是异步的（mermaid.render 返回 Promise，runJavaScript 不等 Promise）：
    // 启动渲染后轮询 window.__mmdResult 取内容高度
    const script = `renderDiagram(${JSON.stringify(this.source)})`;
    this.controller.runJavaScript(script).catch(() => {});
    this.pollResult(0);
  }

  private pollResult(attempt: number): void {
    setTimeout(() => {
      this.controller.runJavaScript('window.__mmdResult||""')
        .then((result: string) => {
          const raw = result.replace(/^"|"$/g, '');
          const parts = raw.split(',');
          const cssHeight = Number(parts[1]);
          if (parts.length === 2 && !Number.isNaN(cssHeight) && cssHeight > 0) {
            this.viewHeight = cssHeight + 8;
          } else if (attempt < 4) {
            this.pollResult(attempt + 1);
          }
        })
        .catch(() => {
          if (attempt < 4) {
            this.pollResult(attempt + 1);
          }
        });
    }, attempt === 0 ? 300 : 500);
  }

  build() {
    Web({ src: $rawfile('mermaid.html'), controller: this.controller })
      .width('100%')
      .height(this.viewHeight)
      .backgroundColor(Color.Transparent)
      .overScrollMode(OverScrollMode.NEVER)
      .onPageEnd(() => {
        this.render();
      })
  }
}

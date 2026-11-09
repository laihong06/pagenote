import { PdfView, pdfViewManager, pdfService } from '@kit.PDFKit';
import { PdfBridge } from '../core/PdfBridge';

/**
 * PDF 阅读面板：唯一静态 import @kit.PDFKit 的模块。
 * 部分设备/模拟器系统镜像不带 PDF Kit 原生库（libpdfview.z.so），
 * 因此 ReaderPage 只在 canIUse 检测通过后动态 import 本模块；
 * 本模块被加载即代表 PDF Kit 可用，在这里创建真实 PdfController 并回填 bridge。
 */
@Component
export struct PdfPane {
  bridge: PdfBridge = { controller: null, continuous: false };

  aboutToAppear(): void {
    if (this.bridge.controller === null) {
      this.bridge.controller = new pdfViewManager.PdfController();
    }
  }

  build() {
    PdfView({
      controller: this.bridge.controller as pdfViewManager.PdfController,
      pageFit: pdfService.PageFit.FIT_WIDTH,
      pageLayout: pdfService.PageLayout.LAYOUT_SINGLE,
      isContinuous: this.bridge.continuous,
      showScroll: true
    })
      .width('100%')
      .height('100%')
  }
}

@Builder
function buildPdfPane(bridge: PdfBridge) {
  PdfPane({ bridge: bridge })
    .width('100%')
    .height('100%')
}

// wrapBuilder 的参数必须是本模块静态声明的 @Builder（编译器限制 10905109），
// 因此在模块作用域包好再导出，ReaderPage 动态 import 后直接使用。
export const pdfPaneWrappedBuilder: WrappedBuilder<[PdfBridge]> =
  wrapBuilder<[PdfBridge]>(buildPdfPane);

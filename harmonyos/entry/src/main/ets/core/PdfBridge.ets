import type { pdfViewManager } from '@kit.PDFKit';

/**
 * PDF 控制器桥接：ReaderPage 与 PdfPane 之间的共享类型。
 * 只使用 import type（编译期擦除，运行时不会加载 @kit.PDFKit），
 * 因此 ReaderPage 在缺失 PDF Kit 原生库的设备上仍可正常打开；
 * 真实 PdfController 只在 PdfPane（动态 import 加载）中创建并回填。
 */
export interface PdfBridge {
  controller: pdfViewManager.PdfController | null;
}

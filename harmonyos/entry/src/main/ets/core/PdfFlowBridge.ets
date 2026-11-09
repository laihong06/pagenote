/**
 * 连续滚动 PDF 桥接：ReaderPage 与 PdfFlowPane 之间的共享类型。
 * 不 import @kit.PDFKit（编译期擦除，运行时零 PDF Kit 依赖），
 * ReaderPage 只在 canIUse 检测通过后动态 import PdfFlowPane 并挂载本桥。
 *
 * 双向同步约定：
 * - Pane → ReaderPage：onDocumentReady / onVisiblePage（Pane 内部抑制程序化滚动期间的回报，防回跳）；
 * - ReaderPage → Pane：scrollToPage（Pane 挂载后回填真正实现，挂载前为记账存根）。
 *   requestedPage 为跨挂载记账：Pane 每次挂载完成文档打开后读取并消费（>0 时滚到该页，然后清零），
 *   因此 ReaderPage 在 Pane 未挂载时调用 scrollToPage 也不会丢请求。
 */
export interface PdfFlowBridge {
  /** PDF 沙箱路径（Pane 用 pdfService.PdfDocument 自行打开，与 PdfView 的 controller 相互独立） */
  docPath: string;
  /** ReaderPage 记账的目标页（1-based，0=无）；Pane 挂载就绪后消费并清零 */
  requestedPage: number;
  /** 文档打开完成：pageCount<=0 且 error 非空表示打开失败 */
  onDocumentReady: (pageCount: number, error: string) => void;
  /** 可见页变化回报（1-based） */
  onVisiblePage: (page: number) => void;
  /** 滚到指定页（Pane aboutToAppear 回填；挂载前为存根，只确保 requestedPage 已记账） */
  scrollToPage: (page: number) => void;
}

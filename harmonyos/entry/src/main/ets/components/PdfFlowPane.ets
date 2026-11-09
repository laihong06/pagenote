import { image } from '@kit.ImageKit';
import { pdfService } from '@kit.PDFKit';
import { PdfFlowBridge } from '../core/PdfFlowBridge';

/**
 * PDF 连续滚动面板（scroll 翻页模式专用）：pdfService 逐页渲染 PixelMap，
 * 垂直 Scroll 按真实宽高比堆叠，上一页底部与下一页顶部同屏衔接。
 * 模块被加载即代表 PDF Kit 可用（ReaderPage 动态 import 本模块后才挂载）。
 *
 * 渲染与内存：只缓存可视页前后各 CACHE_RADIUS 页的 PixelMap，窗口外立即 release()；
 * 每帧最多渲染一页（2720px 级位图同步渲染较耗时），未就绪页按真实宽高比占位防跳动。
 */

/** 单页原始几何（pt）；打开文档时已按 90/270 旋转换算宽高。 */
interface FlowPageGeom {
  wPt: number;
  hPt: number;
}

const PAGE_GAP_VP: number = 12;
const EDGE_PAD_VP: number = 16;
const CACHE_RADIUS: number = 3;
const RENDER_TICK_MS: number = 16;
const SCROLL_ANIM_MS: number = 400;
/** 程序化滚动期间的可见页回报抑制时长（ms），防跳页动画经过中间页时笔记来回切换 */
const SUPPRESS_MS: number = 700;
const MAX_BITMAP_WIDTH_PX: number = 4096;
/** 渲染清晰度：栏宽（px）的倍数 */
const BITMAP_SCALE: number = 2;

function noopScrollToPage(page: number): void {
  if (page < 0) {
    return;
  }
}

@Component
export struct PdfFlowPane {
  bridge: PdfFlowBridge = {
    docPath: '',
    requestedPage: 0,
    onDocumentReady: (pageCount: number, error: string): void => {},
    onVisiblePage: (page: number): void => {},
    scrollToPage: noopScrollToPage
  };

  @State pageCount: number = 0;
  @State pageGeoms: FlowPageGeom[] = [];
  @State pageIndices: number[] = [];
  @State paneWidthVp: number = 0;
  @State bitmapVersion: number = 0;
  @State errorText: string = '';

  private scroller: Scroller = new Scroller();
  private document: pdfService.PdfDocument | null = null;
  private bitmaps: Map<number, image.PixelMap> = new Map<number, image.PixelMap>();
  private renderQueued: Set<number> = new Set<number>();
  private rendering: boolean = false;
  private disposed: boolean = false;
  /** 各页顶部累计偏移（vp，含首尾留白），与 onDidScroll 的 yOffset 同单位 */
  private topsVp: number[] = [];
  /** 最近一次回报给 ReaderPage 的可见页（1-based） */
  private lastReportedPage: number = 1;
  /** 程序化滚动回报抑制截止（ms） */
  private suppressReportUntil: number = 0;
  /** 文档/栏宽未就绪时暂存的目标页（1-based，0=无） */
  private pendingScrollPage: number = 0;
  /** 栏宽就绪后的初始定位与首窗渲染只执行一次 */
  private windowInitialized: boolean = false;

  aboutToAppear(): void {
    this.bridge.scrollToPage = (page: number): void => {
      this.requestScrollTo(page);
    };
    if (this.bridge.docPath.length === 0) {
      this.errorText = 'PDF 路径为空';
      this.bridge.onDocumentReady(0, this.errorText);
      return;
    }
    try {
      const doc: pdfService.PdfDocument = new pdfService.PdfDocument();
      const result: pdfService.ParseResult = doc.loadDocument(this.bridge.docPath);
      if (result !== pdfService.ParseResult.PARSE_SUCCESS) {
        doc.releaseDocument();
        this.errorText = `PDF 打开失败（解析错误 ${result}）`;
        this.bridge.onDocumentReady(0, this.errorText);
        return;
      }
      this.document = doc;
      const n: number = doc.getPageCount();
      const geoms: FlowPageGeom[] = [];
      for (let i: number = 0; i < n; i++) {
        const page: pdfService.PdfPage = doc.getPage(i);
        let w: number = page.getWidth();
        let h: number = page.getHeight();
        const rot: pdfService.RotationAngle = page.getRotation();
        if (rot === pdfService.RotationAngle.ANGLE_90 || rot === pdfService.RotationAngle.ANGLE_270) {
          const t: number = w;
          w = h;
          h = t;
        }
        page.release();
        geoms.push({ wPt: w > 0 ? w : 1, hPt: h > 0 ? h : 1 });
      }
      this.pageGeoms = geoms;
      this.pageCount = n;
      const indices: number[] = [];
      for (let i: number = 0; i < n; i++) {
        indices.push(i);
      }
      this.pageIndices = indices;
      this.bridge.onDocumentReady(n, '');
      // 消费 ReaderPage 在挂载前记下的跳页请求
      let target: number = 1;
      if (this.bridge.requestedPage >= 1) {
        target = Math.min(this.bridge.requestedPage, Math.max(1, n));
        this.bridge.requestedPage = 0;
      }
      this.pendingScrollPage = target;
      this.lastReportedPage = target;
    } catch (e) {
      this.errorText = `PDF 打开失败：${(e as Error).message}`;
      this.bridge.onDocumentReady(0, this.errorText);
    }
  }

  aboutToDisappear(): void {
    this.disposed = true;
    this.bitmaps.forEach((pm: image.PixelMap) => {
      try {
        pm.release();
      } catch (e) {
        // 已释放的像素图忽略
      }
    });
    this.bitmaps.clear();
    this.renderQueued.clear();
    if (this.document !== null) {
      try {
        this.document.releaseDocument();
      } catch (e) {
        // 已释放/未加载时不阻断退出
      }
      this.document = null;
    }
    // 防止 ReaderPage 继续调用指向已销毁实例的回调
    this.bridge.scrollToPage = noopScrollToPage;
  }

  /** ReaderPage 跳页入口：文档或栏宽未就绪时暂存，就绪后由宽度回调消费。 */
  private requestScrollTo(page: number): void {
    this.pendingScrollPage = Math.min(Math.max(1, page), Math.max(1, this.pageCount));
    if (this.document === null || this.paneWidthVp <= 0) {
      return;
    }
    const target: number = this.pendingScrollPage;
    this.pendingScrollPage = 0;
    this.suppressReportUntil = Date.now() + SUPPRESS_MS;
    this.scroller.scrollTo({
      xOffset: 0,
      yOffset: this.pageTopVp(target - 1),
      animation: { duration: SCROLL_ANIM_MS }
    });
    this.ensureWindow(target - 1);
  }

  private recomputeTops(): void {
    this.topsVp = [];
    let top: number = EDGE_PAD_VP;
    for (let i: number = 0; i < this.pageGeoms.length; i++) {
      this.topsVp.push(top);
      top += this.pageHeightVp(i) + PAGE_GAP_VP;
    }
  }

  private pageHeightVp(idx: number): number {
    const geom: FlowPageGeom | undefined = this.pageGeoms[idx];
    if (geom === undefined || this.paneWidthVp <= 0) {
      return 0;
    }
    return this.paneWidthVp * geom.hPt / geom.wPt;
  }

  private pageTopVp(idx: number): number {
    const top: number | undefined = this.topsVp[idx];
    return top === undefined ? 0 : top;
  }

  /** 偏移（vp）所在页（0-based）：最后一个顶部不超过偏移的页，页间隙归属上一页。 */
  private pageIndexAtOffset(yOffsetVp: number): number {
    let idx: number = 0;
    for (let i: number = 0; i < this.topsVp.length; i++) {
      if (this.topsVp[i] <= yOffsetVp + 0.5) {
        idx = i;
      } else {
        break;
      }
    }
    return idx;
  }

  private hasBitmap(idx: number): boolean {
    // 读取 @State 版本号，使像素图就绪时能触发 Builder 重渲染
    const version: number = this.bitmapVersion;
    if (version < 0) {
      return false;
    }
    return this.bitmaps.has(idx);
  }

  private bitmapOf(idx: number): image.PixelMap {
    const pm: image.PixelMap | undefined = this.bitmaps.get(idx);
    if (pm === undefined) {
      throw new Error(`page ${idx} bitmap missing`);
    }
    return pm;
  }

  /** 维护渲染窗口：可视页前后各 CACHE_RADIUS 页保留像素图，窗口外立即释放。 */
  private ensureWindow(centerIdx: number): void {
    if (this.document === null || this.paneWidthVp <= 0 || this.pageCount === 0) {
      return;
    }
    const lo: number = Math.max(0, centerIdx - CACHE_RADIUS);
    const hi: number = Math.min(this.pageCount - 1, centerIdx + CACHE_RADIUS);
    const stale: number[] = [];
    this.bitmaps.forEach((_pm: image.PixelMap, idx: number) => {
      if (idx < lo || idx > hi) {
        stale.push(idx);
      }
    });
    for (const idx of stale) {
      const pm: image.PixelMap | undefined = this.bitmaps.get(idx);
      if (pm !== undefined) {
        try {
          pm.release();
        } catch (e) {
          // 已释放的像素图忽略
        }
      }
      this.bitmaps.delete(idx);
    }
    if (stale.length > 0) {
      this.bitmapVersion += 1;
    }
    for (let i: number = lo; i <= hi; i++) {
      if (!this.bitmaps.has(i)) {
        this.renderQueued.add(i);
      }
    }
    this.pumpRenderQueue();
  }

  /** 每次只渲染一页，让出帧预算；优先渲染离可视中心最近的页。 */
  private pumpRenderQueue(): void {
    if (this.rendering || this.disposed || this.renderQueued.size === 0) {
      return;
    }
    const center: number = this.lastReportedPage - 1;
    let best: number = -1;
    let bestDist: number = Number.MAX_VALUE;
    this.renderQueued.forEach((idx: number) => {
      const dist: number = Math.abs(idx - center);
      if (dist < bestDist) {
        bestDist = dist;
        best = idx;
      }
    });
    if (best < 0) {
      return;
    }
    this.renderQueued.delete(best);
    this.rendering = true;
    setTimeout(() => {
      if (this.disposed) {
        this.rendering = false;
        return;
      }
      try {
        this.renderPage(best);
      } catch (e) {
        // 单页渲染失败（如像素数超限）不阻断其余页
      }
      this.rendering = false;
      this.bitmapVersion += 1;
      this.pumpRenderQueue();
    }, RENDER_TICK_MS);
  }

  private renderPage(idx: number): void {
    const doc: pdfService.PdfDocument | null = this.document;
    const geom: FlowPageGeom | undefined = this.pageGeoms[idx];
    if (doc === null || geom === undefined || this.bitmaps.has(idx) || this.paneWidthVp <= 0) {
      return;
    }
    const page: pdfService.PdfPage = doc.getPage(idx);
    try {
      const matrix: pdfService.PdfMatrix = new pdfService.PdfMatrix();
      // matrix 的 x/y/width/height 是 PDF 页面坐标系（pt）中的源矩形：
      // 全页渲染 = 源矩形取整页，输出位图按栏宽 2x 等比
      matrix.x = 0;
      matrix.y = 0;
      matrix.width = geom.wPt;
      matrix.height = geom.hPt;
      matrix.rotate = 0;
      const pxW: number =
        Math.min(MAX_BITMAP_WIDTH_PX, Math.max(1, Math.round(vp2px(this.paneWidthVp) * BITMAP_SCALE)));
      const pxH: number = Math.max(1, Math.round(pxW * geom.hPt / geom.wPt));
      const pm: image.PixelMap | null = page.getAreaPixelMap(matrix, pxW, pxH, false, true);
      if (pm !== null) {
        this.bitmaps.set(idx, pm);
      }
    } finally {
      page.release();
    }
  }

  /** 栏宽/文档就绪后的初始定位与首窗渲染（只执行一次）。 */
  private initWindowOnce(): void {
    if (this.windowInitialized || this.document === null || this.paneWidthVp <= 0 || this.pageCount === 0) {
      return;
    }
    this.windowInitialized = true;
    this.recomputeTops();
    const target: number =
      this.pendingScrollPage >= 1 ? Math.min(this.pendingScrollPage, this.pageCount) : this.lastReportedPage;
    this.pendingScrollPage = 0;
    this.ensureWindow(target - 1);
    if (target <= 1) {
      return;
    }
    // 等宽度变化触发的重排完成后再定位，否则 scrollTo 会被旧（零高度）内容钳制在顶部
    setTimeout(() => {
      if (this.disposed) {
        return;
      }
      this.suppressReportUntil = Date.now() + SUPPRESS_MS;
      this.scroller.scrollTo({ xOffset: 0, yOffset: this.pageTopVp(target - 1), animation: false });
    }, 30);
  }

  build() {
    Column() {
      if (this.errorText.length > 0) {
        Column() {
          Text(this.errorText)
            .fontSize(14)
            .fontColor('#999999')
            .padding(24)
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else if (this.pageCount === 0) {
        Column() {
          Text('正在打开 PDF…')
            .fontSize(14)
            .fontColor('#999999')
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else {
        Scroll(this.scroller) {
          Column({ space: PAGE_GAP_VP }) {
            ForEach(this.pageIndices, (idx: number) => {
              this.pageItem(idx)
            }, (idx: number) => `${idx}`)
          }
          .padding({ top: EDGE_PAD_VP, bottom: EDGE_PAD_VP })
          .width('100%')
        }
        .width('100%')
        .height('100%')
        .onDidScroll((xOffset: number, yOffset: number, scrollState: ScrollState) => {
          // onDidScroll 的 yOffset 是每帧增量（非累计），当前页定位必须用累计偏移
          const offset: OffsetResult = this.scroller.currentOffset();
          const idx: number = this.pageIndexAtOffset(offset.yOffset);
          this.ensureWindow(idx);
          const page: number = idx + 1;
          if (page === this.lastReportedPage) {
            return;
          }
          if (Date.now() < this.suppressReportUntil) {
            return;
          }
          this.lastReportedPage = page;
          this.bridge.onVisiblePage(page);
        })
      }
    }
    .width('100%')
    .height('100%')
    .onAreaChange((oldValue: Area, newValue: Area) => {
      const wVp: number = px2vp(Number(newValue.width));
      if (Math.abs(wVp - this.paneWidthVp) > 0.5) {
        this.paneWidthVp = wVp;
        this.recomputeTops();
        this.initWindowOnce();
      }
    })
  }

  @Builder
  pageItem(idx: number) {
    Column() {
      if (this.hasBitmap(idx)) {
        Image(this.bitmapOf(idx))
          .width('100%')
          .height('100%')
          .objectFit(ImageFit.Fill)
          .draggable(false)
      } else {
        // 占位：按真实宽高比预留高度，像素图就绪后不跳动
        Text(`${idx + 1}`)
          .fontSize(14)
          .fontColor('#BBBBBB')
      }
    }
    .width('100%')
    .height(this.pageHeightVp(idx))
    .backgroundColor('#FFFFFF')
    .justifyContent(FlexAlign.Center)
  }
}

@Builder
function buildPdfFlowPane(bridge: PdfFlowBridge) {
  PdfFlowPane({ bridge: bridge })
    .width('100%')
    .height('100%')
}

// wrapBuilder 的参数必须是本模块静态声明的 @Builder（编译器限制 10905109），
// 因此在模块作用域包好再导出，ReaderPage 动态 import 后直接使用。
export const pdfFlowPaneWrappedBuilder: WrappedBuilder<[PdfFlowBridge]> =
  wrapBuilder<[PdfFlowBridge]>(buildPdfFlowPane);

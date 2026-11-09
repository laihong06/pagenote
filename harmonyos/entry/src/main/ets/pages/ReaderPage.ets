import { common } from '@kit.AbilityKit';
import { preferences } from '@kit.ArkData';
import { componentSnapshot } from '@kit.ArkUI';
import { fileIo as fs, picker } from '@kit.CoreFileKit';
import { image } from '@kit.ImageKit';
import { photoAccessHelper } from '@kit.MediaLibraryKit';
import { ProjectStore, OpenProject } from '../core/ProjectStore';
import { PdfBridge } from '../core/PdfBridge';
import { PageNotes } from '../core/PageNotes';
import { ProjectValidation } from '../core/ProjectManifest';
import { NoteHistory, NoteVersion } from '../core/NoteHistory';
import { AgentSettings, ModelConfig, PageTutor, ModelEndpoint, SelectionActions, SelectionAction } from '../core/Agent';
import { Annotations, AnnotationBook, AnnotationItem } from '../core/Annotations';
import { FlashcardItem, FlashcardStore, FlashcardCapture, FlashcardDraft, ReviewOutcome } from '../core/Flashcards';
import { NoteTransfer, NoteTransferFile } from '../core/NoteTransfer';
import { MarkdownView } from '../components/MarkdownView';
import { InkCanvas } from '../components/InkCanvas';
import { cnTypographyAvailable, CnTextAreaTypography } from '../components/CnTypography';
import { IconButton } from '../components/IconButton';
import { UiTheme } from '../components/UiTheme';

interface ReaderParams {
  dir: string;
}

interface SearchHit {
  page: number;
  snippet: string;
  source: string; // 笔记 | 原文
}

/** 雨课堂来源清单中的单页：项目页码 + 页面图片路径。 */
interface RainPageAsset {
  page: number;
  asset: string;
}

interface InkToolSpec {
  id: string;
  label: string;
  symbol: Resource;
}

const INK_TOOLS: InkToolSpec[] = [
  { id: 'pen', label: '笔', symbol: $r('sys.symbol.pencil_tip') },
  { id: 'highlight', label: '荧光', symbol: $r('sys.symbol.highlighter_tip') },
  { id: 'line', label: '直线', symbol: $r('sys.symbol.minus') },
  { id: 'rect', label: '方框', symbol: $r('sys.symbol.square') },
  { id: 'ellipse', label: '椭圆', symbol: $r('sys.symbol.circle') },
  { id: 'text', label: '文字', symbol: $r('sys.symbol.character_textbox') },
  { id: 'eraser', label: '橡皮擦（压感）', symbol: $r('sys.symbol.eraser_line_fill') },
  { id: 'lasso', label: '套索', symbol: $r('sys.symbol.selector') }
];

interface InkWidthSpec {
  value: number;
  label: string;
}

const INK_WIDTHS: InkWidthSpec[] = [
  { value: 2, label: '细' },
  { value: 4, label: '中' },
  { value: 7, label: '粗' }
];

// 手写转文字：InkCanvas 快照组件 id（componentSnapshot.get 按 id 捕获）
const INK_SNAPSHOT_ID: string = 'inkCanvasDoc';

/**
 * 阅读页：左侧源文档（PDF 走 PdfView，Markdown 直接显示），右侧逐页 Markdown 笔记。
 * 笔记按 `<!-- pagebreak -->` 分页；保存时带 revision 校验并写历史快照；
 * 支持基础 Markdown 预览、历史恢复与按页 AI 讲解（OpenAI 兼容接口，配置存应用偏好）。
 */
@Entry
@Component
struct ReaderPage {
  @State projectName: string = '';
  @State isPdf: boolean = false;
  @State pageCount: number = 1;
  @State currentPage: number = 1;
  @State note: string = '';
  @State sourceMarkdown: string = '';
  @State statusText: string = '';
  @State dirty: boolean = false;
  @State previewMode: boolean = false;
  @State showHistory: boolean = false;
  @State historyList: NoteVersion[] = [];
  @State showSearch: boolean = false;
  @State searchQuery: string = '';
  @State searchResults: SearchHit[] = [];
  @State showAiSettings: boolean = false;
  @State aiBaseUrl: string = '';
  @State aiApiKey: string = '';
  @State aiModel: string = '';
  @State showAiResult: boolean = false;
  @State aiResult: string = '';
  @State aiBusy: boolean = false;
  @State inkMode: boolean = false;
  @State inkColor: string = '#dc3545';
  @State inkItems: AnnotationItem[] = [];
  @State inkTool: string = 'pen'; // pen | highlight | line | rect | ellipse | text | eraser
  // 手锁：开启后手指/鼠标只能拖动平移版面，手写笔照常书写
  @State fingerLock: boolean = false;
  // 手锁拖动/滑动翻页的版面偏移（px）
  @State panX: number = 0;
  @State panY: number = 0;
  // 套索选中的笔迹索引（删除所选用）；切换工具/撤销/清除时递增 selectionEpoch 让画布清选区
  @State selectedIndices: number[] = [];
  @State selectionEpoch: number = 0;
  // 翻页模式：tap=点击翻页（单页+批注可用）；scroll=滑动翻页（跟手纵拖过阈值翻页，批注自动禁用）
  @State pageTurnMode: string = 'tap';
  // 手写转文字：OCR Kit 一次性可用性探测结果（@hms 模块在低版本设备可能不存在，只能动态 import 探测）
  @State ocrReady: boolean = false;
  @State inkWidth: number = 3;
  @State inkTextEditing: boolean = false;
  @State inkTextDraft: string = '';
  // 夜览滤镜：阅读区暖色遮罩（对齐桌面/Android 夜览语义，遮罩不拦截触摸）
  @State nightMode: boolean = false;
  // 横屏左右分栏、竖屏上下分栏（平板旋转/自由窗口）
  @State isLandscape: boolean = true;
  private inkTextPoint: number[] = [0, 0];

  // 闪卡
  @State showCards: boolean = false;
  @State cardTab: string = 'make'; // make | review | lib
  @State cards: FlashcardItem[] = [];
  @State cardFront: string = '';
  @State cardBack: string = '';
  @State cardTags: string = '';
  @State reviewQueue: FlashcardItem[] = [];
  @State reviewIndex: number = 0;
  @State reviewShowBack: boolean = false;
  private selStart: number = 0;
  private selEnd: number = 0;

  // 选区 AI 动作
  @State showAiAction: boolean = false;
  @State aiActionBusy: boolean = false;
  @State aiActionResult: string = '';
  @State aiActionError: string = '';

  // 导出/导入
  @State showTransfer: boolean = false;

  private annotations: AnnotationBook = Annotations.empty();

  // 系统深浅色模式（EntryAbility 用 media query (dark-mode) 注册并监听切换）
  @StorageProp('isDarkMode') darkMode: boolean = false;

  private isDarkMode(): boolean {
    return this.darkMode;
  }

  // API 26 中文排版（避孤字/标点悬挂）：仅确认可用时才挂载修饰器
  private cnTypographyMod: AttributeModifier<TextAreaAttribute> | undefined =
    cnTypographyAvailable() ? new CnTextAreaTypography() : undefined;

  @State pdfPaneBuilder: WrappedBuilder<[PdfBridge]> | null = null;
  // PDF Kit 缺失时，雨课堂项目用来源图片降级显示（页序 = yuketang-source.json 的 projectPage）
  @State rainPageImages: string[] = [];

  private project: OpenProject | null = null;
  private noteRevision: string | null = null;
  // PDF Kit 只在 PdfPane（动态 import）里实例化，缺失 PDF Kit 的设备上本页仍可打开 Markdown 项目
  private pdfBridge: PdfBridge = { controller: null, continuous: false };
  private pdfLoaded: boolean = false;

  aboutToAppear(): void {
    this.loadTurnMode();
    const params = this.getUIContext().getRouter().getParams() as ReaderParams;
    if (params === null || params === undefined || params.dir === undefined) {
      this.statusText = '缺少项目参数';
      return;
    }
    try {
      this.project = ProjectStore.open(params.dir);
    } catch (e) {
      this.statusText = `打开项目失败：${(e as Error).message}`;
      return;
    }
    this.projectName = this.project.manifest.name;
    this.isPdf = ProjectValidation.kindOf(this.project.manifest.left) === 'pdf';
    try {
      this.annotations = Annotations.load(`${this.project.dir}/src/annotations.json`);
    } catch (e) {
      this.statusText = `批注数据损坏：${(e as Error).message}`;
    }
    const saved = AgentSettings.load(this.getUIContext().getHostContext() as common.UIAbilityContext);
    this.aiBaseUrl = saved.baseUrl;
    this.aiApiKey = saved.apiKey;
    this.aiModel = saved.model;
    if (this.isPdf) {
      this.initPdf(this.loadRainFallback());
    } else {
      this.loadMarkdownSource();
    }
    this.loadPage(1);
    this.probeOcrReady();
  }

  aboutToDisappear(): void {
    if (this.pdfLoaded && this.pdfBridge.controller !== null) {
      this.pdfBridge.controller.releaseDocument();
      this.pdfLoaded = false;
    }
  }

  private historyDir(): string {
    return `${this.project!.dir}/src/history`;
  }

  /** 读取雨课堂来源清单作为 PDF Kit 缺失时的图片降级；返回是否有可用页面图片。 */
  private loadRainFallback(): boolean {
    if (this.project === null) {
      return false;
    }
    const path = `${this.project.dir}/src/yuketang-source.json`;
    try {
      const parsed = JSON.parse(fs.readTextSync(path)) as Record<string, Object | null>;
      const pages = parsed['pages'];
      if (!Array.isArray(pages)) {
        return false;
      }
      const dir = this.project.dir;
      const entries = (pages as Array<Object | null>)
        .map((element: Object | null): RainPageAsset => {
          const item = element as Record<string, Object | null>;
          const entry: RainPageAsset = {
            page: Number(item['projectPage'] ?? 0),
            asset: item['asset']?.toString() ?? ''
          };
          return entry;
        })
        .filter((entry: RainPageAsset): boolean => entry.page >= 1 && entry.asset.length > 0)
        .sort((a: RainPageAsset, b: RainPageAsset): number => a.page - b.page);
      if (entries.length === 0) {
        return false;
      }
      this.rainPageImages = entries.map((entry: RainPageAsset): string => `file://${dir}/${entry.asset}`);
      this.pageCount = this.rainPageImages.length;
      return true;
    } catch (e) {
      return false;
    }
  }

  /** 设备缺失 PDF Kit（部分模拟器镜像）时不阻断页面：雨课堂项目降级为来源图片显示，其余提示。 */
  private async initPdf(imageFallback: boolean): Promise<void> {
    if (!canIUse('SystemCapability.OfficeService.PDFService.Core')) {
      this.statusText = imageFallback
        ? '当前设备不支持 PDF Kit，正用课件原始图片显示（内容与 PDF 一致）'
        : '当前设备不支持 PDF Kit，无法阅读 PDF（Markdown 项目不受影响）';
      return;
    }
    try {
      const pdfPane = await import('../components/PdfPane');
      this.pdfPaneBuilder = pdfPane.pdfPaneWrappedBuilder;
      this.loadPdf(0);
    } catch (e) {
      this.statusText = imageFallback
        ? 'PDF 组件加载失败，正用课件原始图片显示（内容与 PDF 一致）'
        : 'PDF 组件加载失败，无法阅读 PDF（Markdown 项目不受影响）';
    }
  }

  private loadPdf(retried: number): void {
    if (this.project === null) {
      return;
    }
    const path = this.project.leftPath;
    // loadDocument 需要 PdfPane 挂载并创建好控制器，让出一帧；未就绪时重试一次
    setTimeout(() => {
      const controller = this.pdfBridge.controller;
      if (controller === null) {
        if (retried < 1) {
          this.loadPdf(retried + 1);
        } else {
          this.statusText = 'PDF 初始化超时';
        }
        return;
      }
      controller.loadDocument(path, undefined, 0)
        .then(() => {
          this.pdfLoaded = true;
          this.pageCount = controller.getPageCount();
          if (this.pageCount < 1) {
            this.pageCount = 1;
          }
          this.statusText = '';
        })
        .catch((e: Error) => {
          this.statusText = `PDF 打开失败：${e.message}`;
        });
    }, 300);
  }

  private loadMarkdownSource(): void {
    if (this.project === null) {
      return;
    }
    try {
      this.sourceMarkdown = fs.readTextSync(this.project.leftPath);
    } catch (e) {
      this.sourceMarkdown = '';
      this.statusText = `读取原文失败：${(e as Error).message}`;
    }
  }

  // ---- 翻页模式（点击/滑动），偏好持久化 ----

  private prefStore(): preferences.Preferences {
    return preferences.getPreferencesSync(this.getUIContext().getHostContext() as common.UIAbilityContext,
      { name: 'pagenote_reader' });
  }

  private loadTurnMode(): void {
    try {
      const v: preferences.ValueType = this.prefStore().getSync('pageTurnMode', 'tap');
      if (v === 'scroll' || v === 'tap') {
        this.pageTurnMode = v as string;
      }
    } catch (e) {
      // 偏好不可用时保持默认点击翻页
    }
  }

  private saveTurnMode(): void {
    try {
      const store = this.prefStore();
      store.putSync('pageTurnMode', this.pageTurnMode);
      store.flush();
    } catch (e) {
      // 忽略持久化失败
    }
  }

  /** 切回点击模式时让 PDF 回到当前页（滑动翻页走 InkCanvas 跟手拖动，不动 PdfView 内部模式）。 */
  private applyTurnMode(): void {
    const controller = this.pdfBridge.controller;
    if (controller !== null && this.pageTurnMode === 'tap') {
      controller.goToPage(this.currentPage - 1);
    }
  }

  private toggleTurnMode(): void {
    this.pageTurnMode = this.pageTurnMode === 'scroll' ? 'tap' : 'scroll';
    this.saveTurnMode();
    // 滑动翻页时批注定会错位，强制退出批注并清选区
    this.inkMode = false;
    this.selectedIndices = [];
    this.selectionEpoch += 1;
    this.panX = 0;
    this.panY = 0;
    this.applyTurnMode();
  }

  private loadPage(page: number): void {
    if (this.project === null) {
      return;
    }
    const notes = ProjectStore.readNotes(this.project.rightPath);
    this.note = notes.get(page) ?? '';
    this.currentPage = page;
    this.dirty = false;
    this.panX = 0;
    this.panY = 0;
    // 静态审查 H1：翻页必须清套索选区，否则旧索引会作用到新页笔迹（可删错/拖错，数据损坏链）
    this.selectedIndices = [];
    this.selectionEpoch += 1;
    this.inkTextEditing = false;
    this.inkItems = Annotations.pageItems(this.annotations, 'document', page);
    PageNotes.revision(this.note).then((rev: string) => {
      this.noteRevision = rev;
    });
    if (this.isPdf && this.pdfLoaded && this.pdfBridge.controller !== null) {
      this.pdfBridge.controller.goToPage(page - 1);
    }
  }

  private async saveCurrentPage(): Promise<void> {
    if (this.project === null) {
      return;
    }
    try {
      const changed = await ProjectStore.saveNote(this.project.rightPath, this.currentPage, this.note,
        this.noteRevision);
      if (changed) {
        NoteHistory.snapshotOnce(this.historyDir(), this.currentPage, this.note);
      }
      this.noteRevision = await PageNotes.revision(this.note);
      this.dirty = false;
      this.statusText = changed ? `第 ${this.currentPage} 页笔记已保存` : '内容未变化';
    } catch (e) {
      this.statusText = `保存失败：${(e as Error).message}`;
      this.getUIContext().getPromptAction().showToast({ message: this.statusText });
    }
  }

  /** 跨页全文检索笔记；Markdown 项目连同原文一起检索。每页/原文取首个命中做摘要。 */
  private runSearch(): void {
    if (this.project === null) {
      return;
    }
    const query = this.searchQuery.trim().toLowerCase();
    if (query.length === 0) {
      this.searchResults = [];
      return;
    }
    const hits: SearchHit[] = [];
    const notes = ProjectStore.readNotes(this.project.rightPath);
    notes.forEach((text: string, page: number) => {
      const snippet = ReaderPage.snippetAt(text, query);
      if (snippet !== null) {
        hits.push({ page: page, snippet: snippet, source: '笔记' });
      }
    });
    if (!this.isPdf && this.sourceMarkdown.length > 0) {
      const snippet = ReaderPage.snippetAt(this.sourceMarkdown, query);
      if (snippet !== null) {
        hits.push({ page: 0, snippet: snippet, source: '原文' });
      }
    }
    this.searchResults = hits;
  }

  /** 返回命中位置前后的摘要（空白压缩为单空格），未命中返回 null。 */
  private static snippetAt(text: string, queryLower: string): string | null {
    const index = text.toLowerCase().indexOf(queryLower);
    if (index < 0) {
      return null;
    }
    const start = Math.max(0, index - 20);
    const end = Math.min(text.length, index + queryLower.length + 40);
    let snippet = text.substring(start, end).replace(/\s+/g, ' ').trim();
    if (start > 0) {
      snippet = `…${snippet}`;
    }
    if (end < text.length) {
      snippet = `${snippet}…`;
    }
    return snippet;
  }

  private openSearch(): void {
    this.showSearch = true;
    this.runSearch();
  }

  private jumpToHit(hit: SearchHit): void {
    this.showSearch = false;
    if (hit.source === '笔记') {
      this.goToPage(hit.page);
    }
  }

  /** 笔记内图片链接（如 assets/x.png）解析为项目 src/ 下的沙箱路径；拒绝逃逸与绝对路径。 */
  private resolveAsset(src: string): string {
    if (this.project === null || src.length === 0) {
      return '';
    }
    let cleaned = src;
    if (cleaned.startsWith('./')) {
      cleaned = cleaned.substring(2);
    }
    if (cleaned.startsWith('/') || cleaned.indexOf('..') >= 0 || cleaned.length === 0) {
      return '';
    }
    // Image 组件对裸路径会按 rawfile 资源查找（GetAsset failed），必须 file:// 前缀
    return `file://${this.project.dir}/src/${cleaned}`;
  }

  private openHistory(): void {
    if (this.project === null) {
      return;
    }
    this.historyList = NoteHistory.entries(this.historyDir(), this.currentPage);
    this.showHistory = true;
  }

  private async restoreVersion(version: NoteVersion): Promise<void> {
    if (this.project === null || this.noteRevision === null) {
      return;
    }
    try {
      const text = await NoteHistory.restore(this.historyDir(), version.id, this.currentPage,
        this.noteRevision, this.note);
      this.note = text;
      this.dirty = true;
      this.showHistory = false;
      this.statusText = `已载入 ${new Date(version.created).toLocaleString()} 的快照，保存后生效`;
    } catch (e) {
      this.getUIContext().getPromptAction().showToast({ message: `恢复失败：${(e as Error).message}` });
    }
  }

  private async explainCurrentPage(): Promise<void> {
    if (this.project === null) {
      return;
    }
    let endpoint: ModelEndpoint;
    try {
      endpoint = ModelConfig.resolve(this.aiBaseUrl, this.aiApiKey, this.aiModel);
    } catch (e) {
      this.showAiSettings = true;
      return;
    }
    this.aiBusy = true;
    this.aiResult = '';
    this.showAiResult = true;
    try {
      const sourceHint = this.isPdf ? `PDF 文档第 ${this.currentPage} 页` : 'Markdown 原文';
      this.aiResult = await PageTutor.explainPage(endpoint, this.currentPage, this.note, sourceHint);
    } catch (e) {
      this.aiResult = `调用失败：${(e as Error).message}`;
    } finally {
      this.aiBusy = false;
    }
  }

  private saveAnnotations(): void {
    if (this.project === null) {
      return;
    }
    Annotations.setPageItems(this.annotations, 'document', this.currentPage, this.inkItems);
    try {
      Annotations.save(`${this.project.dir}/src/annotations.json`, this.annotations);
    } catch (e) {
      this.statusText = `批注保存失败：${(e as Error).message}`;
    }
  }

  private undoInk(): void {
    if (this.inkItems.length === 0) {
      return;
    }
    this.inkItems = this.inkItems.slice(0, this.inkItems.length - 1);
    this.selectedIndices = [];
    this.selectionEpoch += 1;
    this.saveAnnotations();
  }

  private clearInk(): void {
    if (this.inkItems.length === 0) {
      return;
    }
    this.inkItems = [];
    this.selectedIndices = [];
    this.selectionEpoch += 1;
    this.saveAnnotations();
  }

  /** OCR Kit 可用性一次性探测：syscap 校验 + 动态 import（@hms 模块缺失时静态 import 会崩）。 */
  private async probeOcrReady(): Promise<void> {
    if (!canIUse('SystemCapability.AI.OCR.TextRecognition')) {
      return;
    }
    try {
      const ocr = await import('@hms.ai.ocr.textRecognition');
      this.ocrReady = typeof ocr.default.recognizeText === 'function';
    } catch (e) {
      this.ocrReady = false;
    }
  }

  /**
   * 手写转文字：把套索选中的笔迹区域截图送 OCR，识别成功后删除原笔迹、
   * 在原包围盒左上角插入文字批注（颜色/线宽取当前笔设置）；失败统一提示未识别到。
   */
  private async convertSelectedInkToText(): Promise<void> {
    const indices: number[] = this.selectedIndices;
    if (indices.length === 0 || !this.ocrReady) {
      return;
    }
    const toast = this.getUIContext().getPromptAction();
    // 选中笔迹的 1000 比例系包围盒（画布 y 与 x 同比例系数，像素 = 坐标 * 宽 / 1000）
    let minX: number = Number.MAX_VALUE;
    let minY: number = Number.MAX_VALUE;
    let maxX: number = -Number.MAX_VALUE;
    let maxY: number = -Number.MAX_VALUE;
    for (const idx of indices) {
      const item: AnnotationItem | undefined = this.inkItems[idx];
      if (item === undefined) {
        continue;
      }
      for (const p of item.points) {
        if (p.length < 2) {
          continue;
        }
        minX = Math.min(minX, p[0]);
        minY = Math.min(minY, p[1]);
        maxX = Math.max(maxX, p[0]);
        maxY = Math.max(maxY, p[1]);
      }
    }
    if (minX > maxX) {
      toast.showToast({ message: '未识别到手写文字' });
      return;
    }
    try {
      const shot: image.PixelMap = componentSnapshot.getSync(INK_SNAPSHOT_ID,
        { waitUntilRenderFinished: true });
      const info: image.ImageInfo = await shot.getImageInfo();
      const layerW: number = info.size.width;
      const layerH: number = info.size.height;
      // 20px 边距余量防切字，裁剪框钳制在画布内
      const margin: number = 20;
      const x0: number = Math.max(0, Math.floor(minX * layerW / 1000) - margin);
      const y0: number = Math.max(0, Math.floor(minY * layerW / 1000) - margin);
      const x1: number = Math.min(layerW, Math.ceil(maxX * layerW / 1000) + margin);
      const y1: number = Math.min(layerH, Math.ceil(maxY * layerW / 1000) + margin);
      const cropW: number = x1 - x0;
      const cropH: number = y1 - y0;
      if (cropW < 1 || cropH < 1) {
        toast.showToast({ message: '未识别到手写文字' });
        return;
      }
      // 裁剪走 PNG 编解码：readPixels 固定输出 BGRA，直接重建 PixelMap 会交换红蓝通道
      const packer: image.ImagePacker = image.createImagePacker();
      const packed: ArrayBuffer = await packer.packToData(shot, { format: 'image/png', quality: 100 });
      await packer.release();
      const source: image.ImageSource = image.createImageSource(packed);
      const cropped: image.PixelMap = await source.createPixelMap({
        desiredRegion: { x: x0, y: y0, size: { width: cropW, height: cropH } }
      });
      await source.release();
      const ocr = await import('@hms.ai.ocr.textRecognition');
      const result = await ocr.default.recognizeText({ pixelMap: cropped });
      const text: string = result.value.trim();
      if (text.length === 0) {
        toast.showToast({ message: '未识别到手写文字' });
        return;
      }
      const drop: Set<number> = new Set<number>(indices);
      const kept: AnnotationItem[] =
        this.inkItems.filter((_: AnnotationItem, i: number): boolean => !drop.has(i));
      const textItem: AnnotationItem = {
        type: 'text', color: this.inkColor, width: this.inkWidth,
        points: [[minX, minY]], text: text
      };
      this.inkItems = [...kept, textItem];
      this.selectedIndices = [];
      this.selectionEpoch += 1;
      this.saveAnnotations();
      toast.showToast({
        message: `已转为文字：${text.length > 30 ? text.substring(0, 30) + '…' : text}`
      });
    } catch (e) {
      toast.showToast({ message: '未识别到手写文字' });
    }
  }

  // ---- 闪卡 ----

  private flashcardsPath(): string {
    return `${this.project!.dir}/src/flashcards.json`;
  }

  private reloadCards(): void {
    if (this.project === null) {
      return;
    }
    this.cards = FlashcardStore.load(this.flashcardsPath());
  }

  private openCards(): void {
    this.reloadCards();
    this.cardTab = 'make';
    this.cardFront = '';
    this.cardBack = '';
    this.cardTags = '';
    this.reviewQueue = [];
    this.showCards = true;
  }

  /** 制卡草稿：有选区用选区，否则用光标（selEnd）所在段落。 */
  private async fillCardDraft(): Promise<void> {
    if (this.note.trim().length === 0) {
      this.getUIContext().getPromptAction().showToast({ message: '本页笔记为空，无可制卡内容' });
      return;
    }
    try {
      const draft = this.selEnd > this.selStart
        ? await FlashcardCapture.fromSelection(this.currentPage, this.note, this.selStart, this.selEnd)
        : await FlashcardCapture.fromParagraph(this.currentPage, this.note, this.selEnd);
      this.cardFront = draft.front;
      this.cardTab = 'make';
    } catch (e) {
      this.getUIContext().getPromptAction().showToast({ message: (e as Error).message });
    }
  }

  private async saveCard(): Promise<void> {
    if (this.project === null || this.cardFront.trim().length === 0) {
      this.getUIContext().getPromptAction().showToast({ message: '卡片正面不能为空' });
      return;
    }
    const tags = FlashcardCapture.normalizeTags(this.cardTags);
    try {
      // 优先按选区/段落捕获锚点；本页无可用内容时允许纯手工卡（无来源锚点）
      let draft: FlashcardDraft;
      try {
        draft = this.selEnd > this.selStart
          ? await FlashcardCapture.fromSelection(this.currentPage, this.note, this.selStart, this.selEnd)
          : await FlashcardCapture.fromParagraph(this.currentPage, this.note, this.selEnd);
        draft.front = this.cardFront.trim();
      } catch (e) {
        draft = {
          page: this.currentPage, sourceKind: 'note_paragraph',
          front: this.cardFront.trim(), sourceQuote: '',
          anchorStart: 0, anchorEnd: 0,
          revision: this.noteRevision ?? ''
        };
      }
      const card = FlashcardStore.newCard(draft, this.cardBack.trim(), tags, Date.now());
      const next = [...this.cards, card];
      FlashcardStore.save(this.flashcardsPath(), next);
      this.cards = next;
      this.cardFront = '';
      this.cardBack = '';
      this.cardTags = '';
      this.getUIContext().getPromptAction().showToast({ message: '卡片已保存' });
    } catch (e) {
      this.getUIContext().getPromptAction().showToast({ message: `保存失败：${(e as Error).message}` });
    }
  }

  private startReview(): void {
    this.reviewQueue = FlashcardStore.dueCards(this.cards, Date.now());
    this.reviewIndex = 0;
    this.reviewShowBack = false;
    this.cardTab = 'review';
  }

  private rateCard(outcome: ReviewOutcome): void {
    const card = this.reviewQueue[this.reviewIndex];
    FlashcardStore.applyReview(card, outcome, Date.now());
    FlashcardStore.save(this.flashcardsPath(), this.cards);
    this.reviewIndex += 1;
    this.reviewShowBack = false;
  }

  private deleteCard(card: FlashcardItem): void {
    const next = this.cards.filter((item: FlashcardItem): boolean => item.id !== card.id);
    FlashcardStore.save(this.flashcardsPath(), next);
    this.cards = next;
  }

  // ---- 图片插入 ----

  /** 相册选图复制进项目 src/assets/ 并在光标处插入引用（与 MarkdownView 图片渲染同一契约）。 */
  private insertImage(): void {
    if (this.project === null) {
      return;
    }
    const photoPicker = new photoAccessHelper.PhotoViewPicker();
    const options = new photoAccessHelper.PhotoSelectOptions();
    options.maxSelectNumber = 1;
    photoPicker.select(options).then((result: photoAccessHelper.PhotoSelectResult) => {
      const uris = result.photoUris;
      if (uris.length === 0) {
        return;
      }
      const uri = uris[0];
      const decoded = decodeURIComponent(uri);
      const dot = decoded.lastIndexOf('.');
      const ext = dot > 0 ? decoded.substring(dot + 1).toLowerCase() : 'jpg';
      const name = `insert-${Date.now()}.${ext}`;
      const targetPath = `${this.project!.dir}/src/assets/${name}`;
      const src = fs.openSync(uri, fs.OpenMode.READ_ONLY);
      try {
        fs.copyFileSync(src.fd, targetPath);
      } finally {
        fs.closeSync(src);
      }
      // 插入到光标（selEnd）处
      const insertion = `![图片](assets/${name})`;
      const at = Math.min(Math.max(this.selEnd, 0), this.note.length);
      this.note = this.note.substring(0, at) + insertion + this.note.substring(at);
      this.dirty = true;
      this.statusText = `图片已插入：assets/${name}`;
    }).catch(() => {
      // 用户取消选择
    });
  }

  // ---- 选区 AI 动作 ----

  private selectedText(): string {
    if (this.selEnd > this.selStart && this.selEnd <= this.note.length) {
      return this.note.substring(this.selStart, this.selEnd);
    }
    return '';
  }

  private openAiAction(): void {
    this.aiActionResult = '';
    this.aiActionError = '';
    this.showAiAction = true;
  }

  private async runAiAction(action: SelectionAction): Promise<void> {
    const text = this.selectedText();
    if (text.trim().length === 0) {
      this.aiActionError = '请先在笔记里选中一段文字';
      return;
    }
    let endpoint: ModelEndpoint;
    try {
      endpoint = ModelConfig.resolve(this.aiBaseUrl, this.aiApiKey, this.aiModel);
    } catch (e) {
      this.showAiAction = false;
      this.showAiSettings = true;
      return;
    }
    this.aiActionBusy = true;
    this.aiActionResult = '';
    this.aiActionError = '';
    try {
      this.aiActionResult = await SelectionActions.run(endpoint, action, text);
    } catch (e) {
      this.aiActionError = `调用失败：${(e as Error).message}`;
    } finally {
      this.aiActionBusy = false;
    }
  }

  /** 确认后插回：替换选区或追加到选区之后。 */
  private insertAiAction(mode: string): void {
    if (this.aiActionResult.trim().length === 0 || this.selEnd <= this.selStart) {
      return;
    }
    if (mode === 'replace') {
      this.note = this.note.substring(0, this.selStart) + this.aiActionResult
        + this.note.substring(this.selEnd);
    } else {
      this.note = this.note.substring(0, this.selEnd) + '\n\n' + this.aiActionResult
        + this.note.substring(this.selEnd);
    }
    this.dirty = true;
    this.showAiAction = false;
    this.statusText = 'AI 结果已插入笔记，保存后生效';
  }

  // ---- 导出 / 导入 ----

  /** 脏草稿先落盘再导出，导出内容含当前页草稿。 */
  private async prepareTransfer(): Promise<boolean> {
    if (this.project === null) {
      return false;
    }
    if (this.dirty) {
      await this.saveCurrentPage();
      if (this.dirty) {
        this.getUIContext().getPromptAction().showToast({ message: '当前页保存失败，未导出' });
        return false;
      }
    }
    return true;
  }

  private async exportBackup(): Promise<void> {
    if (!await this.prepareTransfer()) {
      return;
    }
    const project = this.project!;
    let fullText = '';
    try {
      fullText = fs.readTextSync(project.rightPath);
    } catch (e) {
      // 无笔记文件按空导出
    }
    const pageCount = PageNotes.split(fullText).length;
    const file = NoteTransfer.fullBackup(project.manifest.name, pageCount, fullText);
    this.saveToUserFile(file);
  }

  private async exportCurrentPage(): Promise<void> {
    if (this.project === null) {
      return;
    }
    const file = NoteTransfer.currentPage(this.project.manifest.name, this.currentPage, this.note);
    this.saveToUserFile(file);
  }

  private saveToUserFile(file: NoteTransferFile): void {
    const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
    const docPicker = new picker.DocumentViewPicker(context);
    const options = new picker.DocumentSaveOptions();
    options.newFileNames = [file.fileName];
    docPicker.save(options).then((uris: string[]) => {
      if (uris.length === 0) {
        return;
      }
      try {
        const target = fs.openSync(uris[0], fs.OpenMode.READ_WRITE | fs.OpenMode.TRUNC);
        try {
          fs.writeSync(target.fd, file.content);
        } finally {
          fs.closeSync(target);
        }
        this.statusText = `已导出：${file.fileName}`;
      } catch (e) {
        this.getUIContext().getPromptAction().showToast({ message: `导出失败：${(e as Error).message}` });
      }
    }).catch(() => {
      // 用户取消
    });
  }

  /** 导入：JSON 备份合并进整份笔记（合法页覆盖、空串清页、缺失不动）；MD/TXT 文本插入光标处。 */
  private importNotes(): void {
    const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
    const docPicker = new picker.DocumentViewPicker(context);
    const options = new picker.DocumentSelectOptions();
    options.maxSelectNumber = 1;
    // 官方格式为「描述|后缀」，缺竖线会把整串当后缀导致什么都筛不出来（实测）
    options.fileSuffixFilters = ['JSON|.json', 'Markdown/文本|.md,.markdown,.txt', '所有文件(*.*)|.*'];
    docPicker.select(options).then((uris: string[]) => {
      if (uris.length === 0) {
        return;
      }
      try {
        const text = fs.readTextSync(uris[0]);
        if (NoteTransfer.isBackupJson(text)) {
          let existing = '';
          try {
            existing = fs.readTextSync(this.project!.rightPath);
          } catch (e) {
            // 无笔记文件按空合并
          }
          const merged = NoteTransfer.mergeBackup(existing, text);
          ProjectStore.atomicWrite(this.project!.rightPath, merged);
          this.loadPage(this.currentPage);
          this.statusText = '备份已合并进当前项目';
        } else {
          const at = Math.min(Math.max(this.selEnd, 0), this.note.length);
          this.note = this.note.substring(0, at) + text + this.note.substring(at);
          this.dirty = true;
          this.statusText = '文件内容已插入当前页，保存后生效';
        }
        this.showTransfer = false;
      } catch (e) {
        this.getUIContext().getPromptAction().showToast({ message: `导入失败：${(e as Error).message}` });
      }
    }).catch(() => {
      // 用户取消
    });
  }

  private goToPage(page: number): void {
    let target = page;
    if (target < 1) {
      target = 1;
    }
    if (this.isPdf && target > this.pageCount) {
      target = this.pageCount;
    }
    this.loadPage(target);
  }

  build() {
    Column() {
      Row() {
        IconButton({
          symbol: $r('sys.symbol.chevron_left'), label: '返回',
          onTap: () => {
            this.getUIContext().getRouter().back();
          }
        })
        Text(this.projectName)
          .fontSize(18)
          .fontColor(UiTheme.ink(this.isDarkMode()))
          .fontWeight(FontWeight.Medium)
          .margin({ left: 12 })
          .layoutWeight(1)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        if (this.isPdf) {
          Text(`第 ${this.currentPage} / ${this.pageCount} 页`)
            .fontSize(14)
            .fontColor(UiTheme.ink2(this.isDarkMode()))
        }
        IconButton({
          symbol: this.pageTurnMode === 'scroll' ? $r('sys.symbol.arrow_up_and_down_square_portrait') :
            $r('sys.symbol.hand_point_up_tap_fill_1'),
          label: this.pageTurnMode === 'scroll' ? '切换为点击翻页' : '切换为滑动翻页',
          active: this.pageTurnMode === 'scroll',
          onTap: () => {
            this.toggleTurnMode();
          }
        })
        IconButton({
          symbol: $r('sys.symbol.paintbrush_pointed'), label: this.inkMode ? '关闭批注' : '批注',
          active: this.inkMode, enabledState: this.pageTurnMode === 'tap',
          onTap: () => {
            this.inkMode = !this.inkMode;
            // 静态审查 L12：关闭批注时同步清选区，避免重新开启后残留旧选区入口
            this.selectedIndices = [];
            this.selectionEpoch += 1;
          }
        })
          .margin({ left: 12 })
        IconButton({
          symbol: $r('sys.symbol.moon'), label: this.nightMode ? '关闭夜览' : '夜览',
          active: this.nightMode, activeColor: UiTheme.ACCENT,
          onTap: () => {
            this.nightMode = !this.nightMode;
          }
        })
          .margin({ left: 8 })
        IconButton({
          symbol: $r('sys.symbol.ellipsis_circle'), label: '更多',
          onTap: () => {
            this.showTransfer = true;
          }
        })
          .margin({ left: 8 })
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 10, bottom: 10 })
      .alignItems(VerticalAlign.Center)
      .backgroundColor(UiTheme.cardBg(this.isDarkMode()))
      .border({ width: { bottom: 0.5 }, color: UiTheme.hairline(this.isDarkMode()) })
      // 工具栏背景延伸至状态栏区域
      .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP])
      // 同一节点挂多个 bindSheet 会导致全部失效（API 24 实测），每个 sheet 挂不同节点
      .bindSheet($$this.showSearch, this.searchBuilder(), {
        height: SheetSize.LARGE,
        title: { title: '搜索笔记' }
      })

      // 画笔栏常显（工具预选随时可用）；「批注」按钮仍是书写的总开关
      Row() {
          ForEach(INK_TOOLS, (spec: InkToolSpec) => {
            IconButton({
              symbol: spec.symbol, label: spec.label,
              active: this.inkTool === spec.id,
              onTap: () => {
                this.inkTool = spec.id;
                this.selectionEpoch += 1;
              }
            })
              .margin({ left: 6 })
          }, (spec: InkToolSpec) => spec.id)
          ForEach(['#dc3545', UiTheme.PRIMARY, UiTheme.SUCCESS, '#222222'], (c: string) => {
            // fill 是 API 26+ 接口，用 Stack+圆角代替 Circle().fill 保持 API 20 兼容
            Stack()
              .width(22)
              .height(22)
              .borderRadius(11)
              .backgroundColor(c)
              .margin({ left: 10 })
              .border(this.inkColor === c ? { width: 2, color: '#FFAA00' } : { width: 0 })
              .onClick(() => {
                this.inkColor = c;
              })
          }, (c: string) => c)
          ForEach(INK_WIDTHS, (spec: InkWidthSpec) => {
            Button(spec.label)
              .fontSize(12)
              .height(28)
              .padding({ left: 8, right: 8 })
              .margin({ left: 6 })
              .borderRadius(8)
              .backgroundColor(this.inkWidth === spec.value ? UiTheme.accentSoft(this.isDarkMode()) : '#00000000')
              .fontColor(this.inkWidth === spec.value ? UiTheme.ACCENT : UiTheme.ink2(this.isDarkMode()))
              .onClick(() => {
                this.inkWidth = spec.value;
              })
          }, (spec: InkWidthSpec) => spec.label)
          Blank()
          IconButton({
            symbol: $r('sys.symbol.minus_circle'), label: '删除所选',
            enabledState: this.selectedIndices.length > 0,
            onTap: () => {
              const drop = new Set<number>(this.selectedIndices);
              this.inkItems = this.inkItems.filter((_: AnnotationItem, i: number): boolean => !drop.has(i));
              this.selectedIndices = [];
              this.selectionEpoch += 1;
              this.saveAnnotations();
            }
          })
          IconButton({
            symbol: $r('sys.symbol.textformat'), label: '手写转文字',
            enabledState: this.selectedIndices.length > 0 && this.ocrReady,
            onTap: () => {
              this.convertSelectedInkToText();
            }
          })
            .margin({ left: 8 })
          IconButton({
            symbol: $r('sys.symbol.hand_raised_hexagon_fill'), label: this.fingerLock ? '解锁手指' : '锁定手指',
            active: this.fingerLock,
            onTap: () => {
              this.fingerLock = !this.fingerLock;
            }
          })
            .margin({ left: 8 })
          IconButton({
            symbol: $r('sys.symbol.undo'), label: '撤销',
            onTap: () => {
              this.undoInk();
            }
          })
            .margin({ left: 8 })
          IconButton({
            symbol: $r('sys.symbol.trash'), label: '清除本页',
            onTap: () => {
              this.clearInk();
            }
          })
            .margin({ left: 8 })
        }
        .width('100%')
        .padding({ left: 16, right: 16, top: 6, bottom: 6 })
        .alignItems(VerticalAlign.Center)
        .backgroundColor(UiTheme.cardBg(this.isDarkMode()))
        .border({ width: { bottom: 0.5 }, color: UiTheme.hairline(this.isDarkMode()) })

      // 文字批注输入行：内嵌在页面里（sheet 会被软键盘顶掉，实测）
      if (this.inkMode && this.inkTextEditing) {
        Row() {
          TextInput({ placeholder: '批注内容', text: $$this.inkTextDraft })
            .layoutWeight(1)
            .height(36)
          Button('添加')
            .fontSize(13)
            .height(30)
            .margin({ left: 8 })
            .onClick(() => {
              const text = this.inkTextDraft.trim();
              if (text.length === 0) {
                this.getUIContext().getPromptAction().showToast({ message: '请输入批注内容' });
                return;
              }
              const item: AnnotationItem = {
                type: 'text', color: this.inkColor, width: this.inkWidth,
                points: [this.inkTextPoint], text: text
              };
              this.inkItems = [...this.inkItems, item];
              this.saveAnnotations();
              this.inkTextEditing = false;
            })
          Button('取消')
            .fontSize(13)
            .height(30)
            .margin({ left: 8 })
            .backgroundColor(UiTheme.controlIdle(this.isDarkMode()))
            .fontColor(UiTheme.ink(this.isDarkMode()))
            .onClick(() => {
              this.inkTextEditing = false;
            })
        }
        .width('100%')
        .padding({ left: 16, right: 16, top: 6, bottom: 6 })
        .alignItems(VerticalAlign.Center)
        .backgroundColor(UiTheme.cardBg(this.isDarkMode()))
        .border({ width: { bottom: 0.5 }, color: UiTheme.hairline(this.isDarkMode()) })
      }

      Stack() {
        // 横屏左右分栏，竖屏（或窄窗口）上下分栏
        if (this.isLandscape) {
          Row() {
            this.leftPane()
            this.rightPane()
          }
          .layoutWeight(1)
          .width('100%')
          .bindSheet($$this.showTransfer, this.transferBuilder(), {
            height: SheetSize.FIT_CONTENT,
            title: { title: '导出 / 导入笔记' }
          })
        } else {
          Column() {
            this.leftPane()
            this.rightPane()
          }
          .layoutWeight(1)
          .width('100%')
          .bindSheet($$this.showTransfer, this.transferBuilder(), {
            height: SheetSize.FIT_CONTENT,
            title: { title: '导出 / 导入笔记' }
          })
        }


        // 夜览滤镜：覆盖阅读区的暖色遮罩，不拦截触摸与批注
        if (this.nightMode) {
          Column()
            .width('100%')
            .height('100%')
            .backgroundColor('#3DFF9E3D')
            .hitTestBehavior(HitTestMode.None)
        }
      }
      .layoutWeight(1)
      .width('100%')
      .bindSheet($$this.showHistory, this.historyBuilder(), {
        height: SheetSize.FIT_CONTENT,
        title: { title: `第 ${this.currentPage} 页历史` }
      })
    }
    .width('100%')
    .height('100%')
    .backgroundColor(UiTheme.pageBg(this.isDarkMode()))
    // 页面背景延伸至底部导航条区域，深色下不留白边
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])
    // 2in1/外接键盘快捷键：Ctrl+S 保存当前页，Ctrl+F 打开搜索
    .keyboardShortcut('S', [ModifierKey.CTRL], () => {
      this.saveCurrentPage().catch(() => {});
    })
    .keyboardShortcut('F', [ModifierKey.CTRL], () => {
      this.openSearch();
    })
    .onAreaChange((oldValue: Area, newValue: Area) => {
      this.isLandscape = Number(newValue.width) >= Number(newValue.height);
    })
    // 根节点无其他 sheet，闪卡面板挂这里
    .bindSheet($$this.showCards, this.cardsBuilder(), {
      height: SheetSize.LARGE,
      title: { title: '闪卡' }
    })
  }

  @Builder
  transferBuilder() {
    Column() {
      Text('导出内容不写入项目源文件；导入 JSON 备份按页合并（合法页覆盖、空串清页、缺失不动），Markdown/文本插入当前页光标处。')
        .fontSize(12)
        .fontColor(UiTheme.ink3(this.isDarkMode()))
        .width('100%')
        .margin({ bottom: 8 })
      Row() {
        Button('导出全量备份（JSON）')
          .onClick(() => {
            this.showTransfer = false;
            this.exportBackup();
          })
      }
      .width('100%')
      .margin({ top: 8 })
      Row() {
        Button('导出当前页（Markdown）')
          .onClick(() => {
            this.showTransfer = false;
            this.exportCurrentPage();
          })
      }
      .width('100%')
      .margin({ top: 8 })
      Row() {
        Button('导入笔记（JSON / Markdown / TXT）')
          .onClick(() => {
            this.importNotes();
          })
      }
      .width('100%')
      .margin({ top: 8, bottom: 16 })
    }
    .width('100%')
    .padding({ left: 16, right: 16 })
  }

  @Builder
  cardsBuilder() {
    Column() {
      Row() {
        Button('制卡')
          .fontSize(13)
          .height(30)
          .backgroundColor(this.cardTab === 'make' ? UiTheme.PRIMARY : UiTheme.controlIdle(this.isDarkMode()))
          .fontColor(this.cardTab === 'make' ? '#FFFFFF' : UiTheme.ink(this.isDarkMode()))
          .onClick(() => {
            this.fillCardDraft();
          })
        Button(`复习(${FlashcardStore.dueCards(this.cards, Date.now()).length})`)
          .fontSize(13)
          .height(30)
          .margin({ left: 8 })
          .backgroundColor(this.cardTab === 'review' ? UiTheme.PRIMARY : UiTheme.controlIdle(this.isDarkMode()))
          .fontColor(this.cardTab === 'review' ? '#FFFFFF' : UiTheme.ink(this.isDarkMode()))
          .onClick(() => {
            this.startReview();
          })
        Button(`卡片库(${this.cards.length})`)
          .fontSize(13)
          .height(30)
          .margin({ left: 8 })
          .backgroundColor(this.cardTab === 'lib' ? UiTheme.PRIMARY : UiTheme.controlIdle(this.isDarkMode()))
          .fontColor(this.cardTab === 'lib' ? '#FFFFFF' : UiTheme.ink(this.isDarkMode()))
          .onClick(() => {
            this.cardTab = 'lib';
          })
      }
      .width('100%')
      .padding({ top: 4, bottom: 8 })

      if (this.cardTab === 'make') {
        Column() {
          Text('正面（问题/原文，默认取选区或光标段落）')
            .fontSize(12)
            .fontColor(UiTheme.ink3(this.isDarkMode()))
            .width('100%')
          TextArea({ placeholder: '卡片正面', text: $$this.cardFront })
            .height(100)
            .margin({ top: 4 })
          Text('背面（答案）')
            .fontSize(12)
            .fontColor(UiTheme.ink3(this.isDarkMode()))
            .width('100%')
            .margin({ top: 8 })
          TextArea({ placeholder: '卡片背面（可留空）', text: $$this.cardBack })
            .height(100)
            .margin({ top: 4 })
          TextInput({ placeholder: '标签（逗号分隔，可留空）', text: $$this.cardTags })
            .margin({ top: 8 })
          Button('保存卡片')
            .margin({ top: 12 })
            .onClick(() => {
              this.saveCard();
            })
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
      } else if (this.cardTab === 'review') {
        if (this.reviewQueue.length === 0 || this.reviewIndex >= this.reviewQueue.length) {
          Text(this.cards.length === 0 ? '还没有卡片，先去制卡' : '现在没有到期的卡片')
            .fontSize(14)
            .fontColor(UiTheme.ink3(this.isDarkMode()))
            .padding(24)
        } else {
          Column() {
            Text(`第 ${this.reviewIndex + 1} / ${this.reviewQueue.length} 张 · 第 ${this.reviewQueue[this.reviewIndex].page} 页`)
              .fontSize(12)
              .fontColor(UiTheme.ink3(this.isDarkMode()))
              .width('100%')
            Text(this.reviewQueue[this.reviewIndex].front)
              .fontSize(17)
              .fontColor(UiTheme.ink(this.isDarkMode()))
              .width('100%')
              .margin({ top: 12 })
              .textAlign(TextAlign.Center)
            if (this.reviewShowBack) {
              Divider().margin({ top: 12, bottom: 12 })
              Text(this.reviewQueue[this.reviewIndex].back.length > 0
                ? this.reviewQueue[this.reviewIndex].back : '（背面留空，回忆后自评）')
                .fontSize(15)
                .fontColor(UiTheme.ink2(this.isDarkMode()))
                .width('100%')
                .textAlign(TextAlign.Center)
              Row() {
                Button('没记住')
                  .backgroundColor(UiTheme.DANGER)
                  .onClick(() => {
                    this.rateCard('again');
                  })
                Button('勉强')
                  .margin({ left: 8 })
                  .backgroundColor(UiTheme.ACCENT)
                  .onClick(() => {
                    this.rateCard('hard');
                  })
                Button('记住')
                  .margin({ left: 8 })
                  .backgroundColor(UiTheme.SUCCESS)
                  .onClick(() => {
                    this.rateCard('good');
                  })
                Button('轻松')
                  .margin({ left: 8 })
                  .onClick(() => {
                    this.rateCard('easy');
                  })
              }
              .margin({ top: 16 })
              .justifyContent(FlexAlign.Center)
              .width('100%')
            } else {
              Button('显示答案')
                .margin({ top: 16 })
                .onClick(() => {
                  this.reviewShowBack = true;
                })
            }
          }
          .width('100%')
          .padding({ bottom: 16 })
        }
      } else {
        List() {
          ForEach(this.cards, (card: FlashcardItem) => {
            ListItem() {
              Row() {
                Column() {
                  Text(`第 ${card.page} 页 · ${card.tags.join('、')}`)
                    .fontSize(12)
                    .fontColor(UiTheme.ink3(this.isDarkMode()))
                  Text(card.front)
                    .fontSize(14)
                    .fontColor(UiTheme.ink(this.isDarkMode()))
                    .maxLines(2)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                    .margin({ top: 4 })
                }
                .alignItems(HorizontalAlign.Start)
                .layoutWeight(1)
                Button('删除')
                  .fontSize(13)
                  .height(30)
                  .margin({ left: 12 })
                  .backgroundColor(UiTheme.controlIdle(this.isDarkMode()))
                  .fontColor(UiTheme.DANGER)
                  .onClick(() => {
                    this.deleteCard(card);
                  })
              }
              .width('100%')
              .padding(12)
            }
          }, (card: FlashcardItem) => card.id)
        }
        .width('100%')
        .layoutWeight(1)
      }
    }
    .width('100%')
    .height('100%')
    .padding({ left: 16, right: 16, bottom: 16 })
  }

  @Builder
  aiActionBuilder() {
    Column() {
      if (this.selectedText().trim().length === 0) {
        Text('请先在笔记里选中一段文字（长按拖选），再回到这里')
          .fontSize(14)
          .fontColor(UiTheme.ink3(this.isDarkMode()))
          .padding(24)
          .width('100%')
      } else {
        Text(`选区（第 ${this.currentPage} 页）：${this.selectedText().substring(0, 80)}${this.selectedText().length > 80 ? '…' : ''}`)
          .fontSize(12)
          .fontColor(UiTheme.ink3(this.isDarkMode()))
          .width('100%')
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Row() {
          Button('改写')
            .enabled(!this.aiActionBusy)
            .onClick(() => {
              this.runAiAction('rewrite');
            })
          Button('翻译')
            .margin({ left: 8 })
            .enabled(!this.aiActionBusy)
            .onClick(() => {
              this.runAiAction('translate');
            })
          Button('总结')
            .margin({ left: 8 })
            .enabled(!this.aiActionBusy)
            .onClick(() => {
              this.runAiAction('summarize');
            })
        }
        .margin({ top: 8 })
        if (this.aiActionBusy) {
          Text('正在调用模型…')
            .fontSize(13)
            .fontColor(UiTheme.ink3(this.isDarkMode()))
            .padding(16)
        }
        if (this.aiActionError.length > 0) {
          Text(this.aiActionError)
            .fontSize(13)
            .fontColor(UiTheme.DANGER)
            .padding(8)
            .width('100%')
        }
        if (this.aiActionResult.length > 0) {
          Scroll() {
            Text(this.aiActionResult)
              .fontSize(14)
              .fontColor(UiTheme.ink(this.isDarkMode()))
              .width('100%')
              .textAlign(TextAlign.Start)
          }
          .layoutWeight(1)
          .width('100%')
          .margin({ top: 8 })
          Row() {
            Text('确认后插回：')
              .fontSize(13)
              .fontColor(UiTheme.ink2(this.isDarkMode()))
            Button('替换选区')
              .fontSize(13)
              .height(30)
              .margin({ left: 8 })
              .onClick(() => {
                this.insertAiAction('replace');
              })
            Button('追加到选区后')
              .fontSize(13)
              .height(30)
              .margin({ left: 8 })
              .backgroundColor(UiTheme.controlIdle(this.isDarkMode()))
              .fontColor(UiTheme.ink(this.isDarkMode()))
              .onClick(() => {
                this.insertAiAction('append');
              })
          }
          .width('100%')
          .margin({ top: 8, bottom: 8 })
        }
      }
    }
    .width('100%')
    .height('100%')
    .padding({ left: 16, right: 16, bottom: 16 })
  }

  @Builder
  rightPane() {
        // 右侧：逐页笔记；工具条浮动在内容上方（液态玻璃模糊下层滚动文字）
        Stack({ alignContent: Alignment.Top }) {
          Column() {
          if (this.previewMode) {
            MarkdownView({
              content: this.note,
              resolveImage: (src: string): string => this.resolveAsset(src)
            })
              .layoutWeight(1)
              .width('100%')
              .padding({ left: 16, right: 16, top: 64, bottom: 16 })
          } else {
            TextArea({ placeholder: '这一页的笔记，Markdown 格式', text: this.note })
              .layoutWeight(1)
              .width('100%')
              .padding({ left: 16, right: 16, top: 64, bottom: 8 })
              .fontSize(15)
              .fontColor(UiTheme.ink(this.isDarkMode()))
              .textAlign(TextAlign.Start)
              // 中文排版优化（API 26+）：避孤字 + 标点悬挂；低版本运行时无此方法，只能条件挂载
              .attributeModifier(this.cnTypographyMod)
              .onChange((value: string) => {
                this.note = value;
                this.dirty = true;
              })
              .onTextSelectionChange((selectionStart: number, selectionEnd: number) => {
                this.selStart = selectionStart;
                this.selEnd = selectionEnd;
              })
          }

          if (this.statusText.length > 0) {
            Text(this.statusText)
              .fontSize(12)
              .fontColor(UiTheme.ink3(this.isDarkMode()))
              .padding({ left: 16, right: 16, bottom: 10 })
              .width('100%')
          }
          }
          .width('100%')
          .height('100%')

          Row() {
            IconButton({
              symbol: $r('sys.symbol.chevron_left'), label: '上一页',
              onTap: () => {
                this.goToPage(this.currentPage - 1);
              }
            })
            Text(`第 ${this.currentPage} 页`)
              .fontSize(14)
              .fontColor(UiTheme.ink2(this.isDarkMode()))
              .margin({ left: 8, right: 8 })
            IconButton({
              symbol: $r('sys.symbol.chevron_right'), label: '下一页',
              onTap: () => {
                this.goToPage(this.currentPage + 1);
              }
            })
            Blank()
            IconButton({
              symbol: this.previewMode ? $r('sys.symbol.square_and_pencil') : $r('sys.symbol.eye'),
              label: this.previewMode ? '编辑' : '预览',
              onTap: () => {
                this.previewMode = !this.previewMode;
              }
            })
            IconButton({
              symbol: $r('sys.symbol.magnifyingglass'), label: '搜索',
              onTap: () => {
                this.openSearch();
              }
            })
              .margin({ left: 8 })
            IconButton({
              symbol: $r('sys.symbol.arrow_counterclockwise_clock'), label: '历史',
              onTap: () => {
                this.openHistory();
              }
            })
              .margin({ left: 8 })
            IconButton({
              symbol: $r('sys.symbol.AI'), label: 'AI 讲解',
              onTap: () => {
                this.explainCurrentPage();
              }
            })
              .margin({ left: 8 })
            IconButton({
              symbol: $r('sys.symbol.picture'), label: '插图',
              onTap: () => {
                this.insertImage();
              }
            })
              .margin({ left: 8 })
            IconButton({
              symbol: $r('sys.symbol.rectangle_stack'), label: '卡片',
              onTap: () => {
                this.openCards();
              }
            })
              .margin({ left: 8 })
            IconButton({
              symbol: $r('sys.symbol.AI_expand'), label: 'AI 选区',
              onTap: () => {
                this.openAiAction();
              }
            })
              .margin({ left: 8 })
            IconButton({
              symbol: $r('sys.symbol.square_and_arrow_down_1'), label: '保存',
              filled: true, active: true, showDot: this.dirty,
              onTap: () => {
                this.saveCurrentPage();
              }
            })
              .margin({ left: 8 })
          }
          .width('100%')
          .padding({ left: 10, right: 10, top: 6, bottom: 6 })
          .margin({ left: 12, right: 12, top: 12, bottom: 8 })
          .alignItems(VerticalAlign.Center)
          .backgroundColor(UiTheme.pageBg(this.isDarkMode()))
          .borderRadius(22)
          .bindSheet($$this.showAiAction, this.aiActionBuilder(), {
            height: SheetSize.LARGE,
            title: { title: '选区 AI 动作' }
          })
        }
        .layoutWeight(1)
        .width(this.isLandscape ? '0%' : '100%')
        .height(this.isLandscape ? '100%' : '0%')
        .backgroundColor(UiTheme.cardBg(this.isDarkMode()))
        .borderRadius(UiTheme.RADIUS_CARD)
        .clip(true)
        .shadow(UiTheme.cardShadow(this.isDarkMode()))
        .margin(this.isLandscape ? { left: 8, right: 16, top: 8, bottom: 16 } :
          { left: 16, right: 16, top: 8, bottom: 16 })
        .bindSheet($$this.showAiSettings, this.aiSettingsBuilder(), {
          // FIT_CONTENT 实测会裁掉第三个输入框与按钮，改用固定半屏
          height: SheetSize.MEDIUM,
          title: { title: 'AI 模型设置' }
        })
  }

  @Builder
  leftPane() {
    // 左侧：源文档 + 手写批注层同处可平移层——手锁时手指拖动经 onPanDelta 整体平移，
    // 批注随内容一起移动，命中坐标与绘制坐标一致
    Stack() {
      Stack() {
      Column() {
        if (this.isPdf) {
          if (this.pdfPaneBuilder !== null) {
            this.pdfPaneBuilder.builder(this.pdfBridge)
          } else if (this.rainPageImages.length > 0) {
            Scroll() {
              Image(this.rainPageImages[this.currentPage - 1] ?? this.rainPageImages[0])
                .width('100%')
                .objectFit(ImageFit.Contain)
            }
            .layoutWeight(1)
            .width('100%')
            .align(Alignment.TopStart)
          } else {
            Column() {
              Text(this.statusText.length > 0 ? this.statusText : 'PDF 组件不可用')
                .fontSize(14)
                .fontColor(UiTheme.ink3(this.isDarkMode()))
                .padding(24)
            }
            .layoutWeight(1)
            .width('100%')
            .justifyContent(FlexAlign.Center)
          }
        } else {
          Scroll() {
            Text(this.sourceMarkdown)
              .fontSize(15)
              .fontColor(UiTheme.ink(this.isDarkMode()))
              .padding(16)
              .width('100%')
              .textAlign(TextAlign.Start)
          }
          .layoutWeight(1)
          .width('100%')
          .align(Alignment.TopStart)
        }
      }
      .width('100%')
      .height('100%')

      InkCanvas({
        items: this.inkItems,
        penColor: this.inkColor,
        penWidth: this.inkWidth,
        tool: this.inkTool,
        drawingEnabled: this.pageTurnMode === 'scroll' || (this.inkMode && this.pageTurnMode === 'tap'),
        fingerLock: this.pageTurnMode === 'scroll' || this.fingerLock,
        selectionEpoch: this.selectionEpoch,
        onChange: (items: AnnotationItem[]) => {
          this.inkItems = items;
          this.saveAnnotations();
        },
        onSelectionChange: (indices: number[]) => {
          this.selectedIndices = indices;
        },
        onTextTap: (x: number, y: number) => {
          this.inkTextPoint = [x, y];
          this.inkTextDraft = '';
          this.inkTextEditing = true;
        },
        onPanDelta: (dx: number, dy: number) => {
          if (this.pageTurnMode === 'scroll') {
            // 滑动翻页只跟纵向；内容上拉显示下一页、下拉显示上一页
            this.panY += dy;
          } else {
            this.panX += dx;
            this.panY += dy;
          }
        },
        onPanEnd: () => {
          if (this.pageTurnMode === 'scroll') {
            const threshold: number = 150;
            if (this.panY <= -threshold && this.currentPage < this.pageCount) {
              this.loadPage(this.currentPage + 1);
            } else if (this.panY >= threshold && this.currentPage > 1) {
              this.loadPage(this.currentPage - 1);
            } else {
              this.panY = 0;
            }
          }
        }
      })
        .id(INK_SNAPSHOT_ID)
        .width('100%')
        .height('100%')
      }
      .offset({ x: this.panX, y: this.panY })
      .width('100%')
      .height('100%')
    }
    .layoutWeight(1)
    .width(this.isLandscape ? '0%' : '100%')
    .height(this.isLandscape ? '100%' : '0%')
    .backgroundColor(UiTheme.cardBg(this.isDarkMode()))
    .borderRadius(UiTheme.RADIUS_CARD)
    .clip(true)
    .shadow(UiTheme.cardShadow(this.isDarkMode()))
    .margin(this.isLandscape ? { left: 16, right: 8, top: 8, bottom: 16 } :
      { left: 16, right: 16, top: 8, bottom: 8 })
    .bindSheet($$this.showAiResult, this.aiResultBuilder(), {
      height: SheetSize.LARGE,
      title: { title: `第 ${this.currentPage} 页讲解` }
    })
  }

  @Builder
  searchBuilder() {
    Column() {
      Search({ value: $$this.searchQuery, placeholder: '搜索全部笔记' + (this.isPdf ? '' : '与原文') })
        .searchButton('搜索')
        .margin({ top: 4, bottom: 8 })
        .onSubmit((value: string) => {
          this.searchQuery = value;
          this.runSearch();
        })
        .onChange((value: string) => {
          this.searchQuery = value;
          this.runSearch();
        })
      if (this.searchQuery.trim().length > 0 && this.searchResults.length === 0) {
        Text('没有匹配的内容')
          .fontSize(14)
          .fontColor(UiTheme.ink3(this.isDarkMode()))
          .padding(24)
      } else {
        List() {
          ForEach(this.searchResults, (hit: SearchHit) => {
            ListItem() {
              Column() {
                Row() {
                  Text(hit.source === '笔记' ? `第 ${hit.page} 页` : hit.source)
                    .fontSize(12)
                    .fontColor(UiTheme.PRIMARY)
                    .backgroundColor(UiTheme.primarySoft(this.isDarkMode()))
                    .borderRadius(4)
                    .padding({ left: 6, right: 6, top: 2, bottom: 2 })
                }
                .width('100%')
                Text(hit.snippet)
                  .fontSize(14)
                  .fontColor(UiTheme.ink(this.isDarkMode()))
                  .maxLines(2)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                  .width('100%')
                  .margin({ top: 4 })
              }
              .width('100%')
              .alignItems(HorizontalAlign.Start)
              .padding(12)
              .onClick(() => {
                this.jumpToHit(hit);
              })
            }
          }, (hit: SearchHit) => `${hit.source}-${hit.page}-${hit.snippet.length}`)
        }
        .width('100%')
        .layoutWeight(1)
      }
    }
    .width('100%')
    .height('100%')
    .padding({ left: 16, right: 16, bottom: 16 })
  }

  @Builder
  historyBuilder() {
    Column() {
      if (this.historyList.length === 0) {
        Text('这一页还没有历史快照，保存后自动生成')
          .fontSize(14)
          .fontColor(UiTheme.ink3(this.isDarkMode()))
          .padding(24)
      } else {
        List() {
          ForEach(this.historyList, (item: NoteVersion) => {
            ListItem() {
              Row() {
                Text(new Date(item.created).toLocaleString())
                  .fontSize(15)
                  .layoutWeight(1)
                Text(`${item.bytes} B`)
                  .fontSize(12)
                  .fontColor(UiTheme.ink3(this.isDarkMode()))
                Button('恢复')
                  .fontSize(13)
                  .height(30)
                  .margin({ left: 12 })
                  .onClick(() => {
                    this.restoreVersion(item);
                  })
              }
              .width('100%')
              .padding(12)
            }
          }, (item: NoteVersion) => item.id)
        }
        .width('100%')
        .constraintSize({ maxHeight: 400 })
      }
    }
    .padding({ left: 8, right: 8, bottom: 16 })
  }

  @Builder
  aiSettingsBuilder() {
    Column() {
      Text('使用 OpenAI 兼容接口。密钥只保存在本机应用偏好，不写入项目目录。')
        .fontSize(12)
        .fontColor(UiTheme.ink3(this.isDarkMode()))
        .width('100%')
        .margin({ bottom: 8 })
      TextInput({ placeholder: 'Base URL（默认本地中转 http://127.0.0.1:3457/v1）', text: $$this.aiBaseUrl })
        .margin({ top: 8 })
      TextInput({ placeholder: 'API Key', text: $$this.aiApiKey })
        .margin({ top: 8 })
      TextInput({ placeholder: '模型名（默认 cline-free/mimo-v2.6-flash）', text: $$this.aiModel })
        .margin({ top: 8 })
      Row() {
        Button('清空')
          .backgroundColor(UiTheme.controlIdle(this.isDarkMode()))
          .fontColor(UiTheme.ink(this.isDarkMode()))
          .onClick(() => {
            AgentSettings.clear(this.getUIContext().getHostContext() as common.UIAbilityContext);
            this.aiBaseUrl = '';
            this.aiApiKey = '';
            this.aiModel = '';
            this.showAiSettings = false;
          })
        Blank()
        Button('保存')
          .onClick(() => {
            AgentSettings.save(this.getUIContext().getHostContext() as common.UIAbilityContext, {
              baseUrl: this.aiBaseUrl,
              apiKey: this.aiApiKey,
              model: this.aiModel
            });
            this.showAiSettings = false;
            this.explainCurrentPage();
          })
      }
      .width('100%')
      .margin({ top: 16, bottom: 16 })
    }
    .padding({ left: 16, right: 16 })
  }

  @Builder
  aiResultBuilder() {
    Column() {
      if (this.aiBusy) {
        Text('正在生成讲解…')
          .fontSize(14)
          .fontColor(UiTheme.ink3(this.isDarkMode()))
          .padding(24)
      } else {
        MarkdownView({
          content: this.aiResult,
          resolveImage: (src: string): string => this.resolveAsset(src)
        })
          .layoutWeight(1)
          .width('100%')
      }
    }
    .width('100%')
  }
}

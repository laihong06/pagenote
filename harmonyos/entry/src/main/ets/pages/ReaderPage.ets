import { router, promptAction } from '@kit.ArkUI';
import { common } from '@kit.AbilityKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { ProjectStore, OpenProject } from '../core/ProjectStore';
import { PdfBridge } from '../core/PdfBridge';
import { PageNotes } from '../core/PageNotes';
import { ProjectValidation } from '../core/ProjectManifest';
import { NoteHistory, NoteVersion } from '../core/NoteHistory';
import { AgentSettings, ModelConfig, PageTutor, ModelEndpoint, SelectionActions, SelectionAction } from '../core/Agent';
import { Annotations, AnnotationBook, AnnotationItem } from '../core/Annotations';
import { FlashcardItem, FlashcardStore, FlashcardCapture, FlashcardDraft, ReviewOutcome } from '../core/Flashcards';
import { MarkdownView } from '../components/MarkdownView';
import { InkCanvas } from '../components/InkCanvas';

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
}

const INK_TOOLS: InkToolSpec[] = [
  { id: 'pen', label: '笔' },
  { id: 'highlight', label: '荧光' },
  { id: 'line', label: '直线' },
  { id: 'rect', label: '方框' },
  { id: 'ellipse', label: '椭圆' },
  { id: 'text', label: '文字' }
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
  @State inkTool: string = 'pen'; // pen | highlight | line | rect | ellipse | text
  @State inkWidth: number = 3;
  @State inkTextEditing: boolean = false;
  @State inkTextDraft: string = '';
  // 夜览滤镜：阅读区暖色遮罩（对齐桌面/Android 夜览语义，遮罩不拦截触摸）
  @State nightMode: boolean = false;
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

  private annotations: AnnotationBook = Annotations.empty();

  @State pdfPaneBuilder: WrappedBuilder<[PdfBridge]> | null = null;
  // PDF Kit 缺失时，雨课堂项目用来源图片降级显示（页序 = yuketang-source.json 的 projectPage）
  @State rainPageImages: string[] = [];

  private project: OpenProject | null = null;
  private noteRevision: string | null = null;
  // PDF Kit 只在 PdfPane（动态 import）里实例化，缺失 PDF Kit 的设备上本页仍可打开 Markdown 项目
  private pdfBridge: PdfBridge = { controller: null };
  private pdfLoaded: boolean = false;

  aboutToAppear(): void {
    const params = router.getParams() as ReaderParams;
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
    const saved = AgentSettings.load(getContext(this) as common.UIAbilityContext);
    this.aiBaseUrl = saved.baseUrl;
    this.aiApiKey = saved.apiKey;
    this.aiModel = saved.model;
    if (this.isPdf) {
      this.initPdf(this.loadRainFallback());
    } else {
      this.loadMarkdownSource();
    }
    this.loadPage(1);
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

  private loadPage(page: number): void {
    if (this.project === null) {
      return;
    }
    const notes = ProjectStore.readNotes(this.project.rightPath);
    this.note = notes.get(page) ?? '';
    this.currentPage = page;
    this.dirty = false;
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
      promptAction.showToast({ message: this.statusText });
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
      promptAction.showToast({ message: `恢复失败：${(e as Error).message}` });
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
    this.saveAnnotations();
  }

  private clearInk(): void {
    if (this.inkItems.length === 0) {
      return;
    }
    this.inkItems = [];
    this.saveAnnotations();
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
      promptAction.showToast({ message: '本页笔记为空，无可制卡内容' });
      return;
    }
    try {
      const draft = this.selEnd > this.selStart
        ? await FlashcardCapture.fromSelection(this.currentPage, this.note, this.selStart, this.selEnd)
        : await FlashcardCapture.fromParagraph(this.currentPage, this.note, this.selEnd);
      this.cardFront = draft.front;
      this.cardTab = 'make';
    } catch (e) {
      promptAction.showToast({ message: (e as Error).message });
    }
  }

  private async saveCard(): Promise<void> {
    if (this.project === null || this.cardFront.trim().length === 0) {
      promptAction.showToast({ message: '卡片正面不能为空' });
      return;
    }
    const tags = this.cardTags.split(/[,，]/).map((t: string): string => t.trim())
      .filter((t: string): boolean => t.length > 0 && t.length <= FlashcardCapture.MAX_TAG_LENGTH);
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
      promptAction.showToast({ message: '卡片已保存' });
    } catch (e) {
      promptAction.showToast({ message: `保存失败：${(e as Error).message}` });
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
        Button('返回')
          .backgroundColor('#EEEEEE')
          .fontColor('#333333')
          .onClick(() => {
            router.back();
          })
        Text(this.projectName)
          .fontSize(18)
          .fontWeight(FontWeight.Medium)
          .margin({ left: 12 })
          .layoutWeight(1)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        if (this.isPdf) {
          Text(`第 ${this.currentPage} / ${this.pageCount} 页`)
            .fontSize(14)
            .fontColor('#666666')
        }
        Button(this.inkMode ? '批注中' : '批注')
          .fontSize(13)
          .height(30)
          .margin({ left: 12 })
          .backgroundColor(this.inkMode ? '#2263EB' : '#EEEEEE')
          .fontColor(this.inkMode ? '#FFFFFF' : '#333333')
          .onClick(() => {
            this.inkMode = !this.inkMode;
          })
        Button(this.nightMode ? '夜览中' : '夜览')
          .fontSize(13)
          .height(30)
          .margin({ left: 8 })
          .backgroundColor(this.nightMode ? '#B45309' : '#EEEEEE')
          .fontColor(this.nightMode ? '#FFFFFF' : '#333333')
          .onClick(() => {
            this.nightMode = !this.nightMode;
          })
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 8, bottom: 8 })
      .alignItems(VerticalAlign.Center)
      // 同一节点挂多个 bindSheet 会导致全部失效（API 24 实测），每个 sheet 挂不同节点
      .bindSheet($$this.showSearch, this.searchBuilder(), {
        height: SheetSize.LARGE,
        title: { title: '搜索笔记' }
      })

      if (this.inkMode) {
        Row() {
          ForEach(INK_TOOLS, (spec: InkToolSpec) => {
            Button(spec.label)
              .fontSize(12)
              .height(28)
              .padding({ left: 8, right: 8 })
              .margin({ left: 6 })
              .backgroundColor(this.inkTool === spec.id ? '#2263EB' : '#EEEEEE')
              .fontColor(this.inkTool === spec.id ? '#FFFFFF' : '#333333')
              .onClick(() => {
                this.inkTool = spec.id;
              })
          }, (spec: InkToolSpec) => spec.id)
          ForEach(['#dc3545', '#2263EB', '#0E9F6E', '#222222'], (c: string) => {
            Circle()
              .width(22)
              .height(22)
              .fill(c)
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
              .padding({ left: 6, right: 6 })
              .margin({ left: 6 })
              .backgroundColor(this.inkWidth === spec.value ? '#B45309' : '#EEEEEE')
              .fontColor(this.inkWidth === spec.value ? '#FFFFFF' : '#333333')
              .onClick(() => {
                this.inkWidth = spec.value;
              })
          }, (spec: InkWidthSpec) => spec.label)
          Blank()
          Button('撤销')
            .fontSize(13)
            .height(30)
            .backgroundColor('#EEEEEE')
            .fontColor('#333333')
            .onClick(() => {
              this.undoInk();
            })
          Button('清除本页')
            .fontSize(13)
            .height(30)
            .backgroundColor('#EEEEEE')
            .fontColor('#333333')
            .margin({ left: 8 })
            .onClick(() => {
              this.clearInk();
            })
        }
        .width('100%')
        .padding({ left: 16, right: 16, bottom: 6 })
        .alignItems(VerticalAlign.Center)
      }

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
                promptAction.showToast({ message: '请输入批注内容' });
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
            .backgroundColor('#EEEEEE')
            .fontColor('#333333')
            .onClick(() => {
              this.inkTextEditing = false;
            })
        }
        .width('100%')
        .padding({ left: 16, right: 16, bottom: 6 })
        .alignItems(VerticalAlign.Center)
      }

      Stack() {
        Row() {
        // 左侧：源文档 + 手写批注层
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
                    .fontColor('#999999')
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
            drawingEnabled: this.inkMode,
            onChange: (items: AnnotationItem[]) => {
              this.inkItems = items;
              this.saveAnnotations();
            },
            onTextTap: (x: number, y: number) => {
              this.inkTextPoint = [x, y];
              this.inkTextDraft = '';
              this.inkTextEditing = true;
            }
          })
            .width('100%')
            .height('100%')
        }
        .layoutWeight(1)
        .height('100%')
        .backgroundColor('#FFFFFF')
        .border({ width: { right: 0.5 }, color: '#E0E0E0' })
        .bindSheet($$this.showAiResult, this.aiResultBuilder(), {
          height: SheetSize.LARGE,
          title: { title: `第 ${this.currentPage} 页讲解` }
        })

        // 右侧：逐页笔记
        Column() {
          Row() {
            Button('上一页')
              .fontSize(13)
              .height(32)
              .onClick(() => {
                this.goToPage(this.currentPage - 1);
              })
            Text(`第 ${this.currentPage} 页`)
              .fontSize(14)
              .margin({ left: 8, right: 8 })
            Button('下一页')
              .fontSize(13)
              .height(32)
              .onClick(() => {
                this.goToPage(this.currentPage + 1);
              })
            Blank()
            Button(this.previewMode ? '编辑' : '预览')
              .fontSize(13)
              .height(32)
              .backgroundColor('#EEEEEE')
              .fontColor('#333333')
              .onClick(() => {
                this.previewMode = !this.previewMode;
              })
            Button('搜索')
              .fontSize(13)
              .height(32)
              .backgroundColor('#EEEEEE')
              .fontColor('#333333')
              .margin({ left: 8 })
              .onClick(() => {
                this.openSearch();
              })
            Button('历史')
              .fontSize(13)
              .height(32)
              .backgroundColor('#EEEEEE')
              .fontColor('#333333')
              .margin({ left: 8 })
              .onClick(() => {
                this.openHistory();
              })
            Button('AI')
              .fontSize(13)
              .height(32)
              .backgroundColor('#EEEEEE')
              .fontColor('#333333')
              .margin({ left: 8 })
              .onClick(() => {
                this.explainCurrentPage();
              })
            Button('卡片')
              .fontSize(13)
              .height(32)
              .backgroundColor('#EEEEEE')
              .fontColor('#333333')
              .margin({ left: 8 })
              .onClick(() => {
                this.openCards();
              })
            Button('AI 选区')
              .fontSize(13)
              .height(32)
              .backgroundColor('#EEEEEE')
              .fontColor('#333333')
              .margin({ left: 8 })
              .onClick(() => {
                this.openAiAction();
              })
            Button(this.dirty ? '保存 *' : '保存')
              .fontSize(13)
              .height(32)
              .margin({ left: 8 })
              .onClick(() => {
                this.saveCurrentPage();
              })
          }
          .width('100%')
          .padding(8)
          .alignItems(VerticalAlign.Center)
          .bindSheet($$this.showAiAction, this.aiActionBuilder(), {
            height: SheetSize.LARGE,
            title: { title: '选区 AI 动作' }
          })

          if (this.previewMode) {
            MarkdownView({
              content: this.note,
              resolveImage: (src: string): string => this.resolveAsset(src)
            })
              .layoutWeight(1)
              .width('100%')
          } else {
            TextArea({ placeholder: '这一页的笔记，Markdown 格式', text: this.note })
              .layoutWeight(1)
              .width('100%')
              .padding(8)
              .fontSize(15)
              .textAlign(TextAlign.Start)
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
              .fontColor('#999999')
              .padding(8)
              .width('100%')
          }
        }
        .layoutWeight(1)
        .height('100%')
        .bindSheet($$this.showAiSettings, this.aiSettingsBuilder(), {
          height: SheetSize.FIT_CONTENT,
          title: { title: 'AI 模型设置' }
        })
      }
      .layoutWeight(1)
      .width('100%')

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
    .backgroundColor('#FAFAFA')
    // 根节点无其他 sheet，闪卡面板挂这里
    .bindSheet($$this.showCards, this.cardsBuilder(), {
      height: SheetSize.LARGE,
      title: { title: '闪卡' }
    })
  }

  @Builder
  cardsBuilder() {
    Column() {
      Row() {
        Button('制卡')
          .fontSize(13)
          .height(30)
          .backgroundColor(this.cardTab === 'make' ? '#2263EB' : '#EEEEEE')
          .fontColor(this.cardTab === 'make' ? '#FFFFFF' : '#333333')
          .onClick(() => {
            this.fillCardDraft();
          })
        Button(`复习(${FlashcardStore.dueCards(this.cards, Date.now()).length})`)
          .fontSize(13)
          .height(30)
          .margin({ left: 8 })
          .backgroundColor(this.cardTab === 'review' ? '#2263EB' : '#EEEEEE')
          .fontColor(this.cardTab === 'review' ? '#FFFFFF' : '#333333')
          .onClick(() => {
            this.startReview();
          })
        Button(`卡片库(${this.cards.length})`)
          .fontSize(13)
          .height(30)
          .margin({ left: 8 })
          .backgroundColor(this.cardTab === 'lib' ? '#2263EB' : '#EEEEEE')
          .fontColor(this.cardTab === 'lib' ? '#FFFFFF' : '#333333')
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
            .fontColor('#999999')
            .width('100%')
          TextArea({ placeholder: '卡片正面', text: $$this.cardFront })
            .height(100)
            .margin({ top: 4 })
          Text('背面（答案）')
            .fontSize(12)
            .fontColor('#999999')
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
            .fontColor('#999999')
            .padding(24)
        } else {
          Column() {
            Text(`第 ${this.reviewIndex + 1} / ${this.reviewQueue.length} 张 · 第 ${this.reviewQueue[this.reviewIndex].page} 页`)
              .fontSize(12)
              .fontColor('#999999')
              .width('100%')
            Text(this.reviewQueue[this.reviewIndex].front)
              .fontSize(17)
              .width('100%')
              .margin({ top: 12 })
              .textAlign(TextAlign.Center)
            if (this.reviewShowBack) {
              Divider().margin({ top: 12, bottom: 12 })
              Text(this.reviewQueue[this.reviewIndex].back.length > 0
                ? this.reviewQueue[this.reviewIndex].back : '（背面留空，回忆后自评）')
                .fontSize(15)
                .fontColor('#555555')
                .width('100%')
                .textAlign(TextAlign.Center)
              Row() {
                Button('没记住')
                  .backgroundColor('#C7254E')
                  .onClick(() => {
                    this.rateCard('again');
                  })
                Button('勉强')
                  .margin({ left: 8 })
                  .backgroundColor('#B45309')
                  .onClick(() => {
                    this.rateCard('hard');
                  })
                Button('记住')
                  .margin({ left: 8 })
                  .backgroundColor('#0E9F6E')
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
                    .fontColor('#999999')
                  Text(card.front)
                    .fontSize(14)
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
                  .backgroundColor('#EEEEEE')
                  .fontColor('#C7254E')
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
          .fontColor('#999999')
          .padding(24)
          .width('100%')
      } else {
        Text(`选区（第 ${this.currentPage} 页）：${this.selectedText().substring(0, 80)}${this.selectedText().length > 80 ? '…' : ''}`)
          .fontSize(12)
          .fontColor('#999999')
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
            .fontColor('#999999')
            .padding(16)
        }
        if (this.aiActionError.length > 0) {
          Text(this.aiActionError)
            .fontSize(13)
            .fontColor('#C7254E')
            .padding(8)
            .width('100%')
        }
        if (this.aiActionResult.length > 0) {
          Scroll() {
            Text(this.aiActionResult)
              .fontSize(14)
              .width('100%')
              .textAlign(TextAlign.Start)
          }
          .layoutWeight(1)
          .width('100%')
          .margin({ top: 8 })
          Row() {
            Text('确认后插回：')
              .fontSize(13)
              .fontColor('#666666')
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
              .backgroundColor('#EEEEEE')
              .fontColor('#333333')
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
          .fontColor('#999999')
          .padding(24)
      } else {
        List() {
          ForEach(this.searchResults, (hit: SearchHit) => {
            ListItem() {
              Column() {
                Row() {
                  Text(hit.source === '笔记' ? `第 ${hit.page} 页` : hit.source)
                    .fontSize(12)
                    .fontColor('#2263EB')
                    .backgroundColor('#EAF0FE')
                    .borderRadius(4)
                    .padding({ left: 6, right: 6, top: 2, bottom: 2 })
                }
                .width('100%')
                Text(hit.snippet)
                  .fontSize(14)
                  .fontColor('#333333')
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
          .fontColor('#999999')
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
                  .fontColor('#999999')
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
        .fontColor('#999999')
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
          .backgroundColor('#EEEEEE')
          .fontColor('#333333')
          .onClick(() => {
            AgentSettings.clear(getContext(this) as common.UIAbilityContext);
            this.aiBaseUrl = '';
            this.aiApiKey = '';
            this.aiModel = '';
            this.showAiSettings = false;
          })
        Blank()
        Button('保存')
          .onClick(() => {
            AgentSettings.save(getContext(this) as common.UIAbilityContext, {
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
          .fontColor('#999999')
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

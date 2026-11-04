import { router, promptAction } from '@kit.ArkUI';
import { common } from '@kit.AbilityKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { ProjectStore, OpenProject } from '../core/ProjectStore';
import { PdfBridge } from '../core/PdfBridge';
import { PageNotes } from '../core/PageNotes';
import { ProjectValidation } from '../core/ProjectManifest';
import { NoteHistory, NoteVersion } from '../core/NoteHistory';
import { AgentSettings, ModelConfig, PageTutor, ModelEndpoint } from '../core/Agent';
import { Annotations, AnnotationBook, AnnotationItem } from '../core/Annotations';
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
          Text('批注')
            .fontSize(13)
            .fontColor('#2263EB')
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
            drawingEnabled: this.inkMode,
            onChange: (items: AnnotationItem[]) => {
              this.inkItems = items;
              this.saveAnnotations();
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
      .bindSheet($$this.showHistory, this.historyBuilder(), {
        height: SheetSize.FIT_CONTENT,
        title: { title: `第 ${this.currentPage} 页历史` }
      })
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#FAFAFA')
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

import { router, promptAction } from '@kit.ArkUI';
import { common } from '@kit.AbilityKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { PdfView, pdfViewManager, pdfService } from '@kit.PDFKit';
import { ProjectStore, OpenProject } from '../core/ProjectStore';
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

  private project: OpenProject | null = null;
  private noteRevision: string | null = null;
  private pdfController: pdfViewManager.PdfController = new pdfViewManager.PdfController();
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
      this.loadPdf();
    } else {
      this.loadMarkdownSource();
    }
    this.loadPage(1);
  }

  aboutToDisappear(): void {
    if (this.pdfLoaded) {
      this.pdfController.releaseDocument();
      this.pdfLoaded = false;
    }
  }

  private historyDir(): string {
    return `${this.project!.dir}/src/history`;
  }

  private loadPdf(): void {
    if (this.project === null) {
      return;
    }
    const path = this.project.leftPath;
    // loadDocument 需要组件挂载完成，用 setTimeout 让出一帧
    setTimeout(() => {
      this.pdfController.loadDocument(path, undefined, 0)
        .then(() => {
          this.pdfLoaded = true;
          this.pageCount = this.pdfController.getPageCount();
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
    if (this.isPdf && this.pdfLoaded) {
      this.pdfController.goToPage(page - 1);
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
              PdfView({
                controller: this.pdfController,
                pageFit: pdfService.PageFit.FIT_WIDTH,
                pageLayout: pdfService.PageLayout.LAYOUT_SINGLE,
                isContinuous: false,
                showScroll: true
              })
                .layoutWeight(1)
                .width('100%')
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
            MarkdownView({ content: this.note })
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
      }
      .layoutWeight(1)
      .width('100%')
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#FAFAFA')
    .bindSheet($$this.showHistory, this.historyBuilder(), {
      height: SheetSize.FIT_CONTENT,
      title: { title: `第 ${this.currentPage} 页历史` }
    })
    .bindSheet($$this.showAiSettings, this.aiSettingsBuilder(), {
      height: SheetSize.FIT_CONTENT,
      title: { title: 'AI 模型设置' }
    })
    .bindSheet($$this.showAiResult, this.aiResultBuilder(), {
      height: SheetSize.LARGE,
      title: { title: `第 ${this.currentPage} 页讲解` }
    })
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
        MarkdownView({ content: this.aiResult })
          .layoutWeight(1)
          .width('100%')
      }
    }
    .width('100%')
  }
}

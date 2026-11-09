import { common } from '@kit.AbilityKit';
import { picker, fileIo as fs } from '@kit.CoreFileKit';
import { image } from '@kit.ImageKit';
import { ProjectStore, ProjectSummary, OpenProject } from '../core/ProjectStore';
import { RainClient, RainLoginState, RainLiveState } from '../core/RainClient';
import { RainCourse, RainLesson, RainSlideImage, RainCurrentLesson } from '../core/RainProtocol';
import { RainProjectWriter } from '../core/RainProjectWriter';

/**
 * 书架页：列出沙箱项目库中的全部项目，支持新建项目与导入文档建项目。
 * 项目统一存放在 context.filesDir/projects/ 下，跨设备迁移复制整个项目目录。
 */
@Entry
@Component
struct BookshelfPage {
  @State projects: ProjectSummary[] = [];
  @State showCreateDialog: boolean = false;
  @State newProjectName: string = '';
  @State errorText: string = '';
  // 雨课堂面板
  @State showRain: boolean = false;
  @State rainLoginStatus: string = 'signed_out';
  @State rainQr: PixelMap | null = null;
  @State rainAccount: string = '';
  @State rainCourses: RainCourse[] = [];
  @State rainLessons: RainLesson[] = [];
  @State rainSelectedCourse: string = '';
  @State rainBusy: boolean = false;
  @State rainProgress: string = '';
  @State rainError: string = '';
  @State rainCurrentLessons: RainCurrentLesson[] = [];
  @State rainLiveStatus: string = '';
  @State rainLiveText: string = '';
  // 系统横向断点（EntryAbility 在 loadContent 回调内注册并持续更新）
  @StorageProp('currentWidthBreakpoint') widthBp: WidthBreakpoint = WidthBreakpoint.WIDTH_LG;

  private rainClient: RainClient | null = null;
  private liveProjectDir: string | null = null;

  private rain(): RainClient {
    if (this.rainClient === null) {
      this.rainClient = new RainClient(this.getUIContext().getHostContext() as common.UIAbilityContext);
    }
    return this.rainClient;
  }

  private projectsRoot(): string {
    return ProjectStore.projectsRoot((this.getUIContext().getHostContext() as common.UIAbilityContext).filesDir);
  }

  aboutToAppear(): void {
    this.reload();
  }

  onPageShow(): void {
    this.reload();
  }

  private reload(): void {
    this.projects = ProjectStore.list(this.projectsRoot());
  }

  private createProject(name: string): void {
    ProjectStore.create(this.projectsRoot(), name)
      .then((project: OpenProject) => {
        this.reload();
        this.getUIContext().getRouter().pushUrl({ url: 'pages/ReaderPage', params: { dir: project.dir } });
      })
      .catch((e: Error) => {
        this.getUIContext().getPromptAction().showToast({ message: `创建失败：${e.message}` });
      });
  }

  private importDocument(): void {
    const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
    const docPicker = new picker.DocumentViewPicker(context);
    const options = new picker.DocumentSelectOptions();
    options.maxSelectNumber = 1;
    options.selectMode = picker.DocumentSelectMode.FILE;
    options.fileSuffixFilters = ['PDF(.pdf)', 'Markdown(.md,.markdown)', '所有文件(.*)'];
    docPicker.select(options).then((uris: string[]) => {
      if (uris.length === 0) {
        return;
      }
      this.importIntoNewProject(uris[0]);
    }).catch(() => {
      // 用户取消选择
    });
  }

  private importIntoNewProject(uri: string): void {    const decoded = decodeURIComponent(uri);
    const slash = decoded.lastIndexOf('/');
    const fileName = slash >= 0 ? decoded.substring(slash + 1) : decoded;
    const dot = fileName.lastIndexOf('.');
    const base = dot > 0 ? fileName.substring(0, dot) : fileName;
    const ext = dot > 0 ? fileName.substring(dot + 1).toLowerCase() : '';
    ProjectStore.create(this.projectsRoot(), base)
      .then((project: OpenProject) => {
        const isMd = ext === 'md' || ext === 'markdown';
        const targetName = isMd ? 'left.md' : `left.pdf`;
        const targetPath = `${project.dir}/src/${targetName}`;
        const src = fs.openSync(uri, fs.OpenMode.READ_ONLY);
        try {
          fs.copyFileSync(src.fd, targetPath);
        } finally {
          fs.closeSync(src);
        }
        project.manifest.left = `src/${targetName}`;
        project.manifest.leftName = fileName;
        ProjectStore.saveManifest(project.dir, project.manifest);
        this.reload();
        this.getUIContext().getRouter().pushUrl({ url: 'pages/ReaderPage', params: { dir: project.dir } });
      })
      .catch((e: Error) => {
        this.getUIContext().getPromptAction().showToast({ message: `导入失败：${e.message}` });
      });
  }

  // ---- 雨课堂 ----

  private openRain(): void {
    this.showRain = true;
    this.rainError = '';
    this.rainProgress = '';
    this.rainSelectedCourse = '';
    this.rainLessons = [];
    this.rain().refreshLogin()
      .then((state: RainLoginState) => {
        this.rainLoginStatus = state.status;
        if (state.status === 'authenticated') {
          this.rainAccount = state.accountName ?? '雨课堂用户';
          this.loadRainCourses();
        }
      })
      .catch((e: Error) => {
        this.rainLoginStatus = 'signed_out';
        this.rainError = e.message;
      });
  }

  private startRainLogin(): void {
    this.rainError = '';
    this.rain().startLogin((state: RainLoginState) => {
      this.onRainLoginState(state);
    });
  }

  private onRainLoginState(state: RainLoginState): void {
    this.rainLoginStatus = state.status;
    this.rainError = state.error ?? '';
    if (state.status === 'waiting_scan' && state.qrImage !== null) {
      const source = image.createImageSource(state.qrImage);
      source.createPixelMap().then((pm: PixelMap) => {
        this.rainQr = pm;
        source.release();
      }).catch(() => {
        this.rainError = '二维码图片解码失败';
      });
    }
    if (state.status === 'authenticated') {
      this.rainAccount = state.accountName ?? '雨课堂用户';
      this.rainQr = null;
      this.loadRainCourses();
    }
  }

  private cancelRainLogin(): void {
    this.rain().cancelLogin();
    this.rainLoginStatus = 'signed_out';
    this.rainQr = null;
  }

  private logoutRain(): void {
    this.rain().logout();
    this.rainLoginStatus = 'signed_out';
    this.rainAccount = '';
    this.rainCourses = [];
    this.rainLessons = [];
    this.rainSelectedCourse = '';
  }

  private loadRainCourses(): void {
    this.rainBusy = true;
    this.rain().listCurrentLessons()
      .then((lessons: RainCurrentLesson[]) => {
        this.rainCurrentLessons = lessons;
      })
      .catch(() => {
        this.rainCurrentLessons = [];
      });
    this.rain().listCourses()
      .then((courses: RainCourse[]) => {
        this.rainCourses = courses;
        this.rainError = courses.length === 0 ? '这个账号下没有课程' : '';
      })
      .catch((e: Error) => {
        this.rainError = e.message;
      })
      .finally(() => {
        this.rainBusy = false;
      });
  }

  private selectRainCourse(course: RainCourse): void {
    this.rainSelectedCourse = course.classroomId;
    this.rainLessons = [];
    this.rainBusy = true;
    this.rainError = '';
    this.rain().listLessons(course.classroomId)
      .then((lessons: RainLesson[]) => {
        this.rainLessons = lessons;
        if (lessons.length === 0) {
          this.rainError = '这门课还没有历史课件';
        }
      })
      .catch((e: Error) => {
        this.rainError = e.message;
      })
      .finally(() => {
        this.rainBusy = false;
      });
  }

  /** 项目名只允许安全字符；与 ProjectStore.newManifest 的 BAD_NAME 一致。 */
  private sanitizeRainProjectName(name: string): string {
    const cleaned = name.replace(/[<>:"/\\|?*]/g, '_').trim();
    return cleaned.length > 0 ? cleaned : '雨课堂课件';
  }

  private rainLiveStatusText(status: string): string {
    switch (status) {
      case 'joining': return '正在进入课堂（进入行为可能记录签到）';
      case 'connecting': return '正在连接课堂';
      case 'active': return '正在实时采集';
      case 'reconnecting': return '连接断开，重连中';
      case 'finished': return '课堂已结束';
      case 'stopped': return '已停止采集';
      case 'error': return '采集出错';
      default: return status;
    }
  }

  /** 开始实时采集：首条页面创建项目，后续页面追加。课程匹配不上时不静默借用其他课程。 */
  private async startRainLive(current: RainCurrentLesson): Promise<void> {
    let course = this.rainCourses.find(
      (item: RainCourse): boolean => item.classroomId === current.classroomId);
    if (course === undefined && current.classroomId === null) {
      course = this.rainCourses.find(
        (item: RainCourse): boolean => item.courseId === current.courseId);
    }
    if (course === undefined) {
      this.rainError = '无法将该课堂关联到你的课程，请确认课程列表已刷新';
      return;
    }
    const lesson: RainLesson = {
      classroomId: current.classroomId ?? course.classroomId,
      lessonId: current.lessonId,
      presentationId: '',
      name: current.name,
      createdAt: null
    };
    this.liveProjectDir = null;
    this.rainError = '';
    try {
      await this.rain().startLive(lesson,
        async (image: RainSlideImage): Promise<void> => {
          if (this.liveProjectDir === null) {
            let candidate = this.sanitizeRainProjectName(lesson.name);
            let suffix = 2;
            while (this.projects.some((item: ProjectSummary): boolean => item.name === candidate)) {
              candidate = `${this.sanitizeRainProjectName(lesson.name)}(${suffix})`;
              suffix += 1;
            }
            const project = await RainProjectWriter.create(this.projectsRoot(), candidate,
              course!, lesson, [image]);
            this.liveProjectDir = project.dir;
            this.reload();
          } else {
            await RainProjectWriter.insert(this.liveProjectDir, course!, lesson, image);
          }
        },
        (state: RainLiveState) => {
          this.rainLiveStatus = state.status === 'stopped' ? '' : state.status;
          let text = `${this.rainLiveStatusText(state.status)}，已采集 ${state.collected} 页`;
          if (state.pending > 0) {
            text += `，待写入 ${state.pending} 页`;
          }
          if (state.error !== null) {
            text += `：${state.error}`;
          }
          this.rainLiveText = text;
        });
    } catch (e) {
      this.rainError = (e as Error).message;
      this.rainLiveStatus = '';
      this.rainLiveText = '';
    }
  }

  private stopRainLive(): void {
    this.rain().stopLive();
    this.rainLiveStatus = '';
    this.rainLiveText = '';
  }

  private async importRainLesson(course: RainCourse, lesson: RainLesson): Promise<void> {
    const name = this.sanitizeRainProjectName(lesson.name);
    if (this.projects.some((item: ProjectSummary): boolean => item.name === name)) {
      this.rainError = `已存在同名项目：${name}`;
      return;
    }
    this.rainBusy = true;
    this.rainError = '';
    try {
      this.rainProgress = '正在读取课件列表…';
      const slides = await this.rain().loadImportSlides(lesson);
      const images: RainSlideImage[] = [];
      for (let i = 0; i < slides.length; i++) {
        this.rainProgress = `正在下载 ${i + 1} / ${slides.length} 页…`;
        images.push(await this.rain().downloadSlideImage(slides[i]));
      }
      const project = await RainProjectWriter.create(this.projectsRoot(), name, course, lesson, images,
        (done: number, total: number) => {
          this.rainProgress = `正在生成项目 ${done} / ${total} 页…`;
        });
      this.showRain = false;
      this.reload();
      this.getUIContext().getRouter().pushUrl({ url: 'pages/ReaderPage', params: { dir: project.dir } });
    } catch (e) {
      this.rainError = (e as Error).message;
      this.rainProgress = '';
    } finally {
      this.rainBusy = false;
    }
  }

  build() {
    Column() {
      Row() {
        Text('PageNote 书架')
          .fontSize(24)
          .fontWeight(FontWeight.Bold)
        Blank()
        Button('雨课堂')
          .onClick(() => {
            this.openRain();
          })
        Button('导入文档')
          .margin({ left: 12 })
          .onClick(() => {
            this.importDocument();
          })
        Button('新建项目')
          .margin({ left: 12 })
          .onClick(() => {
            this.newProjectName = '';
            this.showCreateDialog = true;
          })
      }
      .width('100%')
      .padding(16)
      .alignItems(VerticalAlign.Center)
      .backgroundColor('#FAFAFA')
      // 标题栏背景延伸至状态栏区域，避免沉浸式下出现色带
      .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP])
      // 同节点多 bindSheet 会全部失效，雨课堂面板挂到标题栏节点
      .bindSheet($$this.showRain, this.rainBuilder(), {
        height: SheetSize.LARGE,
        title: { title: '雨课堂' }
      })

      if (this.projects.length === 0) {
        Column() {
          Text('还没有项目')
            .fontSize(18)
            .fontColor('#666666')
          Text('新建项目，或导入 PDF / Markdown 文档开始逐页笔记')
            .fontSize(14)
            .fontColor('#999999')
            .margin({ top: 8 })
        }
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
        .width('100%')
      } else {
        List() {
          ForEach(this.projects, (item: ProjectSummary) => {
            ListItem() {
              Row() {
                Text(item.kind === 'pdf' ? 'PDF' : 'MD')
                  .fontSize(12)
                  .fontColor('#FFFFFF')
                  .backgroundColor(item.kind === 'pdf' ? '#2263EB' : '#0E9F6E')
                  .borderRadius(4)
                  .padding({ left: 6, right: 6, top: 2, bottom: 2 })
                Column() {
                  Text(item.name)
                    .fontSize(18)
                    .maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                  Text(item.modified ?? '')
                    .fontSize(12)
                    .fontColor('#999999')
                    .margin({ top: 4 })
                }
                .alignItems(HorizontalAlign.Start)
                .margin({ left: 12 })
                .layoutWeight(1)
              }
              .width('100%')
              .padding(16)
              .alignItems(VerticalAlign.Center)
            }
            .hoverEffect(HoverEffect.Highlight)
            .onClick(() => {
              this.getUIContext().getRouter().pushUrl({ url: 'pages/ReaderPage', params: { dir: item.dir } });
            })
          }, (item: ProjectSummary) => item.id)
        }
        .layoutWeight(1)
        .width('100%')
        // 重复布局：书架列数随横向断点递增（基线 1 列 → md 2 列 → lg/xl 3 列）
        .lanes(this.widthBp >= WidthBreakpoint.WIDTH_LG ? 3 :
          this.widthBp >= WidthBreakpoint.WIDTH_MD ? 2 : 1)
        .divider({ strokeWidth: 0.5, color: '#E5E5E5' })
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#FAFAFA')
    .bindSheet($$this.showCreateDialog, this.createDialogBuilder(), {
      height: SheetSize.FIT_CONTENT,
      title: { title: '新建项目' }
    })
  }

  @Builder
  rainBuilder() {
    Column() {
      if (this.rainProgress.length > 0) {
        Text(this.rainProgress)
          .fontSize(13)
          .fontColor('#2263EB')
          .width('100%')
          .padding({ top: 4, bottom: 4 })
      }
      if (this.rainError.length > 0) {
        Text(this.rainError)
          .fontSize(13)
          .fontColor('#C7254E')
          .width('100%')
          .padding({ top: 4, bottom: 4 })
      }

      if (this.rainLoginStatus !== 'authenticated') {
        // 登录区
        Column() {
          if (this.rainQr !== null) {
            Image(this.rainQr)
              .width(220)
              .height(220)
              .margin({ top: 12 })
            Text('用微信扫描雨课堂官方二维码完成登录')
              .fontSize(13)
              .fontColor('#666666')
              .margin({ top: 8 })
          } else {
            Text(this.rainLoginStatus === 'connecting' || this.rainLoginStatus === 'loading_qr'
              ? '正在获取雨课堂登录二维码…'
              : '登录后可将雨课堂课件导入为项目')
              .fontSize(14)
              .fontColor('#666666')
              .margin({ top: 16 })
          }
          Row() {
            if (this.rainLoginStatus !== 'waiting_scan' && this.rainLoginStatus !== 'connecting'
              && this.rainLoginStatus !== 'loading_qr') {
              Button(this.rainLoginStatus === 'expired' ? '重新生成二维码' : '登录雨课堂')
                .margin({ top: 16 })
                .enabled(!this.rainBusy)
                .onClick(() => {
                  this.startRainLogin();
                })
            }
            if (this.rainLoginStatus === 'waiting_scan' || this.rainLoginStatus === 'connecting'
              || this.rainLoginStatus === 'loading_qr') {
              Button('取消')
                .margin({ top: 16, left: 12 })
                .backgroundColor('#EEEEEE')
                .fontColor('#333333')
                .onClick(() => {
                  this.cancelRainLogin();
                })
            }
          }
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else {
        // 已登录：账号条 + 课程/课堂列表
        Row() {
          Text(this.rainAccount)
            .fontSize(15)
            .fontWeight(FontWeight.Medium)
            .layoutWeight(1)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          Button('刷新')
            .fontSize(13)
            .height(30)
            .backgroundColor('#EEEEEE')
            .fontColor('#333333')
            .enabled(!this.rainBusy)
            .onClick(() => {
              this.loadRainCourses();
            })
          Button('退出登录')
            .fontSize(13)
            .height(30)
            .backgroundColor('#EEEEEE')
            .fontColor('#333333')
            .margin({ left: 8 })
            .enabled(!this.rainBusy)
            .onClick(() => {
              this.logoutRain();
            })
        }
        .width('100%')
        .padding({ top: 4, bottom: 8 })

        if (this.rainLiveText.length > 0) {
          Row() {
            Text(this.rainLiveText)
              .fontSize(13)
              .fontColor('#2263EB')
              .layoutWeight(1)
            Button('停止采集')
              .fontSize(13)
              .height(30)
              .backgroundColor('#EEEEEE')
              .fontColor('#333333')
              .onClick(() => {
                this.stopRainLive();
              })
          }
          .width('100%')
          .padding({ bottom: 8 })
        }

        if (this.rainCurrentLessons.length > 0 && this.rainSelectedCourse.length === 0
          && this.rainLiveStatus.length === 0) {
          Text('正在进行的课堂')
            .fontSize(13)
            .fontColor('#999999')
            .width('100%')
            .padding({ bottom: 4 })
          ForEach(this.rainCurrentLessons, (current: RainCurrentLesson) => {
            Row() {
              Text(current.name)
                .fontSize(15)
                .layoutWeight(1)
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
              Button('开始采集')
                .fontSize(13)
                .height(30)
                .margin({ left: 12 })
                .enabled(!this.rainBusy)
                .onClick(() => {
                  this.startRainLive(current);
                })
            }
            .width('100%')
            .padding({ top: 6, bottom: 6 })
          }, (current: RainCurrentLesson) => current.lessonId)
          Divider().margin({ top: 8, bottom: 8 })
          Text('历史课件')
            .fontSize(13)
            .fontColor('#999999')
            .width('100%')
            .padding({ bottom: 4 })
        }

        if (this.rainSelectedCourse.length === 0) {
          List() {
            ForEach(this.rainCourses, (course: RainCourse) => {
              ListItem() {
                Column() {
                  Text(course.name)
                    .fontSize(16)
                    .maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                  Text(`${course.teacher}${course.term.length > 0 ? ` · ${course.term}` : ''}`)
                    .fontSize(12)
                    .fontColor('#999999')
                    .margin({ top: 4 })
                }
                .width('100%')
                .alignItems(HorizontalAlign.Start)
                .padding(12)
              }
              .hoverEffect(HoverEffect.Highlight)
              .onClick(() => {
                this.selectRainCourse(course);
              })
            }, (course: RainCourse) => course.classroomId)
          }
          .width('100%')
          .layoutWeight(1)
          .divider({ strokeWidth: 0.5, color: '#E5E5E5' })
        } else {
          Row() {
            Button('返回课程列表')
              .fontSize(13)
              .height(30)
              .backgroundColor('#EEEEEE')
              .fontColor('#333333')
              .enabled(!this.rainBusy)
              .onClick(() => {
                this.rainSelectedCourse = '';
                this.rainLessons = [];
                this.rainError = '';
              })
          }
          .width('100%')
          .padding({ bottom: 8 })
          List() {
            ForEach(this.rainLessons, (lesson: RainLesson) => {
              ListItem() {
                Row() {
                  Column() {
                    Text(lesson.name)
                      .fontSize(16)
                      .maxLines(1)
                      .textOverflow({ overflow: TextOverflow.Ellipsis })
                    Text(lesson.createdAt ?? '')
                      .fontSize(12)
                      .fontColor('#999999')
                      .margin({ top: 4 })
                  }
                  .alignItems(HorizontalAlign.Start)
                  .layoutWeight(1)
                  Button('导入')
                    .fontSize(13)
                    .height(30)
                    .margin({ left: 12 })
                    .enabled(!this.rainBusy)
                    .onClick(() => {
                      const course = this.rainCourses.find(
                        (item: RainCourse): boolean => item.classroomId === this.rainSelectedCourse);
                      if (course !== undefined) {
                        this.importRainLesson(course, lesson);
                      }
                    })
                }
                .width('100%')
                .padding(12)
              }
              .hoverEffect(HoverEffect.Highlight)
            }, (lesson: RainLesson) => `${lesson.presentationId}-${lesson.lessonId ?? ''}`)
          }
          .width('100%')
          .layoutWeight(1)
          .divider({ strokeWidth: 0.5, color: '#E5E5E5' })
        }
      }
    }
    .width('100%')
    .height('100%')
    .padding({ left: 16, right: 16, bottom: 16 })
  }

  @Builder
  createDialogBuilder() {
    Column() {
      TextInput({ placeholder: '项目名称', text: $$this.newProjectName })
        .margin({ top: 8 })
      Row() {
        Button('取消')
          .backgroundColor('#CCCCCC')
          .onClick(() => {
            this.showCreateDialog = false;
          })
        Button('创建')
          .margin({ left: 12 })
          .onClick(() => {
            const name = this.newProjectName.trim();
            if (name.length === 0) {
              this.getUIContext().getPromptAction().showToast({ message: '请输入项目名称' });
              return;
            }
            this.showCreateDialog = false;
            this.createProject(name);
          })
      }
      .margin({ top: 16, bottom: 16 })
      .justifyContent(FlexAlign.End)
      .width('100%')
    }
    .padding({ left: 16, right: 16 })
  }
}

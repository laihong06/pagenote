import { router } from '@kit.ArkUI';
import { common } from '@kit.AbilityKit';
import { picker, fileIo as fs } from '@kit.CoreFileKit';
import { promptAction } from '@kit.ArkUI';
import { ProjectStore, ProjectSummary, OpenProject } from '../core/ProjectStore';

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

  private projectsRoot(): string {
    return ProjectStore.projectsRoot(getContext(this).filesDir);
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
        router.pushUrl({ url: 'pages/ReaderPage', params: { dir: project.dir } });
      })
      .catch((e: Error) => {
        promptAction.showToast({ message: `创建失败：${e.message}` });
      });
  }

  private importDocument(): void {
    const context = getContext(this) as common.UIAbilityContext;
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

  private importIntoNewProject(uri: string): void {
    const decoded = decodeURIComponent(uri);
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
        router.pushUrl({ url: 'pages/ReaderPage', params: { dir: project.dir } });
      })
      .catch((e: Error) => {
        promptAction.showToast({ message: `导入失败：${e.message}` });
      });
  }

  build() {
    Column() {
      Row() {
        Text('PageNote 书架')
          .fontSize(24)
          .fontWeight(FontWeight.Bold)
        Blank()
        Button('导入文档')
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
            .onClick(() => {
              router.pushUrl({ url: 'pages/ReaderPage', params: { dir: item.dir } });
            })
          }, (item: ProjectSummary) => item.id)
        }
        .layoutWeight(1)
        .width('100%')
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
              promptAction.showToast({ message: '请输入项目名称' });
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

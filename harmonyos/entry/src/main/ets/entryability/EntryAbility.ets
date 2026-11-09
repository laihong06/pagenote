import { AbilityConstant, UIAbility, Want } from '@kit.AbilityKit';
import { mediaquery, window } from '@kit.ArkUI';
import { hilog } from '@kit.PerformanceAnalysisKit';

const DOMAIN: number = 0x4e50;
const TAG: string = 'PageNote';

export default class EntryAbility extends UIAbility {
  private mainWindow: window.Window | null = null;
  private sizeChangeCallback: (size: window.Size) => void = () => {};
  private darkQuery: mediaquery.MediaQueryListener | null = null;
  private darkChangeCallback: (result: mediaquery.MediaQueryResult) => void = () => {};

  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    hilog.info(DOMAIN, TAG, 'EntryAbility onCreate');
  }

  /** 状态栏/导航条内容色跟随应用主题：自定义深色背景下系统不会自动反转（本镜像 config 恒 LIGHT）。 */
  private applySystemBarStyle(dark: boolean): void {
    if (this.mainWindow === null) {
      return;
    }
    try {
      this.mainWindow.setWindowSystemBarProperties({
        statusBarContentColor: dark ? '#FFFFFF' : '#000000',
        navigationBarContentColor: dark ? '#FFFFFF' : '#000000'
      });
    } catch (e) {
      // 部分窗口模式下不支持，忽略
    }
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    windowStage.loadContent('pages/BookshelfPage', (err) => {
      if (err.code) {
        hilog.error(DOMAIN, TAG, `loadContent failed: ${JSON.stringify(err)}`);
        return;
      }
      // getMainWindowSync 必须在 loadContent 回调内调用，否则拿到 0x0 窗口
      const mainWindow = windowStage.getMainWindowSync();
      this.mainWindow = mainWindow;
      const uiContext = mainWindow.getUIContext();
      AppStorage.setOrCreate('currentWidthBreakpoint', uiContext.getWindowWidthBreakpoint());
      const initRect = mainWindow.getWindowProperties().windowRect;
      AppStorage.setOrCreate('screenWidthVp', uiContext.px2vp(initRect.width));
      AppStorage.setOrCreate('screenHeightVp', uiContext.px2vp(initRect.height));
      this.sizeChangeCallback = (size: window.Size): void => {
        AppStorage.setOrCreate('currentWidthBreakpoint', uiContext.getWindowWidthBreakpoint());
        AppStorage.setOrCreate('screenWidthVp', uiContext.px2vp(size.width));
        AppStorage.setOrCreate('screenHeightVp', uiContext.px2vp(size.height));
      };
      mainWindow.on('windowSizeChange', this.sizeChangeCallback);

      // 深浅色跟随系统：media query (dark-mode) 通道。
      // 不用 Environment.envProp('colorMode') —— 本镜像上该值恒为 0 不随系统切换（实测），
      // media query 深/浅两态均正确且支持 change 监听，运行中切换也能收到。
      this.darkQuery = uiContext.getMediaQuery().matchMediaSync('(dark-mode: true)');
      AppStorage.setOrCreate('isDarkMode', this.darkQuery.matches);
      this.applySystemBarStyle(this.darkQuery.matches);
      this.darkChangeCallback = (result: mediaquery.MediaQueryResult): void => {
        AppStorage.setOrCreate('isDarkMode', result.matches);
        this.applySystemBarStyle(result.matches);
      };
      this.darkQuery.on('change', this.darkChangeCallback);
    });
  }

  onWindowStageDestroy(): void {
    if (this.mainWindow !== null) {
      try {
        this.mainWindow.off('windowSizeChange', this.sizeChangeCallback);
      } catch (e) {
        // 窗口已销毁时 off 会抛 1300002（Window is nullptr），忽略
      }
      this.mainWindow = null;
    }
    if (this.darkQuery !== null) {
      this.darkQuery.off('change', this.darkChangeCallback);
      this.darkQuery = null;
    }
    hilog.info(DOMAIN, TAG, 'EntryAbility onWindowStageDestroy');
  }
}

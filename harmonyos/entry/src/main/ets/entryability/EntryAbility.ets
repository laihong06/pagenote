import { AbilityConstant, UIAbility, Want } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';
import { hilog } from '@kit.PerformanceAnalysisKit';

const DOMAIN: number = 0x4e50;
const TAG: string = 'PageNote';

export default class EntryAbility extends UIAbility {
  private mainWindow: window.Window | null = null;
  private sizeChangeCallback: (size: window.Size) => void = () => {};

  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    hilog.info(DOMAIN, TAG, 'EntryAbility onCreate');
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
    hilog.info(DOMAIN, TAG, 'EntryAbility onWindowStageDestroy');
  }
}

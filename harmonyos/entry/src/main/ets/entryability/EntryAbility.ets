import { AbilityConstant, UIAbility, Want } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';
import { hilog } from '@kit.PerformanceAnalysisKit';

const DOMAIN: number = 0x4e50;
const TAG: string = 'PageNote';

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    hilog.info(DOMAIN, TAG, 'EntryAbility onCreate');
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    windowStage.loadContent('pages/BookshelfPage', (err) => {
      if (err.code) {
        hilog.error(DOMAIN, TAG, `loadContent failed: ${JSON.stringify(err)}`);
      }
    });
  }

  onWindowStageDestroy(): void {
    hilog.info(DOMAIN, TAG, 'EntryAbility onWindowStageDestroy');
  }
}

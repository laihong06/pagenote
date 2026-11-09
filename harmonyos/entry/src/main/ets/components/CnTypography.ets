import { deviceInfo } from '@kit.BasicServicesKit';

/**
 * API 26 中文排版优化（避孤字 + 标点悬挂）的条件挂载。
 * 新属性方法在低版本运行时不存在、直接链式调用即 TypeError（API 24 实测崩溃），
 * 因此只能经 AttributeModifier 在确认可用后挂载；可用性判断本身也做了 typeof 防御
 * （deviceInfo.apiAvailable 同样 @since 26）。
 */
export function cnTypographyAvailable(): boolean {
  try {
    return typeof deviceInfo.apiAvailable === 'function' && deviceInfo.apiAvailable('26.0.0');
  } catch (e) {
    return false;
  }
}

export class CnTextAreaTypography implements AttributeModifier<TextAreaAttribute> {
  applyNormalAttribute(instance: TextAreaAttribute): void {
    if (deviceInfo.apiAvailable('26.0.0')) {
      instance.orphanCharOptimization(true);
      instance.punctuationOverflow(true);
    }
  }
}

export class CnTextTypography implements AttributeModifier<TextAttribute> {
  applyNormalAttribute(instance: TextAttribute): void {
    if (deviceInfo.apiAvailable('26.0.0')) {
      instance.orphanCharOptimization(true);
      instance.punctuationOverflow(true);
    }
  }
}

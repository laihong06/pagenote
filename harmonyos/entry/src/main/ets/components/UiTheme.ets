/**
 * 视觉设计令牌：页面灰底 + 白色卡片层次 + 轻阴影，统一圆角/留白/文字层级。
 * 明暗双套色板：组件经 @StorageProp('colorMode') 取系统深色状态（EntryAbility 用
 * Environment.envProp 注册），把 isDark 传给这里的取色函数。品牌色两模式通用。
 */
export class UiTheme {
  static readonly PRIMARY: string = '#2263EB';
  static readonly ACCENT: string = '#B45309';
  static readonly SUCCESS: string = '#0E9F6E';
  static readonly DANGER: string = '#C7254E';
  static readonly RADIUS_CARD: number = 14;
  static readonly RADIUS_CONTROL: number = 10;

  static pageBg(dark: boolean): string {
    return dark ? '#101114' : '#F2F3F7';
  }

  static cardBg(dark: boolean): string {
    return dark ? '#1C1E22' : '#FFFFFF';
  }

  static capsuleBg(dark: boolean): string {
    return dark ? '#26292F' : '#F2F3F7';
  }

  static ink(dark: boolean): string {
    return dark ? '#E8EAED' : '#1A1C1E';
  }

  static ink2(dark: boolean): string {
    return dark ? '#A8ADB4' : '#5A6066';
  }

  static ink3(dark: boolean): string {
    return dark ? '#6E747C' : '#9AA0A6';
  }

  static hairline(dark: boolean): string {
    return dark ? '#31353C' : '#E8EAEE';
  }

  static primarySoft(dark: boolean): string {
    return dark ? '#24344D' : '#E8EFFC';
  }

  static accentSoft(dark: boolean): string {
    return dark ? '#3D2E16' : '#FBF0E2';
  }

  /** 深色下品牌蓝提亮，保证浅底上的图标对比度 */
  static primaryIcon(dark: boolean): string {
    return dark ? '#9AB8FF' : UiTheme.PRIMARY;
  }

  static accentIcon(dark: boolean): string {
    return dark ? '#E5A33C' : UiTheme.ACCENT;
  }

  static hoverBg(dark: boolean): string {
    return dark ? '#1AFFFFFF' : '#14000000';
  }

  /** 表单类灰按钮底（sheet 内取消/次要操作） */
  static controlIdle(dark: boolean): string {
    return dark ? '#2A2D33' : '#EEEEEE';
  }

  static cardShadow(dark: boolean): ShadowOptions {
    return { radius: 16, color: dark ? '#66000000' : '#12000000', offsetX: 0, offsetY: 3 };
  }
}

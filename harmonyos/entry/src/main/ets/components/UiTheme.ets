/**
 * 视觉设计令牌：页面灰底 + 白色卡片层次 + 轻阴影，统一圆角/留白/文字层级。
 * 主色与桌面版品牌色一致；深色模式属后续主题化工程，先统一令牌便于届时切换。
 */
export class UiTheme {
  static readonly PAGE_BG: string = '#F2F3F7';
  static readonly CARD_BG: string = '#FFFFFF';
  static readonly PRIMARY: string = '#2263EB';
  static readonly PRIMARY_SOFT: string = '#E8EFFC';
  static readonly ACCENT: string = '#B45309';
  static readonly ACCENT_SOFT: string = '#FBF0E2';
  static readonly INK: string = '#1A1C1E';
  static readonly INK_SECONDARY: string = '#5A6066';
  static readonly INK_WEAK: string = '#9AA0A6';
  static readonly HAIRLINE: string = '#E8EAEE';
  static readonly RADIUS_CARD: number = 14;
  static readonly RADIUS_CONTROL: number = 10;

  static cardShadow(): ShadowOptions {
    return { radius: 16, color: '#12000000', offsetX: 0, offsetY: 3 };
  }
}

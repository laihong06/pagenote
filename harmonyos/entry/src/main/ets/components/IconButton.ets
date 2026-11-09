import { UiTheme } from './UiTheme';

/**
 * 图标按钮：华为系统符号（SymbolGlyph + $r('sys.symbol.*')）替代文字按钮。
 * 常态透明底，悬停浅灰底并浮出文字气泡；激活态浅彩底 + 彩色图标；
 * filled 为实心主按钮（保存/新建项目等）。label 仅作无障碍朗读与悬停气泡文本。
 * 符号名以华为 HarmonyOS Symbol 官方图标库为准（developer.huawei.com/consumer/cn/design/harmonyos-symbol）。
 * 颜色随系统深浅色模式（UiTheme 色板）。
 */
@Component
export struct IconButton {
  symbol: Resource = $r('sys.symbol.plus');
  label: string = '';
  // 父组件运行期切换的参数必须是 @Prop，否则只在创建时赋值一次（激活态/禁用态/角标会冻结）
  @Prop active: boolean = false;
  @Prop activeColor: string = UiTheme.PRIMARY;
  @Prop filled: boolean = false;
  @Prop showDot: boolean = false;
  @Prop enabledState: boolean = true;
  onTap: () => void = () => {};
  // 系统深浅色模式（EntryAbility 用 media query (dark-mode) 注册并监听切换）
  @StorageProp('isDarkMode') darkMode: boolean = false;
  @State private hovering: boolean = false;

  private isAccent(): boolean {
    return this.activeColor === UiTheme.ACCENT;
  }

  private bgColor(): string {
    const dark = this.darkMode;
    if (this.active) {
      if (this.filled) {
        return this.activeColor;
      }
      return this.isAccent() ? UiTheme.accentSoft(dark) : UiTheme.primarySoft(dark);
    }
    return this.hovering ? UiTheme.hoverBg(dark) : '#00000000';
  }

  private iconColor(): string {
    const dark = this.darkMode;
    if (this.active) {
      if (this.filled) {
        return '#FFFFFF';
      }
      return this.isAccent() ? UiTheme.accentIcon(dark) : UiTheme.primaryIcon(dark);
    }
    return dark ? '#A8ADB4' : '#4A4F55';
  }

  build() {
    Stack({ alignContent: Alignment.TopEnd }) {
      Button() {
        SymbolGlyph(this.symbol)
          .fontSize(20)
          .fontColor([this.iconColor()])
      }
      .type(ButtonType.Normal)
      .width(40)
      .height(36)
      .borderRadius(UiTheme.RADIUS_CONTROL)
      .backgroundColor(this.bgColor())
      .opacity(this.enabledState ? 1.0 : 0.35)
      .enabled(this.enabledState)
      .accessibilityText(this.label)
      .onClick(() => {
        // 触摸点击后清除悬停态，否则悬停气泡会残留在屏幕上（触摸设备无 mouseleave 补偿）
        this.hovering = false;
        this.onTap();
      })
      .onHover((isHover: boolean) => {
        this.hovering = isHover;
      })

      if (this.showDot) {
        Stack()
          .width(8)
          .height(8)
          .borderRadius(4)
          .backgroundColor('#FF8800')
          .offset({ x: -4, y: 4 })
      }

      // 悬停文字提示：不用 bindPopup（会被点击连带触发），自定义气泡只在鼠标/手写笔悬停时浮现；
      // 图标按钮都在各面板顶排，气泡向下浮出避免被窗口/弹层上沿裁掉
      if (this.hovering && this.label.length > 0) {
        Text(this.label)
          .fontSize(11)
          .fontColor('#FFFFFF')
          .backgroundColor('#E6333333')
          .borderRadius(6)
          .padding({ left: 8, right: 8, top: 3, bottom: 3 })
          .offset({ x: -11, y: 40 })
          .hitTestBehavior(HitTestMode.None)
          .transition(TransitionEffect.OPACITY.animation({ duration: 150 }))
      }
    }
  }
}
